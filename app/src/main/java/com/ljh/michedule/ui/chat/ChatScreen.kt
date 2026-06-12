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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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

    val app = LocalContext.current.applicationContext as MicheduleApp
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
    var showReactionFor by remember { mutableStateOf<String?>(null) }
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

                    if (showReactionFor == msg.id) {
                        ReactionPicker(
                            onSelect = { emoji ->
                                viewModel.addReaction(msg.id, emoji)
                                showReactionFor = null
                            },
                            onDismiss = { showReactionFor = null }
                        )
                    }

                    ChatBubble(
                        message = msg,
                        isMine = isMine,
                        partnerPhotoUri = uiState.partnerPhotoUri,
                        partnerName = uiState.partnerName,
                        showProfile = showProfile,
                        onLongClick = { showReactionFor = msg.id }
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
    onLongClick: () -> Unit
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
                        Text("💜", fontSize = 16.sp)
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
                        fontSize = 9.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                    )
                }

                Surface(
                    shape = bubbleShape,
                    color = bubbleColor,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick
                    )
                ) {
                    if (message.messageType == "image" && !message.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = "이미지",
                            modifier = Modifier
                                .widthIn(max = 220.dp)
                                .heightIn(max = 300.dp)
                                .clip(bubbleShape),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            message.content,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                if (!isMine) {
                    Text(
                        timeStr,
                        fontSize = 9.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }
            }

            if (reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(start = 4.dp, top = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    reactions.forEach { (emoji, _) ->
                        Text(emoji, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionPicker(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
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
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                REACTION_EMOJIS.forEach { emoji ->
                    Text(
                        emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onSelect(emoji) }
                            .padding(6.dp)
                    )
                }
            }
        }
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
                    fontSize = 14.sp,
                    color = TextPrimary
                ),
                maxLines = 4,
                cursorBrush = SolidColor(Purple80),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text("메시지 입력", color = TextMuted, fontSize = 14.sp)
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
