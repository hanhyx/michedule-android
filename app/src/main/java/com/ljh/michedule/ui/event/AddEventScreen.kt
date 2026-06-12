package com.ljh.michedule.ui.event

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ljh.michedule.data.db.EventEntity
import com.ljh.michedule.model.EventCategory
import com.ljh.michedule.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    initialDate: String,
    onSave: (EventEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(EventCategory.PERSONAL) }
    var selectedColor by remember { mutableStateOf(EventPersonal) }

    val colors = LocalAppColors.current

    val presetColors = listOf(
        EventPersonal,
        EventAppointment,
        EventHospital,
        ShiftOff,
        colors.accent,
        ShiftNightEarly
    )

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("일정 추가", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary
                ),
                actions = {
                    TextButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(
                                    EventEntity(
                                        date = date,
                                        title = title.trim(),
                                        startTime = startTime.ifBlank { null },
                                        endTime = endTime.ifBlank { null },
                                        color = selectedColor.toArgb(),
                                        category = selectedCategory.name.lowercase()
                                    )
                                )
                            }
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text(
                            "저장",
                            color = if (title.isNotBlank()) colors.accent else colors.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("제목", color = colors.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedLabelColor = colors.accent
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Date
            OutlinedTextField(
                value = try {
                    LocalDate.parse(date)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)"))
                } catch (_: Exception) { date },
                onValueChange = {},
                label = { Text("날짜", color = colors.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("시작", color = colors.textMuted) },
                    placeholder = { Text("14:00", color = colors.textMuted) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        cursorColor = colors.accent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("종료", color = colors.textMuted) },
                    placeholder = { Text("16:00", color = colors.textMuted) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        cursorColor = colors.accent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Category
            Text(
                text = "카테고리",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EventCategory.entries.forEach { cat ->
                    val isActive = selectedCategory == cat
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedCategory = cat
                                selectedColor = cat.defaultColor
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActive) cat.defaultColor.copy(alpha = 0.2f) else colors.surface,
                        border = if (isActive) {
                            BorderStroke(1.dp, cat.defaultColor)
                        } else null
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(cat.emoji, fontSize = MaterialTheme.typography.titleLarge.fontSize)
                            Text(
                                cat.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive) cat.defaultColor else colors.textMuted
                            )
                        }
                    }
                }
            }

            // Color
            Text(
                text = "색상",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presetColors.forEach { color ->
                    val isActive = selectedColor == color
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isActive) Modifier.border(
                                    3.dp, Color.White, CircleShape
                                ) else Modifier
                            )
                            .clickable { selectedColor = color }
                    )
                }
            }
        }
    }
}
