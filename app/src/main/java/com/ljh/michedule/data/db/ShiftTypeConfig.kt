package com.ljh.michedule.data.db

import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_type_configs")
data class ShiftTypeConfig(
    @PrimaryKey val id: String,
    val label: String,
    val shortLabel: String,
    val emoji: String,
    val colorHex: String,
    val bgColorHex: String,
    val defaultTimeRange: String,
    val sortOrder: Int,
    val inCycle: Boolean,
    val isBuiltIn: Boolean
) {
    val color: Color get() = parseColor(colorHex)
    val bgColor: Color get() = parseColor(bgColorHex)

    companion object {
        fun parseColor(hex: String): Color {
            val cleaned = hex.removePrefix("#")
            return try {
                Color(java.lang.Long.parseLong(cleaned, 16).let {
                    if (cleaned.length <= 6) it or 0xFF000000 else it
                })
            } catch (_: Exception) {
                Color.Gray
            }
        }

        val DEFAULTS = listOf(
            ShiftTypeConfig("day", "주간", "주", "☀️", "#FFFBBF24", "#33FBBF24", "06:00 - 16:30", 0, true, true),
            ShiftTypeConfig("night", "야간", "야", "🌙", "#FF818CF8", "#33818CF8", "18:00 - 06:00", 1, true, true),
            ShiftTypeConfig("off", "비번", "비", "😴", "#FF34D399", "#3334D399", "종일 휴무", 2, true, true),
            ShiftTypeConfig("nightEarly", "조기야간", "조", "🌇", "#FFF472B6", "#33F472B6", "16:30 - 04:00", 3, true, true),
            ShiftTypeConfig("alba", "알바", "알", "💼", "#FFF97316", "#33F97316", "시간 미정", 4, false, true)
        )
    }
}
