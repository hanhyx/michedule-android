package com.ljh.michedule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friend_shifts")
data class FriendShiftEntity(
    @PrimaryKey val date: String,
    val type: String,
    val friendName: String = "",
    val hasAlba: Boolean = false,
    val memo: String? = null,
    val mood: String? = null,
    val moodNote: String? = null,
    val todoCount: Int = 0,
    val extraShifts: String = "",
    val todoTexts: String = ""
) {
    fun getExtraShiftList(): List<String> =
        extraShifts.split(",").filter { it.isNotBlank() }

    fun getTodoList(): List<Pair<String, Boolean>> {
        if (todoTexts.isBlank()) return emptyList()
        return todoTexts.split("||").mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) parts[1] to (parts[0] == "1") else null
        }
    }
}
