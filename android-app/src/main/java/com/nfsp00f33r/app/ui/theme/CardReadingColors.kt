package com.nfsp00f33r.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Color palette for Card Reading Screen
 * Centralized color definitions for consistent theming
 */
object CardReadingColors {
    // Background Colors
    val Background = Color(0xFF0A0A0A)
    val CardBackground = Color(0xFF0F1419)
    val TerminalBackground = Color(0xFF000000)
    val ButtonBackground = Color(0xFF1A1F2E)
    
    // Primary Colors
    val SuccessGreen = Color(0xFF00FF41)
    val BrightGreen = Color(0xFF4CAF50)
    val ErrorRed = Color(0xFFFF1744)
    val BrightRed = Color(0xFFFF6B6B)
    val DangerRed = Color(0xFFF44336)
    
    // Info Colors
    val InfoBlue = Color(0xFF2196F3)
    val LightBlue = Color(0xFF4FC3F7)
    val WarningOrange = Color(0xFFFFB74D)
    val Orange = Color(0xFFFF9800)
    val Purple = Color(0xFFE1BEE7)
    
    // Text Colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF888888)
    val TextTertiary = Color(0xFF666666)
    
    // Border & Outline Colors
    val BorderDark = Color(0xFF333333)
    
    // State-specific Colors
    val SafeBackground = Color(0xFF1B4332)
    val VulnerableBackground = Color(0xFF4A1A1A)
    
    // Accent Colors
    val Cyan = Color(0xFF00FFFF)
}

/**
 * Standardized spacing system for consistent layouts
 * Uses 4dp base unit with multiples: 4, 8, 12, 16, 24, 32
 */
object CardReadingSpacing {
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val ExtraLarge = 24.dp
    val Huge = 32.dp
}

/**
 * Standardized corner radius values
 */
object CardReadingRadius {
    val Small = 4.dp
    val Medium = 6.dp
    val Large = 8.dp
    val ExtraLarge = 16.dp
}

/**
 * Component-specific dimensions
 */
object CardReadingDimensions {
    val ButtonHeightSmall = 36.dp
    val ButtonHeightMedium = 40.dp
    val TerminalHeight = 300.dp
    val EmvDataMaxHeight = 300.dp
    val CardWidth = 300.dp
    val CardHeight = 180.dp
}
