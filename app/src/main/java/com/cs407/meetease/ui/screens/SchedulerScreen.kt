package com.cs407.meetease.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.meetease.data.MeetingSuggestion
import com.cs407.meetease.ui.theme.AppGreen
import com.cs407.meetease.ui.theme.AppGreenLight
import com.cs407.meetease.ui.viewmodels.SchedulerViewModel
import com.cs407.meetease.data.Member

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(viewModel: SchedulerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            item {
                DurationSelector(
                    selectedSlots = uiState.selectedDurationSlots,
                    onDurationSelected = { viewModel.setDuration(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Member Availability",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                CalendarGrid(
                    members = uiState.members,
                    currentUser = uiState.currentUser,
                    onSlotClick = { day, slot -> viewModel.toggleAvailability(day, slot) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.findBestMeetingTimes() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Find Best Times")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.isLoading) {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }

            if (uiState.suggestions.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Suggestions",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(uiState.suggestions) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        onConfirm = { viewModel.confirmMeeting(it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationSelector(
    selectedSlots: Int,
    onDurationSelected: (Int) -> Unit
) {
    val durationOptions = mapOf(
        "30 Minutes" to 1,
        "1 Hour" to 2,
        "1.5 Hours" to 3,
        "2 Hours" to 4
    )
    val selectedText = durationOptions.entries.find { it.value == selectedSlots }?.key ?: "1 Hour"
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        Text("Meeting Duration", style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = { isExpanded = !isExpanded }
        ) {
            TextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false }
            ) {
                durationOptions.forEach { (text, slots) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            onDurationSelected(slots)
                            isExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarGrid(
    members: List<Member>,
    currentUser: Member?,
    onSlotClick: (Int, Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Column {
            Spacer(modifier = Modifier.height(30.dp)) // Header space
            SchedulerViewModel.TIMES.forEach { time ->
                Box(
                    modifier = Modifier.height(60.dp), // 2 slots * 30dp
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, end = 4.dp)
                    )
                }
            }
        }
        SchedulerViewModel.DAYS.forEachIndexed { dayIndex, day ->
            Column {
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .width(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = day, style = MaterialTheme.typography.labelMedium)
                }
                Column {
                    for (slotIndex in 0 until SchedulerViewModel.TOTAL_SLOTS_PER_DAY) {
                        val isUserAvailable = currentUser?.availability
                            ?.any { it.dayIndex == dayIndex && it.slotIndex == slotIndex } == true

                        val othersAvailableCount = members
                            .filter { it.id != currentUser?.id }
                            .count { m -> m.availability.any { it.dayIndex == dayIndex && it.slotIndex == slotIndex } }

                        val bgColor = when {
                            isUserAvailable -> AppGreen
                            othersAvailableCount > 0 -> AppGreenLight
                            else -> MaterialTheme.colorScheme.surface
                        }

                        val dashEffect = if (slotIndex % 2 != 0) {
                            PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                        } else null

                        Box(
                            modifier = Modifier
                                .size(50.dp, 30.dp)
                                .background(bgColor)
                                .border(0.5.dp, Color.LightGray)
                                .drawWithContent {
                                    drawContent()
                                    if (dashEffect != null) {
                                        drawLine(
                                            color = Color.Gray,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            strokeWidth = 0.5f,
                                            pathEffect = dashEffect
                                        )
                                    }
                                }
                                .clickable { onSlotClick(dayIndex, slotIndex) }
                        ) {
                            if (isUserAvailable && othersAvailableCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.White.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionCard(
    suggestion: MeetingSuggestion,
    onConfirm: (MeetingSuggestion) -> Unit
) {
    val day = SchedulerViewModel.DAYS[suggestion.dayIndex]
    val startTime = SchedulerViewModel().slotToTime(suggestion.startSlot)
    val endTime = SchedulerViewModel().slotToTime(suggestion.startSlot + suggestion.durationSlots)

    val isBest = suggestion.availableCount == suggestion.totalCount
    val cardColor = if (isBest) AppGreenLight else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$day, $startTime - $endTime",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${suggestion.availableCount} / ${suggestion.totalCount} Members Available",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isBest) {
                    Text(
                        text = "All members available!",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Button(onClick = { onConfirm(suggestion) }) {
                Icon(Icons.Filled.Check, contentDescription = "Confirm")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Select")
            }
        }
    }
}
