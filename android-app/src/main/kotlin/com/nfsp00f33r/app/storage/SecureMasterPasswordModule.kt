package com.nfsp00f33r.app.storage

import android.content.Context
import com.nfsp00f33r.app.core.BaseModule
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.core.getLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SecureMasterPasswordModule - Phase 2B Day 2
 * 
 * Module-based wrapper for SecureMasterPasswordManager
 * Integrates master password management into the module system
 * 
 * Features:
 * - Lifecycle management via BaseModule
 * - Health monitoring of password storage
 * - Secure password operations
 * - Integration with ModuleRegistry
 * 
 * @property context Android application context
 */
class SecureMasterPasswordModule(
    private val context: Context
) : BaseModule() {
    
    override val name: String = "MasterPassword"
    override val dependencies: List<String> = emptyList() // Root module - no dependencies
    
    override fun getVersion(): String = "1.0.0"
    override fun getDescription(): String = "Secure master password management with Android Keystore"
    
    /**
     * Underlying SecureMasterPasswordManager instance
     */
    private lateinit var manager: SecureMasterPasswordManager
    
    /**
     * Password operation statistics
     */
    private var setPasswordCalls: Int = 0
    private var getPasswordCalls: Int = 0
    private var verifyPasswordCalls: Int = 0
    private var failedVerifications: Int = 0
    
    /**
     * Initialize the master password module
     */
    override suspend fun onInitialize() = withContext(Dispatchers.IO) {
        getLogger().info("Initializing MasterPassword module...")
        
        // Create manager instance
        manager = SecureMasterPasswordManager(context)
        
        // Reset statistics
        setPasswordCalls = 0
        getPasswordCalls = 0
        verifyPasswordCalls = 0
        failedVerifications = 0
        
        // Check if password is already set
        val isSet = manager.isPasswordSet()
        getLogger().info("Master password ${if (isSet) "already set" else "not set"}")
        
        getLogger().info("MasterPassword module initialized")
    }
    
    /**
     * Shutdown the master password module
     */
    override suspend fun onShutdown() = withContext(Dispatchers.IO) {
        getLogger().info("Shutting down MasterPassword module...")
        getLogger().info("Password operations: set=$setPasswordCalls, get=$getPasswordCalls, verify=$verifyPasswordCalls, failed=$failedVerifications")
        getLogger().info("MasterPassword module shutdown complete")
    }
    
    /**
     * Check health of the master password system
     */
    override fun checkHealth(): HealthStatus {
        return try {
            // Check if manager is initialized
            if (!::manager.isInitialized) {
                return HealthStatus.unhealthy(
                    "SecureMasterPasswordManager not initialized",
                    HealthStatus.Severity.CRITICAL
                )
            }
            
            // Check if password is set
            val isPasswordSet = manager.isPasswordSet()
            
            if (!isPasswordSet) {
                return HealthStatus(
                    isHealthy = true,
                    message = "Master password not set (first launch expected)",
                    severity = HealthStatus.Severity.WARNING,
                    metrics = mapOf(
                        "passwordSet" to false,
                        "setPasswordCalls" to setPasswordCalls,
                        "getPasswordCalls" to getPasswordCalls
                    ),
                    timestamp = System.currentTimeMillis()
                )
            }
            
            // Try to retrieve password to verify system health
            val password = try {
                manager.getMasterPassword()
            } catch (e: Exception) {
                getLogger().error("Failed to retrieve master password for health check", e)
                return HealthStatus.unhealthy(
                    "Failed to retrieve master password: ${e.message}",
                    HealthStatus.Severity.CRITICAL,
                    mapOf("error" to (e.message ?: "Unknown"))
                )
            }
            
            if (password == null || password.isEmpty()) {
                return HealthStatus.unhealthy(
                    "Master password set but retrieval returned null/empty",
                    HealthStatus.Severity.CRITICAL
                )
            }
            
            // Check verification failure rate
            val failureRate = if (verifyPasswordCalls > 0) {
                (failedVerifications.toDouble() / verifyPasswordCalls.toDouble()) * 100
            } else {
                0.0
            }
            
            if (failureRate > 50.0 && verifyPasswordCalls > 10) {
                return HealthStatus(
                    isHealthy = true,
                    message = "High password verification failure rate: ${failureRate.toInt()}%",
                    severity = HealthStatus.Severity.WARNING,
                    metrics = mapOf(
                        "passwordSet" to true,
                        "failureRate" to failureRate,
                        "verifyPasswordCalls" to verifyPasswordCalls,
                        "failedVerifications" to failedVerifications
                    ),
                    timestamp = System.currentTimeMillis()
                )
            }
            
            // All healthy
            HealthStatus.healthy(
                "Master password system operational",
                mapOf(
                    "passwordSet" to true,
                    "setPasswordCalls" to setPasswordCalls,
                    "getPasswordCalls" to getPasswordCalls,
                    "verifyPasswordCalls" to verifyPasswordCalls,
                    "failedVerifications" to failedVerifications,
                    "failureRate" to failureRate
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
    
    // ========== Public API (delegates to SecureMasterPasswordManager) ==========
    
    /**
     * Check if master password is set
     */
    fun isPasswordSet(): Boolean {
        ensureInitialized()
        return manager.isPasswordSet()
    }
    
    /**
     * Set master password
     */
    fun setMasterPassword(password: String): Boolean {
        ensureInitialized()
        getLogger().info("Setting master password...")
        setPasswordCalls++
        
        return try {
            val result = manager.setMasterPassword(password)
            if (result) {
                getLogger().info("Master password set successfully")
            } else {
                getLogger().warn("Failed to set master password")
            }
            result
        } catch (e: Exception) {
            getLogger().error("Exception setting master password", e)
            false
        }
    }
    
    /**
     * Get master password
     */
    fun getMasterPassword(): String? {
        ensureInitialized()
        getPasswordCalls++
        return manager.getMasterPassword()
    }
    
    /**
     * Verify master password
     */
    fun verifyMasterPassword(password: String): Boolean {
        ensureInitialized()
        verifyPasswordCalls++
        
        val result = manager.verifyPassword(password)
        if (!result) {
            failedVerifications++
            getLogger().warn("Password verification failed (attempt $verifyPasswordCalls, failed $failedVerifications)")
        }
        
        return result
    }
    
    /**
     * Clear master password
     */
    fun clearMasterPassword() {
        ensureInitialized()
        getLogger().warn("Clearing master password")
        manager.clearPassword()
    }
    
    /**
     * Validate password strength (Phase 2B Day 2)
     */
    fun validatePasswordStrength(password: String): SecureMasterPasswordManager.PasswordValidation {
        ensureInitialized()
        return manager.validatePasswordStrength(password)
    }
    
    /**
     * Get password statistics
     */
    fun getStatistics(): PasswordStatistics {
        return PasswordStatistics(
            passwordSet = if (::manager.isInitialized) manager.isPasswordSet() else false,
            setPasswordCalls = setPasswordCalls,
            getPasswordCalls = getPasswordCalls,
            verifyPasswordCalls = verifyPasswordCalls,
            failedVerifications = failedVerifications
        )
    }
    
    /**
     * Ensure module is initialized before operations
     */
    private fun ensureInitialized() {
        if (!::manager.isInitialized) {
            throw IllegalStateException("SecureMasterPasswordModule not initialized. Call initialize() first or register with ModuleRegistry.")
        }
    }
    
    /**
     * Password statistics data class
     */
    data class PasswordStatistics(
        val passwordSet: Boolean,
        val setPasswordCalls: Int,
        val getPasswordCalls: Int,
        val verifyPasswordCalls: Int,
        val failedVerifications: Int
    )
}
