package com.nfsp00f33r.app.hardware

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.nfsp00f33r.app.core.BaseModule
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.core.getLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

/**
 * PN532DeviceModule - Phase 2B Day 1
 * 
 * Module-based wrapper for PN532Manager
 * Integrates PN532 hardware management into the module system
 * 
 * Features:
 * - Lifecycle management via BaseModule
 * - Health monitoring of device connection
 * - Graceful initialization and shutdown
 * - Integration with ModuleRegistry
 * 
 * @property context Android application context
 */
class PN532DeviceModule(
    private val context: Context
) : BaseModule() {
    
    override val name: String = "PN532Device"
    override val dependencies: List<String> = emptyList() // No dependencies - hardware module
    
    override fun getVersion(): String = "1.0.0"
    override fun getDescription(): String = "PN532 NFC hardware module manager"
    
    /**
     * Underlying PN532Manager instance
     */
    private lateinit var manager: PN532Manager
    
    /**
     * Connection state tracking
     */
    private var lastConnectionState: PN532Manager.ConnectionState = PN532Manager.ConnectionState.DISCONNECTED
    private var lastConnectionType: PN532Manager.ConnectionType = PN532Manager.ConnectionType.USB_SERIAL
    private var connectionAttempts: Int = 0
    private var successfulConnections: Int = 0
    private var failedConnections: Int = 0
    
    /**
     * Initialize the PN532 device module
     */
    override suspend fun onInitialize() = withContext(Dispatchers.IO) {
        getLogger().info("Initializing PN532Device module...")
        
        // Create manager instance
        manager = PN532Manager(context)
        
        // Set up state observation
        manager.connectionState.observeForever { state ->
            lastConnectionState = state
            getLogger().debug("PN532 connection state changed: $state")
        }
        
        manager.connectionType.observeForever { type ->
            lastConnectionType = type
            getLogger().debug("PN532 connection type changed: $type")
        }
        
        // Reset statistics
        connectionAttempts = 0
        successfulConnections = 0
        failedConnections = 0
        
        getLogger().info("PN532Device module initialized (device not connected yet)")
    }
    
    /**
     * Shutdown the PN532 device module
     */
    override suspend fun onShutdown() = withContext(Dispatchers.IO) {
        getLogger().info("Shutting down PN532Device module...")
        
        if (::manager.isInitialized) {
            // Disconnect if connected
            if (lastConnectionState == PN532Manager.ConnectionState.CONNECTED) {
                getLogger().info("Disconnecting PN532 device before shutdown...")
                manager.disconnect()
            }
        }
        
        getLogger().info("PN532Device module shutdown complete")
    }
    
    /**
     * Check health of the PN532 device
     */
    override fun checkHealth(): HealthStatus {
        return try {
            // Check if manager is initialized
            if (!::manager.isInitialized) {
                return HealthStatus.unhealthy(
                    "PN532Manager not initialized",
                    HealthStatus.Severity.CRITICAL
                )
            }
            
            // Check connection state
            when (lastConnectionState) {
                PN532Manager.ConnectionState.DISCONNECTED -> {
                    return HealthStatus(
                        isHealthy = true,
                        message = "PN532 device disconnected (ready to connect)",
                        severity = HealthStatus.Severity.INFO,
                        metrics = mapOf(
                            "connectionState" to lastConnectionState.name,
                            "connectionAttempts" to connectionAttempts,
                            "successfulConnections" to successfulConnections,
                            "failedConnections" to failedConnections
                        ),
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                PN532Manager.ConnectionState.CONNECTING -> {
                    return HealthStatus(
                        isHealthy = true,
                        message = "PN532 device connecting...",
                        severity = HealthStatus.Severity.INFO,
                        metrics = mapOf(
                            "connectionState" to lastConnectionState.name,
                            "connectionType" to lastConnectionType.name
                        ),
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                PN532Manager.ConnectionState.CONNECTED -> {
                    // Test device responsiveness
                    val testFuture = manager.testConnection()
                    val isResponsive = try {
                        testFuture.get(5, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: Exception) {
                        getLogger().warn("Device responsiveness test failed: ${e.message}")
                        false
                    }
                    
                    if (!isResponsive) {
                        return HealthStatus.unhealthy(
                            "PN532 device connected but not responsive",
                            HealthStatus.Severity.WARNING,
                            mapOf(
                                "connectionState" to lastConnectionState.name,
                                "connectionType" to lastConnectionType.name,
                                "responsive" to false
                            )
                        )
                    }
                    
                    return HealthStatus.healthy(
                        "PN532 device connected and responsive",
                        mapOf(
                            "connectionState" to lastConnectionState.name,
                            "connectionType" to lastConnectionType.name,
                            "connectionAttempts" to connectionAttempts,
                            "successfulConnections" to successfulConnections,
                            "failedConnections" to failedConnections,
                            "responsive" to true
                        )
                    )
                }
                
                PN532Manager.ConnectionState.ERROR -> {
                    val errorMessage = manager.lastError.value ?: "Unknown error"
                    return HealthStatus.unhealthy(
                        "PN532 device in error state: $errorMessage",
                        HealthStatus.Severity.ERROR,
                        mapOf(
                            "connectionState" to lastConnectionState.name,
                            "lastError" to errorMessage,
                            "failedConnections" to failedConnections
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            getLogger().error("Health check failed", e)
            HealthStatus.unhealthy(
                "Health check failed: ${e.message}",
                HealthStatus.Severity.ERROR
            )
        }
    }
    
    // ========== Public API (delegates to PN532Manager) ==========
    
    /**
     * Connect to PN532 device
     */
    fun connect(type: PN532Manager.ConnectionType = PN532Manager.ConnectionType.USB_SERIAL) {
        ensureInitialized()
        getLogger().info("Connecting to PN532 via ${type.name}...")
        
        connectionAttempts++
        
        try {
            manager.connect(type)
            successfulConnections++
        } catch (e: Exception) {
            failedConnections++
            getLogger().error("Connection failed", e)
            throw e
        }
    }
    
    /**
     * Disconnect from PN532 device
     */
    fun disconnect() {
        ensureInitialized()
        getLogger().info("Disconnecting from PN532...")
        manager.disconnect()
    }
    
    /**
     * Test device connection
     */
    fun testConnection(): CompletableFuture<Boolean> {
        ensureInitialized()
        return manager.testConnection()
    }
    
    /**
     * Check if PN532 device is connected
     */
    fun isConnected(): Boolean {
        ensureInitialized()
        return manager.isConnected()
    }
    
    /**
     * Set connection callback for status updates
     */
    fun setConnectionCallback(callback: (String) -> Unit) {
        ensureInitialized()
        manager.setConnectionCallback(callback)
    }
    
    /**
     * Get connection state LiveData
     */
    fun getConnectionState(): MutableLiveData<PN532Manager.ConnectionState> {
        ensureInitialized()
        return manager.connectionState
    }
    
    /**
     * Get connection type LiveData
     */
    fun getConnectionType(): MutableLiveData<PN532Manager.ConnectionType> {
        ensureInitialized()
        return manager.connectionType
    }
    
    /**
     * Get last error LiveData
     */
    fun getLastError(): MutableLiveData<String> {
        ensureInitialized()
        return manager.lastError
    }
    
    /**
     * Get connection statistics
     */
    fun getConnectionStatistics(): ConnectionStatistics {
        return ConnectionStatistics(
            totalAttempts = connectionAttempts,
            successful = successfulConnections,
            failed = failedConnections,
            currentState = lastConnectionState,
            currentType = lastConnectionType
        )
    }
    
    /**
     * Ensure module is initialized before operations
     */
    private fun ensureInitialized() {
        if (!::manager.isInitialized) {
            throw IllegalStateException("PN532DeviceModule not initialized. Call initialize() first or register with ModuleRegistry.")
        }
    }
    
    /**
     * Connection statistics data class
     */
    data class ConnectionStatistics(
        val totalAttempts: Int,
        val successful: Int,
        val failed: Int,
        val currentState: PN532Manager.ConnectionState,
        val currentType: PN532Manager.ConnectionType
    )
}
