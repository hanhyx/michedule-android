package com.ljh.michedule.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ljh.michedule.MicheduleApp
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.ui.calendar.CalendarScreen
import com.ljh.michedule.ui.calendar.CalendarViewModel
import com.ljh.michedule.ui.chat.ChatScreen
import com.ljh.michedule.ui.event.AddEventScreen
import com.ljh.michedule.ui.settings.SettingsScreen
import com.ljh.michedule.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Calendar : Screen("calendar", "캘린더", Icons.Default.CalendarMonth)
    data object Chat : Screen("chat", "채팅", Icons.AutoMirrored.Filled.Chat)
    data object Settings : Screen("settings", "설정", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicheduleNavHost(prefsManager: PrefsManager) {
    val navController = rememberNavController()
    val calendarViewModel: CalendarViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val app = LocalContext.current.applicationContext as MicheduleApp
    val myCode by prefsManager.myCode.collectAsStateWithLifecycle(initialValue = "")
    val partnerCode by prefsManager.partnerCode.collectAsStateWithLifecycle(initialValue = "")
    val lastReadAt by prefsManager.chatLastReadAt.collectAsStateWithLifecycle(initialValue = 0L)

    val roomCode = remember(myCode, partnerCode) {
        if (myCode.isNotBlank() && partnerCode.isNotBlank()) {
            val sorted = listOf(myCode, partnerCode).sorted()
            "${sorted[0]}_${sorted[1]}"
        } else ""
    }

    val unreadCount by remember(roomCode, myCode, lastReadAt) {
        if (roomCode.isNotBlank() && myCode.isNotBlank()) {
            app.chatRepository.getUnreadCount(roomCode, myCode, lastReadAt)
        } else {
            kotlinx.coroutines.flow.flowOf(0)
        }
    }.collectAsStateWithLifecycle(initialValue = 0)

    val bottomBarScreens = listOf(Screen.Calendar, Screen.Chat, Screen.Settings)
    val showBottomBar = currentRoute in bottomBarScreens.map { it.route }

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = DarkSurface) {
                    bottomBarScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                if (screen is Screen.Chat && unreadCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = androidx.compose.ui.graphics.Color(0xFFEF4444),
                                                contentColor = androidx.compose.ui.graphics.Color.White
                                            ) {
                                                Text("$unreadCount")
                                            }
                                        }
                                    ) {
                                        Icon(screen.icon, contentDescription = screen.label)
                                    }
                                } else {
                                    Icon(screen.icon, contentDescription = screen.label)
                                }
                            },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Calendar.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Purple80,
                                selectedTextColor = Purple80,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = Purple40.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Calendar.route
        ) {
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    viewModel = calendarViewModel,
                    onNavigateToAddEvent = { date ->
                        navController.navigate("addEvent/$date")
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            composable(Screen.Chat.route) {
                ChatScreen(
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(Screen.Calendar.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .imePadding()
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    prefsManager = prefsManager,
                    onAutofill = { pattern, startDate, endDate ->
                        calendarViewModel.autofillPattern(
                            pattern,
                            LocalDate.parse(startDate),
                            LocalDate.parse(endDate)
                        )
                    },
                    onClearMonth = { calendarViewModel.clearMonth() },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            composable(
                route = "addEvent/{date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType })
            ) { backStackEntry ->
                val dateStr = backStackEntry.arguments?.getString("date") ?: LocalDate.now().toString()
                AddEventScreen(
                    initialDate = dateStr,
                    onSave = { event ->
                        calendarViewModel.addEvent(event)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
