package com.nfsp00f33r.app.screens.cardreading

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nfsp00f33r.app.emv.TransactionType
import com.nfsp00f33r.app.emv.TerminalDecision
import com.nfsp00f33r.app.ui.theme.CardReadingColors
import com.nfsp00f33r.app.ui.theme.CardReadingSpacing
import com.nfsp00f33r.app.ui.theme.CardReadingRadius
import com.nfsp00f33r.app.ui.theme.CardReadingDimensions
import java.text.NumberFormat
import java.util.Locale

/**
 * PROXMARK3 TRANSACTION CONTROLS
 * Phase 7 (Verification) - UI Integration for complete Proxmark3 workflow
 * 
 * Provides user controls for:
 * - Transaction Type selection (MSD/VSDC/qVSDC/CDA)
 * - Terminal Decision selection (AAC/TC/ARQC)
 * - Transaction Amount input (default 100 cents = $1.00)
 * - CVM Summary display
 * - Terminal Parameters display (collapsible)
 */
@Composable
fun ProxmarkTransactionControls(viewModel: CardReadingViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Large)
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = CardReadingSpacing.Small),
            color = CardReadingColors.BorderDark
        )
        
        // Transaction Type Selection
        TransactionTypeSelector(viewModel)
        
        // Terminal Decision & Amount Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Medium)
        ) {
            TerminalDecisionSelector(viewModel, Modifier.weight(1f))
            TransactionAmountInput(viewModel, Modifier.weight(1f))
        }
        
        // CVM Summary Display (if available)
        if (viewModel.cvmSummary.isNotEmpty()) {
            CvmSummaryCard(viewModel)
        }
        
        // Terminal Parameters Display (Collapsible)
        TerminalParametersCard(viewModel)
        
        HorizontalDivider(
            modifier = Modifier.padding(vertical = CardReadingSpacing.Small),
            color = CardReadingColors.BorderDark
        )
    }
}

@Composable
private fun TransactionTypeSelector(viewModel: CardReadingViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small / 2)
    ) {
        Text(
            "TRANSACTION TYPE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CardReadingColors.TextSecondary
        )
        
        var transactionTypeExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { transactionTypeExpanded = true },
                modifier = Modifier.fillMaxWidth().height(CardReadingDimensions.ButtonHeightSmall),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardReadingColors.ButtonBackground,
                    contentColor = CardReadingColors.TextPrimary
                ),
                border = BorderStroke(1.dp, CardReadingColors.BorderDark),
                contentPadding = PaddingValues(horizontal = CardReadingSpacing.Medium, vertical = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${viewModel.selectedTransactionType.label} - ${viewModel.selectedTransactionType.description}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    Text(
                        String.format("0x%02X", viewModel.selectedTransactionType.ttqByte1.toInt() and 0xFF),
                        style = MaterialTheme.typography.labelSmall,
                        color = CardReadingColors.TextTertiary
                    )
                }
            }
            
            DropdownMenu(
                expanded = transactionTypeExpanded,
                onDismissRequest = { transactionTypeExpanded = false },
                modifier = Modifier.background(CardReadingColors.ButtonBackground)
            ) {
                TransactionType.values().forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    "${type.label} - ${type.description}",
                                    color = CardReadingColors.TextPrimary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "TTQ: 0x${String.format("%02X", type.ttqByte1.toInt() and 0xFF)}000000",
                                    color = CardReadingColors.TextTertiary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        onClick = {
                            viewModel.setTransactionType(type)
                            transactionTypeExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalDecisionSelector(viewModel: CardReadingViewModel, modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small / 2)
    ) {
        Text(
            "TERMINAL DECISION",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CardReadingColors.TextSecondary
        )
        
        var terminalDecisionExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { terminalDecisionExpanded = true },
                modifier = Modifier.fillMaxWidth().height(CardReadingDimensions.ButtonHeightSmall),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardReadingColors.ButtonBackground,
                    contentColor = CardReadingColors.TextPrimary
                ),
                border = BorderStroke(1.dp, CardReadingColors.BorderDark),
                contentPadding = PaddingValues(horizontal = CardReadingSpacing.Medium, vertical = 0.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        viewModel.selectedTerminalDecision.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        String.format("P1=0x%02X", viewModel.selectedTerminalDecision.p1Byte.toInt() and 0xFF),
                        style = MaterialTheme.typography.labelSmall,
                        color = CardReadingColors.TextTertiary
                    )
                }
            }
            
            DropdownMenu(
                expanded = terminalDecisionExpanded,
                onDismissRequest = { terminalDecisionExpanded = false },
                modifier = Modifier.background(CardReadingColors.ButtonBackground)
            ) {
                TerminalDecision.values().forEach { decision ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    decision.name,
                                    color = CardReadingColors.TextPrimary,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    decision.description,
                                    color = CardReadingColors.TextTertiary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        onClick = {
                            viewModel.setTerminalDecision(decision)
                            terminalDecisionExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionAmountInput(viewModel: CardReadingViewModel, modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small / 2)
    ) {
        Text(
            "AMOUNT (CENTS)",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CardReadingColors.TextSecondary
        )
        
        var amountText by remember { mutableStateOf(viewModel.transactionAmountCents.toString()) }
        
        OutlinedTextField(
            value = amountText,
            onValueChange = { newValue ->
                // Only allow digits
                val filtered = newValue.filter { it.isDigit() }
                if (filtered.length <= 10) { // Max 10 digits
                    amountText = filtered
                    filtered.toLongOrNull()?.let { amount ->
                        viewModel.updateTransactionAmount(amount)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(CardReadingDimensions.ButtonHeightSmall),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardReadingColors.ButtonBackground,
                unfocusedContainerColor = CardReadingColors.ButtonBackground,
                focusedTextColor = CardReadingColors.TextPrimary,
                unfocusedTextColor = CardReadingColors.TextPrimary,
                focusedBorderColor = CardReadingColors.BorderDark,
                unfocusedBorderColor = CardReadingColors.BorderDark
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            placeholder = {
                Text(
                    "100",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardReadingColors.TextTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            shape = RoundedCornerShape(CardReadingRadius.Small)
        )
        
        // Display formatted amount
        val formattedAmount = remember(viewModel.transactionAmountCents) {
            val dollars = viewModel.transactionAmountCents / 100.0
            NumberFormat.getCurrencyInstance(Locale.US).format(dollars)
        }
        Text(
            formattedAmount,
            style = MaterialTheme.typography.labelSmall,
            color = CardReadingColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CvmSummaryCard(viewModel: CardReadingViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CardReadingColors.Background
        ),
        shape = RoundedCornerShape(CardReadingRadius.Small),
        border = BorderStroke(1.dp, CardReadingColors.BorderDark)
    ) {
        Column(
            modifier = Modifier.padding(CardReadingSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small / 2)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "CVM",
                    tint = CardReadingColors.InfoBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "CARDHOLDER VERIFICATION (CVM)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = CardReadingColors.TextSecondary
                )
            }
            Text(
                viewModel.cvmSummary,
                style = MaterialTheme.typography.bodySmall,
                color = CardReadingColors.TextPrimary
            )
        }
    }
}

@Composable
private fun TerminalParametersCard(viewModel: CardReadingViewModel) {
    var terminalParamsExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CardReadingColors.Background
        ),
        shape = RoundedCornerShape(CardReadingRadius.Small),
        border = BorderStroke(1.dp, CardReadingColors.BorderDark)
    ) {
        Column(
            modifier = Modifier.padding(CardReadingSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Terminal Parameters",
                        tint = CardReadingColors.WarningOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "TERMINAL PARAMETERS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = CardReadingColors.TextSecondary
                    )
                }
                IconButton(
                    onClick = { terminalParamsExpanded = !terminalParamsExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (terminalParamsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (terminalParamsExpanded) "Collapse" else "Expand",
                        tint = CardReadingColors.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            if (terminalParamsExpanded) {
                HorizontalDivider(color = CardReadingColors.BorderDark)
                Column(
                    verticalArrangement = Arrangement.spacedBy(CardReadingSpacing.Small / 2)
                ) {
                    TerminalParamRow("Country Code", "0x0840 (USA)")
                    TerminalParamRow("Currency Code", "0x0840 (USD)")
                    TerminalParamRow("Terminal Type", "0x22")
                    TerminalParamRow("Capabilities", "0xE0E1C8")
                    TerminalParamRow("Additional", "0x2200000000")
                    TerminalParamRow("TTQ", String.format("0x%02X000000", viewModel.selectedTransactionType.ttqByte1.toInt() and 0xFF))
                }
            }
        }
    }
}

@Composable
private fun TerminalParamRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = CardReadingColors.TextTertiary
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CardReadingColors.TextPrimary
        )
    }
}
