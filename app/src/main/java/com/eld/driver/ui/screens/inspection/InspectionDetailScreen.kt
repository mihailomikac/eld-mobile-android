package com.eld.driver.ui.screens.inspection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eld.driver.data.models.*
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.theme.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Inspection Detail Screen - Matches iOS InspectionDetailView exactly
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionDetailScreen(
    navController: NavController,
    inspectionId: Int,
    authToken: String,
    viewModel: InspectionViewModel = viewModel()
) {
    val inspectionDetailState by viewModel.inspectionDetailState.collectAsState()
    var showVerifyConfirmation by remember { mutableStateOf(false) }

    // Load inspection on first composition
    LaunchedEffect(inspectionId) {
        viewModel.loadInspectionDetail(authToken, inspectionId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SecondaryBackground)
    ) {
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
                        .padding(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "Inspection Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    // Placeholder for symmetry
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Curved white shape at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)  // Height of the curved section
                        .align(Alignment.BottomCenter)
                        .clip(CurvedWaveShape())
                        .background(SecondaryBackground)
                )
            }

            // Content
            when (val state = inspectionDetailState) {
                is InspectionDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Blue600)
                    }
                }

                is InspectionDetailUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.message,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Button(
                                onClick = { viewModel.loadInspectionDetail(authToken, inspectionId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                is InspectionDetailUiState.Success -> {
                    InspectionDetailContent(
                        inspection = state.inspection,
                        onVerifyClick = { showVerifyConfirmation = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Verification confirmation dialog
    if (showVerifyConfirmation) {
        AlertDialog(
            onDismissRequest = { showVerifyConfirmation = false },
            title = { Text("Verify Inspection", color = TextPrimary) },
            text = { Text("Are you sure you want to verify this inspection? This action cannot be undone.", color = TextPrimary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.verifyInspection(authToken, inspectionId) {
                            showVerifyConfirmation = false
                        }
                    }
                ) {
                    Text("Verify", color = Blue600)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyConfirmation = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun InspectionDetailContent(
    inspection: Inspection,
    onVerifyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = 100.dp)
    ) {
        Spacer(modifier = Modifier.height(Spacing.lg))

        // Inspection Info Card
        InspectionInfoCard(inspection)

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Vehicle Info Card
        VehicleInfoCard(inspection)

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Vehicle Defects (if any)
        inspection.vehicleDefects?.let { defects ->
            if (defects.isNotEmpty()) {
                DefectsCard(
                    title = "Vehicle Defects",
                    defects = defects.map { DefectDisplayItem(it.defect, it.comment, it.photoUrls) }
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }

        // Asset Defects (if any)
        inspection.assetDefects?.let { defects ->
            if (defects.isNotEmpty()) {
                DefectsCard(
                    title = "Asset/Trailer Defects",
                    defects = defects.map { DefectDisplayItem(it.defect, it.comment, it.photoUrls) }
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }

        // Notes (if any)
        inspection.notes?.let { notes ->
            if (notes.isNotBlank()) {
                NotesCard(notes)
                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }

        // Location (if available)
        if (inspection.latitude != null && inspection.longitude != null) {
            LocationCard(
                description = inspection.locationDescription,
                latitude = inspection.latitude,
                longitude = inspection.longitude
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
        }

        // Verification Section
        if (inspection.verifiedOn != null) {
            VerifiedStatusCard(verifiedOn = inspection.verifiedOn)
        } else {
            VerifyButton(onClick = onVerifyClick)
        }
    }
}

// Continued in next message due to length...

/**
 * Inspection Info Card - Main inspection details
 */
@Composable
fun InspectionInfoCard(inspection: Inspection) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            // Type icon and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (inspection.inspectionType == InspectionType.PRE_TRIP)
                                Blue600.copy(alpha = 0.15f)
                            else
                                Color(0xFF7C3AED).copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (inspection.inspectionType == InspectionType.PRE_TRIP)
                            Icons.Default.ArrowCircleUp
                        else
                            Icons.Default.ArrowCircleDown,
                        contentDescription = null,
                        tint = if (inspection.inspectionType == InspectionType.PRE_TRIP)
                            Blue600
                        else
                            Color(0xFF7C3AED),
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (inspection.inspectionType == InspectionType.PRE_TRIP)
                            "PRE-TRIP INSPECTION"
                        else
                            "POST-TRIP INSPECTION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Text(
                        text = "Inspection #${inspection.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )

                    // Status badge
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inspection.passedWithoutDefects)
                                        AccentGreen
                                    else
                                        AccentOrange
                                )
                        )
                        Text(
                            text = if (inspection.passedWithoutDefects) "PASSED" else "DEFECTS FOUND",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (inspection.passedWithoutDefects)
                                AccentGreen
                            else
                                AccentOrange
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = Spacing.lg))

            // Inspection Time
            InfoRow(
                icon = Icons.Default.AccessTime,
                label = "Inspection Time",
                value = formatDateTime(inspection.inspectionTime)
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // Odometer Reading
            inspection.odometerReading?.let { odometer ->
                InfoRow(
                    icon = Icons.Default.Speed,
                    label = "Odometer Reading",
                    value = String.format("%.1f miles", odometer)
                )
            }
        }
    }
}

/**
 * Vehicle Info Card
 */
@Composable
fun VehicleInfoCard(inspection: Inspection) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            InfoRow(
                icon = Icons.Default.ConfirmationNumber,
                label = "Vehicle ID",
                value = inspection.vehicleIdString ?: inspection.vehicleId.toString()
            )

            inspection.vin?.let { vin ->
                Spacer(modifier = Modifier.height(Spacing.md))
                InfoRow(
                    icon = Icons.Default.QrCode,
                    label = "VIN",
                    value = vin
                )
            }
        }
    }
}

data class DefectDisplayItem(
    val defect: String,
    val comment: String?,
    val photoUrls: List<String>?
)

/**
 * Defects Card - For vehicle or asset defects
 */
@Composable
fun DefectsCard(
    title: String,
    defects: List<DefectDisplayItem>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Warning icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AccentOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                // Count badge
                Surface(
                    color = AccentOrange,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = defects.size.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (defects.size == 1) "Issue" else "Issues",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Defects list
            defects.forEachIndexed { index, defect ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Orange bullet
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentOrange)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Defect name
                        Text(
                            text = defect.defect,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )

                        // Comment (if any)
                        defect.comment?.let { comment ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = TextSecondary
                                )
                                Text(
                                    text = comment,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Photos (if any)
                        defect.photoUrls?.let { photos ->
                            if (photos.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = Blue600
                                    )
                                    Text(
                                        text = "${photos.size} photo${if (photos.size == 1) "" else "s"} attached",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = Blue600
                                    )
                                }
                            }
                        }
                    }
                }

                if (index < defects.size - 1) {
                    Divider(modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
                }
            }
        }
    }
}

/**
 * Notes Card
 */
@Composable
fun NotesCard(notes: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Blue600,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Location Card
 */
@Composable
fun LocationCard(
    description: String?,
    latitude: Double,
    longitude: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            description?.let { desc ->
                InfoRow(
                    icon = Icons.Default.LocationOn,
                    label = "Location",
                    value = desc
                )
                Spacer(modifier = Modifier.height(Spacing.md))
            }

            InfoRow(
                icon = Icons.Default.Public,
                label = "Coordinates",
                value = String.format("%.6f, %.6f", latitude, longitude)
            )
        }
    }
}

/**
 * Verified Status Card
 */
@Composable
fun VerifiedStatusCard(verifiedOn: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFD1FAE5),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkmark icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AccentGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "VERIFIED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = AccentGreen
                )

                Text(
                    text = "Inspection Verified",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = TextSecondary
                    )
                    Text(
                        text = formatDateTime(verifiedOn),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Verify Button
 */
@Composable
fun VerifyButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = AccentGreen.copy(alpha = 0.3f)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            AccentGreen,
                            Color(0xFF059669)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Verify Inspection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Info Row - Icon, label, and value
 */
@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Blue600,
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                color = TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextPrimary
            )
        }
    }
}

/**
 * Format ISO8601 date to readable format
 */
private fun formatDateTime(isoString: String): String {
    return try {
        val dateTime = ZonedDateTime.parse(isoString)
        dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a"))
    } catch (e: Exception) {
        isoString
    }
}
