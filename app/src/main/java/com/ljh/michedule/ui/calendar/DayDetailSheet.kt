package com.ljh.michedule.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.ljh.michedule.data.db.*
import com.ljh.michedule.model.ShiftType
import com.ljh.michedule.ui.theme.*
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
    friendShift: FriendShiftEntity?,
    todos: List<TodoEntity>,
    mood: MoodEntity?,
    shiftHistory: List<ShiftHistoryEntity>,
    onDismiss: () -> Unit,
    onShiftSelect: (ShiftType) -> Unit,
    onShiftClear: () -> Unit,
    onMemoChange: (String?) -> Unit,
    onAddEvent: () -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onAddTodo: (String, String?, Boolean) -> Unit,
    onToggleTodo: (Long, Boolean) -> Unit,
    onDeleteTodo: (Long) -> Unit,
    onMoodSelect: (String, String) -> Unit
) {
    var memoText by remember(date, shift) { mutableStateOf(shift?.memo ?: "") }
    val currentShift = shift?.let { ShiftType.fromString(it.type) }

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
        ) {
            // Date header
            Text(
                text = date.format(DateTimeFormatter.ofPattern("M월 d일 (E)")),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── Mood Section ──
            MoodSection(mood = mood, onMoodSelect = onMoodSelect)

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))

            // ── Shift Selection ──
            Text("근무 유형", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShiftType.entries.forEach { type ->
                    ShiftButton(type = type, isActive = currentShift == type, onClick = { onShiftSelect(type) })
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

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))

            // ── Memo ──
            Text("메모", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
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
                maxLines = 3
            )
            if (memoText != (shift?.memo ?: "")) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { onMemoChange(memoText.ifBlank { null }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) { Text("저장") }
            }

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))

            // ── Todos ──
            TodoSection(todos, onAddTodo, onToggleTodo, onDeleteTodo)

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))

            // ── Events ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("일정", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
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

            // ── Friend shift ──
            if (friendShift != null) {
                val fType = ShiftType.fromString(friendShift.type)
                if (fType != null) {
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, Modifier.size(18.dp), tint = fType.color)
                        Spacer(Modifier.width(8.dp))
                        Text("${friendShift.friendName}: ${fType.emoji} ${fType.label}",
                            style = MaterialTheme.typography.bodyLarge, color = fType.color)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Mood Section ──

@Composable
private fun MoodSection(mood: MoodEntity?, onMoodSelect: (String, String) -> Unit) {
    val moodOptions = listOf("😊", "🥰", "😐", "😢", "😤", "😴", "🤩", "😰")
    var selectedEmoji by remember(mood) { mutableStateOf(mood?.emoji ?: "") }
    var moodNote by remember(mood) { mutableStateOf(mood?.note ?: "") }

    Text("오늘의 감정", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        moodOptions.forEach { emoji ->
            val isSelected = selectedEmoji == emoji
            Surface(
                modifier = Modifier
                    .size(38.dp)
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
                    Text(emoji, fontSize = 20.sp)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    OutlinedTextField(
        value = moodNote,
        onValueChange = {
            moodNote = it
            if (selectedEmoji.isNotBlank()) onMoodSelect(selectedEmoji, it)
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("한줄 감정 메모...", color = TextMuted) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple80, unfocusedBorderColor = DarkBorder,
            cursorColor = Purple80, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
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
            val oldLabel = h.oldType?.let { ShiftType.fromString(it)?.label } ?: "없음"
            val newLabel = h.newType?.let { ShiftType.fromString(it)?.label } ?: "없음"

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

// ── Shift Button ──

@Composable
private fun ShiftButton(type: ShiftType, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) type.bgColor else DarkSurface)
            .border(1.dp, if (isActive) type.color else DarkBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(type.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(type.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = if (isActive) type.color else TextPrimary)
            Text(type.timeRange, fontSize = 11.sp,
                color = if (isActive) type.color.copy(alpha = 0.7f) else TextMuted)
        }
        if (isActive) {
            Icon(Icons.Default.CheckCircle, null, tint = type.color, modifier = Modifier.size(20.dp))
        }
    }
}
