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

    fun getUnreadCount(roomCode: String, myCode: String, lastReadAt: Long): Flow<Int> =
        dao.getUnreadCount(roomCode, myCode, lastReadAt)

    fun getRoomCode(myCode: String, partnerCode: String): String {
        val sorted = listOf(myCode, partnerCode).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    suspend fun insertMessageFromPush(
        msgId: String,
        roomCode: String,
        senderCode: String,
        content: String,
        messageType: String = "text",
        createdAt: Long = System.currentTimeMillis()
    ) {
        val entity = ChatMessageEntity(
            id = msgId,
            roomCode = roomCode,
            senderCode = senderCode,
            messageType = messageType,
            content = content,
            createdAt = createdAt
        )
        dao.upsert(entity)
        Log.d(TAG, "Inserted push message locally: $msgId")
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

    data class SentMessage(val id: String, val createdAt: Long)

    suspend fun sendMessage(roomCode: String, myCode: String, content: String, type: String = "text", imageUrl: String? = null): SentMessage {
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
            val supabase = ensureClient() ?: return SentMessage(msgId, now)
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
        return SentMessage(msgId, now)
    }

    suspend fun sendImage(roomCode: String, myCode: String, imageUri: Uri): SentMessage? {
        try {
            val resized = resizeImage(imageUri) ?: return null
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
                val sent = sendMessage(roomCode, myCode, "", "image", publicUrl)
                Log.d(TAG, "Image uploaded: $publicUrl")
                return sent
            } else {
                Log.e(TAG, "Image upload failed: ${response.status}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send image", e)
        }
        return null
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

    private var pollingJob: Job? = null

    fun subscribeToChat(roomCode: String, scope: CoroutineScope) {
        subscribeJob?.cancel()
        pollingJob?.cancel()

        subscribeJob = scope.launch {
            var realtimeActive = false
            try {
                val supabase = ensureClient() ?: run {
                    Log.w(TAG, "Supabase client unavailable, falling back to polling")
                    startPolling(roomCode, scope)
                    return@launch
                }
                chatChannel = supabase.realtime.channel("chat-$roomCode")

                chatChannel?.let { ch ->
                    val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = CHAT_TABLE
                    }
                    ch.subscribe()
                    realtimeActive = true
                    Log.d(TAG, "Realtime subscribed for room: $roomCode")

                    changeFlow.collect { action ->
                        when (action) {
                            is PostgresAction.Insert,
                            is PostgresAction.Update -> {
                                Log.d(TAG, "Realtime event received, syncing...")
                                syncHistory(roomCode)
                            }
                            else -> {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chat subscription failed, falling back to polling", e)
                if (!realtimeActive) {
                    startPolling(roomCode, scope)
                }
            }
        }

        startPolling(roomCode, scope)
    }

    private fun startPolling(roomCode: String, scope: CoroutineScope) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(5000)
                try {
                    syncHistory(roomCode)
                } catch (e: Exception) {
                    Log.e(TAG, "Polling sync failed", e)
                }
            }
        }
    }

    fun unsubscribe() {
        subscribeJob?.cancel()
        subscribeJob = null
        pollingJob?.cancel()
        pollingJob = null
        chatChannel = null
    }

    suspend fun sendChatPush(partnerCode: String, senderName: String, content: String, messageType: String, msgId: String = "", createdAt: Long = 0L) {
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

            val myCode = prefsManager.ensureMyCode()
            val roomCode = getRoomCode(myCode, partnerCode)
            val payload = buildJsonObject {
                put("fcm_token", partnerToken)
                put("title", "💬 $senderName")
                put("body", if (messageType == "image") "사진을 보냈습니다" else content.take(100))
                put("data", buildJsonObject {
                    put("type", "chat")
                    put("sender_name", senderName)
                    put("sender_code", myCode)
                    put("room_code", roomCode)
                    put("content", content.take(100))
                    put("message_type", messageType)
                    if (msgId.isNotBlank()) put("msg_id", msgId)
                    if (createdAt > 0) put("created_at", createdAt.toString())
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
