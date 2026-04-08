package com.danielealbano.androidremotecontrolmcp.ui.viewmodels

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielealbano.androidremotecontrolmcp.data.model.AppInfo
import com.danielealbano.androidremotecontrolmcp.data.model.ChannelConnectionStatus
import com.danielealbano.androidremotecontrolmcp.data.model.EventChannelConfig
import com.danielealbano.androidremotecontrolmcp.data.model.GeofenceZone
import com.danielealbano.androidremotecontrolmcp.data.model.NotificationFilterMode
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.di.IoDispatcher
import com.danielealbano.androidremotecontrolmcp.services.apps.AppManager
import com.danielealbano.androidremotecontrolmcp.services.channel.EventChannelService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val appManager: AppManager,
        @ApplicationContext private val appContext: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        val eventChannelConfig: StateFlow<EventChannelConfig> =
            settingsRepository.eventChannelConfig
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EventChannelConfig())

        val channelConnectionStatus: StateFlow<ChannelConnectionStatus> =
            EventChannelService.serviceStatus

        private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
        val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

        private val _endpointUrlInput = MutableStateFlow("")
        val endpointUrlInput: StateFlow<String> = _endpointUrlInput.asStateFlow()
        private val _endpointUrlError = MutableStateFlow<String?>(null)
        val endpointUrlError: StateFlow<String?> = _endpointUrlError.asStateFlow()
        private val _authTokenInput = MutableStateFlow("")
        val authTokenInput: StateFlow<String> = _authTokenInput.asStateFlow()

        init {
            viewModelScope.launch {
                eventChannelConfig.collect { config ->
                    _endpointUrlInput.value = config.endpointUrl
                    _authTokenInput.value = config.authToken
                }
            }
        }

        fun updateChannelEnabled(enabled: Boolean) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.updateEventChannelEnabled(enabled)
            }
            if (enabled) startChannelService() else stopChannelService()
        }

        fun updateEndpointUrl(url: String) {
            _endpointUrlInput.value = url
            val result = settingsRepository.validateEndpointUrl(url)
            if (result.isSuccess) {
                _endpointUrlError.value = null
                viewModelScope.launch(ioDispatcher) {
                    settingsRepository.updateEventChannelEndpointUrl(url)
                }
            } else {
                _endpointUrlError.value = result.exceptionOrNull()?.message
            }
        }

        fun generateNewAuthToken() {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.generateNewEventChannelAuthToken()
            }
        }

        // Notification settings
        fun updateNotificationChannelEnabled(enabled: Boolean) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.updateNotificationChannelEnabled(enabled)
            }
        }

        fun updateNotificationFilterMode(mode: NotificationFilterMode) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.updateNotificationFilterMode(mode)
            }
        }

        fun toggleNotificationFilterApp(
            packageName: String,
            included: Boolean,
        ) {
            viewModelScope.launch(ioDispatcher) {
                val current = settingsRepository.getEventChannelConfig().notifications.filterApps
                val updated = if (included) current + packageName else current - packageName
                settingsRepository.updateNotificationFilterApps(updated)
            }
        }

        // WiFi settings
        fun updateWifiChannelEnabled(enabled: Boolean) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.updateWifiChannelEnabled(enabled)
            }
        }

        fun addWifiSsid(ssid: String) {
            viewModelScope.launch(ioDispatcher) {
                val current = settingsRepository.getEventChannelConfig().wifi.ssids
                settingsRepository.updateWifiSsids(current + ssid)
            }
        }

        fun removeWifiSsid(ssid: String) {
            viewModelScope.launch(ioDispatcher) {
                val current = settingsRepository.getEventChannelConfig().wifi.ssids
                settingsRepository.updateWifiSsids(current - ssid)
            }
        }

        fun updateWifiNotifyOnDiscovered(enabled: Boolean) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.updateWifiNotifyOnDiscovered(enabled)
            }
        }

        fun updateWifiNotifyOnLost(enabled: Boolean) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.updateWifiNotifyOnLost(enabled)
            }
        }

        fun updateWifiNotifyOnConnected(enabled: Boolean) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.updateWifiNotifyOnConnected(enabled)
            }
        }

        fun updateWifiNotifyOnDisconnected(enabled: Boolean) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.updateWifiNotifyOnDisconnected(enabled)
            }
        }

        // Geofence settings
        fun updateGeofenceChannelEnabled(enabled: Boolean) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.updateGeofenceChannelEnabled(enabled)
            }
        }

        fun addGeofenceZone(zone: GeofenceZone) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.addGeofenceZone(zone)
            }
        }

        fun removeGeofenceZone(zoneId: String) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.removeGeofenceZone(zoneId)
            }
        }

        fun updateGeofenceZone(zone: GeofenceZone) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.updateGeofenceZone(zone)
            }
        }

        fun loadInstalledApps() {
            viewModelScope.launch(ioDispatcher) {
                val apps = appManager.listInstalledApps()
                _installedApps.value = apps.sortedBy { it.name.lowercase() }
            }
        }

        private fun startChannelService() {
            val intent =
                Intent(appContext, EventChannelService::class.java).apply {
                    action = EventChannelService.ACTION_START
                }
            appContext.startForegroundService(intent)
        }

        private fun stopChannelService() {
            val intent =
                Intent(appContext, EventChannelService::class.java).apply {
                    action = EventChannelService.ACTION_STOP
                }
            appContext.startService(intent)
        }

        companion object {
            private const val STOP_TIMEOUT_MS = 5000L
        }
    }
