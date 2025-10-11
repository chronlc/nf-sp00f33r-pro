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
import com.nfsp00f33r.app.cardreading.EmvTlvParser
import com.nfsp00f33r.app.cardreading.EmvTagDictionary
import com.nfsp00f33r.app.ui.theme.CardReadingColors
import com.nfsp00f33r.app.ui.theme.CardReadingSpacing
import com.nfsp00f33r.app.ui.theme.CardReadingRadius
import com.nfsp00f33r.app.ui.theme.CardReadingDimensions

/**
 * SLEEK DATA-FOCUSED EMV Card Reading Screen
 * Clean, professional design with compact controls and large display area
 */
@Composable
fun CardReadingScreen() {
    val context = LocalContext.current
    
    // ViewModel creation
    val viewModel: CardReadingViewModel = remember {
        CardReadingViewModel.Factory(context).create(CardReadingViewModel::class.java)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardReadingColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CardReadingSpacing.Medium)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Medium)
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
        colors = CardDefaults.cardColors(containerColor = CardReadingColors.CardBackground),
        shape = RoundedCornerShape(CardReadingRadius.Large),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(CardReadingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Medium)
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
                color = CardReadingColors.SuccessGreen
            )                // Reader Status Chip
                Surface(
                    color = if (viewModel.selectedReader != null) CardReadingColors.SafeBackground else CardReadingColors.VulnerableBackground,
                    shape = RoundedCornerShape(CardReadingRadius.ExtraLarge)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = CardReadingSpacing.Medium, vertical = CardReadingSpacing.Small / 2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small / 2)
                    ) {
                        Icon(
                            if (viewModel.selectedReader != null) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = "Reader Status",
                            tint = if (viewModel.selectedReader != null) CardReadingColors.SuccessGreen else CardReadingColors.BrightRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            viewModel.selectedReader?.let { viewModel.getReaderDisplayName(it) } ?: "No Reader",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (viewModel.selectedReader != null) CardReadingColors.SuccessGreen else CardReadingColors.BrightRed
                        )
                    }
                }
            }
            
            // Data Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DataStat("State", viewModel.scanState.name, CardReadingColors.SuccessGreen)
            DataStat("Cards", "${viewModel.scannedCards.size}", CardReadingColors.LightBlue)
            DataStat("APDUs", "${viewModel.apduLog.size}", CardReadingColors.WarningOrange)
            DataStat("NFC", if (com.nfsp00f33r.app.activities.MainActivity.currentNfcTag != null) "DETECTED" else "WAITING", 
                if (com.nfsp00f33r.app.activities.MainActivity.currentNfcTag != null) CardReadingColors.BrightGreen else CardReadingColors.Purple)
            // ROCA vulnerability status
            DataStat(
                "ROCA",
                if (viewModel.isRocaVulnerable) "VULN" else if (viewModel.rocaVulnerabilityStatus != "Not checked") "SAFE" else "N/A",
                if (viewModel.isRocaVulnerable) CardReadingColors.DangerRed else if (viewModel.rocaVulnerabilityStatus != "Not checked") CardReadingColors.BrightGreen else CardReadingColors.TextTertiary
            )
            }
            
            // NFC Debug Info
            Text(
                com.nfsp00f33r.app.activities.MainActivity.nfcDebugMessage,
                style = MaterialTheme.typography.bodySmall,
                color = CardReadingColors.TextSecondary,
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
            color = CardReadingColors.TextSecondary
        )
    }
}

@Composable
private fun ControlPanelCard(viewModel: CardReadingViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardReadingColors.CardBackground),
        shape = RoundedCornerShape(CardReadingRadius.Large),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(CardReadingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Large)
        ) {
            // Reader & Technology Selection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Medium)
            ) {
                // Reader Selection
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small / 2)
                ) {
                    Text(
                        "READER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = CardReadingColors.TextSecondary
                    )
                    
                    var readerExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { readerExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(CardReadingDimensions.ButtonHeightSmall),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardReadingColors.ButtonBackground,
                                contentColor = CardReadingColors.TextPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardReadingColors.BorderDark),
                            contentPadding = PaddingValues(horizontal = CardReadingSpacing.Medium, vertical = 0.dp)
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
                            modifier = Modifier.background(CardReadingColors.ButtonBackground)
                        ) {
                            viewModel.availableReaders.forEach { reader ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            viewModel.getReaderDisplayName(reader),
                                            color = CardReadingColors.TextPrimary,
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
                    verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small / 2)
                ) {
                    Text(
                        "PROTOCOL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = CardReadingColors.TextSecondary
                    )
                    
                    var techExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { techExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(CardReadingDimensions.ButtonHeightSmall),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardReadingColors.ButtonBackground,
                                contentColor = CardReadingColors.TextPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardReadingColors.BorderDark),
                            contentPadding = PaddingValues(horizontal = CardReadingSpacing.Medium, vertical = 0.dp)
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
                            modifier = Modifier.background(CardReadingColors.ButtonBackground)
                        ) {
                            listOf("EMV/ISO-DEP", "Auto-Detect").forEach { tech ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            tech,
                                            color = CardReadingColors.TextPrimary,
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
                CardReadingViewModel.ScanState.SCANNING -> CardReadingColors.ErrorRed
                CardReadingViewModel.ScanState.IDLE -> CardReadingColors.SuccessGreen
                else -> CardReadingColors.TextTertiary
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
                    .height(CardReadingDimensions.ButtonHeightMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scanButtonColor
                ),
                shape = RoundedCornerShape(CardReadingRadius.Medium),
                contentPadding = PaddingValues(horizontal = CardReadingSpacing.Large, vertical = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small)
                ) {
                    Icon(
                        if (viewModel.scanState == CardReadingViewModel.ScanState.SCANNING) 
                            Icons.Default.Stop 
                        else 
                            Icons.Default.PlayArrow,
                        contentDescription = scanButtonText,
                        tint = CardReadingColors.Background,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        scanButtonText,
                        color = CardReadingColors.Background,
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
                    color = CardReadingColors.TextSecondary,
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
                CardReadingColors.VulnerableBackground // Dark red for vulnerable
            } else {
                CardReadingColors.SafeBackground // Dark green for safe
            }
        ),
        shape = RoundedCornerShape(CardReadingRadius.Large),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardReadingSpacing.Large),
            horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Medium),
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
                    CardReadingColors.BrightRed // Bright red
                } else {
                    CardReadingColors.SuccessGreen // Bright green
                },
                modifier = Modifier.size(CardReadingSpacing.Huge)
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "ROCA SECURITY CHECK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = CardReadingColors.TextSecondary
                )
                Text(
                    viewModel.rocaVulnerabilityStatus ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (viewModel.isRocaVulnerable) {
                        CardReadingColors.BrightRed
                    } else {
                        CardReadingColors.SuccessGreen
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveCardsSection(viewModel: CardReadingViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Medium)
    ) {
        Text(
            "ACTIVE CARDS (${viewModel.scannedCards.size})",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CardReadingColors.SuccessGreen
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Large),
            contentPadding = PaddingValues(horizontal = CardReadingSpacing.Tiny)
        ) {
            items(viewModel.scannedCards) { card ->
                VirtualCardView(card = card)
            }
        }
        
        // Pagination indicator if more than 3 cards
        if (viewModel.scannedCards.size > 3) {
            Spacer(modifier = Modifier.height(CardReadingSpacing.Small))
            Text(
                "${viewModel.scannedCards.size} cards scanned - scroll to view all",
                style = MaterialTheme.typography.bodySmall,
                color = CardReadingColors.TextTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ApduTerminalSection(viewModel: CardReadingViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardReadingColors.CardBackground),
        shape = RoundedCornerShape(CardReadingRadius.Large),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(CardReadingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Medium)
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
                    color = CardReadingColors.SuccessGreen
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small)
                ) {
                    Text(
                        "${viewModel.apduLog.size} cmds",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardReadingColors.TextTertiary
                    )

                }
            }
            
            // Large Terminal Window
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CardReadingDimensions.TerminalHeight)
                    .background(
                        CardReadingColors.TerminalBackground,
                        RoundedCornerShape(CardReadingRadius.Large)
                    )
                    .padding(CardReadingSpacing.Medium)
            ) {
                if (viewModel.apduLog.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            ">>> Waiting for card communication...",
                            color = CardReadingColors.SuccessGreen,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small / 2),
                        reverseLayout = false // Show chronological order (PPSE → AID → GPO → Records)
                    ) {
                        items(viewModel.apduLog.takeLast(20)) { apduEntry ->
                            ApduLogItemParsed(apduEntry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApduLogItemParsed(apduEntry: com.nfsp00f33r.app.data.ApduLogEntry) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // TX (Transmit) - Command in GREEN
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "TX>",
                color = CardReadingColors.SuccessGreen,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.width(35.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    apduEntry.command,
                    color = CardReadingColors.SuccessGreen,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
                // Enhanced description with parsed command details
                val enhancedDesc = EmvTagDictionary.enhanceApduDescription(
                    apduEntry.command,
                    apduEntry.response,
                    apduEntry.description
                )
                Text(
                    enhancedDesc,
                    color = CardReadingColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp
                    )
                )
            }
        }
        
        // RX (Receive) - Response in BLUE with status word decoding
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "RX<",
                color = CardReadingColors.InfoBlue,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.width(35.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small / 2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (apduEntry.response.length > 40) 
                            "${apduEntry.response.take(40)}..." 
                        else 
                            apduEntry.response,
                        color = CardReadingColors.InfoBlue,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Status Word with color coding
                    val (swColor, swDesc) = decodeStatusWord(apduEntry.statusWord)
                    Surface(
                        color = swColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(CardReadingRadius.Small)
                    ) {
                        Text(
                            apduEntry.statusWord,
                            color = swColor,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small)
                ) {
                    Text(
                        decodeStatusWord(apduEntry.statusWord).second,
                        color = CardReadingColors.TextTertiary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 9.sp
                        )
                    )
                    Text(
                        "⏱ ${apduEntry.executionTimeMs}ms",
                        color = CardReadingColors.TextTertiary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }
        
        // Parse response for EMV tags if data length > 0
        if (apduEntry.response.length > 4 && apduEntry.statusWord == "9000") {
            val parsedTags = parseApduResponseTags(apduEntry.response)
            if (parsedTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(start = 35.dp, top = 2.dp)
                ) {
                    Column {
                        parsedTags.take(3).forEach { (tag, desc) ->
                            Text(
                                "  ├─ $tag: $desc",
                                color = CardReadingColors.WarningOrange,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            )
                        }
                        if (parsedTags.size > 3) {
                            Text(
                                "  └─ +${parsedTags.size - 3} more tags",
                                color = CardReadingColors.TextTertiary,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
    }
}

/**
 * Decode ISO 7816-4 status words with color coding
 */
private fun decodeStatusWord(sw: String): Pair<Color, String> {
    return when (sw.uppercase()) {
        "9000" -> Pair(CardReadingColors.BrightGreen, "Success")
        "6100" -> Pair(CardReadingColors.InfoBlue, "More data available")
        "6283" -> Pair(CardReadingColors.Orange, "File invalidated")
        "6284" -> Pair(CardReadingColors.Orange, "Selected file in termination state")
        "6300" -> Pair(CardReadingColors.Orange, "Authentication failed")
        "6400" -> Pair(CardReadingColors.DangerRed, "State memory unchanged")
        "6581" -> Pair(CardReadingColors.DangerRed, "Memory failure")
        "6700" -> Pair(CardReadingColors.DangerRed, "Wrong length")
        "6800" -> Pair(CardReadingColors.DangerRed, "Functions not supported")
        "6900" -> Pair(CardReadingColors.DangerRed, "Command not allowed")
        "6982" -> Pair(CardReadingColors.DangerRed, "Security status not satisfied")
        "6983" -> Pair(CardReadingColors.DangerRed, "Authentication method blocked")
        "6985" -> Pair(CardReadingColors.DangerRed, "No current EF selected")
        "6986" -> Pair(CardReadingColors.DangerRed, "No PIN defined")
        "6A80" -> Pair(CardReadingColors.DangerRed, "Incorrect parameters")
        "6A81" -> Pair(CardReadingColors.DangerRed, "Function not supported")
        "6A82" -> Pair(CardReadingColors.DangerRed, "File not found")
        "6A83" -> Pair(CardReadingColors.DangerRed, "Record not found")
        "6A84" -> Pair(CardReadingColors.DangerRed, "Not enough memory")
        "6A86" -> Pair(CardReadingColors.DangerRed, "Incorrect P1/P2")
        "6A88" -> Pair(CardReadingColors.DangerRed, "Referenced data not found")
        "6B00" -> Pair(CardReadingColors.DangerRed, "Wrong parameters P1-P2")
        "6C00" -> Pair(CardReadingColors.DangerRed, "Wrong length Le")
        "6D00" -> Pair(CardReadingColors.DangerRed, "Instruction not supported")
        "6E00" -> Pair(CardReadingColors.DangerRed, "Class not supported")
        "6F00" -> Pair(CardReadingColors.DangerRed, "Unknown error")
        else -> {
            if (sw.startsWith("61")) {
                Pair(CardReadingColors.InfoBlue, "${sw.substring(2).toIntOrNull(16) ?: 0} bytes available")
            } else if (sw.startsWith("6C")) {
                Pair(CardReadingColors.Orange, "Wrong length, correct: ${sw.substring(2)}")
            } else {
                Pair(CardReadingColors.TextSecondary, "Unknown status")
            }
        }
    }
}

/**
 * Parse APDU response for EMV tags using existing EmvTlvParser
 */
private fun parseApduResponseTags(response: String): List<Pair<String, String>> {
    if (response.length < 4) return emptyList()
    
    try {
        val responseBytes = response.chunked(2)
            .mapNotNull { it.toIntOrNull(16)?.toByte() }
            .toByteArray()
        
        val parseResult = EmvTlvParser.parseEmvTlvData(responseBytes, "APDU", validateTags = true)
        
        return parseResult.tags.map { (tag, value) ->
            val description = EmvTagDictionary.getTagDescription(tag)
            Pair(tag, description)
        }
    } catch (e: Exception) {
        return emptyList()
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
        shape = RoundedCornerShape(CardReadingRadius.Large)
    ) {
        Column(
            modifier = Modifier.padding(CardReadingSpacing.Large)
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
                        color = CardReadingColors.SuccessGreen
                    )
                )
                
                Text(
                    text = "${viewModel.parsedEmvFields.size} fields",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = CardReadingColors.TextTertiary
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Display parsed EMV fields in a grid-like layout
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Tiny)
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
                                color = CardReadingColors.Cyan,
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
                                color = CardReadingColors.Cyan,
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
                                color = CardReadingColors.Cyan,
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
                
                // Show remaining fields grouped by TAG with context
                val remainingFields = viewModel.parsedEmvFields.filter { (key, _) ->
                    key !in (cardData.keys + appData.keys + cryptoData.keys) && !key.startsWith("raw_")
                }
                
                if (remainingFields.isNotEmpty()) {
                    item {
                        Text(
                            text = "OTHER EMV FIELDS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = CardReadingColors.Cyan,
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
 * Format: TAG# - Tag Name
 */
@Composable
private fun EmvFieldRow(key: String, value: String) {
    // Format tag display as "TAG# - Tag Name"
    val displayKey = if (key.matches(Regex("^[0-9A-F]+$"))) {
        // It's a hex tag, get description from dictionary
        val tagName = EmvTagDictionary.getTagDescription(key)
        "$key - $tagName"
    } else {
        // It's already a friendly name
        key.uppercase().replace("_", " ")
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = displayKey,
            style = MaterialTheme.typography.bodySmall.copy(
                color = CardReadingColors.TextSecondary,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.weight(1.5f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = CardReadingColors.SuccessGreen,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End
        )
    }
}