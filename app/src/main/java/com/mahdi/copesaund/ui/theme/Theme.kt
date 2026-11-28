package com.mahdi.copesaund.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mahdi.copesaund.model.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8EDE5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF0E7490),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8E9F7),
    onSecondaryContainer = Color(0xFF001F28),
    tertiary = Color(0xFF6D4FB2),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7DEFF),
    onTertiaryContainer = Color(0xFF21005D),
    background = Color(0xFFF6FBFA),
    onBackground = Color(0xFF161D1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF54DBC8),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB8EDE5),
    secondary = Color(0xFF54CDE9),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004D62),
    onSecondaryContainer = Color(0xFFB8E9F7),
    tertiary = Color(0xFFCBBEFF),
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF4F3A8B),
    onTertiaryContainer = Color(0xFFE7DEFF),
    background = Color(0xFF0B1211),
    onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF101817),
    onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    outline = Color(0xFF89938F)
)

@Composable
fun AppTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkScheme else LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
