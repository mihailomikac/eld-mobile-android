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
import androidx.navigation.NavController
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.theme.*

/**
 * Logs Screen - Shows list of daily logs matching iOS design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(navController: NavController, authToken: String) {
    // Mock data for now
    val logs = remember {
        listOf(
            DailyLog("Tue, Nov 25", "0 hr 0 min", "No Inspections", "0 mi", false),
            DailyLog("Mon, Nov 24", "0 hr 0 min", "No Inspections", "0 mi", true),
            DailyLog("Sun, Nov 23", "0 hr 0 min", "No Inspections", "0 mi", false),
            DailyLog("Sat, Nov 22", "0 hr 18 min", "1 defect", "0 mi", false),
            DailyLog("Fri, Nov 21", "0 hr 0 min", "No Inspections", "0 mi", false),
            DailyLog("Thu, Nov 20", "0 hr 0 min", "No Inspections", "0 mi", false)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

                    IconButton(onClick = { /* TODO: Show notifications */ }) {
                        Badge(containerColor = AccentRed) {
                            Text("6", color = Color.White, fontSize = 10.sp)
                        }
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White
                        )
                    }
                }

                // Curved background shape at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)  // Height of the curved section
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
                                onClick = {
                                    // TODO: Navigate to log detail
                                    // navController.navigate("log_detail/${log.date}")
                                }
                            )
                        }
                    }

                    // Certify All button
                    Button(
                        onClick = { /* TODO: Certify all logs */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                        shape = RoundedCornerShape(CornerRadius.medium)
                    ) {
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

/**
 * Daily Log Card
 */
@Composable
private fun DailyLogCard(
    log: DailyLog,
    onClick: () -> Unit
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
                    text = log.date,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                if (log.isUncertified) {
                    Text(
                        text = "Uncertified",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AccentRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Recap and Inspections
            Text(
                text = "Recap: ${log.recap}  |  ${log.inspections}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // Distance
            Text(
                text = "Distance: ${log.distance}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

/**
 * Daily Log data class
 */
data class DailyLog(
    val date: String,
    val recap: String,
    val inspections: String,
    val distance: String,
    val isUncertified: Boolean
)
