package com.danielealbano.androidremotecontrolmcp.services.pulselink

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

class ProceduralHapticEngine(
    context: Context,
    private val buttplugClient: ButtplugWebSocketClient,
    private val gamepadController: GamepadHapticController = GamepadHapticController(),
) {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            PulseLogger.e("Haptic task failed: ${throwable.message}", throwable)
        }
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)
    private val hapticMutex = Mutex()
    private var activeHapticJob: Job? = null
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    private val phoneVibrationAttributes =
        VibrationAttributes
            .Builder()
            .setUsage(VibrationAttributes.USAGE_TOUCH)
            .build()

    @Volatile
    var defaultLevel: Int = 3

    init {
        PulseLogger.i(
            "Phone vibrator ready: hasVibrator=${vibrator.hasVibrator()} " +
                "amplitudeControl=${vibrator.hasAmplitudeControl()}",
        )
        refreshGamepads()
    }

    suspend fun trigger(
        mode: String,
        level: Int?,
        randomize: Boolean,
        target: String,
    ): Boolean {
        val effectiveLevel = (level ?: defaultLevel).coerceIn(MIN_LEVEL, MAX_LEVEL)
        val targets = normalizeTargets(target)
        if (targets.isEmpty()) {
            PulseLogger.w("Unknown haptic target '$target'; command ignored")
            return false
        }
        if (HapticPatternLibrary.isContinuous(mode)) {
            hapticMutex.withLock {
                activeHapticJob?.cancelAndJoin()
                activeHapticJob =
                    engineScope.launch {
                        runContinuous(mode, effectiveLevel, targets)
                    }
            }
            return true
        }

        val baseTiming =
            HapticPatternLibrary.get(mode, effectiveLevel)
                ?: run {
                    PulseLogger.w("Unknown haptic mode: $mode")
                    return false
                }

        hapticMutex.withLock {
            activeHapticJob?.cancelAndJoin()
            activeHapticJob =
                engineScope.launch {
                    runPattern(mode, effectiveLevel, baseTiming, randomize, targets)
                }
        }
        return true
    }

    suspend fun emergencyStop() {
        hapticMutex.withLock {
            activeHapticJob?.cancelAndJoin()
            activeHapticJob = null
        }
        withContext(NonCancellable) {
            buttplugClient.sendScalarCmd(0.0)
            vibrator.cancel()
            gamepadController.cancel()
        }
        PulseLogger.i("Emergency stop executed")
    }

    fun close() {
        engineScope.cancel()
        vibrator.cancel()
        gamepadController.cancel()
    }

    fun gamepadStatus(): List<GamepadStatus> = gamepadController.scan()

    fun cachedGamepadStatus(): List<GamepadStatus> = gamepadController.cachedStatus()

    fun hasCachedGamepadVibrator(): Boolean = gamepadController.hasCachedAvailableVibrator()

    fun hasGamepadVibrator(): Boolean = gamepadController.hasAvailableVibrator()

    fun refreshGamepads(): List<GamepadStatus> {
        val gamepads = gamepadStatus()
        PulseLogger.i(
            "Gamepad refresh: count=${gamepads.size} " +
                "vibratorAvailable=${gamepads.any { it.vibratorAvailable }}",
        )
        return gamepads
    }

    private suspend fun runPattern(
        mode: String,
        effectiveLevel: Int,
        baseTiming: LongArray,
        randomize: Boolean,
        targets: List<String>,
    ) {
        try {
            supervisorScope {
                for (target in targets) {
                    when (target) {
                        TARGET_PHONE ->
                            launch {
                                triggerPhone(
                                    timing = if (randomize) applyMicroRandomization(baseTiming) else baseTiming,
                                    effectiveLevel = effectiveLevel,
                                )
                            }
                        TARGET_TOY -> launch { triggerToy(if (randomize) applyMicroRandomization(baseTiming) else baseTiming) }
                        TARGET_GAMEPAD ->
                            launch {
                                gamepadController.vibrate(
                                    timing = if (randomize) applyMicroRandomization(baseTiming) else baseTiming,
                                    effectiveLevel = effectiveLevel,
                                )
                            }
                    }
                }
            }
            PulseLogger.i("Triggered haptic mode=$mode level=$effectiveLevel randomize=$randomize targets=${targets.joinToString(",")}")
        } finally {
            vibrator.cancel()
        }
    }

    private suspend fun runContinuous(
        mode: String,
        effectiveLevel: Int,
        targets: List<String>,
    ) {
        PulseLogger.i("Triggered continuous haptic mode=$mode level=$effectiveLevel targets=${targets.joinToString(",")}")
        try {
            supervisorScope {
                for (target in targets) {
                    when (target) {
                        TARGET_PHONE -> launch { triggerPhoneContinuous(effectiveLevel) }
                        TARGET_TOY -> launch { triggerToyContinuous() }
                        TARGET_GAMEPAD -> launch { gamepadController.vibrateContinuous(effectiveLevel) }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                buttplugClient.sendScalarCmd(0.0)
                vibrator.cancel()
                gamepadController.cancel()
            }
            PulseLogger.i("Continuous haptic stopped mode=$mode targets=${targets.joinToString(",")}")
        }
    }

    private suspend fun triggerPhone(
        timing: LongArray,
        effectiveLevel: Int,
    ) {
        val amplitude = phoneAmplitude(effectiveLevel)
        val effect = VibrationEffect.createWaveform(timing, phoneAmplitudes(timing.size, amplitude), NO_REPEAT)
        if (!vibrator.hasVibrator()) {
            PulseLogger.w("Phone vibrator unavailable; skipped local vibration")
            delay(timing.sum().coerceAtLeast(0L))
            return
        }
        vibrator.vibrate(effect, phoneVibrationAttributes)
        PulseLogger.d("Phone vibration requested segments=${timing.size} amplitude=$amplitude")
        delay(timing.sum().coerceAtLeast(0L))
    }

    private suspend fun triggerToy(timing: LongArray) {
        buttplugClient.sendWaveform(timing)
    }

    private suspend fun triggerPhoneContinuous(effectiveLevel: Int) {
        val amplitude = phoneAmplitude(effectiveLevel)
        val effect =
            VibrationEffect.createWaveform(
                CONTINUOUS_TIMING,
                phoneAmplitudes(CONTINUOUS_TIMING.size, amplitude),
                CONTINUOUS_REPEAT_INDEX,
            )
        if (!vibrator.hasVibrator()) {
            PulseLogger.w("Phone vibrator unavailable; skipped continuous local vibration")
            awaitCancellation()
        }
        vibrator.vibrate(effect, phoneVibrationAttributes)
        PulseLogger.d("Continuous phone vibration requested amplitude=$amplitude")
        awaitCancellation()
    }

    private suspend fun triggerToyContinuous() {
        try {
            while (true) {
                buttplugClient.sendScalarCmd(DEFAULT_TOY_CONTINUOUS_INTENSITY)
                delay(CONTINUOUS_TOY_REFRESH_MS)
            }
        } finally {
            withContext(NonCancellable) {
                buttplugClient.sendScalarCmd(0.0)
            }
        }
    }

    private fun applyMicroRandomization(baseTiming: LongArray): LongArray =
        baseTiming
            .map { ms ->
                if (ms <= 0L) {
                    ms
                } else {
                    val factor = 1.0 + Random.nextDouble(-RANDOMIZATION_FACTOR, RANDOMIZATION_FACTOR)
                    (ms * factor).toLong().coerceAtLeast(MIN_RANDOMIZED_SEGMENT_MS)
                }
            }.toLongArray()

    private fun phoneAmplitudes(
        segmentCount: Int,
        amplitude: Int,
    ): IntArray =
        IntArray(segmentCount) { index ->
            if (index % 2 == 0) 0 else amplitude
        }

    private fun phoneAmplitude(effectiveLevel: Int): Int =
        when (effectiveLevel.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
            1 -> 160
            2 -> 210
            3 -> 255
            4 -> 255
            else -> 255
        }

    private fun normalizeTargets(rawTarget: String): List<String> =
        rawTarget
            .split(",", " ", ";")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .flatMap { target ->
                when (target) {
                    TARGET_ALL -> listOf(TARGET_PHONE, TARGET_TOY, TARGET_GAMEPAD)
                    TARGET_PHONE, "local" -> listOf(TARGET_PHONE)
                    TARGET_TOY, "egg", "fleshlight" -> listOf(TARGET_TOY)
                    TARGET_GAMEPAD, TARGET_CONTROLLER -> listOf(TARGET_GAMEPAD)
                    else -> emptyList()
                }
            }
            .distinct()

    private companion object {
        private const val TARGET_PHONE = "phone"
        private const val TARGET_TOY = "toy"
        private const val TARGET_GAMEPAD = "gamepad"
        private const val TARGET_CONTROLLER = "controller"
        private const val TARGET_ALL = "all"
        private const val MIN_LEVEL = 1
        private const val MAX_LEVEL = 5
        private const val NO_REPEAT = -1
        private const val CONTINUOUS_REPEAT_INDEX = 0
        private const val RANDOMIZATION_FACTOR = 0.15
        private const val MIN_RANDOMIZED_SEGMENT_MS = 10L
        private const val DEFAULT_TOY_CONTINUOUS_INTENSITY = 0.8
        private const val CONTINUOUS_TOY_REFRESH_MS = 1_000L
        private val CONTINUOUS_TIMING = longArrayOf(0L, 1_000L)
    }
}
