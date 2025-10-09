package com.nfsp00f33r.app.security.roca

import com.nfsp00f33r.app.storage.models.CardProfile
import timber.log.Timber
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * RocaBatchScanner - Feature Enhancement Phase
 * 
 * Batch vulnerability scanner for multiple cards
 * Enables efficient scanning of entire card collections for ROCA vulnerabilities
 * 
 * Features:
 * - Parallel card scanning
 * - Progress tracking
 * - Detailed reporting per card
 * - Summary statistics
 * - Priority classification
 * - Export capabilities
 */
class RocaBatchScanner(
    private val detector: RocaDetector = RocaDetector(enableDetailedAnalysis = true)
) {
    
    companion object {
        private const val TAG = "🔍 BatchScanner"
    }
    
    /**
     * Scan result for a single card
     */
    data class CardScanResult(
        val cardProfile: CardProfile,
        val hasRsaKey: Boolean,
        val rocaResult: RocaDetector.RocaTestResult?,
        val scanTimeMs: Long,
        val scanTimestamp: Long,
        val priority: VulnerabilityPriority
    )
    
    /**
     * Vulnerability priority classification
     */
    enum class VulnerabilityPriority {
        CRITICAL,   // 512-bit vulnerable keys (can be cracked in hours)
        HIGH,       // 1024-bit vulnerable keys (can be cracked in days)
        MEDIUM,     // 2048-bit vulnerable keys (theoretically vulnerable)
        LOW,        // Not vulnerable or no RSA key
        NONE        // No RSA data available
    }
    
    /**
     * Batch scan report
     */
    data class BatchScanReport(
        val totalCards: Int,
        val cardsWithRsaKeys: Int,
        val vulnerableCards: Int,
        val safeCards: Int,
        val criticalVulnerabilities: Int,
        val highPriorityVulnerabilities: Int,
        val mediumPriorityVulnerabilities: Int,
        val totalScanTimeMs: Long,
        val scanTimestamp: Long,
        val cardResults: List<CardScanResult>,
        val recommendations: List<String>
    )
    
    /**
     * Progress callback for batch scanning
     */
    interface ScanProgressCallback {
        fun onProgress(current: Int, total: Int, currentCard: String)
        fun onCardScanned(result: CardScanResult)
        fun onComplete(report: BatchScanReport)
        fun onError(error: Exception)
    }
    
    /**
     * Scan multiple cards for ROCA vulnerabilities
     */
    suspend fun scanCards(
        cards: List<CardProfile>,
        progressCallback: ScanProgressCallback? = null
    ): BatchScanReport = withContext(Dispatchers.Default) {
        Timber.i("$TAG Starting batch scan of ${cards.size} cards")
        val startTime = System.currentTimeMillis()
        
        try {
            val results = cards.mapIndexed { index, card ->
                progressCallback?.onProgress(index + 1, cards.size, card.staticData.pan)
                
                val cardResult = scanCard(card)
                progressCallback?.onCardScanned(cardResult)
                
                cardResult
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            val report = generateReport(results, totalTime)
            
            Timber.i("$TAG Batch scan complete: ${report.vulnerableCards} vulnerable out of ${report.totalCards}")
            progressCallback?.onComplete(report)
            
            report
            
        } catch (e: Exception) {
            Timber.e("$TAG Batch scan failed", e)
            progressCallback?.onError(e)
            throw e
        }
    }
    
    /**
     * Scan cards in parallel (faster but more resource intensive)
     */
    suspend fun scanCardsParallel(
        cards: List<CardProfile>,
        progressCallback: ScanProgressCallback? = null,
        maxConcurrency: Int = 4
    ): BatchScanReport = withContext(Dispatchers.Default) {
        Timber.i("$TAG Starting parallel batch scan of ${cards.size} cards (max $maxConcurrency concurrent)")
        val startTime = System.currentTimeMillis()
        
        try {
            // Split into batches for controlled concurrency
            val batches = cards.chunked(maxConcurrency)
            var completedCount = 0
            
            val results = batches.flatMap { batch ->
                coroutineScope {
                    batch.map { card ->
                        async {
                            val result = scanCard(card)
                            
                            // Thread-safe progress update
                            synchronized(this@RocaBatchScanner) {
                                completedCount++
                                progressCallback?.onProgress(completedCount, cards.size, card.staticData.pan)
                                progressCallback?.onCardScanned(result)
                            }
                            
                            result
                        }
                    }.awaitAll()
                }
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            val report = generateReport(results, totalTime)
            
            Timber.i("$TAG Parallel batch scan complete in ${totalTime}ms: ${report.vulnerableCards}/${report.totalCards} vulnerable")
            progressCallback?.onComplete(report)
            
            report
            
        } catch (e: Exception) {
            Timber.e("$TAG Parallel batch scan failed", e)
            progressCallback?.onError(e)
            throw e
        }
    }
    
    /**
     * Scan a single card
     */
    private fun scanCard(card: CardProfile): CardScanResult {
        val startTime = System.currentTimeMillis()
        
        // Extract RSA key from card (if available)
        val rsaKey = extractRsaKey(card)
        
        val result = if (rsaKey != null) {
            detector.isVulnerable(rsaKey)
        } else {
            null
        }
        
        val priority = classifyPriority(result)
        val scanTime = System.currentTimeMillis() - startTime
        
        return CardScanResult(
            cardProfile = card,
            hasRsaKey = rsaKey != null,
            rocaResult = result,
            scanTimeMs = scanTime,
            scanTimestamp = System.currentTimeMillis(),
            priority = priority
        )
    }
    
    /**
     * Extract RSA public key from card profile
     */
    private fun extractRsaKey(card: CardProfile): BigInteger? {
        val cryptoData = card.cryptographicData
        
        // Try ICC public key certificate
        val iccCert = cryptoData.iccPublicKeyCertificate
        if (iccCert != null) {
            try {
                return BigInteger(iccCert, 16)
            } catch (e: Exception) {
                Timber.w("$TAG Failed to parse ICC certificate: ${e.message}")
            }
        }
        
        // Try issuer public key certificate
        val issuerCert = cryptoData.issuerPublicKeyCertificate
        if (issuerCert != null) {
            try {
                return BigInteger(issuerCert, 16)
            } catch (e: Exception) {
                Timber.w("$TAG Failed to parse issuer certificate: ${e.message}")
            }
        }
        
        return null
    }
    
    /**
     * Classify vulnerability priority
     */
    private fun classifyPriority(result: RocaDetector.RocaTestResult?): VulnerabilityPriority {
        if (result == null) return VulnerabilityPriority.NONE
        if (!result.isVulnerable) return VulnerabilityPriority.LOW
        
        return when (result.keyLength) {
            in 488..520 -> VulnerabilityPriority.CRITICAL  // 512-bit: hours to crack
            in 984..1040 -> VulnerabilityPriority.HIGH     // 1024-bit: days to crack
            in 1952..2080 -> VulnerabilityPriority.MEDIUM  // 2048-bit: years to crack
            else -> VulnerabilityPriority.LOW
        }
    }
    
    /**
     * Generate comprehensive batch scan report
     */
    private fun generateReport(results: List<CardScanResult>, totalScanTime: Long): BatchScanReport {
        val totalCards = results.size
        val cardsWithKeys = results.count { it.hasRsaKey }
        val vulnerableCards = results.count { it.rocaResult?.isVulnerable == true }
        val safeCards = cardsWithKeys - vulnerableCards
        
        val critical = results.count { it.priority == VulnerabilityPriority.CRITICAL }
        val high = results.count { it.priority == VulnerabilityPriority.HIGH }
        val medium = results.count { it.priority == VulnerabilityPriority.MEDIUM }
        
        val recommendations = buildRecommendations(critical, high, medium, vulnerableCards, totalCards)
        
        return BatchScanReport(
            totalCards = totalCards,
            cardsWithRsaKeys = cardsWithKeys,
            vulnerableCards = vulnerableCards,
            safeCards = safeCards,
            criticalVulnerabilities = critical,
            highPriorityVulnerabilities = high,
            mediumPriorityVulnerabilities = medium,
            totalScanTimeMs = totalScanTime,
            scanTimestamp = System.currentTimeMillis(),
            cardResults = results.sortedByDescending { it.priority },
            recommendations = recommendations
        )
    }
    
    /**
     * Build actionable recommendations
     */
    private fun buildRecommendations(
        critical: Int,
        high: Int,
        medium: Int,
        vulnerable: Int,
        total: Int
    ): List<String> {
        val recs = mutableListOf<String>()
        
        if (critical > 0) {
            recs.add("🔴 CRITICAL: $critical cards have 512-bit vulnerable keys that can be cracked in hours. Immediate key replacement required.")
        }
        
        if (high > 0) {
            recs.add("🟠 HIGH: $high cards have 1024-bit vulnerable keys that can be cracked in days. Key replacement strongly recommended.")
        }
        
        if (medium > 0) {
            recs.add("🟡 MEDIUM: $medium cards have 2048-bit vulnerable keys. Monitor for exploitation attempts and plan key replacement.")
        }
        
        if (vulnerable > 0) {
            val percentage = (vulnerable.toDouble() / total.toDouble()) * 100
            recs.add("📊 Overall: ${String.format("%.1f", percentage)}% of scanned cards are vulnerable to ROCA attack.")
        }
        
        if (vulnerable == 0 && total > 0) {
            recs.add("✅ GOOD: No ROCA vulnerabilities detected in scanned cards.")
        }
        
        return recs
    }
    
    /**
     * Export report to JSON-compatible map
     */
    fun exportReport(report: BatchScanReport): Map<String, Any> {
        return mapOf(
            "summary" to mapOf(
                "total_cards" to report.totalCards,
                "cards_with_rsa_keys" to report.cardsWithRsaKeys,
                "vulnerable_cards" to report.vulnerableCards,
                "safe_cards" to report.safeCards,
                "critical" to report.criticalVulnerabilities,
                "high" to report.highPriorityVulnerabilities,
                "medium" to report.mediumPriorityVulnerabilities,
                "scan_time_ms" to report.totalScanTimeMs,
                "scan_timestamp" to report.scanTimestamp
            ),
            "recommendations" to report.recommendations,
            "card_results" to report.cardResults.map { result ->
                mapOf(
                    "pan" to result.cardProfile.staticData.pan,
                    "has_rsa_key" to result.hasRsaKey,
                    "is_vulnerable" to (result.rocaResult?.isVulnerable ?: false),
                    "priority" to result.priority.name,
                    "key_length" to (result.rocaResult?.keyLength ?: 0),
                    "confidence" to (result.rocaResult?.confidence ?: 0.0),
                    "scan_time_ms" to result.scanTimeMs
                )
            }
        )
    }
}
