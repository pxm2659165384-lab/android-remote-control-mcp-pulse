package com.danielealbano.androidremotecontrolmcp.ui.viewmodels

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.data.model.EventChannelConfig
import com.danielealbano.androidremotecontrolmcp.data.model.GeofenceZone
import com.danielealbano.androidremotecontrolmcp.data.model.NotificationFilterMode
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.services.apps.AppManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ChannelViewModel")
class ChannelViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val appManager = mockk<AppManager>(relaxed = true)
    private val appContext = mockk<Context>(relaxed = true)

    private val configFlow = MutableStateFlow(EventChannelConfig())

    private lateinit var viewModel: ChannelViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.eventChannelConfig } returns configFlow
        coEvery { settingsRepository.getEventChannelConfig() } answers { configFlow.value }
        viewModel = ChannelViewModel(settingsRepository, appManager, appContext, testDispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {
        @Test
        fun `initial config loaded from repository`() =
            runTest {
                advanceUntilIdle()
                assertEquals(EventChannelConfig(), viewModel.eventChannelConfig.value)
            }
    }

    @Nested
    @DisplayName("endpoint URL")
    inner class EndpointUrl {
        @Test
        fun `updateEndpointUrl validates and persists valid URL`() =
            runTest {
                every { settingsRepository.validateEndpointUrl("http://localhost:9090") } returns
                    Result.success("http://localhost:9090")

                viewModel.updateEndpointUrl("http://localhost:9090")
                advanceUntilIdle()

                assertNull(viewModel.endpointUrlError.value)
                coVerify { settingsRepository.updateEventChannelEndpointUrl("http://localhost:9090") }
            }

        @Test
        fun `updateEndpointUrl with empty string sets error`() =
            runTest {
                every { settingsRepository.validateEndpointUrl("") } returns
                    Result.failure(IllegalArgumentException("Endpoint URL cannot be empty"))

                viewModel.updateEndpointUrl("")
                advanceUntilIdle()

                assertNotNull(viewModel.endpointUrlError.value)
            }
    }

    @Nested
    @DisplayName("notification filter")
    inner class NotificationFilter {
        @Test
        fun `toggleNotificationFilterApp adds to set`() =
            runTest {
                configFlow.value =
                    EventChannelConfig(
                        notifications =
                            com.danielealbano.androidremotecontrolmcp.data.model.NotificationChannelConfig(
                                filterApps = setOf("com.existing.app"),
                            ),
                    )

                viewModel.toggleNotificationFilterApp("com.new.app", true)
                advanceUntilIdle()

                coVerify {
                    settingsRepository.updateNotificationFilterApps(
                        setOf("com.existing.app", "com.new.app"),
                    )
                }
            }

        @Test
        fun `toggleNotificationFilterApp removes from set`() =
            runTest {
                configFlow.value =
                    EventChannelConfig(
                        notifications =
                            com.danielealbano.androidremotecontrolmcp.data.model.NotificationChannelConfig(
                                filterApps = setOf("com.existing.app", "com.remove.app"),
                            ),
                    )

                viewModel.toggleNotificationFilterApp("com.remove.app", false)
                advanceUntilIdle()

                coVerify {
                    settingsRepository.updateNotificationFilterApps(setOf("com.existing.app"))
                }
            }
    }

    @Nested
    @DisplayName("wifi settings")
    inner class WifiSettings {
        @Test
        fun `addWifiSsid adds to set`() =
            runTest {
                configFlow.value = EventChannelConfig()

                viewModel.addWifiSsid("MyNetwork")
                advanceUntilIdle()

                coVerify { settingsRepository.updateWifiSsids(setOf("MyNetwork")) }
            }

        @Test
        fun `removeWifiSsid removes from set`() =
            runTest {
                configFlow.value =
                    EventChannelConfig(
                        wifi =
                            com.danielealbano.androidremotecontrolmcp.data.model.WifiChannelConfig(
                                ssids = setOf("Network1", "Network2"),
                            ),
                    )

                viewModel.removeWifiSsid("Network1")
                advanceUntilIdle()

                coVerify { settingsRepository.updateWifiSsids(setOf("Network2")) }
            }
    }

    @Nested
    @DisplayName("geofence settings")
    inner class GeofenceSettings {
        @Test
        fun `addGeofenceZone delegates to repository`() =
            runTest {
                val zone =
                    GeofenceZone(
                        id = "z1",
                        name = "Test",
                        latitude = 40.0,
                        longitude = -74.0,
                        radiusMeters = 200f,
                    )
                viewModel.addGeofenceZone(zone)
                advanceUntilIdle()

                coVerify { settingsRepository.addGeofenceZone(zone) }
            }
    }

    @Nested
    @DisplayName("installed apps")
    inner class InstalledApps {
        @Test
        fun `loadInstalledApps populates list sorted`() =
            runTest {
                val apps =
                    listOf(
                        com.danielealbano.androidremotecontrolmcp.data.model.AppInfo(
                            "com.b",
                            "Bravo",
                            "1.0",
                            1,
                            false,
                        ),
                        com.danielealbano.androidremotecontrolmcp.data.model.AppInfo(
                            "com.a",
                            "Alpha",
                            "1.0",
                            1,
                            false,
                        ),
                    )
                coEvery { appManager.listInstalledApps(any(), any()) } returns apps

                viewModel.loadInstalledApps()
                advanceUntilIdle()

                assertEquals("Alpha", viewModel.installedApps.value[0].name)
                assertEquals("Bravo", viewModel.installedApps.value[1].name)
            }
    }
}
