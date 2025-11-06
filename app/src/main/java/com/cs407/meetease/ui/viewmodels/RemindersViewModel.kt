package com.cs407.meetease.ui.viewmodels

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.LocationService
import com.cs407.meetease.data.ConfirmedMeeting
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class RemindersUiState(
    val isSharingLocation: Boolean = false,
    val sharingStatus: String = "Tap to share live location"
)

class RemindersViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RemindersUiState())
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var groupId: String? = null

    init {
        loadGroupId()
    }

    private fun loadGroupId() {
        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                try {
                    val userDoc = db.collection("users").document(userId).get().await()
                    groupId = userDoc.getString("groupId")
                } catch (e: Exception) {

                }
            }
        }
    }

    fun toggleLocationSharing(meeting: ConfirmedMeeting?) {
        if (meeting == null || groupId == null) return
        val userId = auth.currentUser?.uid ?: return

        val isCurrentlySharing = _uiState.value.isSharingLocation
        if (!isCurrentlySharing) {
            startLocationSharing()
        } else {
            stopLocationSharing()
        }
    }

    private fun startLocationSharing() {
        val context = getApplication<Application>().applicationContext
        Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }.let {
            context.startService(it)
        }
        _uiState.update {
            it.copy(
                isSharingLocation = true,
                sharingStatus = "Live location is ON"
            )
        }
    }

    private fun stopLocationSharing() {
        val context = getApplication<Application>().applicationContext
        Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }.let {
            context.startService(it)
        }
        _uiState.update {
            it.copy(
                isSharingLocation = false,
                sharingStatus = "Tap to share live location"
            )
        }
    }

    fun showPermissionError() {
        _uiState.update {
            it.copy(sharingStatus = "Location permission is required to share location.")
        }
    }
}