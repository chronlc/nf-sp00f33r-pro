package com.nfsp00f33r.app.theme.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun NfSp00fTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NfSp00fGreen,
            secondary = NfSp00fGreen,
            background = Color.Black,
            surface = DarkSurface,
            onSurface = NfSp00fGreen,
            onBackground = Color.White
        ),
        typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp
            ),
            titleMedium = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.15.sp
            ),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp
            ),
            labelSmall = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp
            )
        ),
        content = content
    )
}