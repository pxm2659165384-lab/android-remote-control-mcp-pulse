package com.danielealbano.androidremotecontrolmcp.services.pulselink

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class PulseServiceState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR,
}

data class PulseRuntimeStatus(
    val state: PulseServiceState = PulseServiceState.STOPPED,
    val host: String = "0.0.0.0",
    val port: Int = HapticMiddlewareService.DEFAULT_PORT,
    val localIp: String = "127.0.0.1",
    val error: String? = null,
)

@Serializable
data class CommandResponse(
    val success: Boolean,
    val error: String? = null,
    val target: String? = null,
    @SerialName("gamepad_connected")
    val gamepadConnected: Boolean? = null,
    @SerialName("gamepad_vibrator_available")
    val gamepadVibratorAvailable: Boolean? = null,
)

@Serializable
data class StatusResponse(
    val success: Boolean,
    @SerialName("ktor_server")
    val ktorServer: String,
    val host: String,
    val port: Int,
    @SerialName("local_ip")
    val localIp: String,
    @SerialName("buttplug_connected")
    val buttplugConnected: Boolean,
    @SerialName("gadgetbridge_ok")
    val gadgetbridgeOk: Boolean,
    @SerialName("current_default_level")
    val currentDefaultLevel: Int,
    @SerialName("matrix_mode")
    val matrixMode: String,
    @SerialName("relay_nodes")
    val relayNodes: Int,
    @SerialName("media_configured")
    val mediaConfigured: Boolean,
    @SerialName("android_api")
    val androidApi: Int,
    @SerialName("gamepad_connected")
    val gamepadConnected: Boolean,
    @SerialName("gamepad_count")
    val gamepadCount: Int,
    @SerialName("gamepad_vibrator_available")
    val gamepadVibratorAvailable: Boolean,
    val gamepads: List<GamepadStatus>,
)

@Serializable
data class MatrixConfigResponse(
    val success: Boolean,
    @SerialName("matrix_mode")
    val matrixMode: String,
    @SerialName("relay_nodes")
    val relayNodes: Int,
    @SerialName("local_ip")
    val localIp: String,
    val nodes: List<String>,
    val error: String? = null,
)
