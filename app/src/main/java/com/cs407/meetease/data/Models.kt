package com.cs407.meetease.data

import com.google.firebase.firestore.GeoPoint
data class Group(
    val groupId: String = "",
    val groupName: String = "My Team",
    val organizerId: String = ""
)

data class Member(
    val id: String = "", // Added default value
    val name: String = "", // Added default value
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
    val dayIndex: Int = 0, // Added default value
    val slotIndex: Int = 0 // Added default value
)

data class MeetingSuggestion(
    val dayIndex: Int = 0,
    val startSlot: Int = 0,
    val durationSlots: Int = 0,
    val availableCount: Int = 0,
    val totalCount: Int = 0,
    val availableMembers: List<String> = emptyList() // Added default value
)

data class ConfirmedMeeting(
    val day: String = "", // Added default value
    val timeRange: String = "", // Added default value
    val attendees: List<MemberStatus> = emptyList() // Added default value
)

data class MemberStatus(
    val name: String = "", // Added default value
    val status: String = "", // Added default value
    val eta: String? = null
)