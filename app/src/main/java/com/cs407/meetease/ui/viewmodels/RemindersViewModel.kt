package com.cs407.meetease.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.data.ConfirmedMeeting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemindersUiState(
    val isSharingLocation: Boolean = false,
    val sharingStatus: String = "Tap to share live location"
)

class RemindersViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RemindersUiState())
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()

    fun toggleLocationSharing(meeting: ConfirmedMeeting?) {
        if (meeting == null) return

        val isCurrentlySharing = _uiState.value.isSharingLocation
        if (!isCurrentlySharing) {
            startLocationSharing()
        } else {
            stopLocationSharing()
        }
    }

    private fun startLocationSharing() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSharingLocation = true, sharingStatus = "Starting location service...") }

            // --- BACKEND HOOK ---
            // In a real app, you would:
            // 1. Check for FINE_LOCATION and BACKGROUND_LOCATION permissions.
            // 2. Start a Foreground Service (to run in the background).
            // 3. This service would get location updates from FusedLocationProviderClient.
            // 4. It would then write the Lat/Lng to Firebase Firestore:
            //    e.g., db.collection("meetings").document(meetingId)
            //            .collection("attendees").document(userId)
            //            .update("location", GeoPoint(lat, lng))

            // Simulation
            delay(1500)
            _uiState.update { it.copy(sharingStatus = "Live location is ON") }
        }
    }

    private fun stopLocationSharing() {
        viewModelScope.launch {
            // --- BACKEND HOOK ---
            // 1. Stop the Foreground Service.
            // 2. Clear the location data from Firestore.

            _uiState.update { it.copy(isSharingLocation = false, sharingStatus = "Tap to share live location") }
        }
    }
}
