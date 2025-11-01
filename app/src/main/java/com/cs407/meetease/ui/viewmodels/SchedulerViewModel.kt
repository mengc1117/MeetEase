package com.cs407.meetease.ui.viewmodels

import android.util.Log // Import Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.data.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var groupId: String? = null
    private val userId = auth.currentUser?.uid

    companion object {
        val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val TIMES = List(16) { "${it + 8}:00" } // 8:00 to 23:00
        const val SLOTS_PER_HOUR = 2
        const val TOTAL_SLOTS_PER_DAY = 16 * SLOTS_PER_HOUR
    }

    init {
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
                val currentUser = members.firstOrNull { it.id == userId }

                viewModelScope.launch {
                    val membersWithAvailability = members.map { member ->
                        member.availability = loadAvailabilityForMember(groupId, member.id)
                        member
                    }
                    _uiState.update {
                        it.copy(
                            members = membersWithAvailability,
                            currentUser = currentUser,
                            isLoading = false
                        )
                    }
                }
            }
    }

    //
    // --- THIS FUNCTION IS NOW FIXED ---
    //
    private suspend fun loadAvailabilityForMember(groupId: String, memberId: String): MutableList<AvailabilitySlot> {
        return try {
            val doc = db.collection("groups").document(groupId)
                .collection("availability").document(memberId).get().await()

            if (!doc.exists()) {
                return mutableListOf() // Document doesn't exist, return empty list
            }

            // This is the correct parsing logic (from your old catch block)
            val slotsData = doc.get("slots") as? List<HashMap<String, Any>>
            slotsData?.map {
                AvailabilitySlot(
                    (it["dayIndex"] as Long).toInt(),
                    (it["slotIndex"] as Long).toInt()
                )
            }?.toMutableList() ?: mutableListOf()

        } catch (e: Exception) {
            // The .get().await() failed (e.g., permissions)
            // Log the error and return an empty list instead of crashing
            Log.e("SchedulerViewModel", "Error loading availability: ${e.message}")
            mutableListOf()
        }
    }


    fun toggleAvailability(dayIndex: Int, slotIndex: Int) {
        val gId = groupId ?: return
        val uId = userId ?: return
        val slot = AvailabilitySlot(dayIndex, slotIndex)

        val availability = _uiState.value.currentUser?.availability ?: mutableListOf()
        val isAvailable = availability.contains(slot)

        viewModelScope.launch {
            try {
                val docRef = db.collection("groups").document(gId).collection("availability").document(uId)

                if (isAvailable) {
                    // This was correct
                    availability.remove(slot)
                    docRef.update("slots", FieldValue.arrayRemove(slot)).await()
                } else {
                    // This was correct
                    availability.add(slot)
                    // Use set with merge option or update, arrayUnion is safer
                    docRef.set(mapOf("slots" to FieldValue.arrayUnion(slot)), com.google.firebase.firestore.SetOptions.merge()).await()
                }

                _uiState.update {
                    val updatedUser = it.currentUser?.copy()
                    updatedUser?.availability = availability
                    it.copy(currentUser = updatedUser)
                }
            } catch (e: Exception) {
                // Check if doc exists on first write
                if (e.message?.contains("NOT_FOUND") == true && !isAvailable) {
                    try {
                        val docRef = db.collection("groups").document(gId).collection("availability").document(uId)
                        docRef.set(mapOf("slots" to listOf(slot))).await() // Create doc

                        // Manually update state again
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

    fun setDuration(durationSlots: Int) {
        _uiState.update { it.copy(selectedDurationSlots = durationSlots) }
    }


    fun findBestMeetingTimes() {
        // ... (your logic)
    }

    fun confirmMeeting(suggestion: MeetingSuggestion) {
        // ... (your logic)
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