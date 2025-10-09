package com.nfsp00f33r.app.emulation

import com.nfsp00f33r.app.data.EmvCardData
import timber.log.Timber

/**
 * AttackChainCoordinator - Feature Enhancement Phase
 * 
 * Coordinates chained EMV attacks for comprehensive card exploitation
 * Executes multiple attack sequences automatically with dependency management
 * 
 * Features:
 * - Attack dependency resolution
 * - Sequential attack execution
 * - Failure recovery and fallback strategies
 * - Success rate tracking per chain
 * - Timing analysis
 * - Comprehensive result aggregation
 */
class AttackChainCoordinator(
    private val manager: EmvAttackEmulationManager
) {
    
    companion object {
        private const val TAG = "🔗 AttackChain"
        
        /**
         * Predefined attack chains for different scenarios
         */
        val CHAIN_OFFLINE_FRAUD = listOf("AIP_FORCE_OFFLINE", "CVM_BYPASS", "TRACK2_MANIPULATION")
        val CHAIN_CRYPTOGRAM_BYPASS = listOf("CRYPTOGRAM_DOWNGRADE", "CVM_BYPASS")
        val CHAIN_FULL_COMPROMISE = listOf("PPSE_AID_POISONING", "AIP_FORCE_OFFLINE", "CRYPTOGRAM_DOWNGRADE", "CVM_BYPASS", "TRACK2_MANIPULATION")
        val CHAIN_QUICK_BYPASS = listOf("CVM_BYPASS", "TRACK2_MANIPULATION")
    }
    
    /**
     * Attack chain execution result
     */
    data class ChainResult(
        val chainName: String,
        val totalAttacks: Int,
        val successfulAttacks: Int,
        val failedAttacks: Int,
        val successRate: Double,
        val executionTimeMs: Long,
        val attackResults: List<AttackStepResult>,
        val overallSuccess: Boolean,
        val failurePoint: String?
    )
    
    /**
     * Individual attack step result
     */
    data class AttackStepResult(
        val attackType: String,
        val success: Boolean,
        val executionTimeMs: Long,
        val result: Map<String, Any>,
        val order: Int
    )
    
    /**
     * Execute a predefined attack chain
     */
    fun executeChain(chainName: String, cardData: EmvCardData): ChainResult {
        val attacks = when (chainName) {
            "OFFLINE_FRAUD" -> CHAIN_OFFLINE_FRAUD
            "CRYPTOGRAM_BYPASS" -> CHAIN_CRYPTOGRAM_BYPASS
            "FULL_COMPROMISE" -> CHAIN_FULL_COMPROMISE
            "QUICK_BYPASS" -> CHAIN_QUICK_BYPASS
            else -> {
                Timber.w("$TAG Unknown chain: $chainName")
                return ChainResult(
                    chainName = chainName,
                    totalAttacks = 0,
                    successfulAttacks = 0,
                    failedAttacks = 0,
                    successRate = 0.0,
                    executionTimeMs = 0,
                    attackResults = emptyList(),
                    overallSuccess = false,
                    failurePoint = "UNKNOWN_CHAIN"
                )
            }
        }
        
        return executeCustomChain(chainName, attacks, cardData)
    }
    
    /**
     * Execute a custom attack sequence
     */
    fun executeCustomChain(
        chainName: String,
        attacks: List<String>,
        cardData: EmvCardData,
        stopOnFailure: Boolean = false
    ): ChainResult {
        Timber.i("$TAG Executing chain '$chainName' with ${attacks.size} attacks")
        
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<AttackStepResult>()
        var successCount = 0
        var failurePoint: String? = null
        
        attacks.forEachIndexed { index, attackType ->
            // Validate card data before attack
            if (!manager.validateCardData(attackType, cardData)) {
                Timber.w("$TAG Attack $attackType skipped - insufficient card data")
                val stepResult = AttackStepResult(
                    attackType = attackType,
                    success = false,
                    executionTimeMs = 0,
                    result = mapOf("status" to "INSUFFICIENT_DATA"),
                    order = index + 1
                )
                results.add(stepResult)
                
                if (stopOnFailure && failurePoint == null) {
                    failurePoint = attackType
                    return@forEachIndexed
                }
                return@forEachIndexed
            }
            
            // Execute attack
            val attackStart = System.currentTimeMillis()
            val attackResult = manager.executeAttack(attackType, cardData)
            val attackTime = System.currentTimeMillis() - attackStart
            
            val success = attackResult["status"] == "SUCCESS" || attackResult["status"] == "READY"
            if (success) successCount++
            
            val stepResult = AttackStepResult(
                attackType = attackType,
                success = success,
                executionTimeMs = attackTime,
                result = attackResult,
                order = index + 1
            )
            results.add(stepResult)
            
            Timber.d("$TAG [${index + 1}/${attacks.size}] $attackType: ${if (success) "✓" else "✗"} (${attackTime}ms)")
            
            // Stop if failure and stopOnFailure enabled
            if (!success && stopOnFailure && failurePoint == null) {
                failurePoint = attackType
                Timber.w("$TAG Chain stopped at $attackType due to failure")
                return@forEachIndexed
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        val successRate = if (results.isNotEmpty()) {
            (successCount.toDouble() / results.size.toDouble()) * 100
        } else {
            0.0
        }
        
        val overallSuccess = if (stopOnFailure) {
            failurePoint == null && successCount == attacks.size
        } else {
            successRate >= 50.0 // At least 50% success
        }
        
        Timber.i("$TAG Chain '$chainName' complete: $successCount/${results.size} successful (${successRate.toInt()}%) in ${totalTime}ms")
        
        return ChainResult(
            chainName = chainName,
            totalAttacks = results.size,
            successfulAttacks = successCount,
            failedAttacks = results.size - successCount,
            successRate = successRate,
            executionTimeMs = totalTime,
            attackResults = results,
            overallSuccess = overallSuccess,
            failurePoint = failurePoint
        )
    }
    
    /**
     * Get recommended attack chain for card data
     */
    fun getRecommendedChain(cardData: EmvCardData): Pair<String, List<String>> {
        val availableAttacks = manager.getAvailableAttacks(cardData)
        
        return when {
            // Full data available - full compromise
            availableAttacks.size >= 4 -> "FULL_COMPROMISE" to CHAIN_FULL_COMPROMISE
            
            // Cryptogram available - cryptogram bypass
            availableAttacks.contains("CRYPTOGRAM_DOWNGRADE") -> 
                "CRYPTOGRAM_BYPASS" to CHAIN_CRYPTOGRAM_BYPASS
            
            // AIP available - offline fraud
            availableAttacks.contains("AIP_FORCE_OFFLINE") -> 
                "OFFLINE_FRAUD" to CHAIN_OFFLINE_FRAUD
            
            // Minimal data - quick bypass
            else -> "QUICK_BYPASS" to CHAIN_QUICK_BYPASS
        }
    }
    
    /**
     * Analyze chain feasibility for card data
     */
    fun analyzeChainFeasibility(chainName: String, cardData: EmvCardData): ChainFeasibilityReport {
        val attacks = when (chainName) {
            "OFFLINE_FRAUD" -> CHAIN_OFFLINE_FRAUD
            "CRYPTOGRAM_BYPASS" -> CHAIN_CRYPTOGRAM_BYPASS
            "FULL_COMPROMISE" -> CHAIN_FULL_COMPROMISE
            "QUICK_BYPASS" -> CHAIN_QUICK_BYPASS
            else -> emptyList()
        }
        
        val availableAttacks = mutableListOf<String>()
        val missingAttacks = mutableListOf<String>()
        val missingData = mutableMapOf<String, String>()
        
        attacks.forEach { attackType ->
            if (manager.validateCardData(attackType, cardData)) {
                availableAttacks.add(attackType)
            } else {
                missingAttacks.add(attackType)
                missingData[attackType] = when (attackType) {
                    "PPSE_AID_POISONING" -> "PAN required"
                    "AIP_FORCE_OFFLINE" -> "AIP required"
                    "TRACK2_MANIPULATION" -> "Track2 data required"
                    "CRYPTOGRAM_DOWNGRADE" -> "Cryptogram data required"
                    "CVM_BYPASS" -> "CVM list required"
                    else -> "Unknown requirements"
                }
            }
        }
        
        val feasibilityScore = if (attacks.isNotEmpty()) {
            (availableAttacks.size.toDouble() / attacks.size.toDouble()) * 100
        } else {
            0.0
        }
        
        val feasible = feasibilityScore >= 60.0 // At least 60% of attacks available
        
        return ChainFeasibilityReport(
            chainName = chainName,
            totalAttacks = attacks.size,
            availableAttacks = availableAttacks.size,
            missingAttacks = missingAttacks.size,
            feasibilityScore = feasibilityScore,
            feasible = feasible,
            availableAttackTypes = availableAttacks,
            missingAttackTypes = missingAttacks,
            missingDataRequirements = missingData
        )
    }
    
    /**
     * Chain feasibility report
     */
    data class ChainFeasibilityReport(
        val chainName: String,
        val totalAttacks: Int,
        val availableAttacks: Int,
        val missingAttacks: Int,
        val feasibilityScore: Double,
        val feasible: Boolean,
        val availableAttackTypes: List<String>,
        val missingAttackTypes: List<String>,
        val missingDataRequirements: Map<String, String>
    )
}
