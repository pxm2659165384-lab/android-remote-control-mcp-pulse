package com.danielealbano.androidremotecontrolmcp.services.pulselink

import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.view.InputDevice
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GamepadStatus(
    val id: Int,
    val name: String,
    val sources: List<String>,
    @SerialName("vibrator_ids")
    val vibratorIds: List<Int>,
    @SerialName("vibrator_available")
    val vibratorAvailable: Boolean,
)

class GamepadHapticController {
    @Volatile
    private var cachedStatuses: List<GamepadStatus> = emptyList()

    private val vibrationAttributes =
        VibrationAttributes
            .Builder()
            .setUsage(VibrationAttributes.USAGE_TOUCH)
            .build()

    fun cachedStatus(): List<GamepadStatus> = cachedStatuses

    fun hasCachedAvailableVibrator(): Boolean = cachedStatuses.any { it.vibratorAvailable }

    fun scan(): List<GamepadStatus> {
        val statuses =
            InputDevice
            .getDeviceIds()
            .asSequence()
            .mapNotNull { id -> InputDevice.getDevice(id) }
            .filter { it.isGamepadLike() }
            .map { device ->
                val vibratorIds = device.vibratorManager.vibratorIds.toList()
                GamepadStatus(
                    id = device.id,
                    name = device.name.orEmpty(),
                    sources = device.sourceLabels(),
                    vibratorIds = vibratorIds,
                    vibratorAvailable = vibratorIds.isNotEmpty(),
                )
            }.toList()
        cachedStatuses = statuses
        return statuses
    }

    fun hasAvailableVibrator(): Boolean = scan().any { it.vibratorAvailable }

    suspend fun vibrate(
        timing: LongArray,
        effectiveLevel: Int,
    ): Boolean {
        val devices = activeDevices()
        if (devices.isEmpty()) {
            logUnavailable()
            delay(timing.sum().coerceAtLeast(0L))
            return false
        }

        val amplitude = gamepadAmplitude(effectiveLevel)
        val effect = VibrationEffect.createWaveform(timing, amplitudes(timing.size, amplitude), NO_REPEAT)
        val combined = CombinedVibration.createParallel(effect)
        devices.forEach { device ->
            runCatching {
                device.vibratorManager.vibrate(combined, vibrationAttributes)
            }.onSuccess {
                PulseLogger.i("Gamepad vibration requested ${device.name} id=${device.id} amplitude=$amplitude")
            }.onFailure {
                PulseLogger.w("Gamepad vibration failed ${device.name} id=${device.id}: ${it.message}")
            }
        }
        delay(timing.sum().coerceAtLeast(0L))
        return true
    }

    suspend fun vibrateContinuous(effectiveLevel: Int): Boolean {
        val devices = activeDevices()
        if (devices.isEmpty()) {
            logUnavailable()
            return false
        }

        val amplitude = gamepadAmplitude(effectiveLevel)
        val effect =
            VibrationEffect.createWaveform(
                CONTINUOUS_TIMING,
                amplitudes(CONTINUOUS_TIMING.size, amplitude),
                CONTINUOUS_REPEAT_INDEX,
            )
        val combined = CombinedVibration.createParallel(effect)
        devices.forEach { device ->
            runCatching {
                device.vibratorManager.vibrate(combined, vibrationAttributes)
            }.onSuccess {
                PulseLogger.i("Continuous gamepad vibration requested ${device.name} id=${device.id} amplitude=$amplitude")
            }.onFailure {
                PulseLogger.w("Continuous gamepad vibration failed ${device.name} id=${device.id}: ${it.message}")
            }
        }
        awaitCancellation()
        return true
    }

    fun cancel() {
        activeDevices().forEach { device ->
            runCatching { device.vibratorManager.cancel() }
                .onFailure { PulseLogger.w("Gamepad cancel failed ${device.name} id=${device.id}: ${it.message}") }
        }
    }

    private fun activeDevices(): List<InputDevice> =
        InputDevice
            .getDeviceIds()
            .asSequence()
            .mapNotNull { id -> InputDevice.getDevice(id) }
            .filter { it.isGamepadLike() && it.vibratorManager.vibratorIds.isNotEmpty() }
            .toList()

    private fun logUnavailable() {
        val gamepads = scan()
        if (gamepads.isEmpty()) {
            PulseLogger.w("No Android gamepad detected; gamepad vibration skipped")
        } else {
            PulseLogger.w("Gamepad connected but Android exposes no vibrator; gamepad vibration skipped")
        }
    }

    private fun InputDevice.isGamepadLike(): Boolean =
        hasSource(InputDevice.SOURCE_GAMEPAD) || hasSource(InputDevice.SOURCE_JOYSTICK)

    private fun InputDevice.hasSource(source: Int): Boolean = sources and source == source

    private fun InputDevice.sourceLabels(): List<String> =
        buildList {
            if (hasSource(InputDevice.SOURCE_GAMEPAD)) add("GAMEPAD")
            if (hasSource(InputDevice.SOURCE_JOYSTICK)) add("JOYSTICK")
            if (hasSource(InputDevice.SOURCE_DPAD)) add("DPAD")
        }

    private fun amplitudes(
        segmentCount: Int,
        amplitude: Int,
    ): IntArray =
        IntArray(segmentCount) { index ->
            if (index % 2 == 0) 0 else amplitude
        }

    private fun gamepadAmplitude(effectiveLevel: Int): Int =
        when (effectiveLevel.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
            1 -> 160
            2 -> 210
            3 -> 255
            4 -> 255
            else -> 255
        }

    private companion object {
        private const val MIN_LEVEL = 1
        private const val MAX_LEVEL = 5
        private const val NO_REPEAT = -1
        private const val CONTINUOUS_REPEAT_INDEX = 0
        private val CONTINUOUS_TIMING = longArrayOf(0L, 1_000L)
    }
}
