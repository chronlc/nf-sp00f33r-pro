package com.nfsp00f33r.app.screens.health

import android.content.Context
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.data.health.HealthHistoryEntry
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Health Trends Screen with Charts
 * Phase 3 Days 5-6: Health History & Trends Visualization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthTrendsScreen(
    viewModel: HealthMonitoringViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val healthHistory by viewModel.healthHistory.collectAsState()
    val trends = remember(healthHistory) {
        viewModel.analyzeHealthTrends()
    }
    
    var showExportDialog by remember { mutableStateOf(false) }
    var exportSuccess by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Trends & History") },
                actions = {
                    IconButton(onClick = { viewModel.loadHealthHistoryFromDb() }) {
                        Icon(Icons.Default.Refresh, "Refresh from database")
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Share, "Export health report")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Statistics Cards
            StatsCardsRow(healthHistory)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Overall Health Trend Chart
            Text(
                "Overall System Health Trend",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            OverallHealthTrendChart(healthHistory)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Per-Module Trend Chart
            Text(
                "Module Health Comparison",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            ModuleHealthBarChart(healthHistory)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Trend Analysis Cards
            Text(
                "Trend Analysis",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            trends.forEach { (moduleName, analysis) ->
                TrendAnalysisCard(moduleName, analysis)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Health Event Timeline
            Text(
                "Health Event Timeline",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            HealthEventTimeline(healthHistory)
        }
    }
    
    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Health Report") },
            text = {
                Column {
                    Text("Export complete health report as JSON file?")
                    exportSuccess?.let { path ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Exported to: $path",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val report = viewModel.exportHealthReport()
                        val filePath = exportToFile(context, report)
                        exportSuccess = filePath
                    }
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false; exportSuccess = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun StatsCardsRow(healthHistory: List<HealthHistoryEntry>) {
    val totalEntries = healthHistory.size
    val criticalCount = healthHistory.count { entry ->
        entry.moduleStatuses.values.any { it.severity == HealthStatus.Severity.CRITICAL }
    }
    val errorCount = healthHistory.count { entry ->
        entry.moduleStatuses.values.any { it.severity == HealthStatus.Severity.ERROR }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "Total Entries",
            value = totalEntries.toString(),
            icon = Icons.Default.Info,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Critical",
            value = criticalCount.toString(),
            icon = Icons.Default.Warning,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Errors",
            value = errorCount.toString(),
            icon = Icons.Default.Star,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = color
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun OverallHealthTrendChart(healthHistory: List<HealthHistoryEntry>) {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(true)
                
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                
                axisRight.isEnabled = false
                axisLeft.axisMinimum = 0f
                axisLeft.axisMaximum = 3f
            }
        },
        update = { chart ->
            val entries = healthHistory.takeLast(20).mapIndexed { index, entry ->
                val severity = calculateAverageSeverity(entry.moduleStatuses.values.toList())
                Entry(index.toFloat(), severity)
            }
            
            val dataSet = LineDataSet(entries, "System Health").apply {
                color = Color.BLUE
                setCircleColor(Color.BLUE)
                lineWidth = 2f
                circleRadius = 4f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }
            
            chart.data = LineData(dataSet)
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}

@Composable
fun ModuleHealthBarChart(healthHistory: List<HealthHistoryEntry>) {
    if (healthHistory.isEmpty()) {
        Text("No data available", style = MaterialTheme.typography.bodyMedium)
        return
    }
    
    AndroidView(
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.granularity = 1f
                
                axisRight.isEnabled = false
                axisLeft.axisMinimum = 0f
                axisLeft.axisMaximum = 3f
            }
        },
        update = { chart ->
            val latestEntry = healthHistory.firstOrNull() ?: return@AndroidView
            
            val moduleNames = listOf(
                "CardDataStore", "Logging", "PN532Device",
                "MasterPassword", "NfcHce", "Emulation"
            )
            
            val entries = moduleNames.mapIndexed { index, name ->
                val status = latestEntry.moduleStatuses[name]
                val severity = when (status?.severity) {
                    HealthStatus.Severity.INFO -> 0f
                    HealthStatus.Severity.WARNING -> 1f
                    HealthStatus.Severity.ERROR -> 2f
                    HealthStatus.Severity.CRITICAL -> 3f
                    null -> 0f
                }
                BarEntry(index.toFloat(), severity)
            }
            
            val dataSet = BarDataSet(entries, "Module Health").apply {
                colors = listOf(
                    Color.GREEN,
                    Color.YELLOW,
                    Color.rgb(255, 165, 0),
                    Color.RED
                )
                setDrawValues(true)
            }
            
            chart.data = BarData(dataSet)
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(moduleNames)
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    )
}

@Composable
fun TrendAnalysisCard(moduleName: String, analysis: TrendAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    moduleName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    analysis.message,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Data points: ${analysis.dataPoints}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            val (icon, color) = when (analysis.trend) {
                HealthTrend.IMPROVING -> Icons.Default.KeyboardArrowUp to MaterialTheme.colorScheme.primary
                HealthTrend.STABLE -> Icons.Default.Check to MaterialTheme.colorScheme.tertiary
                HealthTrend.DEGRADING -> Icons.Default.KeyboardArrowDown to MaterialTheme.colorScheme.error
            }
            
            Icon(
                icon,
                contentDescription = analysis.trend.name,
                tint = color,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun HealthEventTimeline(healthHistory: List<HealthHistoryEntry>) {
    if (healthHistory.isEmpty()) {
        Text("No events recorded", style = MaterialTheme.typography.bodyMedium)
        return
    }
    
    Column {
        healthHistory.take(10).forEach { entry ->
            val hasCritical = entry.moduleStatuses.values.any { 
                it.severity == HealthStatus.Severity.CRITICAL 
            }
            val hasError = entry.moduleStatuses.values.any { 
                it.severity == HealthStatus.Severity.ERROR 
            }
            
            if (hasCritical || hasError) {
                TimelineEventCard(entry, hasCritical, hasError)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun TimelineEventCard(
    entry: HealthHistoryEntry,
    hasCritical: Boolean,
    hasError: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasCritical) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    entry.timestamp,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (hasCritical) "CRITICAL" else "ERROR",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasCritical) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            entry.moduleStatuses.filter { 
                it.value.severity == HealthStatus.Severity.CRITICAL || 
                it.value.severity == HealthStatus.Severity.ERROR 
            }.forEach { (name, status) ->
                Text(
                    "$name: ${status.message}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Calculate average severity score
 */
private fun calculateAverageSeverity(statuses: List<HealthStatus>): Float {
    if (statuses.isEmpty()) return 0f
    
    val sum = statuses.sumOf { status ->
        when (status.severity) {
            HealthStatus.Severity.INFO -> 0L
            HealthStatus.Severity.WARNING -> 1L
            HealthStatus.Severity.ERROR -> 2L
            HealthStatus.Severity.CRITICAL -> 3L
        }
    }
    
    return sum.toFloat() / statuses.size
}

/**
 * Export health report to JSON file
 */
private fun exportToFile(context: Context, jsonContent: String): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "health_report_$timestamp.json"
    
    val file = File(context.getExternalFilesDir(null), fileName)
    FileWriter(file).use { writer ->
        writer.write(jsonContent)
    }
    
    return file.absolutePath
}
