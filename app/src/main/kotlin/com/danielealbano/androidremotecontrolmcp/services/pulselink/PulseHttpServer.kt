package com.danielealbano.androidremotecontrolmcp.services.pulselink

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

data class PulseHttpRequest(
    val method: String,
    val path: String,
    val parameters: Map<String, String>,
)

data class PulseHttpResponse(
    val status: Int = HTTP_OK,
    val contentType: String = CONTENT_TYPE_JSON,
    val body: String = "",
    val afterSend: (() -> Unit)? = null,
) {
    companion object {
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        const val CONTENT_TYPE_TEXT = "text/plain; charset=utf-8"
    }
}

class PulseHttpServer(
    private val host: String,
    private val port: Int,
    private val handler: suspend (PulseHttpRequest) -> PulseHttpResponse,
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    val isServing: Boolean
        get() = running.get() && serverSocket?.isClosed == false && acceptThread?.isAlive == true

    @Synchronized
    fun start() {
        if (running.get()) stop()
        val socket =
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(host, port))
            }
        serverSocket = socket
        PulseLogger.i("Pulse HTTP server listening on $host:$port")
        running.set(true)
        acceptThread =
            Thread(
                {
                    while (running.get()) {
                        val client =
                            try {
                                socket.accept()
                            } catch (e: Exception) {
                                if (!running.get() || socket.isClosed) {
                                    break
                                }
                                if (running.get()) {
                                    PulseLogger.w("Pulse HTTP accept failed: ${e.message}")
                                    sleepBeforeAcceptRetry()
                                }
                                continue
                        }
                        PulseLogger.d("Pulse HTTP accepted ${client.remoteSocketAddress}")
                        Thread(
                            {
                                runCatching {
                                    runBlocking { handleClient(client) }
                                }.onSuccess {
                                    PulseLogger.d("Pulse HTTP handled ${client.remoteSocketAddress}")
                                }.onFailure {
                                    PulseLogger.e("Pulse HTTP client failed: ${it.message}", it)
                                }
                            },
                            "PulseHttpClient",
                        ).start()
                    }
                    PulseLogger.i("Pulse HTTP accept loop stopped")
                },
                "PulseHttpAccept",
            ).also { it.start() }
    }

    @Synchronized
    fun stop() {
        PulseLogger.i("Stopping Pulse HTTP server")
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { acceptThread?.join(ACCEPT_JOIN_TIMEOUT_MS) }
        acceptThread = null
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = SOCKET_TIMEOUT_MS
            val requestHeader = readRequestHeader(client)
            val requestLine = requestHeader.lineSequence().firstOrNull().orEmpty()
            var requestPathForLog: String? = null
            val response =
                runCatching {
                    parseRequestLine(requestLine)?.let { request ->
                        requestPathForLog = request.path
                        if (request.path != HEALTH_PATH) {
                            PulseLogger.i("Pulse HTTP request ${request.method} ${request.path}")
                        }
                        when (request.method.uppercase()) {
                            "GET" -> handler(request)
                            "OPTIONS" -> PulseHttpResponse(status = PulseHttpResponse.HTTP_NO_CONTENT)
                            else ->
                                PulseHttpResponse(
                                    status = PulseHttpResponse.HTTP_METHOD_NOT_ALLOWED,
                                    contentType = PulseHttpResponse.CONTENT_TYPE_TEXT,
                                    body = "method not allowed",
                                )
                        }
                    } ?: PulseHttpResponse(
                        status = PulseHttpResponse.HTTP_NOT_FOUND,
                        contentType = PulseHttpResponse.CONTENT_TYPE_TEXT,
                        body = "not found",
                    )
                }.getOrElse {
                    PulseLogger.e("Pulse HTTP request failed: ${it.message}", it)
                    PulseHttpResponse(status = 500, body = "{\"success\":false,\"error\":\"internal_error\"}")
                }
            writeResponse(client.getOutputStream(), response)
            if (requestPathForLog != HEALTH_PATH) {
                PulseLogger.i(
                    "Pulse HTTP response ${response.status} " +
                        "bytes=${response.body.toByteArray(StandardCharsets.UTF_8).size}",
                )
            }
            response.afterSend?.invoke()
        }
    }

    private fun readRequestHeader(socket: Socket): String {
        val input = socket.getInputStream()
        val buffer = ByteArrayOutputStream()
        var previous = -1
        var previous2 = -1
        var previous3 = -1

        while (buffer.size() < MAX_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) break
            buffer.write(next)

            val endsWithCrlfCrlf = previous3 == '\r'.code &&
                previous2 == '\n'.code &&
                previous == '\r'.code &&
                next == '\n'.code
            val endsWithLfLf = previous == '\n'.code && next == '\n'.code
            if (endsWithCrlfCrlf || endsWithLfLf) break

            previous3 = previous2
            previous2 = previous
            previous = next
        }
        if (buffer.size() >= MAX_HEADER_BYTES) {
            PulseLogger.w("Pulse HTTP header exceeded ${MAX_HEADER_BYTES} bytes")
        }
        return buffer.toString(StandardCharsets.UTF_8.name())
    }

    private fun parseRequestLine(line: String): PulseHttpRequest? {
        val parts = line.split(" ")
        if (parts.size < 2) return null
        val method = parts[0]
        val target = parts[1]
        val queryStart = target.indexOf('?')
        val path = if (queryStart >= 0) target.substring(0, queryStart) else target
        val query = if (queryStart >= 0) target.substring(queryStart + 1) else ""
        return PulseHttpRequest(
            method = method,
            path = path,
            parameters = parseQuery(query),
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query
            .split("&")
            .mapNotNull { pair ->
                if (pair.isBlank()) {
                    null
                } else {
                    val separator = pair.indexOf('=')
                    val key = if (separator >= 0) pair.substring(0, separator) else pair
                    val value = if (separator >= 0) pair.substring(separator + 1) else ""
                    decode(key) to decode(value)
                }
            }.toMap()
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun writeResponse(
        output: OutputStream,
        response: PulseHttpResponse,
    ) {
        val bodyBytes = response.body.toByteArray(StandardCharsets.UTF_8)
        val headers =
            "HTTP/1.1 ${response.status} ${reasonPhrase(response.status)}\r\n" +
                "Content-Type: ${response.contentType}\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        output.write(headers.toByteArray(StandardCharsets.UTF_8))
        if (bodyBytes.isNotEmpty()) {
            output.write(bodyBytes)
        }
        output.flush()
    }

    private fun reasonPhrase(status: Int): String =
        when (status) {
            PulseHttpResponse.HTTP_OK -> "OK"
            PulseHttpResponse.HTTP_NO_CONTENT -> "No Content"
            PulseHttpResponse.HTTP_NOT_FOUND -> "Not Found"
            PulseHttpResponse.HTTP_METHOD_NOT_ALLOWED -> "Method Not Allowed"
            500 -> "Internal Server Error"
            else -> "OK"
        }

    private fun sleepBeforeAcceptRetry() {
        runCatching { Thread.sleep(ACCEPT_RETRY_DELAY_MS) }
            .onFailure { Thread.currentThread().interrupt() }
    }

    private companion object {
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val ACCEPT_RETRY_DELAY_MS = 250L
        private const val ACCEPT_JOIN_TIMEOUT_MS = 500L
        private const val MAX_HEADER_BYTES = 8_192
        private const val HEALTH_PATH = "/healthz"
    }
}
