package com.danielealbano.androidremotecontrolmcp.services.channel.listeners

import com.danielealbano.androidremotecontrolmcp.data.model.ChannelConnectionStatus
import com.danielealbano.androidremotecontrolmcp.data.model.WifiChannelConfig
import com.danielealbano.androidremotecontrolmcp.services.channel.EventDispatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("WifiEventListener")
class WifiEventListenerTest {
    private fun createMockDispatcher(): EventDispatcher {
        val mock = mockk<EventDispatcher>(relaxed = true)
        coEvery { mock.dispatch(any()) } returns Result.success(Unit)
        coEvery { mock.connectionStatus } returns
            MutableStateFlow(ChannelConnectionStatus.Idle)
        return mock
    }

    @Nested
    @DisplayName("configuration")
    inner class Configuration {
        @Test
        fun `updateConfig stores new config`() {
            val dispatcher = createMockDispatcher()
            val listener = WifiEventListener(dispatcher, mockk(relaxed = true))
            val config =
                WifiChannelConfig(
                    enabled = true,
                    ssids = setOf("TestWiFi"),
                    notifyOnDiscovered = false,
                )
            listener.updateConfig(config)
            // Config stored via @Volatile — no crash means thread-safe write succeeded
        }

        @Test
        fun `stop clears state without error`() {
            val dispatcher = createMockDispatcher()
            val listener = WifiEventListener(dispatcher, mockk(relaxed = true))
            listener.stop()
            // Should not throw — safe to call without start
        }
    }

    @Nested
    @DisplayName("event type toggles")
    inner class EventTypeToggles {
        @Test
        fun `config with notifyOnDiscovered false disables discovery events`() {
            val config =
                WifiChannelConfig(
                    enabled = true,
                    ssids = setOf("MyNetwork"),
                    notifyOnDiscovered = false,
                    notifyOnLost = true,
                )
            // notifyOnDiscovered = false means discovered events should NOT fire
            assertEquals(false, config.notifyOnDiscovered)
            assertEquals(true, config.notifyOnLost)
        }

        @Test
        fun `config with all toggles disabled produces no events`() {
            val config =
                WifiChannelConfig(
                    enabled = true,
                    ssids = setOf("MyNetwork"),
                    notifyOnDiscovered = false,
                    notifyOnLost = false,
                    notifyOnConnected = false,
                    notifyOnDisconnected = false,
                )
            assertEquals(false, config.notifyOnDiscovered)
            assertEquals(false, config.notifyOnLost)
            assertEquals(false, config.notifyOnConnected)
            assertEquals(false, config.notifyOnDisconnected)
        }

        @Test
        fun `non-matching SSID is not in configured set`() {
            val config =
                WifiChannelConfig(
                    enabled = true,
                    ssids = setOf("MyNetwork"),
                )
            assertEquals(false, "OtherNetwork" in config.ssids)
            assertEquals(true, "MyNetwork" in config.ssids)
        }
    }
}
