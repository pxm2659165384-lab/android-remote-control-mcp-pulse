package com.danielealbano.androidremotecontrolmcp.mcp.oauth

import kotlinx.coroutines.flow.StateFlow

/** Lifecycle state of an on-device authorization approval. */
enum class ApprovalState { PENDING, APPROVED, DENIED, EXPIRED }

/**
 * A pending on-device approval surfaced to the user.
 *
 * @property id High-entropy, unguessable token (the `/authorize/status` poll key).
 * @property clientName Resolved display name (never null).
 * @property redirectHost Host of the client's redirect URI (display).
 * @property matchCode 2-digit confirmation code shown in the browser and the app (unique among pending).
 * @property expiresAtMs Approval window deadline.
 */
data class PendingApproval(
    val id: String,
    val clientName: String,
    val redirectHost: String,
    val matchCode: String,
    val expiresAtMs: Long,
)

/**
 * Coordinates the number-match on-device approval: `/authorize` registers a pending request with a
 * 2-digit code, the user approves/denies in-app, and `/authorize/status` polls the resulting state.
 */
interface OAuthApprovalCoordinator {
    /** The currently-PENDING approvals (drives the in-app screen + notification). */
    fun observePending(): StateFlow<List<PendingApproval>>

    suspend fun createPending(
        clientName: String,
        redirectHost: String,
        nowMs: Long,
    ): PendingApproval

    suspend fun approve(id: String)

    suspend fun deny(id: String)

    suspend fun stateOf(
        id: String,
        nowMs: Long,
    ): ApprovalState
}
