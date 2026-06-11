package com.ljh.michedule.ui.calendar

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ljh.michedule.data.ShiftTypeManager
import com.ljh.michedule.data.db.*
import com.ljh.michedule.model.ShiftType
import com.ljh.michedule.ui.theme.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    date: LocalDate,
    shift: ShiftEntity?,
    events: List<EventEntity>,
    todos: List<TodoEntity>,
    mood: MoodEntity?,
    shiftHistory: List<ShiftHistoryEntity>,
    shiftTypeManager: ShiftTypeManager,
    datePlan: DatePlanEntity? = null,
    onDismiss: () -> Unit,
    onShiftSelect: (ShiftType) -> Unit,
    onShiftSelectById: (String) -> Unit = {},
    onShiftClear: () -> Unit,
    onAlbaToggle: (Boolean) -> Unit,
    onExtraShiftToggle: (String) -> Unit = {},
    onShiftTimeEdit: (ShiftType, String) -> Unit,
    onMemoChange: (String?) -> Unit,
    onAddEvent: () -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onAddTodo: (String, String?, Boolean) -> Unit,
    onToggleTodo: (Long, Boolean) -> Unit,
    onDeleteTodo: (Long) -> Unit,
    onMoodSelect: (String, String) -> Unit,
    myName: String = "",
    myCode: String = "",
    partnerName: String = "",
    onDatePlanSet: (String) -> Unit = {},
    onDatePlanDelete: () -> Unit = {}
) {
    val allTypes by shiftTypeManager.allTypes.collectAsState()
    var memoText by remember(date, shift) { mutableStateOf(shift?.memo ?: "") }
    val currentShiftId = shift?.type?.takeIf { it.isNotBlank() }
    var editingShiftType by remember { mutableStateOf<ShiftType?>(null) }

    if (editingShiftType != null) {
        TimeRangeEditDialog(
            shiftType = editingShiftType!!,
            onConfirm = { newRange ->
                onShiftTimeEdit(editingShiftType!!, newRange)
                editingShiftType = null
            },
            onDismiss = { editingShiftType = null }
        )
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = {
            if (memoText != (shift?.memo ?: "")) onMemoChange(memoText.ifBlank { null })
            onDismiss()
        },
        containerColor = DarkCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DarkBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Date header
            Text(
                text = date.format(DateTimeFormatter.ofPattern("M월 d일 (E)")),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── 1. 추가 근무 토글 (복수 선택) ──
            val extraTypes by shiftTypeManager.extraTypes.collectAsState()
            val rawExtras = shift?.getExtraShiftList() ?: emptyList()
            val activeExtras = if (rawExtras.isEmpty() && shift?.hasAlba == true) listOf("alba") else rawExtras

            if (extraTypes.isNotEmpty()) {
                Text(
                    "추가 근무",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                extraTypes.forEach { extra ->
                    val isActive = extra.id in activeExtras
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onExtraShiftToggle(extra.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isActive) extra.color.copy(alpha = 0.15f) else DarkSurface,
                        border = BorderStroke(1.dp, if (isActive) extra.color else DarkBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(extra.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(extra.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                    color = if (isActive) extra.color else TextPrimary)
                                Text(
                                    text = extra.defaultTimeRange,
                                    fontSize = 11.sp,
                                    color = if (isActive) extra.color.copy(alpha = 0.7f) else TextMuted
                                )
                            }
                            Switch(
                                checked = isActive,
                                onCheckedChange = { onExtraShiftToggle(extra.id) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = extra.color,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkBorder
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 10.dp))
            }

            // ── 2. 메모 ──
            Text("📝 메모", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = memoText,
                onValueChange = { memoText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("메모를 입력하세요", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple80, unfocusedBorderColor = DarkBorder,
                    cursorColor = Purple80, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onMemoChange(memoText.ifBlank { null })
                    keyboardController?.hide()
                    focusManager.clearFocus()
                })
            )
            if (memoText != (shift?.memo ?: "")) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        onMemoChange(memoText.ifBlank { null })
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) { Text("저장") }
            }

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 10.dp))

            // ── 3. 우리 만나요 ──
            DatePlanSection(datePlan = datePlan, myName = myName, myCode = myCode, partnerName = partnerName, onDatePlanSet = onDatePlanSet, onDatePlanDelete = onDatePlanDelete)

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 10.dp))

            // ── 4. 할 일 ──
            TodoSection(todos, onAddTodo, onToggleTodo, onDeleteTodo)

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 10.dp))

            // ── 5. 일정 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📅 일정", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                IconButton(onClick = onAddEvent, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, "추가", tint = Purple80)
                }
            }
            if (events.isEmpty()) {
                Text("등록된 일정이 없습니다", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            } else {
                events.forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(event.color)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.bodyMedium)
                            if (event.startTime != null) {
                                Text("${event.startTime}${event.endTime?.let { " - $it" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                        IconButton(onClick = { onDeleteEvent(event.id) }, Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, "삭제", Modifier.size(14.dp), tint = TextMuted)
                        }
                    }
                }
            }

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 10.dp))

            // ── 6. 오늘의 감정 ──
            MoodSection(mood = mood, onMoodSelect = onMoodSelect)

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 10.dp))

            // ── 7. 근무 유형 설정 (최하단) ──
            Text("근무 유형", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "꾹 눌러서 시간 수정",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allTypes.filter { it.id != "alba" }.forEach { config ->
                    val builtInType = ShiftType.fromString(config.id)
                    ShiftConfigButton(
                        config = config,
                        isActive = currentShiftId == config.id,
                        onClick = {
                            if (builtInType != null) onShiftSelect(builtInType)
                            else onShiftSelectById(config.id)
                        },
                        onLongClick = {
                            if (builtInType != null) editingShiftType = builtInType
                        }
                    )
                }
            }
            TextButton(onClick = onShiftClear, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Clear, null, Modifier.size(14.dp), tint = TextMuted)
                Spacer(Modifier.width(4.dp))
                Text("초기화", color = TextMuted, fontSize = 12.sp)
            }

            // ── Shift History ──
            if (shiftHistory.isNotEmpty()) {
                ShiftHistorySection(shiftHistory)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Mood Section ──

@Composable
private fun MoodSection(mood: MoodEntity?, onMoodSelect: (String, String) -> Unit) {
    val quickEmojis = listOf("😊", "🥰", "😐", "😢", "😤", "😴", "🤩", "😰", "🥲", "😎")
    var selectedEmoji by remember(mood) { mutableStateOf(mood?.emoji ?: "") }
    var moodNote by remember(mood) { mutableStateOf(mood?.note ?: "") }
    var customEmoji by remember { mutableStateOf("") }

    Text("오늘의 감정", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        quickEmojis.take(8).forEach { emoji ->
            val isSelected = selectedEmoji == emoji
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        selectedEmoji = emoji
                        onMoodSelect(emoji, moodNote)
                    },
                shape = CircleShape,
                color = if (isSelected) Purple40 else Color.Transparent,
                border = if (isSelected) BorderStroke(2.dp, Purple80) else null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 18.sp)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = customEmoji,
            onValueChange = { input ->
                customEmoji = input
                val trimmed = input.trim()
                if (trimmed.isNotBlank()) {
                    selectedEmoji = trimmed
                    onMoodSelect(trimmed, moodNote)
                }
            },
            modifier = Modifier.width(80.dp),
            placeholder = { Text("이모지", color = TextMuted, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple80, unfocusedBorderColor = DarkBorder,
                cursorColor = Purple80, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        OutlinedTextField(
            value = moodNote,
            onValueChange = {
                moodNote = it
                if (selectedEmoji.isNotBlank()) onMoodSelect(selectedEmoji, it)
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text("한줄 감정 메모...", color = TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple80, unfocusedBorderColor = DarkBorder,
                cursorColor = Purple80, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }

    if (selectedEmoji.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "선택됨: $selectedEmoji",
            fontSize = 12.sp,
            color = Purple80
        )
    }
}

// ── Shift History Section ──

@Composable
private fun ShiftHistorySection(history: List<ShiftHistoryEntity>) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.History, null, Modifier.size(14.dp), tint = TextMuted)
        Spacer(Modifier.width(4.dp))
        Text("변경 이력 (${history.size})", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.weight(1f))
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            null, Modifier.size(16.dp), tint = TextMuted
        )
    }
    if (expanded) {
        history.take(10).forEach { h ->
            val time = Instant.ofEpochMilli(h.changedAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
            val oldLabel = h.oldType?.let { ShiftType.fromString(it)?.label ?: it } ?: "없음"
            val newLabel = h.newType?.let { ShiftType.fromString(it)?.label ?: it } ?: "없음"

            Text(
                text = "$time : $oldLabel → $newLabel",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(start = 18.dp, top = 2.dp)
            )
        }
    }
}

// ── Todo Section ──

@Composable
private fun TodoSection(
    todos: List<TodoEntity>,
    onAdd: (String, String?, Boolean) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    var newTodoText by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("할 일", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        val doneCount = todos.count { it.isDone }
        if (todos.isNotEmpty()) {
            Text("$doneCount/${todos.size}", style = MaterialTheme.typography.bodySmall,
                color = if (doneCount == todos.size) ShiftOff else TextMuted)
        }
    }
    Spacer(modifier = Modifier.height(6.dp))

    todos.forEach { todo ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (todo.isDone) DarkSurface.copy(alpha = 0.5f) else DarkSurface)
                .clickable { onToggle(todo.id, !todo.isDone) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (todo.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null, tint = if (todo.isDone) ShiftOff else TextMuted, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = (if (todo.isHabit) "🔄 " else "") + todo.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (todo.isDone) TextMuted else TextPrimary,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onDelete(todo.id) }, Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, "삭제", Modifier.size(12.dp), tint = TextMuted)
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newTodoText,
            onValueChange = { newTodoText = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("할 일 추가...", color = TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple80, unfocusedBorderColor = DarkBorder,
                cursorColor = Purple80, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        IconButton(onClick = {
            if (newTodoText.isNotBlank()) {
                onAdd(newTodoText.trim(), null, false)
                newTodoText = ""
            }
        }) { Icon(Icons.Default.Add, "추가", tint = Purple80) }
    }
}

// ── Shift Config Button ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShiftConfigButton(
    config: ShiftTypeConfig,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) config.bgColor else DarkSurface)
            .border(1.dp, if (isActive) config.color else DarkBorder, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(config.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(config.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = if (isActive) config.color else TextPrimary)
            Text(config.defaultTimeRange, fontSize = 11.sp,
                color = if (isActive) config.color.copy(alpha = 0.7f) else TextMuted)
        }
        if (isActive) {
            Icon(Icons.Default.CheckCircle, null, tint = config.color, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Time Range Edit Dialog ──

@Composable
private fun TimeRangeEditDialog(
    shiftType: ShiftType,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val currentRange = shiftType.timeRange
    val parts = currentRange.replace("~", "-").split("-").map { it.trim() }
    var startTime by remember { mutableStateOf(if (parts.size >= 2 && parts[0].contains(":")) parts[0] else "") }
    var endTime by remember { mutableStateOf(if (parts.size >= 2 && parts[1].contains(":")) parts[1] else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(shiftType.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("${shiftType.label} 시간 설정", color = TextPrimary)
            }
        },
        text = {
            Column {
                Text("현재: $currentRange", fontSize = 12.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { if (it.length <= 5) startTime = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("시작", color = TextMuted, fontSize = 12.sp) },
                        placeholder = { Text("HH:MM", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = shiftType.color,
                            unfocusedBorderColor = DarkBorder,
                            cursorColor = shiftType.color,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text("~", color = TextMuted, fontSize = 16.sp)
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { if (it.length <= 5) endTime = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("종료", color = TextMuted, fontSize = 12.sp) },
                        placeholder = { Text("HH:MM", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = shiftType.color,
                            unfocusedBorderColor = DarkBorder,
                            cursorColor = shiftType.color,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "기본값: ${shiftType.defaultTimeRange}",
                    fontSize = 11.sp, color = TextMuted
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onConfirm(shiftType.defaultTimeRange)
                }) {
                    Text("기본값", color = TextMuted)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val range = "$startTime - $endTime"
                        onConfirm(range)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = shiftType.color),
                    enabled = startTime.isNotBlank() && endTime.isNotBlank()
                ) {
                    Text("저장")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TextMuted)
            }
        }
    )
}

// ── Date Plan Section ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDayDetailSheet(
    date: LocalDate,
    friendShift: FriendShiftEntity?,
    datePlan: DatePlanEntity?,
    shiftTypeManager: ShiftTypeManager,
    onDismiss: () -> Unit
) {
    val shiftConfig = friendShift?.type?.takeIf { it.isNotBlank() }?.let { shiftTypeManager.getByIdForPartner(it) }
    val partnerName = friendShift?.friendName?.ifBlank { "상대" } ?: "상대"
    val formatter = DateTimeFormatter.ofPattern("M월 d일 (E)")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💕", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "${partnerName}의 일정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        date.format(formatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            HorizontalDivider(color = DarkBorder)

            // 근무 유형
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📋", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("근무", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            }
            if (shiftConfig != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = shiftConfig.bgColor
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(shiftConfig.emoji, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                shiftConfig.label,
                                fontWeight = FontWeight.Bold,
                                color = shiftConfig.color
                            )
                            if (shiftConfig.defaultTimeRange.isNotBlank()) {
                                Text(
                                    shiftConfig.defaultTimeRange,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = shiftConfig.color.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            } else {
                Text("등록된 근무가 없습니다", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }

            // 추가 근무
            val partnerExtras = friendShift?.getExtraShiftList() ?: emptyList()
            if (partnerExtras.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    partnerExtras.forEach { extraId ->
                        val ec = shiftTypeManager.getByIdForPartner(extraId)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (ec?.color ?: Color(0xFFFBBF24)).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, (ec?.color ?: Color(0xFFFBBF24)).copy(alpha = 0.3f))
                        ) {
                            Text(
                                "${ec?.emoji ?: "💼"} ${ec?.label ?: extraId}",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = ec?.color ?: Color(0xFFFBBF24)
                            )
                        }
                    }
                }
            }

            // 메모
            if (!friendShift?.memo.isNullOrBlank()) {
                HorizontalDivider(color = DarkBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📝", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("메모", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface
                ) {
                    Text(
                        friendShift?.memo ?: "",
                        modifier = Modifier.padding(12.dp),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 기분
            if (!friendShift?.mood.isNullOrBlank()) {
                HorizontalDivider(color = DarkBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("😊", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("기분", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                }
                Text(friendShift?.mood ?: "", fontSize = 28.sp)
            }

            // 할 일
            val todoList = friendShift?.getTodoList() ?: emptyList()
            if (todoList.isNotEmpty()) {
                HorizontalDivider(color = DarkBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✅", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("할 일", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Purple40.copy(alpha = 0.3f)
                    ) {
                        Text(
                            "${todoList.size}개",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Purple80
                        )
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        todoList.forEach { (title, isDone) ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (isDone) "✅" else "☐",
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDone) TextMuted else TextPrimary,
                                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            } else if ((friendShift?.todoCount ?: 0) > 0) {
                HorizontalDivider(color = DarkBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✅", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("할 일", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Purple40.copy(alpha = 0.3f)
                    ) {
                        Text(
                            "${friendShift?.todoCount}개",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Purple80
                        )
                    }
                }
            }

            // 만나요
            if (datePlan != null) {
                HorizontalDivider(color = DarkBorder)
                val heartColor = Color(0xFFEC4899)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = heartColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, heartColor.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("💕 데이트 예정!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = heartColor)
                        if (datePlan.memo.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(datePlan.memo, fontSize = 13.sp, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePlanSection(
    datePlan: DatePlanEntity?,
    myName: String,
    myCode: String = "",
    partnerName: String = "",
    onDatePlanSet: (String) -> Unit,
    onDatePlanDelete: () -> Unit
) {
    var planMemo by remember(datePlan) { mutableStateOf(datePlan?.memo ?: "") }
    val heartColor = Color(0xFFEC4899)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("💕", fontSize = 18.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text("우리 만나요", style = MaterialTheme.typography.labelLarge, color = heartColor)
    }
    Spacer(modifier = Modifier.height(8.dp))

    if (datePlan != null) {
        val canCancel = datePlan.createdBy == myCode || datePlan.createdBy == myName || datePlan.createdBy == "나"
        val displayName = when (datePlan.createdBy) {
            myCode -> myName.ifBlank { "나" }
            "나" -> myName.ifBlank { "나" }
            else -> partnerName.ifBlank { datePlan.createdBy }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = heartColor.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, heartColor.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💕 데이트 예정!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = heartColor)
                    Spacer(modifier = Modifier.weight(1f))
                    if (canCancel) {
                        Text(
                            "취소",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.clickable { onDatePlanDelete() }
                        )
                    }
                }
                if (datePlan.memo.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(datePlan.memo, fontSize = 13.sp, color = TextPrimary)
                }
                if (displayName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("by $displayName", fontSize = 10.sp, color = TextMuted)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = planMemo,
            onValueChange = { planMemo = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("메모 (선택)", fontSize = 13.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = heartColor,
                unfocusedBorderColor = DarkBorder,
                cursorColor = heartColor
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { onDatePlanSet(planMemo) },
            colors = ButtonDefaults.buttonColors(containerColor = heartColor),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("💕 만나요!", fontSize = 13.sp)
        }
    }
}
