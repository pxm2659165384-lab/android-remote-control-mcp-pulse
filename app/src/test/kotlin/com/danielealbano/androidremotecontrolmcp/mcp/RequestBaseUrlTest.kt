package com.danielealbano.androidremotecontrolmcp.mcp

import io.ktor.http.RequestConnectionPoint
import io.ktor.http.headersOf
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RequestBaseUrl derivation")
class RequestBaseUrlTest {
    @BeforeEach
    fun setUp() {
        mockkStatic(
            "io.ktor.server.request.ApplicationRequestPropertiesKt",
            "io.ktor.server.plugins.OriginConnectionPointKt",
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(
            "io.ktor.server.request.ApplicationRequestPropertiesKt",
            "io.ktor.server.plugins.OriginConnectionPointKt",
        )
    }

    private fun mockCall(
        forwardedHost: String? = null,
        forwardedProto: String? = null,
        host: String = "listener.local",
        port: Int = 80,
        scheme: String = "http",
    ): ApplicationCall {
        val headerPairs = mutableListOf<Pair<String, List<String>>>()
        forwardedHost?.let { headerPairs += "X-Forwarded-Host" to listOf(it) }
        forwardedProto?.let { headerPairs += "X-Forwarded-Proto" to listOf(it) }
        val headers = headersOf(*headerPairs.toTypedArray())

        val request = mockk<ApplicationRequest>()
        every { request.headers } returns headers
        every { request.host() } returns host
        every { request.port() } returns port
        val connectionPoint = mockk<RequestConnectionPoint>()
        every { connectionPoint.scheme } returns scheme
        every { request.origin } returns connectionPoint

        val call = mockk<ApplicationCall>()
        every { call.request } returns request
        return call
    }

    @Test
    @DisplayName("uses X-Forwarded-Host and X-Forwarded-Proto when present")
    fun usesForwardedHeaders() {
        val call = mockCall(forwardedHost = "tunnel.example", forwardedProto = "https")
        assertEquals("https://tunnel.example", deriveBaseUrl(call))
    }

    @Test
    @DisplayName("falls back to Host header and origin scheme")
    fun fallsBackToHostAndScheme() {
        val call = mockCall(host = "listener.local", port = 8080, scheme = "http")
        assertEquals("http://listener.local:8080", deriveBaseUrl(call))
    }

    @Test
    @DisplayName("omits default port")
    fun omitsDefaultPort() {
        assertEquals(
            "https://tunnel.example",
            deriveBaseUrl(mockCall(forwardedHost = "tunnel.example:443", forwardedProto = "https")),
        )
        assertEquals(
            "http://tunnel.example",
            deriveBaseUrl(mockCall(forwardedHost = "tunnel.example:80", forwardedProto = "http")),
        )
        assertEquals(
            "https://tunnel.example:8443",
            deriveBaseUrl(mockCall(forwardedHost = "tunnel.example:8443", forwardedProto = "https")),
        )
    }

    @Test
    @DisplayName("lowercases scheme and host and strips trailing slash")
    fun normalizes() {
        assertEquals("https://example.com", normalizeBaseUrl("HTTPS://Example.COM/"))
        assertEquals("http://host.local:9090", normalizeBaseUrl("HTTP://Host.Local:9090"))
    }

    @Test
    @DisplayName("takes first value of comma-separated forwarded header")
    fun takesFirstForwardedValue() {
        val call = mockCall(forwardedHost = "a.example, b.example", forwardedProto = "https, http")
        assertEquals("https://a.example", deriveBaseUrl(call))
    }

    @Test
    @DisplayName("effectiveBaseUrl returns normalized override when set")
    fun effectiveOverrideWins() {
        val call = mockCall(forwardedHost = "tunnel.example", forwardedProto = "https")
        assertEquals("https://pinned.host", effectiveBaseUrl(call, "HTTPS://Pinned.Host/"))
    }

    @Test
    @DisplayName("effectiveBaseUrl falls back to derive when override blank")
    fun effectiveFallsBackWhenBlank() {
        val call = mockCall(forwardedHost = "tunnel.example", forwardedProto = "https")
        assertEquals(deriveBaseUrl(call), effectiveBaseUrl(call, "   "))
    }

    @Test
    @DisplayName("currentRequestBaseUrl returns element value, else fallback")
    fun currentRequestBaseUrlReadsElement() =
        runTest {
            val viaElement =
                withContext(RequestBaseUrlElement("https://from-element")) {
                    currentRequestBaseUrl { "https://fallback" }
                }
            assertEquals("https://from-element", viaElement)
            assertEquals("https://fallback", currentRequestBaseUrl { "https://fallback" })
        }

    @Test
    @DisplayName("context element survives an inner withContext(Dispatchers.IO)")
    fun elementSurvivesIoHop() =
        runTest {
            val value =
                withContext(RequestBaseUrlElement("https://from-element")) {
                    withContext(Dispatchers.IO) {
                        currentRequestBaseUrl { "https://fallback" }
                    }
                }
            assertEquals("https://from-element", value)
        }

    @Test
    @DisplayName("canonicalResource appends /mcp")
    fun canonicalResourceAppendsMcp() {
        assertEquals("https://host.example/mcp", canonicalResource("https://host.example"))
    }
}
