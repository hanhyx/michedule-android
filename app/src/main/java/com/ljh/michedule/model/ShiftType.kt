package com.ljh.michedule.model

import androidx.compose.ui.graphics.Color

enum class ShiftType(
    val label: String,
    val shortLabel: String,
    val emoji: String,
    val timeRange: String,
    val color: Color,
    val bgColor: Color
) {
    DAY(
        label = "주간",
        shortLabel = "주",
        emoji = "☀️",
        timeRange = "08:30 - 16:30",
        color = Color(0xFFFBBF24),
        bgColor = Color(0x33FBBF24)
    ),
    NIGHT(
        label = "야간",
        shortLabel = "야",
        emoji = "🌙",
        timeRange = "18:00 - 06:00",
        color = Color(0xFF818CF8),
        bgColor = Color(0x33818CF8)
    ),
    NIGHT_EARLY(
        label = "조기야간",
        shortLabel = "조",
        emoji = "🌇",
        timeRange = "16:30 - 04:00",
        color = Color(0xFFF472B6),
        bgColor = Color(0x33F472B6)
    ),
    OFF(
        label = "비번",
        shortLabel = "비",
        emoji = "😴",
        timeRange = "종일 휴무",
        color = Color(0xFF34D399),
        bgColor = Color(0x3334D399)
    ),
    ALBA(
        label = "알바",
        shortLabel = "알",
        emoji = "💼",
        timeRange = "시간 미정",
        color = Color(0xFFF59E0B),
        bgColor = Color(0x33F59E0B)
    );

    companion object {
        fun fromString(s: String?): ShiftType? = when (s) {
            "day" -> DAY
            "night" -> NIGHT
            "nightEarly" -> NIGHT_EARLY
            "off" -> OFF
            "alba" -> ALBA
            else -> null
        }

        fun toDbString(type: ShiftType): String = when (type) {
            DAY -> "day"
            NIGHT -> "night"
            NIGHT_EARLY -> "nightEarly"
            OFF -> "off"
            ALBA -> "alba"
        }
    }
}
