package com.eld.driver

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.delay

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

    // DEBUG: Log immediately when ELDApp composes
    android.util.Log.d("ELDApp", "═══════════════════════════════════════════════════")
    android.util.Log.d("ELDApp", "🚀 ELDApp COMPOSING")
    android.util.Log.d("ELDApp", "   ELDDriverApplication.getCurrentVehicleId() = ${ELDDriverApplication.getCurrentVehicleId()}")
    android.util.Log.d("ELDApp", "   ELDDriverApplication.isLoggedIn() = ${ELDDriverApplication.isLoggedIn()}")
    android.util.Log.d("ELDApp", "   viewModel currentVehicleId = $currentVehicleId")
    android.util.Log.d("ELDApp", "═══════════════════════════════════════════════════")

    // Try to restore session on app start (only once per app lifecycle)
    // Using rememberSaveable to survive configuration changes
    var sessionRestored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!sessionRestored) {
            android.util.Log.d("ELDApp", "🔄 Checking for saved session...")
            val restored = loginViewModel.tryRestoreSession()
            sessionRestored = true

            android.util.Log.d("ELDApp", "   tryRestoreSession() = $restored")
            android.util.Log.d("ELDApp", "   isLoggedIn() = ${ELDDriverApplication.isLoggedIn()}")
            android.util.Log.d("ELDApp", "   getAuthToken() = ${ELDDriverApplication.getAuthToken()?.take(30)}...")
            android.util.Log.d("ELDApp", "   getCurrentVehicleId() = ${ELDDriverApplication.getCurrentVehicleId()}")
            android.util.Log.d("ELDApp", "   getCurrentUser() = ${ELDDriverApplication.getCurrentUser()?.email}")

            if (restored) {
                // Session restored - check if we have vehicle ID too
                val vehicleId = ELDDriverApplication.getCurrentVehicleId()
                val token = ELDDriverApplication.getAuthToken()
                if (vehicleId != null && token != null) {
                    // Restore vehicle data from API
                    vehicleViewModel.restoreVehicleSession(token)
                    // Navigate directly to dashboard - clear entire back stack
                    android.util.Log.d("ELDApp", "✅ Session restored - navigating to DASHBOARD (vehicleId=$vehicleId)")
                    navController.navigate("dashboard") {
                        popUpTo(0) { inclusive = true }  // Clear entire back stack
                    }
                } else if (token != null && vehicleId == null) {
                    // Need to select vehicle - this should only happen on first login
                    android.util.Log.d("ELDApp", "⚠️ Session restored but NO VEHICLE - going to vehicle selection")
                    navController.navigate("vehicle_selection") {
                        popUpTo(0) { inclusive = true }  // Clear entire back stack
                    }
                }
            } else {
                android.util.Log.d("ELDApp", "❌ No session to restore - staying on login")
            }
        }
    }

    // Update global vehicle ID when it changes (but don't clear it on app start!)
    LaunchedEffect(currentVehicleId) {
        // Only update if we have a NEW vehicle ID - don't clear the stored one with null
        // This prevents the bug where ViewModel's initial null value clears the persisted vehicleId
        if (currentVehicleId != null) {
            android.util.Log.d("ELDApp", "📌 Updating vehicle ID in global state: $currentVehicleId")
            ELDDriverApplication.setCurrentVehicleId(currentVehicleId)
        }
    }

    // Reset ViewModels when user logs out (authToken becomes null)
    var previousAuthToken by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(authToken) {
        if (previousAuthToken != null && authToken == null) {
            // User logged out - reset ViewModels for next user
            android.util.Log.d("ELDApp", "🔄 User logged out - resetting ViewModels")
            dashboardViewModel.resetForNewUser()
            vehicleViewModel.resetForNewUser()
        }
        previousAuthToken = authToken
    }

    // State for force logout dialog
    var showForceLogoutDialog by remember { mutableStateOf(false) }
    var forceLogoutReason by remember { mutableStateOf("") }

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

        // Set up force logout handler (when another device logs in)
        ELDDriverApplication.onForceLogout = { reason ->
            android.util.Log.w("ELDApp", "!!! FORCE LOGOUT - Another device logged in !!!")
            android.util.Log.w("ELDApp", "Reason: $reason")
            forceLogoutReason = reason
            showForceLogoutDialog = true
        }
    }

    // Clean up callbacks when composable leaves
    DisposableEffect(Unit) {
        onDispose {
            ELDDriverApplication.onNavigateToDashboard = null
            ELDDriverApplication.onForceLogout = null
        }
    }

    // Force logout dialog
    if (showForceLogoutDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                // Cannot dismiss - must acknowledge
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Session Ended",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(forceLogoutReason)
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showForceLogoutDialog = false
                        // Logout the user (this clears session and navigates to login)
                        loginViewModel.logout("Force logged out - another device logged in")
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2)
                    )
                ) {
                    Text("OK")
                }
            }
        )
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

        // Vehicle Selection screen - requires BT and Location
        composable("vehicle_selection") {
            // BLOCK system back navigation - user must use Back to Login button or select a vehicle
            androidx.activity.compose.BackHandler(enabled = true) {
                android.util.Log.d("ELDApp", "Back pressed on vehicle_selection - BLOCKED (use Back to Login button)")
                // Do nothing - user must use the Back to Login button
            }
            RequiredPermissionsGate {
                VehicleSelectionScreen(
                    navController = navController,
                    authToken = authToken ?: "",
                    viewModel = vehicleViewModel,
                    onLogout = {
                        // Clear session and go back to login
                        android.util.Log.d("ELDApp", "🚪 Back to Login from vehicle_selection")
                        loginViewModel.logout()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        // Vehicle Confirmation screen - requires BT and Location
        composable("vehicle_confirmation") {
            // Back goes to vehicle selection, not dashboard
            androidx.activity.compose.BackHandler(enabled = true) {
                android.util.Log.d("ELDApp", "Back pressed on vehicle_confirmation - going to vehicle_selection")
                navController.navigate("vehicle_selection") {
                    popUpTo("vehicle_confirmation") { inclusive = true }
                }
            }
            RequiredPermissionsGate {
                VehicleConfirmationScreen(
                    navController = navController,
                    authToken = authToken ?: "",
                    viewModel = vehicleViewModel
                )
            }
        }

        // Dashboard screen - requires BT and Location
        composable("dashboard") {
            // Debug: Log ViewModel state when dashboard route is composed
            android.util.Log.d("ELDApp", "📱 Composing dashboard route - ViewModel isInitialLoading=${dashboardViewModel.isInitialLoading.value}")
            RequiredPermissionsGate {
                DashboardScreen(
                    navController = navController,
                    authToken = authToken ?: "",
                    loginViewModel = loginViewModel,
                    vehicleViewModel = vehicleViewModel,
                    dashboardViewModel = dashboardViewModel
                )
            }
        }

        // DVIR Inspections List screen - requires BT and Location
        composable("inspections") {
            RequiredPermissionsGate {
                InspectionListScreen(
                    navController = navController,
                    authToken = authToken ?: ""
                )
            }
        }

        // Inspection Detail screen - requires BT and Location
        composable(
            route = "inspection_detail/{inspectionId}",
            arguments = listOf(navArgument("inspectionId") { type = NavType.IntType })
        ) { backStackEntry ->
            val inspectionId = backStackEntry.arguments?.getInt("inspectionId") ?: 0
            RequiredPermissionsGate {
                InspectionDetailScreen(
                    navController = navController,
                    inspectionId = inspectionId,
                    authToken = authToken ?: ""
                )
            }
        }

        // Create Inspection screen - requires BT and Location
        composable("create_inspection") {
            RequiredPermissionsGate {
                CreateInspectionScreen(
                    navController = navController,
                    authToken = authToken ?: "",
                    vehicleViewModel = vehicleViewModel
                )
            }
        }

        // Logs screen - requires BT and Location
        composable("logs") {
            RequiredPermissionsGate {
                LogsScreen(
                    navController = navController,
                    authToken = authToken ?: ""
                )
            }
        }

        // Log Detail screen - requires BT and Location
        composable(
            route = "log_detail/{date}",
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            RequiredPermissionsGate {
                LogDetailScreen(
                    navController = navController,
                    date = date,
                    authToken = authToken ?: ""
                )
            }
        }

        // BLE Test screen - requires BT and Location
        composable("ble_test") {
            RequiredPermissionsGate {
                BleTestScreen(navController = navController)
            }
        }

        // DVIR Main screen - requires BT and Location
        composable("dvir") {
            RequiredPermissionsGate {
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
        }

        // Driver Inspection screen - requires BT and Location
        composable(
            route = "driver_inspection/{vehicleId}",
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: 0
            RequiredPermissionsGate {
                DriverInspectionScreen(
                    navController = navController,
                    authToken = authToken ?: "",
                    vehicleId = vehicleId,
                    vehicle = selectedVehicle,
                    dvirViewModel = dvirViewModel
                )
            }
        }

        // Vehicle Defects screen - requires BT and Location
        composable(
            route = "vehicle_defects/{vehicleId}",
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: 0
            RequiredPermissionsGate {
                VehicleDefectsScreen(
                    navController = navController,
                    authToken = authToken ?: "",
                    vehicleId = vehicleId,
                    isTrailer = false,
                    dvirViewModel = dvirViewModel
                )
            }
        }

        // Trailer Defects screen - requires BT and Location
        composable(
            route = "trailer_defects/{vehicleId}",
            arguments = listOf(navArgument("vehicleId") { type = NavType.IntType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getInt("vehicleId") ?: 0
            RequiredPermissionsGate {
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
}

/**
 * Gate composable that blocks app usage if Bluetooth or Location are not enabled.
 * Continuously monitors both settings and shows a blocking screen if either is disabled.
 * @param skipCheck If true, shows content without checking (used for login screen)
 */
@Composable
fun RequiredPermissionsGate(
    skipCheck: Boolean = false,
    content: @Composable () -> Unit
) {
    // If skipping check (e.g., login screen), just show content
    if (skipCheck) {
        content()
        return
    }
    val context = LocalContext.current

    // State for Bluetooth and Location enabled
    var isBluetoothEnabled by remember { mutableStateOf(false) }
    var isLocationEnabled by remember { mutableStateOf(false) }
    var hasBluetoothPermission by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    // Check functions
    fun checkBluetoothEnabled(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter?.isEnabled == true
    }

    fun checkLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
               locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    fun checkBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-Android 12 doesn't need runtime BT permissions
        }
    }

    fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Permission launchers
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermission = permissions.values.all { it }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    // Bluetooth enable launcher
    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isBluetoothEnabled = checkBluetoothEnabled()
    }

    // Continuously check states
    LaunchedEffect(Unit) {
        while (true) {
            isBluetoothEnabled = checkBluetoothEnabled()
            isLocationEnabled = checkLocationEnabled()
            hasBluetoothPermission = checkBluetoothPermission()
            hasLocationPermission = checkLocationPermission()
            delay(1000) // Check every second
        }
    }

    // Determine what's missing
    val needsBluetoothPermission = !hasBluetoothPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val needsLocationPermission = !hasLocationPermission
    val needsBluetoothEnabled = hasBluetoothPermission && !isBluetoothEnabled
    val needsLocationEnabled = hasLocationPermission && !isLocationEnabled

    val somethingMissing = needsBluetoothPermission || needsLocationPermission ||
                          needsBluetoothEnabled || needsLocationEnabled

    if (somethingMissing) {
        // Show blocking screen
        RequiredSettingsBlockingScreen(
            needsBluetoothPermission = needsBluetoothPermission,
            needsLocationPermission = needsLocationPermission,
            needsBluetoothEnabled = needsBluetoothEnabled,
            needsLocationEnabled = needsLocationEnabled,
            onRequestBluetoothPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bluetoothPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        )
                    )
                }
            },
            onRequestLocationPermission = {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            onEnableBluetooth = {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothEnableLauncher.launch(enableBtIntent)
            },
            onEnableLocation = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        )
    } else {
        // All good - show content
        content()
    }
}

/**
 * Blocking screen shown when Bluetooth or Location are not properly configured.
 */
@Composable
fun RequiredSettingsBlockingScreen(
    needsBluetoothPermission: Boolean,
    needsLocationPermission: Boolean,
    needsBluetoothEnabled: Boolean,
    needsLocationEnabled: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onEnableLocation: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Required Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ELD Driver app requires Bluetooth and Location to be enabled for FMCSA compliance.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Bluetooth Section
                if (needsBluetoothPermission || needsBluetoothEnabled) {
                    SettingRequirementRow(
                        icon = Icons.Default.Bluetooth,
                        title = "Bluetooth",
                        description = if (needsBluetoothPermission) "Permission required" else "Needs to be enabled",
                        isEnabled = !needsBluetoothPermission && !needsBluetoothEnabled,
                        buttonText = if (needsBluetoothPermission) "Grant Permission" else "Enable",
                        onClick = if (needsBluetoothPermission) onRequestBluetoothPermission else onEnableBluetooth
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Location Section
                if (needsLocationPermission || needsLocationEnabled) {
                    SettingRequirementRow(
                        icon = Icons.Default.LocationOn,
                        title = "Location",
                        description = if (needsLocationPermission) "Permission required" else "Needs to be enabled",
                        isEnabled = !needsLocationPermission && !needsLocationEnabled,
                        buttonText = if (needsLocationPermission) "Grant Permission" else "Enable",
                        onClick = if (needsLocationPermission) onRequestLocationPermission else onEnableLocation
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "The app cannot function without these settings.",
                    fontSize = 12.sp,
                    color = Color(0xFFFF6B6B),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SettingRequirementRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isEnabled: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F3460), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isEnabled) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        if (!isEnabled) {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = buttonText, fontSize = 12.sp)
            }
        } else {
            Text(
                text = "✓",
                fontSize = 20.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
