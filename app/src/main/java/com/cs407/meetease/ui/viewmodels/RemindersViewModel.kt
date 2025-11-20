package com.cs407.meetease.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.LocationService
import com.cs407.meetease.data.ConfirmedMeeting
import com.cs407.meetease.data.Group
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class RemindersUiState(
    val confirmedMeeting: ConfirmedMeeting? = null,
    val isSharingLocation: Boolean = false,
    val sharingStatus: String = "Tap to share live location",
    val isOrganizer: Boolean = false,
    val message: String? = null
)

class RemindersViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RemindersUiState())
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var groupId: String? = null

    init {
        loadGroupIdAndListen()
    }

    private fun loadGroupIdAndListen() {
        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                try {
                    val userDoc = db.collection("users").document(userId).get().await()
                    groupId = userDoc.getString("currentGroupId")

                    if (groupId != null) {
                        listenForGroup(groupId!!, userId)
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun listenForGroup(groupId: String, userId: String) {
        db.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    val group = snapshot.toObject<Group>()
                    val isOrganizer = group?.organizerId == userId
                    _uiState.update {
                        it.copy(
                            confirmedMeeting = group?.confirmedMeeting,
                            isOrganizer = isOrganizer
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(confirmedMeeting = null, isOrganizer = false, message = "Group deleted by organizer.")
                    }
                }
            }
    }

    fun cancelMeeting() {
        val gId = groupId ?: return
        val meeting = _uiState.value.confirmedMeeting ?: return

        viewModelScope.launch {
            if (meeting.googleEventId != null) {
                val success = deleteEventFromCalendar(meeting.googleEventId)
                if (!success) {
                    _uiState.update { it.copy(message = "Could not remove from Google Calendar (Network/Auth error), but removing from App.") }
                }
            }

            try {
                db.collection("groups").document(gId)
                    .update("confirmedMeeting", null)
                    .await()

                _uiState.update { it.copy(message = "Meeting Cancelled.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Failed to cancel meeting: ${e.message}") }
            }
        }
    }

    private suspend fun deleteEventFromCalendar(eventId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext false

                val credential = GoogleAccountCredential.usingOAuth2(context, setOf(CalendarScopes.CALENDAR)).setSelectedAccount(account.account)
                val service = Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential).setApplicationName("MeetEase").build()

                service.events().delete("primary", eventId).execute()
                return@withContext true
            } catch (e: Exception) {
                Log.e("RemindersViewModel", "Failed to delete calendar event", e)
                return@withContext false
            }
        }
    }

    fun toggleLocationSharing(meeting: ConfirmedMeeting?) {
        if (meeting == null || groupId == null) return
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
        }.let { context.startService(it) }
        _uiState.update { it.copy(isSharingLocation = true, sharingStatus = "Live location is ON") }
    }

    private fun stopLocationSharing() {
        val context = getApplication<Application>().applicationContext
        Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }.let { context.startService(it) }
        _uiState.update { it.copy(isSharingLocation = false, sharingStatus = "Tap to share live location") }
    }

    fun showPermissionError() {
        _uiState.update { it.copy(sharingStatus = "Location permission is required to share location.") }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}