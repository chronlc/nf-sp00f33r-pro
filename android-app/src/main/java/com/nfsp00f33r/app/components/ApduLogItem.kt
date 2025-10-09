package com.nfsp00f33r.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfsp00f33r.app.data.ApduLogEntry
import com.nfsp00f33r.app.cardreading.EmvTagDictionary

@Composable
fun ApduLogItem(
    logEntry: ApduLogEntry,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Command/Response indicator and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (logEntry.command.isNotEmpty()) Color(0xFF0066CC) else Color(0xFF4CAF50),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (logEntry.command.isNotEmpty()) "CMD" else "RSP",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                Text(
                    text = "${logEntry.executionTimeMs}ms",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            // APDU data
            if (logEntry.command.isNotEmpty()) {
                Text(
                    text = logEntry.command,
                    color = Color(0xFF00AAFF),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
            
            if (logEntry.response.isNotEmpty()) {
                Text(
                    text = logEntry.response,
                    color = Color(0xFF00FF88),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
            
            // Enhanced status with EMV analysis
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SW: ${logEntry.statusWord}",
                        color = if (logEntry.statusWord == "9000") Color(0xFF00FF88) else Color(0xFFFF4444),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Show EMV tag analysis for critical responses
                    if (logEntry.response.length > 8) {
                        val tagAnalysis = EmvTagDictionary.enhanceApduDescription(
                            logEntry.command, logEntry.response, ""
                        )
                        if (tagAnalysis.contains("Tags:")) {
                            Text(
                                text = tagAnalysis.substringAfter("Tags: ").take(30) + "...",
                                color = Color(0xFF4CAF50),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                
                Text(
                    text = "⚡ ${logEntry.executionTimeMs}ms",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}