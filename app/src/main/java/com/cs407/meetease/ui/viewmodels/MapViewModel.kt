package com.cs407.meetease.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.data.Member
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
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
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class MapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var groupId: String? = null

    init {
        loadUserAndGroupData()
    }

    private fun loadUserAndGroupData() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.update { it.copy(errorMessage = "User not logged in.", isLoading = false) }
                return@launch
            }

            try {

                val userDoc = db.collection("users").document(userId).get().await()
                groupId = userDoc.getString("groupId")
                if (groupId == null) {
                    _uiState.update { it.copy(errorMessage = "User has no group.", isLoading = false) }
                    return@launch
                }


                listenForMemberLocations(groupId!!)

            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
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
}