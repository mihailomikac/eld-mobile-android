package com.eld.driver.ui.screens.vehicle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.theme.*

/**
 * Vehicle Confirmation Screen - Shows selected vehicle details for confirmation
 */
@Composable
fun VehicleConfirmationScreen(
    navController: NavController,
    authToken: String,
    viewModel: VehicleViewModel = viewModel()
) {
    val filteredVehicles by viewModel.filteredVehicles.collectAsState()
    val currentVehicleId by viewModel.currentVehicleId.collectAsState()

    // Get the current vehicle
    val currentVehicle = filteredVehicles.firstOrNull { it.id == currentVehicleId }

    // Back handler - go back to vehicle selection
    BackHandler(enabled = true) {
        navController.navigate("vehicle_selection") {
            popUpTo("vehicle_confirmation") { inclusive = true }
        }
    }

    // Redirect to vehicle_selection if no vehicle is selected
    LaunchedEffect(currentVehicleId) {
        if (currentVehicleId == null) {
            android.util.Log.e("VehicleConfirmationScreen", "❌ No vehicle selected - redirecting to vehicle_selection")
            navController.navigate("vehicle_selection") {
                popUpTo("vehicle_confirmation") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SecondaryBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with gradient and curved wave
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
                        text = "Change Vehicle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Placeholder for symmetry
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Curved background shape at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .clip(CurvedWaveShape())
                        .background(SecondaryBackground)
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.lg)
            ) {
                Spacer(modifier = Modifier.height(Spacing.xl))

                // "ASSIGNED TRUCK" header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ASSIGNED TRUCK",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Vehicle details
                if (currentVehicle != null) {
                    // ID
                    VehicleDetailRow(
                        icon = Icons.Default.LocalShipping,
                        label = "ID",
                        value = currentVehicle.vehicleNumber
                    )

                    Spacer(modifier = Modifier.height(Spacing.xl))

                    // VIN
                    if (currentVehicle.vin != null) {
                        VehicleDetailRow(
                            icon = Icons.Default.CreditCard,
                            label = "VIN",
                            value = currentVehicle.vin!!
                        )
                        Spacer(modifier = Modifier.height(Spacing.xl))
                    }

                    // Year
                    if (currentVehicle.year != null) {
                        VehicleDetailRow(
                            icon = Icons.Default.CalendarToday,
                            label = "Year",
                            value = currentVehicle.year.toString()
                        )
                        Spacer(modifier = Modifier.height(Spacing.xl))
                    }

                    // Manufacturer
                    if (currentVehicle.make != null) {
                        VehicleDetailRow(
                            icon = Icons.Default.Factory,
                            label = "Manufacturer",
                            value = currentVehicle.make!!
                        )
                        Spacer(modifier = Modifier.height(Spacing.xl))
                    }

                    // Model
                    if (currentVehicle.model != null) {
                        VehicleDetailRow(
                            icon = Icons.Default.DirectionsCar,
                            label = "Model",
                            value = currentVehicle.model!!
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Decline button
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Blue600
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 2.dp
                        )
                    ) {
                        Text(
                            text = "Decline",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    }

                    // Accept button
                    Button(
                        onClick = {
                            // Send LOGIN tick event and navigate to dashboard
                            viewModel.confirmVehicleSelection(authToken) {
                                navController.navigate("dashboard") {
                                    popUpTo(0) { inclusive = true }  // Clear entire back stack
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue600
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp
                        )
                    ) {
                        Text(
                            text = "Accept",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(32.dp)
        )

        // Label and value
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 14.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                fontSize = 18.sp
            )
        }
    }
}
