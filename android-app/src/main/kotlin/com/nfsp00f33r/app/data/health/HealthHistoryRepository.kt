package com.nfsp00f33r.app.data.health

import android.content.Context
import com.nfsp00f33r.app.core.HealthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for health history data
 * Phase 3 Days 5-6: Health History Storage
 * 
 * Provides abstraction layer between ViewModel and Room database
 */
class HealthHistoryRepository(context: Context) {
    
    private val database = HealthDatabase.getInstance(context)
    private val dao = database.healthHistoryDao()
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Save health snapshot to database
     */
    suspend fun saveHealthSnapshot(
        moduleStatuses: Map<String, HealthStatus>,
        overallSeverity: HealthStatus.Severity
    ) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        
        // Convert HealthStatus to serializable data
        val statusDataMap = moduleStatuses.mapValues { (_, status) ->
            HealthStatusData.fromHealthStatus(status)
        }
        
        // Count modules by severity
        val criticalCount = moduleStatuses.values.count { it.severity == HealthStatus.Severity.CRITICAL }
        val errorCount = moduleStatuses.values.count { it.severity == HealthStatus.Severity.ERROR }
        val warningCount = moduleStatuses.values.count { it.severity == HealthStatus.Severity.WARNING }
        val healthyCount = moduleStatuses.values.count { it.isHealthy }
        
        val entity = HealthHistoryEntity(
            timestamp = timestamp,
            timestampFormatted = dateFormat.format(Date(timestamp)),
            moduleStatuses = HealthHistoryConverters().fromModuleStatusesMap(statusDataMap),
            overallSeverity = overallSeverity.name,
            healthyModuleCount = healthyCount,
            warningModuleCount = warningCount,
            errorModuleCount = errorCount,
            criticalModuleCount = criticalCount
        )
        
        dao.insert(entity)
        
        // Keep only last 1000 entries to prevent DB bloat
        dao.keepLastN(1000)
    }
    
    /**
     * Get all health history as Flow
     */
    fun getAllHealthHistoryFlow(): Flow<List<HealthHistoryEntry>> {
        return dao.getAllFlow().map { entities ->
            entities.map { entity ->
                entityToEntry(entity)
            }
        }
    }
    
    /**
     * Get all health history (one-time query)
     */
    suspend fun getAllHealthHistory(): List<HealthHistoryEntry> = withContext(Dispatchers.IO) {
        dao.getAll().map { entity ->
            entityToEntry(entity)
        }
    }
    
    /**
     * Get health history within time range
     */
    suspend fun getHealthHistoryByTimeRange(
        startTime: Long,
        endTime: Long
    ): List<HealthHistoryEntry> = withContext(Dispatchers.IO) {
        dao.getByTimeRange(startTime, endTime).map { entity ->
            entityToEntry(entity)
        }
    }
    
    /**
     * Get last N health history entries
     */
    suspend fun getLastNEntries(n: Int): List<HealthHistoryEntry> = withContext(Dispatchers.IO) {
        dao.getLastN(n).map { entity ->
            entityToEntry(entity)
        }
    }
    
    /**
     * Get entries with critical issues
     */
    suspend fun getCriticalEntries(): List<HealthHistoryEntry> = withContext(Dispatchers.IO) {
        dao.getCriticalEntries().map { entity ->
            entityToEntry(entity)
        }
    }
    
    /**
     * Get entries with errors
     */
    suspend fun getErrorEntries(): List<HealthHistoryEntry> = withContext(Dispatchers.IO) {
        dao.getErrorEntries().map { entity ->
            entityToEntry(entity)
        }
    }
    
    /**
     * Delete all health history
     */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
    
    /**
     * Delete entries older than specified timestamp
     */
    suspend fun deleteOlderThan(timestamp: Long) = withContext(Dispatchers.IO) {
        dao.deleteOlderThan(timestamp)
    }
    
    /**
     * Get total count of entries
     */
    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        dao.getCount()
    }
    
    /**
     * Convert Room entity to HealthHistoryEntry
     */
    private fun entityToEntry(entity: HealthHistoryEntity): HealthHistoryEntry {
        val statusDataMap = HealthHistoryConverters().toModuleStatusesMap(entity.moduleStatuses)
        val statusMap = statusDataMap.mapValues { (_, data) ->
            data.toHealthStatus()
        }
        
        return HealthHistoryEntry(
            timestamp = entity.timestampFormatted,
            moduleStatuses = statusMap
        )
    }
}

/**
 * Health history entry (ViewModel representation)
 */
data class HealthHistoryEntry(
    val timestamp: String,
    val moduleStatuses: Map<String, HealthStatus>
)
