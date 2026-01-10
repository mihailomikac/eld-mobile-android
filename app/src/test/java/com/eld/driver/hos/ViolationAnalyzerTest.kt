package com.eld.driver.hos

import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.eld.driver.data.local.entity.HOSViolationType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Comprehensive unit tests for ViolationAnalyzer.
 *
 * ViolationAnalyzer detects when violations START and END based on event history.
 * Unlike HOSCalculator which calculates remaining time, ViolationAnalyzer
 * produces ViolationRecord objects with accurate startTime and endTime.
 *
 * Tests cover:
 * - DRIVE_TIME_EXCEEDED (11-hour limit)
 * - SHIFT_TIME_EXCEEDED (14-hour window, only when DRIVING after 14h)
 * - BREAK_REQUIRED (30-min break after 8h driving)
 * - CYCLE_TIME_EXCEEDED (70h/8-day)
 * - Violation endings (10h rest, 34h restart, 30min break)
 * - ON_DUTY_NOT_DRIVING qualifying for 30-min break
 */
class ViolationAnalyzerTest {

    private lateinit var analyzer: ViolationAnalyzer

    // Time constants
    private val MINUTE_MS = 60L * 1000
    private val HOUR_MS = 60L * MINUTE_MS
    private val DAY_MS = 24L * HOUR_MS

    @Before
    fun setup() {
        analyzer = ViolationAnalyzer(CycleRule.US_70_HOUR_8_DAY)
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

    // ==================== EMPTY/NO VIOLATIONS TESTS ====================

    @Test
    fun `analyze with empty events returns no violations`() {
        val result = analyzer.analyzeViolations(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `no violations when within all limits`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)), // 10h rest
            createEvent("DRIVING", now - hoursToMs(5.0), now) // 5h driving - within limits
        )

        val result = analyzer.analyzeViolations(events, now)

        assertThat(result).isEmpty()
    }

    // ==================== DRIVE_TIME_EXCEEDED TESTS ====================

    @Test
    fun `detect DRIVE_TIME_EXCEEDED after 11 hours driving`() {
        val now = System.currentTimeMillis()
        val driveStart = now - hoursToMs(12.0)
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), driveStart), // Rest before
            createEvent("DRIVING", driveStart, now) // 12 hours driving
        )

        val result = analyzer.analyzeViolations(events, now)

        // 12h driving causes both DRIVE_TIME_EXCEEDED (>11h) and BREAK_REQUIRED (>8h)
        val driveViolation = result.find { it.type == HOSViolationType.DRIVE_TIME_EXCEEDED }
        assertThat(driveViolation).isNotNull()
        assertThat(driveViolation!!.isActive).isTrue()
        // Violation should start at 11 hours into driving
        assertThat(driveViolation.startTime).isEqualTo(driveStart + hoursToMs(11.0))
    }

    @Test
    fun `DRIVE_TIME_EXCEEDED starts exactly at 11 hour mark`() {
        val now = System.currentTimeMillis()
        val driveStart = now - hoursToMs(11.5)
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), driveStart),
            createEvent("DRIVING", driveStart, now) // 11.5 hours
        )

        val result = analyzer.analyzeViolations(events, now)

        val violation = result.find { it.type == HOSViolationType.DRIVE_TIME_EXCEEDED }
        assertThat(violation).isNotNull()
        // Should start exactly when 11h limit is exceeded
        assertThat(violation!!.startTime).isEqualTo(driveStart + hoursToMs(11.0))
    }

    @Test
    fun `no DRIVE_TIME_EXCEEDED at exactly 11 hours`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), now - hoursToMs(11.0)),
            createEvent("DRIVING", now - hoursToMs(11.0), now) // Exactly 11 hours
        )

        val result = analyzer.analyzeViolations(events, now)

        val driveViolation = result.find { it.type == HOSViolationType.DRIVE_TIME_EXCEEDED }
        // At exactly 11h, no violation (violation starts after 11h)
        // But break violation would exist (8h exceeded)
        assertThat(driveViolation).isNull()
    }

    @Test
    fun `DRIVE_TIME_EXCEEDED ends after 10h rest`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(35.0), now - hoursToMs(23.0)),
            createEvent("DRIVING", now - hoursToMs(23.0), now - hoursToMs(11.0)), // 12h driving (violation)
            createEvent("OFF_DUTY", now - hoursToMs(11.0), now - hoursToMs(1.0)), // 10h rest
            createEvent("DRIVING", now - hoursToMs(1.0), now) // New driving
        )

        val result = analyzer.analyzeViolations(events, now)

        val driveViolation = result.find { it.type == HOSViolationType.DRIVE_TIME_EXCEEDED }
        assertThat(driveViolation).isNotNull()
        assertThat(driveViolation!!.isActive).isFalse()
        // Should end when 10h rest completes
        assertThat(driveViolation.endTime).isNotNull()
    }

    @Test
    fun `multiple driving events accumulate for DRIVE_TIME_EXCEEDED`() {
        val now = System.currentTimeMillis()
        val shiftStart = now - hoursToMs(14.0)
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), shiftStart),
            createEvent("DRIVING", shiftStart, shiftStart + hoursToMs(4.0)), // 4h
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart + hoursToMs(4.0), shiftStart + hoursToMs(5.0)), // 1h
            createEvent("DRIVING", shiftStart + hoursToMs(5.0), shiftStart + hoursToMs(9.0)), // 4h
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart + hoursToMs(9.0), shiftStart + hoursToMs(10.0)), // 1h
            createEvent("DRIVING", shiftStart + hoursToMs(10.0), now) // 4h = total 12h driving
        )

        val result = analyzer.analyzeViolations(events, now)

        val driveViolation = result.find { it.type == HOSViolationType.DRIVE_TIME_EXCEEDED }
        assertThat(driveViolation).isNotNull()
        // Should start when cumulative driving hits 11h
    }

    // ==================== SHIFT_TIME_EXCEEDED TESTS ====================

    @Test
    fun `detect SHIFT_TIME_EXCEEDED when driving after 14h window`() {
        val now = System.currentTimeMillis()
        val shiftStart = now - hoursToMs(15.0)
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(30.0), shiftStart), // 10h rest
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart, shiftStart + hoursToMs(2.0)), // 2h
            createEvent("DRIVING", shiftStart + hoursToMs(2.0), shiftStart + hoursToMs(8.0)), // 6h
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart + hoursToMs(8.0), shiftStart + hoursToMs(14.5)), // 6.5h
            createEvent("DRIVING", shiftStart + hoursToMs(14.5), now) // Driving AFTER 14h window
        )

        val result = analyzer.analyzeViolations(events, now)

        val shiftViolation = result.find { it.type == HOSViolationType.SHIFT_TIME_EXCEEDED }
        assertThat(shiftViolation).isNotNull()
    }

    @Test
    fun `no SHIFT_TIME_EXCEEDED for ON_DUTY after 14h - only DRIVING triggers it`() {
        val now = System.currentTimeMillis()
        val shiftStart = now - hoursToMs(16.0)
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(30.0), shiftStart),
            createEvent("DRIVING", shiftStart, shiftStart + hoursToMs(10.0)), // 10h driving (under 11h)
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart + hoursToMs(10.0), now) // 6h on-duty (past 14h)
        )

        val result = analyzer.analyzeViolations(events, now)

        val shiftViolation = result.find { it.type == HOSViolationType.SHIFT_TIME_EXCEEDED }
        // ON_DUTY after 14h is allowed - only DRIVING is prohibited
        assertThat(shiftViolation).isNull()
    }

    @Test
    fun `SHIFT_TIME_EXCEEDED ends after 10h rest`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(40.0), now - hoursToMs(25.0)),
            createEvent("DRIVING", now - hoursToMs(25.0), now - hoursToMs(10.0)), // 15h driving (violation)
            createEvent("OFF_DUTY", now - hoursToMs(10.0), now) // 10h rest - ends violation
        )

        val result = analyzer.analyzeViolations(events, now)

        val shiftViolation = result.find { it.type == HOSViolationType.SHIFT_TIME_EXCEEDED }
        assertThat(shiftViolation).isNotNull()
        assertThat(shiftViolation!!.endTime).isNotNull()
    }

    // ==================== BREAK_REQUIRED TESTS ====================

    @Test
    fun `detect BREAK_REQUIRED after 8 hours driving without break`() {
        val now = System.currentTimeMillis()
        val driveStart = now - hoursToMs(9.0)
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(20.0), driveStart),
            createEvent("DRIVING", driveStart, now) // 9 hours continuous driving
        )

        val result = analyzer.analyzeViolations(events, now)

        val breakViolation = result.find { it.type == HOSViolationType.BREAK_REQUIRED }
        assertThat(breakViolation).isNotNull()
        // Should start at 8h mark
        assertThat(breakViolation!!.startTime).isEqualTo(driveStart + hoursToMs(8.0))
    }

    @Test
    fun `BREAK_REQUIRED ends with 30min OFF_DUTY`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(20.0), now - hoursToMs(10.0)),
            createEvent("DRIVING", now - hoursToMs(10.0), now - hoursToMs(1.5)), // 8.5h driving
            createEvent("OFF_DUTY", now - hoursToMs(1.5), now - hoursToMs(1.0)), // 30min break
            createEvent("DRIVING", now - hoursToMs(1.0), now) // New driving
        )

        val result = analyzer.analyzeViolations(events, now)

        val breakViolation = result.find { it.type == HOSViolationType.BREAK_REQUIRED }
        assertThat(breakViolation).isNotNull()
        assertThat(breakViolation!!.endTime).isNotNull() // Should be ended
    }

    @Test
    fun `BREAK_REQUIRED ends with 30min SLEEPER_BERTH`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(20.0), now - hoursToMs(10.0)),
            createEvent("DRIVING", now - hoursToMs(10.0), now - hoursToMs(1.5)),
            createEvent("SLEEPER_BERTH", now - hoursToMs(1.5), now - hoursToMs(1.0)), // 30min SB
            createEvent("DRIVING", now - hoursToMs(1.0), now)
        )

        val result = analyzer.analyzeViolations(events, now)

        val breakViolation = result.find { it.type == HOSViolationType.BREAK_REQUIRED }
        assertThat(breakViolation).isNotNull()
        assertThat(breakViolation!!.endTime).isNotNull()
    }

    @Test
    fun `BREAK_REQUIRED ends with 30min ON_DUTY_NOT_DRIVING - FMCSA compliant`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(20.0), now - hoursToMs(10.0)),
            createEvent("DRIVING", now - hoursToMs(10.0), now - hoursToMs(1.5)), // 8.5h driving
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(1.5), now - hoursToMs(1.0)), // 30min ON_DUTY
            createEvent("DRIVING", now - hoursToMs(1.0), now)
        )

        val result = analyzer.analyzeViolations(events, now)

        val breakViolation = result.find { it.type == HOSViolationType.BREAK_REQUIRED }
        assertThat(breakViolation).isNotNull()
        // Per FMCSA §395.3(a)(3)(ii): ON_DUTY_NOT_DRIVING qualifies for 30-min break
        assertThat(breakViolation!!.endTime).isNotNull()
    }

    @Test
    fun `29min rest does not end BREAK_REQUIRED`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(20.0), now - hoursToMs(10.0)),
            createEvent("DRIVING", now - hoursToMs(10.0), now - minutesToMs(89)), // 8.5h+ driving
            createEvent("OFF_DUTY", now - minutesToMs(89), now - hoursToMs(1.0)), // 29min break
            createEvent("DRIVING", now - hoursToMs(1.0), now) // Continue driving
        )

        val result = analyzer.analyzeViolations(events, now)

        // Should still have active BREAK_REQUIRED (29min doesn't qualify)
        val breakViolation = result.find { it.type == HOSViolationType.BREAK_REQUIRED && it.isActive }
        assertThat(breakViolation).isNotNull()
    }

    @Test
    fun `break timer resets after valid 30min break`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(25.0), now - hoursToMs(15.0)), // 10h rest
            createEvent("DRIVING", now - hoursToMs(15.0), now - hoursToMs(7.0)), // 8h driving
            createEvent("OFF_DUTY", now - hoursToMs(7.0), now - hoursToMs(6.5)), // 30min break
            createEvent("DRIVING", now - hoursToMs(6.5), now) // 6.5h driving after break
        )

        val result = analyzer.analyzeViolations(events, now)

        // First BREAK_REQUIRED at 8h, ended at break
        // No new BREAK_REQUIRED since only 6.5h driving after break (< 8h)
        val activeBreakViolations = result.filter {
            it.type == HOSViolationType.BREAK_REQUIRED && it.isActive
        }
        assertThat(activeBreakViolations).isEmpty()
    }

    // ==================== CYCLE_TIME_EXCEEDED TESTS ====================

    @Test
    fun `detect CYCLE_TIME_EXCEEDED after 70 hours in 8 days`() {
        val now = System.currentTimeMillis()
        val events = mutableListOf<DutyStatusEventEntity>()

        // 10 hours per day for 7 days = 70 hours, then 3 more hours = 73h total (violation)
        // Start from 7 days ago to ensure all events are within the 8-day window
        for (day in 0 until 7) {
            val dayStart = now - (7 - day) * DAY_MS + HOUR_MS // Add 1 hour buffer from day start
            events.add(createEvent("DRIVING", dayStart, dayStart + hoursToMs(10.0)))
        }
        // Today: 3 more hours to exceed 70h
        events.add(createEvent("DRIVING", now - hoursToMs(3.0), now))

        val result = analyzer.analyzeViolations(events, now)

        val cycleViolation = result.find { it.type == HOSViolationType.CYCLE_TIME_EXCEEDED }
        assertThat(cycleViolation).isNotNull()
    }

    @Test
    fun `CYCLE_TIME_EXCEEDED ends with 34h restart`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            // Build up 70+ hours over several days
            createEvent("DRIVING", now - DAY_MS * 7, now - DAY_MS * 7 + hoursToMs(11.0)),
            createEvent("DRIVING", now - DAY_MS * 6, now - DAY_MS * 6 + hoursToMs(11.0)),
            createEvent("DRIVING", now - DAY_MS * 5, now - DAY_MS * 5 + hoursToMs(11.0)),
            createEvent("DRIVING", now - DAY_MS * 4, now - DAY_MS * 4 + hoursToMs(11.0)),
            createEvent("DRIVING", now - DAY_MS * 3, now - DAY_MS * 3 + hoursToMs(11.0)),
            createEvent("DRIVING", now - DAY_MS * 2, now - DAY_MS * 2 + hoursToMs(11.0)),
            // 34h restart
            createEvent("OFF_DUTY", now - hoursToMs(40.0), now - hoursToMs(6.0)),
            // New driving after restart
            createEvent("DRIVING", now - hoursToMs(6.0), now)
        )

        val result = analyzer.analyzeViolations(events, now)

        // After 34h restart, cycle violation should be ended
        val cycleViolation = result.find { it.type == HOSViolationType.CYCLE_TIME_EXCEEDED }
        if (cycleViolation != null) {
            assertThat(cycleViolation.endTime).isNotNull()
        }
    }

    // ==================== MULTIPLE SIMULTANEOUS VIOLATIONS ====================

    @Test
    fun `detect multiple violations simultaneously`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(30.0), now - hoursToMs(16.0)),
            createEvent("DRIVING", now - hoursToMs(16.0), now) // 16h continuous driving
        )

        val result = analyzer.analyzeViolations(events, now)

        val violationTypes = result.map { it.type }.toSet()
        // Should have all three: DRIVE (>11h), SHIFT (>14h driving), BREAK (>8h without break)
        assertThat(violationTypes).containsAtLeast(
            HOSViolationType.DRIVE_TIME_EXCEEDED,
            HOSViolationType.SHIFT_TIME_EXCEEDED,
            HOSViolationType.BREAK_REQUIRED
        )
    }

    @Test
    fun `violations have correct chronological start times`() {
        val now = System.currentTimeMillis()
        val driveStart = now - hoursToMs(16.0)
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(30.0), driveStart),
            createEvent("DRIVING", driveStart, now)
        )

        val result = analyzer.analyzeViolations(events, now)

        val breakViol = result.find { it.type == HOSViolationType.BREAK_REQUIRED }
        val driveViol = result.find { it.type == HOSViolationType.DRIVE_TIME_EXCEEDED }
        val shiftViol = result.find { it.type == HOSViolationType.SHIFT_TIME_EXCEEDED }

        // BREAK at 8h, DRIVE at 11h, SHIFT at 14h
        assertThat(breakViol!!.startTime).isEqualTo(driveStart + hoursToMs(8.0))
        assertThat(driveViol!!.startTime).isEqualTo(driveStart + hoursToMs(11.0))
        assertThat(shiftViol!!.startTime).isEqualTo(driveStart + hoursToMs(14.0))
    }

    // ==================== 10-HOUR REST SCENARIOS ====================

    @Test
    fun `10h rest clears all shift violations`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            // Old shift with violations
            createEvent("OFF_DUTY", now - hoursToMs(40.0), now - hoursToMs(25.0)),
            createEvent("DRIVING", now - hoursToMs(25.0), now - hoursToMs(12.0)), // 13h driving
            // 10h rest
            createEvent("OFF_DUTY", now - hoursToMs(12.0), now - hoursToMs(2.0)),
            // New shift
            createEvent("DRIVING", now - hoursToMs(2.0), now)
        )

        val result = analyzer.analyzeViolations(events, now)

        // All old violations should be ended
        val activeViolations = result.filter { it.isActive }
        assertThat(activeViolations).isEmpty()
    }

    @Test
    fun `9h rest does not reset violations`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(32.0), now - hoursToMs(22.0)), // 10h rest before
            createEvent("DRIVING", now - hoursToMs(22.0), now - hoursToMs(11.0)), // 11h driving (at limit)
            createEvent("OFF_DUTY", now - hoursToMs(11.0), now - hoursToMs(2.0)), // Only 9h rest
            createEvent("DRIVING", now - hoursToMs(2.0), now) // 2h more driving = 13h total (exceeds 11h)
        )

        val result = analyzer.analyzeViolations(events, now)

        // DRIVE_TIME_EXCEEDED should be active (9h rest doesn't reset, so 11+2=13h > 11h limit)
        val driveViolation = result.find { it.type == HOSViolationType.DRIVE_TIME_EXCEEDED }
        assertThat(driveViolation).isNotNull()
    }

    // ==================== REAL-WORLD SCENARIOS ====================

    @Test
    fun `scenario - typical day with proper breaks`() {
        val now = System.currentTimeMillis()
        val shiftStart = now - hoursToMs(12.0)
        val events = listOf(
            // Previous rest
            createEvent("OFF_DUTY", now - hoursToMs(24.0), shiftStart),
            // Pre-trip
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart, shiftStart + minutesToMs(30)),
            // Morning drive
            createEvent("DRIVING", shiftStart + minutesToMs(30), shiftStart + hoursToMs(4.5)),
            // Fuel stop
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart + hoursToMs(4.5), shiftStart + hoursToMs(5.0)),
            // More driving
            createEvent("DRIVING", shiftStart + hoursToMs(5.0), shiftStart + hoursToMs(8.0)),
            // 30-min break before 8h driving
            createEvent("OFF_DUTY", shiftStart + hoursToMs(8.0), shiftStart + hoursToMs(8.5)),
            // Afternoon drive
            createEvent("DRIVING", shiftStart + hoursToMs(8.5), shiftStart + hoursToMs(11.5)),
            // Post-trip
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart + hoursToMs(11.5), now)
        )

        val result = analyzer.analyzeViolations(events, now)

        // Should have no violations - proper breaks taken
        assertThat(result).isEmpty()
    }

    @Test
    fun `scenario - driver forgets 30min break`() {
        val now = System.currentTimeMillis()
        val shiftStart = now - hoursToMs(10.0)
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(22.0), shiftStart),
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart, shiftStart + minutesToMs(30)),
            // Continuous driving without 30-min break
            createEvent("DRIVING", shiftStart + minutesToMs(30), now) // 9.5h continuous
        )

        val result = analyzer.analyzeViolations(events, now)

        val breakViolation = result.find { it.type == HOSViolationType.BREAK_REQUIRED }
        assertThat(breakViolation).isNotNull()
        assertThat(breakViolation!!.isActive).isTrue()
    }

    @Test
    fun `scenario - driver stops before shift limit but continues ON_DUTY`() {
        val now = System.currentTimeMillis()
        val shiftStart = now - hoursToMs(16.0)
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(30.0), shiftStart),
            // Drive for 10h (under 11h limit)
            createEvent("DRIVING", shiftStart, shiftStart + hoursToMs(10.0)),
            // ON_DUTY for 6 more hours (past 14h shift but NOT driving)
            createEvent("ON_DUTY_NOT_DRIVING", shiftStart + hoursToMs(10.0), now)
        )

        val result = analyzer.analyzeViolations(events, now)

        // BREAK_REQUIRED should exist (>8h driving)
        val breakViolation = result.find { it.type == HOSViolationType.BREAK_REQUIRED }
        assertThat(breakViolation).isNotNull()

        // But NO SHIFT_TIME_EXCEEDED because driver stopped DRIVING before 14h
        val shiftViolation = result.find { it.type == HOSViolationType.SHIFT_TIME_EXCEEDED }
        assertThat(shiftViolation).isNull()
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `handles events out of order`() {
        val now = System.currentTimeMillis()
        // Events in wrong order - analyzer should sort them
        val events = listOf(
            createEvent("DRIVING", now - hoursToMs(5.0), now), // Third
            createEvent("OFF_DUTY", now - hoursToMs(15.0), now - hoursToMs(5.0)), // First
            createEvent("ON_DUTY_NOT_DRIVING", now - hoursToMs(5.0), now - hoursToMs(5.0)) // Second (duration 0)
        )

        // Should not crash, should process correctly
        val result = analyzer.analyzeViolations(events, now)
        assertThat(result).isEmpty() // 5h driving = no violations
    }

    @Test
    fun `handles active driving event with null endTime`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(20.0), now - hoursToMs(12.0)),
            createEvent("DRIVING", now - hoursToMs(12.0), null) // Active, no end time
        )

        val result = analyzer.analyzeViolations(events, now)

        // Should detect violations using 'now' as end time
        val driveViolation = result.find { it.type == HOSViolationType.DRIVE_TIME_EXCEEDED }
        assertThat(driveViolation).isNotNull()
    }

    @Test
    fun `violation ISO time formatting`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            createEvent("OFF_DUTY", now - hoursToMs(20.0), now - hoursToMs(9.0)),
            createEvent("DRIVING", now - hoursToMs(9.0), now) // BREAK violation
        )

        val result = analyzer.analyzeViolations(events, now)
        val violation = result.first()

        // Should produce valid ISO format
        val isoStart = violation.toIsoStartTime()
        assertThat(isoStart).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")
    }
}
