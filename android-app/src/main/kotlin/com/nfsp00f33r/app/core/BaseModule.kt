package com.nfsp00f33r.app.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * BaseModule - Phase 2A Day 1
 * 
 * Abstract base implementation of Module interface
 * Provides common functionality for lifecycle management and state tracking
 * 
 * Subclasses should override:
 * - name: Unique module identifier
 * - dependencies: List of required modules
 * - onInitialize(): Actual initialization logic
 * - checkHealth(): Health check implementation
 */
abstract class BaseModule : Module {
    
    /**
     * Current module state
     * Protected by mutex for thread safety
     */
    private var _state: ModuleState = ModuleState.UNINITIALIZED
    private val stateMutex = Mutex()
    
    override val state: ModuleState
        get() = _state
    
    /**
     * Event listeners registered for this module
     */
    private val listeners = mutableListOf<ModuleEventListener>()
    
    /**
     * Initialization timestamp
     */
    protected var initializationTime: Long = 0
    
    /**
     * Last error encountered
     */
    protected var lastError: Throwable? = null
    
    /**
     * Initialize the module
     * Handles state transitions and error handling
     */
    override suspend fun initialize() {
        stateMutex.withLock {
            if (_state == ModuleState.RUNNING) {
                FrameworkLogger.warn("$name: Module already initialized, skipping")
                return
            }
            
            if (_state == ModuleState.INITIALIZING) {
                FrameworkLogger.warn("$name: Module already initializing, skipping")
                return
            }
            
            FrameworkLogger.info("$name: Initializing module...")
            setState(ModuleState.INITIALIZING)
        }
        
        try {
            val startTime = System.currentTimeMillis()
            
            // Call subclass initialization
            onInitialize()
            
            val elapsedMs = System.currentTimeMillis() - startTime
            initializationTime = System.currentTimeMillis()
            
            stateMutex.withLock {
                setState(ModuleState.RUNNING)
            }
            
            FrameworkLogger.info("$name: Initialized successfully in ${elapsedMs}ms")
            
        } catch (e: Exception) {
            lastError = e
            
            stateMutex.withLock {
                setState(ModuleState.ERROR)
            }
            
            FrameworkLogger.error("$name: Initialization failed", e)
            notifyError(e)
            
            throw e
        }
    }
    
    /**
     * Shutdown the module
     * Handles state transitions and cleanup
     */
    override suspend fun shutdown() {
        stateMutex.withLock {
            if (_state == ModuleState.SHUTDOWN) {
                FrameworkLogger.warn("$name: Module already shut down, skipping")
                return
            }
            
            if (_state == ModuleState.SHUTTING_DOWN) {
                FrameworkLogger.warn("$name: Module already shutting down, skipping")
                return
            }
            
            FrameworkLogger.info("$name: Shutting down module...")
            setState(ModuleState.SHUTTING_DOWN)
        }
        
        try {
            // Call subclass shutdown
            onShutdown()
            
            stateMutex.withLock {
                setState(ModuleState.SHUTDOWN)
            }
            
            FrameworkLogger.info("$name: Shut down successfully")
            
        } catch (e: Exception) {
            lastError = e
            
            FrameworkLogger.error("$name: Shutdown failed", e)
            notifyError(e)
            
            // Still mark as shutdown even if cleanup failed
            stateMutex.withLock {
                setState(ModuleState.SHUTDOWN)
            }
        }
    }
    
    /**
     * Restart the module
     * Shuts down then re-initializes
     */
    override suspend fun restart() {
        FrameworkLogger.info("$name: Restarting module...")
        shutdown()
        initialize()
    }
    
    /**
     * Set module state and notify listeners
     */
    private fun setState(newState: ModuleState) {
        val oldState = _state
        _state = newState
        
        if (oldState != newState) {
            notifyStateChanged(oldState, newState)
        }
    }
    
    /**
     * Default health check implementation
     * Returns healthy if module is RUNNING, unhealthy otherwise
     * 
     * Subclasses should override for custom health checks
     */
    override fun checkHealth(): HealthStatus {
        return when (_state) {
            ModuleState.RUNNING -> {
                HealthStatus.healthy(
                    message = "Module is running normally",
                    metrics = mapOf(
                        "uptime_ms" to (System.currentTimeMillis() - initializationTime),
                        "state" to _state.name
                    )
                )
            }
            ModuleState.ERROR -> {
                HealthStatus.unhealthy(
                    message = "Module is in error state: ${lastError?.message ?: "Unknown error"}",
                    severity = HealthStatus.Severity.ERROR,
                    metrics = mapOf(
                        "state" to _state.name,
                        "last_error" to (lastError?.message ?: "None")
                    )
                )
            }
            else -> {
                HealthStatus.unhealthy(
                    message = "Module is not running (state: $_state)",
                    severity = HealthStatus.Severity.WARNING,
                    metrics = mapOf("state" to _state.name)
                )
            }
        }
    }
    
    /**
     * Add event listener
     */
    fun addEventListener(listener: ModuleEventListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove event listener
     */
    fun removeEventListener(listener: ModuleEventListener) {
        listeners.remove(listener)
    }
    
    /**
     * Notify all listeners of state change
     */
    private fun notifyStateChanged(oldState: ModuleState, newState: ModuleState) {
        listeners.forEach { listener ->
            try {
                listener.onStateChanged(this, oldState, newState)
            } catch (e: Exception) {
                FrameworkLogger.error("$name: Error notifying listener of state change", e)
            }
        }
    }
    
    /**
     * Notify all listeners of health change
     */
    protected fun notifyHealthChanged(health: HealthStatus) {
        listeners.forEach { listener ->
            try {
                listener.onHealthChanged(this, health)
            } catch (e: Exception) {
                FrameworkLogger.error("$name: Error notifying listener of health change", e)
            }
        }
    }
    
    /**
     * Notify all listeners of error
     */
    private fun notifyError(error: Throwable) {
        listeners.forEach { listener ->
            try {
                listener.onError(this, error)
            } catch (e: Exception) {
                FrameworkLogger.error("$name: Error notifying listener of error", e)
            }
        }
    }
    
    /**
     * Subclasses must implement actual initialization logic
     * Called by initialize() after state management
     * 
     * Should throw exception on failure
     */
    protected abstract suspend fun onInitialize()
    
    /**
     * Subclasses must implement actual shutdown logic
     * Called by shutdown() after state management
     * 
     * Should clean up resources, close connections, etc.
     */
    protected abstract suspend fun onShutdown()
    
    /**
     * Get module uptime in milliseconds
     */
    fun getUptimeMs(): Long {
        return if (initializationTime > 0) {
            System.currentTimeMillis() - initializationTime
        } else {
            0
        }
    }
    
    /**
     * Check if module is currently running
     */
    fun isRunning(): Boolean = _state == ModuleState.RUNNING
    
    /**
     * Check if module has error
     */
    fun hasError(): Boolean = _state == ModuleState.ERROR
}
