package com.ljh.michedule.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val accent: Color,
    val accentDark: Color,
    val accentSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val bubbleMine: Color,
    val bubbleOther: Color,
    val isDark: Boolean
)

val DarkAppColors = AppColors(
    background = Color(0xFF0A0A12),
    surface = Color(0xFF16161E),
    card = Color(0xFF1E1E2A),
    border = Color(0xFF2A2A3A),
    accent = Color(0xFFA78BFA),
    accentDark = Color(0xFF6D28D9),
    accentSecondary = Color(0xFF818CF8),
    textPrimary = Color(0xFFE8E8ED),
    textSecondary = Color(0xFF9CA3AF),
    textMuted = Color(0xFF6B7280),
    bubbleMine = Color(0xFF6D28D9),
    bubbleOther = Color(0xFF1E1E2A),
    isDark = true
)

val LightAppColors = AppColors(
    background = Color(0xFFF5F7F5),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFEAF5EA),
    border = Color(0xFFCCE5CC),
    accent = Color(0xFF34D399),
    accentDark = Color(0xFF2AB886),
    accentSecondary = Color(0xFF6EE7B7),
    textPrimary = Color(0xFF1B1B1F),
    textSecondary = Color(0xFF49454F),
    textMuted = Color(0xFF79747E),
    bubbleMine = Color(0xFF2AB886),
    bubbleOther = Color(0xFFEAF5EA),
    isDark = false
)

val LocalAppColors = compositionLocalOf { DarkAppColors }

// Legacy aliases for backward compatibility
val DarkBg = DarkAppColors.background
val DarkSurface = DarkAppColors.surface
val DarkCard = DarkAppColors.card
val DarkBorder = DarkAppColors.border
val Purple80 = DarkAppColors.accent
val Purple60 = DarkAppColors.accentSecondary
val Purple40 = DarkAppColors.accentDark
val TextPrimary = DarkAppColors.textPrimary
val TextSecondary = DarkAppColors.textSecondary
val TextMuted = DarkAppColors.textMuted

val ShiftDay = Color(0xFFFBBF24)
val ShiftNight = Color(0xFF818CF8)
val ShiftNightEarly = Color(0xFFF472B6)
val ShiftOff = Color(0xFF34D399)
val ShiftAlba = Color(0xFFF97316)

val EventPersonal = Color(0xFF60A5FA)
val EventAppointment = Color(0xFFFBBF24)
val EventHospital = Color(0xFFF87171)
val EventOther = Color(0xFF9CA3AF)

val StatusOnline = Color(0xFF34D399)
val StatusOffline = Color(0xFFF87171)
