package com.school.servicemonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeColor(val key: String, val displayName: String, val primaryLight: Color, val primaryDark: Color) {
    VIOLET("VIOLET", "Violet & Black", Color(0xFF7B1FA2), Color(0xFFCE93D8)),
    EMERALD("EMERALD", "Emerald Green", Color(0xFF1B5E20), Color(0xFF81C784)),
    OCEAN("OCEAN", "Ocean Blue", Color(0xFF0D47A1), Color(0xFF64B5F6)),
    CRIMSON("CRIMSON", "Sunset Crimson", Color(0xFFB71C1C), Color(0xFFEF5350)),
    GOLD("GOLD", "Amber Gold", Color(0xFFF57F17), Color(0xFFFFB300));

    companion object {
        fun fromKey(key: String?): AppThemeColor {
            return values().find { it.key == key } ?: VIOLET
        }
    }
}

private fun getLightColorScheme(themeColor: AppThemeColor) = lightColorScheme(
    primary = themeColor.primaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E5F5),
    onPrimaryContainer = Color.Black,
    secondary = themeColor.primaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7F6),
    onSecondaryContainer = Color.Black,
    tertiary = themeColor.primaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCE4EC),
    onTertiaryContainer = Color.Black,
    background = Color(0xFFFAFAFC),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEFEBE9),
    onSurfaceVariant = Color(0xFF222222),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color.Black,
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFFBDBDBD)
)

private fun getDarkColorScheme(themeColor: AppThemeColor) = darkColorScheme(
    primary = themeColor.primaryDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF381460),
    onPrimaryContainer = Color.White,
    secondary = themeColor.primaryDark,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2E0854),
    onSecondaryContainer = Color.White,
    tertiary = themeColor.primaryDark,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF4A148C),
    onTertiaryContainer = Color.White,
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF0F0E14),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1B1922),
    onSurfaceVariant = Color(0xFFDDDDDD),
    error = Color(0xFFEF5350),
    onError = Color.Black,
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = Color.White,
    outline = Color(0xFF8C8894),
    outlineVariant = Color(0xFF48454E)
)

@Composable
fun ServiceMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: AppThemeColor = AppThemeColor.VIOLET,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        getDarkColorScheme(themeColor)
    } else {
        getLightColorScheme(themeColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
