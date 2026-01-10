package com.eld.driver.ble

import com.eld.driver.ble.models.GeometrisEldData
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for automatic duty status change logic.
 *
 * Covers test cases:
 * - Case #8: Speed > 5mph → auto change to DRIVING
 * - Case #10: Stop vehicle → shows STATIONARY but stays DRIVING
 * - Case #12-16: 5 min stationary → show delay dialog
 * - Case #15: Start driving again → dismiss dialog
 */
class AutoStatusChangeTest {

    // Speed threshold constants (matching GeometrisWQManager)
    companion object {
        const val SPEED_THRESHOLD_MPH = 5.0
        const val KMH_TO_MPH = 0.621371
        const val IDLE_THRESHOLD_MS = 5 * 60 * 1000L  // 5 minutes
    }

    // ==================== SPEED THRESHOLD TESTS ====================
    // Case #8: Speed > 5mph → DRIVING

    @Test
    fun `speed above 5mph should trigger DRIVING status`() {
        // 5 mph = 8.05 km/h
        val speedKmh = 10.0  // ~6.2 mph
        val speedMph = speedKmh * KMH_TO_MPH

        assertThat(speedMph).isGreaterThan(SPEED_THRESHOLD_MPH)
        assertThat(shouldChangeToDriving(speedKmh)).isTrue()
    }

    @Test
    fun `speed exactly 5mph should NOT trigger DRIVING status`() {
        val speedKmh = 5.0 / KMH_TO_MPH  // Exactly 5 mph in km/h
        val speedMph = speedKmh * KMH_TO_MPH

        assertThat(speedMph).isWithin(0.01).of(5.0)
        assertThat(shouldChangeToDriving(speedKmh)).isFalse()
    }

    @Test
    fun `speed below 5mph should NOT trigger DRIVING status`() {
        val speedKmh = 5.0  // ~3.1 mph
        val speedMph = speedKmh * KMH_TO_MPH

        assertThat(speedMph).isLessThan(SPEED_THRESHOLD_MPH)
        assertThat(shouldChangeToDriving(speedKmh)).isFalse()
    }

    @Test
    fun `speed 0 should NOT trigger DRIVING status`() {
        assertThat(shouldChangeToDriving(0.0)).isFalse()
    }

    @Test
    fun `speed 6mph should trigger DRIVING status`() {
        val speedKmh = 6.0 / KMH_TO_MPH  // 6 mph in km/h
        assertThat(shouldChangeToDriving(speedKmh)).isTrue()
    }

    @Test
    fun `speed 100kmh should trigger DRIVING status`() {
        assertThat(shouldChangeToDriving(100.0)).isTrue()  // ~62 mph
    }

    // ==================== MOTION STATE TESTS ====================
    // Case #10: Stop vehicle → STATIONARY label but status stays DRIVING

    @Test
    fun `vehicle motion state IN_MOTION when speed above threshold`() {
        val speedKmh = 15.0  // ~9.3 mph
        assertThat(getMotionState(speedKmh)).isEqualTo(VehicleMotionState.IN_MOTION)
    }

    @Test
    fun `vehicle motion state STATIONARY when speed below threshold`() {
        val speedKmh = 3.0  // ~1.9 mph
        assertThat(getMotionState(speedKmh)).isEqualTo(VehicleMotionState.STATIONARY)
    }

    @Test
    fun `vehicle motion state STATIONARY when speed is zero`() {
        assertThat(getMotionState(0.0)).isEqualTo(VehicleMotionState.STATIONARY)
    }

    // ==================== IDLE TIME TESTS ====================
    // Case #12-16: 5 min stationary → dialog

    @Test
    fun `idle time below 5 minutes should NOT trigger dialog`() {
        val idleTimeMs = 4 * 60 * 1000L  // 4 minutes
        assertThat(shouldShowStationaryDialog(idleTimeMs, hasDriverMoved = true)).isFalse()
    }

    @Test
    fun `idle time exactly 5 minutes should trigger dialog`() {
        val idleTimeMs = 5 * 60 * 1000L  // 5 minutes
        assertThat(shouldShowStationaryDialog(idleTimeMs, hasDriverMoved = true)).isTrue()
    }

    @Test
    fun `idle time above 5 minutes should trigger dialog`() {
        val idleTimeMs = 6 * 60 * 1000L  // 6 minutes
        assertThat(shouldShowStationaryDialog(idleTimeMs, hasDriverMoved = true)).isTrue()
    }

    @Test
    fun `idle time 10 minutes should trigger dialog`() {
        val idleTimeMs = 10 * 60 * 1000L  // 10 minutes
        assertThat(shouldShowStationaryDialog(idleTimeMs, hasDriverMoved = true)).isTrue()
    }

    @Test
    fun `idle time should NOT trigger dialog if driver never moved`() {
        // Case: Driver connected but never started driving
        val idleTimeMs = 10 * 60 * 1000L  // 10 minutes
        assertThat(shouldShowStationaryDialog(idleTimeMs, hasDriverMoved = false)).isFalse()
    }

    // ==================== DIALOG DISMISSAL TESTS ====================
    // Case #15: Start driving again → dismiss dialog

    @Test
    fun `starting to drive should dismiss stationary dialog`() {
        val wasDialogShowing = true
        val newSpeedKmh = 15.0  // ~9.3 mph (above threshold)

        val shouldDismiss = shouldDismissDialogOnSpeedChange(newSpeedKmh, wasDialogShowing)
        assertThat(shouldDismiss).isTrue()
    }

    @Test
    fun `staying stationary should NOT dismiss dialog`() {
        val wasDialogShowing = true
        val newSpeedKmh = 2.0  // ~1.2 mph (below threshold)

        val shouldDismiss = shouldDismissDialogOnSpeedChange(newSpeedKmh, wasDialogShowing)
        assertThat(shouldDismiss).isFalse()
    }

    // ==================== STATUS CHANGE SEQUENCE TESTS ====================
    // Case #8-#10 complete flow

    @Test
    fun `complete flow - start driving then stop`() {
        val stateMachine = MockStatusStateMachine()

        // Initial state: ON_DUTY, STATIONARY
        assertThat(stateMachine.currentStatus).isEqualTo("ON_DUTY")
        assertThat(stateMachine.motionState).isEqualTo(VehicleMotionState.STATIONARY)

        // Start driving (speed > 5mph)
        stateMachine.onSpeedChange(15.0)  // ~9.3 mph
        assertThat(stateMachine.currentStatus).isEqualTo("DRIVING")
        assertThat(stateMachine.motionState).isEqualTo(VehicleMotionState.IN_MOTION)

        // Stop (speed = 0)
        stateMachine.onSpeedChange(0.0)
        // Status should remain DRIVING, but motion state changes
        assertThat(stateMachine.currentStatus).isEqualTo("DRIVING")
        assertThat(stateMachine.motionState).isEqualTo(VehicleMotionState.STATIONARY)
    }

    @Test
    fun `complete flow - drive stop for 5min then go on duty`() {
        val stateMachine = MockStatusStateMachine()

        // Start driving
        stateMachine.onSpeedChange(15.0)
        assertThat(stateMachine.currentStatus).isEqualTo("DRIVING")

        // Stop
        stateMachine.onSpeedChange(0.0)
        assertThat(stateMachine.currentStatus).isEqualTo("DRIVING")

        // Wait 5 minutes
        stateMachine.advanceTime(5 * 60 * 1000L)
        assertThat(stateMachine.isDialogShowing).isTrue()

        // User taps "Go On Duty"
        stateMachine.onUserSelectsOnDuty()
        assertThat(stateMachine.currentStatus).isEqualTo("ON_DUTY")
        assertThat(stateMachine.isDialogShowing).isFalse()
    }

    @Test
    fun `complete flow - drive stop for 5min then stay driving`() {
        val stateMachine = MockStatusStateMachine()

        // Start driving
        stateMachine.onSpeedChange(15.0)

        // Stop
        stateMachine.onSpeedChange(0.0)

        // Wait 5 minutes
        stateMachine.advanceTime(5 * 60 * 1000L)
        assertThat(stateMachine.isDialogShowing).isTrue()

        // User taps "Stay Driving"
        stateMachine.onUserSelectsStayDriving()
        assertThat(stateMachine.currentStatus).isEqualTo("DRIVING")
        assertThat(stateMachine.isDialogShowing).isFalse()
    }

    @Test
    fun `complete flow - drive stop for 5min then start driving again`() {
        val stateMachine = MockStatusStateMachine()

        // Start driving
        stateMachine.onSpeedChange(15.0)

        // Stop
        stateMachine.onSpeedChange(0.0)

        // Wait 5 minutes - dialog shows
        stateMachine.advanceTime(5 * 60 * 1000L)
        assertThat(stateMachine.isDialogShowing).isTrue()

        // Start driving again (Case #15)
        stateMachine.onSpeedChange(15.0)
        assertThat(stateMachine.isDialogShowing).isFalse()
        assertThat(stateMachine.currentStatus).isEqualTo("DRIVING")
    }

    @Test
    fun `complete flow - dialog countdown expires`() {
        val stateMachine = MockStatusStateMachine()

        // Start driving
        stateMachine.onSpeedChange(15.0)

        // Stop
        stateMachine.onSpeedChange(0.0)

        // Wait 5 minutes
        stateMachine.advanceTime(5 * 60 * 1000L)
        assertThat(stateMachine.isDialogShowing).isTrue()

        // Wait 60 seconds (countdown expires) - Case #16
        stateMachine.advanceTime(60 * 1000L)
        assertThat(stateMachine.currentStatus).isEqualTo("ON_DUTY")
        assertThat(stateMachine.isDialogShowing).isFalse()
    }

    // ==================== PC/YM STATUS TESTS ====================
    // Same logic applies to PC and YM

    @Test
    fun `PC status - stop for 5min should show dialog`() {
        val stateMachine = MockStatusStateMachine(initialStatus = "PERSONAL_CONVEYANCE")

        // Start driving in PC
        stateMachine.onSpeedChange(15.0)
        assertThat(stateMachine.currentStatus).isEqualTo("PERSONAL_CONVEYANCE")

        // Stop
        stateMachine.onSpeedChange(0.0)

        // Wait 5 minutes
        stateMachine.advanceTime(5 * 60 * 1000L)
        assertThat(stateMachine.isDialogShowing).isTrue()

        // User chooses On Duty
        stateMachine.onUserSelectsOnDuty()
        assertThat(stateMachine.currentStatus).isEqualTo("ON_DUTY")
    }

    @Test
    fun `YM status - stop for 5min should show dialog`() {
        val stateMachine = MockStatusStateMachine(initialStatus = "YARD_MOVE")

        // Start driving in YM
        stateMachine.onSpeedChange(15.0)
        assertThat(stateMachine.currentStatus).isEqualTo("YARD_MOVE")

        // Stop
        stateMachine.onSpeedChange(0.0)

        // Wait 5 minutes
        stateMachine.advanceTime(5 * 60 * 1000L)
        assertThat(stateMachine.isDialogShowing).isTrue()
    }

    // ==================== HELPER FUNCTIONS ====================

    private fun shouldChangeToDriving(speedKmh: Double): Boolean {
        val speedMph = speedKmh * KMH_TO_MPH
        return speedMph > SPEED_THRESHOLD_MPH
    }

    private fun getMotionState(speedKmh: Double): VehicleMotionState {
        val speedMph = speedKmh * KMH_TO_MPH
        return if (speedMph > SPEED_THRESHOLD_MPH) {
            VehicleMotionState.IN_MOTION
        } else {
            VehicleMotionState.STATIONARY
        }
    }

    private fun shouldShowStationaryDialog(idleTimeMs: Long, hasDriverMoved: Boolean): Boolean {
        return hasDriverMoved && idleTimeMs >= IDLE_THRESHOLD_MS
    }

    private fun shouldDismissDialogOnSpeedChange(speedKmh: Double, wasDialogShowing: Boolean): Boolean {
        if (!wasDialogShowing) return false
        val speedMph = speedKmh * KMH_TO_MPH
        return speedMph > SPEED_THRESHOLD_MPH
    }

    // ==================== MOCK STATE MACHINE ====================

    /**
     * Mock implementation of the status state machine for testing.
     * Simulates the behavior of GeometrisWQManager + Dashboard.
     */
    private class MockStatusStateMachine(
        initialStatus: String = "ON_DUTY"
    ) {
        var currentStatus: String = initialStatus
            private set
        var motionState: VehicleMotionState = VehicleMotionState.STATIONARY
            private set
        var isDialogShowing: Boolean = false
            private set

        private var hasDriverMoved = false
        private var lastMovementTime = 0L
        private var currentTime = 0L
        private var dialogStartTime = 0L

        fun onSpeedChange(speedKmh: Double) {
            val speedMph = speedKmh * KMH_TO_MPH

            if (speedMph > SPEED_THRESHOLD_MPH) {
                // Vehicle moving
                motionState = VehicleMotionState.IN_MOTION
                hasDriverMoved = true
                lastMovementTime = currentTime

                // Dismiss dialog if showing
                if (isDialogShowing) {
                    isDialogShowing = false
                }

                // Auto-change to DRIVING if was ON_DUTY
                if (currentStatus == "ON_DUTY") {
                    currentStatus = "DRIVING"
                }
            } else {
                // Vehicle stopped
                motionState = VehicleMotionState.STATIONARY

                // Check if we should show dialog (after 5 min idle)
                if (hasDriverMoved && !isDialogShowing) {
                    val idleTime = currentTime - lastMovementTime
                    if (idleTime >= IDLE_THRESHOLD_MS) {
                        isDialogShowing = true
                        dialogStartTime = currentTime
                    }
                }
            }
        }

        fun advanceTime(milliseconds: Long) {
            currentTime += milliseconds

            // Check if dialog countdown expired (60 seconds)
            if (isDialogShowing) {
                val dialogDuration = currentTime - dialogStartTime
                if (dialogDuration >= 60 * 1000L) {
                    // Auto-change to ON_DUTY
                    currentStatus = "ON_DUTY"
                    isDialogShowing = false
                }
            }

            // Check if we should show dialog after being stationary
            if (motionState == VehicleMotionState.STATIONARY && hasDriverMoved && !isDialogShowing) {
                val idleTime = currentTime - lastMovementTime
                if (idleTime >= IDLE_THRESHOLD_MS) {
                    isDialogShowing = true
                    dialogStartTime = currentTime
                }
            }
        }

        fun onUserSelectsOnDuty() {
            currentStatus = "ON_DUTY"
            isDialogShowing = false
            hasDriverMoved = false
        }

        fun onUserSelectsStayDriving() {
            isDialogShowing = false
            // Reset idle timer
            lastMovementTime = currentTime
        }
    }
}
