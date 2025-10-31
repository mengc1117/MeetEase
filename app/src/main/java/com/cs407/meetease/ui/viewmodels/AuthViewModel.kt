package com.cs407.meetease.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val authSuccess: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signIn(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // --- BACKEND HOOK ---
            // In a real app, you would uncomment and use this:
            // try {
            //     val auth = FirebaseAuth.getInstance()
            //     auth.signInWithEmailAndPassword(email, pass).await()
            //     _uiState.update { it.copy(isLoading = false, authSuccess = true) }
            // } catch (e: Exception) {
            //     _uiState.update { it.copy(isLoading = false, error = e.message) }
            // }

            // Simulation
            delay(1500)
            if (pass == "password123") {
                _uiState.update { it.copy(isLoading = false, authSuccess = true) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Invalid credentials (use 'password123')"
                    )
                }
            }
        }
    }

    fun signUp(email: String, pass: String, confirmPass: String) {
        viewModelScope.launch {
            if (pass != confirmPass) {
                _uiState.update { it.copy(error = "Passwords do not match.") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }

            // --- BACKEND HOOK ---
            // In a real app, you would uncomment and use this:
            // try {
            //     val auth = FirebaseAuth.getInstance()
            //     auth.createUserWithEmailAndPassword(email, pass).await()
            //     // You would also create a user document in Firestore here
            //     _uiState.update { it.copy(isLoading = false, authSuccess = true) }
            // } catch (e: Exception) {
            //     _uiState.update { it.copy(isLoading = false, error = e.message) }
            // }

            // Simulation
            delay(1500)
            _uiState.update { it.copy(isLoading = false, authSuccess = true) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}