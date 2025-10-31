package com.cs407.meetease.data

data class Member(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    var availability: MutableList<AvailabilitySlot> = mutableListOf()
)

data class AvailabilitySlot(
    val dayIndex: Int, // 0 = Monday, 6 = Sunday
    val slotIndex: Int // 0 = 8:00, 1 = 8:30, ...
)

data class MeetingSuggestion(
    val dayIndex: Int,
    val startSlot: Int,
    val durationSlots: Int,
    val availableCount: Int,
    val totalCount: Int,
    val availableMembers: List<String>
)

data class ConfirmedMeeting(
    val day: String,
    val timeRange: String,
    val attendees: List<MemberStatus>
)

data class MemberStatus(
    val name: String,
    val status: String, // "Confirmed", "Running Late", "Pending"
    val eta: String? = null // "ETA: 14:08"
)
