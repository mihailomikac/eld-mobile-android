package com.eld.driver.hos

import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.eld.driver.data.local.entity.HOSStatusEntity
import com.eld.driver.data.local.entity.HOSViolationType
import com.eld.driver.data.models.DutyStatusType

/**
 * HOS (Hours of Service) Calculator.
 *
 * KEY PRINCIPLE: Events are the source of truth. HOS is DERIVED from events.
 * This class walks through events chronologically and calculates:
 * - Drive time in current shift
 * - Shift duration (14-hour window)
 * - Driving time since last 30-min break
 * - Cycle hours (60/70 hour limit)
 *
 * FMCSA Rules for property-carrying vehicles:
 * - 11-Hour Driving Limit: May drive max 11 hours after 10 consecutive hours off duty
 * - 14-Hour Limit: May not drive beyond 14 hours after coming on duty
 * - 30-Minute Break: Must take 30-min break after 8 hours of driving
 * - 60/70-Hour Limit: May not drive after 60/70 hours on duty in 7/8 consecutive days
 * - 34-Hour Restart: Can restart 60/70-hour period after 34+ consecutive hours off duty
 * - Split Sleeper Berth: Can split 10h rest into 7h SB + 2-3h rest (§395.1(g)(ii))
 */

/**
 * Cycle rule types supported by FMCSA
 */
enum class CycleRule(val limitHours: Int, val days: Int) {
    US_70_HOUR_8_DAY(70, 8),
    US_60_HOUR_7_DAY(60, 7)
}

class HOSCalculator(
    private val cycleRule: CycleRule = CycleRule.US_70_HOUR_8_DAY,
    private val splitSleeperDetector: SplitSleeperDetector = SplitSleeperDetector()
) {
    companion object {
        // Limits in milliseconds
        const val DRIVE_LIMIT_MS = 11L * 60 * 60 * 1000        // 11 hours
        const val SHIFT_LIMIT_MS = 14L * 60 * 60 * 1000        // 14 hours
        const val BREAK_REQUIRED_AFTER_MS = 8L * 60 * 60 * 1000 // 8 hours driving
        const val BREAK_MINIMUM_MS = 30L * 60 * 1000           // 30 minutes
        const val REST_PERIOD_MINIMUM_MS = 10L * 60 * 60 * 1000 // 10 hours
        const val CYCLE_RESTART_MS = 34L * 60 * 60 * 1000      // 34 hours for cycle restart

        const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }

    val cycleLimitMs: Long = cycleRule.limitHours * 60L * 60 * 1000

    /**
     * Calculate HOS status from duty status events.
     * Walks through events chronologically to track state accurately.
     *
     * @param events List of duty status events (should be last 8 days)
     * @param currentStatus Current duty status (optional, for context)
     * @return HOSCalculationResult with all calculated values
     */
    fun calculate(
        events: List<DutyStatusEventEntity>,
        currentStatus: String? = null
    ): HOSCalculationResult {
        val now = System.currentTimeMillis()

        if (events.isEmpty()) {
            return createDefaultResult(now, currentStatus)
        }

        // Sort events chronologically
        val sorted = events.sortedBy { it.startTime }

        // Determine current status from last event if not provided
        val effectiveCurrentStatus = currentStatus ?: sorted.lastOrNull()?.dutyStatus

        // Analyze for Split Sleeper Berth pairs (§395.1(g)(ii))
        val splitSleeperAnalysis = splitSleeperDetector.analyze(sorted, now)
        val splitPairs = splitSleeperAnalysis.validPairs

        // State tracking
        var shiftStart: Long? = null
        var driveTimeInShift = 0L
        var driveTimeSinceBreak = 0L
        var consecutiveRestStart: Long? = null
        var lastBreakEndTime: Long? = null
        var cycleRestartTime: Long? = null

        // Track excluded time from split sleeper pairs for 14h calculation
        var splitSleeperExcludedTime = 0L

        // For cycle calculation - track on-duty time by day
        val onDutyByDay = mutableMapOf<Long, Long>()

        // Track violations that occurred during the event history (not just current state)
        val detectedViolations = mutableSetOf<HOSViolationType>()
        val violationStartTimes = mutableMapOf<HOSViolationType, Long>()

        for (event in sorted) {
            val eventEnd = event.endTime ?: now
            val eventDuration = eventEnd - event.startTime

            when {
                event.isRestStatus() -> {
                    // Track start of consecutive rest period
                    if (consecutiveRestStart == null) {
                        consecutiveRestStart = event.startTime
                    }

                    val totalRestDuration = eventEnd - consecutiveRestStart

                    // Check if 30-min break completed
                    if (totalRestDuration >= BREAK_MINIMUM_MS && lastBreakEndTime == null) {
                        lastBreakEndTime = consecutiveRestStart + BREAK_MINIMUM_MS
                        driveTimeSinceBreak = 0L
                    }

                    // Check if 10-hour rest completed (new shift) - standard rule
                    if (totalRestDuration >= REST_PERIOD_MINIMUM_MS && shiftStart != null) {
                        // Reset for new shift
                        driveTimeInShift = 0L
                        driveTimeSinceBreak = 0L
                        shiftStart = null
                        splitSleeperExcludedTime = 0L  // Reset excluded time for new shift
                    }

                    // Check if this rest period is part of a split sleeper pair
                    // If so, track the excluded time for 14h calculation
                    for (pair in splitPairs) {
                        val excludedFromThisEvent = pair.getExcludedTimeMs(event)
                        if (excludedFromThisEvent > 0) {
                            splitSleeperExcludedTime += excludedFromThisEvent
                        }

                        // Check if this completes a split pair - reset shift from calculation point
                        if (pair.secondPeriod.endTime == eventEnd) {
                            // Split pair completed! Reset drive time but use new calculation point
                            driveTimeInShift = 0L
                            driveTimeSinceBreak = 0L
                            shiftStart = null  // Will be set to new CP when next on-duty starts
                        }
                    }

                    // Check if 34-hour restart completed (cycle reset)
                    if (totalRestDuration >= CYCLE_RESTART_MS) {
                        cycleRestartTime = consecutiveRestStart + CYCLE_RESTART_MS
                        onDutyByDay.clear()
                        splitSleeperExcludedTime = 0L  // Reset excluded time
                    }
                }

                event.isDrivingStatus() -> {
                    // End consecutive rest period
                    consecutiveRestStart = null

                    // Start new shift if needed
                    if (shiftStart == null) {
                        shiftStart = event.startTime
                    }

                    // Accumulate drive time
                    driveTimeInShift += eventDuration
                    driveTimeSinceBreak += eventDuration

                    // Track on-duty for cycle
                    addOnDutyToDay(onDutyByDay, event.startTime, eventDuration)

                    // Update last break end time (break was interrupted by driving)
                    lastBreakEndTime = null
                }

                event.isOnDutyNotDriving() -> {
                    // End consecutive rest period
                    consecutiveRestStart = null

                    // Start new shift if needed
                    if (shiftStart == null) {
                        shiftStart = event.startTime
                    }

                    // Track on-duty for cycle (ON_DUTY counts toward cycle but not drive time)
                    addOnDutyToDay(onDutyByDay, event.startTime, eventDuration)

                    // FMCSA §395.3(a)(3)(ii): ON_DUTY_NOT_DRIVING qualifies for 30-minute break!
                    // "the 30-minute rest break can be satisfied by off duty, sleeper berth,
                    // on duty (not driving), or any combination of the three"
                    if (eventDuration >= BREAK_MINIMUM_MS) {
                        lastBreakEndTime = event.startTime + BREAK_MINIMUM_MS
                        driveTimeSinceBreak = 0L
                    }
                }
            }
        }

        // Calculate final values
        val effectiveShiftStart = shiftStart ?: now
        val rawShiftDuration = now - effectiveShiftStart

        // SPLIT SLEEPER BERTH: Exclude rest periods from 14h calculation (§395.1(g)(ii))
        // Both periods of a valid split are excluded from the driving window
        val adjustedShiftDuration = if (splitPairs.isNotEmpty() && shiftStart != null) {
            splitSleeperDetector.calculateAdjustedShiftDuration(
                events = sorted,
                splitPairs = splitPairs,
                shiftStartTime = effectiveShiftStart,
                now = now
            )
        } else {
            rawShiftDuration
        }

        // Calculate cycle hours from tracked on-duty time
        val cycleWindowStart = cycleRestartTime ?: (now - (cycleRule.days * MS_PER_DAY))
        val cycleHours = calculateCycleHoursFromDays(onDutyByDay, cycleWindowStart, now)

        // Check for violations using ADJUSTED shift duration
        // Pass effectiveCurrentStatus because SHIFT_TIME_EXCEEDED only applies when DRIVING after 14h
        val violations = checkViolations(driveTimeInShift, adjustedShiftDuration, driveTimeSinceBreak, cycleHours, effectiveCurrentStatus)

        // Calculate cycle time remaining for tomorrow
        val cycleLeftForTomorrow = calculateCycleLeftForTomorrow(onDutyByDay, now)

        return HOSCalculationResult(
            driveTimeRemainingMs = DRIVE_LIMIT_MS - driveTimeInShift,
            shiftTimeRemainingMs = SHIFT_LIMIT_MS - adjustedShiftDuration,
            breakTimeRemainingMs = BREAK_REQUIRED_AFTER_MS - driveTimeSinceBreak,
            cycleTimeRemainingMs = cycleLimitMs - cycleHours,

            currentDriveTimeMs = driveTimeInShift,
            currentShiftDurationMs = adjustedShiftDuration,
            drivingTimeSinceBreakMs = driveTimeSinceBreak,
            currentCycleHoursMs = cycleHours,

            shiftStartTime = effectiveShiftStart,
            lastBreakEndTime = lastBreakEndTime,
            cycleStartTime = cycleWindowStart,
            calculatedAt = now,

            violations = violations,
            currentDutyStatus = effectiveCurrentStatus,
            cycleRule = cycleRule,
            cycleLeftForTomorrowMs = cycleLeftForTomorrow
        )
    }

    /**
     * Track on-duty time by day for cycle calculation.
     */
    private fun addOnDutyToDay(
        onDutyByDay: MutableMap<Long, Long>,
        startTime: Long,
        duration: Long
    ) {
        val dayStart = getDayStart(startTime)
        val current = onDutyByDay[dayStart] ?: 0L
        onDutyByDay[dayStart] = current + duration
    }

    /**
     * Calculate total on-duty time from day tracking.
     */
    private fun calculateCycleHoursFromDays(
        onDutyByDay: Map<Long, Long>,
        windowStart: Long,
        now: Long
    ): Long {
        return onDutyByDay.entries
            .filter { it.key >= getDayStart(windowStart) }
            .sumOf { it.value }
    }

    /**
     * Calculate remaining cycle time for tomorrow.
     */
    private fun calculateCycleLeftForTomorrow(
        onDutyByDay: Map<Long, Long>,
        now: Long
    ): Long {
        val tomorrowStart = getTomorrowStartMs(now)
        val oldestDayStart = tomorrowStart - (cycleRule.days * MS_PER_DAY)

        // Hours from the oldest day that will "fall off"
        val oldestDayHours = onDutyByDay[getDayStart(oldestDayStart)] ?: 0L

        // Current cycle used
        val currentCycleUsed = onDutyByDay.entries
            .filter { it.key >= getDayStart(now - (cycleRule.days * MS_PER_DAY)) }
            .sumOf { it.value }

        val currentRemaining = cycleLimitMs - currentCycleUsed

        // Tomorrow's remaining = current remaining + hours falling off
        val tomorrowRemaining = currentRemaining + oldestDayHours
        return minOf(tomorrowRemaining, cycleLimitMs)
    }

    private fun getDayStart(timestamp: Long): Long {
        return (timestamp / MS_PER_DAY) * MS_PER_DAY
    }

    private fun getTomorrowStartMs(now: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Check for HOS violations.
     *
     * FMCSA Rules:
     * - DRIVE_TIME_EXCEEDED: Driving more than 11 hours
     * - SHIFT_TIME_EXCEEDED: DRIVING after 14-hour window (ON_DUTY after 14h is allowed)
     * - BREAK_REQUIRED: Driving more than 8h without 30-min break
     * - CYCLE_TIME_EXCEEDED: Exceeding 60/70 hour cycle limit
     */
    private fun checkViolations(
        driveTimeMs: Long,
        shiftDurationMs: Long,
        timeSinceBreakMs: Long,
        cycleHoursMs: Long,
        currentStatus: String?
    ): List<HOSViolationType> {
        val violations = mutableListOf<HOSViolationType>()

        if (driveTimeMs > DRIVE_LIMIT_MS) {
            violations.add(HOSViolationType.DRIVE_TIME_EXCEEDED)
        }

        // SHIFT_TIME_EXCEEDED only triggers when DRIVING after 14h window
        // Being ON_DUTY (not driving) after 14h is allowed per FMCSA
        val isDriving = currentStatus == "DRIVING"
        if (shiftDurationMs > SHIFT_LIMIT_MS && isDriving) {
            violations.add(HOSViolationType.SHIFT_TIME_EXCEEDED)
        }

        if (timeSinceBreakMs > BREAK_REQUIRED_AFTER_MS) {
            violations.add(HOSViolationType.BREAK_REQUIRED)
        }
        if (cycleHoursMs > cycleLimitMs) {
            violations.add(HOSViolationType.CYCLE_TIME_EXCEEDED)
        }

        return violations
    }

    /**
     * Create default result when no events exist.
     */
    private fun createDefaultResult(now: Long, currentStatus: String?): HOSCalculationResult {
        return HOSCalculationResult(
            driveTimeRemainingMs = DRIVE_LIMIT_MS,
            shiftTimeRemainingMs = SHIFT_LIMIT_MS,
            breakTimeRemainingMs = BREAK_REQUIRED_AFTER_MS,
            cycleTimeRemainingMs = cycleLimitMs,

            currentDriveTimeMs = 0L,
            currentShiftDurationMs = 0L,
            drivingTimeSinceBreakMs = 0L,
            currentCycleHoursMs = 0L,

            shiftStartTime = now,
            lastBreakEndTime = null,
            cycleStartTime = now - (cycleRule.days * MS_PER_DAY),
            calculatedAt = now,

            violations = emptyList(),
            currentDutyStatus = currentStatus,
            cycleRule = cycleRule,
            cycleLeftForTomorrowMs = cycleLimitMs
        )
    }

    private fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(timestamp))
    }
}

/**
 * Result of HOS calculation.
 */
data class HOSCalculationResult(
    // Remaining times in milliseconds
    val driveTimeRemainingMs: Long,
    val shiftTimeRemainingMs: Long,
    val breakTimeRemainingMs: Long,
    val cycleTimeRemainingMs: Long,

    // Used/elapsed times in milliseconds
    val currentDriveTimeMs: Long,
    val currentShiftDurationMs: Long,
    val drivingTimeSinceBreakMs: Long,
    val currentCycleHoursMs: Long,

    // Timestamps
    val shiftStartTime: Long,
    val lastBreakEndTime: Long?,
    val cycleStartTime: Long? = null,
    val calculatedAt: Long,

    // Violations
    val violations: List<HOSViolationType>,

    // Context
    val currentDutyStatus: String?,

    // Cycle rule info
    val cycleRule: CycleRule = CycleRule.US_70_HOUR_8_DAY,
    val cycleLeftForTomorrowMs: Long = 0L
) {
    /**
     * Convert to entity for database storage.
     */
    fun toEntity(): HOSStatusEntity {
        return HOSStatusEntity(
            id = 1,
            driveTimeRemainingSeconds = driveTimeRemainingMs / 1000,
            shiftTimeRemainingSeconds = shiftTimeRemainingMs / 1000,
            breakTimeRemainingSeconds = breakTimeRemainingMs / 1000,
            cycleTimeRemainingSeconds = cycleTimeRemainingMs / 1000,

            driveTimeUsedSeconds = currentDriveTimeMs / 1000,
            shiftTimeUsedSeconds = currentShiftDurationMs / 1000,
            breakTimeDrivingSeconds = drivingTimeSinceBreakMs / 1000,
            cycleTimeUsedSeconds = currentCycleHoursMs / 1000,

            shiftStartTime = shiftStartTime,
            lastBreakEndTime = lastBreakEndTime,

            violations = violations.joinToString(",") { it.name },
            violationStartTimesJson = "",
            lastCalculatedAt = calculatedAt,
            currentDutyStatus = currentDutyStatus
        )
    }

    // Cycle limit in ms based on rule
    private val cycleLimitMs: Long get() = cycleRule.limitHours * 60L * 60 * 1000

    // Progress values (0.0 to 1.0)
    val driveProgress: Float
        get() = (driveTimeRemainingMs.toFloat() / HOSCalculator.DRIVE_LIMIT_MS).coerceIn(0f, 1f)

    val shiftProgress: Float
        get() = (shiftTimeRemainingMs.toFloat() / HOSCalculator.SHIFT_LIMIT_MS).coerceIn(0f, 1f)

    val breakProgress: Float
        get() = (breakTimeRemainingMs.toFloat() / HOSCalculator.BREAK_REQUIRED_AFTER_MS).coerceIn(0f, 1f)

    val cycleProgress: Float
        get() = (cycleTimeRemainingMs.toFloat() / cycleLimitMs).coerceIn(0f, 1f)

    // Formatted time strings
    val driveTimeRemainingFormatted: String get() = formatMs(driveTimeRemainingMs)
    val shiftTimeRemainingFormatted: String get() = formatMs(shiftTimeRemainingMs)
    val breakTimeRemainingFormatted: String get() = formatMs(breakTimeRemainingMs)
    val cycleTimeRemainingFormatted: String get() = formatMs(cycleTimeRemainingMs)
    val cycleLeftForTomorrowFormatted: String get() = formatMs(cycleLeftForTomorrowMs)

    val driveTimeTotalFormatted: String get() = "11:00"
    val shiftTimeTotalFormatted: String get() = "14:00"
    val breakTimeTotalFormatted: String get() = "08:00"
    val cycleTimeTotalFormatted: String get() = "${cycleRule.limitHours}:00"

    fun hasViolations(): Boolean = violations.isNotEmpty()

    private fun formatMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return String.format("%02d:%02d", hours, minutes)
    }
}
