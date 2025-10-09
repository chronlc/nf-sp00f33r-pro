package com.nfsp00f33r.app.core

import android.util.Log
import com.nfsp00f33r.app.core.BaseModule
import com.nfsp00f33r.app.core.HealthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LoggingModule - Phase 2A Day 7
 * 
 * Module for centralized application logging configuration
 * Demonstrates minimal module implementation
 * 
 * Features:
 * - Configurable log levels
 * - Log file rotation (future)
 * - Crash reporting integration (future)
 * - Performance monitoring (future)
 */
class LoggingModule(
    private val minLogLevel: LogLevel = LogLevel.DEBUG,
    private val enableFileLogging: Boolean = false
) : BaseModule() {
    
    override val name: String = "Logging"
    override val dependencies: List<String> = emptyList() // Root module
    
    override fun getVersion(): String = "1.0.0"
    override fun getDescription(): String = "Centralized logging configuration and management"
    
    /**
     * Log levels matching Android Log priorities
     */
    enum class LogLevel(val priority: Int) {
        VERBOSE(Log.VERBOSE),
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR),
        ASSERT(Log.ASSERT)
    }
    
    /**
     * Current minimum log level
     */
    var currentMinLogLevel: LogLevel = minLogLevel
        private set
    
    /**
     * Log statistics
     */
    private var logCount: Long = 0
    private var errorCount: Long = 0
    private var warningCount: Long = 0
    
    /**
     * Initialize logging module
     */
    override suspend fun onInitialize() = withContext(Dispatchers.IO) {
        getLogger().info("Initializing Logging module...")
        getLogger().info("Min log level: $minLogLevel")
        getLogger().info("File logging: ${if (enableFileLogging) "enabled" else "disabled"}")
        
        // Reset statistics
        logCount = 0
        errorCount = 0
        warningCount = 0
        
        getLogger().info("Logging module initialized")
    }
    
    /**
     * Shutdown logging module
     */
    override suspend fun onShutdown() = withContext(Dispatchers.IO) {
        getLogger().info("Shutting down Logging module...")
        getLogger().info("Total logs: $logCount (errors: $errorCount, warnings: $warningCount)")
        
        // Flush any pending logs
        if (enableFileLogging) {
            // Future: Flush file buffers
        }
        
        getLogger().info("Logging module shutdown complete")
    }
    
    /**
     * Check logging system health
     */
    override fun checkHealth(): HealthStatus {
        return try {
            // Check if error rate is concerning
            val errorRate = if (logCount > 0) {
                (errorCount.toDouble() / logCount.toDouble()) * 100
            } else {
                0.0
            }
            
            if (errorRate > 50.0) {
                return HealthStatus.unhealthy(
                    "High error rate: ${errorRate.toInt()}%",
                    HealthStatus.Severity.WARNING,
                    mapOf(
                        "logCount" to logCount,
                        "errorCount" to errorCount,
                        "errorRate" to errorRate
                    )
                )
            }
            
            // All healthy
            HealthStatus.healthy(
                "Logging system operational",
                mapOf(
                    "logCount" to logCount,
                    "errorCount" to errorCount,
                    "warningCount" to warningCount,
                    "errorRate" to errorRate,
                    "minLogLevel" to currentMinLogLevel.name
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
    
    // ========== Public API ==========
    
    /**
     * Update minimum log level at runtime
     */
    fun setMinLogLevel(level: LogLevel) {
        getLogger().info("Changing log level from $currentMinLogLevel to $level")
        currentMinLogLevel = level
    }
    
    /**
     * Record a log entry (for statistics)
     */
    fun recordLog(level: LogLevel) {
        logCount++
        when (level) {
            LogLevel.ERROR, LogLevel.ASSERT -> errorCount++
            LogLevel.WARN -> warningCount++
            else -> {} // No action for other levels
        }
    }
    
    /**
     * Get logging statistics
     */
    fun getStatistics(): LogStatistics {
        return LogStatistics(
            totalLogs = logCount,
            errorCount = errorCount,
            warningCount = warningCount,
            currentLevel = currentMinLogLevel
        )
    }
    
    /**
     * Logging statistics data class
     */
    data class LogStatistics(
        val totalLogs: Long,
        val errorCount: Long,
        val warningCount: Long,
        val currentLevel: LogLevel
    )
}
