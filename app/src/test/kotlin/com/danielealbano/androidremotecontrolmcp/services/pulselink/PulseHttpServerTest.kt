package com.danielealbano.androidremotecontrolmcp.services.pulselink

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class PulseHttpServerTest {
    @Test
    fun `afterSend runs after response body is flushed`() {
        val afterSendStarted = CountDownLatch(1)
        val port = freePort()
        val server =
            PulseHttpServer("127.0.0.1", port) {
                PulseHttpResponse(
                    body = """{"success":true}""",
                    afterSend = {
                        afterSendStarted.countDown()
                        Thread.sleep(750)
                    },
                )
            }

        try {
            server.start()

            var response = ""
            val elapsed =
                measureTimeMillis {
                    response = rawGet(port, "/vibrate?mode=mode_1")
                }

            assertTrue(response.contains("""{"success":true}"""))
            assertTrue(afterSendStarted.await(250, TimeUnit.MILLISECONDS))
            assertTrue(elapsed < 500, "response should not wait for afterSend to finish")
        } finally {
            server.stop()
        }
    }

    @Test
    fun `slow afterSend does not block later clients`() {
        val slowAfterSendStarted = CountDownLatch(1)
        val port = freePort()
        val server =
            PulseHttpServer("127.0.0.1", port) { request ->
                when (request.path) {
                    "/slow" ->
                        PulseHttpResponse(
                            body = "slow-ok",
                            afterSend = {
                                slowAfterSendStarted.countDown()
                                Thread.sleep(900)
                            },
                        )

                    else -> PulseHttpResponse(body = "fast-ok")
                }
            }

        try {
            server.start()
            val slowThread = Thread { rawGet(port, "/slow") }
            slowThread.start()
            assertTrue(slowAfterSendStarted.await(500, TimeUnit.MILLISECONDS))

            val elapsed =
                measureTimeMillis {
                    assertTrue(rawGet(port, "/fast").contains("fast-ok"))
                }

            assertTrue(elapsed < 500, "fast client should not wait for slow afterSend")
        } finally {
            server.stop()
        }
    }

    @Test
    fun `query parser decodes parameters`() {
        val port = freePort()
        val server =
            PulseHttpServer("127.0.0.1", port) { request ->
                assertEquals("/vibrate", request.path)
                assertEquals("mode 1", request.parameters["mode"])
                assertEquals("phone", request.parameters["target"])
                PulseHttpResponse(body = "ok")
            }

        try {
            server.start()
            assertTrue(rawGet(port, "/vibrate?mode=mode+1&target=phone").contains("ok"))
        } finally {
            server.stop()
        }
    }

    private fun freePort(): Int =
        ServerSocket(0).use { it.localPort }

    private fun rawGet(
        port: Int,
        path: String,
    ): String =
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 1_000
            socket.getOutputStream().write(
                "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray(),
            )
            socket.getOutputStream().flush()

            val input = socket.getInputStream()
            val buffer = ByteArray(4096)
            val response = StringBuilder()
            while (!response.contains("\r\n\r\n")) {
                val read = input.read(buffer)
                if (read < 0) break
                response.append(String(buffer, 0, read))
            }
            val headerText = response.toString().substringBefore("\r\n\r\n")
            val contentLength =
                headerText
                    .lineSequence()
                    .first { it.startsWith("Content-Length:", ignoreCase = true) }
                    .substringAfter(":")
                    .trim()
                    .toInt()
            while (response.toString().substringAfter("\r\n\r\n").length < contentLength) {
                val read = input.read(buffer)
                if (read < 0) break
                response.append(String(buffer, 0, read))
            }
            response.toString()
        }
}
