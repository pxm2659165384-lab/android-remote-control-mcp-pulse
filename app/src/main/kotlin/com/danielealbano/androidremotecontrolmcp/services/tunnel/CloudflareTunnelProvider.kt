package com.danielealbano.androidremotecontrolmcp.services.tunnel

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.data.model.CloudflareTunnelMode
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelProviderType
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import javax.inject.Inject

/**
 * Cloudflare tunnel provider.
 *
 * Runs the `cloudflared` binary as a child process. Two modes are supported:
 * - [CloudflareTunnelMode.FREE]: a temporary Quick Tunnel with a random
 *   `*.trycloudflare.com` URL (no account or configuration needed).
 * - [CloudflareTunnelMode.TOKEN]: a remotely-managed named tunnel run with a
 *   dashboard token. The public hostname(s) and origin service are configured in
 *   the Cloudflare dashboard; cloudflared receives that config from the edge and
 *   logs it as an `Updated to new configuration` line, which we parse to obtain
 *   the static hostname(s) and to validate that the routed service points at our
 *   local MCP server.
 */
class CloudflareTunnelProvider
    @Inject
    constructor(
        private val binaryResolver: CloudflaredBinaryResolver,
    ) : TunnelProvider {
        private val _status = MutableStateFlow<TunnelStatus>(TunnelStatus.Disconnected)
        override val status: StateFlow<TunnelStatus> = _status.asStateFlow()

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutex = Mutex()
        private var process: Process? = null
        private var stderrReaderJob: Job? = null
        private var processMonitorJob: Job? = null
        private var timeoutJob: Job? = null

        @Volatile
        private var terminating = false

        /**
         * Timeout (ms) within which a token tunnel must receive a valid remote configuration
         * before it is considered misconfigured. Overridable in tests; do NOT change the default.
         */
        internal var configTimeoutMs: Long = TOKEN_CONFIG_TIMEOUT_MS

        override suspend fun start(
            localPort: Int,
            config: ServerConfig,
        ) {
            mutex.withLock {
                check(process == null) { "Tunnel is already running" }
                terminating = false

                val binaryPath =
                    binaryResolver.resolve() ?: run {
                        _status.value = TunnelStatus.Error("cloudflared binary not found")
                        return
                    }

                when (config.cloudflareTunnelMode) {
                    CloudflareTunnelMode.FREE -> startFreeTunnel(binaryPath, localPort)
                    CloudflareTunnelMode.TOKEN -> startTokenTunnel(binaryPath, localPort, config)
                }
            }
        }

        private fun startFreeTunnel(
            binaryPath: String,
            localPort: Int,
        ) {
            _status.value = TunnelStatus.Connecting
            try {
                val pb =
                    ProcessBuilder(
                        binaryPath,
                        "tunnel",
                        "--url",
                        "http://localhost:$localPort",
                        "--output",
                        "json",
                    )
                pb.redirectErrorStream(false)
                val proc = pb.start()
                process = proc

                launchStderrReader(proc) { line ->
                    val match = TUNNEL_URL_REGEX.find(line)
                    if (match != null && _status.value is TunnelStatus.Connecting) {
                        val url = match.value
                        Log.i(TAG, "Cloudflare tunnel URL: $url")
                        _status.value =
                            TunnelStatus.Connected(
                                urls = listOf(url),
                                providerType = TunnelProviderType.CLOUDFLARE,
                            )
                    }
                }
                startProcessMonitor(proc)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.e(TAG, "Failed to start cloudflared process", e)
                _status.value = TunnelStatus.Error("Failed to start cloudflared: ${e.message}")
                process = null
            }
        }

        private fun startTokenTunnel(
            binaryPath: String,
            localPort: Int,
            config: ServerConfig,
        ) {
            if (config.cloudflareTunnelToken.isEmpty()) {
                _status.value = TunnelStatus.Error("Cloudflare tunnel token is required")
                return
            }

            _status.value = TunnelStatus.Connecting
            try {
                // NOTE: exact argument order; `--output json` MUST come before `run`, and `--url`
                // MUST NOT be passed (it breaks the control stream of a remotely-managed tunnel).
                val pb =
                    ProcessBuilder(
                        binaryPath,
                        "tunnel",
                        "--output",
                        "json",
                        "run",
                        "--token",
                        config.cloudflareTunnelToken,
                    )
                pb.redirectErrorStream(false)
                val proc = pb.start()
                process = proc

                launchStderrReader(proc) { line ->
                    // Skip non-JSON lines and act only on the remote-config push; `Registered tunnel
                    // connection` is ignored so status stays Connecting until a valid config validates.
                    if (!terminating && logMessageOf(line) == MSG_UPDATED_CONFIG) {
                        configPayloadOf(line)?.let { handleTokenConfig(it, localPort) }
                    }
                }
                startProcessMonitor(proc)
                startTokenConfigTimeout()
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.e(TAG, "Failed to start cloudflared process", e)
                _status.value = TunnelStatus.Error("Failed to start cloudflared: ${e.message}")
                process = null
            }
        }

        override suspend fun stop() {
            shutdownProcess(setDisconnected = true)
            terminating = false
        }

        /**
         * Mutex-guarded teardown shared by [stop] and [terminateWithError]. Cancels all jobs and
         * destroys the process. When [setDisconnected] is true the status becomes
         * [TunnelStatus.Disconnected]; otherwise the current (error) status is preserved.
         */
        private suspend fun shutdownProcess(setDisconnected: Boolean) {
            mutex.withLock {
                timeoutJob?.cancel()
                timeoutJob = null
                processMonitorJob?.cancel()
                processMonitorJob = null
                stderrReaderJob?.cancel()
                stderrReaderJob = null

                process?.let { proc ->
                    proc.destroy()
                    withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        proc.waitFor()
                    } ?: proc.destroyForcibly()
                }
                process = null

                if (setDisconnected) {
                    _status.value = TunnelStatus.Disconnected
                }
            }
        }

        /**
         * Sets the error status and tears the tunnel down. Safe to call from the stderr-reader or
         * timeout coroutine: the teardown is launched on [scope] (never inline) so it can take the
         * mutex without deadlocking, and [terminating] is set first so the process monitor will not
         * overwrite the error when it observes the resulting process exit.
         */
        private fun terminateWithError(message: String) {
            terminating = true
            _status.value = TunnelStatus.Error(message)
            scope.launch { shutdownProcess(setDisconnected = false) }
        }

        /**
         * Launches a coroutine that reads the process stderr line by line and forwards each line to
         * [onLine]. Shared by both modes; mode-specific handling lives in the caller's lambda.
         */
        private fun launchStderrReader(
            proc: Process,
            onLine: (String) -> Unit,
        ) {
            stderrReaderJob =
                scope.launch {
                    try {
                        val reader = BufferedReader(InputStreamReader(proc.errorStream))
                        while (isActive) {
                            @Suppress("BlockingMethodInNonBlockingContext")
                            val line = reader.readLine() ?: break
                            Log.d(TAG, "cloudflared: $line")
                            onLine(line)
                        }
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        if (isActive) {
                            Log.w(TAG, "Error reading cloudflared stderr", e)
                        }
                    }
                }
        }

        /**
         * Validates a remote-config push and updates status. Runs on every push (re-validation):
         * zero public hostnames or any hostname whose service is not our local MCP server stops the
         * tunnel with an error; otherwise the status becomes Connected with every public hostname.
         */
        private fun handleTokenConfig(
            configJson: String,
            localPort: Int,
        ) {
            val routes = ingressRoutesOf(configJson)
            if (routes.isEmpty()) {
                terminateWithError("No public hostname configured for this tunnel")
                return
            }
            val invalid = routes.firstOrNull { !isServiceValid(it.service, localPort) }
            if (invalid != null) {
                terminateWithError("Tunnel route misconfigured: ${invalid.hostname} -> ${invalid.service}")
                return
            }
            timeoutJob?.cancel()
            timeoutJob = null
            _status.value =
                TunnelStatus.Connected(
                    urls = routes.map { "https://${it.hostname}" },
                    providerType = TunnelProviderType.CLOUDFLARE,
                )
        }

        private fun startTokenConfigTimeout() {
            timeoutJob =
                scope.launch {
                    delay(configTimeoutMs)
                    if (_status.value is TunnelStatus.Connecting) {
                        terminateWithError("No public hostname configured for this tunnel")
                    }
                }
        }

        private fun startProcessMonitor(proc: Process) {
            processMonitorJob =
                scope.launch {
                    // Give the process a moment to start before monitoring exit
                    delay(PROCESS_MONITOR_INITIAL_DELAY_MS)

                    @Suppress("BlockingMethodInNonBlockingContext")
                    val exitCode = proc.waitFor()

                    val statusIsLive =
                        _status.value is TunnelStatus.Connecting || _status.value is TunnelStatus.Connected
                    if (isActive && !terminating && statusIsLive) {
                        Log.w(TAG, "cloudflared process exited unexpectedly with code $exitCode")
                        _status.value =
                            TunnelStatus.Error(
                                "cloudflared process exited unexpectedly (code $exitCode)",
                            )
                        mutex.withLock {
                            process = null
                        }
                    }
                }
        }

        /** A Cloudflare ingress rule that declares a public hostname. */
        internal data class IngressRoute(
            val hostname: String,
            val service: String,
        )

        companion object {
            private const val TAG = "MCP:CloudflareTunnel"
            internal val TUNNEL_URL_REGEX =
                Regex("https://[-a-zA-Z0-9]+\\.trycloudflare\\.com")
            internal const val SHUTDOWN_TIMEOUT_MS = 5_000L
            private const val PROCESS_MONITOR_INITIAL_DELAY_MS = 1_000L
            internal const val MSG_UPDATED_CONFIG = "Updated to new configuration"
            internal const val TOKEN_CONFIG_TIMEOUT_MS = 10_000L
            internal val LOG_JSON = Json { ignoreUnknownKeys = true }

            /** Returns the top-level `message` field of a cloudflared JSON log line, or null for non-JSON. */
            internal fun logMessageOf(line: String): String? =
                runCatching {
                    LOG_JSON
                        .parseToJsonElement(line)
                        .jsonObject["message"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                }.getOrNull()

            /** Returns the escaped `config` payload of an `Updated to new configuration` line, or null. */
            internal fun configPayloadOf(line: String): String? =
                runCatching {
                    LOG_JSON
                        .parseToJsonElement(line)
                        .jsonObject["config"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                }.getOrNull()

            /** Parses the nested config JSON into the ingress entries that declare a hostname. */
            internal fun ingressRoutesOf(configJson: String): List<IngressRoute> =
                runCatching {
                    LOG_JSON
                        .parseToJsonElement(configJson)
                        .jsonObject["ingress"]
                        ?.jsonArray
                        .orEmpty()
                        .mapNotNull { element ->
                            val obj = element.jsonObject
                            val host = obj["hostname"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            IngressRoute(host, obj["service"]?.jsonPrimitive?.contentOrNull ?: "")
                        }
                }.getOrDefault(emptyList())

            /** True when the routed service points at our MCP server: http://(localhost|127.0.0.1):<port>. */
            internal fun isServiceValid(
                service: String,
                expectedPort: Int,
            ): Boolean {
                val uri = runCatching { URI(service) }.getOrNull() ?: return false
                val path = uri.path
                return uri.scheme == "http" &&
                    (uri.host == "localhost" || uri.host == "127.0.0.1") &&
                    uri.port == expectedPort &&
                    (path.isNullOrEmpty() || path == "/")
            }
        }
    }
