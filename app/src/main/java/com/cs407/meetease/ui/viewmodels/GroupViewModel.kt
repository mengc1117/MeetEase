package com.cs407.meetease.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.data.Group
import com.cs407.meetease.data.Member
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class GroupUiState(
    val myGroups: List<Group> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val groupSelected: Boolean = false
)

class GroupViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    fun loadUserGroups() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(userId).get().await()
                val groupIds = userDoc.get("groupIds") as? List<String> ?: emptyList()

                if (groupIds.isEmpty()) {
                    _uiState.update { it.copy(myGroups = emptyList(), isLoading = false) }
                    return@launch
                }

                val validGroups = mutableListOf<Group>()
                val invalidIds = mutableListOf<String>()

                for (gid in groupIds) {
                    val gDoc = db.collection("groups").document(gid).get().await()
                    if (gDoc.exists()) {
                        gDoc.toObject(Group::class.java)?.let { validGroups.add(it) }
                    } else {
                        invalidIds.add(gid)
                    }
                }

                if (invalidIds.isNotEmpty()) {
                    db.collection("users").document(userId)
                        .update("groupIds", FieldValue.arrayRemove(*invalidIds.toTypedArray()))
                }

                _uiState.update { it.copy(myGroups = validGroups, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load groups: ${e.message}", isLoading = false) }
            }
        }
    }

    fun createGroup(groupName: String) {
        val userId = auth.currentUser?.uid ?: return
        val userEmail = auth.currentUser?.email ?: ""

        if (groupName.isBlank()) {
            _uiState.update { it.copy(error = "Group name cannot be empty.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val userDocSnapshot = db.collection("users").document(userId).get().await()
                val dbName = userDocSnapshot.getString("name")

                val finalName = when {
                    !dbName.isNullOrBlank() -> dbName
                    userEmail.isNotBlank() -> userEmail.split("@")[0]
                    else -> "User_${userId.take(4)}"
                }

                val newGroupRef = db.collection("groups").document()
                val newGroup = Group(
                    groupId = newGroupRef.id,
                    groupName = groupName,
                    organizerId = userId
                )
                newGroupRef.set(newGroup).await()

                val selfAsMember = Member(id = userId, name = finalName, email = userEmail)
                newGroupRef.collection("members").document(userId).set(selfAsMember).await()

                val userRef = db.collection("users").document(userId)
                db.runBatch { batch ->
                    batch.update(userRef, "groupIds", FieldValue.arrayUnion(newGroupRef.id))
                    batch.update(userRef, "currentGroupId", newGroupRef.id)
                }.await()

                _uiState.update { it.copy(groupSelected = true, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun joinGroup(targetGroupId: String) {
        val userId = auth.currentUser?.uid ?: return
        val userEmail = auth.currentUser?.email ?: ""
        val cleanedId = targetGroupId.trim()

        if (cleanedId.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a valid Group ID.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val groupDoc = db.collection("groups").document(cleanedId).get().await()

                if (!groupDoc.exists()) {
                    _uiState.update { it.copy(error = "Group not found.", isLoading = false) }
                    return@launch
                }

                val userDoc = db.collection("users").document(userId).get().await()
                val existingIds = userDoc.get("groupIds") as? List<String> ?: emptyList()
                if (existingIds.contains(cleanedId)) {
                    selectGroup(cleanedId)
                    return@launch
                }

                val dbName = userDoc.getString("name")
                val finalName = when {
                    !dbName.isNullOrBlank() -> dbName
                    userEmail.isNotBlank() -> userEmail.split("@")[0]
                    else -> "User_${userId.take(4)}"
                }

                val newMember = Member(id = userId, name = finalName, email = userEmail)

                db.collection("groups").document(cleanedId)
                    .collection("members").document(userId)
                    .set(newMember).await()

                db.collection("users").document(userId)
                    .update("groupIds", FieldValue.arrayUnion(cleanedId)).await()

                selectGroup(cleanedId)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to join: ${e.message}", isLoading = false) }
            }
        }
    }

    fun leaveGroup(groupId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val groupRef = db.collection("groups").document(groupId)
                val groupSnap = groupRef.get().await()
                val group = groupSnap.toObject(Group::class.java)

                if (group?.confirmedMeeting != null) {
                    val memberSnap = groupRef.collection("members").document(userId).get().await()
                    val memberName = memberSnap.getString("name")

                    if (memberName != null) {
                        val updatedAttendees = group.confirmedMeeting.attendees.filter { it.name != memberName }
                        val updatedMeeting = group.confirmedMeeting.copy(attendees = updatedAttendees)
                        groupRef.update("confirmedMeeting", updatedMeeting).await()
                    }
                }

                groupRef.collection("members").document(userId).delete().await()

                db.collection("users").document(userId)
                    .update("groupIds", FieldValue.arrayRemove(groupId)).await()

                val userDoc = db.collection("users").document(userId).get().await()
                if (userDoc.getString("currentGroupId") == groupId) {
                    db.collection("users").document(userId)
                        .update("currentGroupId", null).await()
                }

                loadUserGroups()
                _uiState.update { it.copy(message = "You have left the group.") }

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to leave group: ${e.message}", isLoading = false) }
            }
        }
    }

    fun deleteGroup(groupId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val groupDoc = db.collection("groups").document(groupId).get().await()
                val group = groupDoc.toObject(Group::class.java)

                if (group?.confirmedMeeting?.googleEventId != null) {
                    deleteEventFromCalendar(group.confirmedMeeting.googleEventId)
                }

                db.collection("groups").document(groupId).delete().await()

                db.collection("users").document(userId)
                    .update("groupIds", FieldValue.arrayRemove(groupId)).await()

                val userDocUpdated = db.collection("users").document(userId).get().await()
                if (userDocUpdated.getString("currentGroupId") == groupId) {
                    db.collection("users").document(userId)
                        .update("currentGroupId", null).await()
                }

                loadUserGroups()
                _uiState.update { it.copy(message = "Group dissolved.") }

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete group: ${e.message}", isLoading = false) }
            }
        }
    }

    private suspend fun deleteEventFromCalendar(eventId: String) {
        withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext

                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    setOf(CalendarScopes.CALENDAR)
                ).setSelectedAccount(account.account)

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("MeetEase").build()

                service.events().delete("primary", eventId).execute()
            } catch (e: Exception) {
            }
        }
    }

    fun selectGroup(groupId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(userId)
                    .update("currentGroupId", groupId).await()

                _uiState.update { it.copy(groupSelected = true, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun resetSelection() {
        _uiState.update { it.copy(groupSelected = false) }
    }
}