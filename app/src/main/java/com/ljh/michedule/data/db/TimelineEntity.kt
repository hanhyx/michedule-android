package com.ljh.michedule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timelines")
data class TimelineEntity(
    @PrimaryKey val id: String,
    val roomCode: String,
    val date: String,
    val title: String = "",
    val createdBy: String,
    val createdAt: Long
)

@Entity(tableName = "timeline_places")
data class TimelinePlaceEntity(
    @PrimaryKey val id: String,
    val timelineId: String,
    val placeName: String,
    val time: String = "",
    val memo: String = "",
    val sortOrder: Int = 0
)

@Entity(tableName = "timeline_photos")
data class TimelinePhotoEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    val timelineId: String,
    val imageUrl: String,
    val sortOrder: Int = 0
)

@Entity(tableName = "timeline_stickers")
data class TimelineStickerEntity(
    @PrimaryKey val id: String,
    val timelineId: String,
    val placeId: String? = null,
    val photoId: String? = null,
    val stickerType: String = "emoji",
    val stickerValue: String,
    val posX: Float,
    val posY: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val placedBy: String
)
