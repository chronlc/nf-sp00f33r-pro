package com.nfsp00f33r.app.fuzzing

import com.nfsp00f33r.app.fuzzing.models.*
import timber.log.Timber
import java.time.Instant

/**
 * Analyzes fuzzing results and detects anomalies
 */
class FuzzingAnalytics {
    
    private val responseHistory = mutableListOf<FuzzTestResult>()
    private val uniqueResponses = mutableSetOf<String>()
    private val uniqueStatusWords = mutableSetOf<String>()
    private val responseTimes = mutableListOf<Long>()
    
    // Baseline statistics for anomaly detection
    private var avgResponseTime = 0.0
    private var stdDevResponseTime = 0.0
    
    fun analyzeResult(result: FuzzTestResult): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Track for metrics
        responseHistory.add(result)
        result.response?.let { 
            uniqueResponses.add(it.joinToString("") { byte -> "%02X".format(byte) })
        }
        result.statusWord?.let { uniqueStatusWords.add(it) }
        responseTimes.add(result.executionTimeMs)
        
        // Update baseline stats
        updateBaselineStats()
        
        // Detect anomalies
        anomalies.addAll(detectCrash(result))
        anomalies.addAll(detectTimeout(result))
        anomalies.addAll(detectUnusualTiming(result))
        anomalies.addAll(detectUnexpectedStatusWord(result))
        anomalies.addAll(detectMalformedResponse(result))
        
        return anomalies
    }
    
    private fun detectCrash(result: FuzzTestResult): List<Anomaly> {
        if (result.response == null || result.response.isEmpty()) {
            Timber.w("🔥 CRASH detected: No response for command ${result.command.toHexString()}")
            return listOf(
                Anomaly(
                    severity = AnomalySeverity.CRITICAL,
                    type = "CRASH",
                    description = "No response received (possible terminal crash)",
                    command = result.command,
                    response = null,
                    reproducible = false
                )
            )
        }
        return emptyList()
    }
    
    private fun detectTimeout(result: FuzzTestResult): List<Anomaly> {
        // If execution time is suspiciously close to timeout threshold
        if (result.executionTimeMs > 4500) { // Assuming 5000ms timeout
            Timber.w("⏱️ TIMEOUT detected: ${result.executionTimeMs}ms for ${result.command.toHexString()}")
            return listOf(
                Anomaly(
                    severity = AnomalySeverity.HIGH,
                    type = "TIMEOUT",
                    description = "Response time ${result.executionTimeMs}ms near timeout threshold",
                    command = result.command,
                    response = result.response,
                    reproducible = false
                )
            )
        }
        return emptyList()
    }
    
    private fun detectUnusualTiming(result: FuzzTestResult): List<Anomaly> {
        if (responseTimes.size < 10) return emptyList() // Need baseline
        
        // Detect responses significantly slower than average
        val threshold = avgResponseTime + (3 * stdDevResponseTime)
        if (result.executionTimeMs > threshold && stdDevResponseTime > 0) {
            Timber.d("📊 TIMING anomaly: ${result.executionTimeMs}ms (avg: ${avgResponseTime.toInt()}ms, threshold: ${threshold.toInt()}ms)")
            return listOf(
                Anomaly(
                    severity = AnomalySeverity.LOW,
                    type = "UNUSUAL_TIMING",
                    description = "Response time ${result.executionTimeMs}ms significantly above average ${avgResponseTime.toInt()}ms",
                    command = result.command,
                    response = result.response,
                    reproducible = false
                )
            )
        }
        return emptyList()
    }
    
    private fun detectUnexpectedStatusWord(result: FuzzTestResult): List<Anomaly> {
        val sw = result.statusWord ?: return emptyList()
        
        // Check for error status words
        val errorWords = listOf(
            "6300", "6400", "6500", "6600", "6700", "6800", "6900",
            "6A00", "6B00", "6C00", "6D00", "6E00", "6F00"
        )
        
        if (errorWords.any { sw.startsWith(it.substring(0, 2)) }) {
            val severity = when {
                sw.startsWith("63") || sw.startsWith("65") -> AnomalySeverity.MEDIUM
                sw.startsWith("67") || sw.startsWith("6D") -> AnomalySeverity.HIGH
                else -> AnomalySeverity.LOW
            }
            
            Timber.d("⚠️ ERROR status word: $sw for ${result.command.toHexString()}")
            return listOf(
                Anomaly(
                    severity = severity,
                    type = "ERROR_STATUS_WORD",
                    description = "Unexpected status word: $sw",
                    command = result.command,
                    response = result.response,
                    reproducible = false
                )
            )
        }
        
        return emptyList()
    }
    
    private fun detectMalformedResponse(result: FuzzTestResult): List<Anomaly> {
        val response = result.response ?: return emptyList()
        
        // Response should at least have status word (2 bytes)
        if (response.size < 2) {
            Timber.w("🔧 MALFORMED response: Too short (${response.size} bytes)")
            return listOf(
                Anomaly(
                    severity = AnomalySeverity.MEDIUM,
                    type = "MALFORMED_RESPONSE",
                    description = "Response too short: ${response.size} bytes",
                    command = result.command,
                    response = response,
                    reproducible = false
                )
            )
        }
        
        // Check for all-zero response (suspicious)
        if (response.all { it == 0.toByte() }) {
            Timber.w("🔧 MALFORMED response: All zeros")
            return listOf(
                Anomaly(
                    severity = AnomalySeverity.MEDIUM,
                    type = "MALFORMED_RESPONSE",
                    description = "Response contains only zeros",
                    command = result.command,
                    response = response,
                    reproducible = false
                )
            )
        }
        
        return emptyList()
    }
    
    private fun updateBaselineStats() {
        if (responseTimes.isEmpty()) return
        
        avgResponseTime = responseTimes.average()
        
        if (responseTimes.size > 1) {
            val variance = responseTimes.map { (it - avgResponseTime) * (it - avgResponseTime) }.average()
            stdDevResponseTime = kotlin.math.sqrt(variance)
        }
    }
    
    fun getMetrics(elapsedTimeMs: Long, strategyName: String): FuzzingMetrics {
        val testsPerSecond = if (elapsedTimeMs > 0) {
            (responseHistory.size * 1000.0) / elapsedTimeMs
        } else {
            0.0
        }
        
        // Coverage based on unique responses vs total tests
        val coveragePercent = if (responseHistory.size > 0) {
            (uniqueResponses.size.toDouble() / responseHistory.size.toDouble()) * 100.0
        } else {
            0.0
        }
        
        val minTime = responseTimes.minOrNull() ?: 0L
        val maxTime = responseTimes.maxOrNull() ?: 0L
        val timeoutCount = responseTimes.count { it > 4500 }
        val errorCount = responseHistory.count { it.response == null || it.response.isEmpty() }
        val anomaliesCount = responseHistory.sumOf { it.anomalies.size }
        val crashCount = responseHistory.count { it.response == null || it.response.isEmpty() }
        
        return FuzzingMetrics(
            testsExecuted = responseHistory.size,
            testsPerSecond = testsPerSecond,
            uniqueResponses = uniqueResponses.size,
            uniqueStatusWords = uniqueStatusWords,
            coveragePercent = coveragePercent,
            anomaliesFound = anomaliesCount,
            crashesDetected = crashCount,
            currentStrategy = strategyName,
            elapsedTimeMs = elapsedTimeMs,
            averageResponseTimeMs = avgResponseTime,
            minResponseTimeMs = minTime,
            maxResponseTimeMs = maxTime,
            timeoutCount = timeoutCount,
            errorCount = errorCount
        )
    }
    
    fun getInterestingFindings(): List<FuzzTestResult> {
        // Return results with anomalies, sorted by severity
        return responseHistory
            .filter { it.anomalies.isNotEmpty() }
            .sortedByDescending { result ->
                result.anomalies.maxOfOrNull { anomaly ->
                    when (anomaly.severity) {
                        AnomalySeverity.CRITICAL -> 4
                        AnomalySeverity.HIGH -> 3
                        AnomalySeverity.MEDIUM -> 2
                        AnomalySeverity.LOW -> 1
                    }
                } ?: 0
            }
            .take(50) // Top 50 most interesting
    }
    
    fun getAllAnomalies(): List<Anomaly> {
        return responseHistory.flatMap { it.anomalies }
    }
    
    fun reset() {
        responseHistory.clear()
        uniqueResponses.clear()
        uniqueStatusWords.clear()
        responseTimes.clear()
        avgResponseTime = 0.0
        stdDevResponseTime = 0.0
    }
    
    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }
}
