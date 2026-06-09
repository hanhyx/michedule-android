package com.ljh.michedule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val title: String,
    val startTime: String? = null,
    val endTime: String? = null,
    val color: Int = 0xFF60A5FA.toInt(),
    val category: String = "personal"
)
