package com.danielealbano.androidremotecontrolmcp.services.pulselink

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PulseLogEntry(
    val timestamp: Long,
    val level: Level,
    val message: String,
) {
    enum class Level {
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    val chineseSummary: String
        get() = translateMessage(message)
}

object PulseLogger {
    private const val TAG = "MCP:PulseLink"
    private const val MAX_ENTRIES = 500
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val lock = Any()
    private val buffer = ArrayDeque<PulseLogEntry>(MAX_ENTRIES)
    private val _entries = MutableStateFlow<List<PulseLogEntry>>(emptyList())

    val entries: StateFlow<List<PulseLogEntry>> = _entries.asStateFlow()

    fun d(message: String) = add(PulseLogEntry.Level.DEBUG, message, null)

    fun i(message: String) = add(PulseLogEntry.Level.INFO, message, null)

    fun w(message: String) = add(PulseLogEntry.Level.WARN, message, null)

    fun e(
        message: String,
        throwable: Throwable? = null,
    ) = add(PulseLogEntry.Level.ERROR, message, throwable)

    fun formattedEntries(): List<String> =
        entries.value.map { entry ->
            val time = dateFormat.format(Date(entry.timestamp))
            "$time ${entry.level.zhLabel()}: ${entry.chineseSummary} | ${entry.level.name}: ${entry.message}"
        }

    private fun add(
        level: PulseLogEntry.Level,
        message: String,
        throwable: Throwable?,
    ) {
        when (level) {
            PulseLogEntry.Level.DEBUG -> Log.d(TAG, message, throwable)
            PulseLogEntry.Level.INFO -> Log.i(TAG, message, throwable)
            PulseLogEntry.Level.WARN -> Log.w(TAG, message, throwable)
            PulseLogEntry.Level.ERROR -> Log.e(TAG, message, throwable)
        }

        synchronized(lock) {
            if (buffer.size >= MAX_ENTRIES) {
                buffer.removeFirst()
            }
            buffer.addLast(PulseLogEntry(System.currentTimeMillis(), level, message))
            _entries.value = buffer.toList()
        }
    }
}

private fun PulseLogEntry.Level.zhLabel(): String =
    when (this) {
        PulseLogEntry.Level.DEBUG -> "调试"
        PulseLogEntry.Level.INFO -> "信息"
        PulseLogEntry.Level.WARN -> "警告"
        PulseLogEntry.Level.ERROR -> "错误"
    }

private fun translateMessage(message: String): String {
    val rules =
        listOf(
            "Pulse Link service created" to "Pulse Link 服务已创建",
            "Starting Pulse Link middleware" to "正在启动 Pulse Link 中间件",
            "Pulse Link middleware running" to "Pulse Link 中间件运行中",
            "Pulse Link service destroyed" to "Pulse Link 服务已停止",
            "Pulse HTTP server listening" to "HTTP 服务正在监听",
            "Stopping Pulse HTTP server" to "正在停止 HTTP 服务",
            "Pulse HTTP request" to "收到 HTTP 请求",
            "/vibrate accepted" to "已接收震动请求",
            "Triggered haptic" to "已触发震动",
            "Triggered continuous haptic" to "已开始持续震动",
            "Continuous haptic stopped" to "持续震动已停止",
            "Emergency stop executed" to "已执行紧急停止",
            "Gamepad refresh" to "已刷新手柄状态",
            "Gamepad vibration requested" to "已请求手柄震动",
            "Continuous gamepad vibration requested" to "已请求手柄持续震动",
            "No Android gamepad detected" to "未检测到安卓手柄",
            "Intiface WebSocket connected" to "Intiface 已连接",
            "Intiface WebSocket disconnected" to "Intiface 已断开",
            "Gadgetbridge bridge disabled" to "Gadgetbridge 桥接已关闭",
            "Intiface WebSocket bridge disabled" to "Intiface 桥接已关闭",
            "Matrix mode set" to "设备路由模式已切换",
            "Matrix node configured" to "已配置路由节点",
            "Matrix nodes cleared" to "已清空路由节点",
            "Matrix fanout" to "正在分发到路由节点",
            "Matrix stop node" to "正在停止路由节点",
            "Phone vibrator ready" to "手机震动器已就绪",
            "Phone vibrator unavailable" to "手机震动器不可用",
            "Media cover preview unavailable" to "媒体封面预览不可用",
            "/playMedia launched" to "已打开媒体跳转",
            "/playMedia failed" to "媒体跳转失败",
            "Haptic dispatch timed out or failed" to "震动分发超时或失败",
            "Unknown haptic mode" to "未知震动模式",
            "Unknown haptic target" to "未知震动设备",
            "Audio focus released" to "音频焦点已释放",
        )
    return rules.firstOrNull { (needle, _) -> message.contains(needle, ignoreCase = true) }?.second ?: message
}
