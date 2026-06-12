package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE roomCode = :roomCode ORDER BY createdAt DESC LIMIT :limit")
    fun getMessages(roomCode: String, limit: Int = 200): Flow<List<ChatMessageEntity>>

    @Upsert
    suspend fun upsert(message: ChatMessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT MAX(createdAt) FROM chat_messages WHERE roomCode = :roomCode")
    suspend fun getLatestTimestamp(roomCode: String): Long?

    @Query("DELETE FROM chat_messages WHERE roomCode = :roomCode")
    suspend fun clearRoom(roomCode: String)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE roomCode = :roomCode AND senderCode != :myCode AND createdAt > :lastReadAt")
    fun getUnreadCount(roomCode: String, myCode: String, lastReadAt: Long): Flow<Int>
}
