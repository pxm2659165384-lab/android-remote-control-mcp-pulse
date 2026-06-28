package com.danielealbano.androidremotecontrolmcp.mcp.oauth

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OAuthApprovalCoordinatorImpl")
class OAuthApprovalCoordinatorImplTest {
    private fun newCoordinator() = OAuthApprovalCoordinatorImpl()

    @Test
    @DisplayName("createPending appears in pending flow with 2-digit code")
    fun createPendingAppears() =
        runTest {
            val coordinator = newCoordinator()
            val approval = coordinator.createPending("Claude", "claude.ai", null, 0L)
            assertEquals(2, approval.matchCode.length)
            assertTrue(coordinator.observePending().value.any { it.id == approval.id })
        }

    @Test
    @DisplayName("createPending carries logoUri onto the pending approval")
    fun createPendingCarriesLogoUri() =
        runTest {
            val coordinator = newCoordinator()
            val approval = coordinator.createPending("Claude", "claude.ai", "https://cdn.example/logo.png", 0L)
            assertEquals("https://cdn.example/logo.png", approval.logoUri)
        }

    @Test
    @DisplayName("approve transitions and clears pending")
    fun approveTransitions() =
        runTest {
            val coordinator = newCoordinator()
            val approval = coordinator.createPending("Claude", "claude.ai", null, 0L)
            coordinator.approve(approval.id, 1L)
            assertEquals(ApprovalState.APPROVED, coordinator.stateOf(approval.id, 1L))
            assertTrue(coordinator.observePending().value.none { it.id == approval.id })
        }

    @Test
    @DisplayName("approve after the window has lapsed yields EXPIRED, not APPROVED")
    fun approveAfterExpiryYieldsExpired() =
        runTest {
            val coordinator = newCoordinator()
            val approval = coordinator.createPending("Claude", "claude.ai", null, 0L)
            coordinator.approve(approval.id, OAuthPolicy.APPROVAL_WINDOW_MS + 1)
            assertEquals(ApprovalState.EXPIRED, coordinator.stateOf(approval.id, OAuthPolicy.APPROVAL_WINDOW_MS + 1))
        }

    @Test
    @DisplayName("deny transitions to DENIED")
    fun denyTransitions() =
        runTest {
            val coordinator = newCoordinator()
            val approval = coordinator.createPending("Claude", "claude.ai", null, 0L)
            coordinator.deny(approval.id)
            assertEquals(ApprovalState.DENIED, coordinator.stateOf(approval.id, 1L))
            assertTrue(coordinator.observePending().value.none { it.id == approval.id })
        }

    @Test
    @DisplayName("expiry yields EXPIRED")
    fun expiryYieldsExpired() =
        runTest {
            val coordinator = newCoordinator()
            val approval = coordinator.createPending("Claude", "claude.ai", null, 0L)
            assertEquals(ApprovalState.EXPIRED, coordinator.stateOf(approval.id, OAuthPolicy.APPROVAL_WINDOW_MS + 1))
            assertTrue(coordinator.observePending().value.none { it.id == approval.id })
        }
}
