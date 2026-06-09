package com.ljh.michedule.model

import androidx.compose.ui.graphics.Color

enum class EventCategory(
    val label: String,
    val emoji: String,
    val defaultColor: Color
) {
    PERSONAL("개인", "👤", Color(0xFF60A5FA)),
    APPOINTMENT("약속", "🤝", Color(0xFFFBBF24)),
    HOSPITAL("병원", "🏥", Color(0xFFF87171)),
    OTHER("기타", "📌", Color(0xFF9CA3AF));

    companion object {
        fun fromString(s: String?): EventCategory = when (s) {
            "personal" -> PERSONAL
            "appointment" -> APPOINTMENT
            "hospital" -> HOSPITAL
            "other" -> OTHER
            else -> OTHER
        }
    }
}
