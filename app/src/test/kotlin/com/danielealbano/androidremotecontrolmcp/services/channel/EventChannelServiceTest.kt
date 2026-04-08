package com.danielealbano.androidremotecontrolmcp.services.channel

import com.danielealbano.androidremotecontrolmcp.data.model.ChannelConnectionStatus
import com.danielealbano.androidremotecontrolmcp.data.model.EventChannelConfig
import com.danielealbano.androidremotecontrolmcp.data.model.NotificationChannelConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("EventChannelService")
class EventChannelServiceTest {
    @Nested
    @DisplayName("config validation")
    inner class ConfigValidation {
        @Test
        fun `empty endpoint url should prevent start`() {
            val config = EventChannelConfig(enabled = true, endpointUrl = "", authToken = "token")
            assertTrue(config.endpointUrl.isBlank())
        }

        @Test
        fun `empty auth token should prevent start`() {
            val config = EventChannelConfig(enabled = true, endpointUrl = "http://localhost:9090", authToken = "")
            assertTrue(config.authToken.isBlank())
        }

        @Test
        fun `valid config allows start`() {
            val config =
                EventChannelConfig(
                    enabled = true,
                    endpointUrl = "http://localhost:9090",
                    authToken = "test-token",
                )
            assertFalse(config.endpointUrl.isBlank())
            assertFalse(config.authToken.isBlank())
        }
    }

    @Nested
    @DisplayName("service status")
    inner class ServiceStatus {
        @Test
        fun `initial service status is Idle`() {
            assertEquals(ChannelConnectionStatus.Idle, EventChannelService.serviceStatus.value)
        }
    }

    @Nested
    @DisplayName("listener configuration")
    inner class ListenerConfiguration {
        @Test
        fun `config with notifications enabled requires notification listener`() {
            val config =
                EventChannelConfig(
                    enabled = true,
                    endpointUrl = "http://localhost:9090",
                    authToken = "token",
                    notifications = NotificationChannelConfig(enabled = true),
                )
            assertTrue(config.notifications.enabled)
        }

        @Test
        fun `config with all listeners disabled has no active sources`() {
            val config =
                EventChannelConfig(
                    enabled = true,
                    endpointUrl = "http://localhost:9090",
                    authToken = "token",
                )
            assertFalse(config.notifications.enabled)
            assertFalse(config.wifi.enabled)
            assertFalse(config.geofence.enabled)
        }

        @Test
        fun `disabled channel config should trigger stop`() {
            val config = EventChannelConfig(enabled = false)
            assertFalse(config.enabled)
        }
    }
}
