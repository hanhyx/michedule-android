package com.ljh.michedule.ui.timeline

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ljh.michedule.data.db.*
import com.ljh.michedule.data.repository.UploadState
import com.ljh.michedule.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = viewModel()
) {
    val selectedTimeline by viewModel.selectedTimeline.collectAsStateWithLifecycle()

    if (selectedTimeline != null) {
        TimelineDetailScreen(
            viewModel = viewModel,
            onBack = { viewModel.selectTimeline(null) },
            modifier = modifier
        )
    } else {
        TimelineListScreen(
            viewModel = viewModel,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineListScreen(
    viewModel: TimelineViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val timelines by viewModel.timelines.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.syncFromRemote() }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("타임라인", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "추가", tint = colors.accent)
            }
        }

        if (timelines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📍", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("아직 타임라인이 없어요", color = colors.textMuted, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("데이트나 일상을 기록해보세요!", color = colors.textMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(timelines, key = { it.id }) { timeline ->
                    TimelineCard(
                        timeline = timeline,
                        onClick = { viewModel.selectTimeline(timeline.id) },
                        onDelete = { viewModel.deleteTimeline(timeline) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTimelineDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { date, title ->
                viewModel.createTimeline(date, title)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineCard(
    timeline: TimelineEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDeleteConfirm = true }
            ),
        color = colors.card,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val date = try { LocalDate.parse(timeline.date) } catch (_: Exception) { null }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(colors.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = date?.dayOfMonth?.toString() ?: "?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
                Text(
                    text = date?.format(DateTimeFormatter.ofPattern("EEE", Locale.KOREAN)) ?: "",
                    fontSize = 12.sp,
                    color = colors.accent
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeline.title.ifBlank { date?.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")) ?: timeline.date },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = date?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) ?: timeline.date,
                    fontSize = 12.sp,
                    color = colors.textMuted
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.textMuted)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("삭제", color = colors.textPrimary) },
            text = { Text("이 타임라인을 삭제할까요?", color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("삭제", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소", color = colors.textMuted)
                }
            },
            containerColor = colors.card
        )
    }
}

@Composable
private fun CreateTimelineDialog(
    onDismiss: () -> Unit,
    onCreate: (date: String, title: String) -> Unit
) {
    val colors = LocalAppColors.current
    var title by remember { mutableStateOf("") }
    val today = LocalDate.now().toString()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 타임라인", color = colors.textPrimary) },
        text = {
            Column {
                Text("오늘 날짜로 생성됩니다", color = colors.textMuted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("제목 (선택)", color = colors.textMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        cursorColor = colors.accent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(today, title) }) {
                Text("생성", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = colors.textMuted)
            }
        },
        containerColor = colors.card
    )
}

// ── Detail Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineDetailScreen(
    viewModel: TimelineViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val timeline by viewModel.selectedTimeline.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val stickers by viewModel.stickers.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val locationLoading by viewModel.locationLoading.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val uploadError by viewModel.uploadError.collectAsStateWithLifecycle()

    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var stickerTargetPlaceId by remember { mutableStateOf<String?>(null) }
    var stickerTargetPhotoId by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) {
            viewModel.fetchCurrentLocation()
        }
        showAddPlaceDialog = true
    }

    val tl = timeline ?: return

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uploadError) {
        uploadError?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearUploadError()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFFEF4444),
                    contentColor = Color.White
                )
            }
        },
        containerColor = colors.background
    ) { innerPadding ->
    Column(modifier = modifier.fillMaxSize().padding(innerPadding).background(colors.background)) {
        if (uploadState == UploadState.UPLOADING) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.accent,
                trackColor = colors.border
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = colors.textPrimary)
            }
            Text(
                text = tl.title.ifBlank {
                    try {
                        LocalDate.parse(tl.date).format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))
                    } catch (_: Exception) { tl.date }
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showStickerPicker = true; stickerTargetPlaceId = null; stickerTargetPhotoId = null }) {
                Text("🎨", fontSize = 22.sp)
            }
            IconButton(onClick = {
                if (viewModel.locationHelper.hasLocationPermission()) {
                    viewModel.fetchCurrentLocation()
                    showAddPlaceDialog = true
                } else {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }) {
                Icon(Icons.Default.AddLocation, contentDescription = "장소 추가", tint = colors.accent)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(places, key = { it.id }) { place ->
                    val placePhotos = photos.filter { it.placeId == place.id }
                    PlaceTimelineItem(
                        place = place,
                        photos = placePhotos,
                        stickers = stickers.filter { it.placeId == place.id },
                        isLast = place == places.lastOrNull(),
                        onAddPhoto = { uri -> viewModel.addPhoto(place.id, uri) },
                        onAddSticker = {
                            stickerTargetPlaceId = place.id
                            stickerTargetPhotoId = null
                            showStickerPicker = true
                        },
                        onStickerMove = { id, x, y -> viewModel.moveSticker(id, x, y) },
                        onStickerRemove = { id -> viewModel.removeSticker(id) },
                        onDelete = { viewModel.deletePlace(place) }
                    )
                }

                if (places.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📸", fontSize = 40.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("장소를 추가해보세요!", color = colors.textMuted, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            val timelineStickers = stickers.filter { it.placeId == null && it.photoId == null }
            timelineStickers.forEach { sticker ->
                DraggableSticker(
                    sticker = sticker,
                    onMove = { x, y -> viewModel.moveSticker(sticker.id, x, y) },
                    onRemove = { viewModel.removeSticker(sticker.id) }
                )
            }
        }
    }
    } // Scaffold

    if (showAddPlaceDialog) {
        AddPlaceDialog(
            onDismiss = { showAddPlaceDialog = false },
            onAdd = { name, time, memo ->
                viewModel.addPlace(name, time, memo)
                showAddPlaceDialog = false
            },
            defaultTime = viewModel.getCurrentTimeFormatted(),
            detectedPlaceName = currentLocation?.placeName,
            isLoadingLocation = locationLoading
        )
    }

    if (showStickerPicker) {
        StickerPickerSheet(
            onDismiss = { showStickerPicker = false },
            onSelect = { type, value ->
                viewModel.addSticker(
                    stickerType = type,
                    stickerValue = value,
                    posX = 0.5f,
                    posY = 0.5f,
                    placeId = stickerTargetPlaceId,
                    photoId = stickerTargetPhotoId
                )
                showStickerPicker = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceTimelineItem(
    place: TimelinePlaceEntity,
    photos: List<TimelinePhotoEntity>,
    stickers: List<TimelineStickerEntity>,
    isLast: Boolean,
    onAddPhoto: (Uri) -> Unit,
    onAddSticker: () -> Unit,
    onStickerMove: (String, Float, Float) -> Unit,
    onStickerRemove: (String) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAddPhoto(it) }
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(colors.accent, CircleShape)
                    .border(2.dp, colors.accent.copy(alpha = 0.3f), CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(IntrinsicSize.Max)
                        .defaultMinSize(minHeight = 80.dp)
                        .background(colors.border)
                )
            }
        }

        Column(modifier = Modifier.weight(1f).padding(bottom = 16.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showDeleteConfirm = true }
                    ),
                color = colors.card,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Box {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (place.time.isNotBlank()) {
                                Text(place.time, fontSize = 12.sp, color = colors.accent, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(place.placeName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        }
                        if (place.memo.isNotBlank()) {
                            Text(place.memo, fontSize = 13.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        }

                        if (photos.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(photos, key = { it.id }) { photo ->
                                    AsyncImage(
                                        model = photo.imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { photoLauncher.launch("image/*") }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("사진", fontSize = 12.sp, color = colors.textMuted)
                            }
                            TextButton(onClick = onAddSticker, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("🎨", fontSize = 14.sp)
                                Spacer(Modifier.width(4.dp))
                                Text("스티커", fontSize = 12.sp, color = colors.textMuted)
                            }
                        }
                    }

                    stickers.forEach { sticker ->
                        DraggableSticker(
                            sticker = sticker,
                            onMove = { x, y -> onStickerMove(sticker.id, x, y) },
                            onRemove = { onStickerRemove(sticker.id) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("장소 삭제", color = colors.textPrimary) },
            text = { Text("'${place.placeName}'을(를) 삭제할까요?", color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("삭제", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소", color = colors.textMuted)
                }
            },
            containerColor = colors.card
        )
    }
}

@Composable
fun DraggableSticker(
    sticker: TimelineStickerEntity,
    onMove: (Float, Float) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offset by remember(sticker.id) { mutableStateOf(Offset(sticker.posX, sticker.posY)) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    var showRemoveButton by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onGloballyPositioned { containerSize = it.parentLayoutCoordinates?.size ?: IntSize.Zero }
            .offset(
                x = with(density) { (offset.x * (containerSize.width.coerceAtLeast(1))).toDp() },
                y = with(density) { (offset.y * (containerSize.height.coerceAtLeast(1))).toDp() }
            )
            .pointerInput(sticker.id) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        onMove(offset.x, offset.y)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = (offset.x + dragAmount.x / containerSize.width.coerceAtLeast(1)).coerceIn(0f, 0.9f)
                        val newY = (offset.y + dragAmount.y / containerSize.height.coerceAtLeast(1)).coerceIn(0f, 0.9f)
                        offset = Offset(newX, newY)
                    }
                )
            }
            .clickable { showRemoveButton = !showRemoveButton }
    ) {
        if (sticker.stickerType == "emoji") {
            Text(
                text = sticker.stickerValue,
                fontSize = (28 * sticker.scale).sp,
                modifier = Modifier.padding(2.dp)
            )
        } else {
            AsyncImage(
                model = sticker.stickerValue,
                contentDescription = null,
                modifier = Modifier
                    .size((48 * sticker.scale).dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }

        AnimatedVisibility(
            visible = showRemoveButton && !isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp)
        ) {
            IconButton(
                onClick = { onRemove(); showRemoveButton = false },
                modifier = Modifier
                    .size(20.dp)
                    .background(Color(0xFFEF4444), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "제거", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun AddPlaceDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, time: String, memo: String) -> Unit,
    defaultTime: String = "",
    detectedPlaceName: String? = null,
    isLoadingLocation: Boolean = false
) {
    val colors = LocalAppColors.current
    var name by remember(detectedPlaceName) { mutableStateOf(detectedPlaceName ?: "") }
    var time by remember { mutableStateOf(defaultTime) }
    var memo by remember { mutableStateOf("") }
    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.border,
        cursorColor = colors.accent,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary
    )

    LaunchedEffect(detectedPlaceName) {
        if (detectedPlaceName != null && name.isBlank()) {
            name = detectedPlaceName
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("장소 추가", color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = {
                        if (isLoadingLocation) Text("📍 위치 감지 중...", color = colors.accent)
                        else Text("장소 이름", color = colors.textMuted)
                    },
                    leadingIcon = {
                        if (isLoadingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.accent
                            )
                        } else {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = tfColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    placeholder = { Text("시간", color = colors.textMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    },
                    colors = tfColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    placeholder = { Text("메모 (선택)", color = colors.textMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                    },
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onAdd(name, time, memo) }, enabled = name.isNotBlank()) {
                Text("추가", color = if (name.isNotBlank()) colors.accent else colors.textMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = colors.textMuted)
            }
        },
        containerColor = colors.card
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (type: String, value: String) -> Unit
) {
    val colors = LocalAppColors.current
    val emojiList = listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🩷", "🖤",
        "⭐", "🌟", "✨", "💫", "🔥", "🎉", "🎊", "🎈",
        "🌸", "🌺", "🌻", "🌹", "🌷", "🍀", "🌿", "🍃",
        "☕", "🍰", "🧁", "🍩", "🍫", "🍦", "🎂", "🍕",
        "🐱", "🐶", "🐰", "🐻", "🦊", "🐼", "🐨", "🦋",
        "📸", "🎵", "🎶", "💌", "🎀", "👑", "💎", "🪄",
        "😍", "🥰", "😘", "🤗", "😊", "🥳", "😎", "🤩"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.textMuted) }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("스티커 선택", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text("붙이고 싶은 스티커를 골라보세요!", fontSize = 13.sp, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))

            val columns = 8
            val rows = (emojiList.size + columns - 1) / columns
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (c in 0 until columns) {
                        val idx = r * columns + c
                        if (idx < emojiList.size) {
                            Text(
                                text = emojiList[idx],
                                fontSize = 28.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelect("emoji", emojiList[idx]) }
                                    .wrapContentSize(Alignment.Center)
                            )
                        } else {
                            Spacer(Modifier.size(44.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
