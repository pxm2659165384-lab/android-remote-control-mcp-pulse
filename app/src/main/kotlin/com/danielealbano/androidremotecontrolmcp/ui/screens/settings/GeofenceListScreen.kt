@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.data.model.GeofenceZone
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ChannelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceListScreen(
    viewModel: ChannelViewModel,
    onNavigateToMap: (zoneId: String?) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.eventChannelConfig.collectAsStateWithLifecycle()
    var zoneToDelete by remember { mutableStateOf<GeofenceZone?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geofence Zones") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToMap(null) }) {
                Icon(Icons.Default.Add, "Add zone")
            }
        },
    ) { padding ->
        if (config.geofence.zones.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No geofence zones configured",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(config.geofence.zones) { zone ->
                    ListItem(
                        headlineContent = { Text(zone.name) },
                        supportingContent = {
                            Text(
                                "${"%.4f".format(zone.latitude)}, ${"%.4f".format(zone.longitude)}" +
                                    " — ${zone.radiusMeters.toInt()}m",
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onNavigateToMap(zone.id) }) {
                                    Icon(Icons.Default.Edit, "Edit")
                                }
                                IconButton(onClick = { zoneToDelete = zone }) {
                                    Icon(Icons.Default.Delete, "Delete")
                                }
                            }
                        },
                    )
                }
            }
        }

        zoneToDelete?.let { zone ->
            AlertDialog(
                onDismissRequest = { zoneToDelete = null },
                title = { Text("Delete zone?") },
                text = { Text("Remove \"${zone.name}\"?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeGeofenceZone(zone.id)
                        zoneToDelete = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { zoneToDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}
