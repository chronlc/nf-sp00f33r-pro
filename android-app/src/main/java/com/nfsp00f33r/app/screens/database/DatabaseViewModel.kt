package com.nfsp00f33r.app.screens.database

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfsp00f33r.app.application.NfSp00fApplication
import com.nfsp00f33r.app.storage.CardDataStore
import com.nfsp00f33r.app.storage.CardProfileAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.nfsp00f33r.app.models.CardProfile
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * Database Screen ViewModel - Phase 1A: Now uses CardDataStore with encrypted persistence
 * Migrated from in-memory CardProfileManager to encrypted CardDataStore
 * All data now persists across app restarts with AES-256-GCM encryption
 */
class DatabaseViewModel : ViewModel() {
    
    // Card data storage (Phase 2A: Using CardDataStoreModule)
    private val cardDataStore = NfSp00fApplication.getCardDataStoreModule()
    
    // ROCA vulnerability scanner - Phase 2B Quick Wins
    private val rocaScanner = com.nfsp00f33r.app.security.roca.RocaBatchScanner()
    
    // UI State
    var cardProfiles by mutableStateOf(listOf<CardProfile>())
        private set
    
    var filteredCards by mutableStateOf(listOf<CardProfile>())
        private set
    
    var searchQuery by mutableStateOf("")
        private set
    
    // Statistics
    var totalCards by mutableStateOf(0)
        private set
    
    var encryptedCards by mutableStateOf(0)
        private set
    
    var uniqueCategories by mutableStateOf(0)
        private set
    
    // ROCA scanning state - Phase 2B Quick Wins
    var rocaScanResult by mutableStateOf<com.nfsp00f33r.app.security.roca.RocaBatchScanner.BatchScanReport?>(null)
        private set
    
    var isRocaScanning by mutableStateOf(false)
        private set
    
    var rocaScanProgress by mutableStateOf<String>("")
        private set
    
    init {
        // Initial data load from encrypted storage
        refreshData()
        Timber.d("DatabaseViewModel initialized - Cards: $totalCards")
    }
    
    /**
     * Refresh data from CardDataStore (encrypted persistent storage)
     */
    private fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get all profiles from encrypted storage
                val storageProfiles = cardDataStore.getAllProfiles()
                
                // Convert to app profiles using adapter
                val appProfiles = storageProfiles.map { CardProfileAdapter.toAppProfile(it) }
                
                // Update UI state on main thread
                withContext(Dispatchers.Main) {
                    cardProfiles = appProfiles
                    updateStatistics()
                    filterCards(searchQuery)
                    Timber.d("Database refreshed from encrypted storage - Total cards: ${cardProfiles.size}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh data from encrypted storage")
            }
        }
    }
    
    /**
     * Update search query and filter cards
     */
    fun updateSearchQuery(query: String) {
        searchQuery = query
        filterCards(query)
    }
    
    /**
     * Filter cards based on search query
     */
    private fun filterCards(query: String) {
        filteredCards = if (query.isBlank()) {
            cardProfiles
        } else {
            cardProfiles.filter { profile ->
                val emvData = profile.emvCardData
                val pan = emvData.getUnmaskedPan()
                val cardholderName = emvData.cardholderName ?: ""
                val applicationLabel = emvData.applicationLabel
                
                pan.contains(query, ignoreCase = true) ||
                cardholderName.contains(query, ignoreCase = true) ||
                applicationLabel.contains(query, ignoreCase = true) ||
                profile.aid?.contains(query, ignoreCase = true) == true
            }
        }
    }
    
    /**
     * Update statistics based on current data
     */
    private fun updateStatistics() {
        totalCards = cardProfiles.size
        
        // Count cards with encryption indicators (Track2 encrypted data, etc)
        encryptedCards = cardProfiles.count { profile ->
            val emvData = profile.emvCardData
            emvData.track2Data?.isNotEmpty() == true || 
            emvData.applicationCryptogram?.isNotEmpty() == true
        }
        
        // Count unique application categories
        uniqueCategories = cardProfiles.mapNotNull { it.emvCardData.applicationLabel }
            .distinct()
            .size
    }
    
    /**
     * Delete card profile by ID from encrypted storage
     */
    fun deleteCard(cardId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = cardDataStore.deleteProfile(cardId)
                if (success) {
                    Timber.d("Card deleted from encrypted storage: $cardId")
                    // Refresh data from storage
                    refreshData()
                } else {
                    Timber.w("Failed to delete card: $cardId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting card: $cardId")
            }
        }
    }
    
    /**
     * Export all card data to JSON from encrypted storage
     */
    fun exportToJson(): String {
        return try {
            val storageProfiles = cardDataStore.getAllProfiles()
            val appProfiles = storageProfiles.map { CardProfileAdapter.toAppProfile(it) }
            
            // Create JSON export
            val profiles = appProfiles.map { profile ->
                """
                {
                    "id": "${profile.id}",
                    "pan": "${profile.emvCardData.pan ?: ""}",
                    "cardholderName": "${profile.emvCardData.cardholderName ?: ""}",
                    "track2": "${profile.emvCardData.track2Data ?: ""}",
                    "apduLogs": ${profile.apduLogs.size},
                    "createdAt": "${profile.createdTimestamp}"
                }
                """.trimIndent()
            }
            "[${profiles.joinToString(",\n")}]"
        } catch (e: Exception) {
            Timber.e(e, "Failed to export to JSON")
            "[]"
        }
    }
    
    /**
     * Clear all card data from encrypted storage
     */
    fun clearAllCards() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allProfiles = cardDataStore.getAllProfiles()
                allProfiles.forEach { profile ->
                    cardDataStore.deleteProfile(profile.profileId)
                }
                Timber.d("All cards cleared from encrypted storage")
                // Refresh data from storage
                refreshData()
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear all cards")
            }
        }
    }
    
    /**
     * Get formatted relative time for card
     */
    fun getRelativeTime(profile: CardProfile): String {
        return try {
            val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val timestamp = profile.createdAt
            val now = System.currentTimeMillis()
            val diff = now - profile.createdAt.time
            
            when {
                diff < 60L * 1000L -> "Just now"
                diff < 60L * 60L * 1000L -> "${diff / (60L * 1000L)} min ago"
                diff < 24L * 60L * 60L * 1000L -> "${diff / (60L * 60L * 1000L)} hours ago"
                diff < 7L * 24L * 60L * 60L * 1000L -> "${diff / (24L * 60L * 60L * 1000L)} days ago"
                else -> formatter.format(timestamp)
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Get card type from PAN (VISA, Mastercard, etc)
     */
    fun getCardType(pan: String): String {
        return when {
            pan.startsWith("4") -> "VISA"
            pan.startsWith("5") || pan.startsWith("2") -> "MASTERCARD"
            pan.startsWith("3") -> "AMEX"
            pan.startsWith("6") -> "DISCOVER"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Export card data to JSON file
     */
    fun exportCardData(callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                // Get profiles from encrypted storage
                val storageProfiles = withContext(Dispatchers.IO) {
                    cardDataStore.getAllProfiles()
                }
                val profiles = storageProfiles.map { CardProfileAdapter.toAppProfile(it) }
                val jsonArray = JSONArray()
                
                profiles.forEach { profile ->
                    val jsonProfile = JSONObject().apply {
                        put("id", profile.emvCardData.id)
                        put("cardUid", profile.emvCardData.cardUid ?: "")
                        put("pan", profile.emvCardData.pan ?: "")
                        put("cardholderName", profile.emvCardData.cardholderName ?: "")
                        put("expiryDate", profile.emvCardData.expiryDate ?: "")
                        put("aid", profile.emvCardData.applicationIdentifier ?: "")
                        put("createdAt", profile.createdAt.time)
                        put("apduCount", profile.apduLogs.size)
                    }
                    jsonArray.put(jsonProfile)
                }
                
                withContext(Dispatchers.IO) {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val filename = "nf_sp00f33r_export_$timestamp.json"
                    val downloadsDir = File("/storage/emulated/0/Download")
                    val file = File(downloadsDir, filename)
                    
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    
                    file.writeText(jsonArray.toString(2))
                    callback(true, "Exported ${profiles.size} cards to $filename")
                }
                
            } catch (e: Exception) {
                callback(false, "Export failed: ${e.message}")
            }
        }
    }
    
    /**
     * Import card data from JSON file
     */
    fun importCardData(callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val downloadsDir = File("/storage/emulated/0/Download")
                    val files = downloadsDir.listFiles { file ->
                        file.name.startsWith("nf_sp00f33r_export_") && file.name.endsWith(".json")
                    }
                    
                    if (files.isNullOrEmpty()) {
                        callback(false, "No export files found in Downloads folder")
                        return@withContext
                    }
                    
                    val latestFile = files.maxByOrNull { it.lastModified() }
                    if (latestFile == null) {
                        callback(false, "No valid export file found")
                        return@withContext
                    }
                    
                    val jsonContent = latestFile.readText()
                    val jsonArray = JSONArray(jsonContent)
                    var importedCount = 0
                    
                    for (i in 0 until jsonArray.length()) {
                        val jsonProfile = jsonArray.getJSONObject(i)
                        
                        val emvData = com.nfsp00f33r.app.data.EmvCardData(
                            id = jsonProfile.optString("id"),
                            cardUid = jsonProfile.optString("cardUid"),
                            pan = jsonProfile.optString("pan"),
                            cardholderName = jsonProfile.optString("cardholderName"),
                            expiryDate = jsonProfile.optString("expiryDate"),
                            applicationIdentifier = jsonProfile.optString("aid")
                        )
                        
                        val cardProfile = com.nfsp00f33r.app.models.CardProfile(
                            emvCardData = emvData,
                            apduLogs = mutableListOf(),
                            cardholderName = emvData.cardholderName,
                            aid = emvData.applicationIdentifier,
                            createdAt = Date(jsonProfile.optLong("createdAt"))
                        )
                        
                        // Convert to storage profile and save with encryption
                        val storageProfile = CardProfileAdapter.toStorageProfile(cardProfile)
                        cardDataStore.storeProfile(storageProfile)
                        importedCount++
                    }
                    
                    callback(true, "Imported $importedCount cards from ${latestFile.name}")
                }
                
            } catch (e: Exception) {
                callback(false, "Import failed: ${e.message}")
            }
        }
    }
    
    /**
     * Scan all cards for ROCA vulnerability - Phase 2B Quick Wins
     */
    fun scanAllCardsForRoca() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    isRocaScanning = true
                    rocaScanResult = null
                    rocaScanProgress = "Preparing scan..."
                }
                
                Timber.d("Starting ROCA batch scan for ${cardProfiles.size} cards")
                
                // Convert app profiles to storage profiles for scanning
                val storageProfiles = cardProfiles.map { CardProfileAdapter.toStorageProfile(it) }
                
                // Filter cards that have RSA certificates
                val cardsWithCerts = storageProfiles.filter { card ->
                    val cert = card.cryptographicData.iccPublicKeyCertificate
                    cert != null && cert.isNotEmpty()
                }
                
                if (cardsWithCerts.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        rocaScanProgress = "No cards with RSA certificates found"
                        isRocaScanning = false
                    }
                    Timber.w("No valid RSA certificates found for ROCA scanning")
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    rocaScanProgress = "Scanning ${cardsWithCerts.size} cards..."
                }
                
                Timber.d("Scanning ${cardsWithCerts.size} cards with certificates for ROCA vulnerability")
                
                // Perform batch scan
                val result = rocaScanner.scanCards(cardsWithCerts)
                
                withContext(Dispatchers.Main) {
                    rocaScanResult = result
                    isRocaScanning = false
                    rocaScanProgress = ""
                    Timber.d("ROCA scan complete: ${result.vulnerableCards} vulnerable cards found out of ${result.totalCards}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to scan cards for ROCA vulnerability")
                withContext(Dispatchers.Main) {
                    rocaScanProgress = "Scan failed: ${e.message}"
                    isRocaScanning = false
                }
            }
        }
    }
    
    /**
     * Get ROCA scan summary
     */
    fun getRocaScanSummary(): String {
        val result = rocaScanResult ?: return "No scan results available"
        return "Scanned: ${result.totalCards} | Vulnerable: ${result.vulnerableCards} | Safe: ${result.safeCards}"
    }
    
    override fun onCleared() {
        super.onCleared()
        // No cleanup needed for CardDataStore (managed by Application)
        Timber.d("DatabaseViewModel cleared")
    }
}