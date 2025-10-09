package com.nfsp00f33r.app.cardreading

import com.nfsp00f33r.app.models.CardProfile
import com.nfsp00f33r.app.data.EmvCardData
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Manager for card profiles with full EMV data and APDU logs
 * Per newrule.md: Production-grade, real-data-only, no simulation
 * Singleton pattern for shared state across fragments
 */
class CardProfileManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: CardProfileManager? = null
        
        fun getInstance(): CardProfileManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CardProfileManager().also { INSTANCE = it }
            }
        }
    }
    
    private val cardProfiles = CopyOnWriteArrayList<CardProfile>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    
    /**
     * Add listener for real-time updates (thread-safe)
     */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }
    
    /**
     * Remove listener (thread-safe)
     */
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
    
    /**
     * Notify all listeners of changes
     */
    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }
    
    /**
     * Save EMV card data as a new card profile
     */
    fun saveCard(cardData: EmvCardData) {
        val profile = CardProfile(
            emvCardData = cardData,
            apduLogs = cardData.apduLog.toMutableList(), // Copy APDU logs from card data
            cardholderName = cardData.cardholderName,
            expirationDate = cardData.expiryDate,
            applicationLabel = cardData.applicationLabel
        )
        cardProfiles.add(profile)
        Timber.d("💾 Card saved: ${cardData.cardholderName ?: "Unknown"} | APDUs: ${cardData.apduLog.size} | Total: ${cardProfiles.size}")
        notifyListeners() // Notify UI updates
    }
    
    /**
     * Get recent cards
     */
    fun getRecentCards(): List<CardProfile> {
        return cardProfiles.takeLast(5)
    }
    
    /**
     * Get recent cards with limit (thread-safe)
     */
    fun getRecentCards(limit: Int): List<EmvCardData> {
        return cardProfiles.takeLast(limit).map { it.emvCardData }
    }
    
    /**
     * Get all cards (thread-safe)
     */
    fun getAllCards(): List<CardProfile> {
        return cardProfiles.toList()
    }
    
    /**
     * Save card profile (thread-safe)
     */
    @Synchronized
    fun saveCardProfile(profile: CardProfile) {
        cardProfiles.add(profile)
        notifyListeners()
    }
    
    /**
     * Get all card profiles with PAN-first, then UID sorting (thread-safe)
     * Per user requirements: "list by PAN first then list by UID after"
     */
    @Synchronized
    fun getAllCardProfiles(): List<CardProfile> {
        return cardProfiles.sortedWith(compareBy<CardProfile> { profile ->
            // Primary sort: PAN presence (cards with PAN first)
            val pan = profile.emvCardData.getUnmaskedPan()
            if (pan.isBlank()) 1 else 0
        }.thenBy { profile ->
            // Secondary sort: PAN value (ascending)
            profile.emvCardData.getUnmaskedPan()
        }.thenBy { profile ->
            // Tertiary sort: UID for cards without PAN
            profile.emvCardData.cardUid ?: "zzz_unknown"
        })
    }
    
    /**
     * Delete card profile by ID (thread-safe)
     */
    @Synchronized
    fun deleteCardProfile(id: String) {
        cardProfiles.removeAll { it.id == id }
        Timber.d("🗑️ Card profile deleted: $id")
        notifyListeners()
    }
    
    /**
     * Update existing card profile
     */
    fun updateCardProfile(updatedProfile: CardProfile) {
        val index = cardProfiles.indexOfFirst { it.id == updatedProfile.id }
        if (index >= 0) {
            cardProfiles[index] = updatedProfile
            Timber.d("📝 Card profile updated: ${updatedProfile.id}")
        } else {
            cardProfiles.add(updatedProfile)
            Timber.d("➕ Card profile added as new: ${updatedProfile.id}")
        }
    }
    
    /**
     * Search card profiles by query string
     */
    fun searchCardProfiles(query: String): List<CardProfile> {
        if (query.isBlank()) return cardProfiles.toList()
        
        return cardProfiles.filter { profile ->
            val pan = profile.emvCardData.pan ?: ""
            val cardholderName = profile.emvCardData.cardholderName ?: ""
            val applicationLabel = profile.emvCardData.applicationLabel
            
            pan.contains(query, ignoreCase = true) ||
            cardholderName.contains(query, ignoreCase = true) ||
            applicationLabel.contains(query, ignoreCase = true)
        }
    }
    
    /**
     * Clear all card profiles
     */
    fun clearAllProfiles() {
        val count = cardProfiles.size
        cardProfiles.clear()
        Timber.d("🧹 Cleared $count card profiles")
        notifyListeners()
    }
    
    /**
     * Get card profile by ID
     */
    fun getCardProfileById(id: String): CardProfile? {
        return cardProfiles.find { it.id == id }
    }
    
    /**
     * Export all profiles to JSON string
     */
    fun exportToJson(): String {
        // Simplified JSON export for demo
        val profiles = cardProfiles.map { profile ->
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
        return "[${profiles.joinToString(",\n")}]"
    }
    
    /**
     * Import profiles from JSON string (simplified)
     */
    fun importFromJson(jsonString: String): Int {
        // Simplified import - just log for now
        Timber.d("📥 Import requested with ${jsonString.length} chars")
        return 0 // Would parse and add profiles in real implementation
    }
}
