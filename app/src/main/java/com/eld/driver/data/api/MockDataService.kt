package com.eld.driver.data.api

import com.eld.driver.data.models.*
import java.util.*

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

    // Mock Daily Logs
    fun getMockDailyLogs(): List<DailyLog> {
        val calendar = Calendar.getInstance()
        val today = calendar.time

        return listOf(
            DailyLog(
                id = "1",
                date = today,
                recapHours = 0,
                recapMinutes = 0,
                inspections = 0,
                distance = 0.0,
                isCertified = true,
                hasDefects = false
            ),
            DailyLog(
                id = "2",
                date = Date(today.time - 86400000L), // 1 day ago
                recapHours = 0,
                recapMinutes = 0,
                inspections = 0,
                distance = 0.0,
                isCertified = false,
                hasDefects = false
            ),
            DailyLog(
                id = "3",
                date = Date(today.time - 172800000L), // 2 days ago
                recapHours = 0,
                recapMinutes = 0,
                inspections = 0,
                distance = 0.0,
                isCertified = true,
                hasDefects = false
            ),
            DailyLog(
                id = "4",
                date = Date(today.time - 259200000L), // 3 days ago
                recapHours = 0,
                recapMinutes = 18,
                inspections = 0,
                distance = 0.0,
                isCertified = true,
                hasDefects = true
            ),
            DailyLog(
                id = "5",
                date = Date(today.time - 345600000L), // 4 days ago
                recapHours = 0,
                recapMinutes = 0,
                inspections = 0,
                distance = 0.0,
                isCertified = true,
                hasDefects = false
            ),
            DailyLog(
                id = "6",
                date = Date(today.time - 432000000L), // 5 days ago
                recapHours = 0,
                recapMinutes = 0,
                inspections = 0,
                distance = 0.0,
                isCertified = true,
                hasDefects = false
            )
        )
    }
}
