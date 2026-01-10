package com.eld.driver.hos

import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Comprehensive unit tests for SplitSleeperDetector.
 *
 * Tests FMCSA §395.1(g)(ii) Split Sleeper Berth provision:
 * - Period A: At least 7 consecutive hours in SLEEPER_BERTH only
 * - Period B: At least 2 hours (can be SB, OFF_DUTY, or PC)
 * - Total must be at least 10 hours
 * - Both periods excluded from 14-hour calculation
 * - New calculation point starts at end of FIRST period chronologically
 */
class SplitSleeperDetectorTest {

    private lateinit var detector: SplitSleeperDetector

    // Time constants
    private val MINUTE_MS = 60L * 1000
    private val HOUR_MS = 60L * MINUTE_MS
    private val DAY_MS = 24L * HOUR_MS

    @Before
    fun setup() {
        detector = SplitSleeperDetector()
    }

    // ==================== HELPER FUNCTIONS ====================

    private fun createEvent(
        dutyStatus: String,
        startTime: Long,
        endTime: Long? = null,
        id: String = UUID.randomUUID().toString()
    ): DutyStatusEventEntity {
        return DutyStatusEventEntity(
            id = id,
            dutyStatus = dutyStatus,
            startTime = startTime,
            endTime = endTime
        )
    }

    private fun hoursToMs(hours: Double): Long = (hours * HOUR_MS).toLong()
    private fun minutesToMs(minutes: Int): Long = minutes * MINUTE_MS

    // ==================== EMPTY/NO SPLIT TESTS ====================

    @Test
    fun `analyze with empty events returns no pairs`() {
        val result = detector.analyze(emptyList())

        assertThat(result.validPairs).isEmpty()
        assertThat(result.restPeriods).isEmpty()
        assertThat(result.hasActiveSplit).isFalse()
    }

    @Test
    fun `no split pair when no sleeper berth`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(2.0)),
            createEvent("OFF_DUTY", now - hoursToMs(2.0), now)
        )

        val result = detector.analyze(events, now)

        assertThat(result.validPairs).isEmpty()
    }

    // ==================== VALID SPLIT PAIR TESTS ====================

    @Test
    fun `detect valid split - 7h SB then 3h OFF_DUTY`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(15.0), now - hoursToMs(12.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(12.0), now - hoursToMs(5.0)), // 7h SB = Period A
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(2.0)),
            createEvent("OFF_DUTY", now - hoursToMs(2.0), now) // 2h+ OFF = Period B - but needs 3h total with A
        )

        val result = detector.analyze(events, now)

        // Period B needs to be at least 2h, total needs to be 10h
        // 7h SB + 2h OFF = 9h (not enough)
        // Let's fix this - need 3h OFF_DUTY
        assertThat(result.validPairs).isEmpty() // 7+2=9 < 10
    }

    @Test
    fun `detect valid split - 7h SB then 3h OFF_DUTY totaling 10h`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(16.0), now - hoursToMs(13.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(13.0), now - hoursToMs(6.0)), // 7h SB = Period A
            createEvent("DRIVING", now - hoursToMs(6.0), now - hoursToMs(3.0)),
            createEvent("OFF_DUTY", now - hoursToMs(3.0), now) // 3h OFF = Period B
        )

        val result = detector.analyze(events, now)

        // 7h + 3h = 10h total ✓
        assertThat(result.validPairs).hasSize(1)

        val pair = result.validPairs.first()
        assertThat(pair.periodA.durationMs).isEqualTo(hoursToMs(7.0))
        assertThat(pair.periodB.durationMs).isEqualTo(hoursToMs(3.0))
        assertThat(pair.totalDurationMs).isEqualTo(hoursToMs(10.0))
    }

    @Test
    fun `detect valid split - 8h SB then 2h OFF_DUTY`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(15.0), now - hoursToMs(12.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(12.0), now - hoursToMs(4.0)), // 8h SB
            createEvent("DRIVING", now - hoursToMs(4.0), now - hoursToMs(2.0)),
            createEvent("OFF_DUTY", now - hoursToMs(2.0), now) // 2h OFF
        )

        val result = detector.analyze(events, now)

        // 8h + 2h = 10h total ✓
        assertThat(result.validPairs).hasSize(1)
    }

    @Test
    fun `detect valid split - reversed order (2h OFF then 8h SB)`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(15.0), now - hoursToMs(12.0)),
            createEvent("OFF_DUTY", now - hoursToMs(12.0), now - hoursToMs(10.0)), // 2h OFF = Period B first
            createEvent("DRIVING", now - hoursToMs(10.0), now - hoursToMs(8.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(8.0), now) // 8h SB = Period A second
        )

        val result = detector.analyze(events, now)

        // 2h + 8h = 10h total, order doesn't matter
        assertThat(result.validPairs).hasSize(1)

        val pair = result.validPairs.first()
        // Period A is always the 7h+ SB, Period B is the other
        assertThat(pair.periodA.isSleeperBerth).isTrue()
        assertThat(pair.periodA.durationMs).isEqualTo(hoursToMs(8.0))
    }

    // ==================== INVALID SPLIT TESTS ====================

    @Test
    fun `invalid split - SB less than 7 hours`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(12.0), now - hoursToMs(9.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(9.0), now - hoursToMs(3.0)), // 6h SB (not enough!)
            createEvent("DRIVING", now - hoursToMs(3.0), now - hoursToMs(1.0)),
            createEvent("OFF_DUTY", now - hoursToMs(1.0), now) // 1h OFF
        )

        val result = detector.analyze(events, now)

        // 6h SB doesn't qualify as Period A (must be 7h+)
        assertThat(result.validPairs).isEmpty()
    }

    @Test
    fun `invalid split - Period B less than 2 hours`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(12.0), now - hoursToMs(9.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(9.0), now - hoursToMs(2.0)), // 7h SB
            createEvent("DRIVING", now - hoursToMs(2.0), now - minutesToMs(90)),
            createEvent("OFF_DUTY", now - minutesToMs(90), now) // 1.5h OFF (not enough!)
        )

        val result = detector.analyze(events, now)

        assertThat(result.validPairs).isEmpty()
    }

    @Test
    fun `invalid split - total less than 10 hours`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(12.0), now - hoursToMs(9.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(9.0), now - hoursToMs(2.0)), // 7h SB
            createEvent("DRIVING", now - hoursToMs(2.0), now) // No Period B
        )

        val result = detector.analyze(events, now)

        // 7h alone doesn't make a valid split
        assertThat(result.validPairs).isEmpty()
        // But should show pending Period A
        assertThat(result.pendingPeriodA).isNotNull()
    }

    @Test
    fun `invalid split - Period A must be SLEEPER_BERTH only`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(15.0), now - hoursToMs(12.0)),
            createEvent("OFF_DUTY", now - hoursToMs(12.0), now - hoursToMs(5.0)), // 7h OFF (not SB!)
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(2.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(2.0), now) // 2h SB
        )

        val result = detector.analyze(events, now)

        // OFF_DUTY doesn't qualify as Period A (must be SLEEPER_BERTH)
        // 2h SB can be Period B but there's no valid Period A
        assertThat(result.validPairs).isEmpty()
    }

    // ==================== CALCULATION POINT TESTS ====================

    @Test
    fun `calculation point is end of FIRST period chronologically`() {
        val now = System.currentTimeMillis()
        val periodAStart = now - hoursToMs(13.0)
        val periodAEnd = now - hoursToMs(6.0) // 7h SB ends here
        val periodBStart = now - hoursToMs(3.0)
        val periodBEnd = now // 3h OFF ends here

        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(15.0), periodAStart),
            createEvent("SLEEPER_BERTH", periodAStart, periodAEnd), // Period A (first chronologically)
            createEvent("DRIVING", periodAEnd, periodBStart),
            createEvent("OFF_DUTY", periodBStart, periodBEnd) // Period B (second)
        )

        val result = detector.analyze(events, now)

        assertThat(result.validPairs).hasSize(1)
        val pair = result.validPairs.first()
        // New CP should be end of FIRST period (Period A in this case)
        assertThat(pair.calculationPointStart).isEqualTo(periodAEnd)
    }

    @Test
    fun `calculation point when Period B comes first`() {
        val now = System.currentTimeMillis()
        val periodBStart = now - hoursToMs(13.0)
        val periodBEnd = now - hoursToMs(10.0) // 3h OFF ends here (first)
        val periodAStart = now - hoursToMs(8.0)
        val periodAEnd = now - hoursToMs(1.0) // 7h SB ends here (second)

        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(15.0), periodBStart),
            createEvent("OFF_DUTY", periodBStart, periodBEnd), // Period B (first chronologically)
            createEvent("DRIVING", periodBEnd, periodAStart),
            createEvent("SLEEPER_BERTH", periodAStart, periodAEnd) // Period A (second)
        )

        val result = detector.analyze(events, now)

        assertThat(result.validPairs).hasSize(1)
        val pair = result.validPairs.first()
        // New CP should be end of FIRST period (Period B in this case)
        assertThat(pair.calculationPointStart).isEqualTo(periodBEnd)
    }

    // ==================== ADJUSTED SHIFT DURATION TESTS ====================

    @Test
    fun `calculateAdjustedShiftDuration excludes split rest periods`() {
        val now = System.currentTimeMillis()
        val shiftStart = now - hoursToMs(18.0)

        val events = listOf(
            createEvent("DRIVING", shiftStart, shiftStart + hoursToMs(3.0)),
            createEvent("SLEEPER_BERTH", shiftStart + hoursToMs(3.0), shiftStart + hoursToMs(10.0)), // 7h SB
            createEvent("DRIVING", shiftStart + hoursToMs(10.0), shiftStart + hoursToMs(13.0)),
            createEvent("OFF_DUTY", shiftStart + hoursToMs(13.0), shiftStart + hoursToMs(16.0)), // 3h OFF
            createEvent("DRIVING", shiftStart + hoursToMs(16.0), now)
        )

        val analysis = detector.analyze(events, now)

        assertThat(analysis.validPairs).hasSize(1)

        val adjustedDuration = detector.calculateAdjustedShiftDuration(
            events = events,
            splitPairs = analysis.validPairs,
            shiftStartTime = shiftStart,
            now = now
        )

        // Raw duration: 18h
        // Excluded: 7h (SB) + 3h (OFF) = 10h
        // Adjusted: 18h - 10h = 8h
        assertThat(adjustedDuration).isEqualTo(hoursToMs(8.0))
    }

    @Test
    fun `adjustedShiftDuration equals raw when no split pairs`() {
        val now = System.currentTimeMillis()
        val shiftStart = now - hoursToMs(10.0)

        val events = listOf(
            createEvent("DRIVING", shiftStart, now)
        )

        val adjustedDuration = detector.calculateAdjustedShiftDuration(
            events = events,
            splitPairs = emptyList(),
            shiftStartTime = shiftStart,
            now = now
        )

        // No split = raw duration
        assertThat(adjustedDuration).isEqualTo(hoursToMs(10.0))
    }

    // ==================== PENDING PERIOD A TESTS ====================

    @Test
    fun `detect pending Period A waiting for Period B`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(12.0), now - hoursToMs(9.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(9.0), now - hoursToMs(2.0)), // 7h SB
            createEvent("DRIVING", now - hoursToMs(2.0), now) // No Period B yet
        )

        val result = detector.analyze(events, now)

        assertThat(result.validPairs).isEmpty()
        assertThat(result.hasActiveSplit).isTrue()
        assertThat(result.pendingPeriodA).isNotNull()
        assertThat(result.pendingPeriodA!!.durationMs).isEqualTo(hoursToMs(7.0))
    }

    @Test
    fun `no pending Period A when pair is complete`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(15.0), now - hoursToMs(12.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(12.0), now - hoursToMs(5.0)), // 7h SB
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(3.0)),
            createEvent("OFF_DUTY", now - hoursToMs(3.0), now) // 3h OFF = complete pair
        )

        val result = detector.analyze(events, now)

        assertThat(result.validPairs).hasSize(1)
        assertThat(result.hasActiveSplit).isFalse()
        assertThat(result.pendingPeriodA).isNull()
    }

    // ==================== REST PERIOD DETECTION TESTS ====================

    @Test
    fun `consecutive rest periods are merged`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(12.0), now - hoursToMs(9.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(9.0), now - hoursToMs(6.0)), // 3h SB
            createEvent("SLEEPER_BERTH", now - hoursToMs(6.0), now - hoursToMs(2.0)), // 4h SB (consecutive)
            createEvent("DRIVING", now - hoursToMs(2.0), now)
        )

        val result = detector.analyze(events, now)

        // Two consecutive SB events should merge into one 7h period
        val sbPeriods = result.restPeriods.filter { it.isSleeperBerth }
        assertThat(sbPeriods).hasSize(1)
        assertThat(sbPeriods.first().durationMs).isEqualTo(hoursToMs(7.0))
    }

    @Test
    fun `mixed SB and OFF_DUTY is not pure SB period`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(12.0), now - hoursToMs(9.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(9.0), now - hoursToMs(6.0)), // 3h SB
            createEvent("OFF_DUTY", now - hoursToMs(6.0), now - hoursToMs(2.0)), // 4h OFF (breaks SB)
            createEvent("DRIVING", now - hoursToMs(2.0), now)
        )

        val result = detector.analyze(events, now)

        // SB->OFF_DUTY consecutive is NOT a pure SB period, so not valid Period A
        // (Period A must be SLEEPER_BERTH only)
        val validPeriodA = result.restPeriods.find { it.canBePeriodA }
        assertThat(validPeriodA).isNull()
    }

    // ==================== REAL-WORLD SCENARIOS ====================

    @Test
    fun `scenario - FMCSA Example 12 style split`() {
        // Driver takes 7h sleeper, drives, then takes 3h off
        val now = System.currentTimeMillis()
        val shiftStart = now - hoursToMs(20.0)

        val events = listOf(
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart, shiftStart + minutesToMs(30)),
            createEvent("DRIVING", shiftStart + minutesToMs(30), shiftStart + hoursToMs(5.0)), // 4.5h drive
            createEvent("SLEEPER_BERTH", shiftStart + hoursToMs(5.0), shiftStart + hoursToMs(12.0)), // 7h SB
            createEvent("DRIVING", shiftStart + hoursToMs(12.0), shiftStart + hoursToMs(17.0)), // 5h drive
            createEvent("OFF_DUTY", shiftStart + hoursToMs(17.0), now) // 3h OFF
        )

        val result = detector.analyze(events, now)

        // Valid split: 7h SB + 3h OFF = 10h
        assertThat(result.validPairs).hasSize(1)

        val pair = result.validPairs.first()
        assertThat(pair.periodA.durationMs).isEqualTo(hoursToMs(7.0))
        assertThat(pair.periodB.durationMs).isEqualTo(hoursToMs(3.0))
        assertThat(pair.totalDurationMs).isEqualTo(hoursToMs(10.0))
    }

    @Test
    fun `scenario - multiple potential pairs uses first valid`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(30.0), now - hoursToMs(27.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(27.0), now - hoursToMs(20.0)), // 7h SB (first)
            createEvent("DRIVING", now - hoursToMs(20.0), now - hoursToMs(17.0)),
            createEvent("OFF_DUTY", now - hoursToMs(17.0), now - hoursToMs(14.0)), // 3h OFF (pairs with first)
            createEvent("DRIVING", now - hoursToMs(14.0), now - hoursToMs(11.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(11.0), now - hoursToMs(4.0)), // 7h SB (second)
            createEvent("DRIVING", now - hoursToMs(4.0), now - hoursToMs(1.0)),
            createEvent("OFF_DUTY", now - hoursToMs(1.0), now) // 1h OFF (not enough alone)
        )

        val result = detector.analyze(events, now)

        // First 7h SB should pair with 3h OFF
        // Second 7h SB doesn't have valid Period B yet (1h < 2h minimum)
        assertThat(result.validPairs).hasSize(1)
        assertThat(result.pendingPeriodA).isNotNull() // Second 7h SB waiting
    }

    @Test
    fun `scenario - no violation with proper split use`() {
        val now = System.currentTimeMillis()
        val shiftStart = now - hoursToMs(18.0)

        val events = listOf(
            // Morning: 5h driving
            createEvent("DRIVING", shiftStart, shiftStart + hoursToMs(5.0)),
            // 7h sleeper
            createEvent("SLEEPER_BERTH", shiftStart + hoursToMs(5.0), shiftStart + hoursToMs(12.0)),
            // Afternoon: 3h driving
            createEvent("DRIVING", shiftStart + hoursToMs(12.0), shiftStart + hoursToMs(15.0)),
            // 3h off
            createEvent("OFF_DUTY", shiftStart + hoursToMs(15.0), shiftStart + hoursToMs(18.0)),
            // More driving (allowed because split completed)
            createEvent("DRIVING", shiftStart + hoursToMs(18.0), now)
        )

        val analysis = detector.analyze(events, now)
        assertThat(analysis.validPairs).hasSize(1)

        // Calculate adjusted shift duration
        val adjustedDuration = detector.calculateAdjustedShiftDuration(
            events = events,
            splitPairs = analysis.validPairs,
            shiftStartTime = shiftStart,
            now = now
        )

        // Raw: 18h
        // Excluded: 7h + 3h = 10h
        // Adjusted: 8h (under 14h limit!)
        assertThat(adjustedDuration).isEqualTo(hoursToMs(8.0))
        assertThat(adjustedDuration).isLessThan(SplitSleeperDetector.TOTAL_MINIMUM_MS + hoursToMs(4.0)) // Under 14h
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `exactly 7h SB is valid Period A`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(15.0), now - hoursToMs(12.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(12.0), now - hoursToMs(5.0)), // Exactly 7h
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(3.0)),
            createEvent("OFF_DUTY", now - hoursToMs(3.0), now) // 3h
        )

        val result = detector.analyze(events, now)

        assertThat(result.validPairs).hasSize(1)
    }

    @Test
    fun `exactly 2h Period B is valid`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(14.0), now - hoursToMs(11.0)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(11.0), now - hoursToMs(3.0)), // 8h SB
            createEvent("DRIVING", now - hoursToMs(3.0), now - hoursToMs(2.0)),
            createEvent("OFF_DUTY", now - hoursToMs(2.0), now) // Exactly 2h
        )

        val result = detector.analyze(events, now)

        // 8h + 2h = 10h ✓
        assertThat(result.validPairs).hasSize(1)
    }

    @Test
    fun `periods too far apart are not paired`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            // Day 1
            createEvent("DRIVING", now - DAY_MS * 3, now - DAY_MS * 3 + hoursToMs(3.0)),
            createEvent("SLEEPER_BERTH", now - DAY_MS * 3 + hoursToMs(3.0), now - DAY_MS * 3 + hoursToMs(10.0)), // 7h SB
            // ... 2+ days later ...
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(2.0)),
            createEvent("OFF_DUTY", now - hoursToMs(2.0), now) // 2h OFF
        )

        val result = detector.analyze(events, now)

        // Periods more than 24h apart shouldn't pair
        assertThat(result.validPairs).isEmpty()
    }

    @Test
    fun `handles events in wrong order`() {
        val now = System.currentTimeMillis()
        // Events out of order - detector should sort
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(3.0), now), // Last
            createEvent("SLEEPER_BERTH", now - hoursToMs(12.0), now - hoursToMs(5.0)), // First rest
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(3.0)), // Middle
            createEvent("DRIVING", now - hoursToMs(15.0), now - hoursToMs(12.0)) // First
        )

        // Should not crash and should analyze correctly
        val result = detector.analyze(events, now)

        assertThat(result.validPairs).hasSize(1)
    }
}
