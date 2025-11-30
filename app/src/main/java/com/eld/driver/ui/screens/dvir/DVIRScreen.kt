package com.eld.driver.ui.screens.dvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.data.models.Vehicle
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.theme.*

/**
 * DVIR Main Screen - Entry point for DVIR inspections
 * Shows "Start inspection" button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DVIRScreen(
    navController: NavController,
    authToken: String,
    currentDutyStatus: DutyStatusType?,
    currentVehicle: Vehicle?,
    isEldConnected: Boolean = false,
    onChangeDutyStatus: (location: String) -> Unit,
    dvirViewModel: DVIRViewModel = viewModel()
) {
    var showOnDutyConfirmDialog by remember { mutableStateOf(false) }
    var locationForStatusChange by remember { mutableStateOf("") }

    // Set vehicle in viewmodel
    LaunchedEffect(currentVehicle) {
        dvirViewModel.setCurrentVehicle(currentVehicle)
        dvirViewModel.refreshLocation()
    }

    val currentLocation by dvirViewModel.currentLocation.collectAsState()

    // Update location in dialog
    LaunchedEffect(currentLocation) {
        if (!currentLocation.isNullOrBlank() && locationForStatusChange.isBlank()) {
            locationForStatusChange = currentLocation ?: ""
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Blue Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
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
                        .padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "DVIR",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    // ELD Connection indicator
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "ELD Status",
                            tint = if (isEldConnected) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                }

                // Curved background shape at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .clip(CurvedWaveShape())
                        .background(Color.White)
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(horizontal = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(Spacing.xl))

                // Start Inspection Button
                Button(
                    onClick = {
                        if (currentVehicle == null) {
                            // No vehicle selected - show error or redirect
                            return@Button
                        }

                        // Check if driver is On Duty
                        if (currentDutyStatus == DutyStatusType.ON_DUTY_NOT_DRIVING) {
                            // Already on duty, go to inspection
                            navController.navigate("driver_inspection/${currentVehicle.id}")
                        } else {
                            // Need to change to On Duty first
                            showOnDutyConfirmDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(CornerRadius.medium),
                    enabled = currentVehicle != null
                ) {
                    Text(
                        text = "Start inspection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (currentVehicle == null) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = "Please select a vehicle first",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentRed
                    )
                }
            }
        }
    }

    // On Duty Confirmation Dialog
    if (showOnDutyConfirmDialog) {
        OnDutyConfirmDialog(
            location = locationForStatusChange,
            onLocationChange = { locationForStatusChange = it },
            onRefreshLocation = { dvirViewModel.refreshLocation() },
            onDismiss = { showOnDutyConfirmDialog = false },
            onConfirm = {
                onChangeDutyStatus(locationForStatusChange)
                showOnDutyConfirmDialog = false
                // Navigate to inspection after status change
                currentVehicle?.let {
                    navController.navigate("driver_inspection/${it.id}")
                }
            }
        )
    }
}

/**
 * Dialog to confirm switching to On Duty status before inspection
 */
@Composable
private fun OnDutyConfirmDialog(
    location: String,
    onLocationChange: (String) -> Unit,
    onRefreshLocation: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Change Duty Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // Location field
                OutlinedTextField(
                    value = location,
                    onValueChange = onLocationChange,
                    placeholder = { Text("Location", color = TextSecondary) },
                    trailingIcon = {
                        IconButton(onClick = onRefreshLocation) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Get location",
                                tint = Blue600
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        unfocusedContainerColor = Color(0xFFF3F4F6),
                        focusedContainerColor = Color(0xFFF3F4F6),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Blue600
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "You must switch to On Duty status to perform the Vehicle Inspection. Please Confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Blue600)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Blue600)
            }
        }
    )
}
