package com.nfsp00f33r.app.fuzzing.storage

import androidx.room.*
import java.time.Instant

/**
 * Room entity for persisting fuzzing sessions
 * Allows mapping different terminals and tracking fuzzing history
 */
@Entity(
    tableName = "fuzzing_sessions",
    indices = [
        Index(value = ["terminal_id"]),
        Index(value = ["start_time"]),
        Index(value = ["strategy_type"])
    ]
)
data class FuzzingSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    
    @ColumnInfo(name = "terminal_id")
    val terminalId: String, // Identifier for the terminal being fuzzed
    
    @ColumnInfo(name = "terminal_name")
    val terminalName: String,
    
    @ColumnInfo(name = "terminal_model")
    val terminalModel: String? = null,
    
    @ColumnInfo(name = "strategy_type")
    val strategyType: String, // RANDOM, MUTATION, PROTOCOL_AWARE
    
    @ColumnInfo(name = "start_time")
    val startTime: Instant,
    
    @ColumnInfo(name = "end_time")
    val endTime: Instant?,
    
    @ColumnInfo(name = "tests_executed")
    val testsExecuted: Int,
    
    @ColumnInfo(name = "tests_per_second")
    val testsPerSecond: Double,
    
    @ColumnInfo(name = "unique_responses")
    val uniqueResponses: Int,
    
    @ColumnInfo(name = "coverage_percent")
    val coveragePercent: Double,
    
    @ColumnInfo(name = "anomalies_found")
    val anomaliesFound: Int,
    
    @ColumnInfo(name = "crashes_detected")
    val crashesDetected: Int,
    
    @ColumnInfo(name = "session_notes")
    val sessionNotes: String? = null,
    
    @ColumnInfo(name = "exported_json_path")
    val exportedJsonPath: String? = null
)

/**
 * Anomaly entity for tracking discovered issues
 */
@Entity(
    tableName = "fuzzing_anomalies",
    foreignKeys = [
        ForeignKey(
            entity = FuzzingSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["severity"]),
        Index(value = ["reproducible"])
    ]
)
data class FuzzingAnomalyEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "anomaly_id")
    val anomalyId: Long = 0,
    
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    
    @ColumnInfo(name = "severity")
    val severity: String, // CRITICAL, HIGH, MEDIUM, LOW
    
    @ColumnInfo(name = "type")
    val type: String,
    
    @ColumnInfo(name = "description")
    val description: String,
    
    @ColumnInfo(name = "command_hex")
    val commandHex: String,
    
    @ColumnInfo(name = "response_hex")
    val responseHex: String?,
    
    @ColumnInfo(name = "reproducible")
    val reproducible: Boolean,
    
    @ColumnInfo(name = "reproduction_count")
    val reproductionCount: Int = 0,
    
    @ColumnInfo(name = "discovered_at")
    val discoveredAt: Instant
)

/**
 * Terminal entity for tracking different terminals
 */
@Entity(
    tableName = "terminals",
    indices = [Index(value = ["terminal_name"], unique = true)]
)
data class TerminalEntity(
    @PrimaryKey
    @ColumnInfo(name = "terminal_id")
    val terminalId: String,
    
    @ColumnInfo(name = "terminal_name")
    val terminalName: String,
    
    @ColumnInfo(name = "terminal_model")
    val terminalModel: String? = null,
    
    @ColumnInfo(name = "manufacturer")
    val manufacturer: String? = null,
    
    @ColumnInfo(name = "firmware_version")
    val firmwareVersion: String? = null,
    
    @ColumnInfo(name = "first_fuzzed")
    val firstFuzzed: Instant,
    
    @ColumnInfo(name = "last_fuzzed")
    val lastFuzzed: Instant,
    
    @ColumnInfo(name = "total_sessions")
    val totalSessions: Int = 0,
    
    @ColumnInfo(name = "total_anomalies")
    val totalAnomalies: Int = 0
)

/**
 * Preset entity for fuzzing presets
 */
@Entity(
    tableName = "fuzzing_presets",
    indices = [Index(value = ["preset_name"], unique = true)]
)
data class FuzzingPresetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "preset_id")
    val presetId: Long = 0,
    
    @ColumnInfo(name = "preset_name")
    val presetName: String,
    
    @ColumnInfo(name = "preset_description")
    val presetDescription: String,
    
    @ColumnInfo(name = "strategy_type")
    val strategyType: String,
    
    @ColumnInfo(name = "target_vulnerability")
    val targetVulnerability: String, // ROCA, TRACK2, CVM_BYPASS, etc.
    
    @ColumnInfo(name = "seed_commands_json")
    val seedCommandsJson: String, // JSON array of hex commands
    
    @ColumnInfo(name = "max_tests")
    val maxTests: Int,
    
    @ColumnInfo(name = "tests_per_second")
    val testsPerSecond: Int,
    
    @ColumnInfo(name = "is_builtin")
    val isBuiltin: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant
)
