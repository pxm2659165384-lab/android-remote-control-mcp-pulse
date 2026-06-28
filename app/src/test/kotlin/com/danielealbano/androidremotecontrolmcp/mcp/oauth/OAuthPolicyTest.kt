package com.danielealbano.androidremotecontrolmcp.mcp.oauth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OAuthPolicy")
class OAuthPolicyTest {
    @Test
    @DisplayName("allows claude callback and localhost")
    fun allowsClaudeAndLocalhost() {
        assertTrue(OAuthPolicy.isAllowedRedirectUri(OAuthPolicy.CLAUDE_REDIRECT_URI))
        assertTrue(OAuthPolicy.isAllowedRedirectUri("http://localhost/callback"))
        assertTrue(OAuthPolicy.isAllowedRedirectUri("http://localhost:8080/cb"))
        assertTrue(OAuthPolicy.isAllowedRedirectUri("http://127.0.0.1:1234/cb"))
    }

    @Test
    @DisplayName("rejects other redirect uris")
    fun rejectsOther() {
        assertFalse(OAuthPolicy.isAllowedRedirectUri("https://evil.example/cb"))
        assertFalse(OAuthPolicy.isAllowedRedirectUri("not a uri"))
    }

    @Test
    @DisplayName("rejects deceptive localhost-like hosts")
    fun rejectsDeceptiveHosts() {
        assertFalse(OAuthPolicy.isAllowedRedirectUri("http://localhost.evil.com/cb"))
        assertFalse(OAuthPolicy.isAllowedRedirectUri("http://127.0.0.1.evil.com/cb"))
        assertFalse(OAuthPolicy.isAllowedRedirectUri("http://localhost@evil.com/cb"))
    }

    @Test
    @DisplayName("rejects other loopback and wildcard hosts")
    fun rejectsLoopbackAndWildcard() {
        assertFalse(OAuthPolicy.isAllowedRedirectUri("http://[::1]/cb"))
        assertFalse(OAuthPolicy.isAllowedRedirectUri("http://0.0.0.0/cb"))
        assertFalse(OAuthPolicy.isAllowedRedirectUri("http://127.0.0.2/cb"))
        assertFalse(OAuthPolicy.isAllowedRedirectUri("https://localhost/cb"))
    }

    @Test
    @DisplayName("resourceMatches is host-case and trailing-slash insensitive")
    fun resourceMatches() {
        assertTrue(OAuthPolicy.resourceMatches("HTTPS://Host/mcp/", "https://host/mcp"))
        assertTrue(OAuthPolicy.resourceMatches("https://host/mcp", "https://host/mcp"))
        assertFalse(OAuthPolicy.resourceMatches("https://host/other", "https://host/mcp"))
        assertFalse(OAuthPolicy.resourceMatches("", "https://host/mcp"))
    }
}
