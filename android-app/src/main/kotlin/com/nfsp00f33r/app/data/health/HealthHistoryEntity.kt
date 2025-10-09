package com.nfsp00f33r.app.data.health

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.nfsp00f33r.app.core.HealthStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Room entity for health history persistence
 * Phase 3 Days 5-6: Health History Storage
 */
@Entity(tableName = "health_history")
@TypeConverters(HealthHistoryConverters::class)
data class HealthHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val timestamp: Long,
    
    val timestampFormatted: String,
    
    // Serialized module statuses (JSON)
    val moduleStatuses: String,
    
    // Overall health summary
    val overallSeverity: String,
    
    val healthyModuleCount: Int,
    
    val warningModuleCount: Int,
    
    val errorModuleCount: Int,
    
    val criticalModuleCount: Int
)

/**
 * Type converters for Room database
 */
class HealthHistoryConverters {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    @TypeConverter
    fun fromModuleStatusesMap(value: Map<String, HealthStatusData>): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toModuleStatusesMap(value: String): Map<String, HealthStatusData> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

/**
 * Serializable health status data for Room storage
 */
@Serializable
data class HealthStatusData(
    val isHealthy: Boolean,
    val message: String,
    val severity: String,
    val timestamp: Long
) {
    companion object {
        fun fromHealthStatus(status: HealthStatus): HealthStatusData {
            return HealthStatusData(
                isHealthy = status.isHealthy,
                message = status.message,
                severity = status.severity.name,
                timestamp = status.timestamp
            )
        }
    }
    
    fun toHealthStatus(): HealthStatus {
        val severityEnum = try {
            HealthStatus.Severity.valueOf(severity)
        } catch (e: IllegalArgumentException) {
            HealthStatus.Severity.INFO
        }
        
        return HealthStatus(
            isHealthy = isHealthy,
            message = message,
            severity = severityEnum,
            timestamp = timestamp
        )
    }
}
