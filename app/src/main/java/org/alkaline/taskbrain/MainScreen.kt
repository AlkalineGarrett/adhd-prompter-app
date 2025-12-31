package org.alkaline.taskbrain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.alkaline.taskbrain.ui.auth.GoogleSignInScreen
import org.alkaline.taskbrain.ui.notelist.NoteListScreen
import org.alkaline.taskbrain.ui.home.HomeScreen
import org.alkaline.taskbrain.ui.notifications.NotificationsScreen

sealed class Screen(val route: String, val titleResourceId: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.title_home, Icons.Filled.Home)
    object NoteList : Screen("note_list", R.string.title_note_list, Icons.Filled.Dashboard)
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
        Screen.NoteList,
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
                        containerColor = colorResource(R.color.brand_color),
                        titleContentColor = colorResource(R.color.brand_text_color),
                        actionIconContentColor = colorResource(R.color.brand_text_color),
                        navigationIconContentColor = colorResource(R.color.brand_text_color)
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
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize()
        ) {
            composable(Screen.Login.route) {
                GoogleSignInScreen(
                    isLoading = false,
                    onSignInClick = onSignInClick
                )
            }
            composable(
                route = "${Screen.Home.route}?noteId={noteId}",
                arguments = listOf(navArgument("noteId") {
                    type = NavType.StringType
                    defaultValue = "root_note"
                })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId") ?: "root_note"
                HomeScreen(noteId = noteId)
            }
            // Keep the original home route for direct navigation
            composable(Screen.Home.route) {
                HomeScreen()
            }
            composable(Screen.NoteList.route) {
                NoteListScreen(
                    onNoteClick = { noteId ->
                        navController.navigate("${Screen.Home.route}?noteId=$noteId") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Notifications.route) {
                NotificationsScreen()
            }
        }
    }
}