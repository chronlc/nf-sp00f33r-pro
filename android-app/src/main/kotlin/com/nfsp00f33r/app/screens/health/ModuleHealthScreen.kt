package com.nfsp00f33r.app.screens.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.core.ModuleState

/**
 * Main Health Monitoring Screen
 * Displays real-time health status for all modules with control capabilities
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleHealthScreen(
    viewModel: HealthMonitoringViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val overallHealth by viewModel.overallHealth.collectAsState()
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsState()
    val alerts by viewModel.alerts.collectAsState()

    var showAlerts by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Module Health Monitor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Auto-refresh toggle
                    IconButton(onClick = { viewModel.toggleAutoRefresh() }) {
                        Icon(
                            if (autoRefreshEnabled) Icons.Default.Refresh else Icons.Default.Pause,
                            contentDescription = if (autoRefreshEnabled) "Auto-refresh ON" else "Auto-refresh OFF",
                            tint = if (autoRefreshEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Manual refresh
                    IconButton(onClick = { viewModel.refreshAllHealth() }) {
                        Icon(Icons.Default.Refresh, "Manual Refresh")
                    }

                    // Alerts
                    BadgedBox(
                        badge = {
                            if (alerts.isNotEmpty()) {
                                Badge { Text("${alerts.size}") }
                            }
                        }
                    ) {
                        IconButton(onClick = { showAlerts = !showAlerts }) {
                            Icon(Icons.Default.Notifications, "Alerts")
                        }
                    }

                    // History
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.Default.History, "History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Overall system health card
            item {
                OverallHealthCard(overallHealth)
            }

            // Alerts section (if shown)
            if (showAlerts) {
                item {
                    AlertsSection(
                        alerts = alerts,
                        onDismiss = { showAlerts = false },
                        onClearAll = { viewModel.clearAlerts() }
                    )
                }
            }

            // History section (if shown)
            if (showHistory) {
                item {
                    HistorySection(
                        viewModel = viewModel,
                        onDismiss = { showHistory = false }
                    )
                }
            }

            // Individual module health cards
            item {
                Text(
                    "Module Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // CardDataStore
            item {
                ModuleHealthCard(
                    moduleName = "CardDataStore",
                    healthState = viewModel.cardDataStoreHealth.collectAsState().value,
                    onStart = { viewModel.startModule("CardDataStore") },
                    onStop = { viewModel.stopModule("CardDataStore") },
                    onRestart = { viewModel.restartModule("CardDataStore") }
                )
            }

            // Logging
            item {
                ModuleHealthCard(
                    moduleName = "Logging",
                    healthState = viewModel.loggingHealth.collectAsState().value,
                    onStart = { viewModel.startModule("Logging") },
                    onStop = { viewModel.stopModule("Logging") },
                    onRestart = { viewModel.restartModule("Logging") }
                )
            }

            // PN532Device
            item {
                ModuleHealthCard(
                    moduleName = "PN532Device",
                    healthState = viewModel.pn532DeviceHealth.collectAsState().value,
                    onStart = { viewModel.startModule("PN532Device") },
                    onStop = { viewModel.stopModule("PN532Device") },
                    onRestart = { viewModel.restartModule("PN532Device") }
                )
            }

            // MasterPassword
            item {
                ModuleHealthCard(
                    moduleName = "MasterPassword",
                    healthState = viewModel.passwordHealth.collectAsState().value,
                    onStart = { viewModel.startModule("MasterPassword") },
                    onStop = { viewModel.stopModule("MasterPassword") },
                    onRestart = { viewModel.restartModule("MasterPassword") }
                )
            }

            // NfcHce
            item {
                ModuleHealthCard(
                    moduleName = "NfcHce",
                    healthState = viewModel.nfcHceHealth.collectAsState().value,
                    onStart = { viewModel.startModule("NfcHce") },
                    onStop = { viewModel.stopModule("NfcHce") },
                    onRestart = { viewModel.restartModule("NfcHce") }
                )
            }

            // Emulation
            item {
                ModuleHealthCard(
                    moduleName = "Emulation",
                    healthState = viewModel.emulationHealth.collectAsState().value,
                    onStart = { viewModel.startModule("Emulation") },
                    onStop = { viewModel.stopModule("Emulation") },
                    onRestart = { viewModel.restartModule("Emulation") }
                )
            }
        }
    }
}

/**
 * Overall system health summary card
 */
@Composable
fun OverallHealthCard(state: SystemHealthState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is SystemHealthState.Loaded -> when (state.severity) {
                    HealthStatus.Severity.INFO -> Color(0xFF1B5E20).copy(alpha = 0.1f)
                    HealthStatus.Severity.WARNING -> Color(0xFFF57C00).copy(alpha = 0.1f)
                    HealthStatus.Severity.ERROR -> Color(0xFFD32F2F).copy(alpha = 0.1f)
                    HealthStatus.Severity.CRITICAL -> Color(0xFF880E4F).copy(alpha = 0.1f)
                }
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "System Health",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                when (state) {
                    is SystemHealthState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    is SystemHealthState.Loaded -> {
                        HealthIndicator(state.severity, size = 48.dp)
                    }
                    is SystemHealthState.Error -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (state) {
                is SystemHealthState.Loading -> {
                    Text("Loading system health...", style = MaterialTheme.typography.bodyMedium)
                }
                is SystemHealthState.Loaded -> {
                    Text(
                        state.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Module counts
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HealthStatChip("✓ ${state.healthyModules}", Color(0xFF1B5E20))
                        if (state.warningModules > 0) {
                            HealthStatChip("⚠ ${state.warningModules}", Color(0xFFF57C00))
                        }
                        if (state.errorModules > 0) {
                            HealthStatChip("✗ ${state.errorModules}", Color(0xFFD32F2F))
                        }
                        if (state.criticalModules > 0) {
                            HealthStatChip("!! ${state.criticalModules}", Color(0xFF880E4F))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Last updated: ${state.lastUpdated}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is SystemHealthState.Error -> {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Individual module health card with expandable details
 */
@Composable
fun ModuleHealthCard(
    moduleName: String,
    healthState: ModuleHealthState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(if (expanded) 180f else 0f, label = "rotation")

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (healthState) {
                        is ModuleHealthState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                        is ModuleHealthState.Healthy -> {
                            HealthIndicator(healthState.status.severity, size = 32.dp)
                        }
                        is ModuleHealthState.Error -> {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            moduleName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        when (healthState) {
                            is ModuleHealthState.Healthy -> {
                                Text(
                                    healthState.state.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {}
                        }
                    }
                }

                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotationAngle)
                )
            }

            // Expanded details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider()

                    Spacer(modifier = Modifier.height(4.dp))

                    when (healthState) {
                        is ModuleHealthState.Healthy -> {
                            Text(
                                healthState.status.message,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                "Last checked: ${healthState.lastChecked}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Control buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onStart,
                                    enabled = healthState.state != ModuleState.RUNNING,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Start")
                                }

                                Button(
                                    onClick = onStop,
                                    enabled = healthState.state == ModuleState.RUNNING,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Stop")
                                }

                                Button(
                                    onClick = onRestart,
                                    enabled = healthState.state == ModuleState.RUNNING,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Restart")
                                }
                            }
                        }
                        is ModuleHealthState.Error -> {
                            Text(
                                healthState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        is ModuleHealthState.Loading -> {
                            Text("Loading module health...")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Alerts section showing critical/error alerts
 */
@Composable
fun AlertsSection(
    alerts: List<HealthAlert>,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Alerts (${alerts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    TextButton(onClick = onClearAll) {
                        Text("Clear All")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Dismiss")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (alerts.isEmpty()) {
                Text("No active alerts", style = MaterialTheme.typography.bodyMedium)
            } else {
                alerts.take(5).forEach { alert ->
                    AlertItem(alert)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (alerts.size > 5) {
                    Text(
                        "+${alerts.size - 5} more alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Single alert item
 */
@Composable
fun AlertItem(alert: HealthAlert) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HealthIndicator(alert.severity, size = 24.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                alert.moduleName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                alert.message,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                alert.timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * History section
 */
@Composable
fun HistorySection(
    viewModel: HealthMonitoringViewModel,
    onDismiss: () -> Unit
) {
    val history by viewModel.healthHistory.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Health History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Text("Clear")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Dismiss")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (history.isEmpty()) {
                Text("No history available", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "${history.size} entries recorded",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Health indicator icon based on severity
 */
@Composable
fun HealthIndicator(severity: HealthStatus.Severity, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val (icon, color) = when (severity) {
        HealthStatus.Severity.INFO -> Icons.Default.CheckCircle to Color(0xFF1B5E20)
        HealthStatus.Severity.WARNING -> Icons.Default.Warning to Color(0xFFF57C00)
        HealthStatus.Severity.ERROR -> Icons.Default.Error to Color(0xFFD32F2F)
        HealthStatus.Severity.CRITICAL -> Icons.Default.Cancel to Color(0xFF880E4F)
    }

    Icon(
        icon,
        contentDescription = severity.name,
        tint = color,
        modifier = Modifier.size(size)
    )
}

/**
 * Small health stat chip
 */
@Composable
fun HealthStatChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
