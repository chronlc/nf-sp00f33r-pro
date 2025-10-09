package com.nfsp00f33r.app.emulation

import android.content.Context
import com.nfsp00f33r.app.core.BaseModule
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.core.getLogger
import com.nfsp00f33r.app.data.EmvCardData
import com.nfsp00f33r.app.models.CardProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EmulationModule - Phase 2B Days 5-6
 * 
 * Module-based wrapper for EmvAttackEmulationManager
 * Integrates EMV attack emulation into the module system
 * 
 * Features:
 * - Lifecycle management via BaseModule
 * - Health monitoring of emulation readiness
 * - Attack execution tracking
 * - Card data validation
 * - Integration with ModuleRegistry
 * 
 * @property context Android application context (unused but required for consistency)
 */
class EmulationModule(
    private val context: Context
) : BaseModule() {
    
    override val name: String = "Emulation"
    override val dependencies: List<String> = listOf("CardDataStore") // Needs card data for attacks
    
    override fun getVersion(): String = "1.0.0"
    override fun getDescription(): String = "EMV attack emulation and card spoofing module"
    
    /**
     * Underlying EmvAttackEmulationManager instance
     */
    private lateinit var manager: EmvAttackEmulationManager
    
    /**
     * ENHANCED: Attack chain coordinator for complex attack sequences
     */
    private lateinit var chainCoordinator: AttackChainCoordinator
    
    /**
     * ENHANCED: Analytics engine for detailed attack tracking
     */
    private lateinit var analytics: AttackAnalytics
    
    /**
     * Attack execution tracking
     */
    private var attackExecutions: Int = 0
    private var successfulAttacks: Int = 0
    private var failedAttacks: Int = 0
    private var lastAttackType: String? = null
    private val attackHistory = mutableListOf<AttackRecord>()
    
    /**
     * Initialize the emulation module
     * ENHANCED: Now initializes attack chain coordinator and analytics
     */
    override suspend fun onInitialize() = withContext(Dispatchers.IO) {
        getLogger().info("Initializing Emulation module with enhanced features...")
        
        // Create manager instance
        manager = EmvAttackEmulationManager()
        
        // ENHANCED: Initialize attack chain coordinator
        chainCoordinator = AttackChainCoordinator(manager)
        
        // ENHANCED: Initialize analytics engine
        analytics = AttackAnalytics()
        
        // Reset statistics
        attackExecutions = 0
        successfulAttacks = 0
        failedAttacks = 0
        lastAttackType = null
        attackHistory.clear()
        
        getLogger().info("Emulation module initialized with attack chaining and analytics")
    }
    
    /**
     * Shutdown the emulation module
     */
    override suspend fun onShutdown() = withContext(Dispatchers.IO) {
        getLogger().info("Shutting down Emulation module...")
        getLogger().info("Attack statistics: total=$attackExecutions, success=$successfulAttacks, failed=$failedAttacks")
        getLogger().info("Emulation module shutdown complete")
    }
    
    /**
     * Check health of the emulation system
     */
    override fun checkHealth(): HealthStatus {
        return try {
            // Check if manager is initialized
            if (!::manager.isInitialized) {
                return HealthStatus.unhealthy(
                    "EmvAttackEmulationManager not initialized",
                    HealthStatus.Severity.CRITICAL
                )
            }
            
            // Calculate success rate
            val successRate = if (attackExecutions > 0) {
                (successfulAttacks.toDouble() / attackExecutions.toDouble()) * 100
            } else {
                100.0 // No failures if no attempts
            }
            
            // Warn if success rate is low
            if (attackExecutions > 10 && successRate < 50.0) {
                return HealthStatus(
                    isHealthy = true,
                    message = "Emulation operational but low success rate: ${successRate.toInt()}%",
                    severity = HealthStatus.Severity.WARNING,
                    metrics = mapOf(
                        "attackExecutions" to attackExecutions,
                        "successfulAttacks" to successfulAttacks,
                        "failedAttacks" to failedAttacks,
                        "successRate" to successRate,
                        "lastAttackType" to (lastAttackType ?: "none")
                    ),
                    timestamp = System.currentTimeMillis()
                )
            }
            
            // All healthy
            HealthStatus.healthy(
                "Emulation system operational",
                mapOf(
                    "attackExecutions" to attackExecutions,
                    "successfulAttacks" to successfulAttacks,
                    "failedAttacks" to failedAttacks,
                    "successRate" to successRate,
                    "lastAttackType" to (lastAttackType ?: "none"),
                    "historySize" to attackHistory.size
                )
            )
            
        } catch (e: Exception) {
            getLogger().error("Health check failed", e)
            HealthStatus.unhealthy(
                "Health check failed: ${e.message}",
                HealthStatus.Severity.ERROR
            )
        }
    }
    
    // ========== Public API (delegates to EmvAttackEmulationManager) ==========
    
    /**
     * Get available attacks for card data
     */
    fun getAvailableAttacks(cardData: EmvCardData): List<String> {
        ensureInitialized()
        return manager.getAvailableAttacks(cardData)
    }
    
    /**
     * Execute attack with enhanced tracking and analytics
     * ENHANCED: Now records detailed analytics and timing data
     */
    fun executeAttack(attackType: String, cardData: EmvCardData): Map<String, Any> {
        ensureInitialized()
        getLogger().info("Executing attack: $attackType")
        
        attackExecutions++
        lastAttackType = attackType
        
        val startTime = System.currentTimeMillis()
        val result = try {
            val attackResult = manager.executeAttack(attackType, cardData)
            val executionTime = System.currentTimeMillis() - startTime
            
            // Check if attack was successful
            val status = attackResult["status"] as? String
            val success = status == "SUCCESS" || status == "READY"
            
            if (success) {
                successfulAttacks++
                getLogger().info("Attack $attackType succeeded in ${executionTime}ms")
            } else {
                failedAttacks++
                getLogger().warn("Attack $attackType failed: $status in ${executionTime}ms")
            }
            
            // Record attack
            attackHistory.add(
                AttackRecord(
                    attackType = attackType,
                    timestamp = System.currentTimeMillis(),
                    success = success,
                    pan = cardData.getUnmaskedPan()
                )
            )
            
            // ENHANCED: Record in analytics
            analytics.recordExecution(
                attackType = attackType,
                success = success,
                executionTimeMs = executionTime,
                pan = cardData.getUnmaskedPan(),
                errorMessage = if (!success) status else null
            )
            
            // Limit history size
            if (attackHistory.size > 100) {
                attackHistory.removeAt(0)
            }
            
            // Add timing to result
            attackResult.toMutableMap().apply {
                put("executionTimeMs", executionTime)
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            failedAttacks++
            getLogger().error("Exception executing attack $attackType", e)
            
            // ENHANCED: Record failure in analytics
            analytics.recordExecution(
                attackType = attackType,
                success = false,
                executionTimeMs = executionTime,
                pan = cardData.getUnmaskedPan(),
                errorMessage = e.message
            )
            
            mapOf(
                "status" to "ERROR",
                "error" to (e.message ?: "Unknown error"),
                "attackType" to attackType,
                "executionTimeMs" to executionTime
            )
        }
        
        return result
    }
    
    /**
     * Validate card data for attack
     */
    fun validateCardData(attackType: String, cardData: EmvCardData): Boolean {
        ensureInitialized()
        return manager.validateCardData(attackType, cardData)
    }
    
    /**
     * Get attack information
     */
    fun getAttackInfo(attackType: String): Map<String, String> {
        ensureInitialized()
        return manager.getAttackInfo(attackType)
    }
    
    /**
     * Get attack statistics for card profile
     */
    fun getAttackStatistics(cardProfile: CardProfile): Map<String, Any> {
        ensureInitialized()
        return manager.getAttackStatistics(cardProfile)
    }
    
    /**
     * Check if card profile is ready for emulation
     */
    fun isEmulationReady(cardProfile: CardProfile): Boolean {
        ensureInitialized()
        return manager.isEmulationReady(cardProfile)
    }
    
    /**
     * Get emulation statistics
     */
    fun getStatistics(): EmulationStatistics {
        return EmulationStatistics(
            attackExecutions = attackExecutions,
            successfulAttacks = successfulAttacks,
            failedAttacks = failedAttacks,
            successRate = if (attackExecutions > 0) {
                (successfulAttacks.toDouble() / attackExecutions.toDouble()) * 100
            } else {
                0.0
            },
            lastAttackType = lastAttackType,
            historySize = attackHistory.size
        )
    }
    
    /**
     * Get attack history
     */
    fun getAttackHistory(limit: Int = 20): List<AttackRecord> {
        return attackHistory.takeLast(limit)
    }
    
    /**
     * Clear attack history
     */
    fun clearHistory() {
        getLogger().info("Clearing attack history")
        attackHistory.clear()
    }
    
    // ========== ENHANCED: Attack Chain Coordinator API ==========
    
    /**
     * Execute a predefined attack chain
     */
    fun executeAttackChain(chainName: String, cardData: EmvCardData): AttackChainCoordinator.ChainResult {
        ensureInitialized()
        getLogger().info("Executing attack chain: $chainName")
        return chainCoordinator.executeChain(chainName, cardData)
    }
    
    /**
     * Execute a custom attack sequence
     */
    fun executeCustomAttackChain(
        chainName: String,
        attacks: List<String>,
        cardData: EmvCardData,
        stopOnFailure: Boolean = false
    ): AttackChainCoordinator.ChainResult {
        ensureInitialized()
        return chainCoordinator.executeCustomChain(chainName, attacks, cardData, stopOnFailure)
    }
    
    /**
     * Get recommended attack chain for card
     */
    fun getRecommendedAttackChain(cardData: EmvCardData): Pair<String, List<String>> {
        ensureInitialized()
        return chainCoordinator.getRecommendedChain(cardData)
    }
    
    /**
     * Analyze attack chain feasibility
     */
    fun analyzeChainFeasibility(
        chainName: String,
        cardData: EmvCardData
    ): AttackChainCoordinator.ChainFeasibilityReport {
        ensureInitialized()
        return chainCoordinator.analyzeChainFeasibility(chainName, cardData)
    }
    
    // ========== ENHANCED: Analytics API ==========
    
    /**
     * Get analytics report
     */
    fun getAnalyticsReport(): AttackAnalytics.AnalyticsReport {
        ensureInitialized()
        return analytics.generateReport()
    }
    
    /**
     * Get statistics for specific attack type
     */
    fun getAttackTypeStatistics(attackType: String): AttackAnalytics.AttackTypeStatistics? {
        ensureInitialized()
        return analytics.getAttackTypeStatistics(attackType)
    }
    
    /**
     * Get success rate trend for attack type
     */
    fun getSuccessRateTrend(attackType: String, buckets: Int = 10): List<Double> {
        ensureInitialized()
        return analytics.getSuccessRateTrend(attackType, buckets)
    }
    
    /**
     * Get attack history for specific card
     */
    fun getCardAttackHistory(pan: String): List<AttackAnalytics.AttackExecution> {
        ensureInitialized()
        return analytics.getCardHistory(pan)
    }
    
    /**
     * Get recent attack executions
     */
    fun getRecentExecutions(limit: Int = 20): List<AttackAnalytics.AttackExecution> {
        ensureInitialized()
        return analytics.getRecentExecutions(limit)
    }
    
    /**
     * Export all analytics data
     */
    fun exportAnalyticsData(): Map<String, Any> {
        ensureInitialized()
        return analytics.exportData()
    }
    
    /**
     * Clear analytics data
     */
    fun clearAnalytics() {
        ensureInitialized()
        getLogger().info("Clearing analytics data")
        analytics.clear()
    }
    
    /**
     * Ensure module is initialized before operations
     */
    private fun ensureInitialized() {
        if (!::manager.isInitialized) {
            throw IllegalStateException("EmulationModule not initialized. Call initialize() first or register with ModuleRegistry.")
        }
    }
    
    /**
     * Attack execution record
     */
    data class AttackRecord(
        val attackType: String,
        val timestamp: Long,
        val success: Boolean,
        val pan: String
    )
    
    /**
     * Emulation statistics data class
     */
    data class EmulationStatistics(
        val attackExecutions: Int,
        val successfulAttacks: Int,
        val failedAttacks: Int,
        val successRate: Double,
        val lastAttackType: String?,
        val historySize: Int
    )
}
