@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ChannelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingsScreen(
    viewModel: ChannelViewModel,
    onNavigateToNotificationFilter: () -> Unit,
    onNavigateToWifiMonitor: () -> Unit,
    onNavigateToGeofenceList: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.eventChannelConfig.collectAsStateWithLifecycle()
    val endpointUrlInput by viewModel.endpointUrlInput.collectAsStateWithLifecycle()
    val endpointUrlError by viewModel.endpointUrlError.collectAsStateWithLifecycle()
    val authTokenInput by viewModel.authTokenInput.collectAsStateWithLifecycle()
    val isRunning = config.enabled
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Channel") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp),
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Event Channel") },
                    trailingContent = {
                        Switch(
                            checked = isRunning,
                            onCheckedChange = { viewModel.updateChannelEnabled(it) },
                        )
                    },
                )
            }
            item {
                OutlinedTextField(
                    value = endpointUrlInput,
                    onValueChange = { viewModel.updateEndpointUrl(it) },
                    label = { Text("Endpoint URL") },
                    isError = endpointUrlError != null,
                    supportingText = endpointUrlError?.let { { Text(it) } },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = authTokenInput,
                    onValueChange = {},
                    label = { Text("Auth Token") },
                    readOnly = true,
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation =
                        if (tokenVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (tokenVisible) "Hide" else "Show",
                                )
                            }
                            IconButton(
                                onClick = { clipboardManager.setText(AnnotatedString(authTokenInput)) },
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(
                                onClick = { viewModel.generateNewAuthToken() },
                                enabled = !isRunning,
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Generate new")
                            }
                        }
                    },
                )
            }
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        "Event Sources",
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Notification Events") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = config.notifications.enabled,
                                onCheckedChange = { viewModel.updateNotificationChannelEnabled(it) },
                            )
                        }
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToNotificationFilter),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("WiFi Events") },
                    trailingContent = {
                        Switch(
                            checked = config.wifi.enabled,
                            onCheckedChange = { viewModel.updateWifiChannelEnabled(it) },
                        )
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToWifiMonitor),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Geofence Events") },
                    trailingContent = {
                        Switch(
                            checked = config.geofence.enabled,
                            onCheckedChange = { viewModel.updateGeofenceChannelEnabled(it) },
                        )
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToGeofenceList),
                )
            }
        }
    }
}
