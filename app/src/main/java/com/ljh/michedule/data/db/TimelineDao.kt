package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timelines WHERE roomCode = :roomCode ORDER BY date DESC")
    fun getTimelines(roomCode: String): Flow<List<TimelineEntity>>

    @Query("SELECT * FROM timelines WHERE id = :id")
    suspend fun getById(id: String): TimelineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeline(timeline: TimelineEntity)

    @Delete
    suspend fun deleteTimeline(timeline: TimelineEntity)

    @Query("SELECT * FROM timeline_places WHERE timelineId = :timelineId ORDER BY sortOrder ASC")
    fun getPlaces(timelineId: String): Flow<List<TimelinePlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: TimelinePlaceEntity)

    @Delete
    suspend fun deletePlace(place: TimelinePlaceEntity)

    @Query("SELECT * FROM timeline_photos WHERE timelineId = :timelineId ORDER BY sortOrder ASC")
    fun getPhotos(timelineId: String): Flow<List<TimelinePhotoEntity>>

    @Query("SELECT * FROM timeline_photos WHERE placeId = :placeId ORDER BY sortOrder ASC")
    fun getPhotosForPlace(placeId: String): Flow<List<TimelinePhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: TimelinePhotoEntity)

    @Delete
    suspend fun deletePhoto(photo: TimelinePhotoEntity)

    @Query("SELECT * FROM timeline_stickers WHERE timelineId = :timelineId")
    fun getStickers(timelineId: String): Flow<List<TimelineStickerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSticker(sticker: TimelineStickerEntity)

    @Query("UPDATE timeline_stickers SET posX = :x, posY = :y WHERE id = :id")
    suspend fun updateStickerPosition(id: String, x: Float, y: Float)

    @Query("DELETE FROM timeline_stickers WHERE id = :id")
    suspend fun deleteSticker(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTimelines(timelines: List<TimelineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPlaces(places: List<TimelinePlaceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPhotos(photos: List<TimelinePhotoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStickers(stickers: List<TimelineStickerEntity>)
}
