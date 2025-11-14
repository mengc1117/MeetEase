package com.cs407.meetease.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cs407.meetease.data.AvailabilitySlot
import com.cs407.meetease.data.GoogleCalendarEvent
import com.cs407.meetease.data.Member
import com.cs407.meetease.data.MeetingSuggestion
import com.cs407.meetease.ui.theme.AppGray
import com.cs407.meetease.ui.theme.AppGreen
import com.cs407.meetease.ui.theme.AppGreenLight
import com.cs407.meetease.ui.theme.AppRed
import com.cs407.meetease.ui.viewmodels.SchedulerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(viewModel: SchedulerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateEventDialog by remember { mutableStateOf(false) }
    var clickedEvent: GoogleCalendarEvent? by remember { mutableStateOf(null) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateEventDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create New Event", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scheduler",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            IconButton(
                                onClick = { viewModel.refreshCalendar() },
                                enabled = !uiState.isLoading
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refresh Calendar",
                                    tint = if (uiState.isLoading) Color.Gray else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
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
                    }
                }

                // Show loading state with message
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Syncing calendar events...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (uiState.members.isEmpty()) {
                    // Empty state for no members
                    item {
                        EmptyStateCard(
                            title = "No Team Members",
                            message = "Go to the Members tab to add your team",
                            icon = Icons.Default.EventBusy
                        )
                    }
                } else if (uiState.members.all { it.availability.isEmpty() }) {
                    // Empty state for no availability
                    item {
                        EmptyStateCard(
                            title = "No Availability Set",
                            message = "Tap on the calendar slots below to mark when you're available",
                            icon = Icons.Default.Info
                        )
                    }
                }

                item {
                    CalendarGrid(
                        members = uiState.members,
                        currentUser = uiState.currentUser,
                        googleBusySlots = uiState.googleBusySlots,
                        googleEvents = uiState.googleEvents,
                        dayLabels = uiState.dynamicDayLabels,
                        onSlotClick = { day, slot ->
                            val slotKey = AvailabilitySlot(day, slot)
                            val event = uiState.googleEvents.firstOrNull {
                                it.dayIndex == day && slot >= it.startSlot && slot < it.endSlot
                            }

                            if (event != null) {
                                clickedEvent = event
                            } else {
                                viewModel.toggleAvailability(day, slot)
                            }
                        },
                        onSlotLongClick = { day, slot ->
                            // Show conflict dialog on long click
                            conflictSlot = Pair(day, slot)
                            showConflictDialog = true
                        }
                    )
                }

                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.findBestMeetingTimes() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading && uiState.members.any { it.availability.isNotEmpty() }
                        ) {
                            Text("Find Best Times")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (uiState.suggestions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Top Suggestions",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(uiState.suggestions) { suggestion ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            SuggestionCard(
                                suggestion = suggestion,
                                dayLabels = uiState.dynamicDayLabels,
                                onConfirm = { viewModel.confirmMeeting(it) },
                                slotToTime = viewModel::slotToTime
                            )
                        }
                    }
                }
            }
        }
    }

    if (clickedEvent != null) {
        EventDetailDialog(
            event = clickedEvent!!,
            dayLabels = uiState.dynamicDayLabels,
            onDismiss = { clickedEvent = null }
        )
    }

    if (showCreateEventDialog) {
        CreateEventDialog(
            dayLabels = uiState.dynamicDayLabels,
            onDismiss = { showCreateEventDialog = false },
            onConfirm = { title, dayIndex, startSlot, durationSlots ->
                viewModel.addEventToCalendar(
                    title,
                    dayIndex,
                    startSlot,
                    durationSlots
                )
                showCreateEventDialog = false
            },
            timeToSlot = viewModel::timeToSlot
        )
    }

    if (showConflictDialog && conflictSlot != null) {
        ConflictDialog(
            dayIndex = conflictSlot!!.first,
            slotIndex = conflictSlot!!.second,
            conflicts = viewModel.analyzeConflicts(conflictSlot!!.first, conflictSlot!!.second),
            dayLabels = uiState.dynamicDayLabels,
            slotToTime = viewModel::slotToTime,
            onDismiss = { showConflictDialog = false }
        )
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ConflictDialog(
    dayIndex: Int,
    slotIndex: Int,
    conflicts: List<String>,
    dayLabels: List<String>,
    slotToTime: (Int) -> String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time Slot Conflicts") },
        text = {
            Column {
                Text(
                    text = "${dayLabels.getOrNull(dayIndex) ?: "Unknown"} at ${slotToTime(slotIndex)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (conflicts.isEmpty()) {
                    Text(
                        "Everyone is available!",
                        color = AppGreen,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text("Conflicts:", style = MaterialTheme.typography.bodyMedium)
                    conflicts.forEach { conflict ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("• ", color = AppRed)
                            Text(conflict, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun EventDetailDialog(
    event: GoogleCalendarEvent,
    dayLabels: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Calendar Event") },
        text = {
            Column {
                Text(text = "Title: ${event.title}", style = MaterialTheme.typography.titleMedium)
                Text(text = "Time: ${event.timeRange}", style = MaterialTheme.typography.bodyMedium)
                if (event.dayIndex in dayLabels.indices) {
                    Text(text = "Day: ${dayLabels[event.dayIndex]}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventDialog(
    dayLabels: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Int) -> Unit,
    timeToSlot: (String) -> Int
) {
    var title by remember { mutableStateOf("") }
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var selectedStartTime by remember { mutableStateOf(SchedulerViewModel.TIMES.first()) }
    var selectedDurationSlots by remember { mutableIntStateOf(1) }

    val durationOptions = mapOf(
        "30 Minutes" to 1,
        "1 Hour" to 2,
        "1.5 Hours" to 3,
        "2 Hours" to 4
    )
    val durationOptionsKeys = durationOptions.keys.toList()
    var durationExpanded by remember { mutableStateOf(false) }
    var timeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Day of Week", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    dayLabels.forEachIndexed { index, dayLabel ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (selectedDayIndex == index),
                                onClick = { selectedDayIndex = index }
                            )
                            Text(text = dayLabel.split(" ")[0])
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = timeExpanded,
                    onExpandedChange = { timeExpanded = !timeExpanded }
                ) {
                    TextField(
                        value = selectedStartTime,
                        onValueChange = {},
                        label = { Text("Start Time") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = timeExpanded,
                        onDismissRequest = { timeExpanded = false }
                    ) {
                        SchedulerViewModel.TIMES.forEach { time ->
                            DropdownMenuItem(
                                text = { Text(time) },
                                onClick = {
                                    selectedStartTime = time
                                    timeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = durationExpanded,
                    onExpandedChange = { durationExpanded = !durationExpanded }
                ) {
                    TextField(
                        value = durationOptions.entries.find { it.value == selectedDurationSlots }?.key ?: "30 Minutes",
                        onValueChange = {},
                        label = { Text("Duration") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = durationExpanded,
                        onDismissRequest = { durationExpanded = false }
                    ) {
                        durationOptionsKeys.forEach { text ->
                            DropdownMenuItem(
                                text = { Text(text) },
                                onClick = {
                                    selectedDurationSlots = durationOptions[text] ?: 1
                                    durationExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startSlot = timeToSlot(selectedStartTime)
                    onConfirm(title, selectedDayIndex, startSlot, selectedDurationSlots)
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
    googleBusySlots: Set<AvailabilitySlot>,
    googleEvents: List<GoogleCalendarEvent>,
    dayLabels: List<String>,
    onSlotClick: (Int, Int) -> Unit,
    onSlotLongClick: ((Int, Int) -> Unit)? = null
) {
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val timeSlotHeight = 30.dp
    val dayColumnWidth = 65.dp
    val timeAxisWidth = 60.dp

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, Color.LightGray)
            .height(550.dp)
    ) {
        Column(Modifier.width(timeAxisWidth)) {
            Box(
                modifier = Modifier
                    .height(timeSlotHeight)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(0.5.dp, Color.LightGray)
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(verticalScrollState)
            ) {
                SchedulerViewModel.TIMES.forEach { time ->
                    key(time) {
                        Box(
                            modifier = Modifier
                                .height(timeSlotHeight * 2f)
                                .fillMaxWidth()
                                .border(0.5.dp, Color.LightGray),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .horizontalScroll(horizontalScrollState)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                dayLabels.forEach { dayLabel ->
                    Box(
                        modifier = Modifier
                            .height(timeSlotHeight)
                            .width(dayColumnWidth)
                            .border(0.5.dp, Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxHeight()
                    .verticalScroll(verticalScrollState)
            ) {
                (0 until SchedulerViewModel.NUM_DAYS_TO_SHOW).forEachIndexed { dayIndex, _ ->
                    key(dayIndex) {
                        Column {
                            for (slotIndex in 0 until SchedulerViewModel.TOTAL_SLOTS_PER_DAY) {
                                key(slotIndex) {
                                    val slot = AvailabilitySlot(dayIndex, slotIndex)
                                    val isUserAvailable = currentUser?.availability
                                        ?.any { it.dayIndex == dayIndex && it.slotIndex == slotIndex } == true

                                    val isGoogleBusy = googleBusySlots.contains(slot)

                                    val othersAvailableCount = members
                                        .filter { it.id != currentUser?.id }
                                        .count { m -> m.availability.any { it.dayIndex == dayIndex && it.slotIndex == slotIndex } }

                                    val bgColor = when {
                                        isGoogleBusy -> AppGray.copy(alpha = 0.5f)
                                        isUserAvailable -> AppGreen
                                        othersAvailableCount > 0 -> AppGreenLight
                                        else -> MaterialTheme.colorScheme.surface
                                    }

                                    val eventBlock = googleEvents.firstOrNull {
                                        it.dayIndex == dayIndex && it.startSlot == slotIndex
                                    }

                                    val dashEffect = if (slotIndex % 2 != 0) {
                                        PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                                    } else null

                                    Box(
                                        modifier = Modifier
                                            .size(dayColumnWidth, timeSlotHeight)
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
                                            .clickable { onSlotClick(dayIndex, slotIndex) },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (eventBlock != null) {
                                            Text(
                                                text = eventBlock.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier.padding(start = 2.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else if (isUserAvailable && othersAvailableCount > 0) {
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
        }
    }
}

@Composable
fun SuggestionCard(
    suggestion: MeetingSuggestion,
    dayLabels: List<String>,
    onConfirm: (MeetingSuggestion) -> Unit,
    slotToTime: (Int) -> String
) {
    val day = if (suggestion.dayIndex in dayLabels.indices) dayLabels[suggestion.dayIndex] else "Date Error"
    val startTime = slotToTime(suggestion.startSlot)
    val endTime = slotToTime(suggestion.startSlot + suggestion.durationSlots)

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