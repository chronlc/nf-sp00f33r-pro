package com.nfsp00f33r.app.core

/**
 * Module - Phase 2A Day 1
 * 
 * Core module interface defining the contract for all system modules
 * Provides lifecycle management, health monitoring, and dependency tracking
 * 
 * Every major component (CardDataStore, PN532Manager, etc.) should implement this
 * to participate in the centralized module system with proper lifecycle and monitoring
 */
interface Module {
    
    /**
     * Unique module identifier
     * Used for dependency resolution and logging
     */
    val name: String
    
    /**
     * List of module names this module depends on
     * Modules will be initialized in dependency order
     */
    val dependencies: List<String>
    
    /**
     * Current state of the module
     */
    val state: ModuleState
    
    /**
     * Initialize the module
     * Called once when the module is registered
     * Should set state to RUNNING on success, ERROR on failure
     * 
     * @throws Exception if initialization fails critically
     */
    suspend fun initialize()
    
    /**
     * Shutdown the module gracefully
     * Called when app is closing or module needs to stop
     * Should clean up resources, close connections, etc.
     */
    suspend fun shutdown()
    
    /**
     * Restart the module
     * Useful for recovering from errors or reloading configuration
     */
    suspend fun restart() {
        shutdown()
        initialize()
    }
    
    /**
     * Check if module is healthy and operating correctly
     * Called periodically by ModuleRegistry for monitoring
     * 
     * @return HealthStatus indicating module health
     */
    fun checkHealth(): HealthStatus
    
    /**
     * Get module version
     * Useful for debugging and compatibility checking
     */
    fun getVersion(): String = "1.0.0"
    
    /**
     * Get module description
     * Human-readable description of what this module does
     */
    fun getDescription(): String = "No description available"
}

/**
 * Module lifecycle states
 */
enum class ModuleState {
    /** Module has been created but not initialized */
    UNINITIALIZED,
    
    /** Module is currently initializing */
    INITIALIZING,
    
    /** Module is running normally */
    RUNNING,
    
    /** Module encountered an error */
    ERROR,
    
    /** Module is shutting down */
    SHUTTING_DOWN,
    
    /** Module has been shut down */
    SHUTDOWN
}

/**
 * Module health status
 */
data class HealthStatus(
    /** Overall health: true = healthy, false = unhealthy */
    val isHealthy: Boolean,
    
    /** Human-readable status message */
    val message: String,
    
    /** Detailed health metrics (optional) */
    val metrics: Map<String, Any> = emptyMap(),
    
    /** Timestamp of health check */
    val timestamp: Long = System.currentTimeMillis(),
    
    /** Severity level if unhealthy */
    val severity: Severity = if (isHealthy) Severity.INFO else Severity.WARNING
) {
    enum class Severity {
        INFO,      // Normal operation
        WARNING,   // Issue detected but module still functional
        ERROR,     // Critical issue, module may not be functional
        CRITICAL   // Severe issue, module definitely not functional
    }
    
    companion object {
        /** Quick constructor for healthy status */
        fun healthy(message: String = "Module is healthy", metrics: Map<String, Any> = emptyMap()) =
            HealthStatus(true, message, metrics)
        
        /** Quick constructor for unhealthy status */
        fun unhealthy(
            message: String, 
            severity: Severity = Severity.ERROR,
            metrics: Map<String, Any> = emptyMap()
        ) = HealthStatus(false, message, metrics, severity = severity)
    }
}

/**
 * Module initialization result
 */
sealed class InitializationResult {
    /** Module initialized successfully */
    data class Success(val module: Module) : InitializationResult()
    
    /** Module initialization failed */
    data class Failure(
        val module: Module,
        val error: Throwable,
        val message: String = error.message ?: "Initialization failed"
    ) : InitializationResult()
    
    /** Module initialization skipped (already initialized) */
    data class Skipped(val module: Module, val reason: String) : InitializationResult()
}

/**
 * Module event listener
 * Allows other components to react to module lifecycle changes
 */
interface ModuleEventListener {
    /** Called when module state changes */
    fun onStateChanged(module: Module, oldState: ModuleState, newState: ModuleState) {}
    
    /** Called when module health status changes */
    fun onHealthChanged(module: Module, health: HealthStatus) {}
    
    /** Called when module encounters an error */
    fun onError(module: Module, error: Throwable) {}
}
