package com.eld.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
 * Change Duty Status Modal - Matches iOS design exactly
 * Shows 6 duty status options, location, and notes fields
 */
@Composable
fun ChangeDutyStatusModal(
    currentStatus: String?,
    initialLocation: String? = null,
    onDismiss: () -> Unit,
    onRefreshLocation: (() -> Unit)? = null,
    onConfirm: (status: String, location: String, notes: String) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(currentStatus ?: "OFF_DUTY") }
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Row 1: OFF, ON, SB
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DutyStatusButton(
                            text = "OFF",
                            status = "OFF_DUTY",
                            isSelected = selectedStatus == "OFF_DUTY",
                            onClick = { selectedStatus = "OFF_DUTY" }
                        )
                        DutyStatusButton(
                            text = "ON",
                            status = "ON_DUTY_NOT_DRIVING",
                            isSelected = selectedStatus == "ON_DUTY_NOT_DRIVING",
                            onClick = { selectedStatus = "ON_DUTY_NOT_DRIVING" }
                        )
                        DutyStatusButton(
                            text = "SB",
                            status = "SLEEPER_BERTH",
                            isSelected = selectedStatus == "SLEEPER_BERTH",
                            onClick = { selectedStatus = "SLEEPER_BERTH" }
                        )
                    }

                    // Row 2: PC, YM, D
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DutyStatusButton(
                            text = "PC",
                            status = "PERSONAL_CONVEYANCE",
                            isSelected = selectedStatus == "PERSONAL_CONVEYANCE",
                            onClick = { selectedStatus = "PERSONAL_CONVEYANCE" }
                        )
                        DutyStatusButton(
                            text = "YM",
                            status = "YARD_MOVE",
                            isSelected = selectedStatus == "YARD_MOVE",
                            onClick = { selectedStatus = "YARD_MOVE" }
                        )
                        DutyStatusButton(
                            text = "D",
                            status = "DRIVING",
                            isSelected = selectedStatus == "DRIVING",
                            onClick = { selectedStatus = "DRIVING" }
                        )
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

                // Notes field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Notes", color = TextSecondary) },
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
                        .height(100.dp)
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
