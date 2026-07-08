package com.danielealbano.androidremotecontrolmcp.services.pulselink

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.danielealbano.androidremotecontrolmcp.McpApplication
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.MainActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class HapticMiddlewareService : Service() {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            PulseLogger.e("Pulse service coroutine failed: ${throwable.message}", throwable)
        }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private val serviceActive = AtomicBoolean(false)
    private val responseJson = Json { encodeDefaults = true }
    private val httpServerLock = Any()
    private var server: PulseHttpServer? = null
    private var httpWatchdogJob: Job? = null
    private var keepAliveOverlay: PulseKeepAliveOverlay? = null
    private lateinit var hapticEngine: ProceduralHapticEngine
    private lateinit var biometricCollector: BiometricCollector
    private lateinit var buttplugClient: ButtplugWebSocketClient
    private lateinit var heartRateBridge: GadgetbridgeHeartRateBridge
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var enableIntiface = true
    private var enableGadgetbridge = true

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(AudioManager::class.java)
        acquireWakeLock()
        keepAliveOverlay = PulseKeepAliveOverlay(applicationContext)
        PulseLogger.i("Pulse Link service created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                enableIntiface = intent?.getBooleanExtra(EXTRA_ENABLE_INTIFACE, true) ?: true
                enableGadgetbridge = intent?.getBooleanExtra(EXTRA_ENABLE_GADGETBRIDGE, true) ?: true
                if (serviceActive.compareAndSet(false, true)) {
                    serviceScope.launch { startMiddleware() }
                } else {
                    PulseLogger.w("Pulse Link service already running")
                }
            }
        }
        return START_STICKY
    }

    suspend fun triggerLocal(
        mode: String,
        level: Int?,
        randomize: Boolean,
        target: String,
    ): Boolean {
        if (!::hapticEngine.isInitialized) {
            PulseLogger.w("Pulse Link service is not ready; haptic command ignored")
            return false
        }
        return hapticEngine.trigger(mode, level, randomize, target)
    }

    suspend fun stopHaptics() {
        if (::hapticEngine.isInitialized) {
            hapticEngine.emergencyStop()
        } else {
            PulseLogger.w("Pulse Link service is not ready; stop command ignored")
        }
        if (::audioManager.isInitialized) {
            MediaTransitionManager.abandonAudioFocus(audioManager, audioFocusRequest)
        }
        audioFocusRequest = null
    }

    fun updateDefaultLevel(level: Int) {
        if (::hapticEngine.isInitialized) {
            hapticEngine.defaultLevel = level.coerceIn(1, 5)
            PulseLogger.i("Default haptic level set to ${hapticEngine.defaultLevel}")
        }
    }

    private fun startMiddleware() {
        _runtimeStatus.value = _runtimeStatus.value.copy(state = PulseServiceState.STARTING, error = null)
        PulseLogger.i("Starting Pulse Link middleware")
        LanMatrixManager.load(this)
        MediaTransitionManager.load(this)
        HapticPatternLibrary.loadUserModes(this)
        biometricCollector = BiometricCollector()
        buttplugClient = ButtplugWebSocketClient(serviceScope)
        hapticEngine = ProceduralHapticEngine(this, buttplugClient)
        if (enableGadgetbridge) {
            heartRateBridge = GadgetbridgeHeartRateBridge(this, biometricCollector, serviceScope)
            heartRateBridge.start()
        } else {
            PulseLogger.i("Gadgetbridge bridge disabled for this Pulse Link session")
        }
        if (enableIntiface) {
            serviceScope.launch { buttplugClient.connectLoop() }
        } else {
            PulseLogger.i("Intiface WebSocket bridge disabled for this Pulse Link session")
        }
        keepAliveOverlay?.showIfPermitted()
        startHttpServer()
        startHttpWatchdog()
        PulseLogger.i("Pulse Link middleware running on ${LanMatrixManager.localIpAddress()}:$DEFAULT_PORT")
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock =
            powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:PulseLink")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        PulseLogger.i("Pulse Link wake lock acquired")
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }.onFailure {
            PulseLogger.w("Pulse Link wake lock release failed: ${it.message}")
        }
        wakeLock = null
    }

    private fun startHttpServer() {
        synchronized(httpServerLock) {
            startHttpServerLocked()
        }
    }

    private fun startHttpServerLocked() {
        try {
            server =
                PulseHttpServer(
                    host = HOST,
                    port = DEFAULT_PORT,
                    handler = ::handleHttpRequest,
                ).also { it.start() }
            _runtimeStatus.value =
                PulseRuntimeStatus(
                    state = PulseServiceState.RUNNING,
                    host = HOST,
                    port = DEFAULT_PORT,
                    localIp = LanMatrixManager.localIpAddress(),
                )
        } catch (e: Exception) {
            serviceActive.set(false)
            _runtimeStatus.value =
                PulseRuntimeStatus(
                    state = PulseServiceState.ERROR,
                    host = HOST,
                    port = DEFAULT_PORT,
                    localIp = LanMatrixManager.localIpAddress(),
                    error = e.message,
            )
            PulseLogger.e("Pulse HTTP server start failed: ${e.message}", e)
        }
    }

    private fun restartHttpServer(reason: String) {
        synchronized(httpServerLock) {
            PulseLogger.w("Restarting Pulse HTTP server: $reason")
            runCatching { server?.stop() }
            server = null
            startHttpServerLocked()
        }
    }

    private fun startHttpWatchdog() {
        if (httpWatchdogJob?.isActive == true) return
        httpWatchdogJob =
            serviceScope.launch {
                delay(HTTP_HEALTH_INITIAL_DELAY_MS)
                while (isActive && serviceActive.get()) {
                    val currentServer = synchronized(httpServerLock) { server }
                    val serving = currentServer?.isServing == true
                    val responsive = serving && runHttpHealthProbe()
                    if (!responsive && serviceActive.get()) {
                        val reason =
                            if (serving) {
                                "health probe timeout"
                            } else {
                                "accept thread not serving"
                            }
                        restartHttpServer(reason)
                    }
                    delay(HTTP_HEALTH_INTERVAL_MS)
                }
            }
    }

    private fun runHttpHealthProbe(): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", DEFAULT_PORT), HTTP_HEALTH_TIMEOUT_MS.toInt())
                socket.soTimeout = HTTP_HEALTH_TIMEOUT_MS.toInt()
                socket.getOutputStream().write(HTTP_HEALTH_REQUEST)
                socket.getOutputStream().flush()
                val buffer = ByteArray(HTTP_HEALTH_RESPONSE_BYTES)
                val read = socket.getInputStream().read(buffer)
                read > 0 && String(buffer, 0, read, StandardCharsets.UTF_8).contains(" 200 ")
            }
        }.getOrElse {
            PulseLogger.w("Pulse HTTP health probe failed: ${it.message}")
            false
        }

    private suspend fun handleHttpRequest(request: PulseHttpRequest): PulseHttpResponse =
        when (request.path) {
            "/healthz" ->
                PulseHttpResponse(
                    contentType = PulseHttpResponse.CONTENT_TYPE_TEXT,
                    body = "ok",
                )

            "/vibrate" -> handleVibrate(request.parameters)
            "/stop" -> handleStop()
            "/biometrics" -> handleBiometrics(request.parameters)
            "/status" -> jsonResponse(statusResponse())
            "/matrix/config" -> handleMatrixConfig(request.parameters)
            "/gamepads/refresh" -> handleGamepadsRefresh()
            "/playMedia" -> handlePlayMedia()
            else ->
                PulseHttpResponse(
                    status = PulseHttpResponse.HTTP_NOT_FOUND,
                    contentType = PulseHttpResponse.CONTENT_TYPE_TEXT,
                    body = "not found",
                )
        }

    private fun handleVibrate(parameters: Map<String, String>): PulseHttpResponse {
        val mode = parameters["mode"] ?: return jsonResponse(CommandResponse(success = false, error = "mode is required"))
        val level = parameters["level"]?.toIntOrNull()
        val randomize = parameters["randomize"]?.toBooleanStrictOrNull() ?: true
        val targets = parseTargets(parameters)
        val legacyTarget = legacyTarget(targets)
        val baseLevel = (level ?: hapticEngine.defaultLevel).coerceIn(1, 5)
        if (
            targets.any { it.isGamepadTarget() } &&
            !LanMatrixManager.canRouteGamepadToRemote(this) &&
            !hapticEngine.hasCachedGamepadVibrator()
        ) {
            val gamepads = hapticEngine.cachedGamepadStatus()
            return jsonResponse(
                CommandResponse(
                    success = false,
                    error = "no_gamepad_vibrator",
                    target = legacyTarget,
                    gamepadConnected = gamepads.isNotEmpty(),
                    gamepadVibratorAvailable = false,
                ),
            )
        }

        return jsonResponse(
            CommandResponse(success = true),
            afterSend = {
                dispatchVibrate(mode, level, baseLevel, randomize, targets)
            },
        )
    }

    private fun dispatchVibrate(
        mode: String,
        level: Int?,
        baseLevel: Int,
        randomize: Boolean,
        targets: List<String>,
    ) {
        serviceScope.launch {
            val legacyTarget = legacyTarget(targets)
            val targetsCsv = targets.joinToString(",")
            PulseLogger.i(
                "/vibrate accepted mode=$mode level=${level ?: "default"} " +
                    "baseLevel=$baseLevel randomize=$randomize targets=$targetsCsv matrix=${LanMatrixManager.mode.name} " +
                    "relayNodes=${LanMatrixManager.nodes.count { it.enabled }}",
            )
            runCatching {
                withTimeout(TRIGGER_TIMEOUT_MS) {
                    if (LanMatrixManager.mode == MatrixMode.MASTER) {
                        LanMatrixManager.masterFanOut(
                            context = this@HapticMiddlewareService,
                            hapticEngine = hapticEngine,
                            modeName = mode,
                            baseLevel = baseLevel,
                            randomize = randomize,
                            target = legacyTarget,
                            targets = targets,
                        )
                    } else {
                        hapticEngine.trigger(mode, level, randomize, targetsCsv)
                    }
                }
            }.onFailure {
                PulseLogger.w("Haptic dispatch timed out or failed: ${it.message}")
            }
        }
    }

    private fun handleStop(): PulseHttpResponse {
        serviceScope.launch {
            if (LanMatrixManager.mode == MatrixMode.MASTER) {
                runCatching {
                    withTimeout(TIMEOUT_MS) {
                        LanMatrixManager.stopRemoteNodes(this@HapticMiddlewareService)
                    }
                }.onFailure {
                    PulseLogger.w("Remote matrix stop timed out: ${it.message}")
                }
            }
            runCatching {
                withTimeout(TIMEOUT_MS) {
                    stopHaptics()
                }
            }.onFailure {
                PulseLogger.w("Local haptic stop timed out: ${it.message}")
            }
        }
        return jsonResponse(CommandResponse(success = true))
    }

    private suspend fun handleBiometrics(parameters: Map<String, String>): PulseHttpResponse {
        val duration = parameters["duration"]?.toIntOrNull() ?: 0
        val response =
            if (duration > 0) {
                biometricCollector.getHistoricalData(duration)
            } else {
                biometricCollector.getCurrentData()
            }
        return jsonResponse(response)
    }

    private suspend fun handleMatrixConfig(parameters: Map<String, String>): PulseHttpResponse {
        val modeParam = parameters["mode"]
        val clear = parameters["clear"]?.toBooleanStrictOrNull() == true
        val node = parameters["node"] ?: parameters["ip"]
        val port = parameters["port"]?.toIntOrNull() ?: DEFAULT_PORT
        val attenuation = parameters["attenuation"]?.toFloatOrNull() ?: 1.0f
        val label = parameters["label"] ?: ""
        val matrixMode =
            if (modeParam.isNullOrBlank()) {
                null
            } else {
                runCatching { MatrixMode.valueOf(modeParam.trim().uppercase()) }.getOrNull()
                    ?: return jsonText(matrixConfigJson(success = false, error = "invalid_mode"))
            }

        matrixMode?.let { LanMatrixManager.setMode(this@HapticMiddlewareService, it) }
        if (clear) LanMatrixManager.clearNodes(this@HapticMiddlewareService)
        if (!node.isNullOrBlank()) {
            LanMatrixManager.addNode(
                context = this@HapticMiddlewareService,
                ipAddress = node,
                port = port,
                attenuation = attenuation,
                label = label,
            )
        }

        return jsonText(matrixConfigJson())
    }

    private fun handleGamepadsRefresh(): PulseHttpResponse {
        val gamepads = hapticEngine.cachedGamepadStatus()
        return jsonResponse(
            CommandResponse(
                success = true,
                error = if (gamepads.any { it.vibratorAvailable }) null else "gamepad_refresh_scheduled",
            ),
            afterSend = {
                serviceScope.launch {
                    runCatching {
                        withTimeout(GAMEPAD_REFRESH_TIMEOUT_MS) {
                            hapticEngine.refreshGamepads()
                        }
                    }.onFailure {
                        PulseLogger.w("Gamepad refresh timed out or failed: ${it.message}")
                    }
                }
            },
        )
    }

    private fun handlePlayMedia(): PulseHttpResponse =
        try {
            MediaTransitionManager.play(this)
            PulseLogger.i("/playMedia launched ${MediaTransitionManager.config.displayName}")
            jsonResponse(CommandResponse(success = true))
        } catch (e: Exception) {
            PulseLogger.w("/playMedia failed: ${e.message}")
            jsonResponse(CommandResponse(success = false, error = e.message))
        }

    private fun statusResponse(): StatusResponse {
        val gamepads = hapticEngine.cachedGamepadStatus()
        return StatusResponse(
            success = true,
            ktorServer = "running",
            host = HOST,
            port = DEFAULT_PORT,
            localIp = LanMatrixManager.localIpAddress(),
            buttplugConnected = buttplugClient.isConnected.value,
            gadgetbridgeOk = biometricCollector.isAvailable,
            currentDefaultLevel = hapticEngine.defaultLevel,
            matrixMode = LanMatrixManager.mode.name,
            relayNodes = LanMatrixManager.nodes.count { it.enabled },
            mediaConfigured = MediaTransitionManager.config.fileUri.isNotBlank(),
            androidApi = android.os.Build.VERSION.SDK_INT,
            gamepadConnected = gamepads.isNotEmpty(),
            gamepadCount = gamepads.size,
            gamepadVibratorAvailable = gamepads.any { it.vibratorAvailable },
            gamepads = gamepads,
        )
    }

    private inline fun <reified T> jsonResponse(
        value: T,
        noinline afterSend: (() -> Unit)? = null,
    ): PulseHttpResponse =
        jsonText(responseJson.encodeToString(value), afterSend)

    private fun jsonText(
        body: String,
        afterSend: (() -> Unit)? = null,
    ): PulseHttpResponse = PulseHttpResponse(body = body, afterSend = afterSend)

    override fun onDestroy() {
        _runtimeStatus.value = _runtimeStatus.value.copy(state = PulseServiceState.STOPPING)
        httpWatchdogJob?.cancel()
        httpWatchdogJob = null
        runCatching {
            if (::hapticEngine.isInitialized) {
                runBlocking {
                    withTimeout(TIMEOUT_MS) {
                        hapticEngine.emergencyStop()
                    }
                }
                hapticEngine.close()
            }
        }
        runCatching {
            if (::heartRateBridge.isInitialized) heartRateBridge.stop()
        }
        runCatching {
            if (::buttplugClient.isInitialized) buttplugClient.close()
        }
        runCatching { server?.stop() }
        server = null
        keepAliveOverlay?.hide()
        keepAliveOverlay = null
        releaseWakeLock()
        serviceActive.set(false)
        serviceScope.cancel()
        instance = null
        _runtimeStatus.value = PulseRuntimeStatus(state = PulseServiceState.STOPPED)
        PulseLogger.i("Pulse Link service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun String.isGamepadTarget(): Boolean {
        val target = lowercase()
        return target == "gamepad" || target == "controller"
    }

    private fun parseTargets(parameters: Map<String, String>): List<String> {
        val raw = parameters["targets"] ?: parameters["target"] ?: "all"
        val expanded =
            raw.split(",", " ", ";")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .flatMap { token ->
                    when (token) {
                        "all" -> listOf("phone", "toy", "gamepad")
                        "phone", "local" -> listOf("phone")
                        "toy", "egg", "fleshlight" -> listOf(token)
                        "gamepad", "controller" -> listOf("gamepad")
                        else -> emptyList()
                    }
                }
                .distinct()
        return expanded.ifEmpty { listOf("phone") }
    }

    private fun legacyTarget(targets: List<String>): String =
        targets.distinct().singleOrNull() ?: "all"

    private fun LanMatrixManager.canRouteGamepadToRemote(context: Context): Boolean =
        mode == MatrixMode.MASTER && hasRemoteEnabledNode(context)

    private fun matrixConfigJson(
        success: Boolean = true,
        error: String? = null,
    ): String {
        val nodes =
            LanMatrixManager.nodes.joinToString(prefix = "[", postfix = "]") {
                "\"${it.ipAddress}:${it.port}\""
            }
        val errorJson = error?.let { ",\"error\":\"$it\"" }.orEmpty()
        return "{\"success\":$success,\"matrix_mode\":\"${LanMatrixManager.mode.name}\"," +
            "\"relay_nodes\":${LanMatrixManager.nodes.count { it.enabled }}," +
            "\"local_ip\":\"${LanMatrixManager.localIpAddress()}\",\"nodes\":$nodes$errorJson}"
    }

    private fun createNotification(): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat
            .Builder(this, McpApplication.MCP_SERVER_CHANNEL_ID)
            .setContentTitle("Pulse Link middleware")
            .setContentText("Keeping haptic bridge awake on ${LanMatrixManager.localIpAddress()}:$DEFAULT_PORT")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.danielealbano.androidremotecontrolmcp.ACTION_START_PULSE_LINK"
        const val ACTION_STOP = "com.danielealbano.androidremotecontrolmcp.ACTION_STOP_PULSE_LINK"
        const val EXTRA_ENABLE_INTIFACE = "enable_intiface"
        const val EXTRA_ENABLE_GADGETBRIDGE = "enable_gadgetbridge"
        const val DEFAULT_PORT = 8080
        private const val HOST = "0.0.0.0"
        private const val NOTIFICATION_ID = 1002
        private const val TIMEOUT_MS = 2000L
        private const val TRIGGER_TIMEOUT_MS = 3000L
        private const val GAMEPAD_REFRESH_TIMEOUT_MS = 1500L
        private const val HTTP_HEALTH_INITIAL_DELAY_MS = 5_000L
        private const val HTTP_HEALTH_INTERVAL_MS = 10_000L
        private const val HTTP_HEALTH_TIMEOUT_MS = 1_000L
        private const val HTTP_HEALTH_RESPONSE_BYTES = 96
        private val HTTP_HEALTH_REQUEST =
            "GET /healthz HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n"
                .toByteArray(StandardCharsets.UTF_8)

        private val _runtimeStatus = MutableStateFlow(PulseRuntimeStatus())
        val runtimeStatus: StateFlow<PulseRuntimeStatus> = _runtimeStatus.asStateFlow()

        @Volatile
        var instance: HapticMiddlewareService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, HapticMiddlewareService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, HapticMiddlewareService::class.java).apply { action = ACTION_STOP }
            context.startForegroundService(intent)
        }
    }
}
