package com.eld.driver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eld.driver.data.local.entity.DailyLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for DailyLog operations.
 */
@Dao
interface DailyLogDao {

    /**
     * Insert or update a daily log.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: DailyLogEntity)

    /**
     * Insert or update multiple daily logs.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(logs: List<DailyLogEntity>)

    /**
     * Get all daily logs ordered by date descending.
     */
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    suspend fun getAllLogs(): List<DailyLogEntity>

    /**
     * Get daily logs as Flow for reactive updates.
     */
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    fun getAllLogsFlow(): Flow<List<DailyLogEntity>>

    /**
     * Get logs for the last N days.
     * Note: This uses LIMIT which may exclude uncertified old logs.
     * Prefer getAllLogs() for displaying logs screen.
     */
    @Query("SELECT * FROM daily_logs ORDER BY date DESC LIMIT :days")
    suspend fun getLogsForDays(days: Int): List<DailyLogEntity>

    /**
     * Get all synced logs ordered by date descending.
     * Use this for the logs screen - backend already filters correctly:
     * - Last 7 days + uncertified logs up to 6 months old
     */
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    suspend fun getAllSyncedLogs(): List<DailyLogEntity>

    /**
     * Get a specific daily log by date.
     */
    @Query("SELECT * FROM daily_logs WHERE date = :date")
    suspend fun getLogByDate(date: String): DailyLogEntity?

    /**
     * Update certification status for a log.
     */
    @Query("UPDATE daily_logs SET isCertified = :isCertified, lastUpdated = :timestamp WHERE date = :date")
    suspend fun updateCertificationStatus(date: String, isCertified: Boolean, timestamp: Long = System.currentTimeMillis())

    /**
     * Delete all logs.
     */
    @Query("DELETE FROM daily_logs")
    suspend fun deleteAll()

    /**
     * Delete logs older than a certain date.
     */
    @Query("DELETE FROM daily_logs WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)

    /**
     * Get count of uncertified logs.
     */
    @Query("SELECT COUNT(*) FROM daily_logs WHERE isCertified = 0")
    suspend fun getUncertifiedCount(): Int

    /**
     * Get count of logs with violations.
     */
    @Query("SELECT COUNT(*) FROM daily_logs WHERE violationCount > 0")
    suspend fun getLogsWithViolationsCount(): Int
}
