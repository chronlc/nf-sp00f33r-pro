package com.nfsp00f33r.app.emulation

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * AttackAnalytics - Feature Enhancement Phase
 * 
 * Comprehensive analytics engine for EMV attack tracking and reporting
 * Provides detailed statistics, success rate analysis, and performance metrics
 * 
 * Features:
 * - Per-attack-type success tracking
 * - Timing and performance analysis
 * - Card type correlation
 * - Attack pattern analysis
 * - Success rate trends
 * - Detailed reporting
 */
class AttackAnalytics {
    
    companion object {
        private const val TAG = "📊 Analytics"
    }
    
    /**
     * Attack execution record
     */
    data class AttackExecution(
        val attackType: String,
        val timestamp: Long,
        val success: Boolean,
        val executionTimeMs: Long,
        val pan: String,
        val cardType: String,
        val errorMessage: String?
    )
    
    /**
     * Attack statistics per type
     */
    data class AttackTypeStatistics(
        val attackType: String,
        val totalExecutions: Int,
        val successfulExecutions: Int,
        val failedExecutions: Int,
        val successRate: Double,
        val averageExecutionTimeMs: Long,
        val fastestExecutionMs: Long,
        val slowestExecutionMs: Long,
        val lastExecution: Long?,
        val cardTypeBreakdown: Map<String, Int>
    )
    
    /**
     * Overall analytics report
     */
    data class AnalyticsReport(
        val totalAttacks: Int,
        val totalSuccessful: Int,
        val totalFailed: Int,
        val overallSuccessRate: Double,
        val uniqueCards: Int,
        val attackTypeStats: List<AttackTypeStatistics>,
        val topPerformingAttack: String?,
        val worstPerformingAttack: String?,
        val averageExecutionTime: Long,
        val totalExecutionTime: Long,
        val reportGeneratedAt: Long
    )
    
    // Thread-safe storage
    private val executions = ConcurrentHashMap<String, MutableList<AttackExecution>>()
    private val cardTypes = ConcurrentHashMap<String, String>() // PAN -> CardType
    
    /**
     * Record an attack execution
     */
    fun recordExecution(
        attackType: String,
        success: Boolean,
        executionTimeMs: Long,
        pan: String,
        cardType: String = detectCardType(pan),
        errorMessage: String? = null
    ) {
        val execution = AttackExecution(
            attackType = attackType,
            timestamp = System.currentTimeMillis(),
            success = success,
            executionTimeMs = executionTimeMs,
            pan = pan,
            cardType = cardType,
            errorMessage = errorMessage
        )
        
        // Store execution
        executions.getOrPut(attackType) { mutableListOf() }.add(execution)
        
        // Track card type
        cardTypes[pan] = cardType
        
        // Limit storage (keep last 500 per attack type)
        executions[attackType]?.let { list ->
            if (list.size > 500) {
                list.removeAt(0)
            }
        }
        
        Timber.d("$TAG Recorded $attackType: ${if (success) "✓" else "✗"} ($executionTimeMs ms)")
    }
    
    /**
     * Get statistics for specific attack type
     */
    fun getAttackTypeStatistics(attackType: String): AttackTypeStatistics? {
        val attackExecutions = executions[attackType] ?: return null
        
        if (attackExecutions.isEmpty()) return null
        
        val successful = attackExecutions.count { it.success }
        val failed = attackExecutions.count { !it.success }
        val successRate = (successful.toDouble() / attackExecutions.size.toDouble()) * 100
        
        val executionTimes = attackExecutions.map { it.executionTimeMs }
        val avgTime = executionTimes.average().toLong()
        val fastest = executionTimes.minOrNull() ?: 0
        val slowest = executionTimes.maxOrNull() ?: 0
        
        val lastExecution = attackExecutions.maxByOrNull { it.timestamp }?.timestamp
        
        // Card type breakdown
        val cardTypeBreakdown = attackExecutions.groupingBy { it.cardType }.eachCount()
        
        return AttackTypeStatistics(
            attackType = attackType,
            totalExecutions = attackExecutions.size,
            successfulExecutions = successful,
            failedExecutions = failed,
            successRate = successRate,
            averageExecutionTimeMs = avgTime,
            fastestExecutionMs = fastest,
            slowestExecutionMs = slowest,
            lastExecution = lastExecution,
            cardTypeBreakdown = cardTypeBreakdown
        )
    }
    
    /**
     * Generate comprehensive analytics report
     */
    fun generateReport(): AnalyticsReport {
        val allExecutions = executions.values.flatten()
        
        if (allExecutions.isEmpty()) {
            return AnalyticsReport(
                totalAttacks = 0,
                totalSuccessful = 0,
                totalFailed = 0,
                overallSuccessRate = 0.0,
                uniqueCards = 0,
                attackTypeStats = emptyList(),
                topPerformingAttack = null,
                worstPerformingAttack = null,
                averageExecutionTime = 0,
                totalExecutionTime = 0,
                reportGeneratedAt = System.currentTimeMillis()
            )
        }
        
        val totalSuccessful = allExecutions.count { it.success }
        val totalFailed = allExecutions.count { !it.success }
        val overallSuccessRate = (totalSuccessful.toDouble() / allExecutions.size.toDouble()) * 100
        
        val uniqueCards = allExecutions.map { it.pan }.distinct().size
        
        val attackTypeStats = executions.keys.mapNotNull { getAttackTypeStatistics(it) }
            .sortedByDescending { it.successRate }
        
        val topPerforming = attackTypeStats.maxByOrNull { it.successRate }?.attackType
        val worstPerforming = attackTypeStats.minByOrNull { it.successRate }?.attackType
        
        val avgTime = allExecutions.map { it.executionTimeMs }.average().toLong()
        val totalTime = allExecutions.sumOf { it.executionTimeMs }
        
        Timber.i("$TAG Generated report: ${allExecutions.size} attacks, ${overallSuccessRate.toInt()}% success, $uniqueCards unique cards")
        
        return AnalyticsReport(
            totalAttacks = allExecutions.size,
            totalSuccessful = totalSuccessful,
            totalFailed = totalFailed,
            overallSuccessRate = overallSuccessRate,
            uniqueCards = uniqueCards,
            attackTypeStats = attackTypeStats,
            topPerformingAttack = topPerforming,
            worstPerformingAttack = worstPerforming,
            averageExecutionTime = avgTime,
            totalExecutionTime = totalTime,
            reportGeneratedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Get success rate trend for attack type
     */
    fun getSuccessRateTrend(attackType: String, buckets: Int = 10): List<Double> {
        val attackExecutions = executions[attackType] ?: return emptyList()
        
        if (attackExecutions.size < buckets) {
            return listOf(getAttackTypeStatistics(attackType)?.successRate ?: 0.0)
        }
        
        val bucketSize = attackExecutions.size / buckets
        return (0 until buckets).map { bucketIndex ->
            val start = bucketIndex * bucketSize
            val end = if (bucketIndex == buckets - 1) attackExecutions.size else (bucketIndex + 1) * bucketSize
            val bucketExecutions = attackExecutions.subList(start, end)
            val successful = bucketExecutions.count { it.success }
            (successful.toDouble() / bucketExecutions.size.toDouble()) * 100
        }
    }
    
    /**
     * Get executions for specific card
     */
    fun getCardHistory(pan: String): List<AttackExecution> {
        return executions.values.flatten().filter { it.pan == pan }
            .sortedByDescending { it.timestamp }
    }
    
    /**
     * Get most recent executions
     */
    fun getRecentExecutions(limit: Int = 20): List<AttackExecution> {
        return executions.values.flatten()
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    /**
     * Clear all analytics data
     */
    fun clear() {
        Timber.i("$TAG Clearing all analytics data")
        executions.clear()
        cardTypes.clear()
    }
    
    /**
     * Export analytics data
     */
    fun exportData(): Map<String, Any> {
        val report = generateReport()
        return mapOf(
            "report" to report,
            "executions" to executions.values.flatten(),
            "card_types" to cardTypes,
            "exported_at" to System.currentTimeMillis()
        )
    }
    
    /**
     * Detect card type from PAN
     */
    private fun detectCardType(pan: String): String {
        return when {
            pan.startsWith("4") -> "VISA"
            pan.startsWith("5") || pan.startsWith("2") -> "MASTERCARD"
            pan.startsWith("3") -> "AMEX"
            pan.startsWith("6") -> "DISCOVER"
            pan.startsWith("35") -> "JCB"
            else -> "UNKNOWN"
        }
    }
}
