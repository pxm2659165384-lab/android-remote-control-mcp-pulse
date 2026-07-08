package com.danielealbano.androidremotecontrolmcp.services.pulselink

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.engine.cio.CIO
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ButtplugWebSocketClient(
    private val scope: CoroutineScope,
    private val url: String = DEFAULT_WS_URL,
) {
    private val sendMutex = Mutex()
    private var client: HttpClient? = null
    private var session: DefaultClientWebSocketSession? = null
    private var nextMessageId = 1
    private var retryCount = 0
    private val _isConnected = MutableStateFlow(false)

    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    suspend fun connectLoop() {
        while (scope.isActive) {
            try {
                client?.close()
                client = HttpClient(CIO) { install(WebSockets) }
                client?.webSocket(url) {
                    session = this
                    retryCount = 0
                    _isConnected.value = true
                    PulseLogger.i("Intiface WebSocket connected at $url")
                    sendHandshake()
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            handleFrame(frame.readText())
                        }
                    }
                }
            } catch (e: Exception) {
                _isConnected.value = false
                session = null
                client?.close()
                val delayMillis = retryDelay()
                PulseLogger.w("Intiface WebSocket disconnected; retry ${retryCount + 1} in ${delayMillis}ms: ${e.message}")
                delay(delayMillis)
                retryCount++
            }
        }
    }

    suspend fun sendScalarCmd(intensity: Double) {
        val normalized = intensity.coerceIn(0.0, 1.0)
        sendMessage(buildScalarCmdJson(normalized))
    }

    suspend fun sendWaveform(timing: LongArray) {
        try {
            for ((index, ms) in timing.withIndex()) {
                val intensity = if (index % 2 == 1) DEFAULT_VIBRATION_INTENSITY else 0.0
                sendScalarCmd(intensity)
                delay(ms.coerceAtLeast(MIN_SEGMENT_MS))
            }
        } finally {
            withContext(NonCancellable) {
                sendScalarCmd(0.0)
            }
        }
    }

    fun close() {
        _isConnected.value = false
        session = null
        client?.close()
        client = null
    }

    private suspend fun sendHandshake() {
        sendMessage(
            """
            [
              {
                "RequestServerInfo": {
                  "Id": ${nextId()},
                  "ClientName": "MCP Pulse Link",
                  "MessageVersion": 3
                }
              }
            ]
            """.trimIndent(),
        )
        sendMessage("""[{"StartScanning":{"Id":${nextId()}}}]""")
    }

    private suspend fun sendMessage(json: String) {
        sendMutex.withLock {
            session?.send(json) ?: PulseLogger.d("Intiface not connected; dropping command")
        }
    }

    private fun buildScalarCmdJson(intensity: Double): String =
        """
        [
          {
            "ScalarCmd": {
              "Id": ${nextId()},
              "DeviceIndex": 0,
              "Scalars": [
                {
                  "Index": 0,
                  "Scalar": $intensity,
                  "ActuatorType": "Vibrate"
                }
              ]
            }
          }
        ]
        """.trimIndent()

    private fun handleFrame(text: String) {
        if (text.contains("Error", ignoreCase = true)) {
            PulseLogger.w("Intiface response: $text")
        } else {
            PulseLogger.d("Intiface frame: $text")
        }
    }

    private fun nextId(): Int = nextMessageId++

    private fun retryDelay(): Long = 1000L * (1 shl retryCount.coerceAtMost(4))

    companion object {
        const val DEFAULT_WS_URL = "ws://127.0.0.1:12345"
        private const val DEFAULT_VIBRATION_INTENSITY = 0.8
        private const val MIN_SEGMENT_MS = 80L
    }
}
