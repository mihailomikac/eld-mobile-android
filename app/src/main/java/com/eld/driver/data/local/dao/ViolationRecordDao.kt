package com.eld.driver.data.local.dao

import androidx.room.*
import com.eld.driver.data.local.entity.ViolationRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for violation record operations.
 */
@Dao
interface ViolationRecordDao {

    // ═══════════════════════════════════════════════════════════════
    // QUERY OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get all violations.
     */
    @Query("SELECT * FROM violation_records ORDER BY startTime DESC")
    suspend fun getAllViolations(): List<ViolationRecordEntity>

    /**
     * Get all violations as Flow.
     */
    @Query("SELECT * FROM violation_records ORDER BY startTime DESC")
    fun getAllViolationsFlow(): Flow<List<ViolationRecordEntity>>

    /**
     * Get active (unended) violations.
     */
    @Query("SELECT * FROM violation_records WHERE endTime IS NULL ORDER BY startTime DESC")
    suspend fun getActiveViolations(): List<ViolationRecordEntity>

    /**
     * Get violations pending sync.
     */
    @Query("SELECT * FROM violation_records WHERE syncStatus = 'PENDING' ORDER BY startTime ASC")
    suspend fun getPendingSyncViolations(): List<ViolationRecordEntity>

    /**
     * Get violation by type and start time (for matching).
     */
    @Query("SELECT * FROM violation_records WHERE violationType = :type AND startTime = :startTime LIMIT 1")
    suspend fun findViolation(type: String, startTime: Long): ViolationRecordEntity?

    /**
     * Get violation by server ID.
     */
    @Query("SELECT * FROM violation_records WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: Int): ViolationRecordEntity?

    /**
     * Check if violation exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM violation_records WHERE violationType = :type AND startTime = :startTime)")
    suspend fun exists(type: String, startTime: Long): Boolean

    // ═══════════════════════════════════════════════════════════════
    // INSERT/UPDATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Insert a violation. Replace on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(violation: ViolationRecordEntity)

    /**
     * Insert multiple violations.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(violations: List<ViolationRecordEntity>)

    /**
     * Update a violation.
     */
    @Update
    suspend fun update(violation: ViolationRecordEntity)

    /**
     * Mark violation as synced.
     */
    @Query("UPDATE violation_records SET syncStatus = 'SYNCED', serverId = :serverId, updatedAt = :now WHERE id = :localId")
    suspend fun markSynced(localId: String, serverId: Int, now: Long = System.currentTimeMillis())

    /**
     * Update violation end time.
     */
    @Query("UPDATE violation_records SET endTime = :endTime, syncStatus = 'PENDING', updatedAt = :now WHERE id = :localId")
    suspend fun updateEndTime(localId: String, endTime: Long, now: Long = System.currentTimeMillis())

    /**
     * Update violation end time by type and start time (for matching analyzed violations).
     */
    @Query("UPDATE violation_records SET endTime = :endTime, syncStatus = 'PENDING', updatedAt = :now WHERE violationType = :type AND startTime = :startTime AND endTime IS NULL")
    suspend fun updateEndTimeByKey(type: String, startTime: Long, endTime: Long, now: Long = System.currentTimeMillis())

    // ═══════════════════════════════════════════════════════════════
    // DELETE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Delete a violation.
     */
    @Delete
    suspend fun delete(violation: ViolationRecordEntity)

    /**
     * Delete all violations.
     */
    @Query("DELETE FROM violation_records")
    suspend fun deleteAll()

    /**
     * Delete violations older than a certain time.
     */
    @Query("DELETE FROM violation_records WHERE startTime < :before")
    suspend fun deleteOlderThan(before: Long)
}
