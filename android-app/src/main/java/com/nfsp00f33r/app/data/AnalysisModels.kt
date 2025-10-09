package com.nfsp00f33r.app.data

data class AnalysisTool(
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isEnabled: Boolean = true
)

data class AnalysisResult(
    val tool: String,
    val result: String,
    val timestamp: Long,
    val status: String
)