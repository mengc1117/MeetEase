package com.cs407.meetease


import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cs407.meetease.navigation.Screen
import com.cs407.meetease.ui.screens.LoginScreen
import com.cs407.meetease.ui.screens.MainScreen
import com.cs407.meetease.ui.screens.SignUpScreen
import com.cs407.meetease.ui.viewmodels.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun MeetEaseAppNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()

    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) {
        Screen.Main.route
    } else {
        Screen.Login.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(Screen.SignUp.route) {
            SignUpScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(Screen.Main.route) {

            MainScreen(rootNavController = navController)

        }
    }
}