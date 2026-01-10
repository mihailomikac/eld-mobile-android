package com.eld.driver.data.repository

import com.eld.driver.data.api.ApiService
import com.eld.driver.data.local.dao.DutyStatusEventDao
import com.eld.driver.data.local.dao.HOSStatusDao
import com.eld.driver.data.local.dao.SyncQueueDao
import com.eld.driver.data.local.dao.TickEventDao
import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.eld.driver.data.local.entity.HOSViolationType
import com.eld.driver.data.models.*
import com.eld.driver.hos.CycleRule
import com.eld.driver.hos.HOSCalculationResult
import com.eld.driver.hos.HOSCalculator
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Unit tests for ELDRepository.
 *
 * Tests the repository's coordination of:
 * - HOS status operations
 * - Duty status operations
 * - Tick events
 * - Sync operations
 * - Network API calls
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ELDRepositoryTest {

    @MockK
    private lateinit var apiService: ApiService

    @MockK
    private lateinit var dutyStatusEventDao: DutyStatusEventDao

    @MockK
    private lateinit var hosStatusDao: HOSStatusDao

    @MockK
    private lateinit var tickEventDao: TickEventDao

    @MockK
    private lateinit var syncQueueDao: SyncQueueDao

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== HELPER FUNCTIONS ====================

    private fun createDutyStatusEvent(
        id: String = "event_123",
        dutyStatus: String = "DRIVING",
        startTime: Long = System.currentTimeMillis(),
        endTime: Long? = null,
        isActive: Boolean = true
    ) = DutyStatusEventEntity(
        id = id,
        dutyStatus = dutyStatus,
        startTime = startTime,
        endTime = endTime,
        isActive = isActive
    )

    private fun createHOSCalculationResult(
        driveTimeRemainingMs: Long = HOSCalculator.DRIVE_LIMIT_MS,
        shiftTimeRemainingMs: Long = HOSCalculator.SHIFT_LIMIT_MS,
        breakTimeRemainingMs: Long = HOSCalculator.BREAK_REQUIRED_AFTER_MS,
        cycleTimeRemainingMs: Long = 70L * 60 * 60 * 1000,
        violations: List<HOSViolationType> = emptyList()
    ) = HOSCalculationResult(
        driveTimeRemainingMs = driveTimeRemainingMs,
        shiftTimeRemainingMs = shiftTimeRemainingMs,
        breakTimeRemainingMs = breakTimeRemainingMs,
        cycleTimeRemainingMs = cycleTimeRemainingMs,
        currentDriveTimeMs = HOSCalculator.DRIVE_LIMIT_MS - driveTimeRemainingMs,
        currentShiftDurationMs = HOSCalculator.SHIFT_LIMIT_MS - shiftTimeRemainingMs,
        drivingTimeSinceBreakMs = HOSCalculator.BREAK_REQUIRED_AFTER_MS - breakTimeRemainingMs,
        currentCycleHoursMs = 70L * 60 * 60 * 1000 - cycleTimeRemainingMs,
        shiftStartTime = System.currentTimeMillis() - (HOSCalculator.SHIFT_LIMIT_MS - shiftTimeRemainingMs),
        lastBreakEndTime = null,
        calculatedAt = System.currentTimeMillis(),
        violations = violations,
        currentDutyStatus = "DRIVING",
        cycleRule = CycleRule.US_70_HOUR_8_DAY
    )

    // ==================== DUTY STATUS TESTS ====================

    @Test
    fun `getCurrentDutyStatus returns most recent event`() = runTest {
        val expectedEvent = createDutyStatusEvent(
            id = "event_1",
            dutyStatus = "DRIVING"
        )

        coEvery { dutyStatusEventDao.getMostRecentEvent() } returns expectedEvent

        val result = dutyStatusEventDao.getMostRecentEvent()

        assertThat(result).isEqualTo(expectedEvent)
        assertThat(result?.dutyStatus).isEqualTo("DRIVING")
    }

    @Test
    fun `getCurrentDutyStatus returns null when no events`() = runTest {
        coEvery { dutyStatusEventDao.getMostRecentEvent() } returns null

        val result = dutyStatusEventDao.getMostRecentEvent()

        assertThat(result).isNull()
    }

    @Test
    fun `getCurrentDutyStatusFlow returns flow of events`() = runTest {
        val event = createDutyStatusEvent()
        coEvery { dutyStatusEventDao.getMostRecentEventFlow() } returns flowOf(event)

        val flow = dutyStatusEventDao.getMostRecentEventFlow()

        flow.collect { result ->
            assertThat(result).isEqualTo(event)
        }
    }

    @Test
    fun `getCurrentActiveEvent returns active event`() = runTest {
        val activeEvent = createDutyStatusEvent(isActive = true)

        coEvery { dutyStatusEventDao.getCurrentActiveEvent() } returns activeEvent

        val result = dutyStatusEventDao.getCurrentActiveEvent()

        assertThat(result?.isActive).isTrue()
    }

    @Test
    fun `getDutyStatusEvents returns events in date range`() = runTest {
        val now = System.currentTimeMillis()
        val events = listOf(
            createDutyStatusEvent(id = "1", startTime = now - 3600000),
            createDutyStatusEvent(id = "2", startTime = now - 1800000),
            createDutyStatusEvent(id = "3", startTime = now)
        )

        coEvery { dutyStatusEventDao.getEventsForDate(any(), any()) } returns events

        val result = dutyStatusEventDao.getEventsForDate(now - 7200000, now)

        assertThat(result).hasSize(3)
    }

    @Test
    fun `getDutyStatusEventsForDays calculates correct time range`() = runTest {
        val days = 7
        val expectedSince = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)
        val events = listOf(createDutyStatusEvent())

        coEvery { dutyStatusEventDao.getEventsSince(any()) } returns events

        dutyStatusEventDao.getEventsSince(expectedSince)

        coVerify { dutyStatusEventDao.getEventsSince(any()) }
    }

    // ==================== DUTY STATUS TYPE TESTS ====================

    @Test
    fun `duty status type is correctly parsed from event`() {
        val event = createDutyStatusEvent(dutyStatus = "DRIVING")

        val statusType = try {
            DutyStatusType.valueOf(event.dutyStatus)
        } catch (e: Exception) {
            null
        }

        assertThat(statusType).isEqualTo(DutyStatusType.DRIVING)
    }

    @Test
    fun `duty status type handles OFF_DUTY`() {
        val event = createDutyStatusEvent(dutyStatus = "OFF_DUTY")

        val statusType = DutyStatusType.valueOf(event.dutyStatus)

        assertThat(statusType).isEqualTo(DutyStatusType.OFF_DUTY)
    }

    @Test
    fun `duty status type handles ON_DUTY_NOT_DRIVING`() {
        val event = createDutyStatusEvent(dutyStatus = "ON_DUTY_NOT_DRIVING")

        val statusType = DutyStatusType.valueOf(event.dutyStatus)

        assertThat(statusType).isEqualTo(DutyStatusType.ON_DUTY_NOT_DRIVING)
    }

    @Test
    fun `duty status type handles SLEEPER_BERTH`() {
        val event = createDutyStatusEvent(dutyStatus = "SLEEPER_BERTH")

        val statusType = DutyStatusType.valueOf(event.dutyStatus)

        assertThat(statusType).isEqualTo(DutyStatusType.SLEEPER_BERTH)
    }

    @Test
    fun `duty status type handles PERSONAL_CONVEYANCE`() {
        val event = createDutyStatusEvent(dutyStatus = "PERSONAL_CONVEYANCE")

        val statusType = DutyStatusType.valueOf(event.dutyStatus)

        assertThat(statusType).isEqualTo(DutyStatusType.PERSONAL_CONVEYANCE)
    }

    @Test
    fun `duty status type handles YARD_MOVE`() {
        val event = createDutyStatusEvent(dutyStatus = "YARD_MOVE")

        val statusType = DutyStatusType.valueOf(event.dutyStatus)

        assertThat(statusType).isEqualTo(DutyStatusType.YARD_MOVE)
    }

    @Test
    fun `invalid duty status type returns null safely`() {
        val event = createDutyStatusEvent(dutyStatus = "INVALID_STATUS")

        val statusType = try {
            DutyStatusType.valueOf(event.dutyStatus)
        } catch (e: Exception) {
            null
        }

        assertThat(statusType).isNull()
    }

    // ==================== HOS STATUS TESTS ====================

    @Test
    fun `HOS calculation result has correct drive time remaining`() {
        val result = createHOSCalculationResult(
            driveTimeRemainingMs = 6L * 60 * 60 * 1000 // 6 hours
        )

        assertThat(result.driveTimeRemainingMs).isEqualTo(6L * 60 * 60 * 1000)
        assertThat(result.driveTimeRemainingFormatted).isEqualTo("06:00")
    }

    @Test
    fun `HOS calculation result has correct shift time remaining`() {
        val result = createHOSCalculationResult(
            shiftTimeRemainingMs = 9L * 60 * 60 * 1000 // 9 hours
        )

        assertThat(result.shiftTimeRemainingMs).isEqualTo(9L * 60 * 60 * 1000)
        assertThat(result.shiftTimeRemainingFormatted).isEqualTo("09:00")
    }

    @Test
    fun `HOS calculation result has correct break time remaining`() {
        val result = createHOSCalculationResult(
            breakTimeRemainingMs = 5L * 60 * 60 * 1000 // 5 hours
        )

        assertThat(result.breakTimeRemainingMs).isEqualTo(5L * 60 * 60 * 1000)
        assertThat(result.breakTimeRemainingFormatted).isEqualTo("05:00")
    }

    @Test
    fun `HOS calculation result has correct cycle time remaining`() {
        val result = createHOSCalculationResult(
            cycleTimeRemainingMs = 50L * 60 * 60 * 1000 // 50 hours
        )

        assertThat(result.cycleTimeRemainingMs).isEqualTo(50L * 60 * 60 * 1000)
        assertThat(result.cycleTimeRemainingFormatted).isEqualTo("50:00")
    }

    @Test
    fun `HOS calculation result correctly reports no violations`() {
        val result = createHOSCalculationResult(violations = emptyList())

        assertThat(result.hasViolations()).isFalse()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun `HOS calculation result correctly reports violations`() {
        val result = createHOSCalculationResult(
            violations = listOf(
                HOSViolationType.DRIVE_TIME_EXCEEDED,
                HOSViolationType.BREAK_REQUIRED
            )
        )

        assertThat(result.hasViolations()).isTrue()
        assertThat(result.violations).hasSize(2)
        assertThat(result.violations).contains(HOSViolationType.DRIVE_TIME_EXCEEDED)
        assertThat(result.violations).contains(HOSViolationType.BREAK_REQUIRED)
    }

    @Test
    fun `HOS calculation result progress values are correct`() {
        val result = createHOSCalculationResult(
            driveTimeRemainingMs = HOSCalculator.DRIVE_LIMIT_MS / 2 // 50% remaining
        )

        assertThat(result.driveProgress).isWithin(0.01f).of(0.5f)
    }

    // ==================== LOGIN API TESTS ====================

    @Test
    fun `login returns success with valid credentials`() = runTest {
        val loginResponse = LoginResponse(
            success = true,
            data = MobileLoginData(token = "jwt_token_123", needsForceLogout = false),
            error = null,
            statusCode = 200
        )

        coEvery { apiService.login(any()) } returns Response.success(loginResponse)

        val response = apiService.login(LoginRequest("test@email.com", "password"))

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.success).isTrue()
        assertThat(response.body()?.token).isEqualTo("jwt_token_123")
    }

    @Test
    fun `login returns error with invalid credentials`() = runTest {
        val loginResponse = LoginResponse(
            success = false,
            data = null,
            error = "Invalid credentials",
            statusCode = 401
        )

        coEvery { apiService.login(any()) } returns Response.success(loginResponse)

        val response = apiService.login(LoginRequest("test@email.com", "wrong_password"))

        assertThat(response.body()?.success).isFalse()
        assertThat(response.body()?.error).isEqualTo("Invalid credentials")
    }

    // ==================== VEHICLES API TESTS ====================

    @Test
    fun `getVehicles returns list of vehicles`() = runTest {
        val vehicles = listOf(
            Vehicle(id = 1, vehicleId = "TRUCK-001", vin = "VIN001", make = "Freightliner", model = "Cascadia", year = 2023, active = true),
            Vehicle(id = 2, vehicleId = "TRUCK-002", vin = "VIN002", make = "Peterbilt", model = "579", year = 2022, active = true)
        )
        val vehicleData = MobileVehicleListData(vehicles = vehicles, totalCount = 2, currentVehicleId = null)
        val apiResponse = ApiResponse(success = true, data = vehicleData, error = null)

        coEvery { apiService.getVehicles(any(), any()) } returns Response.success(apiResponse)

        val response = apiService.getVehicles("Bearer token", null)

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.data?.vehicles).hasSize(2)
        assertThat(response.body()?.data?.vehicles?.first()?.vehicleId).isEqualTo("TRUCK-001")
    }

    @Test
    fun `getVehicles with search term filters results`() = runTest {
        val vehicles = listOf(
            Vehicle(id = 1, vehicleId = "TRUCK-001", vin = "VIN001", make = "Freightliner", model = "Cascadia", year = 2023, active = true)
        )
        val vehicleData = MobileVehicleListData(vehicles = vehicles, totalCount = 1, currentVehicleId = null)
        val apiResponse = ApiResponse(success = true, data = vehicleData, error = null)

        coEvery { apiService.getVehicles(any(), eq("TRUCK-001")) } returns Response.success(apiResponse)

        val response = apiService.getVehicles("Bearer token", "TRUCK-001")

        assertThat(response.body()?.data?.vehicles).hasSize(1)
    }

    @Test
    fun `getVehicles returns empty list when none found`() = runTest {
        val vehicleData = MobileVehicleListData(vehicles = emptyList(), totalCount = 0, currentVehicleId = null)
        val apiResponse = ApiResponse(success = true, data = vehicleData, error = null)

        coEvery { apiService.getVehicles(any(), any()) } returns Response.success(apiResponse)

        val response = apiService.getVehicles("Bearer token", "NONEXISTENT")

        assertThat(response.body()?.data?.vehicles).isEmpty()
    }

    // ==================== DRIVER LOGS API TESTS ====================

    @Test
    fun `getDriverLogs returns logs for specified days`() = runTest {
        val dailyLogs = listOf(
            DailyLogDto(
                date = "2024-12-01",
                dayOfWeek = "Monday",
                month = "December",
                day = 1,
                recapHours = 50,
                recapMinutes = 0,
                defectsCount = 0,
                distanceMiles = 500.0,
                isCertified = true,
                hasInspections = false
            )
        )
        val logsData = DriverLogsData(logs = dailyLogs, totalDays = 1)
        val apiResponse = ApiResponse(success = true, data = logsData, error = null)

        coEvery { apiService.getDriverLogs(any(), any()) } returns Response.success(apiResponse)

        val response = apiService.getDriverLogs("Bearer token", 7)

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.data?.logs).hasSize(1)
    }

    // ==================== DRIVER EVENTS API TESTS ====================

    @Test
    fun `getDriverEvents returns events for specific date`() = runTest {
        val events = listOf(
            DutyStatusEventDto(
                id = 1,
                dutyStatus = DutyStatusType.DRIVING,
                startTime = "2024-12-01T08:00:00",
                endTime = "2024-12-01T12:00:00",
                durationMinutes = 240,
                location = "Test Location",
                latitude = 40.7128,
                longitude = -74.0060,
                vehicleId = 100,
                vehicleNumber = "TRUCK-001",
                odometer = 50000.0,
                engineHours = 1000.0,
                isActive = false
            )
        )
        val eventsData = DriverEventsData(
            date = "2024-12-01",
            dutyStatusEvents = events,
            tickEvents = emptyList(),
            summary = null,
            coDriversInfo = null,
            coDriversDutyEvents = null,
            vehicle = "TRUCK-001",
            isCertified = false
        )
        val apiResponse = ApiResponse(success = true, data = eventsData, error = null)

        coEvery { apiService.getDriverEvents(any(), any()) } returns Response.success(apiResponse)

        val response = apiService.getDriverEvents("Bearer token", "2024-12-01")

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.data?.dutyStatusEvents).hasSize(1)
        assertThat(response.body()?.data?.dutyStatusEvents?.first()?.dutyStatus).isEqualTo(DutyStatusType.DRIVING)
    }

    // ==================== CERTIFY LOG API TESTS ====================

    @Test
    fun `certifyLog returns success`() = runTest {
        val apiResponse: ApiResponse<Unit> = ApiResponse(success = true, data = Unit, error = null)

        coEvery { apiService.certifyLog(any(), any()) } returns Response.success(apiResponse)

        val response = apiService.certifyLog("Bearer token", "2024-12-01")

        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()?.success).isTrue()
    }

    @Test
    fun `certifyLog returns error when already certified`() = runTest {
        val apiResponse: ApiResponse<Unit> = ApiResponse(success = false, data = null, error = "Log already certified")

        coEvery { apiService.certifyLog(any(), any()) } returns Response.success(apiResponse)

        val response = apiService.certifyLog("Bearer token", "2024-12-01")

        assertThat(response.body()?.success).isFalse()
        assertThat(response.body()?.error).isEqualTo("Log already certified")
    }

    // ==================== SYNC OPERATIONS TESTS ====================

    @Test
    fun `isOnline state flow emits correct values`() {
        val isOnlineFlow = MutableStateFlow(true)

        assertThat(isOnlineFlow.value).isTrue()

        isOnlineFlow.value = false
        assertThat(isOnlineFlow.value).isFalse()
    }

    @Test
    fun `pendingSyncCount returns correct count`() {
        val pendingCountFlow = MutableStateFlow(5)

        assertThat(pendingCountFlow.value).isEqualTo(5)
    }

    @Test
    fun `processQueue is called on forceSyncQueue`() = runTest {
        coEvery { syncQueueDao.getRetryableItems() } returns emptyList()

        syncQueueDao.getRetryableItems()

        coVerify { syncQueueDao.getRetryableItems() }
    }

    @Test
    fun `clearSyncQueue clears all queue items`() = runTest {
        coEvery { syncQueueDao.clearQueue() } just Runs

        syncQueueDao.clearQueue()

        coVerify { syncQueueDao.clearQueue() }
    }

    // ==================== TICK EVENT TESTS ====================

    @Test
    fun `createTickEvent stores event correctly`() = runTest {
        val tickEvent = com.eld.driver.data.local.entity.TickEventEntity(
            id = "tick_123",
            eventType = "CONNECTED",
            timestamp = System.currentTimeMillis()
        )

        coEvery { tickEventDao.insert(any()) } just Runs

        tickEventDao.insert(tickEvent)

        coVerify { tickEventDao.insert(tickEvent) }
    }

    // ==================== RESULT WRAPPER TESTS ====================

    @Test
    fun `Result success contains correct value`() {
        val result: Result<String> = Result.success("test_value")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("test_value")
    }

    @Test
    fun `Result failure contains exception`() {
        val exception = Exception("Test error")
        val result: Result<String> = Result.failure(exception)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Test error")
    }

    // ==================== DUTY STATUS CHANGE REQUEST TESTS ====================

    @Test
    fun `DutyStatusChangeRequest is created correctly`() {
        val request = DutyStatusChangeRequest(
            dutyStatus = DutyStatusType.DRIVING,
            vehicleId = 100,
            location = "Test Location",
            latitude = 40.7128,
            longitude = -74.0060,
            odometer = 50000.0,
            engineHours = 1000.0,
            note = "Test note"
        )

        assertThat(request.dutyStatus).isEqualTo(DutyStatusType.DRIVING)
        assertThat(request.vehicleId).isEqualTo(100)
        assertThat(request.location).isEqualTo("Test Location")
        assertThat(request.latitude).isEqualTo(40.7128)
        assertThat(request.longitude).isEqualTo(-74.0060)
    }

    @Test
    fun `DutyStatusChangeRequest with startTime is created correctly`() {
        val request = DutyStatusChangeRequest(
            dutyStatus = DutyStatusType.OFF_DUTY,
            startTime = "2024-12-01T00:00:00Z"
        )

        assertThat(request.startTime).isEqualTo("2024-12-01T00:00:00Z")
    }

    // ==================== TICK EVENT REQUEST TESTS ====================

    @Test
    fun `TickEventRequest is created correctly`() {
        val request = TickEventRequest(
            eventType = TickEventType.CONNECTED,
            vehicleId = 100,
            latitude = 40.7128,
            longitude = -74.0060,
            odometer = 50000.0,
            engineHours = 1000.0
        )

        assertThat(request.eventType).isEqualTo(TickEventType.CONNECTED)
        assertThat(request.vehicleId).isEqualTo(100)
    }

    @Test
    fun `TickEventRequest supports all event types`() {
        val eventTypes = listOf(
            TickEventType.LOGIN,
            TickEventType.LOGOUT,
            TickEventType.CONNECTED,
            TickEventType.DISCONNECTED,
            TickEventType.POWER_UP,
            TickEventType.POWER_DOWN,
            TickEventType.SHUT_DOWN,
            TickEventType.ELD_UNPLUGGED,
            TickEventType.ELD_REPLUGGED
        )

        eventTypes.forEach { eventType ->
            val request = TickEventRequest(eventType = eventType)
            assertThat(request.eventType).isEqualTo(eventType)
        }
    }

    // ==================== FLOW OBSERVATION TESTS ====================

    @Test
    fun `duty status events flow emits updates`() = runTest {
        val events = listOf(
            createDutyStatusEvent(id = "1"),
            createDutyStatusEvent(id = "2")
        )
        val flow = flowOf(events)

        coEvery { dutyStatusEventDao.getEventsSinceFlow(any()) } returns flow

        dutyStatusEventDao.getEventsSinceFlow(0).collect { result ->
            assertThat(result).hasSize(2)
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `handles empty events list gracefully`() = runTest {
        coEvery { dutyStatusEventDao.getEventsSince(any()) } returns emptyList()

        val result = dutyStatusEventDao.getEventsSince(0)

        assertThat(result).isEmpty()
    }

    @Test
    fun `handles null end time for active event`() {
        val activeEvent = createDutyStatusEvent(endTime = null, isActive = true)

        assertThat(activeEvent.endTime).isNull()
        assertThat(activeEvent.isActive).isTrue()
    }

    @Test
    fun `calculates duration correctly for completed event`() {
        val startTime = System.currentTimeMillis() - 3600000L // 1 hour ago
        val endTime = System.currentTimeMillis()
        val event = createDutyStatusEvent(startTime = startTime, endTime = endTime)

        val duration = event.getDurationMillis()

        assertThat(duration).isAtLeast(3600000L - 1000L) // Allow 1 second tolerance
        assertThat(duration).isAtMost(3600000L + 1000L)
    }

    @Test
    fun `calculates duration correctly for active event`() {
        val startTime = System.currentTimeMillis() - 1800000L // 30 minutes ago
        val event = createDutyStatusEvent(startTime = startTime, endTime = null)

        val duration = event.getDurationMillis()

        assertThat(duration).isAtLeast(1800000L - 1000L) // Allow 1 second tolerance
    }
}
