package com.cs407.meetease.data

import com.google.firebase.firestore.GeoPoint

data class Group(
    val groupId: String = "",
    val groupName: String = "My Team",
    val organizerId: String = ""
)

data class Member(
    val id: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    var availability: MutableList<AvailabilitySlot> = mutableListOf(),
    val location: GeoPoint? = null
)
data class User(
    val uid: String = "",
    val email: String = "",
    val groupId: String? = null
)


data class AvailabilitySlot(
    val dayIndex: Int = 0,
    val slotIndex: Int = 0
)

data class MeetingSuggestion(
    val dayIndex: Int = 0,
    val startSlot: Int = 0,
    val durationSlots: Int = 0,
    val availableCount: Int = 0,
    val totalCount: Int = 0,
    val availableMembers: List<String> = emptyList()
)

data class ConfirmedMeeting(
    val day: String = "",
    val timeRange: String = "",
    val attendees: List<MemberStatus> = emptyList()
)

data class MemberStatus(
    val name: String = "",
    val status: String = "",
    val eta: String? = null
)

data class GoogleCalendarEvent(
    val dayIndex: Int = 0,
    val startSlot: Int = 0,
    val endSlot: Int = 0,
    val title: String = "Busy",
    val timeRange: String = ""
)