package com.tkolymp.tkolympapp.util

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun parseEventColor(colorRgb: String?, type: String?): Color {
    if (colorRgb == "lesson" || type?.equals("lesson", ignoreCase = true) == true) {
        return if (isSystemInDarkTheme()) Color(0xFF303030) else Color(0xFFF5F5F5)
    }

    if (colorRgb.isNullOrBlank()) {
        return Color(0xFFADD8E6)
    }

    return try {
        val hex = if (colorRgb.startsWith("#")) colorRgb.substring(1) else colorRgb
        val rgb = hex.toLong(16)
        Color(
            red = ((rgb shr 16) and 0xFF).toInt(),
            green = ((rgb shr 8) and 0xFF).toInt(),
            blue = (rgb and 0xFF).toInt()
        )
    } catch (e: Exception) {
        Color(0xFFADD8E6)
    }
}
