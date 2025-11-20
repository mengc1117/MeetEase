package com.cs407.meetease.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cs407.meetease.data.Member
import com.cs407.meetease.ui.theme.AppGray
import com.cs407.meetease.ui.viewmodels.MembersViewModel
import androidx.compose.foundation.layout.Column


@Composable
fun MembersScreen(viewModel: MembersViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showContactsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.loadSystemContacts()
            showContactsDialog = true
        } else {
            viewModel.clearMessage()
            viewModel.showErrorMessage("Contacts permission is required to add members.")
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = "Invite & Manage",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                ActionCard(
                    title = "Add from Contacts",
                    icon = Icons.Filled.Add,
                    onClick = {
                        when (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CONTACTS
                        )) {
                            PackageManager.PERMISSION_GRANTED -> {
                                viewModel.loadSystemContacts()
                                showContactsDialog = true
                            }
                            else -> {
                                launcher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        }
                    }
                )

                ActionCard(
                    title = "Share Invite Code",
                    icon = Icons.Filled.Share,
                    onClick = {
                        val gid = viewModel.getGroupId()
                        if (gid != null) {
                            clipboardManager.setText(AnnotatedString(gid))
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Join my group on MeetEase! ID: $gid")
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Group ID")
                            context.startActivity(shareIntent)
                            Toast.makeText(context, "Group ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.showErrorMessage("Group ID not loaded yet.")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Current Members (${uiState.members.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(uiState.members) { member ->
                MemberCard(
                    member = member,
                    isCurrentUserOrganizer = uiState.isCurrentUserOrganizer,
                    showDelete = uiState.isCurrentUserOrganizer && !member.name.contains("(Organizer)"),
                    onRemove = { viewModel.removeMember(member) }
                )
            }
        }
    }

    if (showContactsDialog) {
        ContactsDialog(
            contacts = uiState.contacts,
            onDismiss = { showContactsDialog = false },
            onContactSelected = {
                viewModel.addMemberFromContacts(it)
                showContactsDialog = false
            }
        )
    }
}

@Composable
fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppGray)
    ) {
        Icon(icon, contentDescription = title, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title)
    }
}

@Composable
fun MemberCard(
    member: Member,
    isCurrentUserOrganizer: Boolean,
    showDelete: Boolean,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = if (member.name.isNotBlank()) member.name.first().toString().uppercase() else "?"
                    Text(
                        text = initial,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (member.name.isNotBlank()) member.name else "Unknown Member",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (member.name.contains("(Organizer)")) {
                        Text(
                            "Admin",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (showDelete) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove Member",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@Composable
fun ContactsDialog(
    contacts: List<Member>,
    onDismiss: () -> Unit,
    onContactSelected: (Member) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add from Contacts") },
        text = {
            if (contacts.isEmpty()) {
                Text("No contacts found with phone numbers.")
            } else {
                LazyColumn {
                    items(contacts) { contact ->
                        TextButton(
                            onClick = { onContactSelected(contact) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(contact.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}