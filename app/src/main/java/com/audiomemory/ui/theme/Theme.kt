package com.audiomemory.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

// Brand colors
val Emerald = Color(0xFF00FF88)
val Cyan = Color(0xFF00CCFF)
val Purple = Color(0xFFA855F7)
val Pink = Color(0xFFFF4488)
val Orange = Color(0xFFFF8844)

// Dark theme
val DarkBackground = Color(0xFF0A0A14)
val DarkSurface = Color(0xFF111125)
val DarkSurfaceVariant = Color(0xFF181838)
val DarkOnSurface = Color(0xFFE2E8F0)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = Emerald,
    secondary = Cyan,
    tertiary = Purple,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = Pink,
    onPrimary = Color.Black,
)

// Light theme (minimal — app is dark-first)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00B864),
    secondary = Color(0xFF0099CC),
    tertiary = Purple,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    error = Color(0xFFDC2626),
)

@Composable
fun AudioMemoryTheme(
    darkTheme: Boolean = true, // default to dark
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
