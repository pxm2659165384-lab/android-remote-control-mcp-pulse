package com.danielealbano.androidremotecontrolmcp.mcp.oauth

import java.net.URI

/**
 * SSRF guard for rendering a DCR `logo_uri`. Returns true only for an `https` URL whose host is not a
 * literal private / loopback / link-local IP and not `localhost`. The loader MUST additionally not
 * follow redirects and MUST cap size/timeout; this predicate is the host-allowlist part.
 */
object LogoUrlPolicy {
    fun isSafeLogoUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost") return false
        val literal = host.removePrefix("[").removeSuffix("]")
        if (isPrivateIpv4(literal) || isPrivateIpv6(literal)) return false
        return true
    }

    @Suppress("ReturnCount")
    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val a = octets[0]
        val b = octets[1]
        return when {
            a == 10 -> true // 10/8
            a == 127 -> true // 127/8 loopback
            a == 172 && b in 16..31 -> true // 172.16/12
            a == 192 && b == 168 -> true // 192.168/16
            a == 169 && b == 254 -> true // 169.254/16 link-local
            else -> false
        }
    }

    private fun isPrivateIpv6(host: String): Boolean {
        if (!host.contains(":")) return false
        val normalized = host.lowercase()
        return normalized == "::1" || // loopback
            normalized.startsWith("fc") || normalized.startsWith("fd") || // fc00::/7 ULA
            normalized.startsWith("fe8") || normalized.startsWith("fe9") ||
            normalized.startsWith("fea") || normalized.startsWith("feb") // fe80::/10 link-local
    }
}
