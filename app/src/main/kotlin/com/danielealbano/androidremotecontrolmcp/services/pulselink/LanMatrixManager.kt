package com.danielealbano.androidremotecontrolmcp.services.pulselink

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import kotlin.math.roundToInt

@Serializable
data class RelayNode(
    val id: String = UUID.randomUUID().toString(),
    val ipAddress: String,
    val port: Int = HapticMiddlewareService.DEFAULT_PORT,
    val attenuation: Float = 1.0f,
    val enabled: Boolean = true,
    val label: String = "",
)

enum class MatrixMode {
    SLAVE,
    MASTER,
}

object LanMatrixManager {
    private val json = Json { ignoreUnknownKeys = true }

    var mode by mutableStateOf(MatrixMode.SLAVE)
        private set
    val nodes = mutableStateListOf<RelayNode>()

    fun load(context: Context) {
        val prefs = prefs(context)
        val savedMode = prefs.getString(KEY_MODE, MatrixMode.SLAVE.name).orEmpty()
        mode = runCatching { MatrixMode.valueOf(savedMode) }.getOrDefault(MatrixMode.SLAVE)
        val savedNodes = prefs.getString(KEY_NODES, null) ?: return
        runCatching {
            json.decodeFromString(ListSerializer(RelayNode.serializer()), savedNodes)
        }.onSuccess {
            nodes.clear()
            nodes.addAll(it)
        }.onFailure {
            PulseLogger.w("Failed to load matrix nodes: ${it.message}")
        }
    }

    fun save(context: Context) {
        prefs(context)
            .edit()
            .putString(KEY_MODE, mode.name)
            .putString(KEY_NODES, json.encodeToString(ListSerializer(RelayNode.serializer()), nodes.toList()))
            .apply()
    }

    fun setMode(
        context: Context,
        newMode: MatrixMode,
    ) {
        mode = newMode
        PulseLogger.i("Matrix mode set to $newMode")
        save(context)
    }

    fun addNode(
        context: Context,
        ipAddress: String,
        port: Int = HapticMiddlewareService.DEFAULT_PORT,
        attenuation: Float = 1.0f,
        label: String = "",
    ) {
        val cleanIp = ipAddress.trim()
        if (cleanIp.isBlank()) return
        val cleanLabel = label.trim()
        val existingIndex =
            nodes.indexOfFirst {
                it.ipAddress.equals(cleanIp, ignoreCase = true) && it.port == port
            }
        val node =
            RelayNode(
                ipAddress = cleanIp,
                port = port,
                attenuation = attenuation.coerceIn(0f, 1f),
                enabled = true,
                label = cleanLabel,
            )
        if (existingIndex >= 0) {
            nodes[existingIndex] =
                nodes[existingIndex].copy(
                    attenuation = node.attenuation,
                    enabled = true,
                    label = cleanLabel.ifBlank { nodes[existingIndex].label },
                )
        } else {
            nodes.add(node)
        }
        PulseLogger.i("Matrix node configured ${node.ipAddress}:${node.port} attenuation=${node.attenuation}")
        save(context)
    }

    fun clearNodes(context: Context) {
        nodes.clear()
        PulseLogger.i("Matrix nodes cleared")
        save(context)
    }

    fun hasRemoteEnabledNode(context: Context): Boolean =
        nodes.any { it.enabled && !it.isLocal(context) }

    fun removeNode(
        context: Context,
        id: String,
    ) {
        nodes.removeAll { it.id == id }
        save(context)
    }

    fun updateNode(
        context: Context,
        node: RelayNode,
    ) {
        val index = nodes.indexOfFirst { it.id == node.id }
        if (index >= 0) {
            nodes[index] = node.copy(attenuation = node.attenuation.coerceIn(0f, 1f))
            save(context)
        }
    }

    suspend fun masterFanOut(
        context: Context,
        hapticEngine: ProceduralHapticEngine,
        modeName: String,
        baseLevel: Int,
        randomize: Boolean,
        target: String,
        targets: List<String> = listOf(target),
    ) {
        val enabledNodes = nodes.filter { it.enabled }
        val targetsCsv = targets.joinToString(",")
        PulseLogger.i(
            "Matrix master local trigger mode=$modeName level=$baseLevel " +
                "randomize=$randomize targets=$targetsCsv remoteNodes=${enabledNodes.count { !it.isLocal(context) }}",
        )
        hapticEngine.trigger(modeName, baseLevel, randomize, targetsCsv)
        val remoteNodes = enabledNodes.filter { !it.isLocal(context) }
        if (remoteNodes.isEmpty()) {
            return
        }

        val completed =
            withTimeoutOrNull(NODE_FANOUT_TOTAL_TIMEOUT_MS) {
                supervisorScope {
                    remoteNodes.forEach { node ->
                        launch(Dispatchers.IO) {
                            fanOutNode(context, hapticEngine, node, modeName, baseLevel, randomize, target, targets)
                        }
                    }
                }
            }
        if (completed == null) {
            PulseLogger.w("Matrix fanout timed out after ${NODE_FANOUT_TOTAL_TIMEOUT_MS}ms")
        }
    }

    suspend fun stopRemoteNodes(context: Context) {
        val enabledNodes = nodes.filter { it.enabled && !it.isLocal(context) }
        if (enabledNodes.isEmpty()) return

        supervisorScope {
            enabledNodes.forEach { node ->
                launch(Dispatchers.IO) {
                    stopNode(node)
                }
            }
        }
    }

    fun localIpAddress(): String =
        runCatching {
            NetworkInterface
                .getNetworkInterfaces()
                .asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        }.getOrNull() ?: "127.0.0.1"

    private suspend fun fanOutNode(
        context: Context,
        hapticEngine: ProceduralHapticEngine,
        node: RelayNode,
        modeName: String,
        baseLevel: Int,
        randomize: Boolean,
        target: String,
        targets: List<String>,
    ) {
        try {
            val targetLevel = (baseLevel * node.attenuation).roundToInt().coerceIn(0, 5)
            if (targetLevel == 0) {
                PulseLogger.i("Matrix node ${node.ipAddress} muted by attenuation")
                return
            }

            if (node.isLocal(context)) {
                PulseLogger.i("Matrix node ${node.ipAddress} is local; triggering locally")
                hapticEngine.trigger(modeName, targetLevel, randomize, targets.joinToString(","))
                return
            }

            val targetsCsv = targets.joinToString(",")
            val url =
                "http://${node.httpHost()}:${node.port}/vibrate" +
                    "?mode=${Uri.encode(modeName)}" +
                    "&level=$targetLevel" +
                    "&randomize=$randomize" +
                    "&target=${Uri.encode(target)}" +
                    "&targets=${Uri.encode(targetsCsv)}"
            PulseLogger.i("Matrix fanout ${node.ipAddress}:${node.port} targets=$targetsCsv level=$targetLevel")
            HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = NODE_TIMEOUT_MS
                    connectTimeoutMillis = NODE_TIMEOUT_MS
                    socketTimeoutMillis = NODE_TIMEOUT_MS
                }
            }.use { client ->
                val response = client.get(url)
                PulseLogger.i("Matrix node ${node.ipAddress} responded ${response.status.value}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PulseLogger.w("Matrix node ${node.ipAddress} failed: ${e.message}")
        }
    }

    private suspend fun stopNode(node: RelayNode) {
        try {
            val url = "http://${node.httpHost()}:${node.port}/stop"
            HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = NODE_TIMEOUT_MS
                    connectTimeoutMillis = NODE_TIMEOUT_MS
                    socketTimeoutMillis = NODE_TIMEOUT_MS
                }
            }.use { client ->
                val response = client.get(url)
                PulseLogger.i("Matrix stop node ${node.ipAddress} responded ${response.status.value}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PulseLogger.w("Matrix stop node ${node.ipAddress} failed: ${e.message}")
        }
    }

    private fun RelayNode.isLocal(context: Context): Boolean {
        val host = ipAddress.trim().lowercase()
        if (host == ADB_REVERSE_HOST) return false
        val localIp = localIpAddress()
        return host.isBlank() ||
            host == "localhost" ||
            host == "127.0.0.1" ||
            host == localIp ||
            host == context.packageName
    }

    private fun RelayNode.httpHost(): String =
        if (ipAddress.trim().lowercase() == ADB_REVERSE_HOST) "127.0.0.1" else ipAddress.trim()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "pulse_link_matrix"
    private const val KEY_MODE = "matrix_mode"
    private const val KEY_NODES = "relay_nodes"
    private const val NODE_TIMEOUT_MS = 1500L
    private const val NODE_FANOUT_TOTAL_TIMEOUT_MS = 2_000L
    private const val ADB_REVERSE_HOST = "adb-reverse"
}
