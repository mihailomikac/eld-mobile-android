package com.eld.driver.ui.screens.bletest

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eld.driver.ble.models.BleConnectionState
import com.eld.driver.ble.models.BleDataState
import com.eld.driver.ble.models.GeometrisEldData
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("MissingPermission")
@Composable
fun BleTestScreen(
    navController: NavController,
    viewModel: BleTestViewModel = viewModel()
) {
    // TODO: Change hardcoded ELD serial to real device serial number
    val hardcodedEldSerial = "87A4141310908"

    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(viewModel.hasRequiredPermissions()) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    // Request permissions on first launch
    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    val connectionState by viewModel.connectionState.collectAsState()
    val dataState by viewModel.dataState.collectAsState()
    val eldDataFromFlow by viewModel.eldData.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()

    // Extract eldData from dataState if available, otherwise use eldDataFromFlow
    val eldData = when (val state = dataState) {
        is BleDataState.DataReceived -> state.eldData
        else -> eldDataFromFlow
    }

    val isScanning = connectionState is BleConnectionState.Scanning
    val isConnected = connectionState is BleConnectionState.Connected ||
            connectionState is BleConnectionState.ServicesDiscovered ||
            connectionState is BleConnectionState.Ready
    val isReady = connectionState is BleConnectionState.Ready

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // Header with curved design
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Blue700, Blue600)
                        )
                    )
                    .clip(CurvedWaveShape())
            )

            // Title and back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    // Navigate explicitly to dashboard instead of popBackStack
                    navController.navigate("dashboard") {
                        popUpTo("ble_test") { inclusive = true }
                        launchSingleTop = true
                    }
                }) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "BLE Test",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission warning
            if (!hasPermissions) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Bluetooth Permissions Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "This app needs Bluetooth permissions to scan and connect to ELD devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Button(
                                onClick = {
                                    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        )
                                    } else {
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    }
                                    permissionLauncher.launch(permissionsToRequest)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Text("Grant Permissions")
                            }
                        }
                    }
                }
            }

            // Connection Status Card
            item {
                ConnectionStatusCard(
                    connectionState = connectionState,
                    dataState = dataState,
                    isScanning = isScanning,
                    isConnected = isConnected,
                    isReady = isReady,
                    hasPermissions = hasPermissions,
                    onStartScan = { viewModel.startScan() },
                    onStopScan = { viewModel.stopScan() },
                    onDisconnect = { viewModel.disconnect() }
                )
            }

            // Discovered Devices
            if (discoveredDevices.isNotEmpty()) {
                item {
                    Text(
                        text = "Discovered Devices (${discoveredDevices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(discoveredDevices) { (device, rssi) ->
                    DeviceCard(
                        device = device,
                        rssi = rssi,
                        isConnected = isConnected,
                        onClick = {
                            if (!isConnected) {
                                viewModel.connect(device)
                            }
                        }
                    )
                }
            }

            // ELD Data Display
            if (isReady) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.requestEldData() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Request Data")
                        }
                    }
                }
            }

            // Parser Logs
            item {
                val logs = viewModel.getDebugLogs()
                if (logs.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Parser Logs (${logs.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            logs.takeLast(15).forEach { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (log.startsWith("ERROR")) Color(0xFFEF4444) else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (log.startsWith("ERROR")) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Debug info card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Debug Info",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Data State: ${dataState::class.simpleName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "ELD Data: ${if (eldData != null) "Available" else "Null"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        if (eldData != null) {
                            Text(
                                text = "Protocol: v${eldData!!.protocolVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Fields:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "VIN: ${eldData!!.vin ?: "null"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Serial: ${eldData!!.serialNumber ?: "null"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "RPM: ${eldData!!.rpm ?: "null"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Speed: ${eldData!!.speed ?: "null"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Odometer: ${eldData!!.odometer ?: "null"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Engine Hours: ${eldData!!.engineHours ?: "null"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Location: ${if (eldData!!.latitude != null && eldData!!.longitude != null) "${eldData!!.latitude}, ${eldData!!.longitude}" else "null"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Show hardcoded serial when not connected
            if (!isConnected && eldData == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Test ELD Serial (Hardcoded)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = hardcodedEldSerial,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (eldData != null) {
                item {
                    Text(
                        text = "ELD Data",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                item {
                    EldDataCard(eldData = eldData!!)
                }
            } else if (isReady) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "No Data Received",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Connected but no ELD data received yet. Click 'Request Data' button above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Error display
            if (connectionState is BleConnectionState.Error) {
                item {
                    ErrorCard(error = (connectionState as BleConnectionState.Error).message)
                }
            }

            if (dataState is BleDataState.Error) {
                item {
                    ErrorCard(error = (dataState as BleDataState.Error).message)
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    connectionState: BleConnectionState,
    dataState: BleDataState,
    isScanning: Boolean,
    isConnected: Boolean,
    isReady: Boolean,
    hasPermissions: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Connection Status",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = when {
                                isReady -> Color(0xFF10B981)
                                isConnected -> Color(0xFFFBBF24)
                                isScanning -> Blue600
                                else -> Color(0xFFEF4444)
                            },
                            shape = RoundedCornerShape(6.dp)
                        )
                )
                Text(
                    text = when (connectionState) {
                        is BleConnectionState.Disconnected -> "Disconnected"
                        is BleConnectionState.Scanning -> "Scanning for devices..."
                        is BleConnectionState.DeviceFound -> "Device found"
                        is BleConnectionState.Connecting -> "Connecting..."
                        is BleConnectionState.Connected -> "Connected"
                        is BleConnectionState.ServicesDiscovered -> "Discovering services..."
                        is BleConnectionState.Ready -> "Ready"
                        is BleConnectionState.Reconnecting -> "Reconnecting ${connectionState.attempt}/${connectionState.maxAttempts}..."
                        is BleConnectionState.Error -> "Error"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }

            // Data state
            if (dataState !is BleDataState.Idle) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (dataState) {
                            is BleDataState.Receiving -> Icons.Default.DownloadForOffline
                            is BleDataState.DataReceived -> Icons.Default.CheckCircle
                            is BleDataState.Error -> Icons.Default.Error
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = when (dataState) {
                            is BleDataState.Receiving -> Blue600
                            is BleDataState.DataReceived -> Color(0xFF10B981)
                            is BleDataState.Error -> Color(0xFFEF4444)
                            else -> TextSecondary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = when (dataState) {
                            is BleDataState.Receiving -> "Receiving data..."
                            is BleDataState.DataReceived -> "Data received"
                            is BleDataState.Error -> "Data error"
                            else -> "Idle"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isConnected) {
                    Button(
                        onClick = if (isScanning) onStopScan else onStartScan,
                        modifier = Modifier.weight(1f),
                        enabled = hasPermissions,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) Color(0xFFEF4444) else Blue600
                        )
                    ) {
                        Icon(
                            if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isScanning) "Stop Scan" else "Start Scan")
                    }
                } else {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceCard(
    device: BluetoothDevice,
    rssi: Int,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnected) { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when {
                        rssi > -60 -> Icons.Default.SignalCellularAlt
                        rssi > -80 -> Icons.Default.SignalCellular4Bar
                        else -> Icons.Default.SignalCellularAlt
                    },
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "$rssi dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun EldDataCard(eldData: GeometrisEldData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Vehicle Info
            if (eldData.vin != null) {
                DataRow(icon = Icons.Default.CreditCard, label = "VIN", value = eldData.vin!!)
            }
            if (eldData.serialNumber != null) {
                DataRow(icon = Icons.Default.Info, label = "Serial", value = eldData.serialNumber!!)
            }

            Divider()

            // Odometer
            if (eldData.odometer != null) {
                val miles = eldData.odometer!! * 0.621371
                DataRow(
                    icon = Icons.Default.Speed,
                    label = "Odometer",
                    value = "%.1f km (%.1f mi)".format(eldData.odometer, miles)
                )
                if (eldData.odometerTimestamp != null) {
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    DataRow(
                        icon = Icons.Default.Schedule,
                        label = "Odometer Timestamp",
                        value = timeFormat.format(eldData.odometerTimestamp)
                    )
                }
            }

            // Engine Hours
            if (eldData.engineHours != null) {
                DataRow(
                    icon = Icons.Default.Schedule,
                    label = "Engine Hrs",
                    value = "%.6f".format(eldData.engineHours)
                )
                if (eldData.engineHoursTimestamp != null) {
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    DataRow(
                        icon = Icons.Default.Schedule,
                        label = "Engine Hrs Timestamp",
                        value = timeFormat.format(eldData.engineHoursTimestamp)
                    )
                }
            }

            // Speed
            if (eldData.speed != null) {
                DataRow(
                    icon = Icons.Default.Speed,
                    label = "Speed",
                    value = "%.0f km/h".format(eldData.speed),
                    status = if (eldData.isVehicleMoving()) "Moving" else "Stopped",
                    statusColor = if (eldData.isVehicleMoving()) Color(0xFF10B981) else TextSecondary
                )
                if (eldData.speedTimestamp != null) {
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    DataRow(
                        icon = Icons.Default.Schedule,
                        label = "Speed Timestamp",
                        value = timeFormat.format(eldData.speedTimestamp)
                    )
                }
            }

            // RPM
            if (eldData.rpm != null) {
                DataRow(
                    icon = Icons.Default.Speed,
                    label = "RPM",
                    value = "%.0f".format(eldData.rpm),
                    status = if (eldData.isEngineRunning()) "Engine Running" else "Engine Stopped",
                    statusColor = if (eldData.isEngineRunning()) Color(0xFF10B981) else Color(0xFFEF4444)
                )
                if (eldData.rpmTimestamp != null) {
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    DataRow(
                        icon = Icons.Default.Schedule,
                        label = "RPM Timestamp",
                        value = timeFormat.format(eldData.rpmTimestamp)
                    )
                }
            }

            // Location Data
            Divider()

            if (eldData.latitude != null) {
                DataRow(
                    icon = Icons.Default.LocationOn,
                    label = "Latitude",
                    value = "%.6f°".format(eldData.latitude)
                )
            }

            if (eldData.longitude != null) {
                DataRow(
                    icon = Icons.Default.LocationOn,
                    label = "Longitude",
                    value = "%.6f°".format(eldData.longitude)
                )
            }

            if (eldData.gpsTime != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                DataRow(
                    icon = Icons.Default.Schedule,
                    label = "GPS Time",
                    value = dateFormat.format(Date(eldData.gpsTime!!))
                )
            }

            // Unidentified Events
            if (eldData.totalUnidentifiedEvents != null && eldData.totalUnidentifiedEvents!! > 0) {
                Divider()
                DataRow(
                    icon = Icons.Default.Warning,
                    label = "Unidentified Events",
                    value = eldData.totalUnidentifiedEvents.toString(),
                    statusColor = Color(0xFFFBBF24)
                )
            }

            if (eldData.unidentifiedEvents.isNotEmpty()) {
                eldData.unidentifiedEvents.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = event.getReasonString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (event.speed != null) {
                                Text(
                                    text = "Speed: %.1f km/h".format(event.speed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DataRow(
    icon: ImageVector,
    label: String,
    value: String,
    status: String? = null,
    statusColor: Color = TextSecondary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Blue600,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFEF4444)
            )
        }
    }
}
