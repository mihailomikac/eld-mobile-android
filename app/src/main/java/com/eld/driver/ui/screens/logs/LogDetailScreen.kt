package com.eld.driver.ui.screens.logs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eld.driver.data.models.DutyStatusEventDto
import com.eld.driver.data.models.DutyStatusSummary
import com.eld.driver.data.models.DutyStatusType
import com.eld.driver.data.models.DriverEventsData
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Format location for display
 * Priority: location string > formatted coordinates > "Unknown location"
 */
private fun formatLocationDisplay(location: String?, latitude: Double?, longitude: Double?): String {
    // If location string exists, use it
    if (!location.isNullOrBlank()) {
        return location
    }

    // If coordinates exist, format them
    if (latitude != null && longitude != null) {
        // Format as "XX.XXXXmi ENE of City" style or just coordinates
        return "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
    }

    return "Unknown location"
}

/**
 * Log Detail Screen - Shows events for a specific date with ELD graph
 * Design matching the provided screenshots
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailScreen(
    navController: NavController,
    date: String,
    authToken: String,
    logsViewModel: LogsViewModel = viewModel()
) {
    val logDetailState by logsViewModel.logDetailState.collectAsState()
    val certifyingDate by logsViewModel.certifyingDate.collectAsState()

    // Parse and format the date for display
    val displayDate = remember(date) {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outputFormat = SimpleDateFormat("EEEE, MMM d", Locale.US)
            val parsedDate = inputFormat.parse(date)
            parsedDate?.let { outputFormat.format(it) } ?: date
        } catch (e: Exception) {
            date
        }
    }

    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // Load events for the date
    LaunchedEffect(date, authToken) {
        logsViewModel.loadLogDetail(authToken, date)
    }

    // Show snackbar when message changes
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    // Clean up when leaving
    DisposableEffect(Unit) {
        onDispose {
            logsViewModel.resetLogDetail()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Blue Header with Curved Wave
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
                        text = "Log View",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    // Placeholder for symmetry
                    Spacer(modifier = Modifier.width(48.dp))
                }

                // Curved background shape at bottom
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
            when (val state = logDetailState) {
                is LogDetailUiState.Idle, is LogDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Blue600)
                    }
                }

                is LogDetailUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Failed to load log details",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Button(
                            onClick = { logsViewModel.loadLogDetail(authToken, date) },
                            colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                        ) {
                            Text("Retry")
                        }
                    }
                }

                is LogDetailUiState.Success -> {
                    LogDetailContent(
                        eventsData = state.events,
                        displayDate = displayDate,
                        date = date,
                        isCertifying = certifyingDate == date,
                        onPreviousDay = {
                            // Navigate to previous day
                            try {
                                val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val currentDate = format.parse(date)
                                val calendar = Calendar.getInstance()
                                calendar.time = currentDate!!
                                calendar.add(Calendar.DAY_OF_MONTH, -1)
                                val prevDate = format.format(calendar.time)
                                navController.navigate("log_detail/$prevDate") {
                                    popUpTo("log_detail/$date") { inclusive = true }
                                }
                            } catch (e: Exception) { }
                        },
                        onNextDay = {
                            // Navigate to next day
                            try {
                                val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val currentDate = format.parse(date)
                                val calendar = Calendar.getInstance()
                                calendar.time = currentDate!!
                                calendar.add(Calendar.DAY_OF_MONTH, 1)
                                val nextDate = format.format(calendar.time)
                                navController.navigate("log_detail/$nextDate") {
                                    popUpTo("log_detail/$date") { inclusive = true }
                                }
                            } catch (e: Exception) { }
                        },
                        onCertify = {
                            logsViewModel.certifyLog(
                                token = authToken,
                                date = date,
                                onSuccess = {
                                    snackbarMessage = "Log certified successfully"
                                    logsViewModel.loadLogDetail(authToken, date)
                                },
                                onError = { error ->
                                    snackbarMessage = "Error: $error"
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LogDetailContent(
    eventsData: DriverEventsData,
    displayDate: String,
    date: String,
    isCertifying: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onCertify: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Date Navigator + ELD Graph Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    // Date Navigator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPreviousDay) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous Day",
                                tint = TextPrimary
                            )
                        }

                        Text(
                            text = displayDate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )

                        IconButton(onClick = onNextDay) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Day",
                                tint = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ELD Graph
                    ELDGraph(
                        events = eventsData.dutyStatusEvents,
                        summary = eventsData.summary,
                        logDate = date,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }

        // EVENTS Section Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE5E7EB))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EVENTS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // Events List
        if (eventsData.dutyStatusEvents.isEmpty()) {
            item {
                Text(
                    text = "No events for this date",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(eventsData.dutyStatusEvents) { event ->
                EventRow(event = event)
                Divider(color = Color(0xFFE5E7EB), thickness = 1.dp)
            }
        }

        // 10 Hour Restart indicator (if applicable)
        item {
            val offDutyMinutes = eventsData.summary?.offDutyMinutes ?: 0
            val sleeperMinutes = eventsData.summary?.sleeperBerthMinutes ?: 0
            val totalRestMinutes = offDutyMinutes + sleeperMinutes

            if (totalRestMinutes >= 600) { // 10 hours = 600 minutes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF97316)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "10",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "10 Hour Restart",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
                Divider(color = Color(0xFFE5E7EB), thickness = 1.dp)
            }
        }

        // GENERAL INFO Section Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE5E7EB))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "GENERAL INFO",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // General Info Fields
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                // Co-Driver
                Text(
                    text = "Co-Driver",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = eventsData.coDriver ?: "-",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Vehicle
                Text(
                    text = "Vehicle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = eventsData.vehicle ?: "-",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Trailers
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF3F4F6))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Trailers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Shipping Docs
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF3F4F6))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Shipping Docs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Notes
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF3F4F6))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // Certified / Certify Button
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (eventsData.isCertified) {
                    Text(
                        text = "Certified",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                } else {
                    Button(
                        onClick = onCertify,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isCertifying
                    ) {
                        if (isCertifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "Certify",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * ELD Graph Component - Visual representation of duty status throughout the day
 * Matches iOS design with all hour markers (M, 1, 2...11, N, 1, 2...11, M)
 *
 * Graph has 4 rows (top to bottom):
 * - Row 0: OFF DUTY
 * - Row 1: SLEEPER BERTH
 * - Row 2: DRIVING
 * - Row 3: ON DUTY
 *
 * Line widths:
 * - Horizontal (duration): thicker
 * - Vertical (transitions): thinner
 * - Orange current time: only shown for today's log
 */
@Composable
private fun ELDGraph(
    events: List<DutyStatusEventDto>,
    summary: DutyStatusSummary?,
    logDate: String, // Format: yyyy-MM-dd
    modifier: Modifier = Modifier
) {
    // All 25 time labels: M=Midnight, N=Noon (matching iOS design)
    val timeLabels = listOf(
        "M", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11",
        "N", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "M"
    )
    val statusLabels = listOf("OFF", "SB", "D", "ON")

    Column(modifier = modifier) {
        // Time labels row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            timeLabels.forEach { label ->
                Text(
                    text = label,
                    fontSize = 7.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(if (label.length > 1) 14.dp else 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Graph area
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Status labels column
            Column(
                modifier = Modifier.width(28.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                statusLabels.forEach { label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier.height(20.dp)
                    )
                }
            }

            // Graph canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val rowHeight = height / 4
                    val gridColor = Color(0xFFD1D5DB)
                    val gridColorLight = Color(0xFFE5E7EB)
                    val lineColor = Color(0xFF374151) // Dark gray for status lines

                    // Draw horizontal grid lines (5 lines for 4 rows)
                    for (i in 0..4) {
                        val y = i * rowHeight
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    // Draw vertical grid lines for every hour (24 hours = 24 segments)
                    for (i in 0..24) {
                        val x = (i / 24f) * width
                        val isMajor = i == 0 || i == 12 || i == 24 // Midnight, Noon, Midnight
                        val isQuarter = i % 6 == 0 // Every 6 hours
                        drawLine(
                            color = if (isMajor) gridColor else gridColorLight,
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = if (isMajor) 1.5f else 0.5f
                        )
                    }

                    // Helper: Convert duty status to row index (0-3)
                    fun getRowIndex(status: DutyStatusType?): Int {
                        return when (status) {
                            DutyStatusType.OFF_DUTY -> 0
                            DutyStatusType.SLEEPER_BERTH -> 1
                            DutyStatusType.DRIVING -> 2
                            DutyStatusType.ON_DUTY_NOT_DRIVING -> 3
                            DutyStatusType.PERSONAL_CONVEYANCE -> 0
                            DutyStatusType.YARD_MOVE -> 3
                            else -> 0
                        }
                    }

                    // Helper: Convert decimal hours to X position on canvas
                    fun hoursToX(hours: Float): Float {
                        return (hours / 24f) * width
                    }

                    // Helper: Convert row index to Y position (center of row)
                    fun rowToY(rowIndex: Int): Float {
                        return rowIndex * rowHeight + rowHeight / 2
                    }

                    // Check if this log is for today
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val isToday = logDate == todayStr

                    // Get current time (local time)
                    val now = Calendar.getInstance()
                    val currentDecimalHours = now.get(Calendar.HOUR_OF_DAY) +
                                              now.get(Calendar.MINUTE) / 60f

                    // Sort events by start time
                    val sortedEvents = events.sortedBy { it.startTime }

                    if (sortedEvents.isEmpty()) {
                        // No events - draw nothing
                        return@Canvas
                    }

                    // Line thickness constants (matching iOS)
                    val horizontalLineWidth = 4.5f  // Thicker for duration
                    val verticalLineWidth = 1.5f   // Thinner for transitions

                    // Draw the ELD status line
                    sortedEvents.forEachIndexed { index, event ->
                        val rowIndex = getRowIndex(event.dutyStatus)
                        val y = rowToY(rowIndex)

                        // Get start time in LOCAL timezone (converted from UTC)
                        val startHours = event.startTimeLocalHours
                        val startX = hoursToX(startHours)

                        // Get end time in LOCAL timezone (converted from UTC)
                        // For ongoing events on today's log, extend to current time
                        val endHours = if (event.endTimeLocalHours != null) {
                            event.endTimeLocalHours!!
                        } else if (isToday) {
                            currentDecimalHours
                        } else {
                            24f // End of day for past logs
                        }
                        val endX = hoursToX(endHours)

                        // 1. Draw HORIZONTAL line for this status duration (thicker)
                        drawLine(
                            color = lineColor,
                            start = Offset(startX, y),
                            end = Offset(endX, y),
                            strokeWidth = horizontalLineWidth
                        )

                        // 2. Draw VERTICAL line at the END to connect to next status (thinner)
                        if (index < sortedEvents.size - 1) {
                            val nextEvent = sortedEvents[index + 1]
                            val nextRowIndex = getRowIndex(nextEvent.dutyStatus)

                            // Only draw vertical if status changes (different row)
                            if (nextRowIndex != rowIndex) {
                                val nextY = rowToY(nextRowIndex)
                                drawLine(
                                    color = lineColor,
                                    start = Offset(endX, y),
                                    end = Offset(endX, nextY),
                                    strokeWidth = verticalLineWidth
                                )
                            }
                        }
                    }

                    // Draw current time indicator (orange vertical line) - ONLY for today
                    if (isToday) {
                        val currentTimeX = hoursToX(currentDecimalHours)
                        drawLine(
                            color = Color(0xFFF97316),
                            start = Offset(currentTimeX, 0f),
                            end = Offset(currentTimeX, height),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            // Summary times column (right side)
            Column(
                modifier = Modifier.width(40.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                val offDutyTime = formatMinutesToTime(summary?.offDutyMinutes ?: 0)
                val sleeperTime = formatMinutesToTime(summary?.sleeperBerthMinutes ?: 0)
                val drivingTime = formatMinutesToTime(summary?.drivingMinutes ?: 0)
                val onDutyTime = formatMinutesToTime(summary?.onDutyNotDrivingMinutes ?: 0)

                listOf(offDutyTime, sleeperTime, drivingTime, onDutyTime).forEach { time ->
                    Text(
                        text = time,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatMinutesToTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return "%02d:%02d".format(hours, mins)
}

/**
 * Event Row Component
 */
@Composable
private fun EventRow(event: DutyStatusEventDto) {
    val statusBadge = when (event.dutyStatus) {
        DutyStatusType.OFF_DUTY -> "OFF" to Color(0xFF6B7280)
        DutyStatusType.SLEEPER_BERTH -> "SB" to Color(0xFF8B5CF6)
        DutyStatusType.DRIVING -> "D" to Color(0xFF10B981)
        DutyStatusType.ON_DUTY_NOT_DRIVING -> "ON" to Color(0xFF3B82F6)
        DutyStatusType.PERSONAL_CONVEYANCE -> "PC" to Color(0xFF6B7280)
        DutyStatusType.YARD_MOVE -> "YM" to Color(0xFF8B5CF6)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Status Badge
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(statusBadge.second),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusBadge.first,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Event Details
        Column(modifier = Modifier.weight(1f)) {
            // Time and Duration
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.timeFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "  |  ${event.durationFormatted}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Location
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatLocationDisplay(event.location, event.latitude, event.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Vehicle inspection note if ON_DUTY
            if (event.dutyStatus == DutyStatusType.ON_DUTY_NOT_DRIVING && event.vehicleNumber != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Inspection",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Vehicle inspection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
