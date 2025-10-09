package com.nfsp00f33r.app.screens.emulation

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfsp00f33r.app.R
import com.nfsp00f33r.app.data.VirtualCard
import com.nfsp00f33r.app.data.EmvWorkflow

@Composable
fun EmulationScreen() {
    var hceServiceActive by remember { mutableStateOf(false) }
    var selectedCard by remember { mutableStateOf<VirtualCard?>(null) }
    var selectedWorkflow by remember { mutableStateOf<EmvWorkflow?>(null) }
    var selectedCryptoMode by remember { mutableStateOf("ARQC") }
    
    val availableCards = listOf(
        VirtualCard("RESEARCH CARD", "4154 **** **** 3556", "02/29", 87, "VISA"),
        VirtualCard("TEST PROFILE", "5555 **** **** 4444", "12/28", 94, "MASTERCARD"),
        VirtualCard("DEMO ACCOUNT", "3782 **** **** 1007", "08/27", 76, "AMEX")
    )
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Sleek background
        Image(
            painter = painterResource(id = R.drawable.nfspoof_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.05f)
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sleek Header with Status
            EmulationHeaderCard(hceServiceActive)
            
            // HCE Service Control
            HceServiceControlCard(
                isActive = hceServiceActive,
                onToggle = { hceServiceActive = !hceServiceActive }
            )
            
            // Card Selection with Stats
            CardSelectionCard(
                selectedCard = selectedCard,
                availableCards = availableCards,
                onCardSelected = { selectedCard = it }
            )
            
            // EMV Workflow Selection
            WorkflowSelectionCard(
                selectedWorkflow = selectedWorkflow,
                onWorkflowSelected = { selectedWorkflow = it }
            )
            
            // Cryptogram Strategy
            CryptogramStrategyCard(
                selectedMode = selectedCryptoMode,
                onModeSelected = { selectedCryptoMode = it }
            )
            
            // Emulation Status & Controls
            EmulationControlCard(
                canEmulate = selectedCard != null && selectedWorkflow != null,
                isActive = hceServiceActive
            )
        }
    }
}

@Composable
private fun EmulationHeaderCard(isActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121717)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            "EMV Payment Emulation",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            "Professional Grade Card Emulator",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFBBBBBB)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Status Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFF4CAF50) else Color(0xFF888888),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        if (isActive) "System Ready for Emulation" else "System Standby",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (isActive) Color(0xFF4CAF50) else Color(0xFF888888)
                    )
                }
            }
        }
    }
}

@Composable
private fun HceServiceControlCard(
    isActive: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF1A3A1A) else Color(0xFF1B1F1F)
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Host Card Emulation Service",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        if (isActive) "Active - Broadcasting virtual card data" else "Inactive - No card emulation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) Color(0xFF81C784) else Color(0xFF888888)
                    )
                }
                
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                        uncheckedThumbColor = Color(0xFF666666),
                        uncheckedTrackColor = Color(0xFF333333)
                    )
                )
            }
            
            if (isActive) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF4CAF50),
                    trackColor = Color(0xFF333333)
                )
            }
        }
    }
}

@Composable
private fun CardSelectionCard(
    selectedCard: VirtualCard?,
    availableCards: List<VirtualCard>,
    onCardSelected: (VirtualCard?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1F1F)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Payment Profile Selection",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF4CAF50)
                )
            }
            
            if (selectedCard != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A4A2A)),
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
                                selectedCard.cardholderName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                "${selectedCard.maskedPan} • ${selectedCard.cardType}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFBBBBBB)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "APDUs:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF888888)
                                )
                                Text(
                                    "${selectedCard.apduCount}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = when {
                                        selectedCard.apduCount >= 50 -> Color(0xFF4CAF50)
                                        selectedCard.apduCount >= 20 -> Color(0xFFFFC107)
                                        else -> Color(0xFFFF9800)
                                    }
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(
                                onClick = { onCardSelected(null) }
                            ) {
                                Text("Change", color = Color(0xFF4CAF50))
                            }
                        }
                    }
                }
            } else {
                Text(
                    "Select a payment profile to emulate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableCards) { card ->
                        Card(
                            modifier = Modifier.width(200.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                            shape = RoundedCornerShape(8.dp),
                            onClick = { onCardSelected(card) }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        card.cardType,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        "${card.apduCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            card.apduCount >= 50 -> Color(0xFF4CAF50)
                                            card.apduCount >= 20 -> Color(0xFFFFC107)
                                            else -> Color(0xFFFF9800)
                                        }
                                    )
                                }
                                Text(
                                    card.cardholderName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    card.maskedPan,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    color = Color(0xFFCCCCCC)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowSelectionCard(
    selectedWorkflow: EmvWorkflow?,
    onWorkflowSelected: (EmvWorkflow) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1F1F)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "EMV Workflow Selection",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF4CAF50)
                )
            }
            
            if (selectedWorkflow != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A4A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            selectedWorkflow.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            selectedWorkflow.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFBBBBBB)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "TTQ: ${selectedWorkflow.ttqValue}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                color = Color(0xFF4CAF50)
                            )
                            TextButton(
                                onClick = { onWorkflowSelected(EmvWorkflow.STANDARD_CONTACTLESS) }
                            ) {
                                Text("Change", color = Color(0xFF4CAF50))
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(EmvWorkflow.getAllWorkflows()) { workflow ->
                        Card(
                            modifier = Modifier.width(180.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                            shape = RoundedCornerShape(8.dp),
                            onClick = { onWorkflowSelected(workflow) }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    workflow.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    workflow.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFBBBBBB),
                                    maxLines = 2
                                )
                                Text(
                                    "TTQ: ${workflow.ttqValue}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CryptogramStrategyCard(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1F1F)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Cryptogram Strategy",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF4CAF50)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CryptoModeChip(
                    label = "ARQC Mode",
                    description = "PIN Approval",
                    icon = Icons.Default.Lock,
                    isSelected = selectedMode == "ARQC",
                    onClick = { onModeSelected("ARQC") },
                    modifier = Modifier.weight(1f)
                )
                CryptoModeChip(
                    label = "TC Mode", 
                    description = "Direct Approval",
                    icon = Icons.Default.CheckCircle,
                    isSelected = selectedMode == "TC",
                    onClick = { onModeSelected("TC") },
                    modifier = Modifier.weight(1f)
                )
                CryptoModeChip(
                    label = "Magstripe",
                    description = "Fallback Mode", 
                    icon = Icons.Default.CreditCard,
                    isSelected = selectedMode == "MAGSTRIPE",
                    onClick = { onModeSelected("MAGSTRIPE") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CryptoModeChip(
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2A4A2A) else Color(0xFF2C2C2C)
        ),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF4CAF50) else Color(0xFF888888),
                modifier = Modifier.size(24.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) Color(0xFF4CAF50) else Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) Color(0xFFBBBBBB) else Color(0xFF888888),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmulationControlCard(
    canEmulate: Boolean,
    isActive: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (canEmulate && isActive) Color(0xFF1A3A1A) else Color(0xFF1B1F1F)
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Emulation Status",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF4CAF50)
                )
            }
            
            val statusText = when {
                !isActive -> "❌ HCE Service Inactive - Enable service to start emulation"
                !canEmulate -> "⚠️ Missing Configuration - Select card profile and workflow"
                else -> "✅ Ready for Payment Emulation - Tap phone to POS terminal"
            }
            
            Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !isActive -> Color(0xFFFF5252)
                    !canEmulate -> Color(0xFFFFC107)
                    else -> Color(0xFF4CAF50)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (canEmulate && isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { /* Start emulation logs */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Monitor")
                    }
                    
                    Button(
                        onClick = { /* Start test transaction */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Pay")
                    }
                }
            }
        }
    }
}

