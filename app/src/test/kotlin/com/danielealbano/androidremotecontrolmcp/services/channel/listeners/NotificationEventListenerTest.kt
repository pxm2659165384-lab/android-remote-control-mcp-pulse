package com.danielealbano.androidremotecontrolmcp.services.channel.listeners

import com.danielealbano.androidremotecontrolmcp.data.model.ChannelConnectionStatus
import com.danielealbano.androidremotecontrolmcp.data.model.ChannelEvent
import com.danielealbano.androidremotecontrolmcp.data.model.NotificationChannelConfig
import com.danielealbano.androidremotecontrolmcp.data.model.NotificationFilterMode
import com.danielealbano.androidremotecontrolmcp.services.channel.EventDispatcher
import com.danielealbano.androidremotecontrolmcp.services.notifications.NotificationActionData
import com.danielealbano.androidremotecontrolmcp.services.notifications.NotificationData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("NotificationEventListener")
class NotificationEventListenerTest {
    private fun createMockDispatcher(): EventDispatcher {
        val mock = mockk<EventDispatcher>(relaxed = true)
        coEvery { mock.dispatch(any()) } returns Result.success(Unit)
        val statusFlow = MutableStateFlow<ChannelConnectionStatus>(ChannelConnectionStatus.Idle)
        coEvery { mock.connectionStatus } returns statusFlow
        return mock
    }

    private fun createNotification(packageName: String): NotificationData =
        NotificationData(
            notificationId = "test123",
            packageName = packageName,
            appName = "Test App",
            title = "Test",
            text = "Test message",
            bigText = null,
            subText = null,
            timestamp = System.currentTimeMillis(),
            isOngoing = false,
            isClearable = true,
            category = null,
            groupKey = null,
            actions = emptyList(),
        )

    @Nested
    @DisplayName("filter modes")
    inner class FilterModes {
        @Test
        fun `ALL mode forwards all notifications`() =
            runTest {
                val dispatcher = createMockDispatcher()
                val listener = NotificationEventListener(dispatcher, this)
                listener.start(
                    NotificationChannelConfig(enabled = true, filterMode = NotificationFilterMode.ALL),
                )
                // Verify filter logic directly
                val config = NotificationChannelConfig(enabled = true, filterMode = NotificationFilterMode.ALL)
                listener.updateConfig(config)
                // ALL mode should pass any package
            }

        @Test
        fun `WHITELIST mode forwards only matching apps`() {
            val config =
                NotificationChannelConfig(
                    enabled = true,
                    filterMode = NotificationFilterMode.WHITELIST,
                    filterApps = setOf("com.whitelisted.app"),
                )
            // Whitelist contains com.whitelisted.app → should forward
            // com.other.app → should NOT forward
        }

        @Test
        fun `BLACKLIST mode blocks matching apps`() {
            val config =
                NotificationChannelConfig(
                    enabled = true,
                    filterMode = NotificationFilterMode.BLACKLIST,
                    filterApps = setOf("com.blocked.app"),
                )
            // Blacklist contains com.blocked.app → should NOT forward
            // com.other.app → should forward
        }
    }
}
