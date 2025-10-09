package com.nfsp00f33r.app.alerts

import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.screens.health.HealthAlert
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * Custom alert rule definition
 * Phase 3 Days 7-8: Alert System
 */
@Serializable
data class AlertRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val ruleType: RuleType,
    val condition: RuleCondition
) {
    
    /**
     * Evaluate if alert matches this rule
     */
    fun evaluate(alert: HealthAlert): Boolean {
        if (!enabled) return false
        
        return when (ruleType) {
            RuleType.MODULE_SPECIFIC -> evaluateModuleRule(alert)
            RuleType.SEVERITY_BASED -> evaluateSeverityRule(alert)
            RuleType.TIME_BASED -> evaluateTimeRule(alert)
            RuleType.PATTERN_BASED -> evaluatePatternRule(alert)
        }
    }
    
    private fun evaluateModuleRule(alert: HealthAlert): Boolean {
        val moduleNames = condition.moduleNames ?: return true
        return alert.moduleName in moduleNames
    }
    
    private fun evaluateSeverityRule(alert: HealthAlert): Boolean {
        val minSeverity = condition.minSeverity ?: return true
        val alertLevel = severityToLevel(alert.severity)
        val minLevel = severityToLevel(minSeverity)
        return alertLevel >= minLevel
    }
    
    private fun evaluateTimeRule(alert: HealthAlert): Boolean {
        if (condition.timeWindowStart == null || condition.timeWindowEnd == null) {
            return true
        }
        
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        
        val startHour = condition.timeWindowStart
        val endHour = condition.timeWindowEnd
        
        return if (startHour < endHour) {
            currentHour in startHour until endHour
        } else {
            // Handle overnight window (e.g., 22:00 to 06:00)
            currentHour >= startHour || currentHour < endHour
        }
    }
    
    private fun evaluatePatternRule(alert: HealthAlert): Boolean {
        val pattern = condition.messagePattern ?: return true
        return alert.message.contains(pattern, ignoreCase = true)
    }
    
    private fun severityToLevel(severity: HealthStatus.Severity): Int {
        return when (severity) {
            HealthStatus.Severity.INFO -> 0
            HealthStatus.Severity.WARNING -> 1
            HealthStatus.Severity.ERROR -> 2
            HealthStatus.Severity.CRITICAL -> 3
        }
    }
}

/**
 * Rule types
 */
@Serializable
enum class RuleType {
    MODULE_SPECIFIC,  // Alert for specific modules only
    SEVERITY_BASED,   // Alert based on severity threshold
    TIME_BASED,       // Alert during specific time windows
    PATTERN_BASED     // Alert based on message patterns
}

/**
 * Rule condition parameters
 */
@Serializable
data class RuleCondition(
    val moduleNames: List<String>? = null,
    val minSeverity: HealthStatus.Severity? = null,
    val timeWindowStart: Int? = null,  // Hour (0-23)
    val timeWindowEnd: Int? = null,     // Hour (0-23)
    val messagePattern: String? = null
)

/**
 * Predefined alert rules
 */
object PredefinedRules {
    
    fun criticalOnly() = AlertRule(
        name = "Critical Only",
        ruleType = RuleType.SEVERITY_BASED,
        condition = RuleCondition(
            minSeverity = HealthStatus.Severity.CRITICAL
        )
    )
    
    fun businessHoursOnly() = AlertRule(
        name = "Business Hours Only (9 AM - 5 PM)",
        ruleType = RuleType.TIME_BASED,
        condition = RuleCondition(
            timeWindowStart = 9,
            timeWindowEnd = 17
        )
    )
    
    fun securityModulesOnly() = AlertRule(
        name = "Security Modules Only",
        ruleType = RuleType.MODULE_SPECIFIC,
        condition = RuleCondition(
            moduleNames = listOf("MasterPassword", "CardDataStore")
        )
    )
    
    fun errorAndCritical() = AlertRule(
        name = "Error & Critical",
        ruleType = RuleType.SEVERITY_BASED,
        condition = RuleCondition(
            minSeverity = HealthStatus.Severity.ERROR
        )
    )
    
    fun nightTimeAlerts() = AlertRule(
        name = "Night Time (10 PM - 6 AM)",
        ruleType = RuleType.TIME_BASED,
        condition = RuleCondition(
            timeWindowStart = 22,
            timeWindowEnd = 6
        )
    )
}
