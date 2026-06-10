package com.ljh.michedule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friend_shifts")
data class FriendShiftEntity(
    @PrimaryKey val date: String,
    val type: String,
    val friendName: String = "",
    val hasAlba: Boolean = false,
    val memo: String? = null
)
