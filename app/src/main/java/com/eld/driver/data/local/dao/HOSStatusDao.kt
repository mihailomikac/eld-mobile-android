package com.eld.driver.data.local.dao

import androidx.room.*
import com.eld.driver.data.local.entity.HOSStatusEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for HOS status.
 * Manages the singleton cached HOS status entity.
 */
@Dao
interface HOSStatusDao {

    /**
     * Get the cached HOS status.
     * Returns null if no HOS has been calculated yet.
     */
    @Query("SELECT * FROM hos_status WHERE id = 1")
    suspend fun getHOSStatus(): HOSStatusEntity?

    /**
     * Get the cached HOS status as Flow for reactive updates.
     */
    @Query("SELECT * FROM hos_status WHERE id = 1")
    fun getHOSStatusFlow(): Flow<HOSStatusEntity?>

    /**
     * Save/update the HOS status.
     * Uses REPLACE to handle both insert and update.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHOSStatus(status: HOSStatusEntity)

    /**
     * Delete the cached HOS status.
     * Called on logout or data reset.
     */
    @Query("DELETE FROM hos_status")
    suspend fun deleteHOSStatus()

    /**
     * Check if HOS status exists.
     */
    @Query("SELECT COUNT(*) FROM hos_status WHERE id = 1")
    suspend fun hasHOSStatus(): Int
}
