package com.ljh.michedule.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = DarkAppColors.accent,
    secondary = DarkAppColors.accentSecondary,
    tertiary = DarkAppColors.accentDark,
    background = DarkAppColors.background,
    surface = DarkAppColors.surface,
    surfaceVariant = DarkAppColors.card,
    onPrimary = DarkAppColors.background,
    onSecondary = DarkAppColors.background,
    onBackground = DarkAppColors.textPrimary,
    onSurface = DarkAppColors.textPrimary,
    onSurfaceVariant = DarkAppColors.textSecondary,
    outline = DarkAppColors.border,
    outlineVariant = DarkAppColors.border
)

private val LightColorScheme = lightColorScheme(
    primary = LightAppColors.accent,
    secondary = LightAppColors.accentSecondary,
    tertiary = LightAppColors.accentDark,
    background = LightAppColors.background,
    surface = LightAppColors.surface,
    surfaceVariant = LightAppColors.card,
    onPrimary = LightAppColors.background,
    onSecondary = LightAppColors.background,
    onBackground = LightAppColors.textPrimary,
    onSurface = LightAppColors.textPrimary,
    onSurfaceVariant = LightAppColors.textSecondary,
    outline = LightAppColors.border,
    outlineVariant = LightAppColors.border
)

@Composable
fun MicheduleTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
