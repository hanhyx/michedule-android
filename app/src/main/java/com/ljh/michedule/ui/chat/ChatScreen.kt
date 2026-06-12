package com.ljh.michedule.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ljh.michedule.MicheduleApp
import com.ljh.michedule.data.db.ChatMessageEntity
import com.ljh.michedule.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

private val REACTION_EMOJIS = listOf("❤️", "😂", "😮", "😢", "👍", "🔥")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val app = context.applicationContext as MicheduleApp
    DisposableEffect(Unit) {
        app.isChatScreenActive = true
        onDispose { app.isChatScreenActive = false }
    }

    LaunchedEffect(uiState.messages.firstOrNull()?.createdAt) {
        val latest = uiState.messages.firstOrNull()?.createdAt ?: return@LaunchedEffect
        app.prefsManager.setChatLastReadAt(latest)
    }

    if (!uiState.connectionMutual || uiState.partnerCode.isBlank()) {
        EmptyChatScreen(onNavigateToSettings, modifier)
        return
    }

    var inputText by remember { mutableStateOf("") }
    var actionMenuFor by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendImage(it) }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val grouped = groupMessagesByDate(uiState.messages)
            grouped.forEach { (dateLabel, messages) ->
                itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                    val isMine = msg.senderCode == uiState.myCode
                    val nextMsg = messages.getOrNull(index + 1)
                    val showProfile = !isMine && (nextMsg == null || nextMsg.senderCode != msg.senderCode
                            || (msg.createdAt - nextMsg.createdAt) > 300_000)

                    if (actionMenuFor?.id == msg.id) {
                        MessageActionMenu(
                            isMine = isMine,
                            isImage = msg.messageType == "image",
                            onReact = { emoji ->
                                viewModel.addReaction(msg.id, emoji)
                                actionMenuFor = null
                            },
                            onCopy = {
                                val clip = android.content.ClipData.newPlainText("message", msg.content)
                                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                    .setPrimaryClip(clip)
                                actionMenuFor = null
                            },
                            onDelete = {
                                viewModel.deleteMessage(msg.id)
                                actionMenuFor = null
                            },
                            onDismiss = { actionMenuFor = null }
                        )
                    }

                    ChatBubble(
                        message = msg,
                        isMine = isMine,
                        partnerPhotoUri = uiState.partnerPhotoUri,
                        partnerName = uiState.partnerName,
                        showProfile = showProfile,
                        onLongClick = { actionMenuFor = msg },
                        onImageClick = { url -> fullscreenImageUrl = url }
                    )
                }

                item(key = "date_$dateLabel") {
                    DateDivider(dateLabel)
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Purple80, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        ChatInput(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText.trim())
                    inputText = ""
                    scope.launch { listState.animateScrollToItem(0) }
                }
            },
            onImageClick = { imagePicker.launch("image/*") }
        )
    }

    if (fullscreenImageUrl != null) {
        FullscreenImageViewer(
            imageUrl = fullscreenImageUrl!!,
            onDismiss = { fullscreenImageUrl = null }
        )
    }
}

@Composable
private fun EmptyChatScreen(onNavigateToSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("💬", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "상대방과 연결 후\n채팅할 수 있습니다",
            textAlign = TextAlign.Center,
            color = TextMuted,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNavigateToSettings,
            colors = ButtonDefaults.buttonColors(containerColor = Purple40),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("설정으로 이동")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: ChatMessageEntity,
    isMine: Boolean,
    partnerPhotoUri: String,
    partnerName: String,
    showProfile: Boolean = true,
    onLongClick: () -> Unit,
    onImageClick: ((String) -> Unit)? = null
) {
    val bubbleColor = if (isMine) Purple40 else DarkCard
    val bubbleShape = if (isMine) {
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    } else if (showProfile) {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp)
    }

    val timeStr = formatTime(message.createdAt)
    val reactions = parseReactions(message.reactions)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isMine) {
            if (showProfile) {
                if (partnerPhotoUri.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(partnerPhotoUri)
                            .crossfade(200)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey(partnerPhotoUri)
                            .diskCacheKey(partnerPhotoUri)
                            .build(),
                        contentDescription = partnerName,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👤", fontSize = 16.sp)
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(36.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            if (!isMine && showProfile) {
                Text(
                    partnerName,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
            ) {
                if (isMine) {
                    Text(
                        timeStr,
                        fontSize = 10.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                    )
                }

                Box(modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                }) {
                    Surface(
                        shape = bubbleShape,
                        color = if (message.messageType == "image") Color.Transparent else bubbleColor,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (message.messageType == "image" && !message.imageUrl.isNullOrBlank()) {
                                    onImageClick?.invoke(message.imageUrl!!)
                                }
                            },
                            onLongClick = onLongClick
                        )
                    ) {
                        if (message.messageType == "image" && !message.imageUrl.isNullOrBlank()) {
                            val painter = rememberAsyncImagePainter(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(message.imageUrl)
                                    .crossfade(300)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            )
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 120.dp, max = 220.dp)
                                    .heightIn(min = 80.dp, max = 300.dp)
                                    .clip(bubbleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                when (painter.state) {
                                    is coil.compose.AsyncImagePainter.State.Loading -> {
                                        CircularProgressIndicator(
                                            color = Purple80,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                    is coil.compose.AsyncImagePainter.State.Error -> {
                                        Text("⚠️", fontSize = 24.sp)
                                    }
                                    else -> {}
                                }
                                Image(
                                    painter = painter,
                                    contentDescription = "이미지",
                                    modifier = Modifier
                                        .widthIn(max = 220.dp)
                                        .heightIn(max = 300.dp)
                                        .clip(bubbleShape),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            Text(
                                message.content,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = Color.White,
                                fontSize = 15.sp,
                                lineHeight = 21.sp
                            )
                        }
                    }

                    if (reactions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 2.dp, y = 14.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            reactions.forEach { (emoji, _) ->
                                Text(emoji, fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (!isMine) {
                    Text(
                        timeStr,
                        fontSize = 10.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionMenu(
    isMine: Boolean,
    isImage: Boolean,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DarkBorder),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                REACTION_EMOJIS.forEach { emoji ->
                    Text(
                        emoji,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onReact(emoji) }
                            .padding(4.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(20.dp)
                        .background(DarkBorder)
                )

                if (!isImage) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "복사",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onCopy() }
                            .padding(5.dp)
                    )
                }

                if (isMine) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = Color(0xFFF87171),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { showDeleteConfirm = true }
                            .padding(5.dp)
                    )
                }

                Icon(
                    Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() }
                        .padding(5.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("메시지 삭제", color = TextPrimary) },
            text = { Text("이 메시지를 삭제하시겠습니까?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("삭제", color = Color(0xFFF87171))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소", color = TextPrimary)
                }
            },
            containerColor = DarkCard
        )
    }
}

@Composable
private fun DateDivider(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Text(
                date,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onImageClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = "이미지",
                tint = TextMuted,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onImageClick)
                    .padding(2.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp, max = 100.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 15.sp,
                    color = TextPrimary
                ),
                maxLines = 4,
                cursorBrush = SolidColor(Purple80),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text("메시지 입력", color = TextMuted, fontSize = 15.sp)
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank()) Purple40 else Color.Transparent)
                    .clickable(enabled = text.isNotBlank(), onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "전송",
                    tint = if (text.isNotBlank()) Color.White else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun groupMessagesByDate(messages: List<ChatMessageEntity>): List<Pair<String, List<ChatMessageEntity>>> {
    val dateFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA)
    return messages
        .groupBy { dateFormat.format(Date(it.createdAt)) }
        .toList()
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("a h:mm", Locale.KOREA)
    return sdf.format(Date(timestamp))
}

private fun parseReactions(json: String): Map<String, String> {
    return try {
        val obj = Json.decodeFromString<JsonObject>(json)
        obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: "" }
    } catch (_: Exception) {
        emptyMap()
    }
}

@Composable
private fun FullscreenImageViewer(imageUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(300)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = "이미지",
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentScale = ContentScale.Fit
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    kotlinx.coroutines.MainScope().launch {
                        try {
                            val resolver = context.contentResolver
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "michedule_${System.currentTimeMillis()}.jpg")
                                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Michedule")
                            }
                            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            if (uri != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val url = java.net.URL(imageUrl)
                                    val bytes = url.openStream().use { it.readBytes() }
                                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                                }
                                android.widget.Toast.makeText(context, "이미지 저장 완료", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "저장 실패", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DarkCard.copy(alpha = 0.7f))
            ) {
                Icon(Icons.Default.Download, contentDescription = "저장", tint = Color.White, modifier = Modifier.size(22.dp))
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DarkCard.copy(alpha = 0.7f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}
