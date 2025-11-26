package com.eld.driver.ui.screens.inspection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.eld.driver.data.models.*
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.screens.inspection.InspectionViewModel
import com.eld.driver.ui.screens.vehicle.VehicleViewModel
import com.eld.driver.ui.theme.*

/**
 * Create Inspection Screen - Full form for creating DVIR inspection
 * Matches iOS CreateInspectionView exactly
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInspectionScreen(
    navController: NavController,
    authToken: String,
    vehicleViewModel: VehicleViewModel,
    inspectionViewModel: InspectionViewModel = viewModel()
) {
    var inspectionType by remember { mutableStateOf(InspectionType.PRE_TRIP) }
    var notes by remember { mutableStateOf("") }
    var passedWithoutDefects by remember { mutableStateOf(true) }
    var selectedVehicleDefects by remember { mutableStateOf(setOf<String>()) }
    var selectedAssetDefects by remember { mutableStateOf(setOf<String>()) }
    var showingSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Get current vehicle from VehicleViewModel
    val currentVehicleId by vehicleViewModel.currentVehicleId.collectAsState()
    val vehicles by vehicleViewModel.filteredVehicles.collectAsState()
    val currentVehicle = vehicles.find { it.id == currentVehicleId }

    // Load defect options when vehicle is selected
    LaunchedEffect(currentVehicleId) {
        currentVehicleId?.let { vehicleId ->
            inspectionViewModel.loadVehicleDefects(authToken, vehicleId)
            // Note: Asset defects would need an assetId - for now just vehicle defects
            // inspectionViewModel.loadAssetDefects(authToken, assetId)
        }
    }

    val vehicleDefects by inspectionViewModel.vehicleDefectsState.collectAsState()
    val assetDefects by inspectionViewModel.assetDefectsState.collectAsState()
    val createInspectionState by inspectionViewModel.createInspectionState.collectAsState()

    // Handle errors from API
    LaunchedEffect(createInspectionState) {
        when (val state = createInspectionState) {
            is CreateInspectionUiState.Error -> {
                errorMessage = state.message
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        text = "Create Inspection",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    // Placeholder for symmetry
                    Spacer(modifier = Modifier.size(48.dp))
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

            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgSecondary)
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Inspection Type Selector
                item {
                    InspectionTypeSelector(
                        selectedType = inspectionType,
                        onTypeSelected = { inspectionType = it }
                    )
                }

                // Vehicle Info Card
                item {
                    if (currentVehicle != null) {
                        VehicleInfoCard(
                            vehicleNumber = currentVehicle.vehicleNumber,
                            vin = currentVehicle.vin ?: "N/A"
                        )
                    } else {
                        // Show message if no vehicle selected
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(CornerRadius.medium)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Vehicle Selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRed
                                )
                                Text(
                                    text = "Please select a vehicle first",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // Pass/Fail Selector
                item {
                    PassFailSelector(
                        passedWithoutDefects = passedWithoutDefects,
                        onSelectionChanged = { passedWithoutDefects = it }
                    )
                }

                // Defects Section (only if failed)
                if (!passedWithoutDefects) {
                    item {
                        DefectsSection(
                            vehicleDefects = vehicleDefects,
                            assetDefects = assetDefects,
                            selectedVehicleDefects = selectedVehicleDefects,
                            selectedAssetDefects = selectedAssetDefects,
                            onVehicleDefectToggle = { defect ->
                                selectedVehicleDefects = if (selectedVehicleDefects.contains(defect)) {
                                    selectedVehicleDefects - defect
                                } else {
                                    selectedVehicleDefects + defect
                                }
                            },
                            onAssetDefectToggle = { defect ->
                                selectedAssetDefects = if (selectedAssetDefects.contains(defect)) {
                                    selectedAssetDefects - defect
                                } else {
                                    selectedAssetDefects + defect
                                }
                            }
                        )
                    }
                }

                // Notes Section
                item {
                    NotesSection(
                        notes = notes,
                        onNotesChanged = { notes = it }
                    )
                }

                // Submit Button
                item {
                    SubmitButton(
                        isLoading = createInspectionState is CreateInspectionUiState.Loading,
                        enabled = currentVehicleId != null,
                        onClick = {
                            val vehicleId = currentVehicleId ?: run {
                                errorMessage = "Please select a vehicle first"
                                return@SubmitButton
                            }

                            // Build defect lists only if there are defects
                            val vehicleDefectsList = if (!passedWithoutDefects && selectedVehicleDefects.isNotEmpty()) {
                                selectedVehicleDefects.map { defect ->
                                    VehicleDefectItem(
                                        defect = defect,
                                        comment = null,
                                        photoUrls = null
                                    )
                                }
                            } else null

                            val assetDefectsList = if (!passedWithoutDefects && selectedAssetDefects.isNotEmpty()) {
                                selectedAssetDefects.map { defect ->
                                    AssetDefectItem(
                                        defect = defect,
                                        comment = null,
                                        photoUrls = null
                                    )
                                }
                            } else null

                            // Create inspection request
                            val request = InspectionCreateRequest(
                                vehicleId = vehicleId,
                                inspectionType = inspectionType,
                                inspectionTime = null, // Server will use current time
                                vehicleDefects = vehicleDefectsList,
                                assetDefects = assetDefectsList,
                                notes = if (notes.isNotBlank()) notes else null,
                                odometerReading = null,
                                latitude = null,
                                longitude = null,
                                locationDescription = null
                            )

                            // Submit inspection
                            inspectionViewModel.createInspection(authToken, request) { inspection ->
                                showingSuccess = true
                            }
                        }
                    )
                }

                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Success Dialog
    if (showingSuccess) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Inspection Created") },
            text = { Text("Your inspection has been submitted successfully.") },
            confirmButton = {
                Button(
                    onClick = {
                        showingSuccess = false
                        navController.popBackStack()
                    }
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
                Button(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Inspection Type Selector - Pre-Trip or Post-Trip
 */
@Composable
private fun InspectionTypeSelector(
    selectedType: InspectionType,
    onTypeSelected: (InspectionType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                text = "Inspection Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                InspectionTypeButton(
                    type = InspectionType.PRE_TRIP,
                    isSelected = selectedType == InspectionType.PRE_TRIP,
                    onClick = { onTypeSelected(InspectionType.PRE_TRIP) },
                    modifier = Modifier.weight(1f)
                )

                InspectionTypeButton(
                    type = InspectionType.POST_TRIP,
                    isSelected = selectedType == InspectionType.POST_TRIP,
                    onClick = { onTypeSelected(InspectionType.POST_TRIP) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Inspection Type Button
 */
@Composable
private fun InspectionTypeButton(
    type: InspectionType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (type == InspectionType.PRE_TRIP) Blue600 else Color(0xFF7C3AED)
    val icon = if (type == InspectionType.PRE_TRIP)
        Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
    val label = if (type == InspectionType.PRE_TRIP) "PRE-TRIP" else "POST-TRIP"

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius.medium))
            .border(
                width = 2.dp,
                color = if (isSelected) color else BorderLight,
                shape = RoundedCornerShape(CornerRadius.medium)
            )
            .background(if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isSelected) color else color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else color,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) color else TextSecondary
        )
    }
}

/**
 * Vehicle Info Card
 */
@Composable
private fun VehicleInfoCard(vehicleNumber: String, vin: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                text = "Vehicle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = "Vehicle",
                    tint = Blue600,
                    modifier = Modifier.size(24.dp)
                )

                Column {
                    Text(
                        text = vehicleNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "VIN: $vin",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Pass/Fail Selector
 */
@Composable
private fun PassFailSelector(
    passedWithoutDefects: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                text = "Inspection Result",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Passed button
                Button(
                    onClick = { onSelectionChanged(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (passedWithoutDefects) AccentGreen else AccentGreen.copy(alpha = 0.1f),
                        contentColor = if (passedWithoutDefects) Color.White else AccentGreen
                    ),
                    shape = RoundedCornerShape(CornerRadius.medium)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (passedWithoutDefects)
                                Icons.Default.CheckCircle else Icons.Default.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Passed - No Defects",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Failed button
                Button(
                    onClick = { onSelectionChanged(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!passedWithoutDefects) AccentOrange else AccentOrange.copy(alpha = 0.1f),
                        contentColor = if (!passedWithoutDefects) Color.White else AccentOrange
                    ),
                    shape = RoundedCornerShape(CornerRadius.medium)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (!passedWithoutDefects)
                                Icons.Default.CheckCircle else Icons.Default.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Defects Found",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Defects Section
 */
@Composable
private fun DefectsSection(
    vehicleDefects: List<String>,
    assetDefects: List<String>,
    selectedVehicleDefects: Set<String>,
    selectedAssetDefects: Set<String>,
    onVehicleDefectToggle: (String) -> Unit,
    onAssetDefectToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                text = "Select Defects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            // Vehicle defects
            if (vehicleDefects.isNotEmpty()) {
                Text(
                    text = "Vehicle Defects",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = Spacing.xs)
                )

                vehicleDefects.forEach { defect ->
                    DefectCheckbox(
                        defect = defect,
                        isSelected = selectedVehicleDefects.contains(defect),
                        onToggle = { onVehicleDefectToggle(defect) }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))
            }

            // Asset defects
            if (assetDefects.isNotEmpty()) {
                Text(
                    text = "Trailer/Asset Defects",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = Spacing.xs)
                )

                assetDefects.forEach { defect ->
                    DefectCheckbox(
                        defect = defect,
                        isSelected = selectedAssetDefects.contains(defect),
                        onToggle = { onAssetDefectToggle(defect) }
                    )
                }
            }
        }
    }
}

/**
 * Defect Checkbox
 */
@Composable
private fun DefectCheckbox(
    defect: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (isSelected) Blue600 else BorderMedium,
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = defect.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

/**
 * Notes Section
 */
@Composable
private fun NotesSection(notes: String, onNotesChanged: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                text = "Notes (Optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    unfocusedContainerColor = BackgroundLight,
                    focusedContainerColor = BackgroundLight,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Blue600,
                    cursorColor = Blue600,
                    focusedPlaceholderColor = TextSecondary,
                    unfocusedPlaceholderColor = TextSecondary
                ),
                shape = RoundedCornerShape(CornerRadius.small)
            )
        }
    }
}

/**
 * Submit Button
 */
@Composable
private fun SubmitButton(
    isLoading: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
        shape = RoundedCornerShape(CornerRadius.medium),
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = "Submit Inspection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
