package com.nfsp00f33r.app.fuzzing.models

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Fuzzing strategy types
 */
enum class FuzzingStrategyType {
    RANDOM,           // Completely random byte generation
    MUTATION,         // Bit-flip and byte mutations
    PROTOCOL_AWARE    // Valid APDU structure with edge cases
}

/**
 * Anomaly severity levels
 */
enum class AnomalySeverity {
    CRITICAL,  // Crash, complete failure
    HIGH,      // Timeout, malformed response
    MEDIUM,    // Unexpected status word
    LOW        // Unusual timing
}

/**
 * Configuration for a fuzzing session
 */
data class FuzzConfig(
    val strategy: FuzzingStrategyType = FuzzingStrategyType.MUTATION,
    val maxTests: Int = 1000,
    val timeoutMs: Long = 5000,
    val testsPerSecond: Int = 10,
    val enableCrashDetection: Boolean = true,
    val saveResults: Boolean = true,
    val targetCommands: List<String>? = null,
    val seedData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FuzzConfig

        if (strategy != other.strategy) return false
        if (maxTests != other.maxTests) return false
        if (timeoutMs != other.timeoutMs) return false
        if (testsPerSecond != other.testsPerSecond) return false
        if (enableCrashDetection != other.enableCrashDetection) return false
        if (saveResults != other.saveResults) return false
        if (targetCommands != other.targetCommands) return false
        if (seedData != null) {
            if (other.seedData == null) return false
            if (!seedData.contentEquals(other.seedData)) return false
        } else if (other.seedData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = strategy.hashCode()
        result = 31 * result + maxTests
        result = 31 * result + timeoutMs.hashCode()
        result = 31 * result + testsPerSecond
        result = 31 * result + enableCrashDetection.hashCode()
        result = 31 * result + saveResults.hashCode()
        result = 31 * result + (targetCommands?.hashCode() ?: 0)
        result = 31 * result + (seedData?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Session state for fuzzing operations
 */
enum class FuzzingSessionState {
    IDLE,
    INITIALIZING,
    RUNNING,
    PAUSED,
    STOPPED,
    ERROR
}

/**
 * Single test result
 */
data class FuzzTestResult(
    val testNumber: Int,
    val command: ByteArray,
    val response: ByteArray?,
    val statusWord: String?,
    val executionTimeMs: Long,
    val timestamp: Instant,
    val anomalies: List<Anomaly>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FuzzTestResult

        if (testNumber != other.testNumber) return false
        if (!command.contentEquals(other.command)) return false
        if (response != null) {
            if (other.response == null) return false
            if (!response.contentEquals(other.response)) return false
        } else if (other.response != null) return false
        if (statusWord != other.statusWord) return false
        if (executionTimeMs != other.executionTimeMs) return false
        if (timestamp != other.timestamp) return false
        if (anomalies != other.anomalies) return false

        return true
    }

    override fun hashCode(): Int {
        var result = testNumber
        result = 31 * result + command.contentHashCode()
        result = 31 * result + (response?.contentHashCode() ?: 0)
        result = 31 * result + (statusWord?.hashCode() ?: 0)
        result = 31 * result + executionTimeMs.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + anomalies.hashCode()
        return result
    }
}

/**
 * Detected anomaly during fuzzing
 */
data class Anomaly(
    val severity: AnomalySeverity,
    val type: String,
    val description: String,
    val command: ByteArray,
    val response: ByteArray?,
    val reproducible: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Anomaly

        if (severity != other.severity) return false
        if (type != other.type) return false
        if (description != other.description) return false
        if (!command.contentEquals(other.command)) return false
        if (response != null) {
            if (other.response == null) return false
            if (!response.contentEquals(other.response)) return false
        } else if (other.response != null) return false
        if (reproducible != other.reproducible) return false

        return true
    }

    override fun hashCode(): Int {
        var result = severity.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + command.contentHashCode()
        result = 31 * result + (response?.contentHashCode() ?: 0)
        result = 31 * result + reproducible.hashCode()
        return result
    }
}

/**
 * Real-time fuzzing metrics
 */
data class FuzzingMetrics(
    val testsExecuted: Int = 0,
    val testsPerSecond: Double = 0.0,
    val uniqueResponses: Int = 0,
    val uniqueStatusWords: Set<String> = emptySet(),
    val coveragePercent: Double = 0.0,
    val anomaliesFound: Int = 0,
    val crashesDetected: Int = 0,
    val currentStrategy: String = "",
    val elapsedTimeMs: Long = 0,
    val averageResponseTimeMs: Double = 0.0,
    val minResponseTimeMs: Long = Long.MAX_VALUE,
    val maxResponseTimeMs: Long = 0L,
    val timeoutCount: Int = 0,
    val errorCount: Int = 0
)

/**
 * Complete fuzzing session data
 */
data class FuzzingSession(
    val sessionId: String,
    val config: FuzzConfig,
    val state: FuzzingSessionState,
    val startTime: Instant?,
    val endTime: Instant?,
    val metrics: FuzzingMetrics,
    val anomalies: List<Anomaly> = emptyList(),
    val interestingFindings: List<FuzzTestResult> = emptyList()
)
