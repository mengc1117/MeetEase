package com.cs407.meetease.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cs407.meetease.ui.viewmodels.MapViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val defaultLocation = LatLng(43.0731, -89.4012)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 12f)
    }

    var initialCameraSet by remember { mutableStateOf(false) }

    val organizerLocation = remember(uiState.membersWithLocation, uiState.organizerId) {
        uiState.membersWithLocation.find { it.id == uiState.organizerId }?.location?.let {
            LatLng(it.latitude, it.longitude)
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
            ) {
                hasLocationPermission = true
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(uiState.meetingDestination) {
        uiState.meetingDestination?.let { dest ->
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(LatLng(dest.latitude, dest.longitude), 14f),
                durationMs = 1000
            )
            initialCameraSet = true
        }
    }

    LaunchedEffect(organizerLocation) {
        if (!initialCameraSet && uiState.meetingDestination == null && organizerLocation != null) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(organizerLocation, 14f),
                durationMs = 1000
            )
            initialCameraSet = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isOrganizer)
                        Text("Map (Long press to set Destination)")
                    else
                        Text("Group Map")
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = hasLocationPermission
                    ),
                    uiSettings = MapUiSettings(
                        myLocationButtonEnabled = hasLocationPermission,
                        zoomControlsEnabled = true
                    ),
                    onMapLongClick = { latLng ->
                        if (uiState.isOrganizer) {
                            viewModel.setMeetingDestination(latLng)
                            Toast.makeText(context, "Meeting Destination Updated!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    uiState.membersWithLocation.forEach { member ->
                        member.location?.let { geoPoint ->
                            val position = LatLng(geoPoint.latitude, geoPoint.longitude)
                            val isOrganizerMarker = member.id == uiState.organizerId
                            val markerColor = if (isOrganizerMarker) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_BLUE
                            val titleSuffix = if (isOrganizerMarker) " (Organizer)" else ""

                            Marker(
                                state = MarkerState(position = position),
                                title = member.name + titleSuffix,
                                snippet = "Last updated location",
                                icon = BitmapDescriptorFactory.defaultMarker(markerColor)
                            )
                        }
                    }

                    uiState.meetingDestination?.let { dest ->
                        val destLatLng = LatLng(dest.latitude, dest.longitude)
                        Marker(
                            state = MarkerState(position = destLatLng),
                            title = "Meeting Destination",
                            snippet = "Click Navigate button to go here",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        )

                        uiState.currentUserLocation?.let { myLoc ->
                            Polyline(
                                points = listOf(myLoc, destLatLng),
                                color = Color.Gray,
                                pattern = listOf(Dash(20f), Gap(10f)),
                                width = 10f,
                                geodesic = true
                            )
                        }
                    }
                }
            }

            if (uiState.meetingDestination != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val dest = uiState.meetingDestination!!
                        launchGoogleMapsNavigation(context, dest.latitude, dest.longitude)
                    },
                    icon = { Icon(Icons.Filled.Navigation, "Navigate") },
                    text = { Text("Navigate") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                )
            }
        }
    }
}

fun launchGoogleMapsNavigation(context: Context, lat: Double, lng: Double) {
    val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
    mapIntent.setPackage("com.google.android.apps.maps")

    try {
        context.startActivity(mapIntent)
    } catch (e: Exception) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"))
            context.startActivity(browserIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, "Could not open Maps application", Toast.LENGTH_SHORT).show()
        }
    }
}