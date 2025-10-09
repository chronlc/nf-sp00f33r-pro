package com.nfsp00f33r.app.fuzzing

import com.nfsp00f33r.app.fuzzing.models.Anomaly
import com.nfsp00f33r.app.fuzzing.models.AnomalySeverity
import com.nfsp00f33r.app.fuzzing.storage.FuzzingAnomalyEntity
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Tests reproducibility of discovered crashes and anomalies
 * Attempts to reproduce anomalies multiple times to confirm reliability
 */
class CrashReproductionTester(
    private val nfcExecutor: NfcFuzzingExecutor
) {
    
    data class ReproductionResult(
        val anomaly: FuzzingAnomalyEntity,
        val reproduced: Boolean,
        val reproductionCount: Int,
        val totalAttempts: Int,
        val reproductionRate: Double,
        val consistentBehavior: Boolean,
        val responses: List<ByteArray?>
    )
    
    /**
     * Test reproducibility of a single anomaly
     * @param attempts Number of reproduction attempts
     * @return ReproductionResult with statistics
     */
    suspend fun testReproducibility(
        anomaly: FuzzingAnomalyEntity,
        attempts: Int = 10,
        delayBetweenAttempts: Long = 100
    ): ReproductionResult {
        Timber.i("🔬 Testing reproducibility for anomaly ${anomaly.anomalyId}: ${anomaly.type}")
        
        val command = hexToBytes(anomaly.commandHex)
        val originalResponse = anomaly.responseHex?.let { hexToBytes(it) }
        
        var reproductionCount = 0
        val responses = mutableListOf<ByteArray?>()
        
        repeat(attempts) { attempt ->
            Timber.d("  Attempt ${attempt + 1}/$attempts")
            
            val (response, executionTime) = nfcExecutor.executeCommand(command, 5000)
            responses.add(response)
            
            // Check if this reproduces the original anomaly
            val reproduced = checkIfReproduced(
                originalResponse = originalResponse,
                currentResponse = response,
                originalSeverity = anomaly.severity,
                executionTime = executionTime
            )
            
            if (reproduced) {
                reproductionCount++
                Timber.d("    ✅ Reproduced (${reproductionCount}/$attempts)")
            } else {
                Timber.d("    ❌ Not reproduced")
            }
            
            delay(delayBetweenAttempts)
        }
        
        val reproductionRate = reproductionCount.toDouble() / attempts.toDouble()
        val consistentBehavior = checkConsistency(responses)
        
        val result = ReproductionResult(
            anomaly = anomaly,
            reproduced = reproductionCount > 0,
            reproductionCount = reproductionCount,
            totalAttempts = attempts,
            reproductionRate = reproductionRate,
            consistentBehavior = consistentBehavior,
            responses = responses
        )
        
        Timber.i("📊 Reproduction rate: ${(reproductionRate * 100).toInt()}% ($reproductionCount/$attempts)")
        
        return result
    }
    
    /**
     * Test reproducibility of multiple anomalies
     */
    suspend fun testMultipleAnomalies(
        anomalies: List<FuzzingAnomalyEntity>,
        attemptsPerAnomaly: Int = 10
    ): List<ReproductionResult> {
        Timber.i("🔬 Testing reproducibility for ${anomalies.size} anomalies")
        
        val results = mutableListOf<ReproductionResult>()
        
        anomalies.forEach { anomaly ->
            val result = testReproducibility(anomaly, attemptsPerAnomaly)
            results.add(result)
            
            // Brief delay between anomaly tests
            delay(500)
        }
        
        // Summary statistics
        val totalReproducible = results.count { it.reproduced }
        val avgReproductionRate = results.map { it.reproductionRate }.average()
        
        Timber.i("""
            📊 Reproduction Testing Complete:
               Total Anomalies: ${anomalies.size}
               Reproducible: $totalReproducible (${(totalReproducible.toDouble() / anomalies.size * 100).toInt()}%)
               Average Reproduction Rate: ${(avgReproductionRate * 100).toInt()}%
        """.trimIndent())
        
        return results
    }
    
    /**
     * Check if current response reproduces the original anomaly
     */
    private fun checkIfReproduced(
        originalResponse: ByteArray?,
        currentResponse: ByteArray?,
        originalSeverity: String,
        executionTime: Long
    ): Boolean {
        // Check for crash (null response)
        if (originalResponse == null && currentResponse == null) {
            return true
        }
        
        // Check for timeout
        if (originalSeverity == "HIGH" && executionTime > 4500) {
            return true
        }
        
        // Check for identical response
        if (originalResponse != null && currentResponse != null) {
            if (originalResponse.contentEquals(currentResponse)) {
                return true
            }
        }
        
        // Check for similar error patterns (same status word)
        if (originalResponse != null && currentResponse != null) {
            if (originalResponse.size >= 2 && currentResponse.size >= 2) {
                val originalSW = originalResponse.takeLast(2)
                val currentSW = currentResponse.takeLast(2)
                if (originalSW[0] == currentSW[0] && originalSW[1] == currentSW[1]) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Check consistency of responses
     */
    private fun checkConsistency(responses: List<ByteArray?>): Boolean {
        if (responses.isEmpty()) return false
        
        // All null responses = consistent crash
        if (responses.all { it == null }) return true
        
        // All non-null responses should be identical
        val nonNullResponses = responses.filterNotNull()
        if (nonNullResponses.isEmpty()) return true
        
        val firstResponse = nonNullResponses.first()
        return nonNullResponses.all { it.contentEquals(firstResponse) }
    }
    
    /**
     * Generate reproducibility report
     */
    fun generateReport(results: List<ReproductionResult>): String {
        val sb = StringBuilder()
        sb.appendLine("=== CRASH REPRODUCIBILITY REPORT ===")
        sb.appendLine()
        
        sb.appendLine("Summary:")
        sb.appendLine("  Total Anomalies Tested: ${results.size}")
        sb.appendLine("  Reproducible: ${results.count { it.reproduced }}")
        sb.appendLine("  Non-Reproducible: ${results.count { !it.reproduced }}")
        sb.appendLine("  Average Reproduction Rate: ${(results.map { it.reproductionRate }.average() * 100).toInt()}%")
        sb.appendLine()
        
        // Group by severity
        val bySeverity = results.groupBy { it.anomaly.severity }
        sb.appendLine("By Severity:")
        listOf("CRITICAL", "HIGH", "MEDIUM", "LOW").forEach { severity ->
            val severityResults = bySeverity[severity] ?: emptyList()
            if (severityResults.isNotEmpty()) {
                val avgRate = severityResults.map { it.reproductionRate }.average()
                sb.appendLine("  $severity: ${severityResults.size} anomalies, ${(avgRate * 100).toInt()}% avg reproduction rate")
            }
        }
        sb.appendLine()
        
        // Detailed results
        sb.appendLine("Detailed Results:")
        results.sortedByDescending { it.reproductionRate }.forEach { result ->
            sb.appendLine()
            sb.appendLine("  Anomaly #${result.anomaly.anomalyId}: ${result.anomaly.type}")
            sb.appendLine("    Severity: ${result.anomaly.severity}")
            sb.appendLine("    Command: ${result.anomaly.commandHex}")
            sb.appendLine("    Reproduction Rate: ${(result.reproductionRate * 100).toInt()}% (${result.reproductionCount}/${result.totalAttempts})")
            sb.appendLine("    Consistent Behavior: ${if (result.consistentBehavior) "Yes" else "No"}")
            sb.appendLine("    Status: ${if (result.reproduced) "REPRODUCIBLE ✅" else "NOT REPRODUCIBLE ❌"}")
        }
        
        return sb.toString()
    }
    
    private fun hexToBytes(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").replace(":", "")
        return cleaned.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
