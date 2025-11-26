package com.eld.driver.ui.screens.vehicle

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eld.driver.ui.theme.*

/**
 * Vehicle Confirmation Screen - Shows selected vehicle with confirmation
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

    // Auto navigate to dashboard after 2 seconds
    LaunchedEffect(currentVehicleId) {
        if (currentVehicleId != null) {
            kotlinx.coroutines.delay(2000)
            navController.navigate("dashboard") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SecondaryBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Success icon with gradient background
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                AccentGreen.copy(alpha = 0.2f),
                                AccentGreen.copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    modifier = Modifier.size(80.dp),
                    tint = AccentGreen
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xxl))

            // Success message
            Text(
                text = "Vehicle Confirmed",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = "You're all set to start driving",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.xxl))

            // Vehicle info card
            if (currentVehicle != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(CornerRadius.large)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Vehicle icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Blue600.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Blue600,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Vehicle number
                        Text(
                            text = currentVehicle.vehicleNumber,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // Vehicle display name
                        Text(
                            text = currentVehicle.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        if (currentVehicle.vin != null) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Text(
                                text = "VIN: ${currentVehicle.vin}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxl))

            // Loading indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Blue600,
                    strokeWidth = 2.dp
                )

                Spacer(modifier = Modifier.width(Spacing.md))

                Text(
                    text = "Loading dashboard...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            // Manual navigation button
            TextButton(
                onClick = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            ) {
                Text(
                    text = "Continue to Dashboard",
                    color = Blue600,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
