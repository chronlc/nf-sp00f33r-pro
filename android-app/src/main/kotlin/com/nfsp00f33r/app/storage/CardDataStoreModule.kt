package com.nfsp00f33r.app.storage

import android.content.Context
import com.nfsp00f33r.app.core.BaseModule
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.core.getLogger
import com.nfsp00f33r.app.storage.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * CardDataStoreModule - Phase 2A Day 4
 * 
 * Module-based wrapper for CardDataStore
 * Integrates encrypted card profile storage into the module system
 * 
 * Features:
 * - Lifecycle management via BaseModule
 * - Health monitoring of storage operations
 * - Graceful initialization and shutdown
 * - Integration with ModuleRegistry
 * 
 * @property context Android application context
 * @property encryptionEnabled Enable/disable encryption (default: true)
 * @property masterPassword Master password for encryption (required if encryption enabled)
 */
class CardDataStoreModule(
    private val context: Context,
    private val encryptionEnabled: Boolean = true,
    private val masterPassword: String? = null
) : BaseModule() {
    
    override val name: String = "CardDataStore"
    override val dependencies: List<String> = emptyList() // Root module - no dependencies
    
    override fun getVersion(): String = CardDataStore.VERSION
    override fun getDescription(): String = "Secure encrypted storage for EMV card profiles"
    
    /**
     * Underlying CardDataStore instance
     */
    private lateinit var dataStore: CardDataStore
    
    /**
     * Storage directory
     */
    private val storageDir: File by lazy {
        File(context.filesDir, "card_profiles")
    }
    
    /**
     * Initialize the card data store
     */
    override suspend fun onInitialize() = withContext(Dispatchers.IO) {
        getLogger().info("Initializing CardDataStore module...")
        
        // Validate master password if encryption enabled
        if (encryptionEnabled && masterPassword.isNullOrEmpty()) {
            throw IllegalStateException("Master password required when encryption is enabled")
        }
        
        // Create and initialize the data store
        dataStore = CardDataStore(
            context = context,
            encryptionEnabled = encryptionEnabled,
            masterPassword = masterPassword
        )
        
        dataStore.initialize()
        
        getLogger().info("CardDataStore initialized with ${dataStore.getAllProfiles().size} profiles")
    }
    
    /**
     * Shutdown the card data store
     */
    override suspend fun onShutdown() = withContext(Dispatchers.IO) {
        getLogger().info("Shutting down CardDataStore module...")
        
        if (::dataStore.isInitialized) {
            dataStore.shutdown()
        }
        
        getLogger().info("CardDataStore shutdown complete")
    }
    
    /**
     * Check health of the storage system
     */
    override fun checkHealth(): HealthStatus {
        return try {
            // Check if data store is initialized
            if (!::dataStore.isInitialized) {
                return HealthStatus.unhealthy(
                    "CardDataStore not initialized",
                    HealthStatus.Severity.CRITICAL
                )
            }
            
            // Check storage directory exists and is writable
            if (!storageDir.exists()) {
                return HealthStatus.unhealthy(
                    "Storage directory does not exist",
                    HealthStatus.Severity.ERROR
                )
            }
            
            if (!storageDir.canWrite()) {
                return HealthStatus.unhealthy(
                    "Storage directory is not writable",
                    HealthStatus.Severity.CRITICAL
                )
            }
            
            // Check available storage space
            val freeSpace = storageDir.freeSpace
            val totalSpace = storageDir.totalSpace
            val freeSpacePercent = (freeSpace.toDouble() / totalSpace.toDouble()) * 100
            
            if (freeSpacePercent < 5.0) {
                return HealthStatus.unhealthy(
                    "Storage space critically low: ${freeSpacePercent.toInt()}% free",
                    HealthStatus.Severity.CRITICAL,
                    mapOf(
                        "freeSpaceBytes" to freeSpace,
                        "totalSpaceBytes" to totalSpace,
                        "freeSpacePercent" to freeSpacePercent
                    )
                )
            }
            
            if (freeSpacePercent < 10.0) {
                return HealthStatus(
                    isHealthy = true,
                    message = "Storage space low: ${freeSpacePercent.toInt()}% free",
                    severity = HealthStatus.Severity.WARNING,
                    metrics = mapOf(
                        "freeSpaceBytes" to freeSpace,
                        "totalSpaceBytes" to totalSpace,
                        "freeSpacePercent" to freeSpacePercent
                    ),
                    timestamp = System.currentTimeMillis()
                )
            }
            
            // Get profile count
            val profileCount = dataStore.getAllProfiles().size
            
            // All healthy
            HealthStatus.healthy(
                "CardDataStore operational",
                mapOf(
                    "profileCount" to profileCount,
                    "freeSpacePercent" to freeSpacePercent,
                    "encryptionEnabled" to encryptionEnabled
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
    
    // ========== Public API (delegates to CardDataStore) ==========
    
    /**
     * Store a new card profile
     */
    fun storeProfile(profile: CardProfile): Result<String> {
        ensureInitialized()
        return dataStore.storeProfile(profile)
    }
    
    /**
     * Retrieve a card profile by ID
     */
    fun getProfile(profileId: String): CardProfile? {
        ensureInitialized()
        return dataStore.getProfile(profileId)
    }
    
    /**
     * Get all stored profiles
     */
    fun getAllProfiles(): List<CardProfile> {
        ensureInitialized()
        return dataStore.getAllProfiles()
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
        ensureInitialized()
        return dataStore.searchProfiles(cardNumber, cardType, issuer, tags)
    }
    
    /**
     * Delete a profile
     */
    fun deleteProfile(profileId: String): Boolean {
        ensureInitialized()
        return dataStore.deleteProfile(profileId)
    }
    
    /**
     * Export profile to JSON file
     */
    fun exportProfile(profileId: String, outputPath: String): Result<Unit> {
        ensureInitialized()
        return dataStore.exportProfile(profileId, outputPath)
    }
    
    /**
     * Import profile from JSON file
     */
    fun importProfile(inputPath: String): Result<String> {
        ensureInitialized()
        return dataStore.importProfile(inputPath)
    }
    
    /**
     * Compare two card profiles
     */
    fun compareProfiles(profileId1: String, profileId2: String): ProfileComparison? {
        ensureInitialized()
        return dataStore.compareProfiles(profileId1, profileId2)
    }
    
    /**
     * Ensure module is initialized before operations
     */
    private fun ensureInitialized() {
        if (!::dataStore.isInitialized) {
            throw IllegalStateException("CardDataStoreModule not initialized. Call initialize() first or register with ModuleRegistry.")
        }
    }
}
