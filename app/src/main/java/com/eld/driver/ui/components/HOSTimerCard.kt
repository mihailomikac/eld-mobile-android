package com.eld.driver.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eld.driver.ui.theme.*
import kotlin.math.abs

/**
 * HOS Timer Card - Circular progress timer with violation support
 * Features:
 * - Supports negative values (violations shown in red)
 * - Progress arc shows remaining time visually
 * - Smooth animations for progress changes
 * - Loading shimmer animation when isLoading=true
 */
@Composable
fun HOSTimerCard(
    title: String,
    remainingMinutes: Int,
    totalMinutes: Int,
    showClockIcon: Boolean = false,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Determine if in violation (negative time)
    val isViolation = remainingMinutes < 0

    // Calculate progress (0.0 to 1.0, capped at 0 for violations)
    val progress = if (totalMinutes > 0) {
        (remainingMinutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Animated progress for smooth transitions
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progress_animation"
    )

    // Colors based on state
    val primaryColor = when {
        isViolation -> ErrorRed
        progress < 0.15f -> WarningOrange
        else -> Blue600
    }

    val backgroundColor = when {
        isViolation -> ErrorRed.copy(alpha = 0.1f)
        progress < 0.15f -> WarningOrange.copy(alpha = 0.1f)
        else -> Blue600.copy(alpha = 0.05f)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(165.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                // Shimmer loading animation
                ShimmerCircle(modifier = Modifier.fillMaxSize())
            } else {
                // Timer display
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 12.dp.toPx()
                    val radius = (size.width - strokeWidth) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Background track (light gray)
                    drawCircle(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Progress arc
                    if (animatedProgress > 0f) {
                        drawArc(
                            color = primaryColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                            size = Size(size.width - strokeWidth, size.height - strokeWidth),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Inner fill circle
                    drawCircle(
                        color = backgroundColor,
                        radius = radius - strokeWidth / 2f - 4.dp.toPx(),
                        center = center
                    )

                    // White inner circle
                    drawCircle(
                        color = Color.White,
                        radius = radius - strokeWidth / 2f - 8.dp.toPx(),
                        center = center
                    )
                }

                // Time text and label in center
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Warning icon for violations
                    if (isViolation) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Violation",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    // Time display
                    Text(
                        text = formatTime(remainingMinutes),
                        fontSize = if (isViolation) 32.sp else 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        letterSpacing = 0.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Label (BREAK, DRIVE, etc.)
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isViolation) ErrorRed else TextPrimary,
                        letterSpacing = 0.5.sp
                    )

                    // Clock icon (only for SHIFT and CYCLE when not in violation)
                    if (showClockIcon && !isViolation) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shimmer loading animation for circular timer
 */
@Composable
private fun ShimmerCircle(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.5f),
        Color.LightGray.copy(alpha = 0.3f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim)
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(brush)
    )
}

/**
 * Format minutes to HH:MM format (supports negative values)
 */
private fun formatTime(minutes: Int): String {
    val isNegative = minutes < 0
    val absMinutes = abs(minutes)
    val hours = absMinutes / 60
    val mins = absMinutes % 60
    val timeStr = String.format("%02d:%02d", hours, mins)
    return if (isNegative) "-$timeStr" else timeStr
}

/**
 * HOS Timers Section - All 4 timers with loading support
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
    isLoading: Boolean = false,
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
                isLoading = isLoading,
                modifier = Modifier.weight(1f)
            )

            HOSTimerCard(
                title = "DRIVE",
                remainingMinutes = driveTimeRemaining,
                totalMinutes = driveTimeTotal,
                showClockIcon = false,
                isLoading = isLoading,
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
                isLoading = isLoading,
                modifier = Modifier.weight(1f)
            )

            HOSTimerCard(
                title = "CYCLE",
                remainingMinutes = cycleTimeRemaining,
                totalMinutes = cycleTimeTotal,
                showClockIcon = true,
                isLoading = isLoading,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Color constants for HOS states
private val ErrorRed = Color(0xFFE53935)
private val WarningOrange = Color(0xFFFB8C00)
