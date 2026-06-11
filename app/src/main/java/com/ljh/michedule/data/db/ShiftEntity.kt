package com.ljh.michedule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val date: String,
    val type: String,
    val memo: String? = null,
    val hasAlba: Boolean = false,
    val extraShifts: String = ""
) {
    fun getExtraShiftList(): List<String> =
        extraShifts.split(",").filter { it.isNotBlank() }

    fun hasExtraShift(id: String): Boolean =
        getExtraShiftList().contains(id)

    fun withExtraShiftToggled(id: String): ShiftEntity {
        val current = getExtraShiftList().toMutableList()
        if (id in current) current.remove(id) else current.add(id)
        return copy(extraShifts = current.joinToString(","))
    }
}
