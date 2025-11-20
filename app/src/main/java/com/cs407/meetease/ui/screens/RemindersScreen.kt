package com.cs407.meetease.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cs407.meetease.data.ConfirmedMeeting
import com.cs407.meetease.data.MemberStatus
import com.cs407.meetease.ui.theme.AppAmber
import com.cs407.meetease.ui.theme.AppGreen
import com.cs407.meetease.ui.theme.AppRed
import com.cs407.meetease.ui.viewmodels.RemindersViewModel

@Composable
fun RemindersScreen(
    remindersViewModel: RemindersViewModel
) {
    val rUiState by remindersViewModel.uiState.collectAsState()
    val confirmedMeeting = rUiState.confirmedMeeting
    val context = LocalContext.current
    var showCancelDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
                remindersViewModel.toggleLocationSharing(confirmedMeeting)
            } else {
                remindersViewModel.showPermissionError()
            }
        }
    )

    LaunchedEffect(rUiState.message) {
        rUiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            remindersViewModel.clearMessage()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Meeting Reminders",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (confirmedMeeting == null) {
            item {
                NoMeetingCard()
            }
        } else {
            item {
                ConfirmedMeetingCard(
                    day = confirmedMeeting.day,
                    timeRange = confirmedMeeting.timeRange
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (rUiState.isOrganizer) {
                    Button(
                        onClick = { showCancelDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Cancel")
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel Meeting")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                LocationSharingCard(
                    uiState = rUiState,
                    onToggleClick = {
                        checkAndRequestLocationPermission(
                            context = context,
                            onPermissionGranted = {
                                remindersViewModel.toggleLocationSharing(confirmedMeeting)
                            },
                            permissionLauncher = locationPermissionLauncher
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Member Status",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(confirmedMeeting.attendees) { attendee ->
                MemberStatusCard(status = attendee)
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Meeting?") },
            text = { Text("This will remove the meeting for everyone and delete it from your Google Calendar.") },
            confirmButton = {
                Button(
                    onClick = {
                        remindersViewModel.cancelMeeting()
                        showCancelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("No") }
            }
        )
    }
}

private fun checkAndRequestLocationPermission(
    context: Context,
    onPermissionGranted: () -> Unit,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION

    val permissionsToRequest = mutableListOf<String>()

    if (ContextCompat.checkSelfPermission(context, fineLocationPermission) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(fineLocationPermission)
    }

    if (permissionsToRequest.isEmpty()) {
        onPermissionGranted()
    } else {
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }
}

@Composable
fun LocationSharingCard(
    uiState: com.cs407.meetease.ui.viewmodels.RemindersUiState,
    onToggleClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.isSharingLocation) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Live Location", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(uiState.sharingStatus, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onToggleClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.LocationOn, contentDescription = "Location")
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (uiState.isSharingLocation) "Stop Sharing" else "Share Live Location")
            }
        }
    }
}

@Composable
fun NoMeetingCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Info, "No Meetings", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No Confirmed Meetings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Go to the 'Scheduler' tab to find and confirm meetings.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ConfirmedMeetingCard(day: String, timeRange: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Confirmed Meeting", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Day: $day")
            Text("Time: $timeRange")
        }
    }
}

@Composable
fun MemberStatusCard(status: MemberStatus) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = status.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = when (status.status) { "Confirmed" -> AppGreen; "Running Late" -> AppAmber; else -> AppRed }
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = status.status)
            }
        }
    }
}