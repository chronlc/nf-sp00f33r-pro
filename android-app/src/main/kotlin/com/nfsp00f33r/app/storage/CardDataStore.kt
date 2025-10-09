package com.nfsp00f33r.app.storage

import android.content.Context
import android.util.Log
import com.nfsp00f33r.app.storage.models.*
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

/**
 * CardDataStore - Phase 1A Implementation
 * 
 * Provides secure storage and management of captured EMV card data profiles.
 * Supports encryption, versioning, import/export, and comparison operations.
 * 
 * Features:
 * - Secure encrypted storage of card profiles
 * - Version control for card data
 * - Import/export functionality
 * - Card profile comparison
 * - Search and filtering capabilities
 * - Transaction history tracking
 * 
 * Security:
 * - AES-256-GCM encryption for stored data
 * - Key derivation from master password
 * - Secure key storage
 * - Data integrity verification
 * 
 * Note: This is a standalone version for Phase 1A. Will be converted to BaseModule in Phase 2A.
 * 
 * @property context Android application context
 * @property encryptionEnabled Enable/disable encryption (default: true)
 * @property masterPassword Master password for encryption (required if encryption enabled)
 */
class CardDataStore(
    private val context: Context,
    private val encryptionEnabled: Boolean = true,
    private val masterPassword: String? = null
) {

    private val TAG = "CardDataStore"
    
    companion object {
        const val VERSION = "1.0.0"
    }

    private val profiles = ConcurrentHashMap<String, CardProfile>()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private var encryptionKey: SecretKey? = null
    private val storageDir: File by lazy {
        File(context.filesDir, "card_profiles").apply {
            if (!exists()) {
                mkdirs()
                Log.i(TAG, "Created storage directory: $absolutePath")
            }
        }
    }

    /**
     * Initialize the CardDataStore
     * Call this after construction to set up encryption and load existing profiles
     */
    fun initialize() {
        Log.i(TAG, "Initializing CardDataStore...")
        
        if (encryptionEnabled) {
            if (masterPassword.isNullOrEmpty()) {
                throw IllegalStateException("Master password required when encryption is enabled")
            }
            encryptionKey = deriveKeyFromPassword(masterPassword)
            Log.i(TAG, "Encryption enabled with AES-256-GCM")
        } else {
            Log.w(TAG, "Encryption disabled - data will be stored in plaintext!")
        }
        
        loadExistingProfiles()
        Log.i(TAG, "Loaded ${profiles.size} existing profiles")
    }

    /**
     * Shutdown the CardDataStore and save all profiles
     */
    fun shutdown() {
        saveAllProfiles()
        profiles.clear()
        Log.i(TAG, "CardDataStore shutdown complete")
    }

    /**
     * Store a new card profile
     */
    fun storeProfile(profile: CardProfile): Result<String> {
        return try {
            val profileId = profile.profileId
            
            // Check if profile already exists
            if (profiles.containsKey(profileId)) {
                // Create new version
                val existingProfile = profiles[profileId]!!
                val newVersion = existingProfile.version + 1
                val updatedProfile = profile.copy(
                    version = newVersion,
                    lastModified = Instant.now()
                )
                profiles[profileId] = updatedProfile
                saveProfile(updatedProfile)
                Log.i(TAG, "Updated profile $profileId to version $newVersion")
            } else {
                // New profile
                profiles[profileId] = profile
                saveProfile(profile)
                Log.i(TAG, "Stored new profile: $profileId")
            }
            
            Result.success(profileId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieve a card profile by ID
     */
    fun getProfile(profileId: String): CardProfile? {
        return profiles[profileId]
    }

    /**
     * Get all stored profiles
     */
    fun getAllProfiles(): List<CardProfile> {
        return profiles.values.toList()
    }

    /**
     * Search profiles by criteria
     */
    fun searchProfiles(
        cardNumber: String? = null,
        cardType: CardType? = null,
        issuer: String? = null,
        tags: Set<String>? = null
    ): List<CardProfile> {
        return profiles.values.filter { profile ->
            (cardNumber == null || profile.staticData.pan.contains(cardNumber)) &&
            (cardType == null || profile.cardType == cardType) &&
            (issuer == null || profile.issuer.contains(issuer, ignoreCase = true)) &&
            (tags == null || profile.tags.any { it in tags })
        }
    }

    /**
     * Delete a profile
     */
    fun deleteProfile(profileId: String): Boolean {
        return try {
            profiles.remove(profileId)
            val file = File(storageDir, "$profileId.json")
            if (file.exists()) {
                file.delete()
            }
            Log.i(TAG, "Deleted profile: $profileId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete profile: ${e.message}", e)
            false
        }
    }

    /**
     * Export profile to JSON file
     */
    fun exportProfile(profileId: String, outputPath: String): Result<Unit> {
        return try {
            val profile = profiles[profileId]
                ?: return Result.failure(IllegalArgumentException("Profile not found: $profileId"))
            
            val jsonData = json.encodeToString(CardProfile.serializer(), profile)
            File(outputPath).writeText(jsonData)
            
            Log.i(TAG, "Exported profile to: $outputPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Import profile from JSON file
     */
    fun importProfile(inputPath: String): Result<String> {
        return try {
            val jsonData = File(inputPath).readText()
            val profile = json.decodeFromString(CardProfile.serializer(), jsonData)
            
            storeProfile(profile)
            Log.i(TAG, "Imported profile from: $inputPath")
            Result.success(profile.profileId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Compare two card profiles
     */
    fun compareProfiles(profileId1: String, profileId2: String): ProfileComparison? {
        val profile1 = profiles[profileId1] ?: return null
        val profile2 = profiles[profileId2] ?: return null
        
        return ProfileComparison(
            profile1 = profile1,
            profile2 = profile2,
            staticDataDifferences = compareStaticData(profile1.staticData, profile2.staticData),
            dynamicDataDifferences = compareDynamicData(profile1.dynamicData, profile2.dynamicData),
            configurationDifferences = compareConfigurations(profile1.configuration, profile2.configuration)
        )
    }

    /**
     * Get profile version history
     */
    fun getVersionHistory(profileId: String): List<CardProfile> {
        // In a full implementation, this would load all versions from storage
        // For now, return current version only
        return profiles[profileId]?.let { listOf(it) } ?: emptyList()
    }

    /**
     * Add transaction to profile
     */
    fun addTransaction(profileId: String, transaction: TransactionRecord): Boolean {
        return try {
            val profile = profiles[profileId] ?: return false
            val updatedTransactions = profile.transactionHistory + transaction
            val updatedProfile = profile.copy(
                transactionHistory = updatedTransactions,
                lastModified = Instant.now()
            )
            profiles[profileId] = updatedProfile
            saveProfile(updatedProfile)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add transaction: ${e.message}", e)
            false
        }
    }

    // Private helper methods

    private fun deriveKeyFromPassword(password: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(password.toByteArray())
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(data: ByteArray): ByteArray {
        if (!encryptionEnabled || encryptionKey == null) {
            return data
        }
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        
        // Prepend IV to encrypted data
        return iv + encrypted
    }

    private fun decrypt(data: ByteArray): ByteArray {
        if (!encryptionEnabled || encryptionKey == null) {
            return data
        }
        
        // Extract IV from beginning
        val iv = data.take(12).toByteArray()
        val encrypted = data.drop(12).toByteArray()
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, spec)
        
        return cipher.doFinal(encrypted)
    }

    private fun saveProfile(profile: CardProfile) {
        try {
            val jsonData = json.encodeToString(CardProfile.serializer(), profile)
            val dataBytes = jsonData.toByteArray()
            val finalData = if (encryptionEnabled) encrypt(dataBytes) else dataBytes
            
            val file = File(storageDir, "${profile.profileId}.json")
            file.writeBytes(finalData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save profile ${profile.profileId}: ${e.message}", e)
        }
    }

    private fun saveAllProfiles() {
        profiles.values.forEach { saveProfile(it) }
        Log.i(TAG, "Saved ${profiles.size} profiles")
    }

    private fun loadExistingProfiles() {
        storageDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
            try {
                val dataBytes = file.readBytes()
                val decryptedData = if (encryptionEnabled) decrypt(dataBytes) else dataBytes
                val jsonData = String(decryptedData)
                val profile = json.decodeFromString(CardProfile.serializer(), jsonData)
                profiles[profile.profileId] = profile
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profile from ${file.name}: ${e.message}", e)
            }
        }
    }

    private fun compareStaticData(data1: StaticCardData, data2: StaticCardData): Map<String, Pair<String, String>> {
        val differences = mutableMapOf<String, Pair<String, String>>()
        
        if (data1.pan != data2.pan) {
            differences["PAN"] = Pair(data1.pan, data2.pan)
        }
        if (data1.expiryDate != data2.expiryDate) {
            differences["ExpiryDate"] = Pair(data1.expiryDate, data2.expiryDate)
        }
        if (data1.cardholderName != data2.cardholderName) {
            differences["CardholderName"] = Pair(data1.cardholderName ?: "", data2.cardholderName ?: "")
        }
        
        return differences
    }

    private fun compareDynamicData(data1: DynamicCardData, data2: DynamicCardData): Map<String, Pair<String, String>> {
        val differences = mutableMapOf<String, Pair<String, String>>()
        
        if (data1.atc != data2.atc) {
            differences["ATC"] = Pair(data1.atc.toString(), data2.atc.toString())
        }
        if (data1.lastOnlineATC != data2.lastOnlineATC) {
            differences["LastOnlineATC"] = Pair(data1.lastOnlineATC.toString(), data2.lastOnlineATC.toString())
        }
        
        return differences
    }

    private fun compareConfigurations(config1: CardConfiguration, config2: CardConfiguration): Map<String, Pair<String, String>> {
        val differences = mutableMapOf<String, Pair<String, String>>()
        
        if (config1.aip != config2.aip) {
            differences["AIP"] = Pair(config1.aip, config2.aip)
        }
        if (config1.cvmList != config2.cvmList) {
            differences["CVMList"] = Pair(config1.cvmList.toString(), config2.cvmList.toString())
        }
        
        return differences
    }
}

/**
 * Profile comparison result
 */
data class ProfileComparison(
    val profile1: CardProfile,
    val profile2: CardProfile,
    val staticDataDifferences: Map<String, Pair<String, String>>,
    val dynamicDataDifferences: Map<String, Pair<String, String>>,
    val configurationDifferences: Map<String, Pair<String, String>>
) {
    val hasStaticDifferences: Boolean get() = staticDataDifferences.isNotEmpty()
    val hasDynamicDifferences: Boolean get() = dynamicDataDifferences.isNotEmpty()
    val hasConfigurationDifferences: Boolean get() = configurationDifferences.isNotEmpty()
    val hasAnyDifferences: Boolean get() = hasStaticDifferences || hasDynamicDifferences || hasConfigurationDifferences
}