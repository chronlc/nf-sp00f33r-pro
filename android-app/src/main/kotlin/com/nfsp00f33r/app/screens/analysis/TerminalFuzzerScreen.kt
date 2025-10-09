package com.nfsp00f33r.app.screens.analysis

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nfsp00f33r.app.fuzzing.models.*
import kotlin.math.roundToInt

/**
 * Terminal Fuzzer Screen - EMV Protocol Fuzzing
 * Material3 Design with Dark Theme
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalFuzzerScreen(
    viewModel: FuzzerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val maxTests by viewModel.maxTests.collectAsState()
    val testsPerSecond by viewModel.testsPerSecond.collectAsState()
    
    var showConfig by remember { mutableStateOf(false) }
    var showFindings by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal Fuzzer", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showConfig = !showConfig }) {
                        Icon(Icons.Default.Settings, "Configuration", tint = Color.White)
                    }
                    IconButton(onClick = { showFindings = !showFindings }) {
                        Icon(Icons.Default.BugReport, "Findings", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Configuration Panel (collapsible)
            AnimatedVisibility(visible = showConfig) {
                ConfigurationPanel(
                    selectedStrategy = selectedStrategy,
                    maxTests = maxTests,
                    testsPerSecond = testsPerSecond,
                    onStrategyChange = { viewModel.setStrategy(it) },
                    onMaxTestsChange = { viewModel.setMaxTests(it) },
                    onRateChange = { viewModel.setTestsPerSecond(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Control Panel
            FuzzingControls(
                sessionState = uiState.sessionState,
                onStart = { viewModel.startFuzzing() },
                onPause = { viewModel.pauseFuzzing() },
                onResume = { viewModel.resumeFuzzing() },
                onStop = { viewModel.stopFuzzing() },
                onReset = { viewModel.resetEngine() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Metrics Dashboard
            MetricsDashboard(
                metrics = uiState.metrics,
                sessionState = uiState.sessionState
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Findings Panel (collapsible)
            if (showFindings) {
                FindingsPanel(
                    findings = uiState.interestingFindings,
                    anomalies = uiState.anomalies
                )
            } else {
                // Real-time Progress
                ProgressVisualization(
                    metrics = uiState.metrics,
                    maxTests = maxTests
                )
            }
        }
    }
}

@Composable
fun ConfigurationPanel(
    selectedStrategy: FuzzingStrategyType,
    maxTests: Int,
    testsPerSecond: Int,
    onStrategyChange: (FuzzingStrategyType) -> Unit,
    onMaxTestsChange: (Int) -> Unit,
    onRateChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "⚙️ Configuration",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Strategy Selection
            Text("Fuzzing Strategy:", color = Color(0xFFB0B0B0), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FuzzingStrategyType.values().forEach { strategy ->
                    FilterChip(
                        selected = selectedStrategy == strategy,
                        onClick = { onStrategyChange(strategy) },
                        label = {
                            Text(
                                when (strategy) {
                                    FuzzingStrategyType.RANDOM -> "Random"
                                    FuzzingStrategyType.MUTATION -> "Mutation"
                                    FuzzingStrategyType.PROTOCOL_AWARE -> "Protocol"
                                },
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4CAF50),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Max Tests
            Text("Max Tests: $maxTests", color = Color(0xFFB0B0B0), fontSize = 14.sp)
            Slider(
                value = maxTests.toFloat(),
                onValueChange = { onMaxTestsChange(it.toInt()) },
                valueRange = 10f..2000f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4CAF50),
                    activeTrackColor = Color(0xFF4CAF50)
                )
            )
            
            // Tests Per Second
            Text("Rate: $testsPerSecond tests/sec", color = Color(0xFFB0B0B0), fontSize = 14.sp)
            Slider(
                value = testsPerSecond.toFloat(),
                onValueChange = { onRateChange(it.toInt()) },
                valueRange = 1f..50f,
                steps = 48,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF2196F3),
                    activeTrackColor = Color(0xFF2196F3)
                )
            )
        }
    }
}

@Composable
fun FuzzingControls(
    sessionState: FuzzingSessionState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Start/Resume Button
            if (sessionState == FuzzingSessionState.IDLE || sessionState == FuzzingSessionState.STOPPED) {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, "Start", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("START")
                }
            } else if (sessionState == FuzzingSessionState.PAUSED) {
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, "Resume", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RESUME")
                }
            } else {
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.weight(1f)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RUNNING")
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Pause Button
            if (sessionState == FuzzingSessionState.RUNNING) {
                Button(
                    onClick = onPause,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Pause, "Pause", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PAUSE")
                }
            } else {
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Pause, "Pause", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PAUSE")
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Stop Button
            if (sessionState == FuzzingSessionState.RUNNING || sessionState == FuzzingSessionState.PAUSED) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, "Stop", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STOP")
                }
            } else {
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF757575)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RESET")
                }
            }
        }
    }
}

@Composable
fun MetricsDashboard(
    metrics: FuzzingMetrics,
    sessionState: FuzzingSessionState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📊 Metrics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // Status indicator
                val statusColor = when (sessionState) {
                    FuzzingSessionState.RUNNING -> Color(0xFF4CAF50)
                    FuzzingSessionState.PAUSED -> Color(0xFFFFA726)
                    FuzzingSessionState.ERROR -> Color(0xFFF44336)
                    else -> Color(0xFF757575)
                }
                
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCard(
                    label = "Tests",
                    value = metrics.testsExecuted.toString(),
                    icon = Icons.Default.Assessment,
                    color = Color(0xFF2196F3)
                )
                MetricCard(
                    label = "Rate",
                    value = "%.1f/s".format(metrics.testsPerSecond),
                    icon = Icons.Default.Speed,
                    color = Color(0xFF4CAF50)
                )
                MetricCard(
                    label = "Anomalies",
                    value = metrics.anomaliesFound.toString(),
                    icon = Icons.Default.Warning,
                    color = Color(0xFFFF9800)
                )
                MetricCard(
                    label = "Crashes",
                    value = metrics.crashesDetected.toString(),
                    icon = Icons.Default.Error,
                    color = Color(0xFFF44336)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCard(
                    label = "Coverage",
                    value = "${metrics.coveragePercent.roundToInt()}%",
                    icon = Icons.Default.PieChart,
                    color = Color(0xFF9C27B0)
                )
                MetricCard(
                    label = "Unique",
                    value = metrics.uniqueResponses.toString(),
                    icon = Icons.Default.FilterAlt,
                    color = Color(0xFF00BCD4)
                )
                MetricCard(
                    label = "Avg Time",
                    value = "${metrics.averageResponseTimeMs.roundToInt()}ms",
                    icon = Icons.Default.Timer,
                    color = Color(0xFF8BC34A)
                )
                MetricCard(
                    label = "Timeouts",
                    value = metrics.timeoutCount.toString(),
                    icon = Icons.Default.Schedule,
                    color = Color(0xFFFF5722)
                )
            }
        }
    }
}

@Composable
fun RowScope.MetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                label,
                fontSize = 10.sp,
                color = Color(0xFFB0B0B0)
            )
        }
    }
}

@Composable
fun ProgressVisualization(
    metrics: FuzzingMetrics,
    maxTests: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "📈 Progress",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress bar
            val progress = if (maxTests > 0) {
                metrics.testsExecuted.toFloat() / maxTests.toFloat()
            } else 0f
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFF424242)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${metrics.testsExecuted} / $maxTests tests",
                    fontSize = 14.sp,
                    color = Color(0xFFB0B0B0)
                )
                Text(
                    "${(progress * 100).roundToInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status Words Distribution
            if (metrics.uniqueStatusWords.isNotEmpty()) {
                Text(
                    "Status Words (${metrics.uniqueStatusWords.size}):",
                    fontSize = 14.sp,
                    color = Color(0xFFB0B0B0)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                metrics.uniqueStatusWords.take(5).forEach { sw ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            sw,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FindingsPanel(
    findings: List<FuzzTestResult>,
    anomalies: List<Anomaly>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "🐛 Findings (${anomalies.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (anomalies.isEmpty()) {
                Text(
                    "No anomalies detected yet",
                    fontSize = 14.sp,
                    color = Color(0xFFB0B0B0),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(anomalies.take(20)) { anomaly ->
                        AnomalyCard(anomaly)
                    }
                }
            }
        }
    }
}

@Composable
fun AnomalyCard(anomaly: Anomaly) {
    val severityColor = when (anomaly.severity) {
        AnomalySeverity.CRITICAL -> Color(0xFFF44336)
        AnomalySeverity.HIGH -> Color(0xFFFF9800)
        AnomalySeverity.MEDIUM -> Color(0xFFFFC107)
        AnomalySeverity.LOW -> Color(0xFF8BC34A)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, severityColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    anomaly.type,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = severityColor
                )
                Text(
                    anomaly.severity.name,
                    fontSize = 11.sp,
                    color = severityColor,
                    modifier = Modifier
                        .background(severityColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                anomaly.description,
                fontSize = 12.sp,
                color = Color(0xFFB0B0B0)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "CMD: ${anomaly.command.joinToString("") { "%02X".format(it) }}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF4CAF50)
            )
        }
    }
}
