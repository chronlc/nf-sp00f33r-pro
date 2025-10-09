package com.nfsp00f33r.app.screens.database

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nfsp00f33r.app.R
import com.nfsp00f33r.app.data.DatabaseCard
import com.nfsp00f33r.app.components.VirtualCardView
import com.nfsp00f33r.app.ui.components.RocaVulnerabilityBadge
import com.nfsp00f33r.app.cardreading.EmvTlvParser

@Composable
fun DatabaseScreen(
    viewModel: DatabaseViewModel = viewModel()
) {
    var showImportDialog by remember { mutableStateOf(false) }
    var showApduBreakdown by remember { mutableStateOf(false) }
    var showCardDetails by remember { mutableStateOf(false) }
    var selectedCardProfile by remember { mutableStateOf<com.nfsp00f33r.app.models.CardProfile?>(null) }
    
    // Get real data from ViewModel (connected to CardProfileManager)
    val cardProfiles = viewModel.filteredCards
    val searchQuery = viewModel.searchQuery
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background
        Image(
            painter = painterResource(id = R.drawable.nfspoof3),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.1f)
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Card Database",
                        color = Color(0xFF4CAF50),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (viewModel.rocaScanResult != null) {
                        Text(
                            viewModel.getRocaScanSummary(),
                            color = Color(0xFF888888),
                            fontSize = 11.sp
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ROCA Scan Button
                    IconButton(
                        onClick = { viewModel.scanAllCardsForRoca() },
                        enabled = !viewModel.isRocaScanning
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "ROCA Scan",
                            tint = if (viewModel.isRocaScanning) Color(0xFFFFAA00) else Color(0xFF4CAF50)
                        )
                    }
                    
                    IconButton(
                        onClick = { showImportDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Upload,
                            contentDescription = "Import",
                            tint = Color(0xFF4CAF50)
                        )
                    }
                    
                    IconButton(
                        onClick = { 
                            viewModel.exportCardData { success, message ->
                                if (success) {
                                    // Show success message
                                    android.util.Log.i("DatabaseScreen", "Export successful: $message")
                                } else {
                                    // Show error message
                                    android.util.Log.e("DatabaseScreen", "Export failed: $message")
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Export",
                            tint = Color(0xFF4CAF50)
                        )
                    }
                }
            }
            
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search cards...", color = Color(0xFF888888)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF4CAF50))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4CAF50),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DatabaseStatCard(
                    title = "Total Cards",
                    value = viewModel.totalCards.toString(),
                    icon = Icons.Default.CreditCard,
                    modifier = Modifier.weight(1f)
                )
                DatabaseStatCard(
                    title = "Encrypted",
                    value = viewModel.encryptedCards.toString(),
                    icon = Icons.Default.Lock,
                    modifier = Modifier.weight(1f)
                )
                DatabaseStatCard(
                    title = "ROCA Vuln",
                    value = if (viewModel.rocaScanResult != null) {
                        viewModel.rocaScanResult?.vulnerableCards?.toString() ?: "0"
                    } else {
                        "?"
                    },
                    icon = Icons.Default.Security,
                    modifier = Modifier.weight(1f),
                    valueColor = if (viewModel.rocaScanResult != null && (viewModel.rocaScanResult?.vulnerableCards ?: 0) > 0) {
                        Color(0xFFF44336)
                    } else if (viewModel.rocaScanResult != null) {
                        Color(0xFF4CAF50)
                    } else {
                        Color.White
                    }
                )
                DatabaseStatCard(
                    title = "Categories",
                    value = viewModel.uniqueCategories.toString(),
                    icon = Icons.Default.Category,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Cards list
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cardProfiles) { cardProfile ->
                        RealDatabaseCardItem(
                            cardProfile = cardProfile,
                            viewModel = viewModel,
                            onDelete = { viewModel.deleteCard(cardProfile.id) },
                            onShowApduBreakdown = {
                                selectedCardProfile = cardProfile
                                showApduBreakdown = true
                            },
                            onShowFullDetails = {
                                selectedCardProfile = cardProfile
                                showCardDetails = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { 
                showImportDialog = false
                // Import functionality - placeholder for now
                // TODO: Implement proper import UI
            }
        )
    }
    
    if (showApduBreakdown && selectedCardProfile != null) {
        ApduBreakdownDialog(
            cardProfile = selectedCardProfile!!,
            onDismiss = {
                showApduBreakdown = false
                selectedCardProfile = null
            }
        )
    }
    
    if (showCardDetails && selectedCardProfile != null) {
        FullCardDetailsDialog(
            cardProfile = selectedCardProfile!!,
            onDismiss = {
                showCardDetails = false
                selectedCardProfile = null
            },
            onShowApduBreakdown = {
                showCardDetails = false
                showApduBreakdown = true
            }
        )
    }
}

@Composable
private fun DatabaseStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                color = valueColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                title,
                color = Color(0xFF888888),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun DatabaseCardItem(card: DatabaseCard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
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
                    card.cardholderName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${card.pan} • ${card.expiry}",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
                Text(
                    "${card.cardType} • ${card.category} • ${card.apduCount} APDUs",
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                if (card.isEncrypted) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = Color(0xFFFFAA00),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    card.lastUsed,
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun RealDatabaseCardItem(
    cardProfile: com.nfsp00f33r.app.models.CardProfile,
    viewModel: DatabaseViewModel,
    onDelete: () -> Unit,
    onShowApduBreakdown: () -> Unit,
    onShowFullDetails: () -> Unit
) {
    val emvData = cardProfile.emvCardData
    val pan = emvData.getUnmaskedPan()
    val cardholderName = emvData.cardholderName ?: "UNKNOWN CARDHOLDER"
    val expiryDate = emvData.expiryDate ?: "**/**"
    val cardType = viewModel.getCardType(pan)
    val apduCount = cardProfile.apduLogs.size
    val lastUsed = viewModel.getRelativeTime(cardProfile)
    val isEncrypted = emvData.track2Data?.isNotEmpty() == true || emvData.applicationCryptogram?.isNotEmpty() == true
    
    Column {
        // Virtual Card Visual - Clickable for full details
        Card(
            onClick = onShowFullDetails,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            VirtualCardView(
                card = com.nfsp00f33r.app.data.VirtualCard(
                    cardholderName = cardholderName,
                    maskedPan = if (pan.length > 8) "${pan.take(4)} **** **** ${pan.takeLast(4)}" else pan,
                    expiryDate = expiryDate,
                    cardType = cardType,
                    apduCount = apduCount,
                    isEncrypted = isEncrypted,
                    lastUsed = lastUsed,
                    category = "PAYMENT"
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Card Details and Actions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
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
                        "$cardType • ${emvData.applicationLabel}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$apduCount APDUs • Last scan: $lastUsed",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ROCA Vulnerability Badge
                    val rocaScanResult = viewModel.rocaScanResult
                    if (rocaScanResult != null) {
                        val cardResult = rocaScanResult.cardResults.find { it.cardProfile.profileId == cardProfile.id }
                        val isVulnerable = cardResult?.rocaResult?.isVulnerable == true
                        val hasRsaKey = cardResult?.hasRsaKey == true
                        val priority = cardResult?.priority
                        
                        if (isVulnerable) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when (priority) {
                                        com.nfsp00f33r.app.security.roca.RocaBatchScanner.VulnerabilityPriority.CRITICAL -> Color(0xFFD32F2F)
                                        com.nfsp00f33r.app.security.roca.RocaBatchScanner.VulnerabilityPriority.HIGH -> Color(0xFFF44336)
                                        com.nfsp00f33r.app.security.roca.RocaBatchScanner.VulnerabilityPriority.MEDIUM -> Color(0xFFFF9800)
                                        else -> Color(0xFFF44336)
                                    }
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    when (priority) {
                                        com.nfsp00f33r.app.security.roca.RocaBatchScanner.VulnerabilityPriority.CRITICAL -> "CRITICAL"
                                        com.nfsp00f33r.app.security.roca.RocaBatchScanner.VulnerabilityPriority.HIGH -> "HIGH"
                                        com.nfsp00f33r.app.security.roca.RocaBatchScanner.VulnerabilityPriority.MEDIUM -> "MEDIUM"
                                        else -> "VULN"
                                    },
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (hasRsaKey && !isVulnerable) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50)),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    "SAFE",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    if (isEncrypted) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = Color(0xFFFFAA00),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onShowApduBreakdown,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = "APDU Analysis",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApduBreakdownDialog(
    cardProfile: com.nfsp00f33r.app.models.CardProfile,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = "APDU Analysis",
                    tint = Color(0xFF4CAF50)
                )
                Text("APDU Breakdown Analysis", color = Color.White, fontSize = 18.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Card: ${cardProfile.emvCardData.cardholderName ?: "Unknown"}",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                items(cardProfile.apduLogs) { apduEntry ->
                    ApduBreakdownItem(apduEntry = apduEntry)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF4CAF50))
            }
        },
        containerColor = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}

@Composable
private fun ApduBreakdownItem(apduEntry: com.nfsp00f33r.app.data.ApduLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Command Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    apduEntry.description,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    apduEntry.timestamp,
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
            
            // Command Breakdown
            if (apduEntry.command.isNotEmpty()) {
                ApduCommandBreakdown(command = apduEntry.command)
            }
            
            // Response Breakdown
            if (apduEntry.response.isNotEmpty()) {
                ApduResponseBreakdown(
                    response = apduEntry.response,
                    statusWord = apduEntry.statusWord,
                    description = apduEntry.description
                )
            }
            
            // Execution Time
            if (apduEntry.executionTimeMs > 0) {
                Text(
                    "⏱️ ${apduEntry.executionTimeMs}ms",
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ApduCommandBreakdown(command: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "📤 Command:",
            color = Color(0xFFFFAA00),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        
        if (command.length >= 8) {
            val cla = command.take(2)
            val ins = command.drop(2).take(2)
            val p1 = command.drop(4).take(2)
            val p2 = command.drop(6).take(2)
            val lc = if (command.length > 8) command.drop(8).take(2) else ""
            val data = if (command.length > 10) command.drop(10) else ""
            
            Text(
                "CLA: $cla | INS: $ins | P1: $p1 | P2: $p2${if (lc.isNotEmpty()) " | LC: $lc" else ""}",
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            
            if (data.isNotEmpty()) {
                Text(
                    "Data: ${if (data.length > 32) "${data.take(32)}..." else data}",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            
            // Command Interpretation
            val interpretation = interpretApduCommand(cla, ins, p1, p2)
            if (interpretation.isNotEmpty()) {
                Text(
                    "💡 $interpretation",
                    color = Color(0xFF81C784),
                    fontSize = 10.sp
                )
            }
        } else {
            Text(
                command,
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ApduResponseBreakdown(response: String, statusWord: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "📥 Response:",
            color = Color(0xFF2196F3),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        
        if (response.length > 4) {
            val data = response.dropLast(4)
            val sw = response.takeLast(4)
            
            if (data.isNotEmpty()) {
                Text(
                    "Data: ${if (data.length > 64) "${data.take(64)}..." else data}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                
                // Parse EMV data from response
                val parsedData = parseEmvResponseData(data, description)
                if (parsedData.isNotEmpty()) {
                    parsedData.forEach { (tag, value) ->
                        Text(
                            "  🏷️ $tag: $value",
                            color = Color(0xFF81C784),
                            fontSize = 9.sp
                        )
                    }
                }
            }
            
            Text(
                "Status: $sw (${interpretStatusWord(sw)})",
                color = when (sw) {
                    "9000" -> Color(0xFF4CAF50)
                    else -> Color(0xFFFF5722)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                response,
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

private fun interpretApduCommand(cla: String, ins: String, p1: String, p2: String): String {
    return when (ins.uppercase()) {
        "A4" -> when (p1 + p2) {
            "0400" -> "SELECT by DF Name (AID)"
            "0200" -> "SELECT by File ID"
            else -> "SELECT File"
        }
        "B2" -> "READ RECORD (SFI: ${p2.toIntOrNull(16)?.and(0xF8)?.shr(3) ?: "?"}, Record: ${p1.toIntOrNull(16) ?: "?"})"
        "CA" -> "GET DATA (Tag: $p1$p2)"
        "A8" -> "GET PROCESSING OPTIONS"
        "88" -> "INTERNAL AUTHENTICATE"
        "82" -> "EXTERNAL AUTHENTICATE"
        "84" -> "GET CHALLENGE"
        "70" -> "MANAGE CHANNEL"
        else -> "Unknown Command (INS: $ins)"
    }
}

private fun parseEmvResponseData(data: String, description: String): List<Pair<String, String>> {
    val results = mutableListOf<Pair<String, String>>()
    
    // Simple TLV parsing for common EMV tags
    var i = 0
    while (i < data.length - 4) {
        try {
            val tag = data.substring(i, i + 2)
            val length = data.substring(i + 2, i + 4).toInt(16)
            
            if (i + 4 + (length * 2) <= data.length) {
                val value = data.substring(i + 4, i + 4 + (length * 2))
                
                val tagName = when (tag.uppercase()) {
                    "5A" -> "PAN"
                    "5F20" -> "Cardholder Name"
                    "5F24" -> "Expiry Date"
                    "5F25" -> "Effective Date"
                    "5F28" -> "Issuer Country Code"
                    "5F2A" -> "Transaction Currency Code"
                    "5F34" -> "PAN Sequence Number"
                    "82" -> "Application Interchange Profile"
                    "84" -> "Application Identifier"
                    "87" -> "Application Priority Indicator"
                    "88" -> "Short File Identifier"
                    "8A" -> "Authorization Response Code"
                    "8C" -> "CDOL1"
                    "8D" -> "CDOL2"
                    "8E" -> "CVM List"
                    "8F" -> "Certification Authority Public Key Index"
                    "90" -> "Issuer Public Key Certificate"
                    "92" -> "Issuer Public Key Remainder"
                    "93" -> "Signed Static Application Data"
                    "94" -> "Application File Locator"
                    "95" -> "Terminal Verification Results"
                    "9A" -> "Transaction Date"
                    "9B" -> "Transaction Status Information"
                    "9C" -> "Transaction Type"
                    "9F02" -> "Amount Authorized"
                    "9F03" -> "Amount Other"
                    "9F06" -> "Application Identifier (Terminal)"
                    "9F07" -> "Application Usage Control"
                    "9F08" -> "Application Version Number"
                    "9F09" -> "Application Version Number (Terminal)"
                    "9F0D" -> "Issuer Action Code - Default"
                    "9F0E" -> "Issuer Action Code - Denial"
                    "9F0F" -> "Issuer Action Code - Online"
                    "9F10" -> "Issuer Application Data"
                    "9F11" -> "Issuer Code Table Index"
                    "9F12" -> "Application Preferred Name"
                    "9F13" -> "Last Online ATC Register"
                    "9F17" -> "PIN Try Counter"
                    "9F1A" -> "Terminal Country Code"
                    "9F1E" -> "Interface Device Serial Number"
                    "9F26" -> "Application Cryptogram"
                    "9F27" -> "Cryptogram Information Data"
                    "9F34" -> "CVM Results"
                    "9F35" -> "Terminal Type"
                    "9F36" -> "Application Transaction Counter"
                    "9F37" -> "Unpredictable Number"
                    "9F38" -> "Processing Options Data Object List"
                    "9F40" -> "Additional Terminal Capabilities"
                    "9F41" -> "Transaction Sequence Counter"
                    "9F42" -> "Application Currency Code"
                    "9F43" -> "Application Reference Currency"
                    "9F44" -> "Application Currency Exponent"
                    "9F45" -> "Data Authentication Code"
                    "9F46" -> "ICC Public Key Certificate"
                    "9F47" -> "ICC Public Key Exponent"
                    "9F48" -> "ICC Public Key Remainder"
                    "9F49" -> "Dynamic Data Authentication Data Object List"
                    "9F4A" -> "Static Data Authentication Tag List"
                    "9F4B" -> "Signed Dynamic Application Data"
                    "9F4C" -> "ICC Dynamic Number"
                    else -> "Tag $tag"
                }
                
                val formattedValue = when (tag.uppercase()) {
                    "5A" -> formatPan(value)
                    "5F24" -> formatDate(value)
                    "5F20" -> hexToAscii(value)
                    "9F12" -> hexToAscii(value)
                    else -> value
                }
                
                results.add(tagName to formattedValue)
                i += 4 + (length * 2)
            } else {
                break
            }
        } catch (e: Exception) {
            i += 2 // Skip malformed data
        }
    }
    
    return results
}

private fun formatPan(hex: String): String {
    return if (hex.length >= 8) {
        val pan = hex.replace("F", "")
        "${pan.take(4)} **** **** ${pan.takeLast(4)}"
    } else hex
}

private fun formatDate(hex: String): String {
    return if (hex.length == 6) {
        "${hex.substring(2, 4)}/${hex.substring(0, 2)}"
    } else hex
}

private fun hexToAscii(hex: String): String {
    return try {
        val sb = StringBuilder()
        for (i in hex.indices step 2) {
            val str = hex.substring(i, i + 2)
            val char = str.toInt(16).toChar()
            if (char.isLetterOrDigit() || char == ' ') {
                sb.append(char)
            }
        }
        sb.toString().trim()
    } catch (e: Exception) {
        hex
    }
}

private fun interpretStatusWord(sw: String): String {
    return when (sw.uppercase()) {
        "9000" -> "Success"
        "6100" -> "Response available"
        "6283" -> "Selected file deactivated"
        "6300" -> "Authentication failed"
        "6400" -> "State unchanged"
        "6581" -> "Memory failure"
        "6700" -> "Wrong length"
        "6800" -> "Functions not supported"
        "6900" -> "Command not allowed"
        "6A00" -> "Wrong parameters P1-P2"
        "6A80" -> "Incorrect data"
        "6A81" -> "Function not supported"
        "6A82" -> "File not found"
        "6A83" -> "Record not found"
        "6A84" -> "Not enough space"
        "6A86" -> "Incorrect P1-P2"
        "6A88" -> "Referenced data not found"
        "6B00" -> "Wrong parameters P1-P2"
        "6C00" -> "Wrong Le field"
        "6D00" -> "Instruction not supported"
        "6E00" -> "Class not supported"
        "6F00" -> "No precise diagnosis"
        else -> when {
            sw.startsWith("61") -> "Response available (${sw.drop(2)} bytes)"
            sw.startsWith("62") -> "Warning (non-volatile memory)"
            sw.startsWith("63") -> "Warning (volatile memory)"
            sw.startsWith("64") -> "Error (non-volatile memory)"
            sw.startsWith("65") -> "Error (volatile memory)"
            sw.startsWith("66") -> "Error (security)"
            else -> "Unknown ($sw)"
        }
    }
}

@Composable
private fun FullCardDetailsDialog(
    cardProfile: com.nfsp00f33r.app.models.CardProfile,
    onDismiss: () -> Unit,
    onShowApduBreakdown: () -> Unit
) {
    val emvData = cardProfile.emvCardData
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CreditCard,
                    contentDescription = "Card Details",
                    tint = Color(0xFF4CAF50)
                )
                Text(
                    "Complete Card Details",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card Identity Section
                item {
                    DetailSection(
                        title = "🆔 CARD IDENTITY",
                        content = {
                            DetailRow("Card UID", emvData.cardUid ?: "Not Available")
                            DetailRow("PAN (Full)", emvData.pan ?: "Not Available")
                            DetailRow("PAN (Formatted)", emvData.getUnmaskedPan())
                            DetailRow("Cardholder Name", emvData.cardholderName ?: "Not Available")
                            DetailRow("Expiry Date", emvData.expiryDate ?: "Not Available")
                            DetailRow("Effective Date", emvData.applicationEffectiveDate ?: "Not Available")
                            DetailRow("Expiration Date", emvData.applicationExpirationDate ?: "Not Available")
                        }
                    )
                }
                
                // Application Information Section
                item {
                    DetailSection(
                        title = "📱 APPLICATION INFO",
                        content = {
                            DetailRow("Application ID", emvData.applicationIdentifier ?: "Not Available")
                            DetailRow("Application Label", emvData.applicationLabel.takeIf { it.isNotEmpty() } ?: "Not Available")
                            DetailRow("Application Version", emvData.applicationVersion ?: "Not Available")
                            DetailRow("Application Usage Control", emvData.applicationUsageControl ?: "Not Available")
                            DetailRow("Selected AID", emvData.selectedAid ?: "Not Available")
                            DetailRow("Available AIDs", emvData.availableAids.joinToString(", ").takeIf { it.isNotEmpty() } ?: "Not Available")
                            DetailRow("Kernel Identifier", emvData.kernelIdentifier ?: "Not Available")
                        }
                    )
                }
                
                // Track Data Section
                item {
                    DetailSection(
                        title = "💳 TRACK DATA",
                        content = {
                            DetailRow("Track 2 Data", emvData.track2Data ?: "Not Available")
                            DetailRow("Service Code", emvData.serviceCode ?: "Not Available")
                            DetailRow("Discretionary Data", emvData.discretionaryData ?: "Not Available")
                        }
                    )
                }
                
                // Cryptographic Data Section
                item {
                    DetailSection(
                        title = "🔐 CRYPTOGRAPHIC DATA",
                        content = {
                            DetailRow("Application Cryptogram", emvData.applicationCryptogram ?: "Not Available")
                            DetailRow("Cryptogram Info Data", emvData.cryptogramInformationData ?: "Not Available")
                            DetailRow("Application Transaction Counter", emvData.applicationTransactionCounter ?: "Not Available")
                            DetailRow("Unpredictable Number", emvData.unpredictableNumber ?: "Not Available")
                            DetailRow("Issuer Application Data", emvData.issuerApplicationData ?: "Not Available")
                            DetailRow("Authorization Response Code", emvData.authorizationResponseCode ?: "Not Available")
                            DetailRow("Terminal Verification Results", emvData.terminalVerificationResults ?: "Not Available")
                        }
                    )
                }
                
                // Processing Data Section
                item {
                    DetailSection(
                        title = "⚙️ PROCESSING DATA",
                        content = {
                            DetailRow("Application Interchange Profile", emvData.applicationInterchangeProfile ?: "Not Available")
                            DetailRow("Application File Locator", emvData.applicationFileLocator ?: "Not Available")
                            DetailRow("PDOL", emvData.processingOptionsDataObjectList ?: "Not Available")
                            DetailRow("CDOL1", emvData.cdol1 ?: "Not Available")
                            DetailRow("CDOL2", emvData.cdol2 ?: "Not Available")
                            DetailRow("CVM List", emvData.cardholderVerificationMethodList ?: "Not Available")
                            DetailRow("CVM Results", emvData.cvmResults ?: "Not Available")
                        }
                    )
                }
                
                // Geographic & Currency Data Section
                item {
                    DetailSection(
                        title = "🌍 GEOGRAPHIC & CURRENCY",
                        content = {
                            DetailRow("Issuer Country Code", emvData.issuerCountryCode ?: "Not Available")
                            DetailRow("Currency Code", emvData.currencyCode ?: "Not Available")
                            DetailRow("Application Currency", emvData.applicationCurrencyCode ?: "Not Available")
                            DetailRow("Language Preference", emvData.languagePreference ?: "Not Available")
                        }
                    )
                }
                
                // Transaction Data Section
                item {
                    DetailSection(
                        title = "💰 TRANSACTION DATA",
                        content = {
                            DetailRow("Transaction Status", emvData.transactionStatusInformation ?: "Not Available")
                            DetailRow("Terminal Transaction Qualifiers", emvData.terminalTransactionQualifiers ?: "Not Available")
                            DetailRow("POS Entry Mode", emvData.posEntryMode ?: "Not Available")
                            DetailRow("Terminal Verification Results", emvData.terminalVerificationResults ?: "Not Available")
                        }
                    )
                }
                
                // Security & Certificates Section
                item {
                    DetailSection(
                        title = "🛡️ SECURITY & CERTIFICATES",
                        content = {
                            DetailRow("CA Public Key Index", emvData.certificationAuthorityPublicKeyIndex ?: "Not Available")
                            DetailRow("Issuer Public Key Certificate", emvData.issuerPublicKeyCertificate?.takeIf { it.isNotEmpty() }?.let { if (it.length > 32) "${it.take(32)}..." else it } ?: "Not Available")
                            DetailRow("Issuer Public Key Exponent", emvData.issuerPublicKeyExponent ?: "Not Available")
                            DetailRow("Issuer Public Key Remainder", emvData.issuerPublicKeyRemainder?.takeIf { it.isNotEmpty() }?.let { if (it.length > 32) "${it.take(32)}..." else it } ?: "Not Available")
                            DetailRow("Signed Static App Data", emvData.signedStaticApplicationData?.takeIf { it.isNotEmpty() }?.let { if (it.length > 32) "${it.take(32)}..." else it } ?: "Not Available")
                        }
                    )
                }
                
                // ROCA Vulnerability Analysis Section
                val rocaResults = EmvTlvParser.getRocaAnalysisResults()
                if (rocaResults.isNotEmpty()) {
                    item {
                        DetailSection(
                            title = "🛡️ ROCA VULNERABILITY ANALYSIS (CVE-2017-15361)",
                            content = {
                                rocaResults.forEach { (tagId, analysisResult) ->
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Tag $tagId",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4CAF50)
                                                )
                                            )
                                            RocaVulnerabilityBadge(
                                                isVulnerable = analysisResult.isVulnerable,
                                                confidence = analysisResult.confidence
                                            )
                                        }
                                        
                                        DetailRow("Status", if (analysisResult.isVulnerable) "⚠️ VULNERABLE" else "✅ SAFE")
                                        DetailRow("Confidence", analysisResult.confidence.name)
                                        if (analysisResult.keySize != null) {
                                            DetailRow("Key Size", "${analysisResult.keySize} bits")
                                        }
                                        if (analysisResult.factorAttempt?.successful == true) {
                                            DetailRow("Factorization", "💥 SUCCESS - Private key compromised!")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                
                // Additional EMV Fields Section
                item {
                    DetailSection(
                        title = "📋 ADDITIONAL EMV FIELDS",
                        content = {
                            DetailRow("Issuer Authentication Data", emvData.issuerAuthenticationData ?: "Not Available")
                            DetailRow("Authorization Response Code", emvData.authorizationResponseCode ?: "Not Available")
                            DetailRow("CVM Results", emvData.cvmResults ?: "Not Available")
                            DetailRow("Reading Timestamp", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(emvData.readingTimestamp)))
                        }
                    )
                }
                
                // EMV Tags Map Section
                item {
                    DetailSection(
                        title = "🏷️ ALL EMV TAGS",
                        content = {
                            if (emvData.emvTags.isNotEmpty()) {
                                emvData.emvTags.forEach { (tag, value) ->
                                    DetailRow(tag, value.takeIf { it.isNotEmpty() } ?: "Empty")
                                }
                            } else {
                                DetailRow("No EMV Tags", "No additional tags found")
                            }
                        }
                    )
                }
                
                // Statistics Section
                item {
                    DetailSection(
                        title = "📊 SCAN STATISTICS",
                        content = {
                            DetailRow("Total APDUs", cardProfile.apduLogs.size.toString())
                            DetailRow("Successful Commands", cardProfile.apduLogs.count { it.statusWord == "9000" }.toString())
                            DetailRow("Failed Commands", cardProfile.apduLogs.count { it.statusWord != "9000" }.toString())
                            DetailRow("Average Response Time", "${cardProfile.apduLogs.filter { it.executionTimeMs > 0 }.map { it.executionTimeMs }.average().takeIf { !it.isNaN() }?.toInt() ?: 0}ms")
                            DetailRow("Scan Date", cardProfile.createdTimestamp)
                            DetailRow("Card Profile ID", cardProfile.id)
                        }
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShowApduBreakdown) {
                    Text("APDU Analysis", color = Color(0xFF2196F3))
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color(0xFF4CAF50))
                }
            }
        },
        containerColor = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = Color(0xFFBBBBBB),
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(1.5f)
        )
    }
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Import Cards", color = Color.White)
        },
        text = {
            Text(
                "Select import source for card data:",
                color = Color(0xFF888888)
            )
        },
        confirmButton = {
            TextButton(onClick = onImport) {
                Text("Import JSON", color = Color(0xFF4CAF50))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF888888))
            }
        },
        containerColor = Color(0xFF1A1A1A)
    )
}