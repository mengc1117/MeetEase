package com.cs407.meetease.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.data.*
import com.cs407.meetease.utils.MeetingReminderScheduler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.TimeZone

data class SchedulerUiState(
    val members: List<Member> = emptyList(),
    val currentUser: Member? = null,
    val googleBusySlots: Set<AvailabilitySlot> = emptySet(),
    val googleEvents: List<GoogleCalendarEvent> = emptyList(),
    val dynamicDayLabels: List<String> = emptyList(),
    val selectedDurationSlots: Int = 2,
    val suggestions: List<MeetingSuggestion> = emptyList(),
    val confirmedMeeting: ConfirmedMeeting? = null,
    val isLoading: Boolean = false,
    val message: String? = null
)

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SchedulerUiState())
    val uiState: StateFlow<SchedulerUiState> = _uiState.asStateFlow()

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var groupId: String? = null
    private val userId = auth.currentUser?.uid
    private val meetingReminderScheduler = MeetingReminderScheduler(application.applicationContext)

    private val today: LocalDate = LocalDate.now()
    private val dayFormatter = DateTimeFormatter.ofPattern("EEE M/d")

    companion object {
        val TIMES = List(16) { "${it + 8}:00" }
        const val SLOTS_PER_HOUR = 2
        const val TOTAL_SLOTS_PER_DAY = 16 * SLOTS_PER_HOUR
        const val START_HOUR = 8
        const val NUM_DAYS_TO_SHOW = 7
    }

    init {
        generateDynamicDayLabels()
        loadUserAndGroupData()
    }

    private fun generateDynamicDayLabels() {
        val labels = (0 until NUM_DAYS_TO_SHOW).map {
            today.plusDays(it.toLong()).format(dayFormatter)
        }
        _uiState.update { it.copy(dynamicDayLabels = labels) }
    }

    fun refreshData() {
        loadUserAndGroupData()
    }

    private fun loadUserAndGroupData() {
        if (userId == null) {
            _uiState.update { it.copy(message = "User not logged in.", isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val userDoc = db.collection("users").document(userId).get().await()
                groupId = userDoc.getString("groupId")
                if (groupId == null) {
                    _uiState.update { it.copy(message = "User has no group.", isLoading = false) }
                    return@launch
                }
                listenForMembers(groupId!!)
                syncGoogleCalendarBusySlots()
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message, isLoading = false) }
            }
        }
    }

    private fun listenForMembers(groupId: String) {
        db.collection("groups").document(groupId).collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(message = error.message, isLoading = false) }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val members = snapshot.toObjects<Member>()

                viewModelScope.launch {
                    val membersWithAvailability = members.map { member ->
                        member.availability = loadAvailabilityForMember(groupId, member.id)
                        member
                    }

                    val updatedCurrentUser = membersWithAvailability.firstOrNull { it.id == userId }

                    _uiState.update {
                        it.copy(
                            members = membersWithAvailability,
                            currentUser = updatedCurrentUser,
                            isLoading = false
                        )
                    }
                }
            }
    }

    private suspend fun loadAvailabilityForMember(groupId: String, memberId: String): MutableList<AvailabilitySlot> {
        return try {
            val doc = db.collection("groups").document(groupId)
                .collection("availability").document(memberId).get().await()

            if (!doc.exists()) {
                return mutableListOf()
            }

            val slotsData = doc.get("slots") as? List<HashMap<String, Any>>
            slotsData?.map {
                AvailabilitySlot(
                    (it["dayIndex"] as Long).toInt(),
                    (it["slotIndex"] as Long).toInt()
                )
            }?.toMutableList() ?: mutableListOf()

        } catch (e: Exception) {
            Log.e("SchedulerViewModel", "Error loading availability: ${e.message}")
            mutableListOf()
        }
    }


    fun toggleAvailability(dayIndex: Int, slotIndex: Int) {
        val gId = groupId ?: return
        val uId = userId ?: return
        val slot = AvailabilitySlot(dayIndex, slotIndex)

        if (_uiState.value.googleBusySlots.contains(slot)) {
            _uiState.update { it.copy(message = "This slot is busy on your Google Calendar.") }
            return
        }

        val availability = _uiState.value.currentUser?.availability ?: mutableListOf()
        val isAvailable = availability.contains(slot)

        viewModelScope.launch {
            try {
                val docRef = db.collection("groups").document(gId).collection("availability").document(uId)

                if (isAvailable) {
                    availability.remove(slot)
                    docRef.update("slots", FieldValue.arrayRemove(slot)).await()
                } else {
                    availability.add(slot)
                    docRef.set(mapOf("slots" to FieldValue.arrayUnion(slot)), com.google.firebase.firestore.SetOptions.merge()).await()
                }

                _uiState.update {
                    val updatedUser = it.currentUser?.copy()
                    updatedUser?.availability = availability
                    it.copy(currentUser = updatedUser)
                }
            } catch (e: Exception) {
                if (e.message?.contains("NOT_FOUND") == true && !isAvailable) {
                    try {
                        val docRef = db.collection("groups").document(gId).collection("availability").document(uId)
                        docRef.set(mapOf("slots" to listOf(slot))).await()

                        val newAvailability = mutableListOf(slot)
                        _uiState.update {
                            val updatedUser = it.currentUser?.copy()
                            updatedUser?.availability = newAvailability
                            it.copy(currentUser = updatedUser)
                        }
                    } catch (e2: Exception) {
                        _uiState.update { it.copy(message = e2.message) }
                    }
                } else {
                    _uiState.update { it.copy(message = e.message) }
                }
            }
        }
    }

    private fun convertUtcToLocalSlot(
        utcStart: DateTime,
        utcEnd: DateTime
    ): List<AvailabilitySlot> {
        val slots = mutableSetOf<AvailabilitySlot>()
        val systemZone = ZoneId.systemDefault()

        var currentInstant = Instant.ofEpochMilli(utcStart.value)
        val endInstant = Instant.ofEpochMilli(utcEnd.value)

        val todayDate = today

        while (currentInstant.isBefore(endInstant)) {
            val localTime = LocalDateTime.ofInstant(currentInstant, systemZone)
            val eventDate = localTime.toLocalDate()
            val dayIndex = ChronoUnit.DAYS.between(todayDate, eventDate).toInt()

            if (dayIndex in 0 until NUM_DAYS_TO_SHOW) {
                val hour = localTime.hour
                val minute = localTime.minute

                if (hour >= START_HOUR) {
                    val hourSlot = (hour - START_HOUR) * SLOTS_PER_HOUR
                    val minuteSlot = minute / (60 / SLOTS_PER_HOUR)
                    val slotIndex = hourSlot + minuteSlot

                    if (slotIndex < TOTAL_SLOTS_PER_DAY) {
                        slots.add(AvailabilitySlot(dayIndex, slotIndex))
                    }
                }
            }
            currentInstant = currentInstant.plus(30, ChronoUnit.MINUTES)
        }
        return slots.toList()
    }

    private fun convertUtcToLocalEvent(
        event: Event
    ): List<GoogleCalendarEvent> {
        val eventsList = mutableListOf<GoogleCalendarEvent>()
        val systemZone = ZoneId.systemDefault()

        val startDateTime = event.start?.dateTime ?: return emptyList()
        val endDateTime = event.end?.dateTime ?: return emptyList()

        val startInstant = Instant.ofEpochMilli(startDateTime.value)
        val endInstant = Instant.ofEpochMilli(endDateTime.value)

        val startLocalTime = LocalDateTime.ofInstant(startInstant, systemZone)
        val endLocalTime = LocalDateTime.ofInstant(endInstant, systemZone)

        val todayDate = today
        val eventDate = startLocalTime.toLocalDate()
        val dayIndex = ChronoUnit.DAYS.between(todayDate, eventDate).toInt()

        val startHourSlot = (startLocalTime.hour - START_HOUR) * SLOTS_PER_HOUR + (startLocalTime.minute / (60 / SLOTS_PER_HOUR))
        val endHourSlot = (endLocalTime.hour - START_HOUR) * SLOTS_PER_HOUR + (endLocalTime.minute / (60 / SLOTS_PER_HOUR))

        val timeRange = "${slotToTime(startHourSlot)} - ${slotToTime(endHourSlot)}"

        if (dayIndex in 0 until NUM_DAYS_TO_SHOW && startHourSlot < TOTAL_SLOTS_PER_DAY) {
            eventsList.add(
                GoogleCalendarEvent(
                    dayIndex = dayIndex,
                    startSlot = startHourSlot,
                    endSlot = endHourSlot,
                    title = event.summary ?: "Busy Time",
                    timeRange = timeRange
                )
            )
        }
        return eventsList
    }

    fun syncGoogleCalendarBusySlots() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val context = getApplication<Application>().applicationContext
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account == null) {
                    _uiState.update { it.copy(message = "Link Google Calendar in Profile to sync busy times.") }
                    return@launch
                }

                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    setOf(CalendarScopes.CALENDAR_READONLY)
                ).setSelectedAccount(account.account)

                val service = Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                    .setApplicationName("MeetEase")
                    .build()

                val startOfToday = today.atStartOfDay(ZoneId.systemDefault())
                val endOfQuery = startOfToday.plusDays(NUM_DAYS_TO_SHOW.toLong())

                val timeMin = DateTime(startOfToday.toInstant().toEpochMilli())
                val timeMax = DateTime(endOfQuery.toInstant().toEpochMilli())

                val events = service.events().list("primary")
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setFields("items(start,end,summary)")
                    .execute()

                val busySlots = mutableSetOf<AvailabilitySlot>()
                val eventsDetails = mutableListOf<GoogleCalendarEvent>()

                events.items?.forEach { event ->
                    if (event.start?.dateTime != null && event.end?.dateTime != null) {
                        busySlots.addAll(convertUtcToLocalSlot(event.start.dateTime, event.end.dateTime))
                        eventsDetails.addAll(convertUtcToLocalEvent(event))
                    }
                }

                _uiState.update {
                    it.copy(
                        googleBusySlots = busySlots,
                        googleEvents = eventsDetails,
                        isLoading = false
                    )
                }

            } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                Log.w("SchedulerViewModel", "User needs to grant calendar permission.", e)
                _uiState.update {
                    it.copy(
                        message = "Please go to the Profile tab and tap 'Link Google Calendar' to grant permission.",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("SchedulerViewModel", "Failed to sync GCal", e)
                _uiState.update { it.copy(message = "Failed to sync Google Calendar: ${e.message}", isLoading = false) }
            }
        }
    }


    fun addEventToCalendar(title: String, dayIndex: Int, startSlot: Int, durationSlots: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val account = GoogleSignIn.getLastSignedInAccount(context)

                if (account == null) {
                    _uiState.update { it.copy(message = "Google Account not linked. Please link in Profile.") }
                    return@launch
                }

                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    setOf(CalendarScopes.CALENDAR)
                ).setSelectedAccount(account.account)

                val service = Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                    .setApplicationName("MeetEase")
                    .build()

                val event = Event().apply {
                    summary = title

                    val calendar = java.util.Calendar.getInstance()
                    val targetDate = today.plusDays(dayIndex.toLong())

                    calendar.set(targetDate.year, targetDate.monthValue - 1, targetDate.dayOfMonth)

                    val startHour = (startSlot / SLOTS_PER_HOUR) + START_HOUR
                    val startMinute = (startSlot % SLOTS_PER_HOUR) * 30

                    val endSlot = startSlot + durationSlots
                    val endHour = (endSlot / SLOTS_PER_HOUR) + START_HOUR
                    val endMinute = (endSlot % SLOTS_PER_HOUR) * 30

                    calendar.set(java.util.Calendar.HOUR_OF_DAY, startHour)
                    calendar.set(java.util.Calendar.MINUTE, startMinute)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    val startDateTime = DateTime(calendar.time)

                    calendar.set(java.util.Calendar.HOUR_OF_DAY, endHour)
                    calendar.set(java.util.Calendar.MINUTE, endMinute)
                    val endDateTime = DateTime(calendar.time)

                    start = EventDateTime().apply {
                        dateTime = startDateTime
                        timeZone = TimeZone.getDefault().id
                    }
                    end = EventDateTime().apply {
                        dateTime = endDateTime
                        timeZone = TimeZone.getDefault().id
                    }
                }

                service.events().insert("primary", event).execute()

                syncGoogleCalendarBusySlots()
                _uiState.update { it.copy(message = "Event '$title' added to Google Calendar!") }

            } catch (e: Exception) {
                Log.e("SchedulerViewModel", "Failed to create calendar event", e)
                _uiState.update { it.copy(message = "Failed to create calendar event: ${e.message}") }
            }
        }
    }


    fun setDuration(durationSlots: Int) {
        _uiState.update { it.copy(selectedDurationSlots = durationSlots) }
    }


    fun findBestMeetingTimes() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = true, suggestions = emptyList()) }

            val members = _uiState.value.members
            val currentUser = _uiState.value.currentUser
            val googleBusySlots = _uiState.value.googleBusySlots
            val duration = _uiState.value.selectedDurationSlots
            val totalMembers = members.size

            if (totalMembers == 0) {
                _uiState.update { it.copy(isLoading = false, message = "No members in the group.") }
                return@launch
            }

            val overlapGrid = Array(NUM_DAYS_TO_SHOW) { IntArray(TOTAL_SLOTS_PER_DAY) { 0 } }

            for (member in members) {
                var memberAvailability = member.availability.toSet()

                if (member.id == currentUser?.id) {
                    memberAvailability = memberAvailability.filterNot { googleBusySlots.contains(it) }.toSet()
                }

                for (slot in memberAvailability) {
                    if (slot.dayIndex in 0 until NUM_DAYS_TO_SHOW && slot.slotIndex in 0 until TOTAL_SLOTS_PER_DAY) {
                        overlapGrid[slot.dayIndex][slot.slotIndex]++
                    }
                }
            }

            val suggestions = mutableListOf<MeetingSuggestion>()
            for (day in 0 until NUM_DAYS_TO_SHOW) {
                for (startSlot in 0..TOTAL_SLOTS_PER_DAY - duration) {
                    val endSlot = startSlot + duration - 1
                    var minAvailableCount = totalMembers

                    for (slot in startSlot..endSlot) {
                        val availableCount = overlapGrid[day][slot]
                        if (availableCount < minAvailableCount) {
                            minAvailableCount = availableCount
                        }
                    }

                    if (minAvailableCount > 0) {
                        val availableNames = members
                            .filter { member ->
                                val memberAvail = (
                                        if(member.id == currentUser?.id)
                                            member.availability.filterNot { googleBusySlots.contains(it) }
                                        else
                                            member.availability
                                        ).toSet()

                                (startSlot..endSlot).all { slotIndex ->
                                    memberAvail.contains(AvailabilitySlot(day, slotIndex))
                                }
                            }
                            .map { it.name }

                        if(availableNames.isNotEmpty()) {
                            suggestions.add(
                                MeetingSuggestion(
                                    dayIndex = day,
                                    startSlot = startSlot,
                                    durationSlots = duration,
                                    availableCount = availableNames.size,
                                    totalCount = totalMembers,
                                    availableMembers = availableNames
                                )
                            )
                        }
                    }
                }
            }

            val finalSuggestions = suggestions
                .distinctBy { "${it.dayIndex}-${it.startSlot}" }
                .sortedWith(
                    compareByDescending<MeetingSuggestion> { it.availableCount }
                        .thenBy { it.dayIndex }
                        .thenBy { it.startSlot }
                )
                .take(5)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    suggestions = finalSuggestions,
                    message = if (finalSuggestions.isEmpty()) "No suitable meeting times found for this duration." else null
                )
            }
        }
    }

    fun confirmMeeting(suggestion: MeetingSuggestion) {
        // Cancel any existing meeting reminder first
        meetingReminderScheduler.cancelMeetingReminder()
        
        val dayLabel = _uiState.value.dynamicDayLabels[suggestion.dayIndex]
        val startTime = slotToTime(suggestion.startSlot)
        val endTime = slotToTime(suggestion.startSlot + suggestion.durationSlots)
        val confirmedMeeting = ConfirmedMeeting(
            day = dayLabel,
            timeRange = "$startTime - $endTime",
            attendees = suggestion.availableMembers.map { MemberStatus(name = it, status = "Confirmed") }
        )
        _uiState.update {
            it.copy(
                confirmedMeeting = confirmedMeeting,
                suggestions = emptyList(),
                message = "Meeting Confirmed!"
            )
        }

        // Schedule reminder notification 10 minutes before meeting
        meetingReminderScheduler.scheduleMeetingReminder(
            meetingDay = dayLabel,
            meetingTimeRange = "$startTime - $endTime",
            attendeesCount = suggestion.availableMembers.size
        )

        addEventToCalendar(
            title = "MeetEase Meeting",
            dayIndex = suggestion.dayIndex,
            startSlot = suggestion.startSlot,
            durationSlots = suggestion.durationSlots
        )
    }

    private fun getCalendarDayOfWeek(dayAbbreviation: String): Int {
        return when (dayAbbreviation.take(3)) {
            "Sun" -> java.util.Calendar.SUNDAY
            "Mon" -> java.util.Calendar.MONDAY
            "Tue" -> java.util.Calendar.TUESDAY
            "Wed" -> java.util.Calendar.WEDNESDAY
            "Thu" -> java.util.Calendar.THURSDAY
            "Fri" -> java.util.Calendar.FRIDAY
            "Sat" -> java.util.Calendar.SATURDAY
            else -> java.util.Calendar.MONDAY
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun slotToTime(slot: Int): String {
        val hour = (slot / SLOTS_PER_HOUR) + START_HOUR
        val minute = (slot % SLOTS_PER_HOUR) * 30
        return String.format("%02d:%02d", hour, minute)
    }

    fun timeToSlot(time: String): Int {
        val parts = time.split(":")
        val hour = parts[0].toIntOrNull() ?: START_HOUR
        val minute = parts[1].toIntOrNull() ?: 0

        return (hour - START_HOUR) * SLOTS_PER_HOUR + (minute / (60 / SLOTS_PER_HOUR))
    }
}