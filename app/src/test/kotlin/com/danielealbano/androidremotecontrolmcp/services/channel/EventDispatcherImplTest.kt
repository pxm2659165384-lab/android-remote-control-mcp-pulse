package com.danielealbano.androidremotecontrolmcp.services.channel

import com.danielealbano.androidremotecontrolmcp.data.model.ChannelConnectionStatus
import com.danielealbano.androidremotecontrolmcp.data.model.ChannelEvent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("EventDispatcherImpl")
class EventDispatcherImplTest {
    private val testEvent =
        ChannelEvent(
            type = "notification",
            timestamp = "2026-04-08T12:00:00Z",
            data =
                buildJsonObject {
                    put("test", "data")
                },
        )

    @Nested
    @DisplayName("initial state")
    inner class InitialState {
        @Test
        fun `status starts as Idle`() {
            val dispatcher = EventDispatcherImpl()
            assertEquals(ChannelConnectionStatus.Idle, dispatcher.connectionStatus.value)
        }
    }

    @Nested
    @DisplayName("dispatch before start")
    inner class DispatchBeforeStart {
        @Test
        fun `dispatch before start returns failure`() =
            runTest {
                val dispatcher = EventDispatcherImpl()
                val result = dispatcher.dispatch(testEvent)
                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalStateException)
            }
    }

    @Nested
    @DisplayName("dispatch after stop")
    inner class DispatchAfterStop {
        @Test
        fun `dispatch after stop returns failure`() =
            runTest {
                val dispatcher = EventDispatcherImpl()
                dispatcher.start("http://localhost:9090", "test-token")
                dispatcher.stop()
                val result = dispatcher.dispatch(testEvent)
                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalStateException)
            }
    }

    @Nested
    @DisplayName("stop")
    inner class Stop {
        @Test
        fun `stop resets status to Idle`() {
            val dispatcher = EventDispatcherImpl()
            dispatcher.start("http://localhost:9090", "test-token")
            dispatcher.stop()
            assertEquals(ChannelConnectionStatus.Idle, dispatcher.connectionStatus.value)
        }
    }

    @Nested
    @DisplayName("dispatch with unreachable server")
    inner class DispatchUnreachable {
        @Test
        fun `dispatch updates status to Error on network failure`() =
            runTest {
                val dispatcher = EventDispatcherImpl()
                dispatcher.start("http://localhost:1", "test-token")
                val result = dispatcher.dispatch(testEvent)
                assertTrue(result.isFailure)
                assertTrue(dispatcher.connectionStatus.value is ChannelConnectionStatus.Error)
            }
    }
}
