package com.ljh.michedule.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.db.ChatMessageDao
import com.ljh.michedule.data.db.ChatMessageEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val TAG = "ChatRepo"
private const val CHAT_TABLE = "chat_messages"

@Serializable
data class ChatMessageRow(
    val id: String? = null,
    val room_code: String,
    val sender_code: String,
    val message_type: String = "text",
    val content: String = "",
    val image_url: String? = null,
    val reactions: JsonObject = JsonObject(emptyMap()),
    val created_at: String? = null
)

class ChatRepository(
    private val dao: ChatMessageDao,
    private val prefsManager: PrefsManager,
    private val appContext: Context
) {
    private var client: SupabaseClient? = null
    private var chatChannel: RealtimeChannel? = null
    private var subscribeJob: Job? = null

    fun getMessages(roomCode: String): Flow<List<ChatMessageEntity>> =
        dao.getMessages(roomCode)

    fun getRoomCode(myCode: String, partnerCode: String): String {
        val sorted = listOf(myCode, partnerCode).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    suspend fun ensureClient(): SupabaseClient? {
        if (client != null) return client
        val url = prefsManager.supabaseUrl.first()
        val key = prefsManager.supabaseKey.first()
        if (url.isBlank() || key.isBlank()) return null
        client = createSupabaseClient(url, key) {
            install(Postgrest)
            install(Realtime)
        }
        return client
    }

    suspend fun sendMessage(roomCode: String, myCode: String, content: String, type: String = "text", imageUrl: String? = null) {
        val msgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val localEntity = ChatMessageEntity(
            id = msgId,
            roomCode = roomCode,
            senderCode = myCode,
            messageType = type,
            content = content,
            imageUrl = imageUrl,
            createdAt = now
        )
        dao.upsert(localEntity)

        try {
            val supabase = ensureClient() ?: return
            val row = ChatMessageRow(
                id = msgId,
                room_code = roomCode,
                sender_code = myCode,
                message_type = type,
                content = content,
                image_url = imageUrl
            )
            supabase.from(CHAT_TABLE).insert(row)
            Log.d(TAG, "Message sent: $msgId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message to Supabase", e)
        }
    }

    suspend fun sendImage(roomCode: String, myCode: String, imageUri: Uri) {
        try {
            val resized = resizeImage(imageUri) ?: return
            val fileName = "${roomCode}/${UUID.randomUUID()}.jpg"

            val url = prefsManager.supabaseUrl.first()
            val key = prefsManager.supabaseKey.first()
            val uploadUrl = "$url/storage/v1/object/chat-images/$fileName"

            val httpClient = HttpClient(OkHttp)
            val response = httpClient.request(uploadUrl) {
                method = HttpMethod.Post
                header("Authorization", "Bearer $key")
                header("apikey", key)
                contentType(ContentType.Image.JPEG)
                setBody(resized)
            }
            httpClient.close()

            if (response.status.isSuccess()) {
                val publicUrl = "$url/storage/v1/object/public/chat-images/$fileName"
                sendMessage(roomCode, myCode, "", "image", publicUrl)
                Log.d(TAG, "Image uploaded: $publicUrl")
            } else {
                Log.e(TAG, "Image upload failed: ${response.status}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send image", e)
        }
    }

    suspend fun addReaction(messageId: String, roomCode: String, emoji: String, userCode: String) {
        try {
            val supabase = ensureClient() ?: return

            val existing = supabase.from(CHAT_TABLE)
                .select { filter { eq("id", messageId) } }
                .decodeList<ChatMessageRow>()
                .firstOrNull() ?: return

            val currentReactions = existing.reactions.toMutableMap()
            val currentValue = currentReactions[emoji]
            if (currentValue is JsonPrimitive && currentValue.content == userCode) {
                currentReactions.remove(emoji)
            } else {
                currentReactions[emoji] = JsonPrimitive(userCode)
            }

            supabase.from(CHAT_TABLE).update({
                set("reactions", JsonObject(currentReactions))
            }) { filter { eq("id", messageId) } }

            val localMsg = dao.getMessages(roomCode).first().find { it.id == messageId }
            if (localMsg != null) {
                dao.upsert(localMsg.copy(reactions = JsonObject(currentReactions).toString()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add reaction", e)
        }
    }

    suspend fun syncHistory(roomCode: String) {
        try {
            val supabase = ensureClient() ?: return
            val rows = supabase.from(CHAT_TABLE)
                .select {
                    filter { eq("room_code", roomCode) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(200)
                }
                .decodeList<ChatMessageRow>()

            val entities = rows.mapNotNull { row ->
                val ts = parseTimestamp(row.created_at) ?: return@mapNotNull null
                ChatMessageEntity(
                    id = row.id ?: return@mapNotNull null,
                    roomCode = row.room_code,
                    senderCode = row.sender_code,
                    messageType = row.message_type,
                    content = row.content,
                    imageUrl = row.image_url,
                    reactions = row.reactions.toString(),
                    createdAt = ts
                )
            }
            if (entities.isNotEmpty()) {
                dao.upsertAll(entities)
            }
            Log.d(TAG, "Synced ${entities.size} messages for $roomCode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync history", e)
        }
    }

    fun subscribeToChat(roomCode: String, scope: CoroutineScope) {
        subscribeJob?.cancel()
        subscribeJob = scope.launch {
            try {
                val supabase = ensureClient() ?: return@launch
                chatChannel = supabase.realtime.channel("chat-$roomCode")

                chatChannel?.let { ch ->
                    val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = CHAT_TABLE
                    }
                    ch.subscribe()

                    changeFlow.collect { action ->
                        when (action) {
                            is PostgresAction.Insert,
                            is PostgresAction.Update -> syncHistory(roomCode)
                            else -> {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chat subscription failed", e)
            }
        }
    }

    fun unsubscribe() {
        subscribeJob?.cancel()
        subscribeJob = null
        chatChannel = null
    }

    suspend fun sendChatPush(partnerCode: String, senderName: String, content: String, messageType: String) {
        try {
            val isMutual = prefsManager.connectionMutual.first()
            if (!isMutual) return

            val supabase = ensureClient() ?: return
            val partnerRow = supabase.from("user_schedules")
                .select { filter { eq("user_code", partnerCode) } }
                .decodeList<JsonObject>()
                .firstOrNull()

            val partnerToken = (partnerRow?.get("fcm_token") as? JsonPrimitive)?.content
            if (partnerToken.isNullOrBlank()) return

            val url = prefsManager.supabaseUrl.first()
            val key = prefsManager.supabaseKey.first()
            val functionUrl = "$url/functions/v1/send-push"

            val body = if (messageType == "image") "$senderName: 사진" else "$senderName: $content"
            val payload = buildJsonObject {
                put("fcm_token", partnerToken)
                put("title", "💬 $senderName")
                put("body", if (messageType == "image") "사진을 보냈습니다" else content.take(100))
                put("data", buildJsonObject {
                    put("type", "chat")
                    put("sender_name", senderName)
                    put("content", content.take(100))
                })
            }

            val httpClient = HttpClient(OkHttp)
            httpClient.request(functionUrl) {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $key")
                setBody(payload.toString())
            }
            httpClient.close()
            Log.d(TAG, "Chat push sent to $partnerCode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send chat push", e)
        }
    }

    private fun resizeImage(uri: Uri): ByteArray? {
        return try {
            val input = appContext.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(input)
            input.close()

            val maxSize = 1024
            val ratio = minOf(maxSize.toFloat() / original.width, maxSize.toFloat() / original.height, 1f)
            val w = (original.width * ratio).toInt()
            val h = (original.height * ratio).toInt()
            val scaled = Bitmap.createScaledBitmap(original, w, h, true)

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            if (scaled !== original) scaled.recycle()
            original.recycle()
            baos.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Image resize failed", e)
            null
        }
    }

    private fun parseTimestamp(ts: String?): Long? {
        if (ts.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(ts).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(ts).toInstant().toEpochMilli()
            } catch (_: Exception) { null }
        }
    }
}
