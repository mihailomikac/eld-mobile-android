package com.eld.driver.ui.screens.dashboard

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

    var showDutyStatusModal by remember { mutableStateOf(false) }
    var showTrailersModal by remember { mutableStateOf(false) }

    // Get current user from LoginViewModel
    val currentUser by loginViewModel.currentUser.collectAsState()

    // Get current vehicle from VehicleViewModel
    val currentVehicleId by vehicleViewModel.currentVehicleId.collectAsState()
    val vehicles by vehicleViewModel.filteredVehicles.collectAsState()
    val currentVehicle = vehicles.find { it.id == currentVehicleId }

    // Load data from API
    LaunchedEffect(Unit) {
        if (authToken.isNotEmpty()) {
            dashboardViewModel.loadCurrentDutyStatus(authToken)
            dashboardViewModel.loadHOSStatus(authToken)
        }
    }

    val dutyStatusState by dashboardViewModel.currentDutyStatus.collectAsState()
    val hosStatus by dashboardViewModel.hosStatus.collectAsState()

    // Extract current status and duration from API response
    val currentStatus = when (val state = dutyStatusState) {
        is DutyStatusUiState.Success -> state.dutyStatus.dutyStatus
        else -> DutyStatusType.OFF_DUTY
    }

    val statusDuration = when (val state = dutyStatusState) {
        is DutyStatusUiState.Success -> dashboardViewModel.getDurationText(state.dutyStatus.startTime)
        else -> "Loading..."
    }

    // Get ELD connection status from ViewModel
    val eldConnection by dashboardViewModel.eldConnectionStatus.collectAsState()
    val notificationCount = 5

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideMenuDrawer(
                navController = navController,
                userName = currentUser?.fullName ?: "Driver",
                userEmail = currentUser?.email ?: "",
                onClose = { scope.launch { drawerState.close() } },
                onLogout = {
                    loginViewModel.logout()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
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
                    onDVIRClick = { navController.navigate("inspections") },
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
                    // Menu button (hamburger)
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
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
                // Status Card
                StatusCard(
                    status = currentStatus,
                    duration = statusDuration,
                    onClick = { showDutyStatusModal = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(75.dp)  // Reduced from 90dp to 75dp for more compact design
                )

                // Vehicle Connection Card
                VehicleConnectionCard(
                    vehicleNumber = currentVehicle?.vehicleNumber ?: "No Vehicle",
                    connectionStatus = eldConnection,
                    onClick = {
                        when (eldConnection) {
                            ELDConnectionStatus.DISCONNECTED -> dashboardViewModel.connectToELD()
                            ELDConnectionStatus.CONNECTED -> dashboardViewModel.disconnectFromELD()
                            ELDConnectionStatus.PAIRING -> { /* Do nothing while pairing */ }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(75.dp)  // Reduced from 90dp to 75dp for more compact design
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            // HOS Timers Section
            hosStatus?.let { hos ->
                HOSTimersSection(
                    breakTimeRemaining = hos.breakTimeRemaining,
                    breakTimeTotal = hos.breakTimeTotal,
                    driveTimeRemaining = hos.driveTimeRemaining,
                    driveTimeTotal = hos.driveTimeTotal,
                    shiftTimeRemaining = hos.shiftTimeRemaining,
                    shiftTimeTotal = hos.shiftTimeTotal,
                    cycleTimeRemaining = hos.cycleTimeRemaining,
                    cycleTimeTotal = hos.cycleTimeTotal
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            // Debug Logs Section
            val debugLogs by dashboardViewModel.debugLogs.collectAsState()
            var showDebugLogsDialog by remember { mutableStateOf(false) }

            if (debugLogs.isNotEmpty()) {
                Button(
                    onClick = { showDebugLogsDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Debug Logs",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Debug Logs (${debugLogs.size})")
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
        ChangeDutyStatusModal(
            currentStatus = when (currentStatus) {
                DutyStatusType.OFF_DUTY -> "OFF_DUTY"
                DutyStatusType.ON_DUTY_NOT_DRIVING -> "ON_DUTY_NOT_DRIVING"
                DutyStatusType.SLEEPER_BERTH -> "SLEEPER_BERTH"
                DutyStatusType.DRIVING -> "DRIVING"
                DutyStatusType.PERSONAL_CONVEYANCE -> "PERSONAL_CONVEYANCE"
                DutyStatusType.YARD_MOVE -> "YARD_MOVE"
            },
            onDismiss = { showDutyStatusModal = false },
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
                    notes = notes.ifBlank { null }
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
}

/**
 * Status Card - Matches iOS StatusCardView exactly
 */
@Composable
private fun StatusCard(
    status: DutyStatusType,
    duration: String,
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
                Text(
                    text = status.shortName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp  // Reduced from 16sp to 14sp
                )
            }

            // Text content
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {  // Reduced from 4dp to 2dp
                Text(
                    text = status.displayName,
                    style = MaterialTheme.typography.bodyMedium,  // Changed from bodyLarge to bodyMedium
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = duration,
                    style = MaterialTheme.typography.bodySmall,  // Changed from bodyMedium to bodySmall
                    color = TextSecondary
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (connectionStatus) {
        ELDConnectionStatus.DISCONNECTED -> StatusConnectRed
        ELDConnectionStatus.PAIRING -> AccentYellow
        ELDConnectionStatus.CONNECTED -> StatusConnectGreen
    }

    val icon = when (connectionStatus) {
        ELDConnectionStatus.DISCONNECTED -> Icons.Default.WifiOff
        ELDConnectionStatus.PAIRING -> Icons.Default.Wifi
        ELDConnectionStatus.CONNECTED -> Icons.Default.Wifi
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
                    text = connectionStatus.displayText,
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
    onDVIRClick: () -> Unit,
    onDispatchClick: () -> Unit,
    onLogsClick: () -> Unit,
    onTrailerDocsClick: () -> Unit,
    onCoDriversClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BottomNavButton(
            icon = Icons.Default.VerifiedUser, // checkmark.shield equivalent
            label = "DVIR",
            badgeCount = null,
            onClick = onDVIRClick,
            modifier = Modifier.weight(1f)
        )

        BottomNavButton(
            icon = Icons.Default.Headset,
            label = "Dispatch",
            badgeCount = null,
            onClick = onDispatchClick,
            modifier = Modifier.weight(1f)
        )

        BottomNavButton(
            icon = Icons.Default.ShowChart, // chart.line.uptrend.xyaxis equivalent
            label = "Logs",
            badgeCount = 1,
            badgeColor = AccentRed,
            onClick = onLogsClick,
            modifier = Modifier.weight(1f)
        )

        BottomNavButton(
            icon = Icons.Default.LocalShipping, // truck.box equivalent
            label = "Trailer/Docs",
            badgeCount = 1,
            badgeColor = AccentOrange,
            onClick = onTrailerDocsClick,
            modifier = Modifier.weight(1f)
        )

        BottomNavButton(
            icon = Icons.Default.People, // person.2 equivalent
            label = "Co-Drivers",
            badgeCount = null,
            onClick = onCoDriversClick,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = TextPrimary
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
                    imageVector = icon,
                    contentDescription = label,
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )

                if (badgeCount != null) {
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
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}
