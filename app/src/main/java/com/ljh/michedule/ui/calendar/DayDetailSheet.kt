package com.ljh.michedule.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ljh.michedule.data.db.EventEntity
import com.ljh.michedule.data.db.FriendShiftEntity
import com.ljh.michedule.data.db.ShiftEntity
import com.ljh.michedule.model.ShiftType
import com.ljh.michedule.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    date: LocalDate,
    shift: ShiftEntity?,
    events: List<EventEntity>,
    friendShift: FriendShiftEntity?,
    onDismiss: () -> Unit,
    onShiftSelect: (ShiftType) -> Unit,
    onShiftClear: () -> Unit,
    onMemoChange: (String?) -> Unit,
    onAddEvent: () -> Unit,
    onDeleteEvent: (Long) -> Unit
) {
    var memoText by remember(date, shift) {
        mutableStateOf(shift?.memo ?: "")
    }
    val currentShift = shift?.let { ShiftType.fromString(it.type) }

    ModalBottomSheet(
        onDismissRequest = {
            if (memoText != (shift?.memo ?: "")) {
                onMemoChange(memoText.ifBlank { null })
            }
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            // Date header
            Text(
                text = date.format(DateTimeFormatter.ofPattern("M월 d일 (E)")),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Shift selection
            Text(
                text = "근무 유형",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShiftType.entries.forEach { type ->
                    val isActive = currentShift == type
                    ShiftButton(
                        type = type,
                        isActive = isActive,
                        onClick = { onShiftSelect(type) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Clear button
            TextButton(
                onClick = {
                    onShiftClear()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextMuted
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("초기화", color = TextMuted, fontSize = 13.sp)
            }

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))

            // Memo
            Text(
                text = "메모",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = memoText,
                onValueChange = { memoText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("메모를 입력하세요", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple80,
                    unfocusedBorderColor = DarkBorder,
                    cursorColor = Purple80,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            // Save memo button
            if (memoText != (shift?.memo ?: "")) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onMemoChange(memoText.ifBlank { null })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("메모 저장")
                }
            }

            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))

            // Events
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "일정",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                IconButton(
                    onClick = onAddEvent,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "일정 추가",
                        tint = Purple80
                    )
                }
            }

            if (events.isEmpty()) {
                Text(
                    text = "등록된 일정이 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                events.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(event.color))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (event.startTime != null) {
                                Text(
                                    text = buildString {
                                        append(event.startTime)
                                        if (event.endTime != null) append(" ~ ${event.endTime}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteEvent(event.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "삭제",
                                modifier = Modifier.size(16.dp),
                                tint = TextMuted
                            )
                        }
                    }
                }
            }

            // Friend shift
            if (friendShift != null) {
                val fType = ShiftType.fromString(friendShift.type)
                if (fType != null) {
                    HorizontalDivider(
                        color = DarkBorder,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = fType.color
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${friendShift.friendName}: ${fType.emoji} ${fType.label}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = fType.color
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShiftButton(
    type: ShiftType,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isActive) type.bgColor else DarkSurface
    val borderColor = if (isActive) type.color else DarkBorder

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = type.emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = type.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isActive) type.color else TextMuted
        )
    }
}
