package com.nfsp00f33r.app.screens.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nfsp00f33r.app.R
import com.nfsp00f33r.app.components.VirtualCardView
import com.nfsp00f33r.app.components.StatsCard
import com.nfsp00f33r.app.data.VirtualCard
import com.nfsp00f33r.app.hardware.HardwareDetectionService

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(context)
    )
    
    // Show loading state while initializing
    if (!viewModel.isInitialized) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
                Text(
                    "Initializing Hardware Detection...",
                    color = Color(0xFFFFFFFF),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderCard(viewModel.hardwareStatus)
        StatsCardsRow(viewModel.hardwareStatus, viewModel.cardStatistics)
        RecentCardsSection(viewModel.recentCards)
    }
}

@Composable
private fun HeaderCard(hardwareStatus: HardwareDetectionService.HardwareStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Main Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121717)),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.nfspoof_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    alpha = 0.2f
                )

                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "NFC PhreaK BoX",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF4CAF50),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "RFiD TooLKiT",
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        ),
                        color = Color(0xFFFFFFFF),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hardware Score Display with Progress
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Hardware Score:",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFFFFFF)
                            )
                            Text(
                                "${hardwareStatus.hardwareScore}/100",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = when {
                                    hardwareStatus.hardwareScore >= 70 -> Color(0xFF4CAF50)
                                    hardwareStatus.hardwareScore >= 40 -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                }
                            )
                        }
                        
                        LinearProgressIndicator(
                            progress = hardwareStatus.hardwareScore / 100f,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = when {
                                hardwareStatus.hardwareScore >= 70 -> Color(0xFF4CAF50)
                                hardwareStatus.hardwareScore >= 40 -> Color(0xFFFFC107)
                                else -> Color(0xFFF44336)
                            },
                            trackColor = Color(0xFF333333)
                        )
                    }

                    // Status Message
                    Text(
                        hardwareStatus.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFBBBBBB),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Detection Phase
                    Text(
                        "Phase: ${hardwareStatus.detectionPhase}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // PN532 Detailed Status Card
        if (hardwareStatus.pn532Connected || hardwareStatus.pn532ConnectionType != "None") {
            PN532DetailCard(hardwareStatus)
        }
        
        // Hardware Status Grid
        HardwareStatusGrid(hardwareStatus)
    }
}

@Composable
private fun StatusIndicator(label: String, isActive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Default.Circle,
            contentDescription = "$label Status",
            tint = if (isActive) Color(0xFF4CAF50) else Color(0xFF888888),
            modifier = Modifier.size(8.dp)
        )
        Text(
            label,
            color = if (isActive) Color(0xFF4CAF50) else Color(0xFF888888),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PN532DetailCard(hardwareStatus: HardwareDetectionService.HardwareStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1F1F)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "PN532 NFC Module Details",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF4CAF50)
            )
            
            if (hardwareStatus.pn532ConnectionType != "None") {
                DetailRow("Connection Type", hardwareStatus.pn532ConnectionType)
            }
            
            if (hardwareStatus.pn532DeviceAddress.isNotEmpty()) {
                DetailRow("Bluetooth Address", hardwareStatus.pn532DeviceAddress)
            }
            
            if (hardwareStatus.pn532FirmwareVersion.isNotEmpty()) {
                DetailRow("Firmware Version", hardwareStatus.pn532FirmwareVersion)
            }
            
            if (hardwareStatus.pn532ChipVersion.isNotEmpty()) {
                DetailRow("Chip Controller", hardwareStatus.pn532ChipVersion)
            }
            
            if (hardwareStatus.connectionLatency > 0) {
                DetailRow("Response Time", "${hardwareStatus.connectionLatency}ms")
            }
            
            if (hardwareStatus.pn532LastResponse.isNotEmpty()) {
                Column {
                    Text(
                        "Raw Firmware Response:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBBBBBB)
                    )
                    Text(
                        hardwareStatus.pn532LastResponse,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, isMonospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFBBBBBB),
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = if (isMonospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = Color(0xFFFFFFFF),
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun HardwareStatusGrid(hardwareStatus: HardwareDetectionService.HardwareStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1F1F)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Hardware Components",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFFFFFFF)
            )
            
            // Android Native Hardware
            HardwareComponentRow(
                title = "Android NFC Controller",
                status = hardwareStatus.androidNfcStatus,
                details = hardwareStatus.androidNfcDetails
            )
            
            HardwareComponentRow(
                title = "Host Card Emulation Service",
                status = hardwareStatus.hceServiceStatus,
                details = hardwareStatus.hceServiceDetails
            )
            
            HardwareComponentRow(
                title = "Android Bluetooth Stack",
                status = hardwareStatus.androidBluetoothStatus,
                details = hardwareStatus.androidBluetoothDetails
            )
            
            // PN532 External Hardware
            HardwareComponentRow(
                title = "PN532 NFC Module (Bluetooth UART)",
                status = hardwareStatus.pn532BluetoothUartStatus,
                details = hardwareStatus.pn532BluetoothUartDetails
            )
            
            HardwareComponentRow(
                title = "PN532 NFC Module (USB UART)",
                status = hardwareStatus.pn532UsbUartStatus,
                details = hardwareStatus.pn532UsbUartDetails
            )
        }
    }
}

@Composable
private fun HardwareStatusItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    status: String,
    isActive: Boolean,
    details: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF4CAF50) else Color(0xFF888888),
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFFFFF),
            textAlign = TextAlign.Center
        )
        Text(
            status,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = if (isActive) Color(0xFF4CAF50) else Color(0xFF888888),
            textAlign = TextAlign.Center
        )
        if (details.isNotEmpty()) {
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HardwareComponentRow(
    title: String,
    status: String,
    details: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFFFFFFF)
            )
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888),
                    maxLines = 2
                )
            }
        }
        
        Text(
            status,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = when {
                status.contains("Connected", ignoreCase = true) || status.contains("Active", ignoreCase = true) -> Color(0xFF4CAF50)
                status.contains("Ready", ignoreCase = true) || status.contains("Available", ignoreCase = true) -> Color(0xFF2196F3)
                status.contains("Detected", ignoreCase = true) || status.contains("Found", ignoreCase = true) -> Color(0xFF4CAF50)
                status.contains("Searching", ignoreCase = true) || status.contains("Connecting", ignoreCase = true) -> Color(0xFFFFC107)
                status.contains("Error", ignoreCase = true) || status.contains("Failed", ignoreCase = true) -> Color(0xFFF44336)
                else -> Color(0xFF888888)
            },
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun StatsCardsRow(
    hardwareStatus: HardwareDetectionService.HardwareStatus,
    cardStats: DashboardViewModel.CardStatistics
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatsCard(
            title = "Total Cards",
            value = "${cardStats.totalCards}",
            icon = Icons.Default.CreditCard,
            modifier = Modifier.weight(1f)
        )
        StatsCard(
            title = "Scanned Today",
            value = "${cardStats.cardsToday}",
            icon = Icons.Default.Today,
            modifier = Modifier.weight(1f)
        )
        StatsCard(
            title = "Hardware Score",
            value = "${hardwareStatus.hardwareScore}/100",
            icon = when {
                hardwareStatus.hardwareScore >= 90 -> Icons.Default.CheckCircle
                hardwareStatus.hardwareScore >= 70 -> Icons.Default.Verified
                hardwareStatus.hardwareScore >= 40 -> Icons.Default.Warning
                else -> Icons.Default.Error
            },
            modifier = Modifier.weight(1f)
        )
    }
    
    // Second row of stats
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatsCard(
            title = "APDU Commands",
            value = "${cardStats.totalApduCommands}",
            icon = Icons.Default.Code,
            modifier = Modifier.weight(1f)
        )
        StatsCard(
            title = "Card Brands",
            value = "${cardStats.uniqueBrands}",
            icon = Icons.Default.Label,
            modifier = Modifier.weight(1f)
        )
        StatsCard(
            title = "PN532 Status",
            value = if (hardwareStatus.pn532Connected) "Ready" else "Scanning",
            icon = if (hardwareStatus.pn532Connected) Icons.Default.Bluetooth else Icons.Default.BluetoothSearching,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RecentCardsSection(cards: List<VirtualCard>) {
    Column {
        Text(
            "Recent Cards",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF4CAF50),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        if (cards.isEmpty()) {
            // Empty state - no hardcoded fallback
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1F1F)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.CreditCard,
                        contentDescription = "No Cards",
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "No cards scanned yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Start scanning NFC cards to see them here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cards) { card ->
                    VirtualCardView(card = card)
                }
            }
        }
    }
}