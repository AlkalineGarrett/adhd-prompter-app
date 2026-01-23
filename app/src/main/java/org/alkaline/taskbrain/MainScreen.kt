package org.alkaline.taskbrain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.StateFlow
import org.alkaline.taskbrain.ui.Dimens
import org.alkaline.taskbrain.ui.auth.GoogleSignInScreen
import org.alkaline.taskbrain.ui.currentnote.CurrentNoteScreen
import org.alkaline.taskbrain.ui.currentnote.CurrentNoteViewModel
import org.alkaline.taskbrain.ui.currentnote.RecentTabsViewModel
import org.alkaline.taskbrain.ui.notelist.NoteListScreen
import org.alkaline.taskbrain.ui.alarms.AlarmsScreen

sealed class Screen(val route: String, val titleResourceId: Int, val icon: ImageVector) {
    object CurrentNote : Screen("current_note", R.string.title_current_note, Icons.Filled.Description)
    object NoteList : Screen("note_list", R.string.title_note_list, Icons.Filled.Dashboard)
    object Notifications : Screen("notifications", R.string.title_notifications, Icons.Filled.Notifications)
    object Login : Screen("login", R.string.google_title_text, Icons.Filled.Home) // Icon not used for login
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSignInClick: () -> Unit,
    isUserSignedIn: Boolean,
    onSignOutClick: () -> Unit,
    isFingerDown: StateFlow<Boolean>
) {
    val navController = rememberNavController()

    // Create ViewModels at MainScreen level so they're shared across all CurrentNoteScreen instances
    // (both the "current_note" and "current_note?noteId={noteId}" routes)
    val recentTabsViewModel: RecentTabsViewModel = viewModel()
    val currentNoteViewModel: CurrentNoteViewModel = viewModel()

    // Determine the start destination based on sign-in status
    val startDestination = if (isUserSignedIn) Screen.CurrentNote.route else Screen.Login.route

    LaunchedEffect(isUserSignedIn) {
        if (isUserSignedIn) {
            navController.navigate(Screen.CurrentNote.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        } else {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val items = listOf(
        Screen.CurrentNote,
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
                    modifier = Modifier.height(Dimens.TopAppBarHeight),
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontSize = Dimens.TopAppBarTitleTextSize
                        )
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
                             label = { Text(
                                 text = stringResource(screen.titleResourceId),
                                 fontSize = Dimens.NavigationBarLabelTextSize
                             ) },
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
                route = "${Screen.CurrentNote.route}?noteId={noteId}",
                arguments = listOf(navArgument("noteId") {
                    type = NavType.StringType
                    nullable = true
                })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId")
                CurrentNoteScreen(
                    noteId = noteId,
                    isFingerDownFlow = isFingerDown,
                    onNavigateBack = {
                        navController.navigate(Screen.NoteList.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToNote = { targetNoteId ->
                        navController.navigate("${Screen.CurrentNote.route}?noteId=$targetNoteId") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    currentNoteViewModel = currentNoteViewModel,
                    recentTabsViewModel = recentTabsViewModel
                )
            }
            // Keep the basic route for direct navigation (tab clicks)
            composable(Screen.CurrentNote.route) {
                CurrentNoteScreen(
                    isFingerDownFlow = isFingerDown,
                    onNavigateBack = {
                        navController.navigate(Screen.NoteList.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToNote = { targetNoteId ->
                        navController.navigate("${Screen.CurrentNote.route}?noteId=$targetNoteId") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    currentNoteViewModel = currentNoteViewModel,
                    recentTabsViewModel = recentTabsViewModel
                )
            }
            composable(Screen.NoteList.route) {
                NoteListScreen(
                    onNoteClick = { noteId ->
                        navController.navigate("${Screen.CurrentNote.route}?noteId=$noteId") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onSaveCompleted = currentNoteViewModel.saveCompleted
                )
            }
            composable(Screen.Notifications.route) {
                AlarmsScreen()
            }
        }
    }
}