package com.eld.driver.ui.screens.dvir

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.eld.driver.data.models.InspectionType
import com.eld.driver.data.models.VehicleDefectItem
import com.eld.driver.data.models.AssetDefectItem
import com.eld.driver.data.models.Vehicle
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.TimeZone

/**
 * Driver Inspection Screen - Form for DVIR inspection
 * Matches the iOS design with Pre-Trip/Post-Trip tabs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverInspectionScreen(
    navController: NavController,
    authToken: String,
    vehicleId: Int,
    vehicle: Vehicle?,
    dvirViewModel: DVIRViewModel = viewModel()
) {
    var inspectionType by remember { mutableStateOf(InspectionType.PRE_TRIP) }
    var odometer by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var trailerId by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val selectedVehicleDefects by dvirViewModel.selectedVehicleDefects.collectAsState()
    val selectedAssetDefects by dvirViewModel.selectedAssetDefects.collectAsState()
    val createInspectionState by dvirViewModel.createInspectionState.collectAsState()
    val currentLocation by dvirViewModel.currentLocation.collectAsState()
    val currentOdometer by dvirViewModel.currentOdometer.collectAsState()
    val eldConnectionState by dvirViewModel.eldConnectionState.collectAsState()
    val eldData by dvirViewModel.eldData.collectAsState()

    // Load defects form, location, and odometer from ELD
    LaunchedEffect(vehicleId) {
        dvirViewModel.loadVehicleDefects(authToken, vehicleId)
        dvirViewModel.refreshLocation()
        dvirViewModel.refreshOdometer()
    }

    // Re-fetch odometer when ELD connection state changes
    LaunchedEffect(eldConnectionState) {
        dvirViewModel.refreshOdometer()
    }

    // Update location field from GPS
    LaunchedEffect(currentLocation) {
        if (!currentLocation.isNullOrBlank() && location.isBlank()) {
            location = currentLocation ?: ""
        }
    }

    // Update odometer field from ELD device (via ViewModel)
    LaunchedEffect(currentOdometer) {
        if (currentOdometer != null && odometer.isBlank()) {
            // Format to whole number (miles)
            odometer = currentOdometer!!.toLong().toString()
        }
    }

    // Also directly watch ELD data for odometer updates
    // This catches cases where odometer data arrives after screen opens
    LaunchedEffect(eldData?.odometer) {
        val odometerKm = eldData?.odometer
        if (odometerKm != null && odometerKm > 0 && odometer.isBlank()) {
            // Convert km to miles (1 km = 0.621371 miles)
            val odometerMiles = (odometerKm * 0.621371).toLong()
            odometer = odometerMiles.toString()
        }
    }

    // Current time formatted in COMPANY TIMEZONE
    val companyTimeZone = remember { dvirViewModel.getCompanyTimeZone() }
    val inspectionTime = remember(companyTimeZone) {
        SimpleDateFormat("hh:mm a", Locale.US).apply {
            timeZone = companyTimeZone
        }.format(Date())
    }

    val isLoading = createInspectionState is CreateInspectionState.Loading

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
                        text = "Driver Inspection",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(48.dp))
                }

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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md)
            ) {
                Spacer(modifier = Modifier.height(Spacing.md))

                // Pre-Trip / Post-Trip Toggle
                InspectionTypeToggle(
                    selectedType = inspectionType,
                    onTypeSelected = { inspectionType = it }
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Vehicle Id (readonly)
                ReadOnlyField(
                    label = "Vehicle Id",
                    value = vehicle?.vehicleNumber ?: "Vehicle #$vehicleId"
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // VIN (readonly)
                ReadOnlyField(
                    label = "VIN",
                    value = vehicle?.vin ?: "N/A"
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Odometer (editable)
                EditableField(
                    value = odometer,
                    onValueChange = { odometer = it },
                    placeholder = "Odometer"
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Location (editable)
                EditableField(
                    value = location,
                    onValueChange = { location = it },
                    placeholder = "Location"
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Inspection time (readonly)
                ReadOnlyField(
                    label = "Inspection time",
                    value = inspectionTime
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Vehicle Defects Section
                DefectsSection(
                    title = "Vehicle Defects",
                    defectsCount = selectedVehicleDefects.size,
                    onAddRemoveClick = {
                        navController.navigate("vehicle_defects/$vehicleId")
                    }
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Trailer Id (editable)
                EditableField(
                    value = trailerId,
                    onValueChange = { trailerId = it },
                    placeholder = "Trailer Id"
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Trailer Defects Section
                DefectsSection(
                    title = "Trailer Defects",
                    defectsCount = selectedAssetDefects.size,
                    onAddRemoveClick = {
                        // TODO: Navigate to trailer defects with asset ID
                        navController.navigate("trailer_defects/$vehicleId")
                    },
                    enabled = trailerId.isNotBlank()
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Sign and Save Button
                Button(
                    onClick = {
                        dvirViewModel.createInspection(
                            token = authToken,
                            vehicleId = vehicleId,
                            inspectionType = inspectionType,
                            odometer = odometer.toDoubleOrNull(),
                            location = location.ifBlank { null },
                            notes = null,
                            onSuccess = {
                                showSuccessDialog = true
                            },
                            onError = { error ->
                                errorMessage = error
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(CornerRadius.medium),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Sign and Save the Report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Inspection Saved") },
            text = { Text("Your DVIR inspection has been saved successfully.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        dvirViewModel.reset()
                        navController.popBackStack("dvir", inclusive = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Error Dialog
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = { errorMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                ) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Pre-Trip / Post-Trip Toggle
 */
@Composable
private fun InspectionTypeToggle(
    selectedType: InspectionType,
    onTypeSelected: (InspectionType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, Blue600, RoundedCornerShape(8.dp))
    ) {
        // Pre-Trip
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    if (selectedType == InspectionType.PRE_TRIP) Blue600 else Color.White
                )
                .clickable { onTypeSelected(InspectionType.PRE_TRIP) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Pre-Trip",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selectedType == InspectionType.PRE_TRIP) Color.White else Blue600
            )
        }

        // Post-Trip
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    if (selectedType == InspectionType.POST_TRIP) Blue600 else Color.White
                )
                .clickable { onTypeSelected(InspectionType.POST_TRIP) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Post-Trip",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selectedType == InspectionType.POST_TRIP) Color.White else Blue600
            )
        }
    }
}

/**
 * Read-only field with label
 */
@Composable
private fun ReadOnlyField(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F4F6))
            .padding(16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

/**
 * Editable field with placeholder
 */
@Composable
private fun EditableField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextSecondary) },
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
}

/**
 * Defects section with Add/Remove button
 */
@Composable
private fun DefectsSection(
    title: String,
    defectsCount: Int,
    onAddRemoveClick: () -> Unit,
    enabled: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )

            Button(
                onClick = onAddRemoveClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) Blue600 else Color(0xFFD1D5DB),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = enabled
            ) {
                Text(
                    text = "Add/Remove",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Show defects count or "No Defects Found"
        Text(
            text = if (defectsCount == 0) "No Defects Found" else "$defectsCount defect(s) selected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (defectsCount == 0) TextSecondary else AccentRed,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
