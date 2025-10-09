package com.nfsp00f33r.app.data.health

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for health monitoring
 * Phase 3 Days 5-6: Health History Storage
 */
@Database(
    entities = [HealthHistoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(HealthHistoryConverters::class)
abstract class HealthDatabase : RoomDatabase() {
    
    abstract fun healthHistoryDao(): HealthHistoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null
        
        private const val DATABASE_NAME = "health_monitoring.db"
        
        fun getInstance(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Clear database instance (for testing)
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
