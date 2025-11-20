package com.cs407.meetease.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cs407.meetease.navigation.Screen
import com.cs407.meetease.ui.viewmodels.GroupViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSelectionScreen(navController: NavController, viewModel: GroupViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showJoinDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    var groupToAction by remember { mutableStateOf<com.cs407.meetease.data.Group?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currentUser = Firebase.auth.currentUser
    val currentUserId = currentUser?.uid ?: ""

    LaunchedEffect(Unit) {
        viewModel.loadUserGroups()
    }

    LaunchedEffect(uiState.groupSelected) {
        if (uiState.groupSelected) {
            viewModel.resetSelection()
            navController.navigate(Screen.Main.route)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Groups") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select a group to enter, or manage your groups.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.myGroups.isEmpty() && !uiState.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No groups yet. Create or Join one.", color = Color.Gray)
                        }
                    }
                }

                items(uiState.myGroups) { group ->
                    val isOrganizer = group.organizerId == currentUserId
                    GroupItemCard(
                        groupName = group.groupName,
                        groupId = group.groupId,
                        isOrganizer = isOrganizer,
                        onClick = { viewModel.selectGroup(group.groupId) },
                        onActionClick = {
                            groupToAction = group
                            showDeleteConfirmDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, "Create")
                    Spacer(Modifier.width(8.dp))
                    Text("Create")
                }

                OutlinedButton(
                    onClick = { showJoinDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.GroupAdd, "Join")
                    Spacer(Modifier.width(8.dp))
                    Text("Join")
                }
            }
        }
    }

    if (showJoinDialog) {
        var groupIdInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join a Group") },
            text = {
                Column {
                    Text("Enter Group ID:", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = groupIdInput,
                        onValueChange = { groupIdInput = it },
                        label = { Text("Group ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.joinGroup(groupIdInput)
                    showJoinDialog = false
                }) { Text("Join") }
            },
            dismissButton = { TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCreateDialog) {
        var groupNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Group") },
            text = {
                Column {
                    Text("Group Name:", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createGroup(groupNameInput.trim())
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirmDialog && groupToAction != null) {
        val group = groupToAction!!
        val isOrganizer = group.organizerId == currentUserId
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(if (isOrganizer) "Delete Group?" else "Leave Group?") },
            text = {
                if (isOrganizer) {
                    Text("You are the organizer. Deleting this group will dissolve it for EVERYONE. This cannot be undone.")
                } else {
                    Text("Are you sure you want to leave '${group.groupName}'?")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isOrganizer) {
                            viewModel.deleteGroup(group.groupId)
                        } else {
                            viewModel.leaveGroup(group.groupId)
                        }
                        showDeleteConfirmDialog = false
                        groupToAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isOrganizer) "Delete" else "Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun GroupItemCard(
    groupName: String,
    groupId: String,
    isOrganizer: Boolean,
    onClick: () -> Unit,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isOrganizer) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Admin",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    text = "ID: $groupId",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = if (isOrganizer) Icons.Default.DeleteForever else Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = if (isOrganizer) "Delete Group" else "Leave Group",
                    tint = if (isOrganizer) MaterialTheme.colorScheme.error else Color.Gray
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Enter",
                tint = Color.Gray
            )
        }
    }
}