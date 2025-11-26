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
        // Circular timer with double ring effect
        Box(
            modifier = Modifier.size(165.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val outerStrokeWidth = 20.dp.toPx()  // Outer ring thickness
                val innerStrokeWidth = 4.dp.toPx()   // Inner white ring thickness
                val gapWidth = 3.dp.toPx()           // Gap between rings

                // Outer blue ring (full circle)
                drawCircle(
                    color = Blue600,
                    radius = (size.width / 2f) - (outerStrokeWidth / 2f),
                    style = Stroke(width = outerStrokeWidth)
                )

                // Inner white ring (creates the double border effect)
                drawCircle(
                    color = Color.White,
                    radius = (size.width / 2f) - outerStrokeWidth - gapWidth - (innerStrokeWidth / 2f),
                    style = Stroke(width = innerStrokeWidth)
                )

                // White background inside
                drawCircle(
                    color = Color.White,
                    radius = (size.width / 2f) - outerStrokeWidth - gapWidth - innerStrokeWidth
                )
            }

            // Time text and label in center
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Time (larger, blue)
                Text(
                    text = formatTime(remainingMinutes),
                    fontSize = 36.sp,  // Increased from 32sp
                    fontWeight = FontWeight.Bold,
                    color = Blue600,
                    letterSpacing = 0.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Label (BREAK, DRIVE, etc.)
                Text(
                    text = title,
                    fontSize = 18.sp,  // Increased from 16sp
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )

                // Clock icon (only for SHIFT and CYCLE)
                if (showClockIcon) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
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
