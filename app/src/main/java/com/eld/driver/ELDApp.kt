package com.eld.driver

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eld.driver.ui.screens.dashboard.DashboardScreen
import com.eld.driver.ui.screens.inspection.CreateInspectionScreen
import com.eld.driver.ui.screens.inspection.InspectionDetailScreen
import com.eld.driver.ui.screens.inspection.InspectionListScreen
import com.eld.driver.ui.screens.login.LoginScreen
import com.eld.driver.ui.screens.login.LoginViewModel
import com.eld.driver.ui.screens.logs.LogsScreen
import com.eld.driver.ui.screens.vehicle.VehicleConfirmationScreen
import com.eld.driver.ui.screens.vehicle.VehicleSelectionScreen
import com.eld.driver.ui.screens.vehicle.VehicleViewModel

/**
 * Main app composable
 * Handles navigation and app-level state
 */
@Composable
fun ELDApp() {
    val navController = rememberNavController()

    // Shared ViewModels across screens
    val loginViewModel: LoginViewModel = viewModel()
    val vehicleViewModel: VehicleViewModel = viewModel()

    // Observe auth token
    val authToken by loginViewModel.authToken.collectAsState()
    val currentUser by loginViewModel.currentUser.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        // Login screen
        composable("login") {
            LoginScreen(
                navController = navController,
                viewModel = loginViewModel
            )
        }

        // Vehicle Selection screen
        composable("vehicle_selection") {
            VehicleSelectionScreen(
                navController = navController,
                authToken = authToken ?: "",
                viewModel = vehicleViewModel
            )
        }

        // Vehicle Confirmation screen
        composable("vehicle_confirmation") {
            VehicleConfirmationScreen(
                navController = navController,
                authToken = authToken ?: "",
                viewModel = vehicleViewModel
            )
        }

        // Dashboard screen
        composable("dashboard") {
            DashboardScreen(
                navController = navController,
                authToken = authToken ?: "",
                loginViewModel = loginViewModel,
                vehicleViewModel = vehicleViewModel
            )
        }

        // DVIR Inspections List screen
        composable("inspections") {
            InspectionListScreen(
                navController = navController,
                authToken = authToken ?: ""
            )
        }

        // Inspection Detail screen
        composable(
            route = "inspection_detail/{inspectionId}",
            arguments = listOf(navArgument("inspectionId") { type = NavType.IntType })
        ) { backStackEntry ->
            val inspectionId = backStackEntry.arguments?.getInt("inspectionId") ?: 0
            InspectionDetailScreen(
                navController = navController,
                inspectionId = inspectionId,
                authToken = authToken ?: ""
            )
        }

        // Create Inspection screen
        composable("create_inspection") {
            CreateInspectionScreen(
                navController = navController,
                authToken = authToken ?: "",
                vehicleViewModel = vehicleViewModel
            )
        }

        // Logs screen
        composable("logs") {
            LogsScreen(
                navController = navController,
                authToken = authToken ?: ""
            )
        }

        // Log Detail screen
        composable("log_detail/{logId}") { backStackEntry ->
            // TODO: LogDetailScreen
        }
    }
}
