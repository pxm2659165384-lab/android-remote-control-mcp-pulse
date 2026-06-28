package com.danielealbano.androidremotecontrolmcp.mcp.oauth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OAuth consent page")
class ConsentPageTest {
    @Test
    @DisplayName("escapes a malicious client name")
    fun escapesClientName() {
        val html = consentPageHtml("approval-id", "42", "<script>alert(1)</script>")
        assertFalse(html.contains("<script>alert(1)</script>"), "raw script must not appear")
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "name must be HTML-escaped")
    }

    @Test
    @DisplayName("shows the match code and has no approve control")
    fun showsCodeNoApprove() {
        val html = consentPageHtml("approval-id", "42", "Claude")
        assertTrue(html.contains("42"))
        assertFalse(html.contains("<button"), "page must not contain an approve control")
        assertFalse(html.contains("<form"), "page must not contain a submit form")
    }
}
