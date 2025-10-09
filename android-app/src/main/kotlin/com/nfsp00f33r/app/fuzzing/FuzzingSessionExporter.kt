package com.nfsp00f33r.app.fuzzing

import android.content.Context
import com.nfsp00f33r.app.fuzzing.models.*
import com.nfsp00f33r.app.fuzzing.storage.FuzzingAnomalyEntity
import com.nfsp00f33r.app.fuzzing.storage.FuzzingSessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Exports fuzzing sessions to JSON format
 * Supports detailed session reports and anomaly documentation
 */
class FuzzingSessionExporter(private val context: Context) {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Export session to JSON file
     * @return File path if successful, null otherwise
     */
    suspend fun exportSession(
        session: FuzzingSessionEntity,
        anomalies: List<FuzzingAnomalyEntity>,
        metrics: FuzzingMetrics? = null
    ): String? {
        try {
            val exportData = SessionExportData(
                sessionId = session.sessionId,
                terminalInfo = TerminalInfo(
                    terminalId = session.terminalId,
                    terminalName = session.terminalName,
                    terminalModel = session.terminalModel
                ),
                sessionMetadata = SessionMetadata(
                    strategyType = session.strategyType,
                    startTime = session.startTime.toString(),
                    endTime = session.endTime?.toString(),
                    durationSeconds = session.endTime?.let {
                        (it.epochSecond - session.startTime.epochSecond)
                    }
                ),
                metrics = SessionMetrics(
                    testsExecuted = session.testsExecuted,
                    testsPerSecond = session.testsPerSecond,
                    uniqueResponses = session.uniqueResponses,
                    coveragePercent = session.coveragePercent,
                    anomaliesFound = session.anomaliesFound,
                    crashesDetected = session.crashesDetected,
                    averageResponseTimeMs = metrics?.averageResponseTimeMs ?: 0.0,
                    minResponseTimeMs = metrics?.minResponseTimeMs ?: 0L,
                    maxResponseTimeMs = metrics?.maxResponseTimeMs ?: 0L,
                    timeoutCount = metrics?.timeoutCount ?: 0,
                    errorCount = metrics?.errorCount ?: 0,
                    uniqueStatusWords = metrics?.uniqueStatusWords?.toList() ?: emptyList()
                ),
                anomalies = anomalies.map { anomaly ->
                    AnomalyExportData(
                        anomalyId = anomaly.anomalyId,
                        severity = anomaly.severity,
                        type = anomaly.type,
                        description = anomaly.description,
                        command = anomaly.commandHex,
                        response = anomaly.responseHex,
                        reproducible = anomaly.reproducible,
                        reproductionCount = anomaly.reproductionCount,
                        discoveredAt = anomaly.discoveredAt.toString()
                    )
                }.sortedByDescending { it.severity },
                notes = session.sessionNotes,
                exportedAt = Instant.now().toString(),
                exportVersion = "1.0"
            )
            
            // Serialize to JSON
            val jsonString = json.encodeToString(exportData)
            
            // Create export directory
            val exportDir = File(context.getExternalFilesDir(null), "fuzzing_exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            // Generate filename with timestamp
            val timestamp = DateTimeFormatter.ISO_INSTANT
                .format(session.startTime)
                .replace(":", "-")
                .replace(".", "-")
            val filename = "fuzz_${session.terminalId}_${timestamp}.json"
            
            // Write to file
            val exportFile = File(exportDir, filename)
            exportFile.writeText(jsonString)
            
            Timber.i("📤 Exported session ${session.sessionId} to ${exportFile.absolutePath}")
            
            return exportFile.absolutePath
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to export session ${session.sessionId}")
            return null
        }
    }
    
    /**
     * Export multiple sessions to single file
     */
    suspend fun exportMultipleSessions(
        sessions: List<Pair<FuzzingSessionEntity, List<FuzzingAnomalyEntity>>>
    ): String? {
        try {
            val exportData = sessions.map { (session, anomalies) ->
                SessionExportData(
                    sessionId = session.sessionId,
                    terminalInfo = TerminalInfo(
                        terminalId = session.terminalId,
                        terminalName = session.terminalName,
                        terminalModel = session.terminalModel
                    ),
                    sessionMetadata = SessionMetadata(
                        strategyType = session.strategyType,
                        startTime = session.startTime.toString(),
                        endTime = session.endTime?.toString(),
                        durationSeconds = session.endTime?.let {
                            (it.epochSecond - session.startTime.epochSecond)
                        }
                    ),
                    metrics = SessionMetrics(
                        testsExecuted = session.testsExecuted,
                        testsPerSecond = session.testsPerSecond,
                        uniqueResponses = session.uniqueResponses,
                        coveragePercent = session.coveragePercent,
                        anomaliesFound = session.anomaliesFound,
                        crashesDetected = session.crashesDetected
                    ),
                    anomalies = anomalies.map { anomaly ->
                        AnomalyExportData(
                            anomalyId = anomaly.anomalyId,
                            severity = anomaly.severity,
                            type = anomaly.type,
                            description = anomaly.description,
                            command = anomaly.commandHex,
                            response = anomaly.responseHex,
                            reproducible = anomaly.reproducible,
                            reproductionCount = anomaly.reproductionCount,
                            discoveredAt = anomaly.discoveredAt.toString()
                        )
                    },
                    notes = session.sessionNotes,
                    exportedAt = Instant.now().toString(),
                    exportVersion = "1.0"
                )
            }
            
            val jsonString = json.encodeToString(exportData)
            
            val exportDir = File(context.getExternalFilesDir(null), "fuzzing_exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            val timestamp = DateTimeFormatter.ISO_INSTANT
                .format(Instant.now())
                .replace(":", "-")
                .replace(".", "-")
            val filename = "fuzz_export_${timestamp}.json"
            
            val exportFile = File(exportDir, filename)
            exportFile.writeText(jsonString)
            
            Timber.i("📤 Exported ${sessions.size} sessions to ${exportFile.absolutePath}")
            
            return exportFile.absolutePath
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to export multiple sessions")
            return null
        }
    }
}

@Serializable
data class SessionExportData(
    val sessionId: String,
    val terminalInfo: TerminalInfo,
    val sessionMetadata: SessionMetadata,
    val metrics: SessionMetrics,
    val anomalies: List<AnomalyExportData>,
    val notes: String? = null,
    val exportedAt: String,
    val exportVersion: String
)

@Serializable
data class TerminalInfo(
    val terminalId: String,
    val terminalName: String,
    val terminalModel: String? = null
)

@Serializable
data class SessionMetadata(
    val strategyType: String,
    val startTime: String,
    val endTime: String? = null,
    val durationSeconds: Long? = null
)

@Serializable
data class SessionMetrics(
    val testsExecuted: Int,
    val testsPerSecond: Double,
    val uniqueResponses: Int,
    val coveragePercent: Double,
    val anomaliesFound: Int,
    val crashesDetected: Int,
    val averageResponseTimeMs: Double = 0.0,
    val minResponseTimeMs: Long = 0L,
    val maxResponseTimeMs: Long = 0L,
    val timeoutCount: Int = 0,
    val errorCount: Int = 0,
    val uniqueStatusWords: List<String> = emptyList()
)

@Serializable
data class AnomalyExportData(
    val anomalyId: Long,
    val severity: String,
    val type: String,
    val description: String,
    val command: String,
    val response: String?,
    val reproducible: Boolean,
    val reproductionCount: Int,
    val discoveredAt: String
)
