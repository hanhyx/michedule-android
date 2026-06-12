package com.ljh.michedule.ui.timeline

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ljh.michedule.MicheduleApp
import com.ljh.michedule.data.db.*
import com.ljh.michedule.data.repository.TimelineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(app: Application) : AndroidViewModel(app) {
    private val micheduleApp = app as MicheduleApp
    private val repo: TimelineRepository = micheduleApp.timelineRepository
    private val prefsManager = micheduleApp.prefsManager

    private val _roomCode = MutableStateFlow("")
    val roomCode: StateFlow<String> = _roomCode

    val timelines: StateFlow<List<TimelineEntity>> = _roomCode
        .filter { it.isNotBlank() }
        .flatMapLatest { repo.getTimelines(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTimelineId = MutableStateFlow<String?>(null)

    val selectedTimeline: StateFlow<TimelineEntity?> = _selectedTimelineId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else timelines.map { list -> list.find { it.id == id } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val places: StateFlow<List<TimelinePlaceEntity>> = _selectedTimelineId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repo.getPlaces(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val photos: StateFlow<List<TimelinePhotoEntity>> = _selectedTimelineId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repo.getPhotos(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stickers: StateFlow<List<TimelineStickerEntity>> = _selectedTimelineId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repo.getStickers(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val myCode = prefsManager.myCode.first()
            val partnerCode = prefsManager.partnerCode.first()
            if (myCode.isNotBlank() && partnerCode.isNotBlank()) {
                val sorted = listOf(myCode, partnerCode).sorted()
                _roomCode.value = "${sorted[0]}_${sorted[1]}"
            }
        }
    }

    fun selectTimeline(id: String?) {
        _selectedTimelineId.value = id
    }

    fun createTimeline(date: String, title: String) {
        val rc = _roomCode.value
        if (rc.isBlank()) return
        viewModelScope.launch {
            val tl = repo.createTimeline(rc, date, title)
            _selectedTimelineId.value = tl.id
        }
    }

    fun addPlace(placeName: String, time: String = "", memo: String = "") {
        val tlId = _selectedTimelineId.value ?: return
        viewModelScope.launch {
            val currentCount = places.value.size
            repo.addPlace(tlId, placeName, time, memo, currentCount)
        }
    }

    fun addPhoto(placeId: String, imageUri: Uri) {
        val tlId = _selectedTimelineId.value ?: return
        viewModelScope.launch {
            val currentCount = photos.value.count { it.placeId == placeId }
            repo.addPhoto(placeId, tlId, imageUri, currentCount)
        }
    }

    fun addSticker(
        stickerType: String,
        stickerValue: String,
        posX: Float, posY: Float,
        placeId: String? = null,
        photoId: String? = null
    ) {
        val tlId = _selectedTimelineId.value ?: return
        viewModelScope.launch {
            repo.addSticker(tlId, placeId, photoId, stickerType, stickerValue, posX, posY)
        }
    }

    fun moveSticker(id: String, x: Float, y: Float) {
        viewModelScope.launch { repo.moveSticker(id, x, y) }
    }

    fun removeSticker(id: String) {
        viewModelScope.launch { repo.removeSticker(id) }
    }

    fun deleteTimeline(timeline: TimelineEntity) {
        viewModelScope.launch {
            repo.deleteTimeline(timeline)
            if (_selectedTimelineId.value == timeline.id) _selectedTimelineId.value = null
        }
    }

    fun deletePlace(place: TimelinePlaceEntity) {
        viewModelScope.launch { repo.deletePlace(place) }
    }

    fun syncFromRemote() {
        val rc = _roomCode.value
        if (rc.isBlank()) return
        viewModelScope.launch { repo.syncFromRemote(rc) }
    }
}
