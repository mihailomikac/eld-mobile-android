package com.eld.driver.data.api

import com.eld.driver.data.models.*
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * Retrofit API Service
 * All backend API endpoints for the ELD Driver mobile app
 */
interface ApiService {

    // Auth - Mobile login with single-session enforcement
    @POST("api/mobile/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // Auth - Mobile logout
    @POST("api/mobile/auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<ApiResponse<Unit>>

    // Vehicles
    @GET("api/mobile/drivers/vehicles")
    suspend fun getVehicles(
        @Header("Authorization") token: String,
        @Query("searchTerm") searchTerm: String? = null,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<ApiResponse<MobileVehicleListData>>

    // Duty Status
    @GET("api/mobile/drivers/status")
    suspend fun getCurrentDutyStatus(
        @Header("Authorization") token: String
    ): Response<ApiResponse<DutyStatus>>

    @POST("api/mobile/drivers/status")
    suspend fun changeDutyStatus(
        @Header("Authorization") token: String,
        @Body request: DutyStatusChangeRequest
    ): Response<ApiResponse<DutyStatus>>

    // Tick Events
    @POST("api/mobile/drivers/tick-events")
    suspend fun createTickEvent(
        @Header("Authorization") token: String,
        @Body request: TickEventRequest
    ): Response<ApiResponse<Any>>

    // Intermediate Events (hourly during DRIVING - FMCSA requirement)
    @POST("api/mobile/drivers/intermediate-events")
    suspend fun createIntermediateEvent(
        @Header("Authorization") token: String,
        @Body request: IntermediateEventRequest
    ): Response<ApiResponse<IntermediateEventResponse>>

    // DVIR Inspections
    @POST("api/mobile/drivers/inspections")
    suspend fun createInspection(
        @Header("Authorization") token: String,
        @Body request: InspectionCreateRequest
    ): Response<ApiResponse<Inspection>>

    @GET("api/mobile/drivers/inspections")
    suspend fun getInspections(
        @Header("Authorization") token: String,
        @Query("vehicleId") vehicleId: Int? = null,
        @Query("inspectionType") inspectionType: InspectionType? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<ApiResponse<PaginatedData<InspectionListItem>>>

    @GET("api/mobile/drivers/inspections/{id}")
    suspend fun getInspectionById(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<ApiResponse<Inspection>>

    @POST("api/mobile/drivers/inspections/{id}/verify")
    suspend fun verifyInspection(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<ApiResponse<String>>

    @GET("api/mobile/drivers/vehicles/{vehicleId}/dvir-form/vehicle-defects")
    suspend fun getVehicleDefects(
        @Header("Authorization") token: String,
        @Path("vehicleId") vehicleId: Int
    ): Response<ApiResponse<VehicleDefectsFormResponse>>

    @GET("api/mobile/drivers/assets/{assetId}/dvir-form/asset-defects")
    suspend fun getAssetDefects(
        @Header("Authorization") token: String,
        @Path("assetId") assetId: Int
    ): Response<ApiResponse<AssetDefectsFormResponse>>

    // Driver Logs - Daily Summary
    @GET("api/mobile/drivers/logs")
    suspend fun getDriverLogs(
        @Header("Authorization") token: String,
        @Query("days") days: Int = 7
    ): Response<ApiResponse<DriverLogsData>>

    // Driver Events for specific date
    @GET("api/mobile/drivers/events")
    suspend fun getDriverEvents(
        @Header("Authorization") token: String,
        @Query("date") date: String? = null
    ): Response<ApiResponse<DriverEventsData>>

    // Certify log for specific date
    @POST("api/mobile/drivers/logs/{date}/certify")
    suspend fun certifyLog(
        @Header("Authorization") token: String,
        @Path("date") date: String
    ): Response<ApiResponse<Unit>>

    // HOS Status - Get current HOS from backend
    @GET("api/mobile/drivers/hos")
    suspend fun getHOSStatus(
        @Header("Authorization") token: String
    ): Response<ApiResponse<HOSResponse>>

    // HOS Status - Send calculated HOS to backend
    // NOTE: This also updates LastSyncTime on backend automatically
    @POST("api/mobile/drivers/hos")
    suspend fun updateHOSStatus(
        @Header("Authorization") token: String,
        @Body request: HOSUpdateRequest
    ): Response<ApiResponse<HOSResponse>>

    // HOS Sync - Send HOS data including violations
    // Mobile calculates all HOS clocks and violations, backend stores them
    @POST("api/mobile/drivers/hos/sync")
    suspend fun syncHOS(
        @Header("Authorization") token: String,
        @Body request: HOSSyncRequest
    ): Response<ApiResponse<HOSSyncResponse>>

    // HOS Violations - Create a new violation
    // Called when mobile detects driver exceeded a limit
    @POST("api/mobile/drivers/hos/violations")
    suspend fun createViolation(
        @Header("Authorization") token: String,
        @Body request: HosViolationRequest
    ): Response<ApiResponse<CreateViolationResponse>>

    // HOS Violations - End an active violation
    // Called when driver takes required break/rest and violation ends
    @PUT("api/mobile/drivers/hos/violations/end")
    suspend fun endViolation(
        @Header("Authorization") token: String,
        @Body request: EndViolationRequest
    ): Response<ApiResponse<Unit>>

    companion object {
        private var instance: ApiService? = null

        fun getInstance(): ApiService {
            if (instance == null) {
                instance = create()
            }
            return instance!!
        }

        private fun create(): ApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val gson = GsonBuilder()
                .setLenient()
                .create()

            val retrofit = Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()

            return retrofit.create(ApiService::class.java)
        }
    }
}

/**
 * API Result wrapper for handling success/error states
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}
