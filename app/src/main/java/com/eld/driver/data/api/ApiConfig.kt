package com.eld.driver.data.api

/**
 * API Configuration - Backend endpoints
 *
 * Base URL: Azure Dev Backend
 * All endpoints require Bearer token authentication
 */
object ApiConfig {
    // Azure Dev Backend URL
    const val BASE_URL = "https://eld-dev-be-bpgjh3g4cadnbrb8.eastus2-01.azurewebsites.net"
    // For local development, use: "http://10.0.2.2:5001"

    object Auth {
        const val LOGIN = "$BASE_URL/api/auth/login"
    }

    object MobileDriver {
        const val VEHICLES = "$BASE_URL/api/mobile/drivers/vehicles"
        const val CONFIRM_VEHICLE = "$BASE_URL/api/mobile/drivers/confirm-vehicle"
        const val CURRENT_STATUS = "$BASE_URL/api/mobile/drivers/status"
        const val CHANGE_STATUS = "$BASE_URL/api/mobile/drivers/status"
        const val TICK_EVENTS = "$BASE_URL/api/mobile/drivers/tick-events"

        object Inspections {
            const val BASE = "$BASE_URL/api/mobile/drivers/inspections"
            fun detail(id: Int) = "$BASE/id"
            fun verify(id: Int) = "$BASE/$id/verify"
            fun vehicleDefects(vehicleId: Int) = "$BASE_URL/api/mobile/drivers/vehicles/$vehicleId/dvir-form/vehicle-defects"
            fun assetDefects(assetId: Int) = "$BASE_URL/api/mobile/drivers/assets/$assetId/dvir-form/asset-defects"
        }
    }
}
