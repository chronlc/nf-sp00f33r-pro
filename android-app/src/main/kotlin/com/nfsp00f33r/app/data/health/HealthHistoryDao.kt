package com.nfsp00f33r.app.data.health

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for health history operations
 * Phase 3 Days 5-6: Health History Storage
 */
@Dao
interface HealthHistoryDao {
    
    /**
     * Insert health history entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HealthHistoryEntity): Long
    
    /**
     * Insert multiple entries
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HealthHistoryEntity>)
    
    /**
     * Get all health history entries (ordered by timestamp descending)
     */
    @Query("SELECT * FROM health_history ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<HealthHistoryEntity>>
    
    /**
     * Get all health history entries (one-time query)
     */
    @Query("SELECT * FROM health_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<HealthHistoryEntity>
    
    /**
     * Get health history entries within time range
     */
    @Query("SELECT * FROM health_history WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getByTimeRange(startTime: Long, endTime: Long): List<HealthHistoryEntity>
    
    /**
     * Get last N health history entries
     */
    @Query("SELECT * FROM health_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastN(limit: Int): List<HealthHistoryEntity>
    
    /**
     * Get health history entries with specific severity
     */
    @Query("SELECT * FROM health_history WHERE overallSeverity = :severity ORDER BY timestamp DESC")
    suspend fun getBySeverity(severity: String): List<HealthHistoryEntity>
    
    /**
     * Get entries with critical issues
     */
    @Query("SELECT * FROM health_history WHERE criticalModuleCount > 0 ORDER BY timestamp DESC")
    suspend fun getCriticalEntries(): List<HealthHistoryEntity>
    
    /**
     * Get entries with errors
     */
    @Query("SELECT * FROM health_history WHERE errorModuleCount > 0 ORDER BY timestamp DESC")
    suspend fun getErrorEntries(): List<HealthHistoryEntity>
    
    /**
     * Delete health history entry
     */
    @Delete
    suspend fun delete(entry: HealthHistoryEntity)
    
    /**
     * Delete all entries
     */
    @Query("DELETE FROM health_history")
    suspend fun deleteAll()
    
    /**
     * Delete entries older than specified timestamp
     */
    @Query("DELETE FROM health_history WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    /**
     * Get total count of entries
     */
    @Query("SELECT COUNT(*) FROM health_history")
    suspend fun getCount(): Int
    
    /**
     * Keep only last N entries, delete older ones
     */
    @Query("""
        DELETE FROM health_history 
        WHERE id NOT IN (
            SELECT id FROM health_history 
            ORDER BY timestamp DESC 
            LIMIT :limit
        )
    """)
    suspend fun keepLastN(limit: Int)
}
