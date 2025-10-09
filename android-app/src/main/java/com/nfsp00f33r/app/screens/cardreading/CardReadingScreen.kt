@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nfsp00f33r.app.screens.cardreading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nfsp00f33r.app.components.VirtualCardView
import com.nfsp00f33r.app.ui.components.RocaVulnerabilityCard
import com.nfsp00f33r.app.ui.components.RocaVulnerabilityBadge
import com.nfsp00f33r.app.cardreading.EmvTlvParser

/**
 * SLEEK DATA-FOCUSED EMV Card Reading Screen
 * Clean, professional design with compact controls and large display area
 */
@Composable
fun CardReadingScreen() {
    val context = LocalContext.current
    val viewModel: CardReadingViewModel = viewModel(
        factory = CardReadingViewModel.Factory(context)
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Data-focused status header
            StatusHeaderCard(viewModel)
            
            // Compact control panel
            ControlPanelCard(viewModel)
            
            // ROCA Vulnerability Status
            if (viewModel.rocaVulnerabilityStatus != null) {
                RocaVulnerabilityStatusCard(viewModel)
            }
            
            // Large data display area
            if (viewModel.scannedCards.isNotEmpty()) {
                ActiveCardsSection(viewModel)
            }
            
            // Real-time EMV data display
            if (viewModel.parsedEmvFields.isNotEmpty()) {
                EmvDataDisplaySection(viewModel)
            }
            
            // Terminal-style APDU log
            ApduTerminalSection(viewModel)
        }
    }
}

@Composable
private fun StatusHeaderCard(viewModel: CardReadingViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1419)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "EMV CARD SCANNER",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF00FF41)
                )
                
                // Reader Status Chip
                Surface(
                    color = if (viewModel.selectedReader != null) Color(0xFF1B4332) else Color(0xFF4A1A1A),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (viewModel.selectedReader != null) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = "Reader Status",
                            tint = if (viewModel.selectedReader != null) Color(0xFF00FF41) else Color(0xFFFF6B6B),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            viewModel.selectedReader?.let { viewModel.getReaderDisplayName(it) } ?: "No Reader",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (viewModel.selectedReader != null) Color(0xFF00FF41) else Color(0xFFFF6B6B)
                        )
                    }
                }
            }
            
            // Data Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DataStat("State", viewModel.scanState.name, Color(0xFF00FF41))
                DataStat("Cards", "${viewModel.scannedCards.size}", Color(0xFF4FC3F7))
                DataStat("APDUs", "${viewModel.apduLog.size}", Color(0xFFFFB74D))
                DataStat("NFC", if (com.nfsp00f33r.app.activities.MainActivity.currentNfcTag != null) "DETECTED" else "WAITING", 
                    if (com.nfsp00f33r.app.activities.MainActivity.currentNfcTag != null) Color(0xFF4CAF50) else Color(0xFFE1BEE7))
                // ROCA vulnerability status
                DataStat(
                    "ROCA",
                    if (viewModel.isRocaVulnerable) "VULN" else if (viewModel.rocaVulnerabilityStatus != "Not checked") "SAFE" else "N/A",
                    if (viewModel.isRocaVulnerable) Color(0xFFF44336) else if (viewModel.rocaVulnerabilityStatus != "Not checked") Color(0xFF4CAF50) else Color(0xFF666666)
                )
            }
            
            // NFC Debug Info
            Text(
                com.nfsp00f33r.app.activities.MainActivity.nfcDebugMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF888888),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DataStat(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF888888)
        )
    }
}

@Composable
private fun ControlPanelCard(viewModel: CardReadingViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1419)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reader & Technology Selection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reader Selection
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "READER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF888888)
                    )
                    
                    var readerExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { readerExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF1A1F2E),
                                contentColor = Color(0xFFFFFFFF)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                viewModel.selectedReader?.let { viewModel.getReaderDisplayName(it) } ?: "Select Reader",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                        
                        DropdownMenu(
                            expanded = readerExpanded,
                            onDismissRequest = { readerExpanded = false },
                            modifier = Modifier.background(Color(0xFF1A1F2E))
                        ) {
                            viewModel.availableReaders.forEach { reader ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            viewModel.getReaderDisplayName(reader),
                                            color = Color(0xFFFFFFFF),
                                            style = MaterialTheme.typography.bodySmall
                                        ) 
                                    },
                                    onClick = {
                                        viewModel.selectReader(reader)
                                        readerExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Technology Selection
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "PROTOCOL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF888888)
                    )
                    
                    var techExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { techExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF1A1F2E),
                                contentColor = Color(0xFFFFFFFF)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                when (viewModel.selectedTechnology) {
                                    CardReadingViewModel.NfcTechnology.EMV_CONTACTLESS -> "EMV/ISO-DEP"
                                    CardReadingViewModel.NfcTechnology.AUTO_SELECT -> "Auto-Detect"
                                    else -> "EMV/ISO-DEP"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                        
                        DropdownMenu(
                            expanded = techExpanded,
                            onDismissRequest = { techExpanded = false },
                            modifier = Modifier.background(Color(0xFF1A1F2E))
                        ) {
                            listOf("EMV/ISO-DEP", "Auto-Detect").forEach { tech ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            tech,
                                            color = Color(0xFFFFFFFF),
                                            style = MaterialTheme.typography.bodySmall
                                        ) 
                                    },
                                    onClick = {
                                        viewModel.selectTechnology(
                                            if (tech == "Auto-Detect") CardReadingViewModel.NfcTechnology.AUTO_SELECT 
                                            else CardReadingViewModel.NfcTechnology.EMV_CONTACTLESS
                                        )
                                        techExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Compact Scan Button
            val scanButtonColor = when (viewModel.scanState) {
                CardReadingViewModel.ScanState.SCANNING -> Color(0xFFFF1744)
                CardReadingViewModel.ScanState.IDLE -> Color(0xFF00FF41)
                else -> Color(0xFF666666)
            }
            
            val scanButtonText = when (viewModel.scanState) {
                CardReadingViewModel.ScanState.SCANNING -> "STOP SCAN"
                CardReadingViewModel.ScanState.IDLE -> "START SCAN"
                else -> "PROCESSING"
            }
            
            Button(
                onClick = {
                    if (viewModel.scanState == CardReadingViewModel.ScanState.SCANNING) {
                        viewModel.stopScanning()
                    } else {
                        viewModel.startScanning()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scanButtonColor
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (viewModel.scanState == CardReadingViewModel.ScanState.SCANNING) 
                            Icons.Default.Stop 
                        else 
                            Icons.Default.PlayArrow,
                        contentDescription = scanButtonText,
                        tint = Color(0xFF0A0A0A),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        scanButtonText,
                        color = Color(0xFF0A0A0A),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            
            // Status Message
            if (viewModel.statusMessage.isNotEmpty()) {
                Text(
                    viewModel.statusMessage,
                    color = Color(0xFF888888),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RocaVulnerabilityStatusCard(viewModel: CardReadingViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (viewModel.isRocaVulnerable) {
                Color(0xFF4A1A1A) // Dark red for vulnerable
            } else {
                Color(0xFF1B4332) // Dark green for safe
            }
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (viewModel.isRocaVulnerable) {
                    Icons.Default.Warning
                } else {
                    Icons.Default.CheckCircle
                },
                contentDescription = "ROCA Status",
                tint = if (viewModel.isRocaVulnerable) {
                    Color(0xFFFF6B6B) // Bright red
                } else {
                    Color(0xFF00FF41) // Bright green
                },
                modifier = Modifier.size(32.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "ROCA SECURITY CHECK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF888888)
                )
                Text(
                    viewModel.rocaVulnerabilityStatus ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (viewModel.isRocaVulnerable) {
                        Color(0xFFFF6B6B)
                    } else {
                        Color(0xFF00FF41)
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveCardsSection(viewModel: CardReadingViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "ACTIVE CARDS (${viewModel.scannedCards.size})",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF00FF41)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(viewModel.scannedCards) { card ->
                VirtualCardView(card = card)
            }
        }
        
        // Pagination indicator if more than 3 cards
        if (viewModel.scannedCards.size > 3) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${viewModel.scannedCards.size} cards scanned - scroll to view all",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ApduTerminalSection(viewModel: CardReadingViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1419)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
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
                    "APDU TERMINAL",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF00FF41)
                )
                
                Text(
                    "${viewModel.apduLog.size} cmds",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666)
                )
            }
            
            // Large Terminal Window
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(
                        Color(0xFF000000),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                if (viewModel.apduLog.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            ">>> Waiting for card communication...",
                            color = Color(0xFF00FF41),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        reverseLayout = true
                    ) {
                        items(viewModel.apduLog.takeLast(20)) { apduEntry ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // TX (Transmit) - Command in GREEN
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        "TX>",
                                        color = Color(0xFF00FF41),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.width(35.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            apduEntry.command,
                                            color = Color(0xFF00FF41),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        )
                                        Text(
                                            apduEntry.description,
                                            color = Color(0xFF666666),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                                
                                // RX (Receive) - Response in BLUE
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        "RX<",
                                        color = Color(0xFF2196F3),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.width(35.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${apduEntry.response} [${apduEntry.statusWord}]",
                                            color = Color(0xFF2196F3),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        )
                                        Text(
                                            "${apduEntry.executionTimeMs}ms",
                                            color = Color(0xFF666666),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Real-time EMV Data Display Section
 * Shows parsed EMV fields as they are extracted from card responses
 */
@Composable
private fun EmvDataDisplaySection(viewModel: CardReadingViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EMV DATA EXTRACTED",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF00)
                    )
                )
                
                Text(
                    text = "${viewModel.parsedEmvFields.size} fields",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF666666)
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Display parsed EMV fields in a grid-like layout
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Group fields by category for better organization
                val cardData = viewModel.parsedEmvFields.filter { (key, _) -> 
                    key in listOf("pan", "expiry_date", "cardholder_name", "track2", "service_code")
                }
                val appData = viewModel.parsedEmvFields.filter { (key, _) -> 
                    key in listOf("aip", "afl", "application_usage_control", "application_version", "df_name")
                }
                val cryptoData = viewModel.parsedEmvFields.filter { (key, _) -> 
                    key in listOf("application_cryptogram", "cryptogram_information_data", "application_transaction_counter", "issuer_application_data")
                }
                
                if (cardData.isNotEmpty()) {
                    item {
                        Text(
                            text = "CARD DATA",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFF00FFFF),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(cardData.toList()) { (key, value) ->
                        EmvFieldRow(key, value)
                    }
                }
                
                if (appData.isNotEmpty()) {
                    item {
                        Text(
                            text = "APPLICATION DATA",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFF00FFFF),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(appData.toList()) { (key, value) ->
                        EmvFieldRow(key, value)
                    }
                }
                
                if (cryptoData.isNotEmpty()) {
                    item {
                        Text(
                            text = "CRYPTOGRAPHIC DATA",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFF00FFFF),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(cryptoData.toList()) { (key, value) ->
                        EmvFieldRow(key, value)
                    }
                }
                
                // ROCA Vulnerability Analysis Section
                val rocaResults = EmvTlvParser.getRocaAnalysisResults()
                if (rocaResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "🛡️ ROCA VULNERABILITY ANALYSIS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFFFF5722),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(rocaResults.toList()) { (tagId, analysisResult) ->
                        RocaVulnerabilityCard(
                            analysisResult = analysisResult,
                            modifier = Modifier.padding(vertical = 4.dp),
                            onFactorDetails = {
                                // Show detailed factorization dialog
                                // Could implement a dialog showing full P and Q factors
                            }
                        )
                    }
                }
                
                // Show remaining fields
                val remainingFields = viewModel.parsedEmvFields.filter { (key, _) ->
                    key !in (cardData.keys + appData.keys + cryptoData.keys) && !key.startsWith("raw_")
                }
                
                if (remainingFields.isNotEmpty()) {
                    item {
                        Text(
                            text = "OTHER EMV FIELDS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFF00FFFF),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(remainingFields.toList()) { (key, value) ->
                        EmvFieldRow(key, value)
                    }
                }
            }
        }
    }
}

/**
 * Individual EMV field row display
 */
@Composable
private fun EmvFieldRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key.uppercase().replace("_", " "),
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF888888),
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF00FF00),
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End
        )
    }
}