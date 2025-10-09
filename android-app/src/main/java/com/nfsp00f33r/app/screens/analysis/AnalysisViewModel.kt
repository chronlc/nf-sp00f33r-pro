package com.nfsp00f33r.app.screens.analysis

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfsp00f33r.app.application.NfSp00fApplication
import com.nfsp00f33r.app.models.CardProfile
import com.nfsp00f33r.app.emulation.AttackAnalytics
import com.nfsp00f33r.app.security.roca.RocaBatchScanner
import com.nfsp00f33r.app.security.roca.RocaDetector
import com.nfsp00f33r.app.storage.models.CardProfile as StorageCardProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigInteger

/**
 * AnalysisViewModel - Phase 2B Quick Wins Integration
 * Exposes analytics and ROCA scanning features to UI
 * 
 * Features:
 * - Attack analytics reporting
 * - Success rate trends
 * - Attack type statistics
 * - Card attack history
 * - ROCA batch vulnerability scanning
 */
class AnalysisViewModel : ViewModel() {

    // Module access
    private val emulationModule by lazy { NfSp00fApplication.getEmulationModule() }
    private val cardDataStore by lazy { NfSp00fApplication.getCardDataStoreModule() }
    private val rocaScanner = RocaBatchScanner()

    // UI State - Analytics
    var analyticsReport by mutableStateOf<AttackAnalytics.AnalyticsReport?>(null)
        private set

    var attackTypeStats by mutableStateOf<Map<String, AttackAnalytics.AttackTypeStatistics>>(emptyMap())
        private set

    var recentExecutions by mutableStateOf<List<AttackAnalytics.AttackExecution>>(emptyList())
        private set

    // UI State - ROCA Scanning
    var rocaScanResult by mutableStateOf<RocaBatchScanner.BatchScanReport?>(null)
        private set

    var isScanning by mutableStateOf(false)
        private set

    var scanProgress by mutableStateOf<String>("")
        private set

    // UI State - Cards
    var availableCards by mutableStateOf<List<StorageCardProfile>>(emptyList())
        private set

    var selectedCardHistory by mutableStateOf<List<AttackAnalytics.AttackExecution>>(emptyList())
        private set

    // UI State - General
    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadInitialData()
        Timber.d("AnalysisViewModel initialized")
    }

    /**
     * Load initial data
     */
    private fun loadInitialData() {
        loadAnalyticsReport()
        loadAvailableCards()
    }

    /**
     * Load comprehensive analytics report
     */
    fun loadAnalyticsReport() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    isLoading = true
                    errorMessage = null
                }

                Timber.d("Loading analytics report")

                // Get comprehensive analytics report
                val report = emulationModule.getAnalyticsReport()

                // Load recent executions
                val recent = emulationModule.getRecentExecutions(20)

                withContext(Dispatchers.Main) {
                    analyticsReport = report
                    recentExecutions = recent
                    isLoading = false
                    Timber.d("Analytics report loaded: ${report.totalAttacks} total executions")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load analytics report")
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to load analytics: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    /**
     * Load statistics for specific attack type
     */
    fun loadAttackTypeStatistics(attackType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.d("Loading statistics for attack type: $attackType")

                val stats = emulationModule.getAttackTypeStatistics(attackType)

                withContext(Dispatchers.Main) {
                    if (stats != null) {
                        attackTypeStats = attackTypeStats + (attackType to stats)
                        Timber.d("Statistics loaded for $attackType: ${stats.totalExecutions} executions")
                    } else {
                        Timber.w("No statistics available for attack type: $attackType")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load attack type statistics")
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to load statistics: ${e.message}"
                }
            }
        }
    }

    /**
     * Get success rate trend for attack type
     */
    fun getSuccessRateTrend(attackType: String, buckets: Int = 10): List<Double> {
        return try {
            emulationModule.getSuccessRateTrend(attackType, buckets)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get success rate trend")
            emptyList()
        }
    }

    /**
     * Load attack history for specific card
     */
    fun loadCardAttackHistory(pan: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    isLoading = true
                    errorMessage = null
                }

                Timber.d("Loading attack history for card: ${pan.take(6)}******")

                val history = emulationModule.getCardAttackHistory(pan)

                withContext(Dispatchers.Main) {
                    selectedCardHistory = history
                    isLoading = false
                    Timber.d("Card history loaded: ${history.size} attacks")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load card attack history")
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to load history: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    /**
     * Load available cards from storage
     */
    private fun loadAvailableCards() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profiles = cardDataStore.getAllProfiles()

                withContext(Dispatchers.Main) {
                    availableCards = profiles
                    Timber.d("Loaded ${profiles.size} cards for analysis")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load cards")
            }
        }
    }

    /**
     * Scan all cards for ROCA vulnerability
     */
    fun scanAllCardsForRoca() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    isScanning = true
                    rocaScanResult = null
                    scanProgress = "Preparing scan..."
                    errorMessage = null
                }

                Timber.d("Starting ROCA batch scan for ${availableCards.size} cards")

                // Filter cards that have RSA certificates
                val cardsWithCerts = availableCards.filter { card ->
                    val cert = card.cryptographicData.iccPublicKeyCertificate
                    cert != null && cert.isNotEmpty()
                }

                if (cardsWithCerts.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "No cards with RSA certificates found"
                        isScanning = false
                        scanProgress = ""
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    scanProgress = "Scanning ${cardsWithCerts.size} cards..."
                }

                Timber.d("Scanning ${cardsWithCerts.size} cards with certificates for ROCA vulnerability")

                // Perform batch scan
                val result = rocaScanner.scanCards(cardsWithCerts)

                withContext(Dispatchers.Main) {
                    rocaScanResult = result
                    isScanning = false
                    scanProgress = ""
                    Timber.d("ROCA scan complete: ${result.vulnerableCards} vulnerable cards found out of ${result.totalCards}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to scan cards for ROCA vulnerability")
                withContext(Dispatchers.Main) {
                    errorMessage = "ROCA scan failed: ${e.message}"
                    isScanning = false
                    scanProgress = ""
                }
            }
        }
    }

    /**
     * Export analytics data to Map
     */
    fun exportAnalyticsData(): Map<String, Any>? {
        return try {
            val data = emulationModule.exportAnalyticsData()
            Timber.d("Analytics data exported: ${data.size} entries")
            data
        } catch (e: Exception) {
            Timber.e(e, "Failed to export analytics data")
            errorMessage = "Export failed: ${e.message}"
            null
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        errorMessage = null
    }

    /**
     * Refresh all data
     */
    fun refresh() {
        loadAnalyticsReport()
        loadAvailableCards()
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("AnalysisViewModel cleared")
    }
}
