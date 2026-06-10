package com.ljh.michedule.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ljh.michedule.MicheduleApp
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    prefsManager: PrefsManager,
    onAutofill: (pattern: List<String>, startDate: String, endDate: String) -> Unit,
    onClearMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val myName by prefsManager.myName.collectAsState(initial = "")
    val roomCode by prefsManager.roomCode.collectAsState(initial = "")
    val syncPaused by prefsManager.syncPaused.collectAsState(initial = false)

    val alarmEnabled by prefsManager.alarmEnabled.collectAsState(initial = false)
    val alarmHoursBefore by prefsManager.alarmHoursBefore.collectAsState(initial = 2)
    val alarmDisabledTypes by prefsManager.alarmDisabledTypes.collectAsState(initial = emptySet())

    var editName by remember(myName) { mutableStateOf(myName) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "설정",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // My Name
        SettingsCard(title = "내 이름") {
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                placeholder = { Text("이름 입력", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = settingsFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        prefsManager.setMyName(editName.trim())
                        (context.applicationContext as MicheduleApp).triggerUpload()
                        Toast.makeText(context, "저장됨", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("저장")
            }
        }

        // Alarm settings
        SettingsCard(title = "⏰ 출근 알람") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("출근 전 알람", fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(
                        text = if (alarmEnabled) "출근 ${alarmHoursBefore}시간 전 알림" else "꺼짐",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = alarmEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            prefsManager.setAlarmEnabled(enabled)
                            if (enabled) {
                                Toast.makeText(context, "알람이 설정되었습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Purple80,
                        checkedTrackColor = Purple40,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurface
                    )
                )
            }
            if (alarmEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("출근 몇 시간 전?", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 3, 4).forEach { hours ->
                        val isSelected = alarmHoursBefore == hours
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Purple40 else DarkSurface,
                            onClick = {
                                scope.launch { prefsManager.setAlarmHoursBefore(hours) }
                            }
                        ) {
                            Text(
                                text = "${hours}시간",
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Purple80 else TextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "예: 야간(18:00) → ${alarmHoursBefore}시간 전 = ${18 - alarmHoursBefore}:00 알림",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 12.dp))
                Text("근무 유형별 알림", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                val alarmTypes = listOf(
                    "day" to "☀️ 주간",
                    "night" to "🌙 야간",
                    "nightEarly" to "🌇 조기야간",
                    "alba" to "💼 알바"
                )
                alarmTypes.forEach { (code, label) ->
                    val isEnabled = code !in alarmDisabledTypes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium,
                            color = if (isEnabled) TextPrimary else TextMuted)
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { prefsManager.toggleAlarmForType(code, enabled) }
                            },
                            modifier = Modifier.height(28.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Purple80,
                                checkedTrackColor = Purple40,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurface
                            )
                        )
                    }
                }
            }
        }

        // Supabase Sync
        SettingsCard(title = "실시간 공유") {
            if (roomCode.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            if (syncPaused) Icons.Default.CloudOff else Icons.Default.Cloud,
                            contentDescription = null,
                            tint = if (syncPaused) Color(0xFFFBBF24) else StatusOnline,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                if (syncPaused) "동기화 일시정지" else "동기화 활성",
                                color = if (syncPaused) Color(0xFFFBBF24) else StatusOnline,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (syncPaused) "일정 입력 후 다시 켜주세요" else "변경사항이 자동으로 공유됩니다",
                                style = MaterialTheme.typography.bodySmall, color = TextMuted
                            )
                        }
                    }
                    Switch(
                        checked = !syncPaused,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                prefsManager.setSyncPaused(!enabled)
                                val app = context.applicationContext as MicheduleApp
                                if (enabled) app.startSync() else app.stopSync()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StatusOnline,
                            checkedTrackColor = StatusOnline.copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFFFBBF24),
                            uncheckedTrackColor = Color(0xFFFBBF24).copy(alpha = 0.2f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔑", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("내 공유 코드", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text(roomCode, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Purple80,
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("room_code", roomCode))
                                    Toast.makeText(context, "코드가 복사되었습니다", Toast.LENGTH_SHORT).show()
                                })
                        }
                        Text("탭하면 복사", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Michedule 스케줄 공유 초대!\n\n공유 코드: $roomCode\n\n앱 설정 → 실시간 공유 → 코드 입력에 위 코드를 넣으면 연결돼!")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "초대 코드 보내기"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("상대방에게 초대 코드 보내기")
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkBorder)
                Spacer(modifier = Modifier.height(8.dp))

                var changeCode by remember { mutableStateOf("") }
                var showChangeInput by remember { mutableStateOf(false) }

                if (showChangeInput) {
                    Text("상대방 코드로 연결", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = changeCode,
                            onValueChange = { changeCode = it.uppercase().take(8) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("코드 입력", color = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Purple80,
                                unfocusedBorderColor = DarkBorder,
                                cursorColor = Purple80,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (changeCode.isNotBlank()) {
                                    scope.launch {
                                        val app = context.applicationContext as MicheduleApp
                                        app.repository.clearAllFriendData()
                                        prefsManager.setRoomCode(changeCode.trim())
                                        app.startSync()
                                        showChangeInput = false
                                        changeCode = ""
                                        Toast.makeText(context, "연결되었습니다!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                            shape = RoundedCornerShape(12.dp),
                            enabled = changeCode.length >= 4
                        ) { Text("연결") }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showChangeInput = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("다른 코드로 연결", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = {
                                val app = context.applicationContext as MicheduleApp
                                app.disconnectRoom()
                                Toast.makeText(context, "연결이 해제되었습니다", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFF87171))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("해제", color = Color(0xFFF87171), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                var joinCode by remember { mutableStateOf("") }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("아직 연결되지 않았습니다", color = TextMuted)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("초대 코드가 있나요?", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase().take(8) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("코드 입력", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple80,
                            unfocusedBorderColor = DarkBorder,
                            cursorColor = Purple80,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (joinCode.isNotBlank()) {
                                scope.launch {
                                    val app = context.applicationContext as MicheduleApp
                                    app.repository.clearAllFriendData()
                                    prefsManager.setRoomCode(joinCode.trim())
                                    app.startSync()
                                    Toast.makeText(context, "연결되었습니다!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                        shape = RoundedCornerShape(12.dp),
                        enabled = joinCode.length >= 4
                    ) {
                        Text("연결")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = DarkBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Text("처음이라면?", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val app = context.applicationContext as MicheduleApp
                            app.repository.clearAllFriendData()
                            val code = generateRoomCode()
                            prefsManager.setRoomCode(code)
                            app.startSync()
                            Toast.makeText(context, "공유가 활성화되었습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Purple80)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("새 공유 방 만들기", color = TextPrimary)
                }
                Text(
                    text = "방을 만든 후 상대에게 코드를 보낼 수 있어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Pattern autofill
        PatternAutofillCard(onAutofill = onAutofill)

        // Data management
        SettingsCard(title = "데이터 관리") {
            OutlinedButton(
                onClick = { showClearConfirm = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOffline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("이번 달 초기화")
            }
        }

        // App info
        SettingsCard(title = "앱 정보") {
            val versionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            } catch (_: Exception) { "?" }
            Text(
                text = "Michedule v$versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = "교대근무 스케줄 관리",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("이번 달 초기화", color = TextPrimary) },
            text = { Text("이번 달의 모든 근무 데이터가 삭제됩니다. 계속하시겠습니까?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onClearMonth()
                    showClearConfirm = false
                    Toast.makeText(context, "초기화됨", Toast.LENGTH_SHORT).show()
                }) {
                    Text("삭제", color = StatusOffline)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("취소", color = TextPrimary)
                }
            },
            containerColor = DarkCard
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Purple80,
    unfocusedBorderColor = DarkBorder,
    cursorColor = Purple80,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = Purple80
)

@Composable
private fun PatternAutofillCard(
    onAutofill: (pattern: List<String>, startDate: String, endDate: String) -> Unit
) {
    val context = LocalContext.current
    val shiftOptions = listOf(
        "day" to "주간 ☀️",
        "night" to "야간 🌙",
        "nightEarly" to "조기 🌇",
        "off" to "비번 😴",
        "alba" to "알바 💼"
    )

    var pattern by remember { mutableStateOf(listOf<String>()) }
    var startDay by remember { mutableStateOf("1") }
    var endDay by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val patternDisplay = pattern.joinToString(" → ") { code ->
        when (code) {
            "day" -> "주"
            "night" -> "야"
            "nightEarly" -> "조"
            "off" -> "비"
            "alba" -> "알"
            else -> "?"
        }
    }

    SettingsCard(title = "패턴 자동채우기") {
        Text(
            text = "짧은 패턴을 만들면 반복해서 채웁니다",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Current pattern display
        if (pattern.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = patternDisplay,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${pattern.size}일 주기",
                        style = MaterialTheme.typography.bodySmall,
                        color = Purple80
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Add shift buttons
        Text(
            text = "근무 추가 (순서대로 탭)",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            shiftOptions.forEach { (code, label) ->
                val color = when (code) {
                    "day" -> ShiftDay
                    "night" -> ShiftNight
                    "nightEarly" -> ShiftNightEarly
                    "off" -> ShiftOff
                    else -> TextMuted
                }
                OutlinedButton(
                    onClick = { pattern = pattern + code },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        label.split(" ").first(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Undo / Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { if (pattern.isNotEmpty()) pattern = pattern.dropLast(1) },
                enabled = pattern.isNotEmpty()
            ) {
                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("하나 삭제", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick = { pattern = emptyList() },
                enabled = pattern.isNotEmpty()
            ) {
                Text("전체 삭제", style = MaterialTheme.typography.bodySmall, color = StatusOffline)
            }
        }

        HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))

        // Date range
        Text(
            text = "채울 범위 (이번 달 기준)",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = startDay,
                onValueChange = { startDay = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("시작일", color = TextMuted) },
                modifier = Modifier.weight(1f),
                colors = settingsFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Text("~", color = TextMuted)
            OutlinedTextField(
                value = endDay,
                onValueChange = { endDay = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("종료일", color = TextMuted) },
                placeholder = { Text("말일", color = TextMuted) },
                modifier = Modifier.weight(1f),
                colors = settingsFieldColors(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Apply button
        Button(
            onClick = {
                if (pattern.isEmpty()) {
                    Toast.makeText(context, "패턴을 먼저 만들어주세요", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val start = startDay.toIntOrNull() ?: 1
                val end = endDay.toIntOrNull()

                val now = java.time.YearMonth.now()
                val startDate = now.atDay(start.coerceIn(1, now.lengthOfMonth()))
                val endDate = if (end != null) {
                    now.atDay(end.coerceIn(1, now.lengthOfMonth()))
                } else {
                    now.atEndOfMonth()
                }

                onAutofill(pattern, startDate.toString(), endDate.toString())
                Toast.makeText(
                    context,
                    "${startDate.dayOfMonth}일~${endDate.dayOfMonth}일 채움 (${pattern.size}일 주기)",
                    Toast.LENGTH_LONG
                ).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Purple40),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            enabled = pattern.isNotEmpty()
        ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("패턴으로 채우기")
        }
    }
}

private fun generateRoomCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..6).map { chars.random() }.joinToString("")
}

