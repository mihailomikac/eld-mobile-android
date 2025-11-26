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

    // Auth
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

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
    ): Response<ApiResponse<String>>

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
