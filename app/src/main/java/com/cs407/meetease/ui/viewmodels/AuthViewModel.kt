package com.cs407.meetease.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.data.Group
import com.cs407.meetease.data.Member
import com.cs407.meetease.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val authSuccess: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore

    fun signIn(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(error = "Email and password cannot be empty.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                auth.signInWithEmailAndPassword(email, pass).await()
                _uiState.update { it.copy(isLoading = false, authSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun signUp(email: String, username: String, pass: String, confirmPass: String) {
        if (email.isBlank() || username.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(error = "All fields are required.") }
            return
        }
        if (pass != confirmPass) {
            _uiState.update { it.copy(error = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
                val userId = authResult.user?.uid ?: throw Exception("Failed to create user.")

                val newGroupRef = db.collection("groups").document()
                val newGroup = Group(
                    groupId = newGroupRef.id,
                    groupName = "$username's Team",
                    organizerId = userId
                )
                newGroupRef.set(newGroup).await()

                val userDoc = User(uid = userId, email = email, groupId = newGroupRef.id)
                db.collection("users").document(userId).set(userDoc).await()

                val selfAsMember = Member(id = userId, name = "$username (Organizer)")
                db.collection("groups").document(newGroupRef.id)
                    .collection("members").document(userId)
                    .set(selfAsMember).await()

                _uiState.update { it.copy(isLoading = false, authSuccess = true) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}