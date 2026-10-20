package com.bookzor.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.bookzor.core.common.AppTheme

object BookzorTheme {

    fun getColorScheme(theme: AppTheme): ColorScheme {
        return when (theme) {
            AppTheme.OLED_BLACK -> darkColorScheme(
                primary = Color(0xFFFF5722),
                background = Color(0xFF000000),
                surface = Color(0xFF121212),
                surfaceVariant = Color(0xFF1E1E1E),
                onPrimary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )
            AppTheme.WARM_SEPIA -> darkColorScheme(
                primary = Color(0xFFFF9800),
                background = Color(0xFF1A1612),
                surface = Color(0xFF26201B),
                surfaceVariant = Color(0xFF332B25),
                onPrimary = Color.Black,
                onBackground = Color(0xFFF0E6D8),
                onSurface = Color(0xFFF0E6D8)
            )
            AppTheme.SLATE_NAVY -> darkColorScheme(
                primary = Color(0xFF38BDF8),
                background = Color(0xFF0B1120),
                surface = Color(0xFF1E293B),
                surfaceVariant = Color(0xFF334155),
                onPrimary = Color.Black,
                onBackground = Color(0xFFF1F5F9),
                onSurface = Color(0xFFF1F5F9)
            )
        }
    }
}
