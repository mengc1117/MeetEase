package com.cs407.meetease.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.cs407.meetease.data.Member
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MembersUiState(
    val members: List<Member> = emptyList(),
    val contacts: List<Member> = emptyList(),
    val message: String? = null
)

class MembersViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MembersUiState())
    val uiState: StateFlow<MembersUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val currentMembers = listOf(
            Member(id = "user_1", name = "You (Organizer)"),
            Member(id = "user_2", name = "Alice Smith"),
            Member(id = "user_3", name = "Bob Johnson")
        )

        val mockContacts = listOf(
            Member(id = "contact_1", name = "Charlie Brown"),
            Member(id = "contact_2", name = "David Lee"),
            Member(id = "contact_3", name = "Emily White")
        )

        _uiState.update {
            it.copy(members = currentMembers, contacts = mockContacts)
        }
    }

    fun addMemberFromContacts(contact: Member) {
        _uiState.update { currentState ->
            if (currentState.members.any { it.name == contact.name }) {
                return@update currentState.copy(message = "${contact.name} is already in the group.")
            }

            val newMembersList = currentState.members + contact.copy(id = "user_${currentState.members.size + 1}")
            val newContactsList = currentState.contacts.filter { it.id != contact.id }

            currentState.copy(
                members = newMembersList,
                contacts = newContactsList,
                message = "${contact.name} added to the group."
            )
        }
    }

    fun removeMember(member: Member) {
        _uiState.update { currentState ->
            if (member.id == "user_1") { // Cannot remove organizer
                return@update currentState.copy(message = "Cannot remove the organizer.")
            }

            val newMembersList = currentState.members.filter { it.id != member.id }
            // Optionally add back to contacts if they came from there

            currentState.copy(
                members = newMembersList,
                message = "${member.name} removed from the group."
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
