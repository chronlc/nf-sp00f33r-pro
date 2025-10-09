package com.nfsp00f33r.app.screens.analysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfsp00f33r.app.R
import com.nfsp00f33r.app.data.AnalysisTool
import com.nfsp00f33r.app.data.AnalysisResult

@Composable
fun AnalysisScreen() {
    var selectedTool by remember { mutableStateOf<AnalysisTool?>(null) }
    var showFuzzer by remember { mutableStateOf(false) }
    
    if (showFuzzer) {
        TerminalFuzzerScreen()
        return
    }
    
    val analysisTools = listOf(
        AnalysisTool("Terminal Fuzzer", "EMV protocol fuzzing and security testing", Icons.Default.BugReport, true),
        AnalysisTool("TLV Parser", "Parse BER-TLV encoded data", Icons.Default.DataObject, true),
        AnalysisTool("TTQ Analyzer", "Analyze Terminal Transaction Qualifiers", Icons.Default.Settings, true),
        AnalysisTool("PDOL Builder", "Build Processing Data Object List", Icons.Default.Build, true),
        AnalysisTool("EMV Tag Dictionary", "Lookup EMV tag definitions", Icons.Default.Book, true),
        AnalysisTool("ROCA Vulnerability", "Analyze RSA certificates for ROCA vulnerability", Icons.Default.Security, true),
        AnalysisTool("Cryptogram Validator", "Validate transaction cryptograms", Icons.Default.Security, true),
        AnalysisTool("CVR Analysis", "Card Verification Results analysis", Icons.Default.VerifiedUser, true),
        AnalysisTool("AIP Decoder", "Application Interchange Profile decoder", Icons.Default.Translate, true)
    )
    
    val sampleResults = listOf(
        AnalysisResult("ROCA Vulnerability", "⚠️ ROCA vulnerability detected in 2 certificates", System.currentTimeMillis() - 30000, "WARNING"),
        AnalysisResult("Cryptogram Validator", "✅ Valid ARQC cryptogram verified", System.currentTimeMillis() - 15000, "SUCCESS"),
        AnalysisResult("TLV Parser", "Successfully parsed 15 EMV tags", System.currentTimeMillis(), "SUCCESS"),
        AnalysisResult("TTQ Analyzer", "Contactless transaction supported", System.currentTimeMillis() - 60000, "SUCCESS"),
        AnalysisResult("PDOL Builder", "Built PDOL with 8 data elements", System.currentTimeMillis() - 120000, "SUCCESS")
    )
    
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "EMV Analysis Tools",
                color = Color(0xFF4CAF50),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Analysis Tools Grid
            AnalysisToolsSection(
                tools = analysisTools,
                onToolSelected = { tool ->
                    if (tool.name == "Terminal Fuzzer") {
                        showFuzzer = true
                    } else {
                        selectedTool = tool
                    }
                }
            )
            
            // Recent Results
            if (sampleResults.isNotEmpty()) {
                RecentResultsSection(sampleResults)
            }
            
            // EMV Tag Reference
            EmvTagReferenceSection()
        }
    }
}

@Composable
private fun AnalysisToolsSection(
    tools: List<AnalysisTool>,
    onToolSelected: (AnalysisTool) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Analysis Tools",
                color = Color(0xFF4CAF50),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            tools.forEach { tool ->
                AnalysisToolItem(
                    tool = tool,
                    onClick = { onToolSelected(tool) }
                )
            }
        }
    }
}

@Composable
private fun AnalysisToolItem(
    tool: AnalysisTool,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tool.isEnabled) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (tool.isEnabled) Color(0xFF2A2A2A) else Color(0xFF1A1A1A)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (tool.isEnabled) 4.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    tool.icon,
                    contentDescription = tool.name,
                    tint = if (tool.isEnabled) Color(0xFF4CAF50) else Color(0xFF666666),
                    modifier = Modifier.size(24.dp)
                )
                
                Column {
                    Text(
                        tool.name,
                        color = if (tool.isEnabled) Color.White else Color(0xFF666666),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        tool.description,
                        color = if (tool.isEnabled) Color(0xFF888888) else Color(0xFF555555),
                        fontSize = 12.sp
                    )
                }
            }
            
            if (!tool.isEnabled) {
                Text(
                    "DISABLED",
                    color = Color(0xFF666666),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RecentResultsSection(results: List<AnalysisResult>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Recent Results",
                color = Color(0xFF4CAF50),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            results.forEach { result ->
                ResultItem(result)
            }
        }
    }
}

@Composable
private fun ResultItem(result: AnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.tool,
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    result.result,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    result.status,
                    color = when (result.status) {
                        "SUCCESS" -> Color(0xFF4CAF50)
                        "ERROR" -> Color(0xFFFF4444)
                        else -> Color(0xFFFFAA00)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    java.text.SimpleDateFormat("HH:mm").format(java.util.Date(result.timestamp)),
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun EmvTagReferenceSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "EMV Tag Reference",
                color = Color(0xFF4CAF50),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            val commonTags = listOf(
                "5A" to "Application Primary Account Number (PAN)",
                "57" to "Track 2 Equivalent Data",
                "5F20" to "Cardholder Name",
                "5F24" to "Application Expiration Date",
                "82" to "Application Interchange Profile",
                "84" to "Dedicated File (DF) Name",
                "94" to "Application File Locator (AFL)",
                "9F02" to "Amount, Authorised (Numeric)",
                "9F10" to "Issuer Application Data",
                "9F26" to "Application Cryptogram",
                "9F27" to "Cryptogram Information Data",
                "9F33" to "Terminal Capabilities",
                "9F34" to "Cardholder Verification Method (CVM) Results",
                "9F36" to "Application Transaction Counter (ATC)",
                "9F37" to "Unpredictable Number"
            )
            
            LazyColumn(
                modifier = Modifier.height(200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(commonTags) { (tag, description) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            tag,
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            description,
                            color = Color(0xFF888888),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}