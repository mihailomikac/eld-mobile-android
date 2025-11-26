package com.eld.driver.ui.screens.inspection

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eld.driver.data.models.InspectionListItem
import com.eld.driver.data.models.InspectionType
import com.eld.driver.ui.theme.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Inspection List Screen - Professional DVIR inspection list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionListScreen(
    navController: NavController,
    authToken: String,
    vehicleId: Int? = null,
    viewModel: InspectionViewModel = viewModel()
) {
    val inspectionsState by viewModel.inspectionsState.collectAsState()
    var selectedFilter by remember { mutableStateOf<InspectionType?>(null) }

    // Load inspections on first composition
    LaunchedEffect(selectedFilter) {
        viewModel.loadInspections(
            token = authToken,
            vehicleId = vehicleId,
            inspectionType = selectedFilter
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "DVIR Inspections",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadInspections(
                            token = authToken,
                            vehicleId = vehicleId,
                            inspectionType = selectedFilter
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Blue600,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    navController.navigate("create_inspection")
                },
                containerColor = Blue600,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Inspection"
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text("New Inspection")
            }
        },
        containerColor = SecondaryBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Blue600,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedFilter == InspectionType.PRE_TRIP,
                        onClick = { selectedFilter = InspectionType.PRE_TRIP },
                        label = { Text("Pre-Trip") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Blue600,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedFilter == InspectionType.POST_TRIP,
                        onClick = { selectedFilter = InspectionType.POST_TRIP },
                        label = { Text("Post-Trip") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Blue600,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Content based on state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md)
            ) {
                when (val state = inspectionsState) {
                    is InspectionsUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Blue600
                        )
                    }

                    is InspectionsUiState.Error -> {
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
                                    text = state.message,
                                    color = AccentRed,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(Spacing.md))
                                Button(
                                    onClick = {
                                        viewModel.loadInspections(
                                            token = authToken,
                                            vehicleId = vehicleId,
                                            inspectionType = selectedFilter
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Blue600
                                    )
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    is InspectionsUiState.Success -> {
                        val inspections = state.data.data

                        if (inspections.isEmpty()) {
                            // No inspections found
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(Spacing.md))
                                Text(
                                    text = "No inspections found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = "Create a new DVIR inspection",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Inspection list
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                items(inspections) { inspection ->
                                    InspectionCard(
                                        inspection = inspection,
                                        onClick = {
                                            navController.navigate("inspection_detail/${inspection.id}")
                                        }
                                    )
                                }

                                // Bottom padding for FAB
                                item {
                                    Spacer(modifier = Modifier.height(80.dp))
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
fun InspectionCard(
    inspection: InspectionListItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
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
            // Status indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (inspection.passedWithoutDefects)
                            AccentGreen.copy(alpha = 0.1f)
                        else
                            AccentRed.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (inspection.passedWithoutDefects)
                        Icons.Default.CheckCircle
                    else
                        Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (inspection.passedWithoutDefects)
                        AccentGreen
                    else
                        AccentRed,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            // Inspection info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (inspection.inspectionType) {
                            InspectionType.PRE_TRIP -> "Pre-Trip"
                            InspectionType.POST_TRIP -> "Post-Trip"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.width(Spacing.sm))

                    // Verification badge
                    if (inspection.verifiedOn != null) {
                        Surface(
                            color = AccentGreen.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "Verified",
                                    tint = AccentGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verified",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentGreen
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Vehicle #${inspection.vehicleIdString ?: inspection.vehicleId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatInspectionTime(inspection.inspectionTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (inspection.signedOn != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Draw,
                            contentDescription = "Signed",
                            tint = AccentGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Signed",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentGreen
                        )
                    }
                }
            }

            // Status text
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = if (inspection.passedWithoutDefects) "PASSED" else "DEFECTS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (inspection.passedWithoutDefects)
                        AccentGreen
                    else
                        AccentRed
                )
            }
        }
    }
}

private fun formatInspectionTime(isoString: String): String {
    return try {
        val dateTime = ZonedDateTime.parse(isoString)
        dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a"))
    } catch (e: Exception) {
        isoString
    }
}
