package com.eld.driver.hos

import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.eld.driver.data.local.entity.HOSViolationType
import com.eld.driver.data.models.DutyStatusType

/**
 * ViolationAnalyzer - Analyzes event history to detect violations.
 *
 * KEY PRINCIPLE: Events are the source of truth. Violations are DERIVED from events.
 * This class walks through the event timeline and:
 * 1. Detects when violations START (when limits are exceeded)
 * 2. Detects when violations END (when proper rest is taken)
 * 3. Returns complete violation records with accurate start/end times
 *
 * This ensures consistency even when driver is offline and syncs later.
 *
 * SPLIT SLEEPER BERTH (§395.1(g)(ii)):
 * Both periods of a valid split (7h SB + 2h rest) are excluded from 14h calculation.
 */
class ViolationAnalyzer(
    private val cycleRule: CycleRule = CycleRule.US_70_HOUR_8_DAY,
    private val splitSleeperDetector: SplitSleeperDetector = SplitSleeperDetector()
) {
    companion object {
        // HOS Limits
        const val DRIVE_LIMIT_MS = 11L * 60 * 60 * 1000        // 11 hours
        const val SHIFT_LIMIT_MS = 14L * 60 * 60 * 1000        // 14 hours
        const val BREAK_REQUIRED_AFTER_MS = 8L * 60 * 60 * 1000 // 8 hours driving
        const val BREAK_MINIMUM_MS = 30L * 60 * 1000           // 30 minutes
        const val REST_PERIOD_MINIMUM_MS = 10L * 60 * 60 * 1000 // 10 hours
        const val CYCLE_RESTART_MS = 34L * 60 * 60 * 1000      // 34 hours

        const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }

    private val cycleLimitMs: Long = cycleRule.limitHours * 60L * 60 * 1000

    /**
     * Analyze complete event history and return all violations
     * with accurate start and end times.
     *
     * @param events All duty status events (should cover at least 8 days)
     * @param now Current timestamp for active violations
     * @return List of violation records (completed and active)
     */
    fun analyzeViolations(
        events: List<DutyStatusEventEntity>,
        now: Long = System.currentTimeMillis()
    ): List<ViolationRecord> {

        if (events.isEmpty()) {
            return emptyList()
        }

        val sorted = events.sortedBy { it.startTime }
        val violations = mutableListOf<ViolationRecord>()
        val activeViolations = mutableMapOf<HOSViolationType, ViolationRecord>()

        // Analyze for Split Sleeper Berth pairs (§395.1(g)(ii))
        val splitSleeperAnalysis = splitSleeperDetector.analyze(sorted, now)
        val splitPairs = splitSleeperAnalysis.validPairs

        // State tracking
        var shiftStart: Long? = null
        var driveTimeInShift = 0L
        var driveTimeSinceBreak = 0L
        var consecutiveRestStart: Long? = null  // Track when consecutive rest started
        var splitSleeperExcludedTime = 0L       // Time excluded from 14h due to split sleeper

        // For cycle calculation
        val onDutyByDay = mutableMapOf<Long, Long>() // dayStart -> onDutyMs

        for (i in sorted.indices) {
            val event = sorted[i]
            val eventEnd = event.endTime ?: now
            val eventDuration = eventEnd - event.startTime

            when {
                event.isRestStatus() -> {
                    // Track start of consecutive rest period
                    if (consecutiveRestStart == null) {
                        consecutiveRestStart = event.startTime
                    }

                    val totalRestDuration = eventEnd - consecutiveRestStart!!

                    // NOTE: We do NOT check for SHIFT violation during rest!
                    // FMCSA 14-hour rule: "May not DRIVE beyond the 14th hour"
                    // If driver stopped driving before 14h and is resting, there's no violation.
                    // Violation only occurs if driver DRIVES after 14 hours.

                    // Check if 30-min break completed (ends BREAK_REQUIRED violation)
                    if (totalRestDuration >= BREAK_MINIMUM_MS) {
                        val breakViol = activeViolations[HOSViolationType.BREAK_REQUIRED]
                        if (breakViol != null) {
                            val breakCompletedAt = consecutiveRestStart!! + BREAK_MINIMUM_MS
                            breakViol.endTime = breakCompletedAt
                            violations.add(breakViol)
                            activeViolations.remove(HOSViolationType.BREAK_REQUIRED)
                        }
                        driveTimeSinceBreak = 0L
                    }

                    // Check if 10-hour rest completed (ends DRIVE/SHIFT violations, new shift)
                    if (totalRestDuration >= REST_PERIOD_MINIMUM_MS) {
                        val restCompletedAt = consecutiveRestStart!! + REST_PERIOD_MINIMUM_MS

                        // End drive violation
                        val driveViol = activeViolations[HOSViolationType.DRIVE_TIME_EXCEEDED]
                        if (driveViol != null) {
                            driveViol.endTime = restCompletedAt
                            violations.add(driveViol)
                            activeViolations.remove(HOSViolationType.DRIVE_TIME_EXCEEDED)
                        }

                        // End shift violation
                        val shiftViol = activeViolations[HOSViolationType.SHIFT_TIME_EXCEEDED]
                        if (shiftViol != null) {
                            shiftViol.endTime = restCompletedAt
                            violations.add(shiftViol)
                            activeViolations.remove(HOSViolationType.SHIFT_TIME_EXCEEDED)
                        }

                        // Reset for new shift
                        driveTimeInShift = 0L
                        driveTimeSinceBreak = 0L
                        shiftStart = null // Will be set when next on-duty/driving starts
                        splitSleeperExcludedTime = 0L
                    }

                    // SPLIT SLEEPER BERTH (§395.1(g)(ii)):
                    // Check if this rest period completes a split sleeper pair
                    for (pair in splitPairs) {
                        // Track excluded time for 14h calculation
                        val excludedFromThisEvent = pair.getExcludedTimeMs(event)
                        if (excludedFromThisEvent > 0) {
                            splitSleeperExcludedTime += excludedFromThisEvent
                        }

                        // Check if this completes the split pair
                        if (pair.secondPeriod.endTime == eventEnd) {
                            val completedAt = eventEnd

                            // End drive violation (split rest counts as reset)
                            val driveViol = activeViolations[HOSViolationType.DRIVE_TIME_EXCEEDED]
                            if (driveViol != null) {
                                driveViol.endTime = completedAt
                                violations.add(driveViol)
                                activeViolations.remove(HOSViolationType.DRIVE_TIME_EXCEEDED)
                            }

                            // End shift violation (split rest excludes time from 14h)
                            val shiftViol = activeViolations[HOSViolationType.SHIFT_TIME_EXCEEDED]
                            if (shiftViol != null) {
                                shiftViol.endTime = completedAt
                                violations.add(shiftViol)
                                activeViolations.remove(HOSViolationType.SHIFT_TIME_EXCEEDED)
                            }

                            // Reset for new calculation period
                            driveTimeInShift = 0L
                            driveTimeSinceBreak = 0L
                            shiftStart = null
                            splitSleeperExcludedTime = 0L
                        }
                    }

                    // Check if 34-hour restart completed (ends CYCLE violation)
                    if (totalRestDuration >= CYCLE_RESTART_MS) {
                        val restartAt = consecutiveRestStart!! + CYCLE_RESTART_MS

                        val cycleViol = activeViolations[HOSViolationType.CYCLE_TIME_EXCEEDED]
                        if (cycleViol != null) {
                            cycleViol.endTime = restartAt
                            violations.add(cycleViol)
                            activeViolations.remove(HOSViolationType.CYCLE_TIME_EXCEEDED)
                        }

                        // Clear cycle history for restart
                        onDutyByDay.clear()
                        splitSleeperExcludedTime = 0L  // Reset excluded time for new cycle
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

                    // Check for DRIVE_TIME_EXCEEDED violation
                    if (driveTimeInShift > DRIVE_LIMIT_MS) {
                        if (!activeViolations.containsKey(HOSViolationType.DRIVE_TIME_EXCEEDED)) {
                            val violationStart = calculateViolationStart(
                                event, driveTimeInShift - eventDuration, DRIVE_LIMIT_MS
                            )
                            activeViolations[HOSViolationType.DRIVE_TIME_EXCEEDED] = ViolationRecord(
                                type = HOSViolationType.DRIVE_TIME_EXCEEDED,
                                startTime = violationStart,
                                endTime = null
                            )
                        }
                    }

                    // Check for BREAK_REQUIRED violation
                    if (driveTimeSinceBreak > BREAK_REQUIRED_AFTER_MS) {
                        if (!activeViolations.containsKey(HOSViolationType.BREAK_REQUIRED)) {
                            val violationStart = calculateViolationStart(
                                event, driveTimeSinceBreak - eventDuration, BREAK_REQUIRED_AFTER_MS
                            )
                            activeViolations[HOSViolationType.BREAK_REQUIRED] = ViolationRecord(
                                type = HOSViolationType.BREAK_REQUIRED,
                                startTime = violationStart,
                                endTime = null
                            )
                        }
                    }

                    // Check for SHIFT_TIME_EXCEEDED violation
                    // SPLIT SLEEPER BERTH: Use adjusted duration (excluding split rest periods)
                    val currentShiftStart = shiftStart  // Capture for smart cast
                    if (currentShiftStart != null) {
                        val rawShiftDuration = eventEnd - currentShiftStart
                        // Subtract excluded time from split sleeper pairs
                        val adjustedShiftDuration = if (splitPairs.isNotEmpty()) {
                            splitSleeperDetector.calculateAdjustedShiftDuration(
                                events = sorted.filter { it.startTime >= currentShiftStart },
                                splitPairs = splitPairs,
                                shiftStartTime = currentShiftStart,
                                now = eventEnd
                            )
                        } else {
                            rawShiftDuration
                        }

                        if (adjustedShiftDuration > SHIFT_LIMIT_MS) {
                            if (!activeViolations.containsKey(HOSViolationType.SHIFT_TIME_EXCEEDED)) {
                                // Calculate actual violation start based on adjusted time
                                val violationStart = currentShiftStart + SHIFT_LIMIT_MS + splitSleeperExcludedTime
                                activeViolations[HOSViolationType.SHIFT_TIME_EXCEEDED] = ViolationRecord(
                                    type = HOSViolationType.SHIFT_TIME_EXCEEDED,
                                    startTime = violationStart,
                                    endTime = null
                                )
                            }
                        }
                    }

                    // Check for CYCLE_TIME_EXCEEDED violation
                    val cycleHours = calculateCycleHours(onDutyByDay, eventEnd)
                    if (cycleHours > cycleLimitMs) {
                        if (!activeViolations.containsKey(HOSViolationType.CYCLE_TIME_EXCEEDED)) {
                            // Approximate start time
                            val overBy = cycleHours - cycleLimitMs
                            val violationStart = eventEnd - overBy
                            activeViolations[HOSViolationType.CYCLE_TIME_EXCEEDED] = ViolationRecord(
                                type = HOSViolationType.CYCLE_TIME_EXCEEDED,
                                startTime = violationStart,
                                endTime = null
                            )
                        }
                    }
                }

                event.isOnDutyNotDriving() -> {
                    // End consecutive rest period
                    consecutiveRestStart = null

                    // Start new shift if needed
                    if (shiftStart == null) {
                        shiftStart = event.startTime
                    }

                    // Track on-duty for cycle
                    addOnDutyToDay(onDutyByDay, event.startTime, eventDuration)

                    // FMCSA §395.3(a)(3)(ii): ON_DUTY_NOT_DRIVING qualifies for 30-minute break!
                    // "the 30-minute rest break can be satisfied by off duty, sleeper berth,
                    // on duty (not driving), or any combination of the three"
                    if (eventDuration >= BREAK_MINIMUM_MS) {
                        // End any active BREAK_REQUIRED violation
                        val breakViol = activeViolations[HOSViolationType.BREAK_REQUIRED]
                        if (breakViol != null) {
                            val breakCompletedAt = event.startTime + BREAK_MINIMUM_MS
                            breakViol.endTime = breakCompletedAt
                            violations.add(breakViol)
                            activeViolations.remove(HOSViolationType.BREAK_REQUIRED)
                        }
                        driveTimeSinceBreak = 0L
                    }

                    // NOTE: We do NOT check for SHIFT violation during ON_DUTY_NOT_DRIVING!
                    // FMCSA 14-hour rule: "May not DRIVE beyond the 14th hour"
                    // Driver can be ON_DUTY (loading, paperwork, etc.) after 14 hours without violation.
                    // They just cannot DRIVE until they take 10 hours of rest.

                    // Check for CYCLE_TIME_EXCEEDED violation
                    val cycleHours = calculateCycleHours(onDutyByDay, eventEnd)
                    if (cycleHours > cycleLimitMs) {
                        if (!activeViolations.containsKey(HOSViolationType.CYCLE_TIME_EXCEEDED)) {
                            val overBy = cycleHours - cycleLimitMs
                            val violationStart = eventEnd - overBy
                            activeViolations[HOSViolationType.CYCLE_TIME_EXCEEDED] = ViolationRecord(
                                type = HOSViolationType.CYCLE_TIME_EXCEEDED,
                                startTime = violationStart,
                                endTime = null
                            )
                        }
                    }
                }
            }

            // Check for cycle rolloff at day boundaries
            checkCycleRolloff(event, onDutyByDay, activeViolations, violations, now)
        }

        // Add any still-active violations
        for (violation in activeViolations.values) {
            violations.add(violation)
        }

        return violations
    }

    /**
     * Calculate the exact time when a violation started.
     */
    private fun calculateViolationStart(
        event: DutyStatusEventEntity,
        timeBefore: Long,
        limit: Long
    ): Long {
        // How much of this event pushed us over the limit?
        val timeNeeded = limit - timeBefore
        return event.startTime + timeNeeded
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
     * Calculate total on-duty time in the rolling cycle window.
     */
    private fun calculateCycleHours(
        onDutyByDay: Map<Long, Long>,
        now: Long
    ): Long {
        val windowStart = now - (cycleRule.days * MS_PER_DAY)
        return onDutyByDay.entries
            .filter { it.key >= windowStart }
            .sumOf { it.value }
    }

    /**
     * Check if hours "falling off" the cycle window end a cycle violation.
     */
    private fun checkCycleRolloff(
        event: DutyStatusEventEntity,
        onDutyByDay: MutableMap<Long, Long>,
        activeViolations: MutableMap<HOSViolationType, ViolationRecord>,
        violations: MutableList<ViolationRecord>,
        now: Long
    ) {
        val cycleViol = activeViolations[HOSViolationType.CYCLE_TIME_EXCEEDED] ?: return

        // Calculate cycle hours at this point
        val eventEnd = event.endTime ?: now
        val cycleHours = calculateCycleHours(onDutyByDay, eventEnd)

        // If under limit now, violation ended due to rolloff
        if (cycleHours <= cycleLimitMs) {
            // Find when it went under (at midnight of some day)
            val windowStart = eventEnd - (cycleRule.days * MS_PER_DAY)
            val rolloffDay = getDayStart(windowStart) + MS_PER_DAY // Midnight when oldest day fell off

            cycleViol.endTime = rolloffDay
            violations.add(cycleViol)
            activeViolations.remove(HOSViolationType.CYCLE_TIME_EXCEEDED)
        }
    }

    private fun getDayStart(timestamp: Long): Long {
        return (timestamp / MS_PER_DAY) * MS_PER_DAY
    }

    private fun DutyStatusEventEntity.isRestStatus(): Boolean {
        return dutyStatus == DutyStatusType.OFF_DUTY.name ||
                dutyStatus == DutyStatusType.SLEEPER_BERTH.name
    }

    private fun DutyStatusEventEntity.isDrivingStatus(): Boolean {
        return dutyStatus == DutyStatusType.DRIVING.name
    }

    private fun DutyStatusEventEntity.isOnDutyNotDriving(): Boolean {
        return dutyStatus == DutyStatusType.ON_DUTY_NOT_DRIVING.name ||
                dutyStatus == DutyStatusType.YARD_MOVE.name
    }
}

/**
 * Represents a detected violation with start and end times.
 */
data class ViolationRecord(
    val type: HOSViolationType,
    val startTime: Long,
    var endTime: Long?,       // null if still active
    val vehicleId: Int? = null,

    // Sync tracking
    var serverId: Int? = null,
    var isSynced: Boolean = false
) {
    val isActive: Boolean get() = endTime == null

    fun toIsoStartTime(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(startTime))
    }

    fun toIsoEndTime(): String? {
        return endTime?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date(it))
        }
    }
}
