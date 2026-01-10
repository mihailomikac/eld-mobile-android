package com.eld.driver.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.eld.driver.data.local.dao.DailyLogDao
import com.eld.driver.data.local.dao.DutyStatusEventDao
import com.eld.driver.data.local.dao.HOSStatusDao
import com.eld.driver.data.local.dao.SyncQueueDao
import com.eld.driver.data.local.dao.TickEventDao
import com.eld.driver.data.local.dao.USCityDao
import com.eld.driver.data.local.dao.ViolationRecordDao
import com.eld.driver.data.local.entity.DailyLogEntity
import com.eld.driver.data.local.entity.DutyStatusEventEntity
import com.eld.driver.data.local.entity.HOSStatusEntity
import com.eld.driver.data.local.entity.SyncQueueEntity
import com.eld.driver.data.local.entity.TickEventEntity
import com.eld.driver.data.local.entity.USCityEntity
import com.eld.driver.data.local.entity.ViolationRecordEntity

/**
 * Room Database for ELD Driver application.
 * Stores all local data for offline-first operation.
 */
@Database(
    entities = [
        DutyStatusEventEntity::class,
        TickEventEntity::class,
        HOSStatusEntity::class,
        SyncQueueEntity::class,
        USCityEntity::class,
        DailyLogEntity::class,
        ViolationRecordEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class ELDDatabase : RoomDatabase() {

    abstract fun dutyStatusEventDao(): DutyStatusEventDao
    abstract fun tickEventDao(): TickEventDao
    abstract fun hosStatusDao(): HOSStatusDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun usCityDao(): USCityDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun violationRecordDao(): ViolationRecordDao

    companion object {
        private const val DATABASE_NAME = "eld_database"

        @Volatile
        private var INSTANCE: ELDDatabase? = null

        /**
         * Get singleton database instance.
         * Uses double-checked locking for thread safety.
         */
        fun getInstance(context: Context): ELDDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ELDDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ELDDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // For development - replace with proper migrations in production
                .build()
        }

        /**
         * Close database connection.
         * Call this on app termination or when switching users.
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
