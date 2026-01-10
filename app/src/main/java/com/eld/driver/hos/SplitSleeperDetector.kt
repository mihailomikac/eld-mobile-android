package com.eld.driver.hos

import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.eld.driver.data.models.DutyStatusType

/**
 * SplitSleeperDetector - Detects valid Split Sleeper Berth pairs per FMCSA §395.1(g)(ii).
 *
 * SPLIT SLEEPER BERTH RULE:
 * Instead of taking 10 consecutive hours off duty, a driver may split the sleeper berth time
 * into two periods:
 *
 * Period A: At least 7 CONSECUTIVE hours in the SLEEPER BERTH
 * Period B: At least 2 hours (can be Sleeper Berth, Off Duty, or Personal Conveyance)
 *
 * Total of both periods must be at least 10 hours.
 *
 * KEY BENEFIT: Both periods are EXCLUDED from the 14-hour driving window calculation!
 *
 * When a valid split is completed:
 * 1. Both rest periods are excluded from 14-hour calculation
 * 2. The new calculation point (CP) starts at the END of the FIRST period in the pair
 * 3. Drive time and shift time are recalculated from that point
 *
 * Reference: FMCSA HOS Examples 12-18 (2022-04-28)
 */
class SplitSleeperDetector {
    companion object {
        // Split Sleeper Requirements
        const val PERIOD_A_MINIMUM_MS = 7L * 60 * 60 * 1000  // 7 hours minimum in SB
        const val PERIOD_B_MINIMUM_MS = 2L * 60 * 60 * 1000  // 2 hours minimum
        const val TOTAL_MINIMUM_MS = 10L * 60 * 60 * 1000    // 10 hours total

        // Standard rest requirements (for reference)
        const val STANDARD_REST_MS = 10L * 60 * 60 * 1000    // 10 consecutive hours
    }

    /**
     * Represents a rest period that could be part of a split sleeper pair.
     */
    data class RestPeriod(
        val startTime: Long,
        val endTime: Long,
        val durationMs: Long,
        val isSleeperBerth: Boolean,  // true = SLEEPER_BERTH only, false = OFF_DUTY/PC
        val eventIds: List<String>     // IDs of events that make up this period
    ) {
        val canBePeriodA: Boolean get() = isSleeperBerth && durationMs >= PERIOD_A_MINIMUM_MS
        val canBePeriodB: Boolean get() = durationMs >= PERIOD_B_MINIMUM_MS

        fun overlapsWith(other: RestPeriod): Boolean {
            return startTime < other.endTime && endTime > other.startTime
        }
    }

    /**
     * Represents a valid split sleeper pair (Period A + Period B).
     */
    data class SplitSleeperPair(
        val periodA: RestPeriod,       // The 7+ hour sleeper berth period
        val periodB: RestPeriod,       // The 2+ hour rest period
        val totalDurationMs: Long,
        val calculationPointStart: Long  // New CP starts at end of FIRST period chronologically
    ) {
        val firstPeriod: RestPeriod get() = if (periodA.startTime < periodB.startTime) periodA else periodB
        val secondPeriod: RestPeriod get() = if (periodA.startTime < periodB.startTime) periodB else periodA

        /**
         * Check if an event falls within this split sleeper pair's excluded time.
         */
        fun isEventExcluded(event: DutyStatusEventEntity): Boolean {
            val eventStart = event.startTime
            val eventEnd = event.endTime ?: System.currentTimeMillis()

            // Check if event overlaps with Period A
            val inPeriodA = eventStart < periodA.endTime && eventEnd > periodA.startTime
            // Check if event overlaps with Period B
            val inPeriodB = eventStart < periodB.endTime && eventEnd > periodB.startTime

            return inPeriodA || inPeriodB
        }

        /**
         * Get the portion of time that should be excluded from 14h calculation for a given event.
         */
        fun getExcludedTimeMs(event: DutyStatusEventEntity): Long {
            val eventStart = event.startTime
            val eventEnd = event.endTime ?: System.currentTimeMillis()

            var excludedTime = 0L

            // Calculate overlap with Period A
            val overlapAStart = maxOf(eventStart, periodA.startTime)
            val overlapAEnd = minOf(eventEnd, periodA.endTime)
            if (overlapAStart < overlapAEnd) {
                excludedTime += overlapAEnd - overlapAStart
            }

            // Calculate overlap with Period B
            val overlapBStart = maxOf(eventStart, periodB.startTime)
            val overlapBEnd = minOf(eventEnd, periodB.endTime)
            if (overlapBStart < overlapBEnd) {
                excludedTime += overlapBEnd - overlapBStart
            }

            return excludedTime
        }
    }

    /**
     * Result of split sleeper analysis for a set of events.
     */
    data class SplitSleeperAnalysis(
        val validPairs: List<SplitSleeperPair>,
        val restPeriods: List<RestPeriod>,
        val hasActiveSplit: Boolean,  // Is there an incomplete split in progress?
        val pendingPeriodA: RestPeriod?  // A 7+ hour SB period waiting for Period B
    )

    /**
     * Analyze events and find all valid split sleeper pairs.
     *
     * @param events List of duty status events to analyze
     * @param now Current timestamp for calculating active periods
     * @return Analysis result with all valid pairs found
     */
    fun analyze(
        events: List<DutyStatusEventEntity>,
        now: Long = System.currentTimeMillis()
    ): SplitSleeperAnalysis {
        if (events.isEmpty()) {
            return SplitSleeperAnalysis(
                validPairs = emptyList(),
                restPeriods = emptyList(),
                hasActiveSplit = false,
                pendingPeriodA = null
            )
        }

        val sorted = events.sortedBy { it.startTime }

        // Step 1: Identify all rest periods (consecutive OFF_DUTY/SB/PC blocks)
        val restPeriods = findRestPeriods(sorted, now)

        // Step 2: Find all valid split pairs
        val validPairs = findValidPairs(restPeriods)

        // Step 3: Check for pending Period A (7+ hours SB waiting for Period B)
        val pendingPeriodA = findPendingPeriodA(restPeriods, validPairs)

        return SplitSleeperAnalysis(
            validPairs = validPairs,
            restPeriods = restPeriods,
            hasActiveSplit = pendingPeriodA != null,
            pendingPeriodA = pendingPeriodA
        )
    }

    /**
     * Find all rest periods from events.
     * Consecutive OFF_DUTY, SLEEPER_BERTH, or PERSONAL_CONVEYANCE events are merged.
     */
    private fun findRestPeriods(
        events: List<DutyStatusEventEntity>,
        now: Long
    ): List<RestPeriod> {
        val restPeriods = mutableListOf<RestPeriod>()

        var currentRestStart: Long? = null
        var currentRestEnd: Long = 0
        var isCurrentSleeperBerth = false  // Track if entire period is SB only
        var hasSleeperBerth = false        // Track if period contains any SB
        var currentEventIds = mutableListOf<String>()

        for (event in events) {
            val eventEnd = event.endTime ?: now
            val isRest = event.isQualifyingRestForSplitSleeper()
            val isSB = event.isSleeperBerthStatus()

            if (isRest) {
                if (currentRestStart == null) {
                    // Start new rest period
                    currentRestStart = event.startTime
                    isCurrentSleeperBerth = isSB
                    hasSleeperBerth = isSB
                    currentEventIds = mutableListOf(event.id)
                } else {
                    // Continue rest period
                    currentEventIds.add(event.id)
                    if (isSB) hasSleeperBerth = true
                    // If we mix SB with OFF_DUTY, it's no longer a "pure" SB period
                    if (!isSB) isCurrentSleeperBerth = false
                }
                currentRestEnd = eventEnd
            } else {
                // Non-rest event - finalize current rest period if exists
                if (currentRestStart != null) {
                    val duration = currentRestEnd - currentRestStart
                    restPeriods.add(
                        RestPeriod(
                            startTime = currentRestStart,
                            endTime = currentRestEnd,
                            durationMs = duration,
                            isSleeperBerth = isCurrentSleeperBerth,
                            eventIds = currentEventIds.toList()
                        )
                    )

                    currentRestStart = null
                    currentEventIds.clear()
                }
            }
        }

        // Don't forget the last rest period if still active
        if (currentRestStart != null) {
            val duration = currentRestEnd - currentRestStart
            restPeriods.add(
                RestPeriod(
                    startTime = currentRestStart,
                    endTime = currentRestEnd,
                    durationMs = duration,
                    isSleeperBerth = isCurrentSleeperBerth,
                    eventIds = currentEventIds.toList()
                )
            )
        }

        return restPeriods
    }

    /**
     * Find all valid split sleeper pairs from rest periods.
     *
     * Rules:
     * - Period A must be at least 7 consecutive hours in SLEEPER_BERTH only
     * - Period B must be at least 2 hours (can be SB, OFF_DUTY, or PC)
     * - Total must be at least 10 hours
     * - Periods can be in any order (A before B, or B before A)
     */
    private fun findValidPairs(restPeriods: List<RestPeriod>): List<SplitSleeperPair> {
        val validPairs = mutableListOf<SplitSleeperPair>()
        val usedPeriods = mutableSetOf<RestPeriod>()

        // Find all Period A candidates (7+ hours in SB)
        val periodACandidates = restPeriods.filter { it.canBePeriodA }

        for (periodA in periodACandidates) {
            if (usedPeriods.contains(periodA)) continue

            // Find matching Period B
            // Period B can be before or after Period A, but should be "reasonably close"
            // (within the same calculation window, typically within 24-48 hours)
            val periodBCandidates = restPeriods.filter { candidate ->
                candidate != periodA &&
                !usedPeriods.contains(candidate) &&
                candidate.canBePeriodB &&
                !candidate.overlapsWith(periodA) &&
                isWithinReasonableWindow(periodA, candidate)
            }

            // Find the best matching Period B (closest in time, preferably adjacent)
            val bestPeriodB = periodBCandidates
                .sortedBy { kotlin.math.abs(it.startTime - periodA.endTime) }
                .firstOrNull { periodA.durationMs + it.durationMs >= TOTAL_MINIMUM_MS }

            if (bestPeriodB != null) {
                val totalDuration = periodA.durationMs + bestPeriodB.durationMs

                // Determine calculation point (end of FIRST period chronologically)
                val firstPeriod = if (periodA.startTime < bestPeriodB.startTime) periodA else bestPeriodB
                val calculationPointStart = firstPeriod.endTime

                val pair = SplitSleeperPair(
                    periodA = periodA,
                    periodB = bestPeriodB,
                    totalDurationMs = totalDuration,
                    calculationPointStart = calculationPointStart
                )

                validPairs.add(pair)
                usedPeriods.add(periodA)
                usedPeriods.add(bestPeriodB)
            }
        }

        return validPairs.sortedBy { it.firstPeriod.startTime }
    }

    /**
     * Check if two rest periods are within a reasonable window to be paired.
     * Per FMCSA, the periods should be within the same "calculation period".
     */
    private fun isWithinReasonableWindow(periodA: RestPeriod, periodB: RestPeriod): Boolean {
        // Periods should be within 24 hours of each other
        // This is a reasonable interpretation based on FMCSA examples
        val maxGapMs = 24L * 60 * 60 * 1000

        val gap = if (periodA.endTime <= periodB.startTime) {
            periodB.startTime - periodA.endTime
        } else if (periodB.endTime <= periodA.startTime) {
            periodA.startTime - periodB.endTime
        } else {
            0L // Overlapping
        }

        return gap <= maxGapMs
    }

    /**
     * Find a pending Period A that hasn't been paired yet.
     * This is useful for UI to show "you need X more hours to complete split".
     */
    private fun findPendingPeriodA(
        restPeriods: List<RestPeriod>,
        validPairs: List<SplitSleeperPair>
    ): RestPeriod? {
        val usedPeriods = validPairs.flatMap { listOf(it.periodA, it.periodB) }.toSet()

        return restPeriods
            .filter { it.canBePeriodA && !usedPeriods.contains(it) }
            .maxByOrNull { it.endTime }  // Most recent unpaired Period A
    }

    /**
     * Calculate adjusted 14-hour window considering split sleeper exclusions.
     *
     * @param events All events in the calculation window
     * @param splitPairs Valid split sleeper pairs
     * @param shiftStartTime When the current shift started
     * @param now Current time
     * @return Adjusted shift duration (excluding split sleeper rest periods)
     */
    fun calculateAdjustedShiftDuration(
        events: List<DutyStatusEventEntity>,
        splitPairs: List<SplitSleeperPair>,
        shiftStartTime: Long,
        now: Long
    ): Long {
        if (splitPairs.isEmpty()) {
            // No split sleeper - standard calculation
            return now - shiftStartTime
        }

        var totalExcludedTime = 0L

        for (pair in splitPairs) {
            // Only count exclusions that fall within our shift window
            if (pair.periodA.startTime >= shiftStartTime || pair.periodB.startTime >= shiftStartTime) {
                // Calculate how much of each period is within our shift
                val periodAExcluded = calculateExcludedPortion(pair.periodA, shiftStartTime, now)
                val periodBExcluded = calculateExcludedPortion(pair.periodB, shiftStartTime, now)
                totalExcludedTime += periodAExcluded + periodBExcluded
            }
        }

        val rawDuration = now - shiftStartTime
        val adjustedDuration = rawDuration - totalExcludedTime

        return maxOf(0L, adjustedDuration)
    }

    /**
     * Calculate the portion of a rest period that falls within a time window.
     */
    private fun calculateExcludedPortion(
        period: RestPeriod,
        windowStart: Long,
        windowEnd: Long
    ): Long {
        val overlapStart = maxOf(period.startTime, windowStart)
        val overlapEnd = minOf(period.endTime, windowEnd)

        return if (overlapStart < overlapEnd) {
            overlapEnd - overlapStart
        } else {
            0L
        }
    }

    /**
     * Get the new calculation point after a split sleeper pair is completed.
     * Per FMCSA: "begin recalculating HOS compliance at the end of the first period used to pair"
     */
    fun getNewCalculationPoint(pair: SplitSleeperPair): Long {
        return pair.calculationPointStart
    }

    /**
     * Check if an event should be excluded from 14-hour calculation.
     */
    fun isEventExcludedFrom14Hour(
        event: DutyStatusEventEntity,
        splitPairs: List<SplitSleeperPair>
    ): Boolean {
        return splitPairs.any { it.isEventExcluded(event) }
    }

    /**
     * Extension to check if event qualifies for split sleeper rest.
     */
    private fun DutyStatusEventEntity.isQualifyingRestForSplitSleeper(): Boolean {
        return dutyStatus == DutyStatusType.OFF_DUTY.name ||
               dutyStatus == DutyStatusType.SLEEPER_BERTH.name ||
               dutyStatus == DutyStatusType.PERSONAL_CONVEYANCE.name
    }
}
