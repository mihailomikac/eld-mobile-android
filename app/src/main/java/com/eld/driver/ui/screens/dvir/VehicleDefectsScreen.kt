package com.eld.driver.ui.screens.dvir

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eld.driver.data.models.VehicleDefectItem
import com.eld.driver.data.models.AssetDefectItem
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.theme.*

/**
 * Vehicle Defects Screen - Select defects for vehicle inspection
 * Matches the iOS design with expandable defect items
 * Now uses DVIRViewModel to load defects from API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDefectsScreen(
    navController: NavController,
    authToken: String = "",
    vehicleId: Int = 0,
    isTrailer: Boolean = false,
    dvirViewModel: DVIRViewModel = viewModel()
) {
    // Load defects from API
    LaunchedEffect(vehicleId) {
        if (vehicleId > 0 && authToken.isNotBlank()) {
            if (isTrailer) {
                dvirViewModel.loadAssetDefects(authToken, vehicleId)
            } else {
                dvirViewModel.loadVehicleDefects(authToken, vehicleId)
            }
        }
    }

    val vehicleDefectsState by dvirViewModel.vehicleDefectsForm.collectAsState()
    val assetDefectsState by dvirViewModel.assetDefectsForm.collectAsState()
    val selectedVehicleDefects by dvirViewModel.selectedVehicleDefects.collectAsState()
    val selectedAssetDefects by dvirViewModel.selectedAssetDefects.collectAsState()

    // Get defect categories from API response or use fallback
    val defectCategories = remember(vehicleDefectsState, assetDefectsState) {
        if (isTrailer) {
            when (val state = assetDefectsState) {
                is AssetDefectsFormState.Success -> {
                    // Try defectCategories first, then allowedAssetDefects, then default
                    state.data.defectCategories?.map { category ->
                        DefectCategory(
                            name = category.name ?: "",
                            defects = category.defects?.map { defect ->
                                DefectOption(
                                    id = defect.name ?: defect.id?.toString() ?: "",
                                    name = defect.name ?: ""
                                )
                            } ?: emptyList()
                        )
                    } ?: state.data.allowedAssetDefects?.let { defects ->
                        listOf(DefectCategory(
                            name = "TRAILER DEFECTS",
                            defects = defects.map { DefectOption(id = it, name = it) }
                        ))
                    } ?: getDefaultTrailerDefectCategories()
                }
                else -> getDefaultTrailerDefectCategories()
            }
        } else {
            when (val state = vehicleDefectsState) {
                is VehicleDefectsFormState.Success -> {
                    // Try defectCategories first, then allowedVehicleDefects, then default
                    state.data.defectCategories?.map { category ->
                        DefectCategory(
                            name = category.name ?: "",
                            defects = category.defects?.map { defect ->
                                DefectOption(
                                    id = defect.name ?: defect.id?.toString() ?: "",
                                    name = defect.name ?: ""
                                )
                            } ?: emptyList()
                        )
                    } ?: state.data.allowedVehicleDefects?.let { defects ->
                        listOf(DefectCategory(
                            name = "VEHICLE DEFECTS",
                            defects = defects.map { DefectOption(id = it, name = it) }
                        ))
                    } ?: getDefaultVehicleDefectCategories()
                }
                else -> getDefaultVehicleDefectCategories()
            }
        }
    }

    // Local state for UI selection (map defect id to selection data)
    var selectedDefects by remember { mutableStateOf<Map<String, SelectedDefect>>(emptyMap()) }
    var expandedDefects by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Initialize selected defects from ViewModel
    LaunchedEffect(selectedVehicleDefects, selectedAssetDefects) {
        if (!isTrailer && selectedVehicleDefects.isNotEmpty()) {
            selectedDefects = selectedVehicleDefects.associate { defect ->
                defect.defect to SelectedDefect(
                    id = defect.defect,
                    name = defect.defect,
                    comment = defect.comment,
                    photoUri = defect.photoUrls?.firstOrNull()
                )
            }
        } else if (isTrailer && selectedAssetDefects.isNotEmpty()) {
            selectedDefects = selectedAssetDefects.associate { defect ->
                defect.defect to SelectedDefect(
                    id = defect.defect,
                    name = defect.defect,
                    comment = defect.comment,
                    photoUri = defect.photoUrls?.firstOrNull()
                )
            }
        }
    }

    val isLoading = when {
        isTrailer -> assetDefectsState is AssetDefectsFormState.Loading
        else -> vehicleDefectsState is VehicleDefectsFormState.Loading
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
                        text = if (isTrailer) "Trailer Defects" else "Vehicle Defects",
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Blue600)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        defectCategories.forEach { category ->
                            // Category Header
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFE5E7EB))
                                        .padding(vertical = 12.dp, horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            // Defect Items
                            items(category.defects) { defect ->
                                val isSelected = selectedDefects.containsKey(defect.id)
                                val isExpanded = expandedDefects.contains(defect.id)
                                val selectedDefect = selectedDefects[defect.id]

                                Column {
                                    // Defect Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Toggle expansion
                                                expandedDefects = if (isExpanded) {
                                                    expandedDefects - defect.id
                                                } else {
                                                    expandedDefects + defect.id
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Checkbox
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedDefects = if (checked) {
                                                    selectedDefects + (defect.id to SelectedDefect(
                                                        id = defect.id,
                                                        name = defect.name,
                                                        comment = null,
                                                        photoUri = null
                                                    ))
                                                } else {
                                                    selectedDefects - defect.id
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Blue600,
                                                uncheckedColor = Color(0xFF9CA3AF)
                                            )
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Defect name
                                        Text(
                                            text = defect.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (isSelected) Blue600 else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Expand/Collapse icon
                                        Icon(
                                            imageVector = if (isExpanded)
                                                Icons.Default.KeyboardArrowUp
                                            else
                                                Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = TextSecondary
                                        )
                                    }

                                    // Expanded Content (Comment + Photo)
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .padding(bottom = 16.dp)
                                        ) {
                                            // Comment field
                                            OutlinedTextField(
                                                value = selectedDefect?.comment ?: "",
                                                onValueChange = { comment ->
                                                    if (isSelected) {
                                                        selectedDefects = selectedDefects + (defect.id to selectedDefect!!.copy(comment = comment))
                                                    }
                                                },
                                                placeholder = { Text("Comment", color = TextSecondary) },
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
                                                enabled = isSelected
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // Camera button
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        // TODO: Open camera
                                                    },
                                                    enabled = isSelected
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CameraAlt,
                                                        contentDescription = "Take Photo",
                                                        tint = if (isSelected) TextSecondary else Color(0xFFD1D5DB),
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Divider
                                    Divider(color = Color(0xFFE5E7EB), thickness = 1.dp)
                                }
                            }
                        }

                        // Bottom spacing
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    // Save selected defects to ViewModel
                    if (isTrailer) {
                        val assetDefects = selectedDefects.values.map { defect ->
                            AssetDefectItem(
                                defect = defect.name,
                                comment = defect.comment,
                                photoUrls = defect.photoUri?.let { listOf(it) }
                            )
                        }
                        dvirViewModel.updateSelectedAssetDefects(assetDefects)
                    } else {
                        val vehicleDefects = selectedDefects.values.map { defect ->
                            VehicleDefectItem(
                                defect = defect.name,
                                comment = defect.comment,
                                photoUrls = defect.photoUri?.let { listOf(it) }
                            )
                        }
                        dvirViewModel.updateSelectedVehicleDefects(vehicleDefects)
                    }
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                shape = RoundedCornerShape(CornerRadius.medium)
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Get default vehicle defect categories when API doesn't provide them
 */
private fun getDefaultVehicleDefectCategories(): List<DefectCategory> {
    return listOf(
        DefectCategory(
            name = "FRONT",
            defects = listOf(
                DefectOption("1", "Bumper"),
                DefectOption("2", "Headlights"),
                DefectOption("3", "Flashers"),
                DefectOption("4", "Fog Lights"),
                DefectOption("5", "Marker Lights"),
                DefectOption("6", "Engine Compartment"),
                DefectOption("7", "Fluids"),
                DefectOption("8", "Belts"),
                DefectOption("9", "Hoses")
            )
        ),
        DefectCategory(
            name = "DRIVER SIDE",
            defects = listOf(
                DefectOption("10", "Fuel Tank"),
                DefectOption("11", "Mirrors"),
                DefectOption("12", "Doors"),
                DefectOption("13", "Tires"),
                DefectOption("14", "Wheels"),
                DefectOption("15", "Mud Flaps")
            )
        ),
        DefectCategory(
            name = "REAR",
            defects = listOf(
                DefectOption("16", "Tail Lights"),
                DefectOption("17", "Brake Lights"),
                DefectOption("18", "Turn Signals"),
                DefectOption("19", "Reflectors"),
                DefectOption("20", "License Plate"),
                DefectOption("21", "Rear Bumper")
            )
        ),
        DefectCategory(
            name = "PASSENGER SIDE",
            defects = listOf(
                DefectOption("22", "Fuel Tank"),
                DefectOption("23", "Mirrors"),
                DefectOption("24", "Doors"),
                DefectOption("25", "Tires"),
                DefectOption("26", "Wheels"),
                DefectOption("27", "Mud Flaps")
            )
        ),
        DefectCategory(
            name = "CAB",
            defects = listOf(
                DefectOption("28", "Horn"),
                DefectOption("29", "Windshield"),
                DefectOption("30", "Wipers"),
                DefectOption("31", "Gauges"),
                DefectOption("32", "Heater/Defroster"),
                DefectOption("33", "Seat Belts"),
                DefectOption("34", "Emergency Equipment"),
                DefectOption("35", "Fire Extinguisher"),
                DefectOption("36", "Triangles")
            )
        )
    )
}

/**
 * Get default trailer defect categories when API doesn't provide them
 */
private fun getDefaultTrailerDefectCategories(): List<DefectCategory> {
    return listOf(
        DefectCategory(
            name = "FRONT",
            defects = listOf(
                DefectOption("101", "Coupling Devices"),
                DefectOption("102", "Landing Gear"),
                DefectOption("103", "Clearance Lights")
            )
        ),
        DefectCategory(
            name = "SIDES",
            defects = listOf(
                DefectOption("104", "Side Markers"),
                DefectOption("105", "Tires"),
                DefectOption("106", "Wheels"),
                DefectOption("107", "Mud Flaps"),
                DefectOption("108", "Doors/Latches")
            )
        ),
        DefectCategory(
            name = "REAR",
            defects = listOf(
                DefectOption("109", "Tail Lights"),
                DefectOption("110", "Brake Lights"),
                DefectOption("111", "Turn Signals"),
                DefectOption("112", "Reflectors"),
                DefectOption("113", "License Plate Light"),
                DefectOption("114", "Rear Doors")
            )
        ),
        DefectCategory(
            name = "INTERIOR",
            defects = listOf(
                DefectOption("115", "Floor"),
                DefectOption("116", "Walls"),
                DefectOption("117", "Roof"),
                DefectOption("118", "Tie-Downs")
            )
        )
    )
}

/**
 * Data classes for defects
 */
data class DefectCategory(
    val name: String,
    val defects: List<DefectOption>
)

data class DefectOption(
    val id: String,
    val name: String
)

data class SelectedDefect(
    val id: String,
    val name: String,
    val comment: String?,
    val photoUri: String?
)
