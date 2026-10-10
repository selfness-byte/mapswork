package com.selfness.mapswork.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MapsworkTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF82D5E9), secondary = Color(0xFFA3D4B4),
        background = Color(0xFF101C22), surface = Color(0xFF14232B),
    ) else lightColorScheme(
        primary = Color(0xFF155E75), secondary = Color(0xFF366B4C),
        background = Color(0xFFF7F9FB), surface = Color.White,
        primaryContainer = Color(0xFFD8EDF4), onPrimaryContainer = Color(0xFF093A49),
    )
    MaterialTheme(colorScheme = colors, content = content)
}
