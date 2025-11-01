package com.cs407.meetease.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object Main : Screen("main")
}

sealed class BottomNavScreen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Scheduler : BottomNavScreen(
        route = "scheduler",
        title = "Scheduler",
        icon = Icons.Filled.DateRange
    )
    object Members : BottomNavScreen(
        route = "members",
        title = "Members",
        icon = Icons.Filled.Groups
    )
    object Reminders : BottomNavScreen(
        route = "reminders",
        title = "Reminders",
        icon = Icons.Filled.Notifications
    )
    object Map : BottomNavScreen(
        route = "map",
        title = "Map",
        icon = Icons.Filled.Map
    )

    object Profile : BottomNavScreen(
        route = "profile",
        title = "Profile",
        icon = Icons.Filled.Person
    )

}