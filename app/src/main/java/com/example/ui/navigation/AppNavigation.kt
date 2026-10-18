package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.AppRepository
import com.example.ui.screens.*

// ── Bottom Nav Destinations ─────────────────────────────────────────────────
sealed class BottomNavDest(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : BottomNavDest("dashboard", "Dashboard",   Icons.Default.Dashboard)
    object Vouchers  : BottomNavDest("vouchers",  "Vouchers",    Icons.Default.ConfirmationNumber)
    object Sessions  : BottomNavDest("sessions",  "Sessions",    Icons.Default.People)
    object PPPoE     : BottomNavDest("pppoe",     "PPPoE",       Icons.Default.Router)
    object Settings  : BottomNavDest("settings",  "Settings",    Icons.Default.Settings)
}

/** Always-visible tabs (no PPPoE — it's added conditionally) */
private val coreNavItems = listOf(
    BottomNavDest.Dashboard,
    BottomNavDest.Vouchers,
    BottomNavDest.Sessions,
    BottomNavDest.Settings,
)

/** Routes that should show the bottom navigation bar */
private val bottomNavRoutes = setOf("dashboard", "vouchers", "sessions", "pppoe", "settings")

// ── Root Navigation ─────────────────────────────────────────────────────────
@Composable
fun AppNavigation(repository: AppRepository, startRoute: String? = null) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(repository))

    // PPPoE tab: only shown when active router has PPPoE server configured.
    // ViewModel checks /ppp/secret count on router switch — 0 = hidden, ≥1 or server present = shown.
    val pppoeEnabled by viewModel.pppoeEnabled.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavRoutes

    // Dynamically build the nav item list: insert PPPoE before Settings when enabled
    val navItems = remember(pppoeEnabled) {
        buildList {
            addAll(coreNavItems)
            if (pppoeEnabled) {
                add(size - 1, BottomNavDest.PPPoE) // before Settings
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn() + slideInVertically { it },
                exit  = fadeOut() + slideOutVertically { it }
            ) {
                NavigationBar(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .border(
                            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        ),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                ) {
                    val currentDestination = navBackStackEntry?.destination
                    navItems.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected  = selected,
                            onClick   = {
                                // If PPPoE tab is removed while user is on it, redirect to dashboard
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor    = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController    = navController,
                startDestination = if (!startRoute.isNullOrBlank()) startRoute else "login"
            ) {
                // ── Auth ──────────────────────────────────────────────────
                composable("login") {
                    LoginScreen(
                        viewModel = viewModel,
                        onLoginSuccess = {
                            navController.navigate("dashboard") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    )
                }

                // ── Bottom Nav Screens ────────────────────────────────────
                composable(BottomNavDest.Dashboard.route) {
                    DashboardScreen(viewModel = viewModel, navController = navController)
                }
                composable(BottomNavDest.Vouchers.route) {
                    VouchersScreen(viewModel = viewModel, navController = navController)
                }
                composable(BottomNavDest.Sessions.route) {
                    ActiveSessionsScreen(viewModel = viewModel, navController = navController)
                }
                composable(BottomNavDest.PPPoE.route) {
                    // PppoeScreen is always registered in the graph even when hidden from nav bar
                    // so deeplinks or programmatic navigation still work
                    PppoeScreen(viewModel = viewModel, navController = navController)
                }
                composable(BottomNavDest.Settings.route) {
                    SettingsScreen(viewModel = viewModel, navController = navController)
                }

                // ── Secondary / Full-Screen Screens ──────────────────────
                composable("profiles") {
                    ProfilesScreen(viewModel = viewModel, navController = navController)
                }
                composable("network_topology") {
                    NetworkTopologyScreen(viewModel = viewModel, navController = navController)
                }
            }
        }
    }

    // If user is on PPPoE screen and PPPoE gets disabled (router switched),
    // automatically navigate them back to dashboard
    LaunchedEffect(pppoeEnabled) {
        if (!pppoeEnabled && currentRoute == "pppoe") {
            navController.navigate("dashboard") {
                popUpTo("dashboard") { inclusive = false }
            }
        }
    }
}
