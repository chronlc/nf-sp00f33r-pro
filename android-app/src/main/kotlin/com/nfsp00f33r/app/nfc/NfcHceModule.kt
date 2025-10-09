package com.nfsp00f33r.app.nfc

import android.content.Context
import com.nfsp00f33r.app.core.BaseModule
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.core.getLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NfcHceModule - Phase 2B Days 3-4
 * 
 * Module-based wrapper for NfcHceManager
 * Integrates NFC Host Card Emulation management into the module system
 * 
 * Features:
 * - Lifecycle management via BaseModule
 * - Health monitoring of NFC/HCE availability
 * - Service state management
 * - Integration with ModuleRegistry
 * 
 * @property context Android application context
 */
class NfcHceModule(
    private val context: Context
) : BaseModule() {
    
    override val name: String = "NfcHce"
    override val dependencies: List<String> = emptyList() // No dependencies - hardware module
    
    override fun getVersion(): String = "1.0.0"
    override fun getDescription(): String = "NFC Host Card Emulation management module"
    
    /**
     * Underlying NfcHceManager instance
     */
    private lateinit var manager: NfcHceManager
    
    /**
     * Service state tracking
     */
    private var serviceStartAttempts: Int = 0
    private var serviceStopAttempts: Int = 0
    private var isServiceRunning: Boolean = false
    
    /**
     * Initialize the NFC HCE module
     */
    override suspend fun onInitialize() = withContext(Dispatchers.IO) {
        getLogger().info("Initializing NfcHce module...")
        
        // Create manager instance
        manager = NfcHceManager(context)
        
        // Reset statistics
        serviceStartAttempts = 0
        serviceStopAttempts = 0
        isServiceRunning = false
        
        // Check initial NFC/HCE availability
        val nfcAvailable = manager.isNfcAvailable()
        val hceSupported = manager.isHceSupported()
        
        getLogger().info("NFC available: $nfcAvailable, HCE supported: $hceSupported")
        getLogger().info("NfcHce module initialized")
    }
    
    /**
     * Shutdown the NFC HCE module
     */
    override suspend fun onShutdown() = withContext(Dispatchers.IO) {
        getLogger().info("Shutting down NfcHce module...")
        
        // Stop service if running
        if (isServiceRunning) {
            stopHceService()
        }
        
        getLogger().info("Service operations: starts=$serviceStartAttempts, stops=$serviceStopAttempts")
        getLogger().info("NfcHce module shutdown complete")
    }
    
    /**
     * Check health of the NFC HCE system
     */
    override fun checkHealth(): HealthStatus {
        return try {
            // Check if manager is initialized
            if (!::manager.isInitialized) {
                return HealthStatus.unhealthy(
                    "NfcHceManager not initialized",
                    HealthStatus.Severity.CRITICAL
                )
            }
            
            // Check NFC availability
            val nfcAvailable = manager.isNfcAvailable()
            if (!nfcAvailable) {
                return HealthStatus(
                    isHealthy = true,
                    message = "NFC not available or disabled on this device",
                    severity = HealthStatus.Severity.WARNING,
                    metrics = mapOf(
                        "nfcAvailable" to false,
                        "hceSupported" to manager.isHceSupported(),
                        "serviceRunning" to isServiceRunning
                    ),
                    timestamp = System.currentTimeMillis()
                )
            }
            
            // Check HCE support
            val hceSupported = manager.isHceSupported()
            if (!hceSupported) {
                return HealthStatus.unhealthy(
                    "NFC available but HCE not supported",
                    HealthStatus.Severity.WARNING,
                    mapOf(
                        "nfcAvailable" to true,
                        "hceSupported" to false,
                        "serviceRunning" to isServiceRunning
                    )
                )
            }
            
            // All healthy - NFC available and HCE supported
            HealthStatus.healthy(
                "NFC and HCE fully operational",
                mapOf(
                    "nfcAvailable" to true,
                    "hceSupported" to true,
                    "serviceRunning" to isServiceRunning,
                    "serviceStartAttempts" to serviceStartAttempts,
                    "serviceStopAttempts" to serviceStopAttempts,
                    "status" to manager.getHceStatus()
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
    
    // ========== Public API (delegates to NfcHceManager) ==========
    
    /**
     * Check if NFC is available and enabled
     */
    fun isNfcAvailable(): Boolean {
        ensureInitialized()
        return manager.isNfcAvailable()
    }
    
    /**
     * Check if HCE is supported
     */
    fun isHceSupported(): Boolean {
        ensureInitialized()
        return manager.isHceSupported()
    }
    
    /**
     * Start HCE service
     */
    fun startHceService(): Boolean {
        ensureInitialized()
        getLogger().info("Starting HCE service...")
        
        serviceStartAttempts++
        
        val result = try {
            val started = manager.startHceService()
            if (started) {
                isServiceRunning = true
                getLogger().info("HCE service started successfully")
            } else {
                getLogger().warn("Failed to start HCE service")
            }
            started
        } catch (e: Exception) {
            getLogger().error("Exception starting HCE service", e)
            false
        }
        
        return result
    }
    
    /**
     * Stop HCE service
     */
    fun stopHceService(): Boolean {
        ensureInitialized()
        getLogger().info("Stopping HCE service...")
        
        serviceStopAttempts++
        
        val result = try {
            val stopped = manager.stopHceService()
            if (stopped) {
                isServiceRunning = false
                getLogger().info("HCE service stopped successfully")
            } else {
                getLogger().warn("Failed to stop HCE service")
            }
            stopped
        } catch (e: Exception) {
            getLogger().error("Exception stopping HCE service", e)
            false
        }
        
        return result
    }
    
    /**
     * Get HCE service status
     */
    fun getHceStatus(): String {
        ensureInitialized()
        return manager.getHceStatus()
    }
    
    /**
     * Get service statistics
     */
    fun getStatistics(): HceStatistics {
        return HceStatistics(
            nfcAvailable = if (::manager.isInitialized) manager.isNfcAvailable() else false,
            hceSupported = if (::manager.isInitialized) manager.isHceSupported() else false,
            serviceRunning = isServiceRunning,
            serviceStartAttempts = serviceStartAttempts,
            serviceStopAttempts = serviceStopAttempts
        )
    }
    
    /**
     * Ensure module is initialized before operations
     */
    private fun ensureInitialized() {
        if (!::manager.isInitialized) {
            throw IllegalStateException("NfcHceModule not initialized. Call initialize() first or register with ModuleRegistry.")
        }
    }
    
    /**
     * HCE service statistics data class
     */
    data class HceStatistics(
        val nfcAvailable: Boolean,
        val hceSupported: Boolean,
        val serviceRunning: Boolean,
        val serviceStartAttempts: Int,
        val serviceStopAttempts: Int
    )
}
