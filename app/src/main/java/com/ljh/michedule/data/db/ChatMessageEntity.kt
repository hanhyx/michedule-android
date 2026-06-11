package com.ljh.michedule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val roomCode: String,
    val senderCode: String,
    val messageType: String = "text",
    val content: String = "",
    val imageUrl: String? = null,
    val reactions: String = "{}",
    val createdAt: Long
)
