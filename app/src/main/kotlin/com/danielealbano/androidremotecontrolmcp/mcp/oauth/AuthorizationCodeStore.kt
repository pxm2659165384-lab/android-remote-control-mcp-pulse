package com.danielealbano.androidremotecontrolmcp.mcp.oauth

/** A pending authorization code (RFC 6749 §4.1), single-use and short-lived. */
data class AuthorizationCode(
    val code: String,
    val clientId: String,
    val redirectUri: String,
    val codeChallenge: String,
    val resource: String,
    val scope: String,
    val expiresAtMs: Long,
)

/**
 * Single-use, TTL-bounded store of authorization codes minted at `/authorize` approval and redeemed
 * once at `/token`.
 */
interface AuthorizationCodeStore {
    /** Creates and stores a code, returning the opaque code value. */
    suspend fun create(
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        resource: String,
        scope: String,
        nowMs: Long,
    ): String

    /** Consumes the code (removed on read); returns null if missing or expired. */
    suspend fun consume(
        code: String,
        nowMs: Long,
    ): AuthorizationCode?
}
