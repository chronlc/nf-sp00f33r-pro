package com.nfsp00f33r.app.screens.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.alerts.AlertPreferences
import com.nfsp00f33r.app.alerts.AlertRule
import com.nfsp00f33r.app.alerts.PredefinedRules
import java.text.SimpleDateFormat
import java.util.*

/**
 * Alert Configuration Screen
 * Phase 3 Days 7-8: Alert System
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertConfigurationScreen(
    viewModel: HealthMonitoringViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var alertsEnabled by remember { mutableStateOf(true) }
    var severityThreshold by remember { mutableStateOf(HealthStatus.Severity.WARNING) }
    var moduleSettings by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var customRules by remember { mutableStateOf(listOf<AlertRule>()) }
    
    // Load preferences
    LaunchedEffect(Unit) {
        val prefs = AlertPreferences(viewModel.getContext())
        alertsEnabled = prefs.isAlertsEnabled()
        severityThreshold = prefs.getAlertSeverityThreshold()
        moduleSettings = prefs.getAllModuleAlertSettings()
        customRules = prefs.getCustomRules()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alert Configuration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Global Settings Section
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Global Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Enable/Disable Alerts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Enable Alerts", fontWeight = FontWeight.Medium)
                                Text(
                                    "Receive notifications for health issues",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = alertsEnabled,
                                onCheckedChange = { enabled ->
                                    alertsEnabled = enabled
                                    val prefs = AlertPreferences(viewModel.getContext())
                                    prefs.setAlertsEnabled(enabled)
                                }
                            )
                        }
                        
                        Divider()
                        
                        // Severity Threshold
                        Column {
                            Text("Minimum Severity", fontWeight = FontWeight.Medium)
                            Text(
                                "Only show alerts at or above this level",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    HealthStatus.Severity.INFO to "Info",
                                    HealthStatus.Severity.WARNING to "Warning",
                                    HealthStatus.Severity.ERROR to "Error",
                                    HealthStatus.Severity.CRITICAL to "Critical"
                                ).forEach { (severity, label) ->
                                    FilterChip(
                                        selected = severityThreshold == severity,
                                        onClick = {
                                            severityThreshold = severity
                                            val prefs = AlertPreferences(viewModel.getContext())
                                            prefs.setAlertSeverityThreshold(severity)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Module-Specific Settings
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Module Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        moduleSettings.forEach { (moduleName, enabled) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(moduleName)
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { newEnabled ->
                                        moduleSettings = moduleSettings.toMutableMap().apply {
                                            put(moduleName, newEnabled)
                                        }
                                        val prefs = AlertPreferences(viewModel.getContext())
                                        prefs.setModuleAlertEnabled(moduleName, newEnabled)
                                    }
                                )
                            }
                            if (moduleName != moduleSettings.keys.last()) {
                                Divider()
                            }
                        }
                    }
                }
            }
            
            // Custom Rules Section
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Custom Rules",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showRuleDialog = true }) {
                                Icon(Icons.Default.Add, "Add Rule")
                            }
                        }
                        
                        if (customRules.isEmpty()) {
                            Text(
                                "No custom rules configured",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        } else {
                            customRules.forEach { rule ->
                                CustomRuleItem(
                                    rule = rule,
                                    onToggle = { enabled ->
                                        val updatedRules = customRules.map {
                                            if (it.id == rule.id) it.copy(enabled = enabled) else it
                                        }
                                        customRules = updatedRules
                                        val prefs = AlertPreferences(viewModel.getContext())
                                        prefs.saveCustomRules(updatedRules)
                                    },
                                    onDelete = {
                                        val prefs = AlertPreferences(viewModel.getContext())
                                        prefs.removeCustomRule(rule.id)
                                        customRules = customRules.filter { it.id != rule.id }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Add Rule Dialog
    if (showRuleDialog) {
        PredefinedRulesDialog(
            onDismiss = { showRuleDialog = false },
            onRuleSelected = { rule ->
                val prefs = AlertPreferences(viewModel.getContext())
                prefs.addCustomRule(rule)
                customRules = prefs.getCustomRules()
                showRuleDialog = false
            }
        )
    }
}

@Composable
private fun CustomRuleItem(
    rule: AlertRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rule.name, fontWeight = FontWeight.Medium)
            Text(
                rule.ruleType.name.replace('_', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
            }
        }
    }
}

@Composable
private fun PredefinedRulesDialog(
    onDismiss: () -> Unit,
    onRuleSelected: (AlertRule) -> Unit
) {
    val predefinedRules = listOf(
        PredefinedRules.criticalOnly(),
        PredefinedRules.errorAndCritical(),
        PredefinedRules.businessHoursOnly(),
        PredefinedRules.nightTimeAlerts(),
        PredefinedRules.securityModulesOnly()
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Predefined Rule") },
        text = {
            LazyColumn {
                items(predefinedRules) { rule ->
                    TextButton(
                        onClick = { onRuleSelected(rule) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(rule.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


