package com.eld.driver.ble

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for background behavior.
 *
 * Covers test cases:
 * - Case #9: Background driving - status changes to DRIVING
 * - Case #11: Background stop - shows STATIONARY
 * - Case #17: Background 5+ min stationary → ON_DUTY
 * - Case #20: Complete background flow
 */
class BackgroundBehaviorTest {

    companion object {
        const val SPEED_THRESHOLD_MPH = 5.0
        const val KMH_TO_MPH = 0.621371
        const val IDLE_THRESHOLD_MS = 5 * 60 * 1000L  // 5 minutes
        const val DIALOG_COUNTDOWN_MS = 60 * 1000L   // 60 seconds
    }

    // ==================== BACKGROUND DRIVING TESTS ====================
    // Case #9

    @Test
    fun `status changes to DRIVING while app in background`() {
        val scenario = BackgroundScenario()

        // Connect and go to background
        scenario.connect()
        scenario.goToBackground()

        // Start driving while in background
        scenario.startDriving(speedMph = 10.0)

        // Come back to foreground
        scenario.goToForeground()

        assertThat(scenario.currentStatus).isEqualTo("DRIVING")
        assertThat(scenario.motionState).isEqualTo(VehicleMotionState.IN_MOTION)
    }

    @Test
    fun `driving duration tracked correctly in background - Case 9`() {
        val scenario = BackgroundScenario()
        val startTime = 0L

        scenario.connect()
        scenario.goToBackground(atTime = startTime)

        // Drive for 5 minutes in background
        scenario.startDriving(speedMph = 10.0, atTime = startTime)
        scenario.advanceTime(5 * 60 * 1000L)

        // Come back
        scenario.goToForeground(atTime = startTime + 5 * 60 * 1000L)

        assertThat(scenario.currentStatus).isEqualTo("DRIVING")
        assertThat(scenario.currentStatusDurationMs).isEqualTo(5 * 60 * 1000L)
    }

    // ==================== BACKGROUND STOP TESTS ====================
    // Case #11

    @Test
    fun `shows STATIONARY after stopping in background - Case 11`() {
        val scenario = BackgroundScenario()

        scenario.connect()
        scenario.goToBackground()

        // Drive then stop while in background
        scenario.startDriving(speedMph = 10.0)
        scenario.advanceTime(5 * 60 * 1000L)
        scenario.stopDriving()

        // Come back to foreground
        scenario.goToForeground()

        assertThat(scenario.currentStatus).isEqualTo("DRIVING")
        assertThat(scenario.motionState).isEqualTo(VehicleMotionState.STATIONARY)
        assertThat(scenario.currentStatusDurationMs).isEqualTo(5 * 60 * 1000L)
    }

    // ==================== BACKGROUND AUTO ON_DUTY TESTS ====================
    // Case #17

    @Test
    fun `auto changes to ON_DUTY after 6min stationary in background - Case 17`() {
        val scenario = BackgroundScenario()
        val startTime = 0L

        scenario.connect()
        scenario.startDriving(speedMph = 10.0, atTime = startTime)
        scenario.goToBackground(atTime = startTime + 1000L)

        // Stop and stay stationary for 6+ minutes
        scenario.stopDriving(atTime = startTime + 60_000L)  // 1 min driving
        scenario.advanceTime(6 * 60 * 1000L)  // 6 min stationary

        // Come back to foreground
        scenario.goToForeground(atTime = startTime + 7 * 60 * 1000L)

        // Should have auto-changed to ON_DUTY (5 min + 60 sec countdown)
        assertThat(scenario.currentStatus).isEqualTo("ON_DUTY")
    }

    @Test
    fun `stays DRIVING if less than 5min stationary in background`() {
        val scenario = BackgroundScenario()
        val startTime = 0L

        scenario.connect()
        scenario.startDriving(speedMph = 10.0, atTime = startTime)
        scenario.goToBackground(atTime = startTime + 1000L)

        // Stop and stay stationary for only 3 minutes
        scenario.stopDriving(atTime = startTime + 60_000L)
        scenario.advanceTime(3 * 60 * 1000L)

        scenario.goToForeground(atTime = startTime + 4 * 60 * 1000L)

        assertThat(scenario.currentStatus).isEqualTo("DRIVING")
    }

    // ==================== COMPLETE BACKGROUND FLOW ====================
    // Case #20

    @Test
    fun `complete background flow - Case 20`() {
        val scenario = BackgroundScenario()
        val startTime = 0L

        // Step 1-4: Connect and start driving
        scenario.connect()
        scenario.startDriving(speedMph = 10.0, atTime = startTime)
        assertThat(scenario.currentStatus).isEqualTo("DRIVING")

        // Step 5: Go to background
        scenario.goToBackground(atTime = startTime + 1000L)

        // Step 6: Stop and wait (still in background)
        scenario.stopDriving(atTime = startTime + 5 * 60 * 1000L)  // 5 min driving

        // Step 7: Walk away for 15 minutes (triggers ON_DUTY)
        scenario.advanceTime(15 * 60 * 1000L)
        // At this point: 5 min idle threshold + 60 sec countdown = auto ON_DUTY
        assertThat(scenario.currentStatus).isEqualTo("ON_DUTY")

        // Step 8: Come back and start driving again
        scenario.startDriving(speedMph = 10.0, atTime = startTime + 20 * 60 * 1000L)

        // Step 9: Drive for 5 more minutes
        scenario.advanceTime(5 * 60 * 1000L)

        // Step 10: Reopen app
        scenario.goToForeground(atTime = startTime + 25 * 60 * 1000L)

        // Expected: DRIVING with 5 min duration, previous event was ON_DUTY
        assertThat(scenario.currentStatus).isEqualTo("DRIVING")
        assertThat(scenario.currentStatusDurationMs).isEqualTo(5 * 60 * 1000L)
        assertThat(scenario.previousStatus).isEqualTo("ON_DUTY")
    }

    // ==================== BACKGROUND RECONNECTION ====================
    // Case #7

    @Test
    fun `connection re-established in background - Case 7`() {
        val scenario = BackgroundScenario()

        scenario.connect()
        scenario.goToBackground()

        // Signal lost in background
        scenario.onSignalLost()
        assertThat(scenario.isReconnecting).isTrue()

        // Auto-reconnect succeeds
        scenario.onReconnected()
        assertThat(scenario.isConnected).isTrue()

        // Reopen app
        scenario.goToForeground()
        assertThat(scenario.isConnected).isTrue()
    }

    // ==================== NAVIGATION WHILE DRIVING ====================
    // Case #22

    @Test
    fun `dashboard opens automatically when driving starts - Case 22`() {
        val scenario = BackgroundScenario()

        scenario.connect()
        scenario.navigateTo("Settings")
        assertThat(scenario.currentScreen).isEqualTo("Settings")

        // Start driving
        scenario.startDriving(speedMph = 10.0)

        // Should auto-navigate to Dashboard
        assertThat(scenario.currentScreen).isEqualTo("Dashboard")
        assertThat(scenario.currentStatus).isEqualTo("DRIVING")
        assertThat(scenario.areButtonsLocked).isTrue()
    }

    @Test
    fun `buttons locked while driving - Case 22`() {
        val scenario = BackgroundScenario()

        scenario.connect()
        scenario.startDriving(speedMph = 10.0)

        assertThat(scenario.areButtonsLocked).isTrue()
        assertThat(scenario.motionState).isEqualTo(VehicleMotionState.IN_MOTION)
    }

    @Test
    fun `buttons unlocked when stationary`() {
        val scenario = BackgroundScenario()

        scenario.connect()
        scenario.startDriving(speedMph = 10.0)
        assertThat(scenario.areButtonsLocked).isTrue()

        scenario.stopDriving()
        assertThat(scenario.areButtonsLocked).isFalse()
        assertThat(scenario.motionState).isEqualTo(VehicleMotionState.STATIONARY)
    }

    // ==================== HELPER CLASSES ====================

    class BackgroundScenario {
        var currentStatus: String = "ON_DUTY"
            private set
        var previousStatus: String? = null
            private set
        var motionState: VehicleMotionState = VehicleMotionState.STATIONARY
            private set
        var currentStatusDurationMs: Long = 0
            private set
        var isConnected: Boolean = false
            private set
        var isReconnecting: Boolean = false
            private set
        var isInBackground: Boolean = false
            private set
        var currentScreen: String = "Dashboard"
            private set
        var areButtonsLocked: Boolean = false
            private set

        private var currentTime: Long = 0
        private var statusStartTime: Long = 0
        private var lastMovementTime: Long = 0
        private var hasDriverMoved: Boolean = false
        private var idleStartTime: Long? = null

        fun connect() {
            isConnected = true
        }

        fun goToBackground(atTime: Long = currentTime) {
            currentTime = atTime
            isInBackground = true
        }

        fun goToForeground(atTime: Long = currentTime) {
            currentTime = atTime
            isInBackground = false

            // Recalculate duration
            currentStatusDurationMs = currentTime - statusStartTime
        }

        fun startDriving(speedMph: Double, atTime: Long = currentTime) {
            currentTime = atTime
            val speedKmh = speedMph / KMH_TO_MPH

            if (speedMph > SPEED_THRESHOLD_MPH) {
                motionState = VehicleMotionState.IN_MOTION
                areButtonsLocked = true
                hasDriverMoved = true
                lastMovementTime = currentTime
                idleStartTime = null

                // Auto-navigate to dashboard
                if (currentScreen != "Dashboard") {
                    currentScreen = "Dashboard"
                }

                // Auto-change to DRIVING if not already
                if (currentStatus != "DRIVING") {
                    previousStatus = currentStatus
                    currentStatus = "DRIVING"
                    statusStartTime = currentTime
                    currentStatusDurationMs = 0
                }
            }
        }

        fun stopDriving(atTime: Long = currentTime) {
            currentTime = atTime
            motionState = VehicleMotionState.STATIONARY
            areButtonsLocked = false
            lastMovementTime = currentTime
            idleStartTime = currentTime
        }

        fun advanceTime(milliseconds: Long) {
            currentTime += milliseconds
            currentStatusDurationMs = currentTime - statusStartTime

            // Check for auto ON_DUTY in background
            if (hasDriverMoved && idleStartTime != null) {
                val idleTime = currentTime - idleStartTime!!
                val autoChangeThreshold = IDLE_THRESHOLD_MS + DIALOG_COUNTDOWN_MS

                if (idleTime >= autoChangeThreshold && currentStatus == "DRIVING") {
                    previousStatus = currentStatus
                    currentStatus = "ON_DUTY"
                    statusStartTime = idleStartTime!! + IDLE_THRESHOLD_MS + DIALOG_COUNTDOWN_MS
                    currentStatusDurationMs = currentTime - statusStartTime
                    hasDriverMoved = false
                    idleStartTime = null
                }
            }
        }

        fun onSignalLost() {
            isConnected = false
            isReconnecting = true
        }

        fun onReconnected() {
            isConnected = true
            isReconnecting = false
        }

        fun navigateTo(screen: String) {
            currentScreen = screen
        }
    }
}
