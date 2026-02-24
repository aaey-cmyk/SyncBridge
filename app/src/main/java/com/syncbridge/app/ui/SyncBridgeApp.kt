package com.syncbridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.syncbridge.app.MainViewModel
import com.syncbridge.app.ui.dashboard.DashboardScreen
import com.syncbridge.app.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Settings  : Screen("settings",  "Settings",  Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncBridgeApp(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()

    val items = listOf(Screen.Dashboard, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    appState = state,
                    onRequestPermissions = onRequestPermissions,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    onStartServer = onStartServer,
                    onStopServer = onStopServer
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
