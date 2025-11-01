package com.cs407.meetease.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.data.ConfirmedMeeting
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
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

class RemindersViewModel : ViewModel() {

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
                    // Handle error
                }
            }
        }
    }

    fun toggleLocationSharing(meeting: ConfirmedMeeting?) {
        if (meeting == null || groupId == null) return
        val userId = auth.currentUser?.uid ?: return

        val isCurrentlySharing = _uiState.value.isSharingLocation
        if (!isCurrentlySharing) {
            startLocationSharing(groupId!!, userId)
        } else {
            stopLocationSharing(groupId!!, userId)
        }
    }

    private fun startLocationSharing(groupId: String, userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSharingLocation = true, sharingStatus = "Starting location service...") }

            try {
                val simulatedLocation = GeoPoint(43.0754, -89.4043)
                db.collection("groups").document(groupId)
                    .collection("members").document(userId)
                    .update("location", simulatedLocation)
                    .await()

                delay(1500) // 模拟启动服务的延迟
                _uiState.update { it.copy(sharingStatus = "Live location is ON") }

            } catch (e: Exception) {
                _uiState.update { it.copy(isSharingLocation = false, sharingStatus = "Failed to start sharing: ${e.message}") }
            }
        }
    }

    private fun stopLocationSharing(groupId: String, userId: String) {
        viewModelScope.launch {

            try {
                db.collection("groups").document(groupId)
                    .collection("members").document(userId)
                    .update("location", FieldValue.delete()) // 删除位置字段
                    .await()

                _uiState.update { it.copy(isSharingLocation = false, sharingStatus = "Tap to share live location") }

            } catch (e: Exception) {
                _uiState.update { it.copy(sharingStatus = "Failed to stop sharing: ${e.message}") }
            }
        }
    }
}