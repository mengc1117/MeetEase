package com.cs407.meetease.ui.screens

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cs407.meetease.R
import com.cs407.meetease.navigation.Screen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(rootNavController: NavController) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isLinking by remember { mutableStateOf(false) }
    var linkStatus by remember { mutableStateOf("") }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLinking = false
        Log.d("ProfileScreen", "Result code: ${result.resultCode}, Expected: ${Activity.RESULT_OK}")

        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    linkStatus = "✓ Google Calendar linked successfully!"
                    Log.d("ProfileScreen", "Success! Email: ${account.email}")
                    Log.d("ProfileScreen", "Granted scopes: ${account.grantedScopes}")
                } else {
                    linkStatus = "Failed to link Google Calendar"
                    Log.e("ProfileScreen", "Account is null")
                }
            } catch (e: ApiException) {
                linkStatus = "Error linking calendar: Code ${e.statusCode}"
                Log.e("ProfileScreen", "ApiException: ${e.statusCode} - ${e.message}", e)
            } catch (e: Exception) {
                linkStatus = "Error: ${e.message}"
                Log.e("ProfileScreen", "Unexpected error", e)
            }
        } else {
            linkStatus = "Calendar linking cancelled"
            Log.w("ProfileScreen", "Cancelled or failed. Result code: ${result.resultCode}")
        }
    }

    val gso = remember {
        try {
            val webClientId = context.getString(R.string.default_web_client_id)
            Log.d("ProfileScreen", "Using web client ID: $webClientId")

            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .requestScopes(
                    Scope(CalendarScopes.CALENDAR),
                    Scope(CalendarScopes.CALENDAR_READONLY)
                )
                .build()
        } catch (e: Exception) {
            Log.e("ProfileScreen", "Error building GoogleSignInOptions", e)
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(
                    Scope(CalendarScopes.CALENDAR),
                    Scope(CalendarScopes.CALENDAR_READONLY)
                )
                .build()
        }
    }

    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (currentUser != null) {
            Text(
                text = "Logged in as:",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = currentUser.email ?: "No email available",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (linkStatus.isNotEmpty()) {
            Text(
                text = linkStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = if (linkStatus.contains("✓") || linkStatus.contains("success"))
                    MaterialTheme.colorScheme.primary
                else if (linkStatus.contains("cancel"))
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLinking = true
                linkStatus = ""
                Log.d("ProfileScreen", "Starting Google Sign-In")
                scope.launch {
                    try {
                        googleSignInClient.signOut().await()
                        Log.d("ProfileScreen", "Signed out, launching intent")
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    } catch (e: Exception) {
                        isLinking = false
                        linkStatus = "Error starting sign-in: ${e.message}"
                        Log.e("ProfileScreen", "Error in onClick", e)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !isLinking
        ) {
            if (isLinking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Linking...")
            } else {
                Text("Link/Re-link Google Calendar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                FirebaseAuth.getInstance().signOut()
                googleSignInClient.signOut()

                rootNavController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Main.route) {
                        inclusive = true
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Log Out")
        }
    }
}