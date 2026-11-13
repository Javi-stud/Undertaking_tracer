package com.school.servicemonitor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AppThemeColor(val key: String, val displayName: String, val accent: Color) {
    VIOLET("VIOLET", "Violet & Black", Color(0xFF8B5CF6)),
    EMERALD("EMERALD", "Emerald Green", Color(0xFF34D399)),
    OCEAN("OCEAN", "Ocean Blue", Color(0xFF3B82F6)),
    CRIMSON("CRIMSON", "Sunset Crimson", Color(0xFFF43F5E)),
    GOLD("GOLD", "Amber Gold", Color(0xFFF59E0B));

    // Kept so older code that reads primaryDark still compiles.
    val primaryDark: Color get() = accent

    companion object {
        fun fromKey(key: String?): AppThemeColor = values().find { it.key == key } ?: VIOLET
    }
}

object AppColors {
    val Ink = Color(0xFF05040A)
    val Card = Color(0xFF15112B)
    val CardHigh = Color(0xFF1F1A3D)
    val Text = Color(0xFFF3EEFF)
    val TextMuted = Color(0xFFA79EC0)
    val Border = Color(0x26FFFFFF)
}

private fun scheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF0B0716),
    primaryContainer = accent.copy(alpha = 0.22f).compositeOver(AppColors.Card),
    onPrimaryContainer = AppColors.Text,
    secondary = accent.copy(alpha = 0.8f).compositeOver(Color.White),
    onSecondary = Color(0xFF0B0716),
    secondaryContainer = AppColors.CardHigh,
    onSecondaryContainer = AppColors.Text,
    tertiary = accent,

    background = Color.Transparent,      // the glow is drawn behind everything
    onBackground = AppColors.Text,
    surface = Color.Transparent,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.Card,
    onSurfaceVariant = AppColors.TextMuted,
    surfaceContainerLowest = AppColors.Ink,
    surfaceContainerLow = AppColors.Card,
    surfaceContainer = AppColors.Card,
    surfaceContainerHigh = AppColors.CardHigh,
    surfaceContainerHighest = AppColors.Card,

    error = Color(0xFFFF6B8A),
    onError = Color(0xFF3A0A18),
    errorContainer = Color(0xFF4A1530),
    onErrorContainer = Color(0xFFFFD9E2),
    outline = Color(0xFF5A4F7A),
    outlineVariant = Color(0xFF2E2745)
)

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val base = Typography()
private val AppTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
fun ServiceMonitorTheme(
    themeColor: AppThemeColor = AppThemeColor.VIOLET,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = scheme(themeColor.accent),
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}