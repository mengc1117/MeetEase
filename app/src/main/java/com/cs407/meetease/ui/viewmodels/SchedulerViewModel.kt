package com.cs407.meetease.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.data.AvailabilitySlot
import com.cs407.meetease.data.ConfirmedMeeting
import com.cs407.meetease.data.Member
import com.cs407.meetease.data.MemberStatus
import com.cs407.meetease.data.MeetingSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class SchedulerUiState(
    val members: List<Member> = emptyList(),
    val currentUser: Member? = null,
    val selectedDurationSlots: Int = 2, // 2 slots = 1 hour
    val suggestions: List<MeetingSuggestion> = emptyList(),
    val confirmedMeeting: ConfirmedMeeting? = null,
    val isLoading: Boolean = false,
    val message: String? = null
)

class SchedulerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SchedulerUiState())
    val uiState: StateFlow<SchedulerUiState> = _uiState.asStateFlow()

    companion object {
        val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val TIMES = List(16) { "${it + 8}:00" } // 8:00 to 23:00
        const val SLOTS_PER_HOUR = 2
        const val TOTAL_SLOTS_PER_DAY = 16 * SLOTS_PER_HOUR
    }

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val user = Member(id = "user_1", name = "You (Organizer)")
        val members = mutableListOf(
            user,
            Member(id = "user_2", name = "Alice Smith"),
            Member(id = "user_3", name = "Bob Johnson")
        )
        _uiState.update { it.copy(members = members, currentUser = user) }
        simulateAllAvailability()
    }

    fun simulateAllAvailability() {
        _uiState.update { currentState ->
            val updatedMembers = currentState.members.map { member ->
                if (member.id != currentState.currentUser?.id) {
                    member.availability = generateRandomAvailability()
                }
                member
            }
            currentState.copy(members = updatedMembers, message = "Simulated availability for all members.")
        }
    }

    private fun generateRandomAvailability(): MutableList<AvailabilitySlot> {
        val availability = mutableListOf<AvailabilitySlot>()
        val totalSlots = DAYS.size * TOTAL_SLOTS_PER_DAY
        val targetSlots = (totalSlots * (0.4 + Random.nextDouble() * 0.3)).toInt()

        for (i in 0 until targetSlots) {
            val day = Random.nextInt(DAYS.size)
            val slot = Random.nextInt(TOTAL_SLOTS_PER_DAY)
            val newSlot = AvailabilitySlot(day, slot)
            if (!availability.contains(newSlot)) {
                availability.add(newSlot)
            }
        }
        return availability
    }

    fun toggleAvailability(dayIndex: Int, slotIndex: Int) {
        _uiState.update { currentState ->
            val currentUser = currentState.currentUser ?: return
            val availability = currentUser.availability
            val slot = AvailabilitySlot(dayIndex, slotIndex)

            if (availability.contains(slot)) {
                availability.remove(slot)
            } else {
                availability.add(slot)
            }
            currentState.copy(currentUser = currentUser)
        }
    }

    fun setDuration(durationSlots: Int) {
        _uiState.update { it.copy(selectedDurationSlots = durationSlots) }
    }

    fun findBestMeetingTimes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, suggestions = emptyList()) }

            val members = _uiState.value.members
            val durationSlots = _uiState.value.selectedDurationSlots

            if (members.any { it.availability.isEmpty() }) {
                _uiState.update { it.copy(isLoading = false, message = "Some members have not submitted availability.") }
                return@launch
            }

            // 1. Create availability map: (Day-Slot) -> List<MemberId>
            val availabilityMap = mutableMapOf<String, List<String>>()
            for (day in DAYS.indices) {
                for (slot in 0 until TOTAL_SLOTS_PER_DAY) {
                    val key = "$day-$slot"
                    availabilityMap[key] = members
                        .filter { m -> m.availability.any { it.dayIndex == day && it.slotIndex == slot } }
                        .map { it.id }
                }
            }

            // 2. Find suggestions
            val suggestions = mutableListOf<MeetingSuggestion>()
            for (day in DAYS.indices) {
                for (startSlot in 0..(TOTAL_SLOTS_PER_DAY - durationSlots)) {
                    val windowSlots = (0 until durationSlots).map { "$day-${startSlot + it}" }
                    val commonMembers = windowSlots
                        .map { availabilityMap[it] ?: emptyList() }
                        .reduce { acc, list -> acc.intersect(list.toSet()).toList() }

                    if (commonMembers.isNotEmpty()) {
                        suggestions.add(
                            MeetingSuggestion(
                                dayIndex = day,
                                startSlot = startSlot,
                                durationSlots = durationSlots,
                                availableCount = commonMembers.size,
                                totalCount = members.size,
                                availableMembers = commonMembers.map { id -> members.first { it.id == id }.name }
                            )
                        )
                    }
                }
            }

            // 3. Rank suggestions
            val sortedSuggestions = suggestions.sortedWith(
                compareByDescending<MeetingSuggestion> { it.availableCount }
                    .thenBy { it.dayIndex }
                    .thenBy { it.startSlot }
            )

            _uiState.update { it.copy(isLoading = false, suggestions = sortedSuggestions.take(5)) }
        }
    }

    fun confirmMeeting(suggestion: MeetingSuggestion) {
        val day = DAYS[suggestion.dayIndex]
        val startTime = slotToTime(suggestion.startSlot)
        val endTime = slotToTime(suggestion.startSlot + suggestion.durationSlots)

        val attendees = _uiState.value.members.map { member ->
            val isAvailable = suggestion.availableMembers.contains(member.name)
            MemberStatus(
                name = member.name,
                status = if (isAvailable) "Confirmed" else "Pending",
                eta = if (member.name == "Bob Johnson" && isAvailable) "Running Late (ETA: 14:08)" else null
            )
        }

        _uiState.update {
            it.copy(
                confirmedMeeting = ConfirmedMeeting(
                    day = day,
                    timeRange = "$startTime - $endTime",
                    attendees = attendees
                )
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun slotToTime(slot: Int): String {
        val hour = (slot / SLOTS_PER_HOUR) + 8
        val minute = (slot % SLOTS_PER_HOUR) * 30
        return String.format("%02d:%02d", hour, minute)
    }
}
