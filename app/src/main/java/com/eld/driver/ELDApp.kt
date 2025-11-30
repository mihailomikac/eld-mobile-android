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
import com.eld.driver.ui.screens.logs.LogDetailScreen
import com.eld.driver.ui.screens.vehicle.VehicleConfirmationScreen
import com.eld.driver.ui.screens.vehicle.VehicleSelectionScreen
import com.eld.driver.ui.screens.vehicle.VehicleViewModel
import com.eld.driver.ui.screens.bletest.BleTestScreen
import com.eld.driver.ui.screens.dvir.DVIRScreen
import com.eld.driver.ui.screens.dvir.DVIRViewModel
import com.eld.driver.ui.screens.dvir.DriverInspectionScreen
import com.eld.driver.ui.screens.dvir.VehicleDefectsScreen
import com.eld.driver.ui.screens.dashboard.DashboardViewModel
import com.eld.driver.ui.screens.dashboard.DutyStatusUiState
import com.eld.driver.data.models.DutyStatusType

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
    val dashboardViewModel: DashboardViewModel = viewModel()
    val dvirViewModel: DVIRViewModel = viewModel()

    // Observe auth token
    val authToken by loginViewModel.authToken.collectAsState()
    val currentUser by loginViewModel.currentUser.collectAsState()

    // Observe vehicle and duty status for DVIR
    val selectedVehicle by vehicleViewModel.selectedVehicle.collectAsState()
    val currentDutyStatus by dashboardViewModel.currentDutyStatus.collectAsState()
    val currentVehicleId by vehicleViewModel.currentVehicleId.collectAsState()

    // Update global vehicle ID when it changes
    LaunchedEffect(currentVehicleId) {
        ELDDriverApplication.setCurrentVehicleId(currentVehicleId)
    }

    // Set up auto-navigation to Dashboard when vehicle starts moving
    LaunchedEffect(Unit) {
        ELDDriverApplication.onNavigateToDashboard = {
            // Only navigate if user is logged in and not already on dashboard
            if (authToken != null) {
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                if (currentRoute != "dashboard" && currentRoute != "login" && currentRoute != "vehicle_selection" && currentRoute != "vehicle_confirmation") {
                    android.util.Log.d("ELDApp", "🚗 Auto-navigating to Dashboard - vehicle started moving")
                    navController.navigate("dashboard") {
                        // Don't pop the back stack, just navigate
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    // Clean up callback when composable leaves
    DisposableEffect(Unit) {
        onDispose {
            ELDDriverApplication.onNavigateToDashboard = null
        }
    }

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
        composable(
            route = "log_detail/{date}",
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            LogDetailScreen(
                navController = navController,
                date = date,
                authToken = authToken ?: ""
            )
        }

        // BLE Test screen
        composable("ble_test") {
            BleTestScreen(navController = navController)
        }

        // DVIR Main screen
        composable("dvir") {
            // Get current duty status type from UiState
            val dutyStatusType = when (val state = currentDutyStatus) {
                is DutyStatusUiState.Success -> state.dutyStatus.dutyStatus
                else -> null
            }

            DVIRScreen(
                navController = navController,
                authToken = authToken ?: "",
                currentDutyStatus = dutyStatusType,
                currentVehicle = selectedVehicle,
                isEldConnected = dashboardViewModel.eldConnectionStatus.collectAsState().value == com.eld.driver.data.models.ELDConnectionStatus.CONNECTED,
                onChangeDutyStatus = { location ->
                    // Change duty status to ON_DUTY before inspection
                    dashboardViewModel.changeDutyStatus(
                        token = authToken ?: "",
                        newStatus = DutyStatusType.ON_DUTY_NOT_DRIVING,
                        location = location,
                        notes = "Changed to On Duty for DVIR inspection",
                        onSuccess = { }
                    )
                },
                dvirViewModel = dvirViewModel
            )
        }

        // Driver Inspection screen with vehicleId parameter
        composable(
            route = "driver_inspection/{vehicleId}",
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: 0

            DriverInspectionScreen(
                navController = navController,
                authToken = authToken ?: "",
                vehicleId = vehicleId,
                vehicle = selectedVehicle,
                dvirViewModel = dvirViewModel
            )
        }

        // Vehicle Defects screen with vehicleId parameter
        composable(
            route = "vehicle_defects/{vehicleId}",
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: 0

            VehicleDefectsScreen(
                navController = navController,
                authToken = authToken ?: "",
                vehicleId = vehicleId,
                isTrailer = false,
                dvirViewModel = dvirViewModel
            )
        }

        // Trailer Defects screen with vehicleId parameter
        composable(
            route = "trailer_defects/{vehicleId}",
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: 0

            VehicleDefectsScreen(
                navController = navController,
                authToken = authToken ?: "",
                vehicleId = vehicleId,
                isTrailer = true,
                dvirViewModel = dvirViewModel
            )
        }
    }
}
