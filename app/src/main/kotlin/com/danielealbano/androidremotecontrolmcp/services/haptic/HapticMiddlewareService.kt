package com.danielealbano.androidremotecontrolmcp.services.haptic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 触觉中台服务（Haptic Middleware Service）
 *
 * 核心职责：
 * - 启动 Ktor HTTP 服务器（127.0.0.1:8080）
 * - 提供三个 API 端点：/vibrate、/biometrics、/status
 * - 管理 Buttplug.io WebSocket 客户端连接循环
 * - 集成 ProceduralHapticEngine 和 BiometricCollector
 *
 * 服务特性：
 * - 前台服务（FOREGROUND_SERVICE_SPECIAL_USE）
 * - 所有网络通信限制在 127.0.0.1
 * - 协程驱动，非阻塞架构
 *
 * API 规范：
 * - GET /vibrate?mode=模式1&target=all  → 触发震动
 * - GET /biometrics                     → 获取当前心率
 * - GET /biometrics?duration=120        → 获取近120秒统计
 * - GET /status                         → 系统状态检查
 */
class HapticMiddlewareService : Service() {

    private var ktorServer: ApplicationEngine? = null
    private lateinit var hapticEngine: ProceduralHapticEngine
    private lateinit var buttplugClient: ButtplugWebSocketClient
    private lateinit var biometricCollector: BiometricCollector

    // 服务专用协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "HapticMiddlewareService creating...")

        // 初始化 Buttplug.io 客户端
        buttplugClient = ButtplugWebSocketClient(this)

        // 初始化触觉引擎
        hapticEngine = ProceduralHapticEngine(this, buttplugClient)

        // 初始化生物特征采集器
        biometricCollector = BiometricCollector(this)
        biometricCollector.start()

        // 将网络服务器与 WS 重连循环全部安全地移入后台 IO 协程线程池，杜绝 UI 卡死
        serviceScope.launch {
            startKtorServer()
        }

        serviceScope.launch {
            buttplugClient.connectLoop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动前台服务，显示持久通知
        startForeground(NOTIFICATION_ID, createNotification())
        Log.i(TAG, "HapticMiddlewareService started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "HapticMiddlewareService destroying...")

        // 停止所有组件
        biometricCollector.stop()
        serviceScope.launch {
            hapticEngine.stopAll()
            buttplugClient.disconnect()
        }
        ktorServer?.stop(1000, 2000)
        serviceScope.cancel()
    }

    /**
     * 启动 Ktor HTTP 服务器（127.0.0.1:8080）
     */
    private fun startKtorServer() {
        try {
            ktorServer = embeddedServer(CIO, host = "127.0.0.1", port = 8080) {
                install(ContentNegotiation) {
                    json()
                }

                routing {
                    /**
                     * 触发震动
                     * GET /vibrate?mode=模式1&target=all
                     * GET /vibrate?mode=mode_5&target=toy
                     * GET /vibrate?mode=任意AI自定义名称&target=phone
                     */
                    get("/vibrate") {
                        val mode = call.parameters["mode"]
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("success" to false, "error" to "mode parameter required")
                            )
                        val target = call.parameters["target"] ?: "all"

                        // 异步触发震动，不阻塞HTTP响应
                        val result = hapticEngine.trigger(mode, target)

                        call.respond(
                            mapOf(
                                "success" to result,
                                "mode" to mode,
                                "target" to target,
                                "level" to hapticEngine.currentLevel
                            )
                        )
                    }

                    /**
                     * 获取心率数据
                     * GET /biometrics           → 返回当前最新数据
                     * GET /biometrics?duration=120 → 返回近120秒的完整统计
                     */
                    get("/biometrics") {
                        val duration = call.parameters["duration"]?.toIntOrNull() ?: 0
                        val response = if (duration > 0) {
                            biometricCollector.getHistoricalData(duration)
                        } else {
                            biometricCollector.getCurrentData()
                        }
                        call.respond(response)
                    }

                    /**
                     * 系统状态检查
                     * GET /status
                     */
                    get("/status") {
                        call.respond(
                            mapOf(
                                "success" to true,
                                "ktor_server" to "running",
                                "buttplug_connected" to buttplugClient.isConnected(),
                                "gadgetbridge_ok" to biometricCollector.isAvailable(),
                                "current_level" to hapticEngine.currentLevel,
                                "android_api" to Build.VERSION.SDK_INT,
                                "supported_modes" to HapticPatternLibrary.getAllModeNames().size
                            )
                        )
                    }

                    /**
                     * 紧急停止所有震动
                     * GET /stop
                     */
                    get("/stop") {
                        serviceScope.launch {
                            hapticEngine.stopAll()
                        }
                        call.respond(mapOf("success" to true, "message" to "All haptics stopped"))
                    }

                    /**
                     * 设置强度档位
                     * GET /set_level?level=3
                     */
                    get("/set_level") {
                        val level = call.parameters["level"]?.toIntOrNull()
                        if (level == null || level !in 1..5) {
                            return@get call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("success" to false, "error" to "level must be 1-5")
                            )
                        }
                        hapticEngine.currentLevel = level
                        call.respond(
                            mapOf(
                                "success" to true,
                                "level" to level
                            )
                        )
                    }
                }
            }.start(wait = false)

            Log.i(TAG, "Ktor server started on http://127.0.0.1:8080")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Ktor server: ${e.message}", e)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        val channelId = "haptic_middleware_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Haptic Middleware Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "触觉中台服务：提供局域网触觉同步和生物特征采集"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        return Notification.Builder(this, channelId)
            .setContentTitle("触觉中台运行中")
            .setContentText("HTTP服务：127.0.0.1:8080")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    companion object {
        private const val TAG = "HapticMiddleware"
        private const val NOTIFICATION_ID = 9001
    }
}
