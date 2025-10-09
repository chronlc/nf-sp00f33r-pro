package com.nfsp00f33r.app.screens.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.nfsp00f33r.app.alerts.HealthAlertManager
import com.nfsp00f33r.app.alerts.AlertStatistics
import java.text.SimpleDateFormat
import java.util.*

/**
 * Alert History Screen
 * Phase 3 Days 7-8: Alert System
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryScreen(
    viewModel: HealthMonitoringViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var alertHistory by remember { mutableStateOf(listOf<HealthAlert>()) }
    var statistics by remember { mutableStateOf<AlertStatistics?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedSeverityFilter by remember { mutableStateOf<HealthStatus.Severity?>(null) }
    
    // Load alert history
    LaunchedEffect(Unit) {
        val alertManager = HealthAlertManager(viewModel.getContext())
        alertHistory = alertManager.getAlertHistory()
        statistics = alertManager.getAlertStatistics()
    }
    
    // Filter alerts by severity
    val filteredAlerts = if (selectedSeverityFilter != null) {
        alertHistory.filter { it.severity == selectedSeverityFilter }
    } else {
        alertHistory
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alert History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val alertManager = HealthAlertManager(viewModel.getContext())
                        alertHistory = alertManager.getAlertHistory()
                        statistics = alertManager.getAlertStatistics()
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, "Clear History")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Statistics Section
            statistics?.let { stats ->
                AlertStatisticsCard(stats = stats)
            }
            
            // Severity Filter
            SeverityFilterRow(
                selectedSeverity = selectedSeverityFilter,
                onSeveritySelected = { selectedSeverityFilter = it }
            )
            
            // Alert List
            if (filteredAlerts.isEmpty()) {
                EmptyAlertHistoryMessage()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredAlerts) { alert ->
                        AlertHistoryItem(alert = alert)
                    }
                }
            }
        }
    }
    
    // Clear History Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Alert History") },
            text = { Text("Are you sure you want to clear all alert history? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val alertManager = HealthAlertManager(viewModel.getContext())
                        alertManager.clearAlertHistory()
                        alertHistory = emptyList()
                        statistics = AlertStatistics(0, 0, 0, 0, 0)
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AlertStatisticsCard(stats: AlertStatistics) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Total", stats.totalAlerts.toString(), Color.Gray)
                StatItem("Critical", stats.criticalCount.toString(), Color.Red)
                StatItem("Errors", stats.errorCount.toString(), Color(0xFFFF9800))
                StatItem("Warnings", stats.warningCount.toString(), Color(0xFFFFC107))
            }
            
            Divider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Last 24 Hours", fontWeight = FontWeight.Medium)
                Text(
                    stats.last24HoursCount.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun SeverityFilterRow(
    selectedSeverity: HealthStatus.Severity?,
    onSeveritySelected: (HealthStatus.Severity?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedSeverity == null,
            onClick = { onSeveritySelected(null) },
            label = { Text("All") }
        )
        
        listOf(
            HealthStatus.Severity.CRITICAL to "Critical",
            HealthStatus.Severity.ERROR to "Error",
            HealthStatus.Severity.WARNING to "Warning",
            HealthStatus.Severity.INFO to "Info"
        ).forEach { (severity, label) ->
            FilterChip(
                selected = selectedSeverity == severity,
                onClick = { onSeveritySelected(severity) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun AlertHistoryItem(alert: HealthAlert) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Severity indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = when (alert.severity) {
                            HealthStatus.Severity.CRITICAL -> Color.Red
                            HealthStatus.Severity.ERROR -> Color(0xFFFF9800)
                            HealthStatus.Severity.WARNING -> Color(0xFFFFC107)
                            HealthStatus.Severity.INFO -> Color.Gray
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (alert.severity) {
                        HealthStatus.Severity.CRITICAL -> Icons.Default.Error
                        HealthStatus.Severity.ERROR -> Icons.Default.Warning
                        HealthStatus.Severity.WARNING -> Icons.Default.Info
                        HealthStatus.Severity.INFO -> Icons.Default.Check
                    },
                    contentDescription = alert.severity.name,
                    tint = Color.White
                )
            }
            
            // Alert content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        alert.moduleName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        alert.timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    alert.message,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    alert.severity.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (alert.severity) {
                        HealthStatus.Severity.CRITICAL -> Color.Red
                        HealthStatus.Severity.ERROR -> Color(0xFFFF9800)
                        HealthStatus.Severity.WARNING -> Color(0xFFFFC107)
                        HealthStatus.Severity.INFO -> Color.Gray
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyAlertHistoryMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "No Alerts",
                modifier = Modifier.size(64.dp),
                tint = Color.Gray
            )
            Text(
                "No alerts to display",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )
            Text(
                "All systems healthy",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}


