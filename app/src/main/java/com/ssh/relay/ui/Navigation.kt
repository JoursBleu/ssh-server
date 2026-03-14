package com.ssh.relay.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class Screen(val route: String, val icon: ImageVector) {
    data object Server : Screen("server", Icons.Default.CloudUpload)
    data object Keys : Screen("keys", Icons.Default.VpnKey)
    data object Settings : Screen("settings", Icons.Default.Settings)

    val title: String get() = when (this) {
        Server -> S.navServer
        Keys -> S.navKeys
        Settings -> S.navSettings
    }
}

val screens = listOf(Screen.Server, Screen.Keys, Screen.Settings)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Read language to trigger recomposition
    val langState = LocalLanguage.current
    val currentLang = langState.value

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                screens.forEach { screen ->
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
        NavHost(navController, startDestination = Screen.Server.route, Modifier.padding(innerPadding)) {
            composable(Screen.Server.route) { ServerScreen() }
            composable(Screen.Keys.route) { KeysScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
