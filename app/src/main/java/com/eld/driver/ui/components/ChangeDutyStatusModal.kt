package com.eld.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eld.driver.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Predefined notes for each duty status
 */
private fun getPredefinedNotes(status: String): List<String> {
    return when (status) {
        "OFF_DUTY" -> listOf("Rest Break", "Off Duty", "Meal", "Lunch", "Home", "Break")
        "ON_DUTY_NOT_DRIVING" -> listOf(
            "Pre-Trip Inspection", "Post-Trip Inspection", "Fuel", "Loading", "Unloading",
            "Pickup", "Delivery", "Drop and Hook", "Scale", "Weight Station",
            "DOT Inspection", "Shop", "Vehicle stationary for 5 minutes"
        )
        "SLEEPER_BERTH" -> listOf("Sleeper", "Rest Break", "Off Duty", "Meal", "Lunch", "Break")
        "PERSONAL_CONVEYANCE" -> listOf("Parking", "Meal", "Lunch", "Home")
        "YARD_MOVE" -> listOf("Parking", "Pickup", "Loading", "Fuel", "Drop and Hook", "DOT Inspection", "Delivery")
        "DRIVING" -> listOf("Vehicle speed over 5 mph")
        else -> emptyList()
    }
}

/**
 * Change Duty Status Modal - Matches iOS design exactly
 * Shows 6 duty status options, location, and notes fields
 */
@Composable
fun ChangeDutyStatusModal(
    currentStatus: String?,
    initialLocation: String? = null,
    allowYardMove: Boolean = false,
    allowPersonalConveyance: Boolean = false,
    allowManualDriveTime: Boolean = false,
    onDismiss: () -> Unit,
    onRefreshLocation: (() -> Unit)? = null,
    onConfirm: (status: String, location: String, notes: String) -> Unit
) {
    // Determine initial selected status - pick first available that's NOT the current status
    val initialSelectedStatus = remember(currentStatus) {
        val mainStatuses = listOf("OFF_DUTY", "ON_DUTY_NOT_DRIVING", "SLEEPER_BERTH")
        mainStatuses.firstOrNull { it != currentStatus } ?: "OFF_DUTY"
    }

    var selectedStatus by remember { mutableStateOf(initialSelectedStatus) }
    var location by remember { mutableStateOf(initialLocation ?: "") }
    var notes by remember { mutableStateOf("") }
    var isLoadingLocation by remember { mutableStateOf(false) }

    // Update location when initialLocation changes
    LaunchedEffect(initialLocation) {
        if (!initialLocation.isNullOrBlank() && location.isBlank()) {
            location = initialLocation
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            shape = RoundedCornerShape(CornerRadius.large),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
            ) {
                // Title
                Text(
                    text = "Change Duty Status",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                )

                // Status buttons - 2 rows of 3
                // Hide the button for the current status (can't change to same status)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Row 1: OFF, ON, SB (hide current status)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (currentStatus != "OFF_DUTY") {
                            DutyStatusButton(
                                text = "OFF",
                                status = "OFF_DUTY",
                                isSelected = selectedStatus == "OFF_DUTY",
                                onClick = { selectedStatus = "OFF_DUTY" }
                            )
                        }
                        if (currentStatus != "ON_DUTY_NOT_DRIVING") {
                            DutyStatusButton(
                                text = "ON",
                                status = "ON_DUTY_NOT_DRIVING",
                                isSelected = selectedStatus == "ON_DUTY_NOT_DRIVING",
                                onClick = { selectedStatus = "ON_DUTY_NOT_DRIVING" }
                            )
                        }
                        if (currentStatus != "SLEEPER_BERTH") {
                            DutyStatusButton(
                                text = "SB",
                                status = "SLEEPER_BERTH",
                                isSelected = selectedStatus == "SLEEPER_BERTH",
                                onClick = { selectedStatus = "SLEEPER_BERTH" }
                            )
                        }
                    }

                    // Row 2: PC (if allowed), YM (if allowed), D (if allowed)
                    // Only show this row if at least one option is allowed AND not current status
                    val showPC = allowPersonalConveyance && currentStatus != "PERSONAL_CONVEYANCE"
                    val showYM = allowYardMove && currentStatus != "YARD_MOVE"
                    val showD = allowManualDriveTime && currentStatus != "DRIVING"

                    if (showPC || showYM || showD) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (showPC) {
                                DutyStatusButton(
                                    text = "PC",
                                    status = "PERSONAL_CONVEYANCE",
                                    isSelected = selectedStatus == "PERSONAL_CONVEYANCE",
                                    onClick = { selectedStatus = "PERSONAL_CONVEYANCE" }
                                )
                            }
                            if (showYM) {
                                DutyStatusButton(
                                    text = "YM",
                                    status = "YARD_MOVE",
                                    isSelected = selectedStatus == "YARD_MOVE",
                                    onClick = { selectedStatus = "YARD_MOVE" }
                                )
                            }
                            if (showD) {
                                DutyStatusButton(
                                    text = "D",
                                    status = "DRIVING",
                                    isSelected = selectedStatus == "DRIVING",
                                    onClick = { selectedStatus = "DRIVING" }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Location field
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    placeholder = { Text("Location", color = TextSecondary) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                isLoadingLocation = true
                                onRefreshLocation?.invoke()
                                // Reset after short delay
                                kotlinx.coroutines.MainScope().launch {
                                    kotlinx.coroutines.delay(1000)
                                    isLoadingLocation = false
                                }
                            },
                            enabled = !isLoadingLocation
                        ) {
                            if (isLoadingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Blue600,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Get location",
                                    tint = Blue600
                                )
                            }
                        }
                    },
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
                    shape = RoundedCornerShape(CornerRadius.medium),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Predefined notes chips
                val predefinedNotes = getPredefinedNotes(selectedStatus)
                if (predefinedNotes.isNotEmpty()) {
                    Text(
                        text = "Quick Notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        predefinedNotes.forEach { note ->
                            val isSelected = notes == note
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        notes = if (isSelected) "" else note
                                    },
                                color = if (isSelected) Blue600 else BackgroundLight,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }

                // Notes field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Notes (or type custom)", color = TextSecondary) },
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
                    shape = RoundedCornerShape(CornerRadius.medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel button
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = Blue600,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.sm))

                    // Confirm button
                    Button(
                        onClick = { onConfirm(selectedStatus, location, notes) },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                        shape = RoundedCornerShape(CornerRadius.medium),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = "Confirm",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Duty Status Button - Circular button for status selection
 */
@Composable
private fun DutyStatusButton(
    text: String,
    status: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(if (isSelected) Blue600 else Color.White)
            .border(
                width = 2.dp,
                color = if (isSelected) Blue600 else BorderLight,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White else TextPrimary
        )
    }
}
