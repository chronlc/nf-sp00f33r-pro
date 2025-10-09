package com.nfsp00f33r.app.alerts

import android.content.Context
import android.content.SharedPreferences
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.screens.health.HealthAlert
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Alert preferences storage
 * Phase 3 Days 7-8: Alert System
 */
class AlertPreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "health_alert_preferences",
        Context.MODE_PRIVATE
    )
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    companion object {
        private const val KEY_ALERTS_ENABLED = "alerts_enabled"
        private const val KEY_SEVERITY_THRESHOLD = "severity_threshold"
        private const val KEY_MODULE_ALERTS_PREFIX = "module_alert_"
        private const val KEY_CUSTOM_RULES = "custom_rules"
        private const val KEY_ALERT_HISTORY = "alert_history"
        private const val KEY_NOTIFICATION_SOUND = "notification_sound"
        private const val KEY_NOTIFICATION_VIBRATE = "notification_vibrate"
    }
    
    /**
     * Check if alerts are globally enabled
     */
    fun isAlertsEnabled(): Boolean {
        return prefs.getBoolean(KEY_ALERTS_ENABLED, true)
    }
    
    /**
     * Enable/disable alerts globally
     */
    fun setAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply()
    }
    
    /**
     * Get severity threshold for alerts
     */
    fun getAlertSeverityThreshold(): HealthStatus.Severity {
        val severityName = prefs.getString(KEY_SEVERITY_THRESHOLD, HealthStatus.Severity.WARNING.name)
        return try {
            HealthStatus.Severity.valueOf(severityName ?: HealthStatus.Severity.WARNING.name)
        } catch (e: IllegalArgumentException) {
            HealthStatus.Severity.WARNING
        }
    }
    
    /**
     * Set severity threshold for alerts
     */
    fun setAlertSeverityThreshold(severity: HealthStatus.Severity) {
        prefs.edit().putString(KEY_SEVERITY_THRESHOLD, severity.name).apply()
    }
    
    /**
     * Check if alerts are enabled for specific module
     */
    fun isModuleAlertEnabled(moduleName: String): Boolean {
        return prefs.getBoolean("$KEY_MODULE_ALERTS_PREFIX$moduleName", true)
    }
    
    /**
     * Enable/disable alerts for specific module
     */
    fun setModuleAlertEnabled(moduleName: String, enabled: Boolean) {
        prefs.edit().putBoolean("$KEY_MODULE_ALERTS_PREFIX$moduleName", enabled).apply()
    }
    
    /**
     * Get all module alert settings
     */
    fun getAllModuleAlertSettings(): Map<String, Boolean> {
        val modules = listOf(
            "CardDataStore", "Logging", "PN532Device",
            "MasterPassword", "NfcHce", "Emulation"
        )
        
        return modules.associateWith { isModuleAlertEnabled(it) }
    }
    
    /**
     * Get custom alert rules
     */
    fun getCustomRules(): List<AlertRule> {
        val rulesJson = prefs.getString(KEY_CUSTOM_RULES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<AlertRule>>(rulesJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Save custom alert rules
     */
    fun saveCustomRules(rules: List<AlertRule>) {
        val rulesJson = json.encodeToString(rules)
        prefs.edit().putString(KEY_CUSTOM_RULES, rulesJson).apply()
    }
    
    /**
     * Add custom rule
     */
    fun addCustomRule(rule: AlertRule) {
        val rules = getCustomRules().toMutableList()
        rules.add(rule)
        saveCustomRules(rules)
    }
    
    /**
     * Remove custom rule
     */
    fun removeCustomRule(ruleId: String) {
        val rules = getCustomRules().filter { it.id != ruleId }
        saveCustomRules(rules)
    }
    
    /**
     * Get alert history
     */
    fun getAlertHistory(): List<HealthAlert> {
        val historyJson = prefs.getString(KEY_ALERT_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<HealthAlert>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Save alert history
     */
    fun saveAlertHistory(history: List<HealthAlert>) {
        val historyJson = json.encodeToString(history)
        prefs.edit().putString(KEY_ALERT_HISTORY, historyJson).apply()
    }
    
    /**
     * Clear alert history
     */
    fun clearAlertHistory() {
        prefs.edit().remove(KEY_ALERT_HISTORY).apply()
    }
    
    /**
     * Check if notification sound is enabled
     */
    fun isNotificationSoundEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_SOUND, true)
    }
    
    /**
     * Enable/disable notification sound
     */
    fun setNotificationSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_SOUND, enabled).apply()
    }
    
    /**
     * Check if notification vibration is enabled
     */
    fun isNotificationVibrateEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_VIBRATE, true)
    }
    
    /**
     * Enable/disable notification vibration
     */
    fun setNotificationVibrateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_VIBRATE, enabled).apply()
    }
    
    /**
     * Reset all preferences to defaults
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
