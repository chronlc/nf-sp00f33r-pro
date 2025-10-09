package com.nfsp00f33r.app.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nfsp00f33r.app.R
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.screens.health.HealthAlert
import java.text.SimpleDateFormat
import java.util.*

/**
 * Health Alert Manager
 * Phase 3 Days 7-8: Alert System & Notifications
 * 
 * Manages health alerts, notifications, and alert rules
 */
class HealthAlertManager(private val context: Context) {
    
    private val notificationManager = NotificationManagerCompat.from(context)
    private val preferences = AlertPreferences(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    companion object {
        private const val CHANNEL_ID_CRITICAL = "health_alerts_critical"
        private const val CHANNEL_ID_ERROR = "health_alerts_error"
        private const val CHANNEL_ID_WARNING = "health_alerts_warning"
        
        private const val NOTIFICATION_ID_BASE = 10000
    }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Create notification channels for different alert severities
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Critical channel (highest priority)
            val criticalChannel = NotificationChannel(
                CHANNEL_ID_CRITICAL,
                "Critical Health Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical module health issues requiring immediate attention"
                enableVibration(true)
                enableLights(true)
            }
            
            // Error channel (high priority)
            val errorChannel = NotificationChannel(
                CHANNEL_ID_ERROR,
                "Error Health Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Module errors affecting functionality"
                enableVibration(true)
            }
            
            // Warning channel (normal priority)
            val warningChannel = NotificationChannel(
                CHANNEL_ID_WARNING,
                "Warning Health Alerts",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Module warnings and minor issues"
            }
            
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(criticalChannel)
            manager.createNotificationChannel(errorChannel)
            manager.createNotificationChannel(warningChannel)
        }
    }
    
    /**
     * Process health alert and show notification if enabled
     */
    fun processAlert(alert: HealthAlert) {
        // Check if alerts are enabled globally
        if (!preferences.isAlertsEnabled()) {
            return
        }
        
        // Check if module-specific alerts are enabled
        if (!preferences.isModuleAlertEnabled(alert.moduleName)) {
            return
        }
        
        // Check severity threshold
        if (!isSeverityAboveThreshold(alert.severity)) {
            return
        }
        
        // Evaluate custom rules
        if (!evaluateCustomRules(alert)) {
            return
        }
        
        // Save to alert history
        saveAlertToHistory(alert)
        
        // Show notification
        showNotification(alert)
    }
    
    /**
     * Check if severity is above user-configured threshold
     */
    private fun isSeverityAboveThreshold(severity: HealthStatus.Severity): Boolean {
        val threshold = preferences.getAlertSeverityThreshold()
        val severityLevel = when (severity) {
            HealthStatus.Severity.INFO -> 0
            HealthStatus.Severity.WARNING -> 1
            HealthStatus.Severity.ERROR -> 2
            HealthStatus.Severity.CRITICAL -> 3
        }
        
        val thresholdLevel = when (threshold) {
            HealthStatus.Severity.INFO -> 0
            HealthStatus.Severity.WARNING -> 1
            HealthStatus.Severity.ERROR -> 2
            HealthStatus.Severity.CRITICAL -> 3
        }
        
        return severityLevel >= thresholdLevel
    }
    
    /**
     * Evaluate custom alert rules
     */
    private fun evaluateCustomRules(alert: HealthAlert): Boolean {
        val rules = preferences.getCustomRules()
        
        if (rules.isEmpty()) {
            return true // No custom rules, allow alert
        }
        
        // Evaluate each rule
        return rules.any { rule ->
            rule.evaluate(alert)
        }
    }
    
    /**
     * Save alert to history
     */
    private fun saveAlertToHistory(alert: HealthAlert) {
        val history = preferences.getAlertHistory().toMutableList()
        history.add(0, alert) // Add to beginning
        
        // Keep last 500 alerts
        val trimmedHistory = history.take(500)
        preferences.saveAlertHistory(trimmedHistory)
    }
    
    /**
     * Show notification for health alert
     */
    private fun showNotification(alert: HealthAlert) {
        val channelId = when (alert.severity) {
            HealthStatus.Severity.CRITICAL -> CHANNEL_ID_CRITICAL
            HealthStatus.Severity.ERROR -> CHANNEL_ID_ERROR
            else -> CHANNEL_ID_WARNING
        }
        
        val priority = when (alert.severity) {
            HealthStatus.Severity.CRITICAL -> NotificationCompat.PRIORITY_HIGH
            HealthStatus.Severity.ERROR -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
        
        // Create intent to open app (you can customize this to open specific screen)
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("${alert.moduleName} ${alert.severity.name}")
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setWhen(System.currentTimeMillis())
            .build()
        
        val notificationId = NOTIFICATION_ID_BASE + alert.id.hashCode()
        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * Clear all notifications
     */
    fun clearAllNotifications() {
        notificationManager.cancelAll()
    }
    
    /**
     * Get alert history
     */
    fun getAlertHistory(): List<HealthAlert> {
        return preferences.getAlertHistory()
    }
    
    /**
     * Clear alert history
     */
    fun clearAlertHistory() {
        preferences.clearAlertHistory()
    }
    
    /**
     * Get alert statistics
     */
    fun getAlertStatistics(): AlertStatistics {
        val history = getAlertHistory()
        
        return AlertStatistics(
            totalAlerts = history.size,
            criticalCount = history.count { it.severity == HealthStatus.Severity.CRITICAL },
            errorCount = history.count { it.severity == HealthStatus.Severity.ERROR },
            warningCount = history.count { it.severity == HealthStatus.Severity.WARNING },
            last24HoursCount = history.count { alert ->
                val alertTime = try {
                    dateFormat.parse(alert.timestamp)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
                System.currentTimeMillis() - alertTime < 24 * 60 * 60 * 1000
            }
        )
    }
}

/**
 * Alert statistics data
 */
data class AlertStatistics(
    val totalAlerts: Int,
    val criticalCount: Int,
    val errorCount: Int,
    val warningCount: Int,
    val last24HoursCount: Int
)
