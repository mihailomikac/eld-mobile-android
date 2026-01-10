package com.eld.driver.ui.screens.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
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
import com.eld.driver.data.models.DailyLogDto
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.theme.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog

/**
 * Logs Screen - Shows list of daily logs with real API data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    navController: NavController,
    authToken: String,
    logsViewModel: LogsViewModel = viewModel()
) {
    val logsState by logsViewModel.logsState.collectAsState()
    val isRefreshing by logsViewModel.isRefreshing.collectAsState()
    val certifyingDate by logsViewModel.certifyingDate.collectAsState()

    // Snackbar state for messages
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // Certification dialog state
    var showCertificationDialog by remember { mutableStateOf(false) }
    var certificationDatePending by remember { mutableStateOf<String?>(null) }
    var certifyAllPending by remember { mutableStateOf(false) }

    // Load logs on first composition
    // Backend returns: last 8 days (today + 7 previous) + uncertified logs (up to 6 months old)
    LaunchedEffect(authToken) {
        logsViewModel.loadLogs(authToken)  // Uses default days = 8
    }

    // Show snackbar when message changes
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                        IconButton(onClick = {
                            // Navigate explicitly to dashboard instead of popBackStack
                            // This prevents navigation state issues that could cause drawer to break
                            navController.navigate("dashboard") {
                                popUpTo("logs") { inclusive = true }
                                launchSingleTop = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = "Logs",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = { logsViewModel.refreshLogs(authToken) },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Curved background shape at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .align(Alignment.BottomCenter)
                            .clip(CurvedWaveShape())
                            .background(BgSecondary)
                    )
                }

                // Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgSecondary)
                ) {
                    when (val state = logsState) {
                        is LogsUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Blue600)
                            }
                        }

                        is LogsUiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(Spacing.lg),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Failed to load logs",
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
                                    onClick = { logsViewModel.loadLogs(authToken) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }

                        is LogsUiState.Success -> {
                            val logs = state.logs
                            // Only show Certify All if there are logs that CAN be certified
                            // (not already certified AND not today's log)
                            val hasCertifiable = logs.any { it.canCertify }

                            Column(modifier = Modifier.fillMaxSize()) {
                                // Logs list
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = Spacing.md, vertical = Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    items(logs) { log ->
                                        DailyLogCard(
                                            log = log,
                                            isCertifying = certifyingDate == log.date.substringBefore("T"),
                                            onClick = {
                                                // Navigate to log detail
                                                val dateParam = log.date.substringBefore("T")
                                                navController.navigate("log_detail/$dateParam")
                                            },
                                            onCertify = {
                                                // Show FMCSA certification dialog first
                                                certificationDatePending = log.date.substringBefore("T")
                                                certifyAllPending = false
                                                showCertificationDialog = true
                                            }
                                        )
                                    }
                                }

                                // Certify All button (only show if there are certifiable logs)
                                if (hasCertifiable) {
                                    Button(
                                        onClick = {
                                            // Show FMCSA certification dialog first
                                            certificationDatePending = null
                                            certifyAllPending = true
                                            showCertificationDialog = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.md)
                                            .height(56.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                                        shape = RoundedCornerShape(CornerRadius.medium),
                                        enabled = certifyingDate == null
                                    ) {
                                        if (certifyingDate != null) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(Spacing.sm))
                                        }
                                        Text(
                                            text = "Certify All",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // FMCSA Certification Dialog
    if (showCertificationDialog) {
        CertificationDialog(
            onAgree = {
                showCertificationDialog = false
                if (certifyAllPending) {
                    // Certify all logs
                    logsViewModel.certifyAllLogs(
                        token = authToken,
                        onSuccess = {
                            snackbarMessage = "All logs certified successfully"
                        },
                        onError = { error ->
                            snackbarMessage = "Error: $error"
                        }
                    )
                } else {
                    // Certify single log
                    certificationDatePending?.let { dateStr ->
                        logsViewModel.certifyLog(
                            token = authToken,
                            date = dateStr,
                            onSuccess = {
                                snackbarMessage = "Log certified successfully"
                            },
                            onError = { error ->
                                snackbarMessage = "Error: $error"
                            }
                        )
                    }
                }
                certificationDatePending = null
                certifyAllPending = false
            },
            onNotReady = {
                showCertificationDialog = false
                certificationDatePending = null
                certifyAllPending = false
            }
        )
    }
}

/**
 * FMCSA Certification Dialog
 * Per 49 CFR Appendix-A-to-Subpart-B-of-Part-395(a):
 * - Must display the certification statement
 * - Must prompt driver to select "Agree" or "Not ready"
 */
@Composable
private fun CertificationDialog(
    onAgree: () -> Unit,
    onNotReady: () -> Unit
) {
    Dialog(onDismissRequest = onNotReady) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            shape = RoundedCornerShape(CornerRadius.large),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "Driver's Certification",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // FMCSA Required Statement
                Text(
                    text = "I hereby certify that my data entries and my record of duty status for this 24-hour period are true and correct.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Not Ready button
                    OutlinedButton(
                        onClick = onNotReady,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(CornerRadius.medium),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text(
                            text = "Not ready",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Agree button
                    Button(
                        onClick = onAgree,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(CornerRadius.medium),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                    ) {
                        Text(
                            text = "Agree",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Daily Log Card
 */
@Composable
private fun DailyLogCard(
    log: DailyLogDto,
    isCertifying: Boolean,
    onClick: () -> Unit,
    onCertify: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(CornerRadius.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            // Date and Uncertified status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.dateFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                when {
                    log.isCertified -> {
                        Text(
                            text = "Certified",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = StatusOnDuty
                        )
                    }
                    log.isToday -> {
                        // Today's log cannot be certified
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                    else -> {
                        // Can certify
                        if (isCertifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentRed,
                                strokeWidth = 2.dp
                            )
                        } else {
                            TextButton(
                                onClick = onCertify,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Certify",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = AccentRed
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Recap and Inspections
            Text(
                text = "Recap: ${log.recapFormatted}  |  ${log.inspectionsFormatted}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // Distance
            Text(
                text = "Distance: ${log.distanceFormatted}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // Violations badge (if any)
            if (log.hasViolations) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentRed.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${log.violationCount} Violation${if (log.violationCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = AccentRed
                        )
                    }
                }
            }

            // Form & Manner errors badge (if any)
            if (log.hasFormMannerErrors) {
                Spacer(modifier = Modifier.height(if (log.hasViolations) 4.dp else 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${log.formMannerErrorCount} Form Error${if (log.formMannerErrorCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF59E0B)
                        )
                    }

                    // Show first error type
                    if (log.formMannerErrors.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = log.formMannerErrors.first(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
