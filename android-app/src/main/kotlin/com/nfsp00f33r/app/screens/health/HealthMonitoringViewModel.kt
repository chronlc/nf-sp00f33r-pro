package com.nfsp00f33r.app.screens.health

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.core.ModuleRegistry
import com.nfsp00f33r.app.core.ModuleState
import com.nfsp00f33r.app.data.health.HealthHistoryEntry
import com.nfsp00f33r.app.data.health.HealthHistoryRepository
import com.nfsp00f33r.app.alerts.HealthAlertManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for Health Monitoring UI
 * Provides real-time module health status, statistics, and control operations
 * 
 * Phase 3 Days 5-6: Added persistent storage with Room DB
 * Phase 3 Days 7-8: Added alert system with notifications
 */
class HealthMonitoringViewModel(context: Context) : ViewModel() {
    
    // Repository for persistent storage
    private val healthRepository = HealthHistoryRepository(context)
    
    // Alert manager for notifications (Phase 3 Days 7-8)
    private val alertManager = HealthAlertManager(context)
    
    // Store context for later use
    private val appContext = context.applicationContext

    // Individual module health states
    private val _cardDataStoreHealth = MutableStateFlow<ModuleHealthState>(ModuleHealthState.Loading)
    val cardDataStoreHealth: StateFlow<ModuleHealthState> = _cardDataStoreHealth.asStateFlow()

    private val _loggingHealth = MutableStateFlow<ModuleHealthState>(ModuleHealthState.Loading)
    val loggingHealth: StateFlow<ModuleHealthState> = _loggingHealth.asStateFlow()

    private val _pn532DeviceHealth = MutableStateFlow<ModuleHealthState>(ModuleHealthState.Loading)
    val pn532DeviceHealth: StateFlow<ModuleHealthState> = _pn532DeviceHealth.asStateFlow()

    private val _passwordHealth = MutableStateFlow<ModuleHealthState>(ModuleHealthState.Loading)
    val passwordHealth: StateFlow<ModuleHealthState> = _passwordHealth.asStateFlow()

    private val _nfcHceHealth = MutableStateFlow<ModuleHealthState>(ModuleHealthState.Loading)
    val nfcHceHealth: StateFlow<ModuleHealthState> = _nfcHceHealth.asStateFlow()

    private val _emulationHealth = MutableStateFlow<ModuleHealthState>(ModuleHealthState.Loading)
    val emulationHealth: StateFlow<ModuleHealthState> = _emulationHealth.asStateFlow()

    // Overall system health
    private val _overallHealth = MutableStateFlow<SystemHealthState>(SystemHealthState.Loading)
    val overallHealth: StateFlow<SystemHealthState> = _overallHealth.asStateFlow()

    // Health history
    private val _healthHistory = MutableStateFlow<List<HealthHistoryEntry>>(emptyList())
    val healthHistory: StateFlow<List<HealthHistoryEntry>> = _healthHistory.asStateFlow()

    // Alert state
    private val _alerts = MutableStateFlow<List<HealthAlert>>(emptyList())
    val alerts: StateFlow<List<HealthAlert>> = _alerts.asStateFlow()

    // Auto-refresh control
    private val _autoRefreshEnabled = MutableStateFlow(true)
    val autoRefreshEnabled: StateFlow<Boolean> = _autoRefreshEnabled.asStateFlow()

    private var refreshJob: Job? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        startAutoRefresh()
    }

    /**
     * Start automatic health refresh (every 5 seconds)
     */
    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (_autoRefreshEnabled.value) {
                refreshAllHealth()
                delay(5000) // 5 second refresh interval
            }
        }
    }

    /**
     * Toggle auto-refresh on/off
     */
    fun toggleAutoRefresh() {
        _autoRefreshEnabled.value = !_autoRefreshEnabled.value
        if (_autoRefreshEnabled.value) {
            startAutoRefresh()
        } else {
            refreshJob?.cancel()
        }
    }

    /**
     * Manually refresh all module health statuses
     */
    fun refreshAllHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modules = ModuleRegistry.getAllModules()
                val healthStatuses = mutableListOf<Pair<String, HealthStatus>>()

                // Collect health from all modules
                modules.forEach { module ->
                    val name = module.name
                    val health = try {
                        module.checkHealth()
                    } catch (e: Exception) {
                        HealthStatus.unhealthy(
                            "Health check failed: ${e.message}",
                            HealthStatus.Severity.CRITICAL
                        )
                    }

                    healthStatuses.add(name to health)

                    // Update individual module states
                    val state = ModuleHealthState.Healthy(
                        moduleName = name,
                        status = health,
                        state = module.state,
                        lastChecked = getCurrentTimestamp()
                    )

                    when (name) {
                        "CardDataStore" -> _cardDataStoreHealth.value = state
                        "Logging" -> _loggingHealth.value = state
                        "PN532Device" -> _pn532DeviceHealth.value = state
                        "MasterPassword" -> _passwordHealth.value = state
                        "NfcHce" -> _nfcHceHealth.value = state
                        "Emulation" -> _emulationHealth.value = state
                    }
                }

                // Calculate overall health
                updateOverallHealth(healthStatuses)

                // Record health history
                recordHealthHistory(healthStatuses)

                // Generate alerts for critical issues
                generateAlerts(healthStatuses)

            } catch (e: Exception) {
                _overallHealth.value = SystemHealthState.Error("Failed to refresh health: ${e.message}")
            }
        }
    }

    /**
     * Update overall system health based on all modules
     */
    private fun updateOverallHealth(healthStatuses: List<Pair<String, HealthStatus>>) {
        val criticalCount = healthStatuses.count { it.second.severity == HealthStatus.Severity.CRITICAL }
        val errorCount = healthStatuses.count { it.second.severity == HealthStatus.Severity.ERROR }
        val warningCount = healthStatuses.count { it.second.severity == HealthStatus.Severity.WARNING }
        val healthyCount = healthStatuses.count { it.second.isHealthy }

        val overallSeverity = when {
            criticalCount > 0 -> HealthStatus.Severity.CRITICAL
            errorCount > 0 -> HealthStatus.Severity.ERROR
            warningCount > 0 -> HealthStatus.Severity.WARNING
            else -> HealthStatus.Severity.INFO
        }

        val summary = buildString {
            append("${healthStatuses.size} modules: ")
            if (healthyCount > 0) append("$healthyCount healthy ")
            if (warningCount > 0) append("$warningCount warning ")
            if (errorCount > 0) append("$errorCount error ")
            if (criticalCount > 0) append("$criticalCount critical")
        }.trim()

        _overallHealth.value = SystemHealthState.Loaded(
            severity = overallSeverity,
            summary = summary,
            totalModules = healthStatuses.size,
            healthyModules = healthyCount,
            warningModules = warningCount,
            errorModules = errorCount,
            criticalModules = criticalCount,
            lastUpdated = getCurrentTimestamp()
        )
    }

    /**
     * Record health snapshot to history (keep last 100 entries in memory + save to DB)
     * Phase 3 Days 5-6: Added persistent storage
     */
    private fun recordHealthHistory(healthStatuses: List<Pair<String, HealthStatus>>) {
        val timestamp = getCurrentTimestamp()
        val statusMap = healthStatuses.toMap()
        
        // Update in-memory history (for quick access)
        val newEntry = HealthHistoryEntry(
            timestamp = timestamp,
            moduleStatuses = statusMap
        )
        val updatedHistory = (_healthHistory.value + newEntry).takeLast(100)
        _healthHistory.value = updatedHistory
        
        // Save to database (async)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val overallSeverity = calculateOverallSeverity(statusMap.values.toList())
                healthRepository.saveHealthSnapshot(statusMap, overallSeverity)
            } catch (e: Exception) {
                // Log error but don't crash
                android.util.Log.e("HealthMonitoringVM", "Failed to save health history to DB", e)
            }
        }
    }
    
    /**
     * Calculate overall severity from health statuses
     */
    private fun calculateOverallSeverity(statuses: List<HealthStatus>): HealthStatus.Severity {
        return when {
            statuses.any { it.severity == HealthStatus.Severity.CRITICAL } -> HealthStatus.Severity.CRITICAL
            statuses.any { it.severity == HealthStatus.Severity.ERROR } -> HealthStatus.Severity.ERROR
            statuses.any { it.severity == HealthStatus.Severity.WARNING } -> HealthStatus.Severity.WARNING
            else -> HealthStatus.Severity.INFO
        }
    }
    
    /**
     * Load health history from database
     * Phase 3 Days 5-6
     */
    fun loadHealthHistoryFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbHistory = healthRepository.getLastNEntries(100)
                _healthHistory.value = dbHistory
            } catch (e: Exception) {
                android.util.Log.e("HealthMonitoringVM", "Failed to load health history from DB", e)
            }
        }
    }

    /**
     * Generate alerts for critical/error severity modules
     * Phase 3 Days 7-8: Enhanced with notification system
     */
    private fun generateAlerts(healthStatuses: List<Pair<String, HealthStatus>>) {
        val newAlerts = healthStatuses
            .filter { it.second.severity in listOf(HealthStatus.Severity.CRITICAL, HealthStatus.Severity.ERROR) }
            .map { (name, status) ->
                HealthAlert(
                    id = UUID.randomUUID().toString(),
                    timestamp = getCurrentTimestamp(),
                    moduleName = name,
                    severity = status.severity,
                    message = status.message
                )
            }

        // Process alerts through alert manager (Phase 3 Days 7-8)
        newAlerts.forEach { alert ->
            alertManager.processAlert(alert)
        }

        // Keep last 50 alerts in memory
        val updatedAlerts = (_alerts.value + newAlerts).takeLast(50)
        _alerts.value = updatedAlerts
    }
    
    /**
     * Get application context (for UI screens to access alert manager)
     * Phase 3 Days 7-8
     */
    fun getContext(): Context = appContext

    /**
     * Start a specific module
     */
    fun startModule(moduleName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val module = ModuleRegistry.getModule(moduleName)
                module?.let {
                    if (it.state != ModuleState.RUNNING) {
                        it.initialize()
                    }
                }
                refreshAllHealth()
            } catch (e: Exception) {
                // Error handling - could emit error state
            }
        }
    }

    /**
     * Stop a specific module
     */
    fun stopModule(moduleName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val module = ModuleRegistry.getModule(moduleName)
                module?.let {
                    if (it.state == ModuleState.RUNNING) {
                        it.shutdown()
                    }
                }
                refreshAllHealth()
            } catch (e: Exception) {
                // Error handling
            }
        }
    }

    /**
     * Restart a specific module
     */
    fun restartModule(moduleName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val module = ModuleRegistry.getModule(moduleName)
                module?.let {
                    if (it.state == ModuleState.RUNNING) {
                        it.shutdown()
                    }
                    delay(500) // Brief pause
                    it.initialize()
                }
                refreshAllHealth()
            } catch (e: Exception) {
                // Error handling
            }
        }
    }

    /**
     * Clear all alerts
     */
    fun clearAlerts() {
        _alerts.value = emptyList()
    }

    /**
     * Clear health history
     */
    fun clearHistory() {
        _healthHistory.value = emptyList()
    }

    /**
     * Analyze health trends over history
     * Detects if module health is improving, stable, or degrading
     */
    fun analyzeHealthTrends(): Map<String, TrendAnalysis> {
        val history = _healthHistory.value
        if (history.size < 3) {
            return emptyMap() // Need at least 3 data points for trend analysis
        }

        val trends = mutableMapOf<String, TrendAnalysis>()
        val modules = listOf(
            "CardDataStore", "Logging", "PN532Device", 
            "MasterPassword", "NfcHce", "Emulation"
        )

        for (moduleName in modules) {
            val moduleHistory = history.mapNotNull { entry ->
                entry.moduleStatuses[moduleName]
            }

            if (moduleHistory.size < 3) continue

            // Analyze last 10 entries (or all if less than 10)
            val recentHistory = moduleHistory.takeLast(10)
            
            // Calculate severity scores (higher = worse)
            val severityScores = recentHistory.map { status ->
                when (status.severity) {
                    HealthStatus.Severity.INFO -> 0
                    HealthStatus.Severity.WARNING -> 1
                    HealthStatus.Severity.ERROR -> 2
                    HealthStatus.Severity.CRITICAL -> 3
                }
            }

            // Calculate trend
            val avgRecent = severityScores.takeLast(3).average()
            val avgOlder = severityScores.take(severityScores.size - 3).average()
            
            val trend = when {
                avgRecent < avgOlder -> HealthTrend.IMPROVING
                avgRecent > avgOlder -> HealthTrend.DEGRADING
                else -> HealthTrend.STABLE
            }

            // Count issues
            val criticalCount = recentHistory.count { it.severity == HealthStatus.Severity.CRITICAL }
            val errorCount = recentHistory.count { it.severity == HealthStatus.Severity.ERROR }
            val warningCount = recentHistory.count { it.severity == HealthStatus.Severity.WARNING }

            trends[moduleName] = TrendAnalysis(
                moduleName = moduleName,
                trend = trend,
                recentAvgSeverity = avgRecent,
                olderAvgSeverity = avgOlder,
                criticalCount = criticalCount,
                errorCount = errorCount,
                warningCount = warningCount,
                dataPoints = recentHistory.size,
                message = generateTrendMessage(trend, avgRecent, avgOlder)
            )
        }

        return trends
    }

    /**
     * Generate human-readable trend message
     */
    private fun generateTrendMessage(
        trend: HealthTrend,
        recentAvg: Double,
        olderAvg: Double
    ): String {
        return when (trend) {
            HealthTrend.IMPROVING -> "Health improving (severity decreased from ${olderAvg.format(2)} to ${recentAvg.format(2)})"
            HealthTrend.DEGRADING -> "Health degrading (severity increased from ${olderAvg.format(2)} to ${recentAvg.format(2)})"
            HealthTrend.STABLE -> "Health stable (severity ~${recentAvg.format(2)})"
        }
    }

    /**
     * Detect health degradation for specific module
     * Returns true if module health is getting worse
     */
    fun detectHealthDegradation(moduleName: String): Boolean {
        val trends = analyzeHealthTrends()
        return trends[moduleName]?.trend == HealthTrend.DEGRADING
    }

    /**
     * Get health trend for specific module
     */
    fun getModuleHealthTrend(moduleName: String): HealthTrend? {
        return analyzeHealthTrends()[moduleName]?.trend
    }

    /**
     * Export health report as JSON string
     * Includes current status, history, alerts, and trend analysis
     */
    fun exportHealthReport(): String {
        try {
            val report = buildString {
                appendLine("{")
                appendLine("  \"timestamp\": \"${getCurrentTimestamp()}\",")
                appendLine("  \"reportType\": \"Health Monitoring Report\",")
                appendLine("  \"version\": \"1.0\",")
                
                // Overall health
                appendLine("  \"overallHealth\": {")
                val overall = _overallHealth.value
                if (overall is SystemHealthState.Loaded) {
                    appendLine("    \"severity\": \"${overall.severity}\",")
                    appendLine("    \"summary\": \"${overall.summary}\",")
                    appendLine("    \"totalModules\": ${overall.totalModules},")
                    appendLine("    \"healthyModules\": ${overall.healthyModules},")
                    appendLine("    \"warningModules\": ${overall.warningModules},")
                    appendLine("    \"errorModules\": ${overall.errorModules},")
                    appendLine("    \"criticalModules\": ${overall.criticalModules}")
                }
                appendLine("  },")
                
                // Module statuses
                appendLine("  \"modules\": [")
                val moduleStates = listOf(
                    _cardDataStoreHealth.value,
                    _loggingHealth.value,
                    _pn532DeviceHealth.value,
                    _passwordHealth.value,
                    _nfcHceHealth.value,
                    _emulationHealth.value
                )
                
                moduleStates.filterIsInstance<ModuleHealthState.Healthy>().forEachIndexed { index, state ->
                    appendLine("    {")
                    appendLine("      \"name\": \"${state.moduleName}\",")
                    appendLine("      \"state\": \"${state.state}\",")
                    appendLine("      \"healthy\": ${state.status.isHealthy},")
                    appendLine("      \"severity\": \"${state.status.severity}\",")
                    appendLine("      \"message\": \"${state.status.message.replace("\"", "\\\"")}\",")
                    appendLine("      \"lastChecked\": \"${state.lastChecked}\"")
                    append("    }")
                    if (index < moduleStates.filterIsInstance<ModuleHealthState.Healthy>().size - 1) {
                        appendLine(",")
                    } else {
                        appendLine()
                    }
                }
                appendLine("  ],")
                
                // Alerts
                appendLine("  \"alerts\": [")
                _alerts.value.forEachIndexed { index, alert ->
                    appendLine("    {")
                    appendLine("      \"id\": \"${alert.id}\",")
                    appendLine("      \"timestamp\": \"${alert.timestamp}\",")
                    appendLine("      \"moduleName\": \"${alert.moduleName}\",")
                    appendLine("      \"severity\": \"${alert.severity}\",")
                    appendLine("      \"message\": \"${alert.message.replace("\"", "\\\"")}\"")
                    append("    }")
                    if (index < _alerts.value.size - 1) {
                        appendLine(",")
                    } else {
                        appendLine()
                    }
                }
                appendLine("  ],")
                
                // Trend analysis
                appendLine("  \"trends\": [")
                val trends = analyzeHealthTrends()
                trends.entries.forEachIndexed { index, (name, analysis) ->
                    appendLine("    {")
                    appendLine("      \"moduleName\": \"$name\",")
                    appendLine("      \"trend\": \"${analysis.trend}\",")
                    appendLine("      \"recentAvgSeverity\": ${analysis.recentAvgSeverity},")
                    appendLine("      \"olderAvgSeverity\": ${analysis.olderAvgSeverity},")
                    appendLine("      \"criticalCount\": ${analysis.criticalCount},")
                    appendLine("      \"errorCount\": ${analysis.errorCount},")
                    appendLine("      \"warningCount\": ${analysis.warningCount},")
                    appendLine("      \"dataPoints\": ${analysis.dataPoints},")
                    appendLine("      \"message\": \"${analysis.message.replace("\"", "\\\"")}\"")
                    append("    }")
                    if (index < trends.size - 1) {
                        appendLine(",")
                    } else {
                        appendLine()
                    }
                }
                appendLine("  ],")
                
                // History summary
                appendLine("  \"history\": {")
                appendLine("    \"totalEntries\": ${_healthHistory.value.size},")
                appendLine("    \"oldestEntry\": \"${_healthHistory.value.firstOrNull()?.timestamp ?: "N/A"}\",")
                appendLine("    \"newestEntry\": \"${_healthHistory.value.lastOrNull()?.timestamp ?: "N/A"}\"")
                appendLine("  }")
                
                appendLine("}")
            }
            
            return report
        } catch (e: Exception) {
            return "{\"error\": \"Failed to export health report: ${e.message}\"}"
        }
    }

    /**
     * Get health statistics summary
     */
    fun getHealthStatistics(): HealthStatistics {
        val history = _healthHistory.value
        val alerts = _alerts.value
        val trends = analyzeHealthTrends()

        val criticalAlerts = alerts.count { it.severity == HealthStatus.Severity.CRITICAL }
        val errorAlerts = alerts.count { it.severity == HealthStatus.Severity.ERROR }
        
        val degradingModules = trends.values.count { it.trend == HealthTrend.DEGRADING }
        val improvingModules = trends.values.count { it.trend == HealthTrend.IMPROVING }

        return HealthStatistics(
            totalHealthChecks = history.size,
            totalAlerts = alerts.size,
            criticalAlerts = criticalAlerts,
            errorAlerts = errorAlerts,
            degradingModules = degradingModules,
            improvingModules = improvingModules,
            stableModules = trends.size - degradingModules - improvingModules
        )
    }

    private fun getCurrentTimestamp(): String {
        return dateFormat.format(Date())
    }

    private fun Double.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}

/**
 * Sealed class representing module health state
 */
sealed class ModuleHealthState {
    object Loading : ModuleHealthState()
    data class Healthy(
        val moduleName: String,
        val status: HealthStatus,
        val state: ModuleState,
        val lastChecked: String
    ) : ModuleHealthState()
    data class Error(val message: String) : ModuleHealthState()
}

/**
 * Sealed class representing overall system health state
 */
sealed class SystemHealthState {
    object Loading : SystemHealthState()
    data class Loaded(
        val severity: HealthStatus.Severity,
        val summary: String,
        val totalModules: Int,
        val healthyModules: Int,
        val warningModules: Int,
        val errorModules: Int,
        val criticalModules: Int,
        val lastUpdated: String
    ) : SystemHealthState()
    data class Error(val message: String) : SystemHealthState()
}

/**
 * Health alert for critical/error conditions
 */
data class HealthAlert(
    val id: String,
    val timestamp: String,
    val moduleName: String,
    val severity: HealthStatus.Severity,
    val message: String
)

/**
 * Health trend enumeration
 */
enum class HealthTrend {
    IMPROVING,  // Module health is getting better
    STABLE,     // Module health is consistent
    DEGRADING   // Module health is getting worse
}

/**
 * Trend analysis data
 */
data class TrendAnalysis(
    val moduleName: String,
    val trend: HealthTrend,
    val recentAvgSeverity: Double,
    val olderAvgSeverity: Double,
    val criticalCount: Int,
    val errorCount: Int,
    val warningCount: Int,
    val dataPoints: Int,
    val message: String
)

/**
 * Health statistics summary
 */
data class HealthStatistics(
    val totalHealthChecks: Int,
    val totalAlerts: Int,
    val criticalAlerts: Int,
    val errorAlerts: Int,
    val degradingModules: Int,
    val improvingModules: Int,
    val stableModules: Int
)
