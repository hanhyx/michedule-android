package com.ljh.michedule.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.db.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.UUID

enum class UploadState { IDLE, UPLOADING, SUCCESS, ERROR }

class TimelineRepository(
    private val dao: TimelineDao,
    private val prefsManager: PrefsManager,
    private val appContext: Context
) {
    companion object {
        private const val TAG = "TimelineRepo"
        private const val TABLE_TIMELINES = "timelines"
        private const val TABLE_PLACES = "timeline_places"
        private const val TABLE_PHOTOS = "timeline_photos"
        private const val TABLE_STICKERS = "timeline_stickers"
        private const val STORAGE_BUCKET = "timeline-photos"
    }

    private var client: SupabaseClient? = null

    private val _uploadState = MutableStateFlow(UploadState.IDLE)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError.asStateFlow()

    fun clearUploadError() { _uploadError.value = null; _uploadState.value = UploadState.IDLE }

    private suspend fun ensureClient(): SupabaseClient? {
        if (client != null) return client
        val url = prefsManager.supabaseUrl.first()
        val key = prefsManager.supabaseKey.first()
        if (url.isBlank() || key.isBlank()) return null
        client = createSupabaseClient(url, key) {
            install(Postgrest)
        }
        return client
    }

    fun getTimelines(roomCode: String): Flow<List<TimelineEntity>> = dao.getTimelines(roomCode)
    fun getPlaces(timelineId: String): Flow<List<TimelinePlaceEntity>> = dao.getPlaces(timelineId)
    fun getPhotos(timelineId: String): Flow<List<TimelinePhotoEntity>> = dao.getPhotos(timelineId)
    fun getPhotosForPlace(placeId: String): Flow<List<TimelinePhotoEntity>> = dao.getPhotosForPlace(placeId)
    fun getStickers(timelineId: String): Flow<List<TimelineStickerEntity>> = dao.getStickers(timelineId)

    suspend fun createTimeline(roomCode: String, date: String, title: String): TimelineEntity {
        val myCode = prefsManager.myCode.first()
        val timeline = TimelineEntity(
            id = UUID.randomUUID().toString(),
            roomCode = roomCode,
            date = date,
            title = title,
            createdBy = myCode,
            createdAt = System.currentTimeMillis()
        )
        dao.insertTimeline(timeline)
        syncTimelineToRemote(timeline)
        return timeline
    }

    suspend fun addPlace(timelineId: String, placeName: String, time: String, memo: String, sortOrder: Int): TimelinePlaceEntity {
        val place = TimelinePlaceEntity(
            id = UUID.randomUUID().toString(),
            timelineId = timelineId,
            placeName = placeName,
            time = time,
            memo = memo,
            sortOrder = sortOrder
        )
        dao.insertPlace(place)
        syncPlaceToRemote(place)
        return place
    }

    suspend fun addPhoto(placeId: String, timelineId: String, imageUri: Uri, sortOrder: Int): TimelinePhotoEntity? {
        _uploadState.value = UploadState.UPLOADING
        _uploadError.value = null
        return try {
            val imageUrl = uploadImage(imageUri)
            if (imageUrl == null) {
                _uploadState.value = UploadState.ERROR
                if (_uploadError.value == null) _uploadError.value = "이미지 업로드 실패"
                return null
            }
            val photo = TimelinePhotoEntity(
                id = UUID.randomUUID().toString(),
                placeId = placeId,
                timelineId = timelineId,
                imageUrl = imageUrl,
                sortOrder = sortOrder
            )
            dao.insertPhoto(photo)
            syncPhotoToRemote(photo)
            _uploadState.value = UploadState.SUCCESS
            photo
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add photo", e)
            _uploadState.value = UploadState.ERROR
            _uploadError.value = "사진 추가 실패: ${e.message}"
            null
        }
    }

    suspend fun addSticker(
        timelineId: String,
        placeId: String? = null,
        photoId: String? = null,
        stickerType: String,
        stickerValue: String,
        posX: Float, posY: Float,
        scale: Float = 1f, rotation: Float = 0f
    ): TimelineStickerEntity {
        val myCode = prefsManager.myCode.first()
        val sticker = TimelineStickerEntity(
            id = UUID.randomUUID().toString(),
            timelineId = timelineId,
            placeId = placeId,
            photoId = photoId,
            stickerType = stickerType,
            stickerValue = stickerValue,
            posX = posX, posY = posY,
            scale = scale, rotation = rotation,
            placedBy = myCode
        )
        dao.insertSticker(sticker)
        syncStickerToRemote(sticker)
        return sticker
    }

    suspend fun moveSticker(id: String, x: Float, y: Float) {
        dao.updateStickerPosition(id, x, y)
        try {
            ensureClient()?.from(TABLE_STICKERS)?.update({
                set("pos_x", x)
                set("pos_y", y)
            }) { filter { eq("id", id) } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync sticker move", e)
        }
    }

    suspend fun removeSticker(id: String) {
        dao.deleteSticker(id)
        try {
            ensureClient()?.from(TABLE_STICKERS)?.delete { filter { eq("id", id) } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync sticker delete", e)
        }
    }

    suspend fun deleteTimeline(timeline: TimelineEntity) {
        dao.deleteTimeline(timeline)
        try {
            val supabase = ensureClient() ?: return
            supabase.from(TABLE_STICKERS).delete { filter { eq("timeline_id", timeline.id) } }
            supabase.from(TABLE_PHOTOS).delete { filter { eq("timeline_id", timeline.id) } }
            supabase.from(TABLE_PLACES).delete { filter { eq("timeline_id", timeline.id) } }
            supabase.from(TABLE_TIMELINES).delete { filter { eq("id", timeline.id) } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete timeline remotely", e)
        }
    }

    suspend fun deletePlace(place: TimelinePlaceEntity) {
        dao.deletePlace(place)
        try {
            val supabase = ensureClient() ?: return
            supabase.from(TABLE_PHOTOS).delete { filter { eq("place_id", place.id) } }
            supabase.from(TABLE_PLACES).delete { filter { eq("id", place.id) } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete place remotely", e)
        }
    }

    private suspend fun uploadImage(uri: Uri): String? {
        return try {
            Log.d(TAG, "Starting image upload for uri: $uri")
            val bytes = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.readBytes()
            }
            if (bytes == null) {
                Log.e(TAG, "Failed to read image bytes from uri: $uri")
                _uploadError.value = "이미지를 읽을 수 없습니다"
                return null
            }
            Log.d(TAG, "Image bytes read: ${bytes.size} bytes")

            val url = prefsManager.supabaseUrl.first()
            val key = prefsManager.supabaseKey.first()
            if (url.isBlank() || key.isBlank()) {
                Log.e(TAG, "Supabase URL or key is blank")
                _uploadError.value = "Supabase 설정이 없습니다"
                return null
            }

            val fileName = "timeline_${UUID.randomUUID()}.jpg"
            val uploadUrl = "$url/storage/v1/object/$STORAGE_BUCKET/$fileName"
            Log.d(TAG, "Uploading to: $uploadUrl")

            val httpClient = HttpClient(OkHttp)
            val response = httpClient.request(uploadUrl) {
                method = HttpMethod.Post
                header("Authorization", "Bearer $key")
                header("apikey", key)
                contentType(ContentType.Image.JPEG)
                setBody(bytes)
            }

            val statusCode = response.status.value
            if (response.status.isSuccess()) {
                httpClient.close()
                val publicUrl = "$url/storage/v1/object/public/$STORAGE_BUCKET/$fileName"
                Log.d(TAG, "Upload success: $publicUrl")
                publicUrl
            } else {
                val body = try { response.bodyAsText() } catch (_: Exception) { "(no body)" }
                httpClient.close()
                Log.e(TAG, "Image upload failed: HTTP $statusCode - $body")
                _uploadError.value = "업로드 실패 (HTTP $statusCode)"
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload image", e)
            _uploadError.value = "업로드 에러: ${e.message?.take(50)}"
            null
        }
    }

    suspend fun syncFromRemote(roomCode: String) {
        try {
            val supabase = ensureClient() ?: return

            val timelines = supabase.from(TABLE_TIMELINES)
                .select { filter { eq("room_code", roomCode) } }
                .decodeList<TimelineRow>()
            dao.insertAllTimelines(timelines.map { it.toEntity() })

            val timelineIds = timelines.map { it.id }
            if (timelineIds.isEmpty()) return

            timelineIds.forEach { tlId ->
                val places = supabase.from(TABLE_PLACES)
                    .select { filter { eq("timeline_id", tlId) } }
                    .decodeList<PlaceRow>()
                dao.insertAllPlaces(places.map { it.toEntity() })

                val photos = supabase.from(TABLE_PHOTOS)
                    .select { filter { eq("timeline_id", tlId) } }
                    .decodeList<PhotoRow>()
                dao.insertAllPhotos(photos.map { it.toEntity() })

                val stickers = supabase.from(TABLE_STICKERS)
                    .select { filter { eq("timeline_id", tlId) } }
                    .decodeList<StickerRow>()
                dao.insertAllStickers(stickers.map { it.toEntity() })
            }

            Log.d(TAG, "Synced ${timelines.size} timelines from remote")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync from remote", e)
        }
    }

    private suspend fun syncTimelineToRemote(t: TimelineEntity) {
        try {
            ensureClient()?.from(TABLE_TIMELINES)?.upsert(TimelineRow.from(t))
        } catch (e: Exception) { Log.e(TAG, "sync timeline failed", e) }
    }
    private suspend fun syncPlaceToRemote(p: TimelinePlaceEntity) {
        try {
            ensureClient()?.from(TABLE_PLACES)?.upsert(PlaceRow.from(p))
        } catch (e: Exception) { Log.e(TAG, "sync place failed", e) }
    }
    private suspend fun syncPhotoToRemote(p: TimelinePhotoEntity) {
        try {
            ensureClient()?.from(TABLE_PHOTOS)?.upsert(PhotoRow.from(p))
        } catch (e: Exception) { Log.e(TAG, "sync photo failed", e) }
    }
    private suspend fun syncStickerToRemote(s: TimelineStickerEntity) {
        try {
            ensureClient()?.from(TABLE_STICKERS)?.upsert(StickerRow.from(s))
        } catch (e: Exception) { Log.e(TAG, "sync sticker failed", e) }
    }
}

@Serializable
data class TimelineRow(
    val id: String,
    val room_code: String,
    val date: String,
    val title: String = "",
    val created_by: String,
    val created_at: Long
) {
    fun toEntity() = TimelineEntity(id, room_code, date, title, created_by, created_at)
    companion object {
        fun from(e: TimelineEntity) = TimelineRow(e.id, e.roomCode, e.date, e.title, e.createdBy, e.createdAt)
    }
}

@Serializable
data class PlaceRow(
    val id: String,
    val timeline_id: String,
    val place_name: String,
    val time: String = "",
    val memo: String = "",
    val sort_order: Int = 0
) {
    fun toEntity() = TimelinePlaceEntity(id, timeline_id, place_name, time, memo, sort_order)
    companion object {
        fun from(e: TimelinePlaceEntity) = PlaceRow(e.id, e.timelineId, e.placeName, e.time, e.memo, e.sortOrder)
    }
}

@Serializable
data class PhotoRow(
    val id: String,
    val place_id: String,
    val timeline_id: String,
    val image_url: String,
    val sort_order: Int = 0
) {
    fun toEntity() = TimelinePhotoEntity(id, place_id, timeline_id, image_url, sort_order)
    companion object {
        fun from(e: TimelinePhotoEntity) = PhotoRow(e.id, e.placeId, e.timelineId, e.imageUrl, e.sortOrder)
    }
}

@Serializable
data class StickerRow(
    val id: String,
    val timeline_id: String,
    val place_id: String? = null,
    val photo_id: String? = null,
    val sticker_type: String = "emoji",
    val sticker_value: String,
    val pos_x: Float,
    val pos_y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val placed_by: String
) {
    fun toEntity() = TimelineStickerEntity(id, timeline_id, place_id, photo_id, sticker_type, sticker_value, pos_x, pos_y, scale, rotation, placed_by)
    companion object {
        fun from(e: TimelineStickerEntity) = StickerRow(e.id, e.timelineId, e.placeId, e.photoId, e.stickerType, e.stickerValue, e.posX, e.posY, e.scale, e.rotation, e.placedBy)
    }
}
