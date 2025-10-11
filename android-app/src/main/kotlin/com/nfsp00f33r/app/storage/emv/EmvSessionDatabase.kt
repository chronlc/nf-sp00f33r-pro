package com.nfsp00f33r.app.storage.emv

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for complete EMV card sessions
 * Phase 1: EmvSessionDatabase Creation (SIMPLIFIED DESIGN)
 * 
 * SINGLE TABLE = SINGLE DAO = SIMPLE!
 * - One row per card scan
 * - All 200+ tags stored as JSON
 * - Complete APDU log stored as JSON
 * - Easy queries, easy exports, easy everything
 */
@Database(
    entities = [EmvCardSessionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(EmvCardSessionConverters::class)
abstract class EmvSessionDatabase : RoomDatabase() {
    
    abstract fun emvCardSessionDao(): EmvCardSessionDao
    
    companion object {
        @Volatile
        private var INSTANCE: EmvSessionDatabase? = null
        
        private const val DATABASE_NAME = "emv_sessions.db"
        
        fun getInstance(context: Context): EmvSessionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EmvSessionDatabase::class.java,
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
