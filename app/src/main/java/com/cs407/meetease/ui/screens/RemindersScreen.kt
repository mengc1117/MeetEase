package com.cs407.meetease.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cs407.meetease.data.MemberStatus
import com.cs407.meetease.ui.theme.AppAmber
import com.cs407.meetease.ui.theme.AppGreen
import com.cs407.meetease.ui.theme.AppRed
import com.cs407.meetease.ui.viewmodels.SchedulerUiState
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.layout.width


@Composable
fun RemindersScreen(schedulerUiState: StateFlow<SchedulerUiState>) {
    val uiState by schedulerUiState.collectAsState()
    val confirmedMeeting = uiState.confirmedMeeting

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
                Text(
                    text = "Member Status (GPS Simulated)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(confirmedMeeting.attendees) { attendee ->
                MemberStatusCard(status = attendee)
            }
        }
    }
}

@Composable
fun NoMeetingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "No Meeting",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Confirmed Meetings",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Go to the 'Scheduler' tab to find and confirm a time.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ConfirmedMeetingCard(day: String, timeRange: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Confirmed Meeting",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "$day, $timeRange",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun MemberStatusCard(status: MemberStatus) {
    val (icon, color) = when (status.status) {
        "Confirmed" -> Icons.Filled.CheckCircle to AppGreen
        "Running Late" -> Icons.Filled.Error to AppRed
        else -> Icons.Filled.HourglassTop to AppAmber
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = status.name.first().toString(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(status.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    status.eta ?: status.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color
                )
            }
            Icon(icon, contentDescription = status.status, tint = color)
        }
    }
}