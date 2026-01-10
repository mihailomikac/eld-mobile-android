package com.eld.driver.hos

import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.eld.driver.data.local.entity.HOSViolationType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Comprehensive unit tests for HOSCalculator.
 *
 * Tests FMCSA HOS rules for property-carrying vehicles:
 * - 11-Hour Driving Limit
 * - 14-Hour Shift Limit
 * - 30-Minute Break (after 8 hours driving)
 * - 60/70-Hour Cycle Limit
 * - 34-Hour Restart
 * - 10-Hour Rest Period (shift start detection)
 */
class HOSCalculatorTest {

    private lateinit var calculator: HOSCalculator

    // Time constants for readability
    private val HOUR_MS = 60L * 60 * 1000
    private val MINUTE_MS = 60L * 1000
    private val DAY_MS = 24 * HOUR_MS

    @Before
    fun setup() {
        calculator = HOSCalculator(CycleRule.US_70_HOUR_8_DAY)
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

    // ==================== DEFAULT/EMPTY STATE TESTS ====================

    @Test
    fun `calculate with empty events returns default full times`() {
        val result = calculator.calculate(emptyList())

        assertThat(result.driveTimeRemainingMs).isEqualTo(HOSCalculator.DRIVE_LIMIT_MS)
        assertThat(result.shiftTimeRemainingMs).isEqualTo(HOSCalculator.SHIFT_LIMIT_MS)
        assertThat(result.breakTimeRemainingMs).isEqualTo(HOSCalculator.BREAK_REQUIRED_AFTER_MS)
        assertThat(result.cycleTimeRemainingMs).isEqualTo(calculator.cycleLimitMs)
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun `calculate with empty events returns zero used times`() {
        val result = calculator.calculate(emptyList())

        assertThat(result.currentDriveTimeMs).isEqualTo(0L)
        assertThat(result.currentCycleHoursMs).isEqualTo(0L)
        assertThat(result.drivingTimeSinceBreakMs).isEqualTo(0L)
    }

    // ==================== DRIVE TIME TESTS ====================

    @Test
    fun `calculate drive time for single driving event`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(3.0), now) // 3 hours driving
        )

        val result = calculator.calculate(events)

        assertThat(result.currentDriveTimeMs).isEqualTo(hoursToMs(3.0))
        assertThat(result.driveTimeRemainingMs).isEqualTo(hoursToMs(8.0)) // 11 - 3 = 8
    }

    @Test
    fun `calculate drive time for multiple driving events`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(6.0), now - hoursToMs(4.0)), // 2 hours
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(4.0), now - hoursToMs(3.0)), // 1 hour on-duty
            createEvent("DRIVING", now - hoursToMs(3.0), now) // 3 hours
        )

        val result = calculator.calculate(events)

        assertThat(result.currentDriveTimeMs).isEqualTo(hoursToMs(5.0)) // 2 + 3 = 5
        assertThat(result.driveTimeRemainingMs).isEqualTo(hoursToMs(6.0)) // 11 - 5 = 6
    }

    @Test
    fun `active driving event calculates time until now`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(2.0), null) // Active driving, no end time
        )

        val result = calculator.calculate(events)

        // Should be approximately 2 hours (within tolerance for test execution time)
        assertThat(result.currentDriveTimeMs).isAtLeast(hoursToMs(1.9))
        assertThat(result.currentDriveTimeMs).isAtMost(hoursToMs(2.1))
    }

    @Test
    fun `drive time only counts DRIVING status`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(4.0)), // 1 hour driving
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(4.0), now - hoursToMs(2.0)), // 2 hours on-duty
            createEvent("OFF_DUTY", now - hoursToMs(2.0), now - hoursToMs(1.0)), // 1 hour off
            createEvent("SLEEPER_BERTH", now - hoursToMs(1.0), now) // 1 hour sleeper
        )

        val result = calculator.calculate(events)

        assertThat(result.currentDriveTimeMs).isEqualTo(hoursToMs(1.0)) // Only DRIVING counts
    }

    // ==================== SHIFT DURATION TESTS ====================

    @Test
    fun `shift duration calculated from shift start to now`() {
        val now = System.currentTimeMillis()
        // After 10+ hours off duty, start a new shift
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)), // 10 hours off
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(5.0), now - hoursToMs(4.0)), // 1 hour on
            createEvent("DRIVING", now - hoursToMs(4.0), now) // 4 hours driving
        )

        val result = calculator.calculate(events)

        // Shift started 5 hours ago (after the 10h rest)
        assertThat(result.currentShiftDurationMs).isAtLeast(hoursToMs(4.9))
        assertThat(result.currentShiftDurationMs).isAtMost(hoursToMs(5.1))
        assertThat(result.shiftTimeRemainingMs).isAtLeast(hoursToMs(8.9)) // 14 - 5 = 9
        assertThat(result.shiftTimeRemainingMs).isAtMost(hoursToMs(9.1))
    }

    @Test
    fun `shift continues across non-rest periods`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(20.0), now - hoursToMs(10.0)), // 10 hours off (shift start after this)
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(10.0), now - hoursToMs(8.0)), // 2 hours on
            createEvent("OFF_DUTY", now - hoursToMs(8.0), now - hoursToMs(6.0)), // 2 hours off (< 10h, doesn't reset)
            createEvent("DRIVING", now - hoursToMs(6.0), now) // 6 hours driving
        )

        val result = calculator.calculate(events)

        // Shift is 10 hours (started after the first 10h rest)
        assertThat(result.currentShiftDurationMs).isAtLeast(hoursToMs(9.9))
        assertThat(result.currentShiftDurationMs).isAtMost(hoursToMs(10.1))
    }

    // ==================== 10-HOUR REST (SHIFT START) TESTS ====================

    @Test
    fun `10 hour rest period resets shift`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(20.0), now - hoursToMs(18.0)), // Old driving
            createEvent("OFF_DUTY", now - hoursToMs(18.0), now - hoursToMs(8.0)), // 10 hours off = NEW SHIFT
            createEvent("DRIVING", now - hoursToMs(8.0), now) // Current driving
        )

        val result = calculator.calculate(events)

        // Shift started 8 hours ago (after 10h rest)
        assertThat(result.shiftStartTime).isEqualTo(now - hoursToMs(8.0))
        assertThat(result.currentDriveTimeMs).isEqualTo(hoursToMs(8.0))
    }

    @Test
    fun `combined sleeper berth and off duty counts as rest`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(20.0), now - hoursToMs(18.0)), // Old driving
            createEvent("SLEEPER_BERTH", now - hoursToMs(18.0), now - hoursToMs(13.0)), // 5 hours sleeper
            createEvent("OFF_DUTY", now - hoursToMs(13.0), now - hoursToMs(8.0)), // 5 hours off = 10h total rest
            createEvent("DRIVING", now - hoursToMs(8.0), now) // Current driving
        )

        val result = calculator.calculate(events)

        // Shift started 8 hours ago
        assertThat(result.shiftStartTime).isEqualTo(now - hoursToMs(8.0))
    }

    @Test
    fun `9 hour rest does not reset shift`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(15.0), now - hoursToMs(14.0)), // 1 hour driving
            createEvent("OFF_DUTY", now - hoursToMs(14.0), now - hoursToMs(5.0)), // 9 hours off (not enough!)
            createEvent("DRIVING", now - hoursToMs(5.0), now) // 5 hours driving
        )

        val result = calculator.calculate(events)

        // Shift started 15 hours ago (9h rest doesn't reset)
        assertThat(result.shiftStartTime).isEqualTo(now - hoursToMs(15.0))
        assertThat(result.currentDriveTimeMs).isEqualTo(hoursToMs(6.0)) // 1 + 5 = 6
    }

    // ==================== 30-MINUTE BREAK TESTS ====================

    @Test
    fun `break time resets after 30 minute rest`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)), // 10h rest (shift start)
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(3.0)), // 2 hours driving
            createEvent("OFF_DUTY", now - hoursToMs(3.0), now - minutesToMs(150)), // 30 min break
            createEvent("DRIVING", now - minutesToMs(150), now) // 2.5 hours driving
        )

        val result = calculator.calculate(events)

        // Driving since break = 2.5 hours (150 minutes)
        assertThat(result.drivingTimeSinceBreakMs).isEqualTo(minutesToMs(150))
        assertThat(result.breakTimeRemainingMs).isEqualTo(hoursToMs(8.0) - minutesToMs(150))
    }

    @Test
    fun `29 minute rest does not count as break`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)), // 10h rest
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(3.0)), // 2 hours driving
            createEvent("OFF_DUTY", now - hoursToMs(3.0), now - minutesToMs(151)), // 29 min (not enough!)
            createEvent("DRIVING", now - minutesToMs(151), now) // ~2.5 hours driving
        )

        val result = calculator.calculate(events)

        // All driving counts (29 min doesn't qualify as break)
        assertThat(result.drivingTimeSinceBreakMs).isAtLeast(hoursToMs(4.4))
    }

    @Test
    fun `sleeper berth counts as break`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)), // 10h rest
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(3.0)), // 2 hours driving
            createEvent("SLEEPER_BERTH", now - hoursToMs(3.0), now - hoursToMs(2.5)), // 30 min sleeper = break
            createEvent("DRIVING", now - hoursToMs(2.5), now) // 2.5 hours driving
        )

        val result = calculator.calculate(events)

        // Driving since break = 2.5 hours
        assertThat(result.drivingTimeSinceBreakMs).isEqualTo(hoursToMs(2.5))
    }

    @Test
    fun `ON_DUTY_NOT_DRIVING counts as break per FMCSA`() {
        // Per FMCSA §395.3(a)(3)(ii): "the 30-minute rest break can be satisfied by
        // off duty, sleeper berth, on duty (not driving), or any combination of the three"
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)), // 10h rest
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(3.0)), // 2 hours driving
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(3.0), now - hoursToMs(2.5)), // 30 min ON_DUTY = break!
            createEvent("DRIVING", now - hoursToMs(2.5), now) // 2.5 hours driving
        )

        val result = calculator.calculate(events)

        // Driving since break = 2.5 hours (ON_DUTY_NOT_DRIVING reset the break timer)
        assertThat(result.drivingTimeSinceBreakMs).isEqualTo(hoursToMs(2.5))
        assertThat(result.breakTimeRemainingMs).isEqualTo(hoursToMs(8.0) - hoursToMs(2.5))
    }

    @Test
    fun `combined short breaks do NOT count as 30 min break per FMCSA`() {
        // FMCSA requires 30 CONSECUTIVE minutes in a single period
        // Multiple shorter breaks do NOT combine to satisfy the requirement
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)), // 10h rest
            createEvent("DRIVING", now - hoursToMs(5.0), now - hoursToMs(3.0)), // 2 hours driving
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(3.0), now - minutesToMs(165)), // 15 min ON_DUTY
            createEvent("OFF_DUTY", now - minutesToMs(165), now - minutesToMs(150)), // 15 min OFF
            createEvent("DRIVING", now - minutesToMs(150), now) // 2.5 hours driving
        )

        val result = calculator.calculate(events)

        // No valid 30-min break, so ALL driving since shift start counts
        // 2h + 2.5h = 4.5h = 270 minutes
        assertThat(result.drivingTimeSinceBreakMs).isEqualTo(minutesToMs(270))
    }

    // ==================== CYCLE HOURS TESTS ====================

    @Test
    fun `cycle hours sum on-duty time over 8 days`() {
        val now = System.currentTimeMillis()
        val events = mutableListOf<DutyStatusEventEntity>()

        // Add 8 hours on-duty for each of 5 days
        for (day in 0 until 5) {
            val dayStart = now - (day + 1) * DAY_MS
            events.add(createEvent("ON_DUTY_NOT_DRIVING", dayStart, dayStart + hoursToMs(4.0)))
            events.add(createEvent("DRIVING", dayStart + hoursToMs(4.0), dayStart + hoursToMs(8.0)))
        }

        val result = calculator.calculate(events)

        // 5 days * 8 hours = 40 hours on-duty
        assertThat(result.currentCycleHoursMs).isAtLeast(hoursToMs(39.0))
        assertThat(result.currentCycleHoursMs).isAtMost(hoursToMs(41.0))
        assertThat(result.cycleTimeRemainingMs).isAtLeast(hoursToMs(29.0)) // 70 - 40 = 30
        assertThat(result.cycleTimeRemainingMs).isAtMost(hoursToMs(31.0))
    }

    @Test
    fun `cycle hours only count DRIVING and ON_DUTY_NOT_DRIVING`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(10.0), now - hoursToMs(8.0)), // 2 hours driving
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(8.0), now - hoursToMs(5.0)), // 3 hours on-duty
            createEvent("OFF_DUTY", now - hoursToMs(5.0), now - hoursToMs(2.0)), // 3 hours off (doesn't count)
            createEvent("SLEEPER_BERTH", now - hoursToMs(2.0), now) // 2 hours sleeper (doesn't count)
        )

        val result = calculator.calculate(events)

        // Only 5 hours on-duty (2 driving + 3 on-duty not driving)
        assertThat(result.currentCycleHoursMs).isEqualTo(hoursToMs(5.0))
    }

    // ==================== 34-HOUR RESTART TESTS ====================

    @Test
    fun `34 hour restart resets cycle`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            // Old cycle - 50 hours
            createEvent("DRIVING", now - DAY_MS * 6, now - DAY_MS * 6 + hoursToMs(10.0)),
            createEvent("DRIVING", now - DAY_MS * 5, now - DAY_MS * 5 + hoursToMs(10.0)),
            createEvent("DRIVING", now - DAY_MS * 4, now - DAY_MS * 4 + hoursToMs(10.0)),
            createEvent("DRIVING", now - DAY_MS * 3, now - DAY_MS * 3 + hoursToMs(10.0)),
            createEvent("DRIVING", now - DAY_MS * 2, now - DAY_MS * 2 + hoursToMs(10.0)),
            // 34 hour restart
            createEvent("OFF_DUTY", now - hoursToMs(40.0), now - hoursToMs(6.0)), // 34 hours off
            // New cycle
            createEvent("DRIVING", now - hoursToMs(6.0), now) // 6 hours driving
        )

        val result = calculator.calculate(events)

        // Cycle should only count 6 hours (after 34h restart)
        assertThat(result.currentCycleHoursMs).isEqualTo(hoursToMs(6.0))
        assertThat(result.cycleTimeRemainingMs).isEqualTo(hoursToMs(64.0)) // 70 - 6 = 64
    }

    @Test
    fun `33 hour rest does not restart cycle`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - DAY_MS * 3, now - DAY_MS * 3 + hoursToMs(10.0)), // 10 hours
            createEvent("OFF_DUTY", now - hoursToMs(38.0), now - hoursToMs(5.0)), // 33 hours off (not enough!)
            createEvent("DRIVING", now - hoursToMs(5.0), now) // 5 hours driving
        )

        val result = calculator.calculate(events)

        // Both driving periods should count (no restart)
        assertThat(result.currentCycleHoursMs).isEqualTo(hoursToMs(15.0)) // 10 + 5
    }

    // ==================== VIOLATION TESTS ====================

    @Test
    fun `detect drive time exceeded violation`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), now - hoursToMs(12.0)), // 13h rest
            createEvent("DRIVING", now - hoursToMs(12.0), now) // 12 hours driving (exceeds 11)
        )

        val result = calculator.calculate(events)

        assertThat(result.violations).contains(HOSViolationType.DRIVE_TIME_EXCEEDED)
        assertThat(result.driveTimeRemainingMs).isLessThan(0L) // Negative = violation
    }

    @Test
    fun `detect shift time exceeded violation when DRIVING after 14h`() {
        // FMCSA: SHIFT_TIME_EXCEEDED only triggers when DRIVING after 14h window
        // ON_DUTY after 14h is allowed (no driving)
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), now - hoursToMs(15.0)), // 10h rest
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(15.0), now - hoursToMs(1.0)), // 14 hours on-duty
            createEvent("DRIVING", now - hoursToMs(1.0), now) // 1 hour DRIVING after 14h window = VIOLATION
        )

        val result = calculator.calculate(events)

        assertThat(result.violations).contains(HOSViolationType.SHIFT_TIME_EXCEEDED)
        assertThat(result.shiftTimeRemainingMs).isLessThan(0L)
    }

    @Test
    fun `ON_DUTY after 14h does NOT trigger shift violation`() {
        // You can be ON_DUTY after 14h window, just cannot DRIVE
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), now - hoursToMs(15.0)), // 10h rest
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(15.0), now) // 15 hours on-duty (no driving)
        )

        val result = calculator.calculate(events)

        // No SHIFT_TIME_EXCEEDED because we're not driving
        assertThat(result.violations).doesNotContain(HOSViolationType.SHIFT_TIME_EXCEEDED)
    }

    @Test
    fun `detect break required violation`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(20.0), now - hoursToMs(9.0)), // 11h rest
            createEvent("DRIVING", now - hoursToMs(9.0), now) // 9 hours continuous driving (exceeds 8)
        )

        val result = calculator.calculate(events)

        assertThat(result.violations).contains(HOSViolationType.BREAK_REQUIRED)
        assertThat(result.breakTimeRemainingMs).isLessThan(0L)
    }

    @Test
    fun `detect cycle time exceeded violation`() {
        val now = System.currentTimeMillis()
        val events = mutableListOf<DutyStatusEventEntity>()

        // Add 10 hours on-duty for 8 days = 80 hours (exceeds 70)
        for (day in 0 until 8) {
            val dayStart = now - (day + 1) * DAY_MS
            events.add(createEvent("DRIVING", dayStart, dayStart + hoursToMs(10.0)))
        }

        val result = calculator.calculate(events)

        assertThat(result.violations).contains(HOSViolationType.CYCLE_TIME_EXCEEDED)
        assertThat(result.cycleTimeRemainingMs).isLessThan(0L)
    }

    @Test
    fun `no violations when within limits`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)), // 10h rest
            createEvent("DRIVING", now - hoursToMs(5.0), now) // 5 hours driving (within all limits)
        )

        val result = calculator.calculate(events)

        assertThat(result.violations).isEmpty()
        assertThat(result.driveTimeRemainingMs).isGreaterThan(0L)
        assertThat(result.shiftTimeRemainingMs).isGreaterThan(0L)
        assertThat(result.breakTimeRemainingMs).isGreaterThan(0L)
        assertThat(result.cycleTimeRemainingMs).isGreaterThan(0L)
    }

    @Test
    fun `multiple violations detected simultaneously`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(30.0), now - hoursToMs(16.0)), // 14h rest
            createEvent("DRIVING", now - hoursToMs(16.0), now) // 16 hours driving
        )

        val result = calculator.calculate(events)

        // Should have all three violations:
        // - Drive time exceeded (16 > 11)
        // - Shift time exceeded (16 > 14)
        // - Break required (16 > 8)
        assertThat(result.violations).containsAtLeast(
            HOSViolationType.DRIVE_TIME_EXCEEDED,
            HOSViolationType.SHIFT_TIME_EXCEEDED,
            HOSViolationType.BREAK_REQUIRED
        )
    }

    // ==================== CYCLE RULE TESTS ====================

    @Test
    fun `60 hour 7 day cycle rule`() {
        val calculator60 = HOSCalculator(CycleRule.US_60_HOUR_7_DAY)
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(10.0), now) // 10 hours
        )

        val result = calculator60.calculate(events)

        assertThat(result.cycleRule).isEqualTo(CycleRule.US_60_HOUR_7_DAY)
        assertThat(result.cycleTimeRemainingMs).isEqualTo(hoursToMs(50.0)) // 60 - 10 = 50
    }

    @Test
    fun `60 hour cycle violation at lower threshold`() {
        val calculator60 = HOSCalculator(CycleRule.US_60_HOUR_7_DAY)
        val now = System.currentTimeMillis()
        val events = mutableListOf<DutyStatusEventEntity>()

        // Add 10 hours for 7 days = 70 hours (exceeds 60)
        for (day in 0 until 7) {
            val dayStart = now - (day + 1) * DAY_MS
            events.add(createEvent("DRIVING", dayStart, dayStart + hoursToMs(10.0)))
        }

        val result = calculator60.calculate(events)

        assertThat(result.violations).contains(HOSViolationType.CYCLE_TIME_EXCEEDED)
    }

    // ==================== PROGRESS VALUE TESTS ====================

    @Test
    fun `progress values are between 0 and 1`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)),
            createEvent("DRIVING", now - hoursToMs(5.0), now)
        )

        val result = calculator.calculate(events)

        assertThat(result.driveProgress).isAtLeast(0f)
        assertThat(result.driveProgress).isAtMost(1f)
        assertThat(result.shiftProgress).isAtLeast(0f)
        assertThat(result.shiftProgress).isAtMost(1f)
        assertThat(result.breakProgress).isAtLeast(0f)
        assertThat(result.breakProgress).isAtMost(1f)
        assertThat(result.cycleProgress).isAtLeast(0f)
        assertThat(result.cycleProgress).isAtMost(1f)
    }

    @Test
    fun `progress clamped to 0 when in violation`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), now - hoursToMs(12.0)),
            createEvent("DRIVING", now - hoursToMs(12.0), now) // 12 hours = violation
        )

        val result = calculator.calculate(events)

        // Progress should be 0 (clamped) when in violation
        assertThat(result.driveProgress).isEqualTo(0f)
    }

    // ==================== FORMATTED OUTPUT TESTS ====================

    @Test
    fun `formatted time strings are correct format`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)),
            createEvent("DRIVING", now - hoursToMs(5.0), now) // 5 hours driving
        )

        val result = calculator.calculate(events)

        // Should be in HH:MM format
        assertThat(result.driveTimeRemainingFormatted).matches("\\d{2}:\\d{2}")
        assertThat(result.shiftTimeRemainingFormatted).matches("\\d{2}:\\d{2}")
        assertThat(result.breakTimeRemainingFormatted).matches("\\d{2}:\\d{2}")
        assertThat(result.cycleTimeRemainingFormatted).matches("\\d{2}:\\d{2}")
    }

    @Test
    fun `formatted drive time shows correct value`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)),
            createEvent("DRIVING", now - hoursToMs(5.0), now) // 5 hours driving
        )

        val result = calculator.calculate(events)

        // 11 - 5 = 6 hours remaining
        assertThat(result.driveTimeRemainingFormatted).isEqualTo("06:00")
    }

    // ==================== TO ENTITY CONVERSION TESTS ====================

    @Test
    fun `toEntity converts milliseconds to seconds`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)),
            createEvent("DRIVING", now - hoursToMs(5.0), now) // 5 hours driving
        )

        val result = calculator.calculate(events)
        val entity = result.toEntity()

        // Convert back: 6 hours * 3600 = 21600 seconds
        assertThat(entity.driveTimeRemainingSeconds).isEqualTo(6 * 60 * 60)
    }

    @Test
    fun `toEntity preserves violation list`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), now - hoursToMs(12.0)),
            createEvent("DRIVING", now - hoursToMs(12.0), now) // Violation
        )

        val result = calculator.calculate(events)
        val entity = result.toEntity()

        assertThat(entity.violations).contains(HOSViolationType.DRIVE_TIME_EXCEEDED.name)
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    fun `handles events out of chronological order`() {
        val now = System.currentTimeMillis()
        // Events in wrong order
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(3.0), now), // Later event first
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)), // Earlier event second
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(5.0), now - hoursToMs(3.0)) // Middle event last
        )

        val result = calculator.calculate(events)

        // Should sort and calculate correctly
        assertThat(result.currentDriveTimeMs).isEqualTo(hoursToMs(3.0))
    }

    @Test
    fun `handles exactly 11 hours driving (no violation)`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), now - hoursToMs(11.0)),
            createEvent("DRIVING", now - hoursToMs(11.0), now) // Exactly 11 hours
        )

        val result = calculator.calculate(events)

        assertThat(result.violations).doesNotContain(HOSViolationType.DRIVE_TIME_EXCEEDED)
        assertThat(result.driveTimeRemainingMs).isEqualTo(0L)
    }

    @Test
    fun `handles exactly 10 hours rest (qualifies as shift reset)`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(15.0), now - hoursToMs(14.0)), // 1 hour old driving
            createEvent("OFF_DUTY", now - hoursToMs(14.0), now - hoursToMs(4.0)), // Exactly 10 hours off
            createEvent("DRIVING", now - hoursToMs(4.0), now) // 4 hours new driving
        )

        val result = calculator.calculate(events)

        // Shift should reset - only 4 hours driving in current shift
        assertThat(result.currentDriveTimeMs).isEqualTo(hoursToMs(4.0))
        assertThat(result.shiftStartTime).isEqualTo(now - hoursToMs(4.0))
    }

    @Test
    fun `handles single very long event`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - DAY_MS * 2, now) // 48 hours driving (unrealistic but tests limits)
        )

        val result = calculator.calculate(events)

        // Should show all violations
        assertThat(result.violations).containsAtLeast(
            HOSViolationType.DRIVE_TIME_EXCEEDED,
            HOSViolationType.SHIFT_TIME_EXCEEDED,
            HOSViolationType.BREAK_REQUIRED
        )
        // Negative remaining times
        assertThat(result.driveTimeRemainingMs).isLessThan(0L)
    }

    @Test
    fun `cycle left for tomorrow calculation`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(5.0), now) // 5 hours today
        )

        val result = calculator.calculate(events)

        // Tomorrow should have at least as much as today (plus any hours falling off)
        assertThat(result.cycleLeftForTomorrowMs).isGreaterThan(0L)
        assertThat(result.cycleLeftForTomorrowMs).isAtMost(calculator.cycleLimitMs)
    }

    @Test
    fun `hasViolations returns correct boolean`() {
        val now = System.currentTimeMillis()

        // No violations
        val noViolationResult = calculator.calculate(listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)),
            createEvent("DRIVING", now - hoursToMs(5.0), now)
        ))
        assertThat(noViolationResult.hasViolations()).isFalse()

        // With violations
        val violationResult = calculator.calculate(listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), now - hoursToMs(12.0)),
            createEvent("DRIVING", now - hoursToMs(12.0), now)
        ))
        assertThat(violationResult.hasViolations()).isTrue()
    }
}
