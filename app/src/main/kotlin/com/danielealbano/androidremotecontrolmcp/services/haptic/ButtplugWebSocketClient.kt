package com.danielealbano.androidremotecontrolmcp.services.haptic

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlin.math.min
import kotlin.math.pow

/**
 * Buttplug.io WebSocket 客户端
 *
 * 职责：
 * - 连接到本地 Intiface Central (ws://127.0.0.1:12345)
 * - 实现指数退避重连策略（最多100次，1s → 16s）
 * - 发送 Buttplug.io JSON-RPC ScalarCmd 控制外部玩具震动
 * - 支持协程取消时强制关闭玩具（NonCancellable上下文）
 *
 * 关键安全机制：
 * - 每次重连前强制关闭旧客户端，防止内存泄漏
 * - sendWaveform 被取消时必须发送强度0.0，避免玩具锁死
 * - BLE安全：每个ScalarCmd间隔至少80ms
 */
class ButtplugWebSocketClient(private val context: Context) {

    private var client: HttpClient? = null
    private var session: DefaultWebSocketSession? = null
    private var retryCount = 0
    private val maxRetries = 100
    private var _isConnected = false
    private var messageId = 0

    /**
     * 连接状态查询
     */
    fun isConnected() = _isConnected

    /**
     * WebSocket 连接循环（指数退避重连）
     *
     * 核心改进：
     * - 使用 while 循环而非递归，避免栈溢出
     * - 每次重连前强制 close 旧客户端，释放网络资源
     * - 首次失败时检查 Intiface Central 是否已安装
     */
    suspend fun connectLoop() {
        while (retryCount < maxRetries) {
            try {
                // 🌟 每次新建前，强制销毁旧客户端释放网络套接字
                client?.close()
                client = HttpClient(CIO) {
                    install(WebSockets)
                }

                session = client!!.webSocketSession("ws://127.0.0.1:12345")
                retryCount = 0 // 连接成功，重置计数器
                _isConnected = true
                Log.i(TAG, "Connected to Intiface Central")

                // 启动心跳（可选，Buttplug.io协议支持）
                startHeartbeat()

                // 监听服务器消息
                for (frame in session!!.incoming) {
                    if (frame is Frame.Text) {
                        handleResponse(frame.readText())
                    }
                }
                break // 连接正常断开，跳出循环
            } catch (e: Exception) {
                _isConnected = false
                client?.close() // 🌟 失败时立即释放网络资源
                Log.e(TAG, "WebSocket connection failed: ${e.message}")

                // 首次连接失败时，检查 Intiface 是否安装
                if (retryCount == 0) {
                    checkIntifaceInstalled()
                }

                val delayMs = getRetryDelay()
                Log.i(TAG, "Reconnecting in ${delayMs}ms (attempt ${retryCount + 1}/$maxRetries)")
                delay(delayMs)
                retryCount++
            }
        }

        if (retryCount >= maxRetries) {
            notifyUser("无法连接到 Intiface Central，已尝试 $maxRetries 次。\n请确认 Intiface Central 已安装并正在运行。")
        }
    }

    /**
     * 检查 Intiface Central 是否已安装
     */
    private fun checkIntifaceInstalled() {
        val pm = context.packageManager
        val intifacePackages = listOf(
            "com.nonpolynomial.intiface_mobile",
            "io.buttplug.intiface_central"
        )
        val installed = intifacePackages.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
        if (!installed) {
            notifyUser("未检测到 Intiface Central，请先安装并运行它，再启动本 App。")
        }
    }

    /**
     * 指数退避计算：1s → 2s → 4s → 8s → 16s（最大上限）
     */
    private fun getRetryDelay(): Long {
        val delay = (1000L * (2.0.pow(retryCount.toDouble()))).toLong()
        return min(delay, 16000L)
    }

    /**
     * 心跳机制（可选，用于检测连接活性）
     */
    private suspend fun startHeartbeat() {
        // Buttplug.io 协议支持 Ping 消息，这里简化实现
        // 实际生产环境可以发送 [{"Id":X,"Ping":{}}] 心跳包
    }

    /**
     * 处理服务器响应（JSON-RPC格式）
     */
    private fun handleResponse(text: String) {
        Log.d(TAG, "Received: $text")
        // 解析响应，处理 DeviceList、ServerInfo 等消息
        // 简化实现：仅记录日志，不做复杂处理
    }

    /**
     * 将 timing 数组转换为 BLE 安全的 ScalarCmd 指令序列
     *
     * timing 格式：[停顿ms, 震动ms, 停顿ms, 震动ms, ...]
     *
     * 🌟 关键修正：
     * - 引入 try-finally 与 NonCancellable 上下文
     * - 确保协程被 cancel 时玩具能绝对收到关闭信号，杜绝硬件锁死
     */
    suspend fun sendWaveform(timing: LongArray) {
        try {
            for ((index, ms) in timing.withIndex()) {
                // 偶数索引=停顿（强度0.0），奇数索引=震动（强度0.8）
                val intensity = if (index % 2 == 1) 0.8 else 0.0
                sendScalarCmd(intensity)
                // 等待该段时间，最小80ms保证BLE安全
                // delay() 天生可以敏锐响应协程的 cancel() 取消信号
                delay(ms.coerceAtLeast(80))
            }
        } finally {
            // 🌟 致命避坑点：当协程被 cancel 强掐时，整个上下文会进入取消状态
            // 此时必须强制切换到 NonCancellable 上下文，否则底层的 WebSocket 发送挂起命令会直接失效
            // 导致玩具死锁在最后的震动强度上轰炸用户的肉体！
            withContext(NonCancellable) {
                sendScalarCmd(0.0)
                Log.i(TAG, "Waveform interrupted or finished. Toy forced to stop (0.0).")
            }
        }
    }

    /**
     * 向 Intiface 发送标准 Buttplug.io JSON-RPC 格式的 ScalarCmd
     *
     * 协议格式：
     * [{"Id":N,"DeviceIndex":0,"Scalars":[{"Index":0,"Scalar":X,"ActuatorType":"Vibrate"}]}]
     */
    private suspend fun sendScalarCmd(intensity: Double, deviceIndex: Int = 0) {
        val message = buildString {
            append("""[{"Id":${++messageId},""")
            append(""""DeviceIndex":$deviceIndex,""")
            append(""""Scalars":[{"Index":0,"Scalar":$intensity,"ActuatorType":"Vibrate"}]}]""")
        }
        try {
            session?.send(Frame.Text(message))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ScalarCmd: ${e.message}")
            _isConnected = false
        }
    }

    /**
     * 用户通知（通过日志实现，生产环境可改为 Notification）
     */
    private fun notifyUser(message: String) {
        Log.w(TAG, "USER_NOTIFICATION: $message")
        // TODO: 改为 Android Notification 或 Toast
    }

    /**
     * 主动断开连接
     */
    suspend fun disconnect() {
        try {
            session?.close()
            client?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect: ${e.message}")
        } finally {
            _isConnected = false
        }
    }

    companion object {
        private const val TAG = "ButtplugWS"
    }
}
