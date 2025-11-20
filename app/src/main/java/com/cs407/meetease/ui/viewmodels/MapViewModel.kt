package com.cs407.meetease.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.data.Group
import com.cs407.meetease.data.Member
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.GeoPoint
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

data class MapUiState(
    val membersWithLocation: List<Member> = emptyList(),
    val meetingDestination: GeoPoint? = null,
    val isOrganizer: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var groupId: String? = null
    private val currentUserId = auth.currentUser?.uid

    init {
        loadUserAndGroupData()
    }

    private fun loadUserAndGroupData() {
        viewModelScope.launch {
            if (currentUserId == null) {
                _uiState.update { it.copy(errorMessage = "User not logged in.", isLoading = false) }
                return@launch
            }

            try {
                val userDoc = db.collection("users").document(currentUserId).get().await()
                groupId = userDoc.getString("currentGroupId")
                if (groupId == null) {
                    _uiState.update { it.copy(errorMessage = "User has no group.", isLoading = false) }
                    return@launch
                }

                listenForMemberLocations(groupId!!)
                listenForGroupInfo(groupId!!)

            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
            }
        }
    }

    private fun listenForGroupInfo(groupId: String) {
        db.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val group = snapshot.toObject<Group>()
                    val destination = group?.confirmedMeeting?.destination
                    val isOrganizer = group?.organizerId == currentUserId

                    _uiState.update {
                        it.copy(
                            meetingDestination = destination,
                            isOrganizer = isOrganizer
                        )
                    }
                }
            }
    }

    private fun listenForMemberLocations(groupId: String) {
        db.collection("groups").document(groupId).collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(errorMessage = error.message, isLoading = false) }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val members = snapshot.toObjects<Member>()
                    _uiState.update {
                        it.copy(
                            membersWithLocation = members.filter { m -> m.location != null },
                            isLoading = false
                        )
                    }
                }
            }
    }

    fun setMeetingDestination(latLng: LatLng) {
        val gId = groupId ?: return
        if (!_uiState.value.isOrganizer) return

        viewModelScope.launch {
            try {
                val geoPoint = GeoPoint(latLng.latitude, latLng.longitude)

                db.collection("groups").document(gId)
                    .update("confirmedMeeting.destination", geoPoint)
                    .await()

            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to set destination: ${e.message}") }
            }
        }
    }
}