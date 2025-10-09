package com.nfsp00f33r.app.core

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * ModuleRegistry - Phase 2A Day 2
 * 
 * Central registry for all system modules
 * Manages module lifecycle, dependencies, and health monitoring
 * 
 * Features:
 * - Automatic dependency resolution
 * - Parallel initialization where possible
 * - Health monitoring with auto-restart
 * - Graceful shutdown in correct order
 * - Thread-safe operations
 */
object ModuleRegistry {
    
    private val logger = FrameworkLogger.forModule("ModuleRegistry")
    
    /**
     * All registered modules
     * Key: module name, Value: module instance
     */
    private val modules = ConcurrentHashMap<String, Module>()
    
    /**
     * Module initialization order (topologically sorted by dependencies)
     */
    private val initializationOrder = mutableListOf<String>()
    
    /**
     * Mutex for thread-safe operations
     */
    private val registryMutex = Mutex()
    
    /**
     * Coroutine scope for module operations
     */
    private val moduleScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Health monitoring job
     */
    private var healthMonitorJob: Job? = null
    
    /**
     * Health check interval (milliseconds)
     */
    var healthCheckIntervalMs: Long = 30_000 // 30 seconds
    
    /**
     * Enable automatic restart of failed modules
     */
    var autoRestartEnabled: Boolean = true
    
    /**
     * Event listeners
     */
    private val eventListeners = mutableListOf<ModuleRegistryListener>()
    
    /**
     * Initialize the module registry
     */
    fun initialize() {
        logger.info("Initializing ModuleRegistry")
        modules.clear()
        initializationOrder.clear()
    }
    
    /**
     * Register a module
     * Must be called before startAll()
     */
    fun registerModule(module: Module) {
        if (modules.containsKey(module.name)) {
            logger.warn("Module ${module.name} already registered, skipping")
            return
        }
        
        logger.info("Registering module: ${module.name}")
        modules[module.name] = module
        
        notifyModuleRegistered(module)
    }
    
    /**
     * Unregister a module
     */
    suspend fun unregisterModule(moduleName: String) {
        modules[moduleName]?.let { module ->
            logger.info("Unregistering module: $moduleName")
            
            // Shutdown if running
            if (module.state == ModuleState.RUNNING) {
                module.shutdown()
            }
            
            modules.remove(moduleName)
            initializationOrder.remove(moduleName)
            
            notifyModuleUnregistered(module)
        }
    }
    
    /**
     * Start all registered modules
     * Initializes modules in dependency order
     */
    suspend fun startAll(): Map<String, InitializationResult> = registryMutex.withLock {
        logger.info("Starting all modules (${modules.size} registered)")
        
        // Calculate initialization order based on dependencies
        val order = calculateInitializationOrder()
        initializationOrder.clear()
        initializationOrder.addAll(order)
        
        logger.info("Initialization order: ${order.joinToString(" -> ")}")
        
        // Initialize modules in order
        val results = mutableMapOf<String, InitializationResult>()
        
        for (moduleName in order) {
            val module = modules[moduleName] ?: continue
            
            try {
                logger.info("Initializing $moduleName...")
                module.initialize()
                results[moduleName] = InitializationResult.Success(module)
                notifyModuleInitialized(module)
            } catch (e: Exception) {
                logger.error("Failed to initialize $moduleName", e)
                results[moduleName] = InitializationResult.Failure(module, e)
                notifyModuleError(module, e)
                
                // Decide whether to continue or abort
                if (module.dependencies.isEmpty()) {
                    // Root module failed - this is serious but continue with others
                    logger.warn("Root module $moduleName failed, continuing with others")
                } else {
                    // Dependent module failed - might want to skip dependents
                    logger.warn("Module $moduleName failed, dependent modules may also fail")
                }
            }
        }
        
        // Start health monitoring
        startHealthMonitoring()
        
        logger.info("Module initialization complete: ${results.values.count { it is InitializationResult.Success }}/${results.size} successful")
        
        results
    }
    
    /**
     * Stop all modules
     * Shuts down in reverse dependency order
     */
    suspend fun stopAll() = registryMutex.withLock {
        logger.info("Stopping all modules")
        
        // Stop health monitoring
        stopHealthMonitoring()
        
        // Shutdown in reverse order
        val shutdownOrder = initializationOrder.reversed()
        
        for (moduleName in shutdownOrder) {
            val module = modules[moduleName] ?: continue
            
            try {
                logger.info("Shutting down $moduleName...")
                module.shutdown()
                notifyModuleShutdown(module)
            } catch (e: Exception) {
                logger.error("Error shutting down $moduleName", e)
            }
        }
        
        logger.info("All modules stopped")
    }
    
    /**
     * Calculate module initialization order using topological sort
     * Ensures dependencies are initialized before dependents
     */
    private fun calculateInitializationOrder(): List<String> {
        val order = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        
        fun visit(moduleName: String) {
            if (visited.contains(moduleName)) return
            
            if (visiting.contains(moduleName)) {
                logger.error("Circular dependency detected involving $moduleName")
                throw IllegalStateException("Circular dependency detected: $moduleName")
            }
            
            visiting.add(moduleName)
            
            val module = modules[moduleName]
            if (module != null) {
                // Visit dependencies first
                for (dependency in module.dependencies) {
                    if (!modules.containsKey(dependency)) {
                        logger.warn("Module $moduleName depends on $dependency which is not registered")
                    } else {
                        visit(dependency)
                    }
                }
            }
            
            visiting.remove(moduleName)
            visited.add(moduleName)
            order.add(moduleName)
        }
        
        // Visit all modules
        for (moduleName in modules.keys) {
            visit(moduleName)
        }
        
        return order
    }
    
    /**
     * Start health monitoring
     * Periodically checks all modules and restarts failed ones
     */
    private fun startHealthMonitoring() {
        if (healthMonitorJob?.isActive == true) {
            logger.warn("Health monitoring already running")
            return
        }
        
        logger.info("Starting health monitoring (interval: ${healthCheckIntervalMs}ms)")
        
        healthMonitorJob = moduleScope.launch {
            while (isActive) {
                try {
                    checkAllModuleHealth()
                } catch (e: Exception) {
                    logger.error("Error in health monitoring", e)
                }
                
                delay(healthCheckIntervalMs)
            }
        }
    }
    
    /**
     * Stop health monitoring
     */
    private fun stopHealthMonitoring() {
        healthMonitorJob?.cancel()
        healthMonitorJob = null
        logger.info("Health monitoring stopped")
    }
    
    /**
     * Check health of all modules
     */
    private suspend fun checkAllModuleHealth() {
        val unhealthyModules = mutableListOf<Pair<Module, HealthStatus>>()
        
        for ((name, module) in modules) {
            try {
                val health = module.checkHealth()
                
                if (!health.isHealthy) {
                    logger.warn("Module $name is unhealthy: ${health.message}")
                    unhealthyModules.add(module to health)
                    notifyModuleHealthChanged(module, health)
                }
            } catch (e: Exception) {
                logger.error("Error checking health of $name", e)
            }
        }
        
        // Auto-restart unhealthy modules if enabled
        if (autoRestartEnabled && unhealthyModules.isNotEmpty()) {
            for ((module, health) in unhealthyModules) {
                if (health.severity == HealthStatus.Severity.CRITICAL || 
                    health.severity == HealthStatus.Severity.ERROR) {
                    logger.info("Auto-restarting unhealthy module: ${module.name}")
                    try {
                        module.restart()
                        logger.info("Successfully restarted ${module.name}")
                    } catch (e: Exception) {
                        logger.error("Failed to restart ${module.name}", e)
                    }
                }
            }
        }
    }
    
    /**
     * Get module by name
     */
    fun getModule(name: String): Module? = modules[name]
    
    /**
     * Get module by type
     */
    fun <T : Module> getModuleByType(type: Class<T>): T? {
        return modules.values.firstOrNull { type.isInstance(it) }?.let { type.cast(it) }
    }
    
    /**
     * Get all modules
     */
    fun getAllModules(): List<Module> = modules.values.toList()
    
    /**
     * Get modules by state
     */
    fun getModulesByState(state: ModuleState): List<Module> {
        return modules.values.filter { it.state == state }
    }
    
    /**
     * Check if all modules are healthy
     */
    fun areAllModulesHealthy(): Boolean {
        return modules.values.all { 
            it.state == ModuleState.RUNNING && it.checkHealth().isHealthy 
        }
    }
    
    /**
     * Get module health summary
     */
    fun getHealthSummary(): Map<String, HealthStatus> {
        return modules.mapValues { (_, module) ->
            try {
                module.checkHealth()
            } catch (e: Exception) {
                HealthStatus.unhealthy(
                    "Health check failed: ${e.message}",
                    HealthStatus.Severity.CRITICAL
                )
            }
        }
    }
    
    /**
     * Add registry event listener
     */
    fun addEventListener(listener: ModuleRegistryListener) {
        eventListeners.add(listener)
    }
    
    /**
     * Remove registry event listener
     */
    fun removeEventListener(listener: ModuleRegistryListener) {
        eventListeners.remove(listener)
    }
    
    // Event notification methods
    private fun notifyModuleRegistered(module: Module) {
        eventListeners.forEach { it.onModuleRegistered(module) }
    }
    
    private fun notifyModuleUnregistered(module: Module) {
        eventListeners.forEach { it.onModuleUnregistered(module) }
    }
    
    private fun notifyModuleInitialized(module: Module) {
        eventListeners.forEach { it.onModuleInitialized(module) }
    }
    
    private fun notifyModuleShutdown(module: Module) {
        eventListeners.forEach { it.onModuleShutdown(module) }
    }
    
    private fun notifyModuleError(module: Module, error: Throwable) {
        eventListeners.forEach { it.onModuleError(module, error) }
    }
    
    private fun notifyModuleHealthChanged(module: Module, health: HealthStatus) {
        eventListeners.forEach { it.onModuleHealthChanged(module, health) }
    }
    
    /**
     * Registry event listener interface
     */
    interface ModuleRegistryListener {
        fun onModuleRegistered(module: Module) {}
        fun onModuleUnregistered(module: Module) {}
        fun onModuleInitialized(module: Module) {}
        fun onModuleShutdown(module: Module) {}
        fun onModuleError(module: Module, error: Throwable) {}
        fun onModuleHealthChanged(module: Module, health: HealthStatus) {}
    }
}
