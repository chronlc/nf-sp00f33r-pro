package com.nfsp00f33r.app.core

import android.util.Log

/**
 * FrameworkLogger - Phase 2A Day 1
 * 
 * Centralized logging system for the framework
 * Provides consistent logging across all modules with proper formatting
 * 
 * Replaces scattered Log.d(), Log.e(), Timber.d() calls with unified interface
 * Can be configured for different log levels and outputs
 */
object FrameworkLogger {
    
    private const val TAG = "NfSp00f33r"
    
    /**
     * Log levels
     */
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
    
    /**
     * Current minimum log level
     * Messages below this level will be ignored
     */
    var minLevel: Level = Level.DEBUG
    
    /**
     * Enable/disable logging globally
     */
    var enabled: Boolean = true
    
    /**
     * Log listeners for custom handling
     */
    private val listeners = mutableListOf<LogListener>()
    
    /**
     * Log a verbose message
     */
    fun verbose(message: String, tag: String = TAG) {
        log(Level.VERBOSE, message, null, tag)
    }
    
    /**
     * Log a debug message
     */
    fun debug(message: String, tag: String = TAG) {
        log(Level.DEBUG, message, null, tag)
    }
    
    /**
     * Log an info message
     */
    fun info(message: String, tag: String = TAG) {
        log(Level.INFO, message, null, tag)
    }
    
    /**
     * Log a warning message
     */
    fun warn(message: String, throwable: Throwable? = null, tag: String = TAG) {
        log(Level.WARN, message, throwable, tag)
    }
    
    /**
     * Log an error message
     */
    fun error(message: String, throwable: Throwable? = null, tag: String = TAG) {
        log(Level.ERROR, message, throwable, tag)
    }
    
    /**
     * Core logging function
     */
    private fun log(level: Level, message: String, throwable: Throwable?, tag: String) {
        if (!enabled) return
        if (level.ordinal < minLevel.ordinal) return
        
        // Format message with timestamp
        val formattedMessage = formatMessage(message)
        
        // Log to Android logcat
        when (level) {
            Level.VERBOSE -> Log.v(tag, formattedMessage, throwable)
            Level.DEBUG -> Log.d(tag, formattedMessage, throwable)
            Level.INFO -> Log.i(tag, formattedMessage, throwable)
            Level.WARN -> Log.w(tag, formattedMessage, throwable)
            Level.ERROR -> Log.e(tag, formattedMessage, throwable)
        }
        
        // Notify listeners
        notifyListeners(LogEntry(level, message, throwable, tag, System.currentTimeMillis()))
    }
    
    /**
     * Format log message with metadata
     */
    private fun formatMessage(message: String): String {
        val threadName = Thread.currentThread().name
        return "[$threadName] $message"
    }
    
    /**
     * Add log listener
     */
    fun addListener(listener: LogListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove log listener
     */
    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }
    
    /**
     * Notify all listeners
     */
    private fun notifyListeners(entry: LogEntry) {
        listeners.forEach { listener ->
            try {
                listener.onLog(entry)
            } catch (e: Exception) {
                // Don't log errors in logging system to avoid infinite loop
                Log.e(TAG, "Error in log listener", e)
            }
        }
    }
    
    /**
     * Log entry data class
     */
    data class LogEntry(
        val level: Level,
        val message: String,
        val throwable: Throwable?,
        val tag: String,
        val timestamp: Long
    )
    
    /**
     * Log listener interface
     */
    interface LogListener {
        fun onLog(entry: LogEntry)
    }
    
    /**
     * Module-specific logger
     * Creates a logger that automatically prefixes messages with module name
     */
    class ModuleLogger(private val moduleName: String) {
        fun verbose(message: String) = FrameworkLogger.verbose("$moduleName: $message")
        fun debug(message: String) = FrameworkLogger.debug("$moduleName: $message")
        fun info(message: String) = FrameworkLogger.info("$moduleName: $message")
        fun warn(message: String, throwable: Throwable? = null) = 
            FrameworkLogger.warn("$moduleName: $message", throwable)
        fun error(message: String, throwable: Throwable? = null) = 
            FrameworkLogger.error("$moduleName: $message", throwable)
    }
    
    /**
     * Create module-specific logger
     */
    fun forModule(moduleName: String): ModuleLogger {
        return ModuleLogger(moduleName)
    }
}

/**
 * Extension functions for easy module logging
 */
fun Module.getLogger(): FrameworkLogger.ModuleLogger {
    return FrameworkLogger.forModule(this.name)
}
