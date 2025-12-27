package org.alkaline.adhdprompter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.alkaline.adhdprompter.ui.auth.GoogleSignInScreen
import org.alkaline.adhdprompter.ui.dashboard.DashboardScreen
import org.alkaline.adhdprompter.ui.home.HomeScreen
import org.alkaline.adhdprompter.ui.notifications.NotificationsScreen

sealed class Screen(val route: String, val titleResourceId: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.title_home, Icons.Filled.Home)
    object Dashboard : Screen("dashboard", R.string.title_dashboard, Icons.Filled.Dashboard)
    object Notifications : Screen("notifications", R.string.title_notifications, Icons.Filled.Notifications)
    object Login : Screen("login", R.string.google_title_text, Icons.Filled.Home) // Icon not used for login
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSignInClick: () -> Unit,
    isUserSignedIn: Boolean,
    onSignOutClick: () -> Unit
) {
    val navController = rememberNavController()
    
    // Determine the start destination based on sign-in status
    val startDestination = if (isUserSignedIn) Screen.Home.route else Screen.Login.route

    LaunchedEffect(isUserSignedIn) {
        if (isUserSignedIn) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        } else {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val items = listOf(
        Screen.Home,
        Screen.Dashboard,
        Screen.Notifications,
    )

    Scaffold(
        topBar = {
            // Only show top bar if not in login screen
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            if (currentDestination?.route != Screen.Login.route) {
                var showMenu by remember { mutableStateOf(false) }

                TopAppBar(
                    title = { 
                        Text(stringResource(R.string.app_name)) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFAED581),
                        titleContentColor = Color.Black,
                        actionIconContentColor = Color.Black,
                        navigationIconContentColor = Color.Black
                    ),
                    actions = {
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.action_settings)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sign_out)) },
                                    onClick = {
                                        showMenu = false
                                        onSignOutClick()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
             val navBackStackEntry by navController.currentBackStackEntryAsState()
             val currentDestination = navBackStackEntry?.destination
             
             // Hide bottom bar on login screen
             if (currentDestination?.route != Screen.Login.route) {
                 NavigationBar {
                     items.forEach { screen ->
                         NavigationBarItem(
                             icon = { Icon(screen.icon, contentDescription = null) },
                             label = { Text(stringResource(screen.titleResourceId)) },
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                GoogleSignInScreen(
                    isLoading = false,
                    onSignInClick = onSignInClick
                )
            }
            composable(Screen.Home.route) {
                HomeScreen()
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen()
            }
            composable(Screen.Notifications.route) {
                NotificationsScreen()
            }
        }
    }
}