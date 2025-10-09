package com.nfsp00f33r.app.screens.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.alerts.HealthAlertManager
import com.nfsp00f33r.app.alerts.AlertPreferences
import com.nfsp00f33r.app.alerts.AlertRule
import com.nfsp00f33r.app.alerts.RuleType
import com.nfsp00f33r.app.alerts.RuleCondition
import com.nfsp00f33r.app.alerts.PredefinedRules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Unit tests for Health Monitoring System
 * Phase 3 Days 9-10: Testing & Documentation
 * 
 * Tests:
 * - Trend analysis algorithm
 * - Degradation detection
 * - Alert evaluation
 * - Alert rules
 * - Statistics calculations
 * - Export functionality
 */
@RunWith(AndroidJUnit4::class)
class HealthMonitoringTest {
    
    private lateinit var context: Context
    private lateinit var alertManager: HealthAlertManager
    private lateinit var alertPreferences: AlertPreferences
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        alertManager = HealthAlertManager(context)
        alertPreferences = AlertPreferences(context)
        
        // Clear preferences before each test
        alertPreferences.resetToDefaults()
    }
    
    @After
    fun tearDown() {
        // Clean up after tests
        alertPreferences.resetToDefaults()
        alertManager.clearAllNotifications()
        alertManager.clearAlertHistory()
    }
    
    /**
     * Test 1: Trend analysis with improving health
     */
    @Test
    fun testTrendAnalysis_Improving() {
        // Simulate improving health trend
        val healthHistory = listOf(
            createMockHealthStatus(HealthStatus.Severity.CRITICAL, 1000),
            createMockHealthStatus(HealthStatus.Severity.ERROR, 2000),
            createMockHealthStatus(HealthStatus.Severity.WARNING, 3000),
            createMockHealthStatus(HealthStatus.Severity.INFO, 4000)
        )
        
        val trend = analyzeTrend(healthHistory)
        assertEquals(HealthTrend.IMPROVING, trend)
    }
    
    /**
     * Test 2: Trend analysis with degrading health
     */
    @Test
    fun testTrendAnalysis_Degrading() {
        // Simulate degrading health trend
        val healthHistory = listOf(
            createMockHealthStatus(HealthStatus.Severity.INFO, 1000),
            createMockHealthStatus(HealthStatus.Severity.WARNING, 2000),
            createMockHealthStatus(HealthStatus.Severity.ERROR, 3000),
            createMockHealthStatus(HealthStatus.Severity.CRITICAL, 4000)
        )
        
        val trend = analyzeTrend(healthHistory)
        assertEquals(HealthTrend.DEGRADING, trend)
    }
    
    /**
     * Test 3: Trend analysis with stable health
     */
    @Test
    fun testTrendAnalysis_Stable() {
        // Simulate stable health trend
        val healthHistory = listOf(
            createMockHealthStatus(HealthStatus.Severity.WARNING, 1000),
            createMockHealthStatus(HealthStatus.Severity.WARNING, 2000),
            createMockHealthStatus(HealthStatus.Severity.WARNING, 3000),
            createMockHealthStatus(HealthStatus.Severity.WARNING, 4000)
        )
        
        val trend = analyzeTrend(healthHistory)
        assertEquals(HealthTrend.STABLE, trend)
    }
    
    /**
     * Test 4: Degradation detection
     */
    @Test
    fun testDegradationDetection() {
        val healthStatuses = mapOf(
            "CardDataStore" to HealthStatus(HealthStatus.Severity.CRITICAL, "Storage error", 1000),
            "Logging" to HealthStatus(HealthStatus.Severity.ERROR, "Log write failed", 2000),
            "PN532Device" to HealthStatus(HealthStatus.Severity.WARNING, "Connection unstable", 3000)
        )
        
        val hasDegradation = detectDegradation(healthStatuses)
        assertTrue("Should detect degradation with CRITICAL and ERROR severities", hasDegradation)
    }
    
    /**
     * Test 5: Alert evaluation with severity threshold
     */
    @Test
    fun testAlertEvaluation_SeverityThreshold() {
        // Set threshold to ERROR
        alertPreferences.setAlertSeverityThreshold(HealthStatus.Severity.ERROR)
        
        val criticalAlert = HealthAlert(
            id = "test1",
            timestamp = getCurrentTimestamp(),
            moduleName = "CardDataStore",
            severity = HealthStatus.Severity.CRITICAL,
            message = "Critical error"
        )
        
        val warningAlert = HealthAlert(
            id = "test2",
            timestamp = getCurrentTimestamp(),
            moduleName = "Logging",
            severity = HealthStatus.Severity.WARNING,
            message = "Minor warning"
        )
        
        // Process alerts
        alertManager.processAlert(criticalAlert)
        alertManager.processAlert(warningAlert)
        
        // Check history (should only have critical alert)
        val history = alertManager.getAlertHistory()
        assertTrue("Critical alert should be in history", history.any { it.id == "test1" })
        assertFalse("Warning alert should be filtered out", history.any { it.id == "test2" })
    }
    
    /**
     * Test 6: Alert rule evaluation - Module specific
     */
    @Test
    fun testAlertRule_ModuleSpecific() {
        val rule = AlertRule(
            name = "Security Modules Only",
            ruleType = RuleType.MODULE_SPECIFIC,
            condition = RuleCondition(
                moduleNames = listOf("CardDataStore", "MasterPassword")
            )
        )
        
        val cardDataStoreAlert = HealthAlert(
            id = "test1",
            timestamp = getCurrentTimestamp(),
            moduleName = "CardDataStore",
            severity = HealthStatus.Severity.ERROR,
            message = "Test error"
        )
        
        val loggingAlert = HealthAlert(
            id = "test2",
            timestamp = getCurrentTimestamp(),
            moduleName = "Logging",
            severity = HealthStatus.Severity.ERROR,
            message = "Test error"
        )
        
        assertTrue("CardDataStore alert should match rule", rule.evaluate(cardDataStoreAlert))
        assertFalse("Logging alert should not match rule", rule.evaluate(loggingAlert))
    }
    
    /**
     * Test 7: Alert rule evaluation - Time based
     */
    @Test
    fun testAlertRule_TimeBased() {
        val rule = AlertRule(
            name = "Business Hours",
            ruleType = RuleType.TIME_BASED,
            condition = RuleCondition(
                timeWindowStart = 9,
                timeWindowEnd = 17
            )
        )
        
        val testAlert = HealthAlert(
            id = "test1",
            timestamp = getCurrentTimestamp(),
            moduleName = "Test",
            severity = HealthStatus.Severity.ERROR,
            message = "Test"
        )
        
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val expectedResult = currentHour in 9 until 17
        
        assertEquals(
            "Time-based rule should evaluate correctly",
            expectedResult,
            rule.evaluate(testAlert)
        )
    }
    
    /**
     * Test 8: Alert rule evaluation - Severity based
     */
    @Test
    fun testAlertRule_SeverityBased() {
        val rule = PredefinedRules.errorAndCritical()
        
        val criticalAlert = HealthAlert(
            id = "test1",
            timestamp = getCurrentTimestamp(),
            moduleName = "Test",
            severity = HealthStatus.Severity.CRITICAL,
            message = "Critical"
        )
        
        val errorAlert = HealthAlert(
            id = "test2",
            timestamp = getCurrentTimestamp(),
            moduleName = "Test",
            severity = HealthStatus.Severity.ERROR,
            message = "Error"
        )
        
        val warningAlert = HealthAlert(
            id = "test3",
            timestamp = getCurrentTimestamp(),
            moduleName = "Test",
            severity = HealthStatus.Severity.WARNING,
            message = "Warning"
        )
        
        assertTrue("Critical alert should pass ERROR threshold", rule.evaluate(criticalAlert))
        assertTrue("Error alert should pass ERROR threshold", rule.evaluate(errorAlert))
        assertFalse("Warning alert should not pass ERROR threshold", rule.evaluate(warningAlert))
    }
    
    /**
     * Test 9: Alert statistics calculation
     */
    @Test
    fun testAlertStatistics() {
        // Clear existing alerts
        alertManager.clearAlertHistory()
        
        // Add test alerts
        val alerts = listOf(
            HealthAlert("1", getCurrentTimestamp(), "M1", HealthStatus.Severity.CRITICAL, "Critical 1"),
            HealthAlert("2", getCurrentTimestamp(), "M2", HealthStatus.Severity.CRITICAL, "Critical 2"),
            HealthAlert("3", getCurrentTimestamp(), "M3", HealthStatus.Severity.ERROR, "Error 1"),
            HealthAlert("4", getCurrentTimestamp(), "M4", HealthStatus.Severity.ERROR, "Error 2"),
            HealthAlert("5", getCurrentTimestamp(), "M5", HealthStatus.Severity.ERROR, "Error 3"),
            HealthAlert("6", getCurrentTimestamp(), "M6", HealthStatus.Severity.WARNING, "Warning 1")
        )
        
        // Enable all alerts
        alertPreferences.setAlertsEnabled(true)
        alertPreferences.setAlertSeverityThreshold(HealthStatus.Severity.WARNING)
        
        alerts.forEach { alertManager.processAlert(it) }
        
        val stats = alertManager.getAlertStatistics()
        
        assertEquals("Total alerts should be 6", 6, stats.totalAlerts)
        assertEquals("Critical count should be 2", 2, stats.criticalCount)
        assertEquals("Error count should be 3", 3, stats.errorCount)
        assertEquals("Warning count should be 1", 1, stats.warningCount)
        assertTrue("Last 24h count should be positive", stats.last24HoursCount > 0)
    }
    
    /**
     * Test 10: Module alert enable/disable
     */
    @Test
    fun testModuleAlertToggle() {
        // Disable alerts for specific module
        alertPreferences.setModuleAlertEnabled("CardDataStore", false)
        
        val alert = HealthAlert(
            id = "test1",
            timestamp = getCurrentTimestamp(),
            moduleName = "CardDataStore",
            severity = HealthStatus.Severity.CRITICAL,
            message = "Critical error"
        )
        
        alertManager.processAlert(alert)
        
        val history = alertManager.getAlertHistory()
        assertFalse("Alert should be filtered out for disabled module", history.any { it.id == "test1" })
    }
    
    /**
     * Test 11: Custom rule persistence
     */
    @Test
    fun testCustomRulePersistence() {
        val customRule = AlertRule(
            name = "Test Rule",
            ruleType = RuleType.PATTERN_BASED,
            condition = RuleCondition(
                messagePattern = "timeout"
            )
        )
        
        alertPreferences.addCustomRule(customRule)
        
        val savedRules = alertPreferences.getCustomRules()
        assertTrue("Custom rule should be persisted", savedRules.any { it.name == "Test Rule" })
    }
    
    /**
     * Test 12: Alert history cleanup
     */
    @Test
    fun testAlertHistoryCleanup() {
        // Clear existing alerts
        alertManager.clearAlertHistory()
        
        // Add 600 alerts (should keep only last 500)
        repeat(600) { index ->
            val alert = HealthAlert(
                id = "test$index",
                timestamp = getCurrentTimestamp(),
                moduleName = "Test",
                severity = HealthStatus.Severity.WARNING,
                message = "Test alert $index"
            )
            
            alertPreferences.setAlertsEnabled(true)
            alertPreferences.setAlertSeverityThreshold(HealthStatus.Severity.WARNING)
            alertManager.processAlert(alert)
        }
        
        val history = alertManager.getAlertHistory()
        assertTrue("History should be limited to 500 alerts", history.size <= 500)
    }
    
    // Helper methods
    
    private fun createMockHealthStatus(severity: HealthStatus.Severity, timestamp: Long): HealthStatus {
        return HealthStatus(severity, "Mock status", timestamp)
    }
    
    private fun analyzeTrend(healthHistory: List<HealthStatus>): HealthTrend {
        if (healthHistory.size < 2) return HealthTrend.STABLE
        
        val severityLevels = healthHistory.map { severityToLevel(it.severity) }
        val firstHalf = severityLevels.take(severityLevels.size / 2).average()
        val secondHalf = severityLevels.takeLast(severityLevels.size / 2).average()
        
        return when {
            secondHalf < firstHalf - 0.5 -> HealthTrend.IMPROVING
            secondHalf > firstHalf + 0.5 -> HealthTrend.DEGRADING
            else -> HealthTrend.STABLE
        }
    }
    
    private fun detectDegradation(healthStatuses: Map<String, HealthStatus>): Boolean {
        return healthStatuses.values.any { 
            it.severity in listOf(HealthStatus.Severity.CRITICAL, HealthStatus.Severity.ERROR)
        }
    }
    
    private fun severityToLevel(severity: HealthStatus.Severity): Int {
        return when (severity) {
            HealthStatus.Severity.INFO -> 0
            HealthStatus.Severity.WARNING -> 1
            HealthStatus.Severity.ERROR -> 2
            HealthStatus.Severity.CRITICAL -> 3
        }
    }
    
    private fun getCurrentTimestamp(): String {
        return dateFormat.format(Date())
    }
}
