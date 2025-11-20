package com.cs407.meetease.ui.viewmodels

import android.app.Application
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.meetease.data.Group
import com.cs407.meetease.data.Member
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class MembersUiState(
    val members: List<Member> = emptyList(),
    val contacts: List<Member> = emptyList(),
    val message: String? = null,
    val isLoading: Boolean = true,
    val isCurrentUserOrganizer: Boolean = false
)

class MembersViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MembersUiState())
    val uiState: StateFlow<MembersUiState> = _uiState.asStateFlow()

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var groupId: String? = null
    private var currentUserId: String? = auth.currentUser?.uid

    init {
        loadUserAndGroupData()
    }

    private fun loadUserAndGroupData() {
        viewModelScope.launch {
            if (currentUserId == null) {
                _uiState.update { it.copy(message = "User not logged in.", isLoading = false) }
                return@launch
            }

            try {
                val userDoc = db.collection("users").document(currentUserId!!).get().await()
                groupId = userDoc.getString("currentGroupId")
                if (groupId == null) {
                    _uiState.update { it.copy(message = "User has no group.", isLoading = false) }
                    return@launch
                }

                listenForGroupInfo(groupId!!)
                listenForMembers(groupId!!)

            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message, isLoading = false) }
            }
        }
    }

    fun getGroupId(): String? = groupId

    private fun listenForGroupInfo(groupId: String) {
        db.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val group = snapshot.toObject<Group>()
                    val isOrganizer = group?.organizerId == currentUserId
                    _uiState.update { it.copy(isCurrentUserOrganizer = isOrganizer) }
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
                if (snapshot != null) {
                    val members = snapshot.toObjects<Member>()
                    _uiState.update { it.copy(members = members, isLoading = false) }
                }
            }
    }

    fun loadSystemContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val contactsList = mutableListOf<Member>()
            val contentResolver = getApplication<Application>().contentResolver
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC"
            )
            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val contactNames = mutableSetOf<String>()
                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val name = it.getString(nameIndex)
                    if (name != null && !contactNames.contains(name)) {
                        contactNames.add(name)
                        contactsList.add(Member(id = id, name = name))
                    }
                }
            }
            _uiState.update { it.copy(contacts = contactsList) }
        }
    }

    fun addMemberFromContacts(contact: Member) {
        val gId = groupId ?: return
        viewModelScope.launch {
            if (_uiState.value.members.any { it.name == contact.name }) {
                _uiState.update { it.copy(message = "${contact.name} is already in the group.") }
                return@launch
            }
            val newMember = Member(id = "contact_${contact.id}", name = contact.name)
            try {
                db.collection("groups").document(gId).collection("members")
                    .document(newMember.id).set(newMember).await()
                _uiState.update { it.copy(message = "${contact.name} added.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message) }
            }
        }
    }

    fun removeMember(member: Member) {
        val gId = groupId ?: return

        if (!_uiState.value.isCurrentUserOrganizer) {
            _uiState.update { it.copy(message = "Only the organizer can remove members.") }
            return
        }

        if (member.id == currentUserId) {
            _uiState.update { it.copy(message = "You cannot kick yourself. Use 'Delete Group' in My Groups.") }
            return
        }

        viewModelScope.launch {
            try {
                db.runBatch { batch ->
                    val memberRef = db.collection("groups").document(gId)
                        .collection("members").document(member.id)
                    batch.delete(memberRef)

                    if (!member.id.startsWith("contact_")) {
                        val userRef = db.collection("users").document(member.id)
                        batch.update(userRef, "groupIds", FieldValue.arrayRemove(gId))
                    }
                }.await()

                removeMemberFromMeetingAttendees(gId, member.name)

                _uiState.update { it.copy(message = "${member.name} removed.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Failed to remove: ${e.message}") }
            }
        }
    }

    private suspend fun removeMemberFromMeetingAttendees(groupId: String, memberName: String) {
        try {
            val groupRef = db.collection("groups").document(groupId)
            val snapshot = groupRef.get().await()
            val group = snapshot.toObject<Group>()

            if (group?.confirmedMeeting != null) {
                val updatedAttendees = group.confirmedMeeting.attendees.filter { it.name != memberName }
                val updatedMeeting = group.confirmedMeeting.copy(attendees = updatedAttendees)
                groupRef.update("confirmedMeeting", updatedMeeting).await()
            }
        } catch (e: Exception) {
        }
    }

    fun showErrorMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}