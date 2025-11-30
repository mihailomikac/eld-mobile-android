package com.eld.driver.data.api

import com.eld.driver.data.models.*

object MockDataService {
    // Mock flag - set to false when backend is ready
    const val useMockData = true

    // Mock Vehicles
    fun getMockVehicles(): List<Vehicle> {
        return listOf(
            Vehicle(
                id = 1,
                vehicleId = "TRUCK-001",
                vin = "1HGBH41JXMN109186",
                make = "Freightliner",
                model = "Cascadia",
                year = 2022,
                active = true
            ),
            Vehicle(
                id = 2,
                vehicleId = "TRUCK-002",
                vin = "1HGBH41JXMN109187",
                make = "Peterbilt",
                model = "579",
                year = 2021,
                active = true
            ),
            Vehicle(
                id = 3,
                vehicleId = "TRUCK-003",
                vin = "1HGBH41JXMN109188",
                make = "Kenworth",
                model = "T680",
                year = 2023,
                active = true
            ),
            Vehicle(
                id = 4,
                vehicleId = "TRUCK-004",
                vin = "1HGBH41JXMN109189",
                make = "Volvo",
                model = "VNL 760",
                year = 2022,
                active = true
            ),
            Vehicle(
                id = 5,
                vehicleId = "TRUCK-005",
                vin = "1HGBH41JXMN109190",
                make = "Mack",
                model = "Anthem",
                year = 2021,
                active = true
            )
        )
    }

    // Mock HOS Status (matching screenshot - all full)
    fun getMockHOSStatus(): HOSStatus {
        return HOSStatus(
            breakTimeRemaining = 480,      // 08:00 remaining
            driveTimeRemaining = 660,      // 11:00 remaining
            shiftTimeRemaining = 840,      // 14:00 remaining
            cycleTimeRemaining = 4200,     // 70:00 remaining
            breakTimeUsed = 0,
            driveTimeUsed = 0,
            shiftTimeUsed = 0,
            cycleTimeUsed = 0,
            breakTimeTotal = 480,          // 08:00 total
            driveTimeTotal = 660,          // 11:00 total
            shiftTimeTotal = 840,          // 14:00 total
            cycleTimeTotal = 4200          // 70:00 total
        )
    }

    // Mock Current Duty Status
    fun getMockCurrentDutyStatus(): DutyStatusType {
        return DutyStatusType.OFF_DUTY
    }

    // Mock Duty Status Duration
    fun getMockDutyStatusDuration(): String {
        return "2 days"
    }

    // Mock ELD Connection Status
    fun getMockELDConnectionStatus(): ELDConnectionStatus {
        return ELDConnectionStatus.DISCONNECTED
    }

    // Note: Daily Logs are now fetched from real API (see LogsViewModel)
}
