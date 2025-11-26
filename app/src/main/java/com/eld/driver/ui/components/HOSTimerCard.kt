package com.eld.driver.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eld.driver.ui.theme.*

/**
 * HOS Timer Card - Circular progress timer matching iOS design exactly
 * Uses blue accent color for all timers (matching iOS accentPrimary)
 */
@Composable
fun HOSTimerCard(
    title: String,
    remainingMinutes: Int,
    totalMinutes: Int,
    showClockIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    val progress = if (totalMinutes > 0) remainingMinutes.toFloat() / totalMinutes.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "progress_animation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Circular timer
        Box(
            modifier = Modifier.size(155.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background circle
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()

                // Background arc (gray)
                drawArc(
                    color = BorderLight,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width - strokeWidth,
                        height = size.height - strokeWidth
                    ),
                    topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                )

                // Progress arc (blue - matches iOS accentPrimary)
                if (animatedProgress > 0f) {
                    drawArc(
                        color = Blue600,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        size = androidx.compose.ui.geometry.Size(
                            width = size.width - strokeWidth,
                            height = size.height - strokeWidth
                        ),
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    )
                }
            }

            // Time text and icon in center
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatTime(remainingMinutes),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue600
                )

                // Clock icon (only for SHIFT and CYCLE)
                if (showClockIcon) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Label
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

/**
 * Format minutes to HH:MM format
 */
private fun formatTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return String.format("%02d:%02d", hours, mins)
}

/**
 * HOS Timers Section - All 4 timers matching iOS exactly
 * All timers use blue color, clock icon only on SHIFT and CYCLE
 */
@Composable
fun HOSTimersSection(
    breakTimeRemaining: Int,
    breakTimeTotal: Int,
    driveTimeRemaining: Int,
    driveTimeTotal: Int,
    shiftTimeRemaining: Int,
    shiftTimeTotal: Int,
    cycleTimeRemaining: Int,
    cycleTimeTotal: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // First row: BREAK and DRIVE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HOSTimerCard(
                title = "BREAK",
                remainingMinutes = breakTimeRemaining,
                totalMinutes = breakTimeTotal,
                showClockIcon = false,
                modifier = Modifier.weight(1f)
            )

            HOSTimerCard(
                title = "DRIVE",
                remainingMinutes = driveTimeRemaining,
                totalMinutes = driveTimeTotal,
                showClockIcon = false,
                modifier = Modifier.weight(1f)
            )
        }

        // Second row: SHIFT and CYCLE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HOSTimerCard(
                title = "SHIFT",
                remainingMinutes = shiftTimeRemaining,
                totalMinutes = shiftTimeTotal,
                showClockIcon = true,
                modifier = Modifier.weight(1f)
            )

            HOSTimerCard(
                title = "CYCLE",
                remainingMinutes = cycleTimeRemaining,
                totalMinutes = cycleTimeTotal,
                showClockIcon = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
