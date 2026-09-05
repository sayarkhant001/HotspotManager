package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.AppRepository
import com.example.ui.screens.*

@Composable
fun AppNavigation(repository: AppRepository) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(repository))

    NavHost(navController = navController, startDestination = "login") {
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
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable("profiles") {
            ProfilesScreen(viewModel = viewModel, navController = navController)
        }
        composable("vouchers") {
            VouchersScreen(viewModel = viewModel, navController = navController)
        }
        composable("active_sessions") {
            ActiveSessionsScreen(viewModel = viewModel, navController = navController)
        }
    }
}
