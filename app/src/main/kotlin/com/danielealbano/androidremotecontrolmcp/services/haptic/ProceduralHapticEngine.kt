package com.danielealbano.androidremotecontrolmcp.services.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * 程序化触觉引擎
 *
 * 核心功能：
 * - 从 HapticPatternLibrary 查询预设模式
 * - 应用微随机噪点（±15%）打破固定循环，避免感官适应
 * - 支持三种目标：phone（本地马达）、toy（外部玩具）、all（同时触发）
 * - 使用互斥锁控制：新命令瞬间掐断上一个未完成的震动循环
 *
 * 关键设计：
 * - 引擎内部维护全局强度档位（1-5），API不接收intensity参数
 * - 震动循环运行在独立协程作用域，避免阻塞HTTP请求
 * - 支持强制覆盖：activeHapticJob.cancelAndJoin() 确保物理通道独占
 */
class ProceduralHapticEngine(
    private val context: Context,
    private val buttplugClient: ButtplugWebSocketClient
) {
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // 独立的协程作用域与互斥锁，用来管理常驻后台的硬件作业
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val hapticMutex = Mutex()
    private var activeHapticJob: Job? = null

    /**
     * 全局强度档位（1-5），默认中档
     * 用户在 App 设置中修改，API 不接收 intensity 参数
     */
    var currentLevel: Int = 3

    /**
     * 触发指定 mode 的震动，并应用微随机噪点
     *
     * 🌟 核心互斥锁机制：
     * - 确保同一时间只有一个震动任务在写入物理通道
     * - 如果上一次 AI 触发的震动循环（包含 delay）还没走完，直接强行掐断并等待它清理马达完毕
     *
     * @param mode 模式名称（支持"模式1"、"mode_1"等）
     * @param target 目标设备（"phone"、"toy"、"all"）
     * @return true=触发成功，false=mode不存在
     */
    suspend fun trigger(mode: String, target: String): Boolean {
        val baseTiming = HapticPatternLibrary.get(mode, currentLevel)
        if (baseTiming == null) {
            Log.w(TAG, "Unknown mode: $mode — ignored, waiting for next valid mode")
            return false
        }

        // 🌟 核心互斥锁：确保同一时间只有一个震动任务在写入物理通道
        hapticMutex.withLock {
            // 如果上一次 AI 触发的震动循环（包含 delay）还没走完，直接强行掐断并等待它清理马达完毕
            activeHapticJob?.cancelAndJoin()

            // 异步启动全新的物理震动流，让 Ktor 请求可以零延迟非阻塞返回
            activeHapticJob = engineScope.launch {
                try {
                    val randomizedTiming = applyMicroRandomization(baseTiming)
                    when (target.lowercase()) {
                        "phone" -> triggerPhone(randomizedTiming)
                        "toy" -> triggerToy(randomizedTiming)
                        "all" -> {
                            triggerPhone(randomizedTiming)
                            // 玩具独立随机：再次应用随机噪点，避免与手机完全同步
                            triggerToy(applyMicroRandomization(baseTiming))
                        }
                        else -> {
                            Log.w(TAG, "Unknown target: $target, defaulting to 'all'")
                            triggerPhone(randomizedTiming)
                            triggerToy(applyMicroRandomization(baseTiming))
                        }
                    }
                } finally {
                    // 🌟 兜底保障：当这个 Job 被 cancel 强行掐断时，确保手机本地马达立即归零熄火
                    vibrator.cancel()
                }
            }
        }
        return true
    }

    /**
     * 微随机解构算法：对时序数组的每个值注入 ±15% 随机抖动
     *
     * 用途：打破固定循环，避免感官适应（人体 2-3 分钟内就会适应固定模式）
     * 关键：总周期保持接近原始值，基本节奏感不变
     */
    private fun applyMicroRandomization(timing: LongArray): LongArray {
        return timing.map { ms ->
            val factor = 1.0 + Random.nextDouble(-0.15, 0.15)
            (ms * factor).toLong().coerceAtLeast(10)
        }.toLongArray()
    }

    /**
     * 触发手机本地马达
     */
    private fun triggerPhone(timing: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(timing, -1) // -1 = 不重复
            vibrator.vibrate(effect)
        } else {
            Log.w(TAG, "VibrationEffect requires API 26+, current: ${Build.VERSION.SDK_INT}")
        }
    }

    /**
     * 触发外部玩具：通过 WebSocket 向 Intiface 发送 ScalarCmd
     *
     * 限制：10-12Hz（每次 sendScalarCmd 间隔至少 80ms）
     */
    private suspend fun triggerToy(timing: LongArray) {
        if (buttplugClient.isConnected()) {
            buttplugClient.sendWaveform(timing)
        } else {
            Log.w(TAG, "Buttplug client not connected, skipping toy trigger")
        }
    }

    /**
     * 停止所有震动（紧急停止）
     */
    suspend fun stopAll() {
        hapticMutex.withLock {
            activeHapticJob?.cancelAndJoin()
            vibrator.cancel()
            if (buttplugClient.isConnected()) {
                // 发送强度 0.0 停止外部玩具
                buttplugClient.sendWaveform(longArrayOf(0, 10)) // 空震动
            }
        }
    }

    companion object {
        private const val TAG = "HapticEngine"
    }
}
