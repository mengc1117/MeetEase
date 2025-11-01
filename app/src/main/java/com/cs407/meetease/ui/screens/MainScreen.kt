package com.cs407.meetease.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cs407.meetease.navigation.BottomNavScreen
import com.cs407.meetease.ui.viewmodels.MapViewModel
import com.cs407.meetease.ui.viewmodels.MembersViewModel
import com.cs407.meetease.ui.viewmodels.RemindersViewModel
import com.cs407.meetease.ui.viewmodels.SchedulerViewModel

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(rootNavController: NavController) {
    val navController = rememberNavController()

    val items = listOf(
        BottomNavScreen.Scheduler,
        BottomNavScreen.Members,
        BottomNavScreen.Reminders,
        BottomNavScreen.Map,
        BottomNavScreen.Profile
    )


    val schedulerViewModel: SchedulerViewModel = viewModel()
    val membersViewModel: MembersViewModel = viewModel()
    val remindersViewModel: RemindersViewModel = viewModel()
    val mapViewModel: MapViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavScreen.Scheduler.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavScreen.Scheduler.route) {
                SchedulerScreen(schedulerViewModel)
            }
            composable(BottomNavScreen.Members.route) {
                MembersScreen(membersViewModel)
            }
            composable(BottomNavScreen.Reminders.route) {
                RemindersScreen(
                    schedulerUiState = schedulerViewModel.uiState,
                    remindersViewModel = remindersViewModel
                )
            }
            composable(BottomNavScreen.Map.route) {
                MapScreen(mapViewModel)
            }

            composable(BottomNavScreen.Profile.route) {
                ProfileScreen(rootNavController = rootNavController)
            }

        }
    }
}