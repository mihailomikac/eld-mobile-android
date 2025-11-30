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

    // Load logs on first composition
    LaunchedEffect(authToken) {
        logsViewModel.loadLogs(authToken, days = 14)
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
                        IconButton(onClick = { navController.popBackStack() }) {
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
                            val hasUncertified = logs.any { !it.isCertified }

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
                                                val dateStr = log.date.substringBefore("T")
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
                                        )
                                    }
                                }

                                // Certify All button (only show if there are uncertified logs)
                                if (hasUncertified) {
                                    Button(
                                        onClick = {
                                            logsViewModel.certifyAllLogs(
                                                token = authToken,
                                                onSuccess = {
                                                    snackbarMessage = "All logs certified successfully"
                                                },
                                                onError = { error ->
                                                    snackbarMessage = "Error: $error"
                                                }
                                            )
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

                if (!log.isCertified) {
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
                } else {
                    Text(
                        text = "Certified",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = StatusOnDuty
                    )
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
        }
    }
}
