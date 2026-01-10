package com.eld.driver.ui.screens.dashboard

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.eld.driver.ble.VehicleMotionState
import com.eld.driver.data.local.TokenManager
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.data.models.ELDConnectionStatus
import com.eld.driver.ui.components.ChangeDutyStatusModal
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.components.HOSTimersSection
import com.eld.driver.ui.components.SideMenuDrawer
import com.eld.driver.ui.components.TrailersModal
import com.eld.driver.ui.screens.login.LoginViewModel
import com.eld.driver.ui.screens.vehicle.VehicleViewModel
import com.eld.driver.ui.theme.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dashboard Screen - Main screen matching iOS design exactly
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    authToken: String = "",
    loginViewModel: LoginViewModel,
    vehicleViewModel: VehicleViewModel,
    dashboardViewModel: DashboardViewModel = viewModel<DashboardViewModel>()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tokenManager = remember { TokenManager.getInstance(context) }

    // Prevent back navigation from Dashboard - this is the home screen after login
    // Back button should NOT go to vehicle_selection or login
    BackHandler(enabled = true) {
        // Do nothing - Dashboard is the home screen
        // User must use logout to exit
        android.util.Log.d("DashboardScreen", "Back pressed on Dashboard - ignoring (use logout to exit)")
    }

    // CRITICAL: Validate that a vehicle is selected - redirect to vehicle_selection if not
    // This prevents the bug where user could reach dashboard without selecting a vehicle
    LaunchedEffect(Unit) {
        val vehicleId = com.eld.driver.ELDDriverApplication.getCurrentVehicleId()
        if (vehicleId == null) {
            android.util.Log.e("DashboardScreen", "❌ NO VEHICLE SELECTED - redirecting to vehicle_selection")
            navController.navigate("vehicle_selection") {
                popUpTo("dashboard") { inclusive = true }
            }
        }
    }

    // Driver settings from token
    val allowYardMove = remember { tokenManager.isYardMoveAllowed() }
    val allowPersonalConveyance = remember { tokenManager.isPersonalConveyanceAllowed() }
    val allowManualDriveTime = remember { tokenManager.isManualDriveTimeAllowed() }

    var showDutyStatusModal by remember { mutableStateOf(false) }
    var showTrailersModal by remember { mutableStateOf(false) }
    var showLockedWhileDrivingDialog by remember { mutableStateOf(false) }
    var showUnidentifiedDrivingDialog by remember { mutableStateOf(false) }

    // Bluetooth enable request
    val showBluetoothEnableRequest by dashboardViewModel.showBluetoothEnableRequest.collectAsState()

    // Bluetooth enable launcher
    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            dashboardViewModel.onBluetoothEnabled()
        } else {
            // User declined - still dismiss but Bluetooth stays off
            dashboardViewModel.dismissBluetoothRequest()
        }
    }

    // Check Bluetooth status on Dashboard load
    LaunchedEffect(Unit) {
        dashboardViewModel.checkBluetoothAndPrompt()
    }

    // Launch Bluetooth enable intent when requested
    LaunchedEffect(showBluetoothEnableRequest) {
        if (showBluetoothEnableRequest) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        }
    }

    // Get current user from LoginViewModel
    val currentUser by loginViewModel.currentUser.collectAsState()

    // Get current vehicle from VehicleViewModel
    val currentVehicleId by vehicleViewModel.currentVehicleId.collectAsState()
    val vehicles by vehicleViewModel.filteredVehicles.collectAsState()
    val currentVehicle = vehicles.find { it.id == currentVehicleId }

    // Fallback vehicle name when vehicle list isn't loaded yet
    // Use stored vehicle ID from persistent storage to prevent "No Vehicle" during loading
    val vehicleDisplayName = currentVehicle?.vehicleNumber
        ?: com.eld.driver.ELDDriverApplication.getCurrentVehicleId()?.let { "Vehicle #$it" }
        ?: "No Vehicle"

    // Start services and sync on first launch
    LaunchedEffect(Unit) {
        if (authToken.isNotEmpty()) {
            // Start HOS/sync services (calculates HOS from local data immediately)
            dashboardViewModel.startServices()

            // Initial sync runs in BACKGROUND - UI shows local data immediately
            // Sync only runs ONCE per session (flag in ViewModel)
            dashboardViewModel.performInitialSync()
        }
    }

    val dutyStatusState by dashboardViewModel.currentDutyStatus.collectAsState()
    val hosStatus by dashboardViewModel.hosStatus.collectAsState()
    val isInitialLoading by dashboardViewModel.isInitialLoading.collectAsState()

    // Sync status
    val isOnline by dashboardViewModel.isOnline.collectAsState()
    val pendingSyncCount by dashboardViewModel.pendingSyncCount.collectAsState()

    // Refresh tick - triggers recomposition every minute to update status duration
    val refreshTick by dashboardViewModel.refreshTick.collectAsState()

    // Extract current status and duration from API response
    val currentStatus = when (val state = dutyStatusState) {
        is DutyStatusUiState.Success -> state.dutyStatus.dutyStatus
        else -> DutyStatusType.OFF_DUTY
    }

    // statusDuration recalculates when refreshTick changes (every minute)
    val statusDuration = when (val state = dutyStatusState) {
        is DutyStatusUiState.Success -> {
            // Use refreshTick to force recalculation (it's used implicitly by being read)
            @Suppress("UNUSED_EXPRESSION")
            refreshTick
            dashboardViewModel.getDurationText(state.dutyStatus.startTime)
        }
        else -> "Loading..."
    }

    // Get ELD connection status from ViewModel
    val eldConnection by dashboardViewModel.eldConnectionStatus.collectAsState()
    val reconnectAttemptInfo by dashboardViewModel.reconnectAttemptInfo.collectAsState()
    val notificationCount = 5

    // Get vehicle motion state
    val vehicleMotionState by dashboardViewModel.vehicleMotionState.collectAsState()

    // Connection lost alert state
    val showConnectionLostAlert by dashboardViewModel.showConnectionLostAlert.collectAsState()
    val autoRestartCountdown by dashboardViewModel.autoRestartCountdown.collectAsState()

    // Stationary delay dialog state
    val showStationaryDelayDialog by dashboardViewModel.showStationaryDelayDialog.collectAsState()
    val stationaryDelayCountdown by dashboardViewModel.stationaryDelayCountdown.collectAsState()

    // Unidentified Driving events count and list
    val unidentifiedEventsCount by dashboardViewModel.unidentifiedEventsCount.collectAsState()
    val unidentifiedEvents by dashboardViewModel.unidentifiedEvents.collectAsState()

    // Check if app is locked (DRIVING + In Motion)
    val isAppLocked = currentStatus == DutyStatusType.DRIVING &&
        vehicleMotionState == VehicleMotionState.IN_MOTION

    // Debug: Log the loading state to understand why dashboard might be stuck
    android.util.Log.d("DashboardScreen", "📱 Rendering DashboardScreen - isInitialLoading=$isInitialLoading, dutyStatusState=${dutyStatusState::class.simpleName}")

    // Show loading screen while initial data is loading
    if (isInitialLoading) {
        android.util.Log.d("DashboardScreen", "📱 Showing DashboardLoadingScreen (isInitialLoading=true)")
        DashboardLoadingScreen()
        return
    }

    android.util.Log.d("DashboardScreen", "📱 Rendering main Dashboard content (isInitialLoading=false)")

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideMenuDrawer(
                navController = navController,
                userName = currentUser?.fullName ?: "Driver",
                userEmail = currentUser?.email ?: "",
                onClose = { scope.launch { drawerState.close() } },
                onLogout = {
                    // Logout clears state synchronously, then navigate
                    loginViewModel.logout()
                    navController.navigate("login") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    ) {
    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Divider(color = BorderLight, thickness = 1.dp)
                BottomNavigationBar(
                    isLocked = isAppLocked,
                    onLockedClick = { showLockedWhileDrivingDialog = true },
                    onDVIRClick = { navController.navigate("dvir") },
                    onDispatchClick = { /* TODO */ },
                    onLogsClick = { navController.navigate("logs") },
                    onTrailerDocsClick = { showTrailersModal = true },
                    onCoDriversClick = { /* TODO */ }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgSecondary)
        ) {
            // Header with gradient and curved wave
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)  // Increased height to accommodate curve
            ) {
                // Blue gradient background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Blue700, Blue600)
                            )
                        )
                )

                // Top navigation bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Menu button (hamburger) - locked when driving
                    IconButton(
                        onClick = {
                            if (isAppLocked) {
                                showLockedWhileDrivingDialog = true
                            } else {
                                scope.launch { drawerState.open() }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isAppLocked) Icons.Default.Lock else Icons.Default.Menu,
                            contentDescription = if (isAppLocked) "Locked" else "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // User name
                    Text(
                        text = currentUser?.fullName ?: "Driver",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    // Notification bell with badge
                    Box {
                        IconButton(onClick = { /* TODO: Show notifications */ }) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        if (notificationCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 8.dp)
                                    .clip(CircleShape)
                                    .background(AccentRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$notificationCount",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Curved background shape at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)  // Height of the curved section
                        .align(Alignment.BottomCenter)
                        .clip(CurvedWaveShape())
                        .background(BgSecondary)
                )
            }

            // Content with padding for bottom bar
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Status and Vehicle Cards Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Check if status change is locked (DRIVING + In Motion)
                val isStatusLocked = currentStatus == DutyStatusType.DRIVING &&
                    vehicleMotionState == VehicleMotionState.IN_MOTION

                // Status Card
                StatusCard(
                    status = currentStatus,
                    duration = statusDuration,
                    isLocked = isStatusLocked,
                    onClick = {
                        if (isStatusLocked) {
                            showLockedWhileDrivingDialog = true
                        } else {
                            showDutyStatusModal = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(75.dp)  // Reduced from 90dp to 75dp for more compact design
                )

                // Vehicle Connection Card
                VehicleConnectionCard(
                    vehicleNumber = vehicleDisplayName,
                    connectionStatus = eldConnection,
                    reconnectAttemptInfo = reconnectAttemptInfo,
                    onClick = {
                        when (eldConnection) {
                            ELDConnectionStatus.DISCONNECTED -> dashboardViewModel.connectToELD()
                            ELDConnectionStatus.CONNECTED -> dashboardViewModel.disconnectFromELD()
                            ELDConnectionStatus.PAIRING -> dashboardViewModel.disconnectFromELD() // Cancel pairing
                            ELDConnectionStatus.RECONNECTING -> dashboardViewModel.cancelReconnect()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(75.dp)  // Reduced from 90dp to 75dp for more compact design
                )
            }

            // Unidentified Driving Banner (Case #23-26)
            if (unidentifiedEventsCount > 0 || unidentifiedEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.md))
                UnidentifiedDrivingBanner(
                    eventsCount = maxOf(unidentifiedEventsCount, unidentifiedEvents.size),
                    onClick = { showUnidentifiedDrivingDialog = true },
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
            }

            // In Motion / Stationary Banner (only show when in DRIVING status)
            if (currentStatus == DutyStatusType.DRIVING) {
                Spacer(modifier = Modifier.height(Spacing.md))
                MotionStatusBanner(
                    motionState = vehicleMotionState,
                    isEldConnected = eldConnection == ELDConnectionStatus.CONNECTED,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Sync Status Indicator
            if (!isOnline || pendingSyncCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md)
                        .background(
                            if (!isOnline) Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (!isOnline) Icons.Default.WifiOff else Icons.Default.Sync,
                        contentDescription = null,
                        tint = if (!isOnline) Color(0xFFD97706) else Color(0xFF16A34A),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = if (!isOnline) {
                            "Offline mode - changes will sync when connected"
                        } else {
                            "$pendingSyncCount pending sync${if (pendingSyncCount > 1) "s" else ""}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!isOnline) Color(0xFFD97706) else Color(0xFF16A34A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // HOS Timers Section
            HOSTimersSection(
                breakTimeRemaining = hosStatus?.breakTimeRemaining ?: 0,
                breakTimeTotal = hosStatus?.breakTimeTotal ?: 480,
                driveTimeRemaining = hosStatus?.driveTimeRemaining ?: 0,
                driveTimeTotal = hosStatus?.driveTimeTotal ?: 660,
                shiftTimeRemaining = hosStatus?.shiftTimeRemaining ?: 0,
                shiftTimeTotal = hosStatus?.shiftTimeTotal ?: 840,
                cycleTimeRemaining = hosStatus?.cycleTimeRemaining ?: 0,
                cycleTimeTotal = hosStatus?.cycleTimeTotal ?: 4200,
                isLoading = hosStatus == null
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // Debug Logs Section
            val debugLogs by dashboardViewModel.debugLogs.collectAsState()
            var showDebugLogsDialog by remember { mutableStateOf(false) }

            // Always show debug button for testing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { showDebugLogsDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Debug",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Debug", fontSize = 12.sp)
                }

                // Force Sync Button
                Button(
                    onClick = {
                        scope.launch {
                            dashboardViewModel.forceSyncNow()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingSyncCount > 0) Color(0xFFEF4444) else Color(0xFF22C55E)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Sync($pendingSyncCount)", fontSize = 12.sp)
                }

                // Clear Queue Button (for testing)
                Button(
                    onClick = {
                        scope.launch {
                            dashboardViewModel.clearSyncQueue()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B7280)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Clear", fontSize = 12.sp)
                }
            }

            // Debug Logs Full Screen Dialog
            if (showDebugLogsDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showDebugLogsDialog = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF1E1E1E)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2D2D2D))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔧 Debug Logs",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                IconButton(onClick = { showDebugLogsDialog = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Logs List (scrollable)
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                reverseLayout = false
                            ) {
                                items(debugLogs.size) { index ->
                                    val log = debugLogs[index]
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            log.contains("❌") || log.contains("Error") -> Color(0xFFEF4444)
                                            log.contains("✅") || log.contains("Success") -> Color(0xFF10B981)
                                            log.contains("⚠️") || log.contains("Warning") -> Color(0xFFF59E0B)
                                            log.contains("🔍") || log.contains("Checking") -> Color(0xFF8B5CF6)
                                            log.contains("🔔") || log.contains("Calling") -> Color(0xFF3B82F6)
                                            else -> Color(0xFFD1D5DB)
                                        },
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    )
                                    if (index < debugLogs.size - 1) {
                                        Divider(
                                            color = Color(0xFF404040),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
    } // End ModalNavigationDrawer

    // Show modals when state is true
    if (showDutyStatusModal) {
        // Location state that updates when refreshed
        var currentLocationAddress by remember { mutableStateOf<String?>(null) }

        // Refresh location when modal opens
        LaunchedEffect(Unit) {
            dashboardViewModel.refreshLocation()
            // Wait a bit for GPS to update, then get the location
            delay(500)
            currentLocationAddress = dashboardViewModel.getCurrentLocation()?.address
        }

        ChangeDutyStatusModal(
            currentStatus = when (currentStatus) {
                DutyStatusType.OFF_DUTY -> "OFF_DUTY"
                DutyStatusType.ON_DUTY_NOT_DRIVING -> "ON_DUTY_NOT_DRIVING"
                DutyStatusType.SLEEPER_BERTH -> "SLEEPER_BERTH"
                DutyStatusType.DRIVING -> "DRIVING"
                DutyStatusType.PERSONAL_CONVEYANCE -> "PERSONAL_CONVEYANCE"
                DutyStatusType.YARD_MOVE -> "YARD_MOVE"
            },
            initialLocation = currentLocationAddress,
            allowYardMove = allowYardMove,
            allowPersonalConveyance = allowPersonalConveyance,
            allowManualDriveTime = allowManualDriveTime,
            onDismiss = { showDutyStatusModal = false },
            onRefreshLocation = {
                dashboardViewModel.refreshLocation()
                // Update the location after refresh
                MainScope().launch {
                    delay(500)
                    currentLocationAddress = dashboardViewModel.getCurrentLocation()?.address
                }
            },
            onConfirm = { status, location, notes ->
                // Call API to change duty status
                val newStatus = when (status) {
                    "OFF_DUTY" -> DutyStatusType.OFF_DUTY
                    "ON_DUTY_NOT_DRIVING" -> DutyStatusType.ON_DUTY_NOT_DRIVING
                    "SLEEPER_BERTH" -> DutyStatusType.SLEEPER_BERTH
                    "DRIVING" -> DutyStatusType.DRIVING
                    "PERSONAL_CONVEYANCE" -> DutyStatusType.PERSONAL_CONVEYANCE
                    "YARD_MOVE" -> DutyStatusType.YARD_MOVE
                    else -> DutyStatusType.OFF_DUTY
                }

                dashboardViewModel.changeDutyStatus(
                    token = authToken,
                    newStatus = newStatus,
                    location = location.ifBlank { null },
                    notes = notes.ifBlank { null },
                    vehicleId = currentVehicleId
                ) {
                    showDutyStatusModal = false
                }
            }
        )
    }

    if (showTrailersModal) {
        TrailersModal(
            currentTrailers = "",
            currentShippingDocs = "",
            onDismiss = { showTrailersModal = false },
            onSave = { trailers, shippingDocs ->
                // TODO: Save trailers and shipping docs
                println("✅ Save trailers: $trailers, shipping docs: $shippingDocs")
                showTrailersModal = false
            }
        )
    }

    // Connection Lost While Driving Dialog
    if (showConnectionLostAlert) {
        ConnectionLostDialog(
            countdown = autoRestartCountdown,
            onDismiss = { dashboardViewModel.dismissConnectionLostAlert() },
            onReconnect = {
                dashboardViewModel.dismissConnectionLostAlert()
                dashboardViewModel.connectToELD()
            }
        )
    }

    // Locked While Driving Dialog
    if (showLockedWhileDrivingDialog) {
        AlertDialog(
            onDismissRequest = { showLockedWhileDrivingDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Status Locked",
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "You cannot change your duty status while the vehicle is in motion.\n\nPlease bring the vehicle to a complete stop (below 5 mph) to change your status.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { showLockedWhileDrivingDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Stationary Delay Dialog (60 sec countdown after 5 min idle)
    if (showStationaryDelayDialog) {
        StationaryDelayDialog(
            countdown = stationaryDelayCountdown ?: 60,
            currentStatus = currentStatus,
            onStayDriving = { dashboardViewModel.stayDriving() },
            onGoOnDuty = { dashboardViewModel.goOnDuty() }
        )
    }

    // Unidentified Driving Events Dialog
    if (showUnidentifiedDrivingDialog) {
        UnidentifiedDrivingDialog(
            events = unidentifiedEvents,
            onDismiss = { showUnidentifiedDrivingDialog = false },
            onClearEvents = {
                dashboardViewModel.purgeUnidentifiedEvents()
                showUnidentifiedDrivingDialog = false
            }
        )
    }
}

/**
 * Stationary Delay Dialog - Shown after 5 minutes stationary
 * Has 60 second countdown, "Stay Driving" and "Go On Duty" buttons
 */
@Composable
private fun StationaryDelayDialog(
    countdown: Int,
    currentStatus: DutyStatusType,
    onStayDriving: () -> Unit,
    onGoOnDuty: () -> Unit
) {
    // Determine button text based on current status
    val stayButtonText = when (currentStatus) {
        DutyStatusType.PERSONAL_CONVEYANCE -> "Stay in PC"
        DutyStatusType.YARD_MOVE -> "Stay in YM"
        else -> "Stay Driving"
    }

    AlertDialog(
        onDismissRequest = { /* Cannot dismiss by tapping outside */ },
        icon = {
            // Countdown circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AccentOrange),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$countdown",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        title = {
            Text(
                text = "Vehicle Stationary",
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your vehicle has been stationary for 5 minutes.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                Text(
                    text = "Status will change to On Duty in $countdown seconds.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGoOnDuty,
                colors = ButtonDefaults.buttonColors(containerColor = Blue600)
            ) {
                Text("Go On Duty")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onStayDriving
            ) {
                Text(stayButtonText)
            }
        }
    )
}

/**
 * Motion Status Banner - Shows In Motion / Stationary with padlock
 */
@Composable
private fun MotionStatusBanner(
    motionState: VehicleMotionState,
    isEldConnected: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isInMotion = motionState == VehicleMotionState.IN_MOTION

    // Colors per spec: Green for In Motion, Orange for Stationary
    val backgroundColor = when {
        isInMotion -> AccentGreen        // Green when in motion
        else -> AccentOrange             // Orange for stationary (per Case #10)
    }

    val statusText = when {
        !isEldConnected -> "Stationary (No ELD)"
        isInMotion -> "In Motion"
        else -> "Stationary"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Padlock icon (locked when stationary, unlocked when in motion)
            Icon(
                imageVector = if (isInMotion && isEldConnected) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = if (isInMotion) "Unlocked" else "Locked",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(Spacing.sm))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * Unidentified Driving Dialog - Shows list of UD events
 */
@Composable
private fun UnidentifiedDrivingDialog(
    events: List<com.eld.driver.ble.models.UnidentifiedEvent>,
    onDismiss: () -> Unit,
    onClearEvents: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentOrange)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unidentified Driving",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                // Info text
                Text(
                    text = "The following driving occurred without a logged-in driver. Review and claim these events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )

                // Events list
                if (events.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No unidentified driving events",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(events.size) { index ->
                            val event = events[index]
                            UnidentifiedEventCard(event = event)
                        }
                    }
                }

                // Bottom buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }

                    if (events.isNotEmpty()) {
                        Button(
                            onClick = onClearEvents,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Claim All")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card for displaying a single Unidentified Event
 */
@Composable
private fun UnidentifiedEventCard(
    event: com.eld.driver.ble.models.UnidentifiedEvent
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Reason and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.getReasonString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                event.timestamp?.let { ts ->
                    Text(
                        text = formatTimestamp(ts),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Speed
                Column {
                    Text(
                        text = "Speed",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${String.format("%.1f", (event.speed ?: 0.0) * 0.621371)} mph",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Odometer
                Column {
                    Text(
                        text = "Odometer",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${String.format("%.1f", (event.odometer ?: 0.0) * 0.621371)} mi",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Engine Hours
                Column {
                    Text(
                        text = "Eng. Hours",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = String.format("%.1f", event.engineHours ?: 0.0),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Location
            if (event.latitude != null && event.longitude != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${String.format("%.4f", event.latitude)}, ${String.format("%.4f", event.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Format timestamp for display
 */
private fun formatTimestamp(timestamp: Long): String {
    return try {
        val date = java.util.Date(timestamp * 1000) // Convert seconds to ms
        java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.US).format(date)
    } catch (e: Exception) {
        "Unknown"
    }
}

/**
 * Unidentified Driving Banner (Case #23-26)
 * Shows orange banner when there are unidentified driving events to review
 */
@Composable
private fun UnidentifiedDrivingBanner(
    eventsCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AccentOrange),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(Spacing.sm))

                Text(
                    text = "Unidentified Driving",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "$eventsCount event${if (eventsCount > 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

/**
 * Connection Lost Dialog with countdown
 */
@Composable
private fun ConnectionLostDialog(
    countdown: Int?,
    onDismiss: () -> Unit,
    onReconnect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Connection Lost",
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ELD connection was lost while vehicle was in motion.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = "Vehicle status changed to STATIONARY",
                    color = AccentRed,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (countdown != null) {
                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Countdown circle
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Blue600),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$countdown",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Text(
                        text = "Auto-reconnect in $countdown seconds",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onReconnect,
                colors = ButtonDefaults.buttonColors(containerColor = Blue600)
            ) {
                Text("Reconnect Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

/**
 * Status Card - Matches iOS StatusCardView exactly
 */
@Composable
private fun StatusCard(
    status: DutyStatusType,
    duration: String,
    isLocked: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md, vertical = 10.dp),  // Reduced from 12dp to 10dp
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),  // Reduced spacing
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status badge (50dp circle - reduced from 60dp)
            Box(
                modifier = Modifier
                    .size(50.dp)  // Reduced from 60dp to 50dp
                    .clip(CircleShape)
                    .background(getStatusColor(status)),
                contentAlignment = Alignment.Center
            ) {
                if (isLocked) {
                    // Show lock icon when driving and in motion
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = status.shortName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp  // Reduced from 16sp to 14sp
                    )
                }
            }

            // Text content
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {  // Reduced from 4dp to 2dp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = status.displayName,
                        style = MaterialTheme.typography.bodyMedium,  // Changed from bodyLarge to bodyMedium
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = AccentRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = if (isLocked) "Stop to change" else duration,
                    style = MaterialTheme.typography.bodySmall,  // Changed from bodyMedium to bodySmall
                    color = if (isLocked) AccentRed else TextSecondary
                )
            }
        }
    }
}

/**
 * Get status color matching iOS exactly
 */
private fun getStatusColor(status: DutyStatusType): Color {
    return when (status) {
        DutyStatusType.OFF_DUTY -> StatusOff
        DutyStatusType.ON_DUTY_NOT_DRIVING -> AccentGreen
        DutyStatusType.SLEEPER_BERTH -> Blue600
        DutyStatusType.DRIVING -> AccentGreen
        DutyStatusType.PERSONAL_CONVEYANCE -> AccentOrange
        DutyStatusType.YARD_MOVE -> AccentYellow
    }
}

/**
 * Vehicle Connection Card - Matches iOS VehicleConnectionCardView exactly
 */
@Composable
private fun VehicleConnectionCard(
    vehicleNumber: String,
    connectionStatus: ELDConnectionStatus,
    reconnectAttemptInfo: Pair<Int, Int>? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (connectionStatus) {
        ELDConnectionStatus.DISCONNECTED -> StatusConnectRed
        ELDConnectionStatus.PAIRING -> AccentYellow
        ELDConnectionStatus.RECONNECTING -> AccentOrange
        ELDConnectionStatus.CONNECTED -> StatusConnectGreen
    }

    val icon = when (connectionStatus) {
        ELDConnectionStatus.DISCONNECTED -> Icons.Default.WifiOff
        ELDConnectionStatus.PAIRING -> Icons.Default.Wifi
        ELDConnectionStatus.RECONNECTING -> Icons.Default.Sync
        ELDConnectionStatus.CONNECTED -> Icons.Default.Wifi
    }

    val displayText = if (connectionStatus == ELDConnectionStatus.RECONNECTING && reconnectAttemptInfo != null) {
        "Reconnecting ${reconnectAttemptInfo.first}/${reconnectAttemptInfo.second}..."
    } else {
        connectionStatus.displayText
    }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md, vertical = 10.dp),  // Reduced from 12dp to 10dp
            verticalArrangement = Arrangement.spacedBy(6.dp)  // Reduced from 8dp to 6dp
        ) {
            // First line: Truck icon + Vehicle number
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),  // Reduced from 8dp to 6dp
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)  // Reduced from 20dp to 18dp
                )
                Text(
                    text = vehicleNumber,
                    style = MaterialTheme.typography.bodyMedium,  // Changed from bodyLarge to bodyMedium
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Second line: Wifi icon + Connection status
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),  // Reduced from 8dp to 6dp
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)  // Reduced from 16dp to 14dp
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,  // Changed from bodyLarge to bodyMedium
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Bottom Navigation Bar - Matches iOS exactly
 */
@Composable
private fun BottomNavigationBar(
    isLocked: Boolean = false,
    onLockedClick: () -> Unit = {},
    onDVIRClick: () -> Unit,
    onDispatchClick: () -> Unit,
    onLogsClick: () -> Unit,
    onTrailerDocsClick: () -> Unit,
    onCoDriversClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isLocked) Color(0xFFE5E7EB) else Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BottomNavButton(
            icon = Icons.Default.VerifiedUser, // checkmark.shield equivalent
            label = "DVIR",
            badgeCount = null,
            isLocked = isLocked,
            onClick = if (isLocked) onLockedClick else onDVIRClick,
            modifier = Modifier.weight(1f)
        )

        BottomNavButton(
            icon = Icons.Default.Headset,
            label = "Dispatch",
            badgeCount = null,
            isLocked = isLocked,
            onClick = if (isLocked) onLockedClick else onDispatchClick,
            modifier = Modifier.weight(1f)
        )

        BottomNavButton(
            icon = Icons.Default.ShowChart, // chart.line.uptrend.xyaxis equivalent
            label = "Logs",
            badgeCount = if (isLocked) null else 1,
            badgeColor = AccentRed,
            isLocked = isLocked,
            onClick = if (isLocked) onLockedClick else onLogsClick,
            modifier = Modifier.weight(1f)
        )

        BottomNavButton(
            icon = Icons.Default.LocalShipping, // truck.box equivalent
            label = "Trailer/Docs",
            badgeCount = if (isLocked) null else 1,
            badgeColor = AccentOrange,
            isLocked = isLocked,
            onClick = if (isLocked) onLockedClick else onTrailerDocsClick,
            modifier = Modifier.weight(1f)
        )

        BottomNavButton(
            icon = Icons.Default.People, // person.2 equivalent
            label = "Co-Drivers",
            badgeCount = null,
            isLocked = isLocked,
            onClick = if (isLocked) onLockedClick else onCoDriversClick,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Bottom Nav Button - Matches iOS BottomNavButton exactly
 */
@Composable
private fun BottomNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeCount: Int?,
    badgeColor: Color = AccentRed,
    isLocked: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColor = if (isLocked) Color(0xFFE5E7EB) else Color.White
    val contentColor = if (isLocked) Color(0xFF9CA3AF) else TextPrimary

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(CornerRadius.small),
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )

                if (badgeCount != null && !isLocked) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-4).dp)
                            .clip(CircleShape)
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$badgeCount",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = label,
                fontSize = 11.sp,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}


/**
 * Loading screen shown while Dashboard initializes
 */
@Composable
private fun DashboardLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Truck icon in a circle
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Blue600.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Blue600
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App name
            Text(
                text = "eldmate",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Blue600,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Blue600,
                strokeWidth = 4.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Loading...",
                fontSize = 16.sp,
                color = TextSecondary
            )
        }
    }
}
