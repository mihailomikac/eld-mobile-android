package com.eld.driver.ui.screens.vehicle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eld.driver.data.models.Vehicle
import com.eld.driver.ui.theme.*

/**
 * Vehicle Selection Screen - Search and select vehicle
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSelectionScreen(
    navController: NavController,
    authToken: String,
    viewModel: VehicleViewModel = viewModel(),
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredVehicles by viewModel.filteredVehicles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentVehicleId by viewModel.currentVehicleId.collectAsState()
    val isLoading = uiState is VehicleUiState.Loading

    // BLOCK back navigation - user MUST select a vehicle to proceed
    // This prevents the bug where pressing back would navigate to dashboard without a vehicle
    BackHandler(enabled = true) {
        // Do nothing - user must select a vehicle
        android.util.Log.d("VehicleSelectionScreen", "Back pressed - blocked (must select vehicle)")
    }

    // Load vehicles on first composition
    LaunchedEffect(Unit) {
        viewModel.loadVehicles(authToken)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    // Back to Login button
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Login",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Text(
                        text = "Select Vehicle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Refresh button
                    IconButton(
                        onClick = { viewModel.loadVehicles(authToken) },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh vehicles",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Blue600,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = SecondaryBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(CornerRadius.medium)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchVehicles(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.sm),
                    placeholder = { Text("Search by vehicle #, VIN, or name") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchVehicles("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Blue600,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedLabelColor = Blue600,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = Blue600,
                        focusedPlaceholderColor = TextSecondary,
                        unfocusedPlaceholderColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(CornerRadius.medium)
                )
            }

            // Content based on state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md)
            ) {
                when (uiState) {
                    is VehicleUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Blue600
                        )
                    }

                    is VehicleUiState.Error -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(top = Spacing.md),
                            colors = CardDefaults.cardColors(
                                containerColor = AccentRed.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(CornerRadius.medium)
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = (uiState as VehicleUiState.Error).message,
                                    color = AccentRed,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(Spacing.md))
                                Button(
                                    onClick = { viewModel.loadVehicles(authToken) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Blue600
                                    )
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    is VehicleUiState.Success -> {
                        if (filteredVehicles.isEmpty()) {
                            // No vehicles found
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(Spacing.md))
                                Text(
                                    text = if (searchQuery.isEmpty())
                                        "No vehicles available"
                                    else
                                        "No vehicles match your search",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Vehicle list
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                items(filteredVehicles) { vehicle ->
                                    VehicleCard(
                                        vehicle = vehicle,
                                        isCurrentVehicle = vehicle.id == currentVehicleId,
                                        onClick = {
                                            viewModel.selectVehicle(vehicle.id) {
                                                navController.navigate("vehicle_confirmation") {
                                                    popUpTo("vehicle_selection") { inclusive = true }
                                                }
                                            }
                                        }
                                    )
                                }

                                // Bottom padding
                                item {
                                    Spacer(modifier = Modifier.height(Spacing.md))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleCard(
    vehicle: Vehicle,
    isCurrentVehicle: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentVehicle)
                Blue600.copy(alpha = 0.1f)
            else
                Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vehicle icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrentVehicle) Blue600 else Blue600.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = if (isCurrentVehicle) Color.White else Blue600,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            // Vehicle info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = vehicle.vehicleNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = vehicle.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                if (vehicle.vin != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "VIN: ${vehicle.vin}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Current vehicle indicator
            if (isCurrentVehicle) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Current vehicle",
                    tint = AccentGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
