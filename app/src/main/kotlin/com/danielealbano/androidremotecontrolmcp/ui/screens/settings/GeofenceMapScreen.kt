package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import android.location.Geocoder
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.data.model.GeofenceZone
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ChannelViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceMapScreen(
    viewModel: ChannelViewModel,
    zoneId: String?,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.eventChannelConfig.collectAsStateWithLifecycle()
    val existingZone = zoneId?.let { id -> config.geofence.zones.find { it.id == id } }

    var name by rememberSaveable { mutableStateOf(existingZone?.name ?: "") }
    var markerPosition by remember {
        mutableStateOf(existingZone?.let { LatLng(it.latitude, it.longitude) })
    }
    var radiusMeters by rememberSaveable {
        mutableFloatStateOf(existingZone?.radiusMeters ?: DEFAULT_RADIUS_METERS)
    }
    var notifyOnEnter by rememberSaveable { mutableStateOf(existingZone?.notifyOnEnter ?: true) }
    var notifyOnExit by rememberSaveable { mutableStateOf(existingZone?.notifyOnExit ?: true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val cameraPositionState =
        rememberCameraPositionState {
            position =
                CameraPosition.fromLatLngZoom(
                    markerPosition ?: LatLng(0.0, 0.0),
                    DEFAULT_ZOOM,
                )
        }

    val canSave = name.isNotBlank() && markerPosition != null
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (zoneId != null) "Edit Zone" else "Add Zone") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val pos = markerPosition ?: return@TextButton
                            val zone =
                                GeofenceZone(
                                    id =
                                        zoneId ?: java.util.UUID
                                            .randomUUID()
                                            .toString(),
                                    name = name,
                                    latitude = pos.latitude,
                                    longitude = pos.longitude,
                                    radiusMeters = radiusMeters,
                                    notifyOnEnter = notifyOnEnter,
                                    notifyOnExit = notifyOnExit,
                                )
                            if (zoneId != null) {
                                viewModel.updateGeofenceZone(zone)
                            } else {
                                viewModel.addGeofenceZone(zone)
                            }
                            onNavigateBack()
                        },
                        enabled = canSave,
                    ) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Address search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search address") },
                trailingIcon = {
                    IconButton(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                @Suppress("DEPRECATION")
                                val results = Geocoder(context).getFromLocationName(searchQuery, 1)
                                val address = results?.firstOrNull()
                                if (address != null) {
                                    val latLng = LatLng(address.latitude, address.longitude)
                                    withContext(Dispatchers.Main) {
                                        markerPosition = latLng
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM),
                                        )
                                    }
                                }
                            } catch (_: IOException) {
                                // Geocoder failed — network unavailable or no results
                            }
                        }
                    }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
            )

            // Google Map
            GoogleMap(
                modifier = Modifier.weight(1f),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng -> markerPosition = latLng },
            ) {
                markerPosition?.let { pos ->
                    Marker(state = MarkerState(position = pos), title = name)
                    Circle(
                        center = pos,
                        radius = radiusMeters.toDouble(),
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        strokeColor = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2f,
                    )
                }
            }

            // Bottom panel
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Zone name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text("Radius: ${radiusMeters.toInt()}m")
                    Slider(
                        value = radiusMeters,
                        onValueChange = { radiusMeters = it },
                        valueRange = MIN_RADIUS_METERS..MAX_RADIUS_METERS,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = notifyOnEnter, onCheckedChange = { notifyOnEnter = it })
                        Text("Notify on enter", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = notifyOnExit, onCheckedChange = { notifyOnExit = it })
                        Text("Notify on exit", modifier = Modifier.padding(start = 8.dp))
                    }
                    markerPosition?.let { pos ->
                        Text(
                            "Lat: ${"%.6f".format(pos.latitude)}, Lon: ${"%.6f".format(pos.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private const val MIN_RADIUS_METERS = 100f
private const val MAX_RADIUS_METERS = 5000f
private const val DEFAULT_RADIUS_METERS = 200f
private const val DEFAULT_ZOOM = 15f
