package com.ljh.michedule.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ljh.michedule.MicheduleApp
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.data.ShiftTypeManager
import com.ljh.michedule.data.db.ShiftTypeConfig
import com.ljh.michedule.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SettingsScreen(
    prefsManager: PrefsManager,
    onAutofill: (pattern: List<String>, startDate: String, endDate: String) -> Unit,
    onClearMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val myName by prefsManager.myName.collectAsState(initial = "")
    val myCode by prefsManager.myCode.collectAsState(initial = "")
    val partnerCode by prefsManager.partnerCode.collectAsState(initial = "")
    val partnerName by prefsManager.partnerName.collectAsState(initial = "")
    val syncPaused by prefsManager.syncPaused.collectAsState(initial = false)
    val connectionMutual by prefsManager.connectionMutual.collectAsState(initial = false)

    val alarmEnabled by prefsManager.alarmEnabled.collectAsState(initial = false)
    val alarmHoursBefore by prefsManager.alarmHoursBefore.collectAsState(initial = 2)
    val alarmDisabledTypes by prefsManager.alarmDisabledTypes.collectAsState(initial = emptySet())

    var editName by remember(myName) { mutableStateOf(myName) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        prefsManager.ensureMyCode()
    }

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
                        val newName = editName.trim()
                        if (myName != newName) {
                            (context.applicationContext as MicheduleApp).updateMyName(newName)
                        }
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
        SettingsCard(title = "출근 알람") {
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

                val stm = (context.applicationContext as MicheduleApp).shiftTypeManager
                val alarmTypes by stm.allTypes.collectAsState()
                alarmTypes.filter { it.id != "off" }.forEach { cfg ->
                    val code = cfg.id
                    val label = "${cfg.emoji} ${cfg.label}"
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

        // My Code (always visible)
        SettingsCard(title = "일정 공유") {
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
                        Text("내 코드", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Text(
                            myCode.ifBlank { "..." },
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Purple80,
                            letterSpacing = 2.sp,
                            modifier = Modifier.clickable {
                                if (myCode.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("my_code", myCode))
                                    Toast.makeText(context, "코드가 복사되었습니다", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    Text("탭하면 복사", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Michedule 스케줄 공유!\n\n내 코드: $myCode\n\n앱 설정 → 일정 공유 → 상대방 코드에 위 코드를 입력하면 연결돼!")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "내 코드 보내기"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = myCode.isNotBlank()
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("내 코드 보내기")
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DarkBorder)
            Spacer(modifier = Modifier.height(12.dp))

            if (partnerCode.isNotBlank()) {
                val isPending = !connectionMutual
                val statusColor = when {
                    syncPaused -> Color(0xFFFBBF24)
                    isPending -> Color(0xFFFF9800)
                    else -> StatusOnline
                }
                val statusIcon = when {
                    syncPaused -> Icons.Default.CloudOff
                    isPending -> Icons.Default.Schedule
                    else -> Icons.Default.Cloud
                }
                val statusText = when {
                    syncPaused -> "동기화 일시정지"
                    isPending -> "상대방 수락 대기 중..."
                    else -> "${partnerName.ifBlank { partnerCode }}와 연결됨"
                }
                val statusSubText = when {
                    syncPaused -> "일정 입력 후 다시 켜주세요"
                    isPending -> "상대방이 내 코드($myCode)를 입력하면 연결됩니다"
                    else -> "상대 코드: $partnerCode"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                statusText,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                statusSubText,
                                style = MaterialTheme.typography.bodySmall, color = TextMuted
                            )
                        }
                    }
                    if (!isPending) {
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
                }

                Spacer(modifier = Modifier.height(12.dp))

                var changeCode by remember { mutableStateOf("") }
                var showChangeInput by remember { mutableStateOf(false) }

                if (showChangeInput) {
                    Text("다른 상대 코드 입력", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = changeCode,
                            onValueChange = { changeCode = it.uppercase().take(6) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("6자리 코드", color = TextMuted) },
                            colors = settingsFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (changeCode.isNotBlank() && changeCode != myCode) {
                                    scope.launch {
                                        val app = context.applicationContext as MicheduleApp
                                        app.connectPartner(changeCode.trim())
                                        showChangeInput = false
                                        changeCode = ""
                                        Toast.makeText(context, "코드 등록 완료! 상대방도 내 코드를 입력하면 연결됩니다", Toast.LENGTH_LONG).show()
                                    }
                                } else if (changeCode == myCode) {
                                    Toast.makeText(context, "내 코드는 입력할 수 없습니다", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                            shape = RoundedCornerShape(12.dp),
                            enabled = changeCode.length == 6
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
                            Text("다른 상대 연결", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = {
                                val app = context.applicationContext as MicheduleApp
                                app.disconnectPartner()
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
                // not connected
                var joinCode by remember { mutableStateOf("") }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("서로의 코드를 입력해야 연결됩니다", color = TextMuted)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("상대방 코드 입력", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase().take(6) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("6자리 코드", color = TextMuted) },
                        colors = settingsFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (joinCode.isNotBlank() && joinCode != myCode) {
                                scope.launch {
                                    val app = context.applicationContext as MicheduleApp
                                    app.connectPartner(joinCode.trim())
                                    Toast.makeText(context, "코드 등록 완료! 상대방도 내 코드를 입력하면 연결됩니다", Toast.LENGTH_LONG).show()
                                }
                            } else if (joinCode == myCode) {
                                Toast.makeText(context, "내 코드는 입력할 수 없습니다", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                        shape = RoundedCornerShape(12.dp),
                        enabled = joinCode.length == 6
                    ) {
                        Text("연결")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "서로의 코드를 입력하면 일정이 공유됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        // Shift type management
        val app = context.applicationContext as MicheduleApp
        ShiftTypeManagementCard(shiftTypeManager = app.shiftTypeManager)

        // Pattern autofill
        PatternAutofillCard(onAutofill = onAutofill, shiftTypeManager = app.shiftTypeManager)

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

// ── Color palette for picker ──

private val COLOR_PALETTE = listOf(
    "#FFFBBF24", "#FFEF4444", "#FFF97316", "#FFF472B6", "#FFEC4899",
    "#FF818CF8", "#FF8B5CF6", "#FF6366F1", "#FF60A5FA", "#FF3B82F6",
    "#FF34D399", "#FF10B981", "#FF14B8A6", "#FF06B6D4", "#FF0EA5E9",
    "#FFA78BFA", "#FFD946EF", "#FFFACC15", "#FF84CC16", "#FF22C55E",
    "#FFE11D48", "#FF9CA3AF", "#FF64748B", "#FFF59E0B"
)

@Composable
private fun ShiftTypeManagementCard(shiftTypeManager: ShiftTypeManager) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val primaryTypes by shiftTypeManager.primaryTypes.collectAsState()
    val extraTypes by shiftTypeManager.extraTypes.collectAsState()
    var editingConfig by remember { mutableStateOf<ShiftTypeConfig?>(null) }
    var showAddPrimaryDialog by remember { mutableStateOf(false) }
    var showAddExtraDialog by remember { mutableStateOf(false) }

    var draggedPrimary by remember { mutableStateOf(primaryTypes) }
    LaunchedEffect(primaryTypes) { draggedPrimary = primaryTypes }

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeight = 52f

    SettingsCard(title = "근무유형 관리") {

        // ── 기본 근무 섹션 ──
        Text(
            "기본 근무 (탭 순환)",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Purple80
        )
        Text(
            "드래그하여 순서를 변경하세요. 탭 순서에 반영됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            draggedPrimary.forEachIndexed { index, config ->
                val offsetY = when {
                    dragIndex == index -> dragOffset
                    dragIndex >= 0 -> {
                        val targetIndex = (dragIndex + (dragOffset / itemHeight).toInt())
                            .coerceIn(0, draggedPrimary.lastIndex)
                        when {
                            index in (dragIndex + 1)..targetIndex -> -itemHeight
                            index in targetIndex until dragIndex -> itemHeight
                            else -> 0f
                        }
                    }
                    else -> 0f
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, offsetY.toInt()) }
                        .clip(RoundedCornerShape(10.dp))
                        .background(config.bgColor)
                        .clickable { editingConfig = config }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "드래그",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(20.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        dragIndex = index
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        if (dragIndex >= 0) {
                                            val targetIndex = (dragIndex + (dragOffset / itemHeight).toInt())
                                                .coerceIn(0, draggedPrimary.lastIndex)
                                            if (targetIndex != dragIndex) {
                                                val reordered = draggedPrimary.toMutableList()
                                                val item = reordered.removeAt(dragIndex)
                                                reordered.add(targetIndex, item)
                                                draggedPrimary = reordered
                                                scope.launch {
                                                    shiftTypeManager.reorder(reordered)
                                                    (context.applicationContext as MicheduleApp).triggerUpload()
                                                }
                                            }
                                        }
                                        dragIndex = -1
                                        dragOffset = 0f
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        dragOffset += dragAmount
                                    }
                                )
                            }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(config.emoji, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(config.label, fontWeight = FontWeight.SemiBold, color = config.color, fontSize = 14.sp)
                        Text(config.defaultTimeRange, fontSize = 10.sp, color = config.color.copy(alpha = 0.7f))
                    }
                    Icon(Icons.Default.Edit, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        if (draggedPrimary.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "탭 순서: ${draggedPrimary.joinToString(" → ") { it.shortLabel }} → 삭제",
                style = MaterialTheme.typography.bodySmall,
                color = Purple80
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showAddPrimaryDialog = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Purple80.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Purple80)
            Spacer(modifier = Modifier.width(4.dp))
            Text("기본 근무 추가", color = Purple80)
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = DarkBorder)
        Spacer(modifier = Modifier.height(12.dp))

        // ── 추가 근무 섹션 ──
        Text(
            "추가 근무 (토글)",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFFF97316)
        )
        Text(
            "하루에 여러 개를 동시에 켤 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(8.dp))

        extraTypes.forEach { config ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(config.bgColor)
                    .clickable { editingConfig = config }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(config.emoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(config.label, fontWeight = FontWeight.SemiBold, color = config.color, fontSize = 14.sp)
                    Text(config.defaultTimeRange, fontSize = 10.sp, color = config.color.copy(alpha = 0.7f))
                }
                Icon(Icons.Default.Edit, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showAddExtraDialog = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFF97316))
            Spacer(modifier = Modifier.width(4.dp))
            Text("추가 근무 추가", color = Color(0xFFF97316))
        }
    }

    if (editingConfig != null) {
        ShiftTypeEditDialog(
            config = editingConfig!!,
            onSave = { updated ->
                scope.launch {
                    shiftTypeManager.save(updated)
                    editingConfig = null
                    (context.applicationContext as MicheduleApp).triggerUpload()
                }
            },
            onDelete = {
                scope.launch {
                    shiftTypeManager.deleteType(editingConfig!!.id)
                    editingConfig = null
                    (context.applicationContext as MicheduleApp).triggerUpload()
                }
            },
            onDismiss = { editingConfig = null }
        )
    }

    if (showAddPrimaryDialog) {
        ShiftTypeEditDialog(
            config = null,
            defaultCategory = "primary",
            onSave = { newConfig ->
                scope.launch {
                    val order = shiftTypeManager.nextSortOrder()
                    val id = "custom_${UUID.randomUUID().toString().take(6)}"
                    shiftTypeManager.save(newConfig.copy(id = id, sortOrder = order, isBuiltIn = false, category = "primary"))
                    showAddPrimaryDialog = false
                    (context.applicationContext as MicheduleApp).triggerUpload()
                    Toast.makeText(context, "${newConfig.label} 추가됨", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = null,
            onDismiss = { showAddPrimaryDialog = false }
        )
    }

    if (showAddExtraDialog) {
        ShiftTypeEditDialog(
            config = null,
            defaultCategory = "extra",
            onSave = { newConfig ->
                scope.launch {
                    val order = shiftTypeManager.nextSortOrder()
                    val id = "custom_${UUID.randomUUID().toString().take(6)}"
                    shiftTypeManager.save(newConfig.copy(id = id, sortOrder = order, isBuiltIn = false, category = "extra", inCycle = false))
                    showAddExtraDialog = false
                    (context.applicationContext as MicheduleApp).triggerUpload()
                    Toast.makeText(context, "${newConfig.label} 추가됨", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = null,
            onDismiss = { showAddExtraDialog = false }
        )
    }
}

@Composable
private fun ShiftTypeEditDialog(
    config: ShiftTypeConfig?,
    defaultCategory: String = "primary",
    onSave: (ShiftTypeConfig) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val isNew = config == null
    val category = config?.category ?: defaultCategory
    var label by remember { mutableStateOf(config?.label ?: "") }
    var shortLabel by remember { mutableStateOf(config?.shortLabel ?: "") }
    var emoji by remember { mutableStateOf(config?.emoji ?: "") }
    var colorHex by remember { mutableStateOf(config?.colorHex ?: COLOR_PALETTE[0]) }
    var timeRange by remember { mutableStateOf(config?.defaultTimeRange ?: "09:00 - 18:00") }
    var inCycle by remember { mutableStateOf(config?.inCycle ?: (category == "primary")) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val bgColorHex = remember(colorHex) {
        val cleaned = colorHex.removePrefix("#")
        if (cleaned.length >= 6) {
            val rgb = cleaned.takeLast(6)
            "#33$rgb"
        } else "#33808080"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(
                if (isNew) "새 근무유형" else "${config!!.emoji} ${config.label} 편집",
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(4) },
                        label = { Text("이모지", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.width(80.dp),
                        colors = settingsFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = shortLabel,
                        onValueChange = { shortLabel = it.take(2) },
                        label = { Text("약자", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.width(70.dp),
                        colors = settingsFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(10) },
                    label = { Text("이름", color = TextMuted, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = timeRange,
                    onValueChange = { timeRange = it },
                    label = { Text("시간 범위", color = TextMuted, fontSize = 12.sp) },
                    placeholder = { Text("HH:MM - HH:MM", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Text("색상 선택", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                ColorPicker(
                    selectedHex = colorHex,
                    onSelect = { colorHex = it }
                )

                if (category == "primary") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("탭 순환에 포함", color = TextPrimary, fontSize = 14.sp)
                        Switch(
                            checked = inCycle,
                            onCheckedChange = { inCycle = it },
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
        },
        confirmButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("삭제", color = Color(0xFFF87171))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) {
                    Text("취소", color = TextMuted)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (label.isBlank() || shortLabel.isBlank()) return@Button
                        val result = ShiftTypeConfig(
                            id = config?.id ?: "",
                            label = label.trim(),
                            shortLabel = shortLabel.trim(),
                            emoji = emoji.trim().ifBlank { "📋" },
                            colorHex = colorHex,
                            bgColorHex = bgColorHex,
                            defaultTimeRange = timeRange.trim().ifBlank { "시간 미정" },
                            sortOrder = config?.sortOrder ?: 0,
                            inCycle = if (category == "extra") false else inCycle,
                            isBuiltIn = config?.isBuiltIn ?: false,
                            category = category
                        )
                        onSave(result)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                    enabled = label.isNotBlank() && shortLabel.isNotBlank()
                ) {
                    Text("저장")
                }
            }
        }
    )

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkCard,
            title = { Text("삭제 확인", color = TextPrimary) },
            text = { Text("'${config?.label}'을 삭제하시겠습니까?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("삭제", color = Color(0xFFF87171)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소", color = TextMuted) }
            }
        )
    }
}

@Composable
private fun ColorPicker(selectedHex: String, onSelect: (String) -> Unit) {
    val columns = 6
    val rows = (COLOR_PALETTE.size + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (col in 0 until columns) {
                    val idx = row * columns + col
                    if (idx < COLOR_PALETTE.size) {
                        val hex = COLOR_PALETTE[idx]
                        val color = ShiftTypeConfig.parseColor(hex)
                        val isSelected = hex == selectedHex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                    else Modifier.border(1.dp, DarkBorder, CircleShape)
                                )
                                .clickable { onSelect(hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternAutofillCard(
    onAutofill: (pattern: List<String>, startDate: String, endDate: String) -> Unit,
    shiftTypeManager: ShiftTypeManager
) {
    val context = LocalContext.current
    val allTypes by shiftTypeManager.allTypes.collectAsState()
    val shiftOptions = allTypes.map { it.id to "${it.label} ${it.emoji}" }

    var pattern by remember { mutableStateOf(listOf<String>()) }
    var startDay by remember { mutableStateOf("1") }
    var endDay by remember { mutableStateOf("") }

    val patternDisplay = pattern.joinToString(" → ") { code ->
        shiftTypeManager.getById(code)?.shortLabel ?: "?"
    }

    SettingsCard(title = "패턴 자동채우기") {
        Text(
            text = "짧은 패턴을 만들면 반복해서 채웁니다",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(12.dp))

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
                val config = shiftTypeManager.getById(code)
                val color = config?.color ?: TextMuted
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
