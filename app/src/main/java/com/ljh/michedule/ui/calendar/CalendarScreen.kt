package com.ljh.michedule.ui.calendar

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.ljh.michedule.MicheduleApp
import com.ljh.michedule.data.ShiftTypeManager
import com.ljh.michedule.data.db.EventEntity
import com.ljh.michedule.data.db.DatePlanEntity
import com.ljh.michedule.data.db.FriendShiftEntity
import com.ljh.michedule.data.db.ShiftTypeConfig
import com.ljh.michedule.data.ocr.OcrCandidate
import com.ljh.michedule.data.ocr.ScheduleOcr
import com.ljh.michedule.data.ocr.ScheduleParser
import com.ljh.michedule.model.ShiftType
import com.ljh.michedule.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onNavigateToAddEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val stm = viewModel.shiftTypeManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showOcrSheet by remember { mutableStateOf(false) }
    var ocrProcessing by remember { mutableStateOf(false) }
    var ocrCandidates by remember { mutableStateOf<List<OcrCandidate>>(emptyList()) }
    var showCandidateDialog by remember { mutableStateOf(false) }

    val cameraImageUri = remember { mutableStateOf<android.net.Uri?>(null) }

    fun applyCandidateResult(candidate: OcrCandidate) {
        val count = viewModel.applyOcrResult(candidate.result)
        Toast.makeText(
            context,
            "${candidate.name}: ${candidate.result.yearMonth.monthValue}월 ${count}일분 일정 입력 완료",
            Toast.LENGTH_LONG
        ).show()
    }

    fun processOcrImage(uri: android.net.Uri) {
        ocrProcessing = true
        scope.launch {
            try {
                val app = context.applicationContext as MicheduleApp
                val userName = app.prefsManager.myName.first()
                val blocks = withContext(Dispatchers.IO) { ScheduleOcr.recognizeBlocks(context, uri) }
                val fullText = withContext(Dispatchers.IO) { ScheduleOcr.recognizeFullText(context, uri) }
                val allTypes = stm.allTypes.value

                val candidates = withContext(Dispatchers.IO) {
                    ScheduleParser.parseAllCandidates(fullText, blocks, allTypes, uiState.currentMonth)
                }

                when {
                    candidates.isEmpty() -> {
                        Toast.makeText(context, "근무 정보를 인식하지 못했습니다. 다시 시도해주세요", Toast.LENGTH_SHORT).show()
                    }
                    candidates.size == 1 -> {
                        applyCandidateResult(candidates.first())
                    }
                    else -> {
                        val autoMatch = if (userName.isNotBlank()) {
                            candidates.firstOrNull { it.name.contains(userName) }
                        } else null

                        if (autoMatch != null) {
                            applyCandidateResult(autoMatch)
                        } else {
                            ocrCandidates = candidates
                            showCandidateDialog = true
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "인식 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                ocrProcessing = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) processOcrImage(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri.value?.let { processOcrImage(it) }
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val ocrDir = File(context.cacheDir, "ocr").apply { mkdirs() }
            val file = File(ocrDir, "schedule_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraImageUri.value = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        TodayHeroBanner(uiState, stm)

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { showOcrSheet = true },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "근무표 사진 인식",
                    tint = Purple40,
                    modifier = Modifier.size(20.dp)
                )
            }
            ViewModeToggle(
                mode = uiState.viewMode,
                onModeChange = { viewModel.setViewMode(it) }
            )
            IconButton(
                onClick = { viewModel.toggleLock() },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = if (uiState.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (uiState.isLocked) "잠금 해제" else "잠금",
                    tint = if (uiState.isLocked) Color(0xFFF87171) else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        when (uiState.viewMode) {
            ViewMode.MONTHLY -> MonthlyScreen(viewModel, uiState, onNavigateToAddEvent, stm)
            ViewMode.WEEKLY -> WeeklyTimelineScreen(viewModel, uiState, stm)
            ViewMode.DAILY -> DailyTimelineScreen(viewModel, uiState, stm)
        }
    }

    if (uiState.showDayDetail) {
        if (uiState.viewingPartner) {
            PartnerDayDetailSheet(
                date = uiState.selectedDate,
                friendShift = uiState.friendShifts[uiState.selectedDate.toString()],
                datePlan = uiState.datePlans[uiState.selectedDate.toString()],
                shiftTypeManager = stm,
                onDismiss = { viewModel.closeDayDetail() }
            )
        } else {
            DayDetailSheet(
                date = uiState.selectedDate,
                shift = uiState.shifts[uiState.selectedDate.toString()],
                events = uiState.events[uiState.selectedDate.toString()] ?: emptyList(),
                todos = uiState.todos,
                mood = uiState.currentMood,
                shiftHistory = uiState.shiftHistory,
                shiftTypeManager = stm,
                onDismiss = { viewModel.closeDayDetail() },
                onShiftSelect = { type -> viewModel.setShift(uiState.selectedDate, type) },
                onShiftSelectById = { typeId -> viewModel.setShiftById(uiState.selectedDate, typeId) },
                onShiftClear = { viewModel.clearShift(uiState.selectedDate) },
                onAlbaToggle = { hasAlba -> viewModel.toggleAlba(uiState.selectedDate, hasAlba) },
                onExtraShiftToggle = { extraId -> viewModel.toggleExtraShift(uiState.selectedDate, extraId) },
                onShiftTimeEdit = { type, range -> viewModel.updateShiftTimeRange(type, range) },
                onMemoChange = { memo -> viewModel.setMemo(uiState.selectedDate, memo) },
                onAddEvent = { onNavigateToAddEvent(uiState.selectedDate.toString()) },
                onDeleteEvent = { viewModel.deleteEvent(it) },
                onAddTodo = { title, time, isHabit -> viewModel.addTodo(title, time, isHabit) },
                onToggleTodo = { id, done -> viewModel.toggleTodo(id, done) },
                onDeleteTodo = { viewModel.deleteTodo(it) },
                onMoodSelect = { emoji, note -> viewModel.setMood(emoji, note) },
                myName = uiState.myName.ifBlank { "나" },
                myCode = uiState.myCode,
                partnerName = uiState.partnerName,
                datePlan = uiState.currentDatePlan,
                onDatePlanSet = { memo -> viewModel.setDatePlan(uiState.selectedDate, memo) },
                onDatePlanDelete = { viewModel.deleteDatePlan(uiState.selectedDate) }
            )
        }
    }

    // OCR 선택 바텀시트
    if (showOcrSheet) {
        AlertDialog(
            onDismissRequest = { showOcrSheet = false },
            containerColor = DarkCard,
            title = {
                Text("📷 근무표 사진 인식", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "근무표 사진을 촬영하거나 이미지를 선택하면\n자동으로 근무 일정이 입력됩니다",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showOcrSheet = false
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple40),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("카메라로 촬영")
                    }
                    OutlinedButton(
                        onClick = {
                            showOcrSheet = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("갤러리에서 선택", color = TextSecondary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOcrSheet = false }) {
                    Text("취소", color = TextMuted)
                }
            }
        )
    }

    // OCR 처리 중 로딩 오버레이
    if (ocrProcessing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Purple40)
                Spacer(modifier = Modifier.height(12.dp))
                Text("근무표 인식 중...", color = Color.White, fontSize = 14.sp)
            }
        }
    }

    // 후보 선택 다이얼로그
    if (showCandidateDialog && ocrCandidates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                showCandidateDialog = false
                ocrCandidates = emptyList()
            },
            containerColor = DarkCard,
            title = {
                Text("👤 누구의 근무표인가요?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${ocrCandidates.size}명의 근무 일정이 인식되었습니다",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ocrCandidates.forEach { candidate ->
                        val shiftSummary = candidate.result.shifts.values
                            .groupBy { it }
                            .entries
                            .sortedByDescending { it.value.size }
                            .take(3)
                            .joinToString(" ") { (typeId, days) ->
                                val config = stm.getById(typeId)
                                "${config?.emoji ?: ""}${config?.shortLabel ?: typeId}${days.size}"
                            }

                        OutlinedButton(
                            onClick = {
                                showCandidateDialog = false
                                applyCandidateResult(candidate)
                                ocrCandidates = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Purple40.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    candidate.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "${candidate.result.shifts.size}일  |  $shiftSummary",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showCandidateDialog = false
                    ocrCandidates = emptyList()
                }) {
                    Text("취소", color = TextMuted)
                }
            }
        )
    }
}

// ── Today's Schedule Compact Banner ──

@Composable
private fun TodayHeroBanner(uiState: CalendarUiState, stm: ShiftTypeManager) {
    val today = LocalDate.now()
    val todayStr = today.toString()
    val myShift = uiState.shifts[todayStr]?.let { stm.getById(it.type) }
    val partnerShift = uiState.friendShifts[todayStr]?.let { stm.getByIdForPartner(it.type) }
    val mood = uiState.moods[todayStr]

    val myDisplayName = uiState.myName.ifBlank { "나" }
    val partnerDisplayName = uiState.partnerName.ifBlank { "상대" }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = today.format(DateTimeFormatter.ofPattern("M/d")),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            if (mood != null) {
                Text(mood.emoji, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(myDisplayName, fontSize = 11.sp, color = TextMuted)
            Text(":", fontSize = 11.sp, color = TextMuted)
            Text(
                text = myShift?.label ?: "─",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = myShift?.color ?: TextMuted,
                modifier = Modifier.padding(start = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(partnerDisplayName, fontSize = 11.sp, color = TextMuted)
            Text(":", fontSize = 11.sp, color = TextMuted)
            Text(
                text = partnerShift?.label ?: "─",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = partnerShift?.color ?: TextMuted,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

// ── View Mode Toggle (3 tabs) ──

@Composable
private fun ViewModeToggle(mode: ViewMode, onModeChange: (ViewMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DarkSurface,
            modifier = Modifier.border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
        ) {
            Row {
                listOf(
                    ViewMode.MONTHLY to "월간",
                    ViewMode.WEEKLY to "주간",
                    ViewMode.DAILY to "일간"
                ).forEach { (m, label) ->
                    Surface(
                        modifier = Modifier
                            .clickable { onModeChange(m) }
                            .padding(1.dp),
                        shape = RoundedCornerShape(7.dp),
                        color = if (mode == m) Purple40 else Color.Transparent
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (mode == m) Color.White else TextMuted
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════
//  MONTHLY VIEW — Calendar-first with couple cells
// ══════════════════════════════════════════════

@Composable
private fun MonthlyScreen(
    viewModel: CalendarViewModel,
    uiState: CalendarUiState,
    onNavigateToAddEvent: (String) -> Unit,
    stm: ShiftTypeManager
) {
    val hasPartner = uiState.partnerName.isNotBlank() && uiState.connectionMutual

    Column(modifier = Modifier.fillMaxSize()) {
        MonthHeader(
            yearMonth = uiState.currentMonth,
            onPrev = { viewModel.navigateMonth(-1) },
            onNext = { viewModel.navigateMonth(1) }
        )

        if (hasPartner) {
            PartnerViewToggle(
                viewingPartner = uiState.viewingPartner,
                myName = uiState.myName.ifBlank { "나" },
                myPhotoUri = uiState.myPhotoUri,
                partnerName = uiState.partnerName.ifBlank { "상대" },
                partnerPhotoUri = uiState.partnerPhotoUri,
                onToggle = { viewModel.toggleViewingPartner() }
            )
        }

        MonthlyCalendarGrid(
            uiState = uiState,
            stm = stm,
            onShiftCycle = { date ->
                if (!uiState.isLocked && !uiState.viewingPartner) {
                    val currentId = uiState.shifts[date.toString()]?.type?.takeIf { it.isNotBlank() }
                    val nextId = stm.cycleNext(currentId)
                    if (nextId != null) viewModel.setShiftById(date, nextId)
                    else viewModel.clearShift(date)
                }
            },
            onDateLongPress = { viewModel.selectDate(it) },
            modifier = Modifier.weight(1f)
        )
        CompactStatsBar(uiState, stm)
    }
}

@Composable
private fun PartnerViewToggle(
    viewingPartner: Boolean,
    myName: String,
    myPhotoUri: String,
    partnerName: String,
    partnerPhotoUri: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemberAvatar(
            name = myName,
            photoUri = myPhotoUri,
            isSelected = !viewingPartner,
            defaultEmoji = "👤",
            onClick = { if (viewingPartner) onToggle() }
        )

        Spacer(modifier = Modifier.width(20.dp))

        MemberAvatar(
            name = partnerName,
            photoUri = partnerPhotoUri,
            isSelected = viewingPartner,
            defaultEmoji = "💜",
            onClick = { if (!viewingPartner) onToggle() }
        )
    }
}

@Composable
private fun MemberAvatar(
    name: String,
    photoUri: String,
    isSelected: Boolean,
    defaultEmoji: String,
    onClick: () -> Unit
) {
    val photoSize = 32.dp
    val alpha = if (isSelected) 1f else 0.5f

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            if (photoUri.isNotBlank()) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = name,
                    modifier = Modifier
                        .size(photoSize)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.border(2.dp, Purple80, CircleShape)
                            else Modifier
                        ),
                    contentScale = ContentScale.Crop,
                    alpha = alpha
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(photoSize)
                        .clip(CircleShape)
                        .background(if (isSelected) DarkSurface else DarkCard)
                        .then(
                            if (isSelected) Modifier.border(2.dp, Purple80, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(defaultEmoji, fontSize = 16.sp, modifier = Modifier.graphicsLayer { this.alpha = alpha })
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(StatusOnline)
                        .border(1.5.dp, DarkBg, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            name,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MonthHeader(yearMonth: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전", tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
        Text(
            text = "${yearMonth.year}년 ${yearMonth.monthValue}월",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음", tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthlyCalendarGrid(
    uiState: CalendarUiState,
    stm: ShiftTypeManager,
    onShiftCycle: (LocalDate) -> Unit,
    onDateLongPress: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val yearMonth = uiState.currentMonth
    val firstDay = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val startDow = firstDay.dayOfWeek.value % 7
    val today = LocalDate.now()
    val totalCells = startDow + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { dow ->
                Text(
                    text = dow,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (dow) {
                        "일" -> Color(0xFFF87171)
                        "토" -> Color(0xFF60A5FA)
                        else -> TextMuted
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))

        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                for (col in 0..6) {
                    val dayNum = row * 7 + col - startDow + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = yearMonth.atDay(dayNum)
                        val dateStr = date.toString()
                        val shiftEntity = uiState.shifts[dateStr]
                        val friendShiftEntity = uiState.friendShifts[dateStr]
                        val mood = uiState.moods[dateStr]

                        val datePlan = uiState.datePlans[dateStr]

                        if (uiState.viewingPartner) {
                            val partnerTodos = friendShiftEntity?.getTodoList() ?: emptyList()
                            val partnerTodoCount = partnerTodos.size.takeIf { it > 0 } ?: (friendShiftEntity?.todoCount ?: 0)
                            SoloCell(
                                day = dayNum,
                                shiftConfig = friendShiftEntity?.type?.takeIf { it.isNotBlank() }?.let { stm.getByIdForPartner(it) },
                                hasAlba = friendShiftEntity?.hasAlba ?: false,
                                extraShifts = friendShiftEntity?.extraShifts ?: "",
                                shiftTypeManager = stm,
                                usePartnerTypes = true,
                                memo = friendShiftEntity?.memo,
                                mood = friendShiftEntity?.mood,
                                moodNote = friendShiftEntity?.moodNote,
                                todoCount = partnerTodoCount,
                                todoFirstTitle = partnerTodos.firstOrNull()?.first,
                                events = emptyList(),
                                datePlan = datePlan,
                                isToday = date == today,
                                isSunday = col == 0,
                                isSaturday = col == 6,
                                onClick = { onShiftCycle(date) },
                                onLongClick = { onDateLongPress(date) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        } else {
                            val myTodosForDate = uiState.todos.filter { it.date == dateStr }
                            SoloCell(
                                day = dayNum,
                                shiftConfig = shiftEntity?.type?.takeIf { it.isNotBlank() }?.let { stm.getById(it) },
                                hasAlba = shiftEntity?.hasAlba ?: false,
                                extraShifts = shiftEntity?.extraShifts ?: "",
                                shiftTypeManager = stm,
                                memo = shiftEntity?.memo,
                                mood = mood?.emoji,
                                moodNote = mood?.note,
                                todoCount = myTodosForDate.size,
                                todoFirstTitle = myTodosForDate.firstOrNull()?.title,
                                events = uiState.events[dateStr] ?: emptyList(),
                                datePlan = datePlan,
                                isToday = date == today,
                                isSunday = col == 0,
                                isSaturday = col == 6,
                                onClick = { onShiftCycle(date) },
                                onLongClick = { onDateLongPress(date) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════
//  SOLO CELL (unified full-width cell)
// ══════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoloCell(
    day: Int,
    shiftConfig: ShiftTypeConfig?,
    hasAlba: Boolean = false,
    albaConfig: ShiftTypeConfig? = null,
    extraShifts: String = "",
    shiftTypeManager: ShiftTypeManager? = null,
    usePartnerTypes: Boolean = false,
    memo: String?,
    mood: String?,
    moodNote: String? = null,
    todoCount: Int,
    todoFirstTitle: String? = null,
    events: List<EventEntity> = emptyList(),
    datePlan: DatePlanEntity? = null,
    isToday: Boolean,
    isSunday: Boolean,
    isSaturday: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderMod = if (isToday) {
        Modifier.border(2.dp, Purple80, RoundedCornerShape(6.dp))
    } else {
        Modifier.border(0.5.dp, DarkBorder.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
    }

    Column(
        modifier = modifier
            .padding(0.5.dp)
            .then(borderMod)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        // 날짜 + 감정 이모지 (가로)
        val dayColor = when {
            isToday -> Purple80
            isSunday -> Color(0xFFF87171)
            isSaturday -> Color(0xFF60A5FA)
            else -> TextPrimary
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$day",
                fontSize = if (isToday) 13.sp else 11.sp,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                color = dayColor,
                lineHeight = 14.sp
            )
            if (mood != null) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(mood, fontSize = 10.sp, lineHeight = 12.sp)
            }
        }

        // 만나요 배너 (날짜 바로 아래, 가로)
        if (datePlan != null) {
            Spacer(modifier = Modifier.height(1.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFEC4899).copy(alpha = 0.2f))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💕", fontSize = 8.sp, lineHeight = 10.sp)
                if (datePlan.memo.isNotBlank()) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(datePlan.memo, fontSize = 7.sp, color = Color(0xFFF9A8D4), maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 9.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 근무유형
        if (shiftConfig != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(shiftConfig.bgColor.copy(alpha = 0.5f))
                    .padding(horizontal = 3.dp, vertical = 2.dp)
            ) {
                Text(shiftConfig.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = shiftConfig.color, maxLines = 1, lineHeight = 14.sp)
            }
        }

        // 추가 근무
        val extraList = extraShifts.split(",").filter { it.isNotBlank() }
        val displayExtras = if (extraList.isEmpty() && hasAlba) listOf("alba") else extraList
        displayExtras.take(2).forEach { extraId ->
            val ec = (if (usePartnerTypes) shiftTypeManager?.getByIdForPartner(extraId) else shiftTypeManager?.getById(extraId)) ?: albaConfig
            val extraColor = ec?.color ?: ShiftAlba
            Spacer(modifier = Modifier.height(2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(extraColor.copy(alpha = 0.15f))
                    .padding(horizontal = 3.dp, vertical = 2.dp)
            ) {
                Text(ec?.label ?: extraId, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = extraColor, maxLines = 1, lineHeight = 12.sp)
            }
        }
        if (displayExtras.size > 2) {
            Text("+${displayExtras.size - 2}", fontSize = 8.sp, color = TextMuted, lineHeight = 10.sp)
        }

        // 감정 메모
        if (!moodNote.isNullOrBlank()) {
            Text(moodNote, fontSize = 8.sp, color = Color(0xFFD4B896), maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 10.sp, modifier = Modifier.padding(top = 1.dp))
        }

        // 메모
        if (!memo.isNullOrBlank()) {
            Text("💬$memo", fontSize = 8.sp, color = Color(0xFF93C5FD), maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 10.sp, modifier = Modifier.padding(top = 1.dp))
        }

        // 이벤트
        events.take(1).forEach { event ->
            Text(event.title, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = Color(event.color), maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 10.sp)
        }

        // 할일
        if (todoCount == 1 && !todoFirstTitle.isNullOrBlank()) {
            Text("📋$todoFirstTitle", fontSize = 8.sp, color = Color(0xFF6EE7B7), maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 10.sp)
        } else if (todoCount > 1) {
            Text("📋$todoCount", fontSize = 8.sp, color = Color(0xFF6EE7B7), lineHeight = 10.sp)
        }
    }
}

// ══════════════════════════════════════════════
//  WEEKLY TIMELINE VIEW
// ══════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeeklyTimelineScreen(viewModel: CalendarViewModel, uiState: CalendarUiState, stm: ShiftTypeManager) {
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    Column(modifier = Modifier.fillMaxSize()) {
        // Day headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Spacer(modifier = Modifier.width(36.dp))
            (0..6).forEach { i ->
                val d = weekStart.plusDays(i.toLong())
                val isToday = d == today
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = listOf("일", "월", "화", "수", "목", "금", "토")[i],
                        fontSize = 10.sp,
                        color = when {
                            isToday -> Purple80
                            i == 0 -> Color(0xFFF87171)
                            i == 6 -> Color(0xFF60A5FA)
                            else -> TextMuted
                        },
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "${d.dayOfMonth}",
                        fontSize = 13.sp,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isToday) Purple80 else TextPrimary
                    )
                }
            }
        }

        HorizontalDivider(color = DarkBorder)

        // Timeline grid (scrollable)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val hours = (0..23).toList()
            hours.forEach { hour ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    // Hour label
                    Text(
                        text = String.format("%02d", hour),
                        modifier = Modifier
                            .width(36.dp)
                            .padding(top = 2.dp),
                        fontSize = 10.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )

                    // 7 day columns
                    (0..6).forEach { dayIdx ->
                        val d = weekStart.plusDays(dayIdx.toLong())
                        val dateStr = d.toString()
                        val shiftConfig = uiState.shifts[dateStr]?.type?.takeIf { it.isNotBlank() }?.let { stm.getById(it) }
                        val friendConfig = uiState.friendShifts[dateStr]?.type?.let { stm.getByIdForPartner(it) }
                        val shift = uiState.shifts[dateStr]?.let { ShiftType.fromString(it.type) }
                        val friendShift = uiState.friendShifts[dateStr]?.let { ShiftType.fromString(it.type) }

                        val isMyWorkHour = isWithinTimeRange(hour, shiftConfig?.defaultTimeRange, shift)
                        val isPartnerWorkHour = isWithinTimeRange(hour, friendConfig?.defaultTimeRange, friendShift)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(0.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        isMyWorkHour && isPartnerWorkHour ->
                                            (shiftConfig?.color ?: Color.Gray).copy(alpha = 0.4f)
                                        isMyWorkHour ->
                                            (shiftConfig?.color ?: Color.Gray).copy(alpha = 0.25f)
                                        isPartnerWorkHour ->
                                            (friendConfig?.color ?: Color.Gray).copy(alpha = 0.1f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(0.5.dp, DarkBorder.copy(alpha = 0.3f))
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { viewModel.selectDate(d) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isMyWorkHour && (shiftConfig != null || shift != null) && isStartHour(hour, shiftConfig?.defaultTimeRange, shift)) {
                                Text(
                                    text = shiftConfig?.shortLabel ?: shift?.shortLabel ?: "",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = shiftConfig?.color ?: Color.Gray
                                )
                            }
                        }
                    }
                }
                if (hour < 23) {
                    HorizontalDivider(
                        color = DarkBorder.copy(alpha = 0.2f),
                        modifier = Modifier.padding(start = 36.dp)
                    )
                }
            }
        }
    }
}

private fun parseTimeRange(timeRange: String?): Pair<Int, Int>? {
    if (timeRange.isNullOrBlank()) return null
    val match = Regex("""(\d{1,2}):?\d*\s*-\s*(\d{1,2})""").find(timeRange) ?: return null
    val start = match.groupValues[1].toIntOrNull() ?: return null
    val end = match.groupValues[2].toIntOrNull() ?: return null
    return start to end
}

private fun isWithinTimeRange(hour: Int, timeRange: String?, fallbackShift: ShiftType?): Boolean {
    val parsed = parseTimeRange(timeRange)
    if (parsed != null) {
        val (start, end) = parsed
        return if (start <= end) hour in start until end
        else hour >= start || hour < end
    }
    if (fallbackShift == null) return false
    return when (fallbackShift) {
        ShiftType.DAY -> hour in 6..16
        ShiftType.NIGHT -> hour >= 18 || hour < 6
        ShiftType.NIGHT_EARLY -> hour >= 16 || hour < 4
        ShiftType.OFF -> false
        ShiftType.ALBA -> hour in 9..17
    }
}

private fun isStartHour(hour: Int, timeRange: String?, fallbackShift: ShiftType?): Boolean {
    val parsed = parseTimeRange(timeRange)
    if (parsed != null) return hour == parsed.first
    if (fallbackShift == null) return false
    return when (fallbackShift) {
        ShiftType.DAY -> hour == 6
        ShiftType.NIGHT -> hour == 18
        ShiftType.NIGHT_EARLY -> hour == 16
        ShiftType.OFF -> false
        ShiftType.ALBA -> hour == 9
    }
}

// ══════════════════════════════════════════════
//  DAILY TIMELINE VIEW
// ══════════════════════════════════════════════

@Composable
private fun DailyTimelineScreen(viewModel: CalendarViewModel, uiState: CalendarUiState, stm: ShiftTypeManager) {
    val date = uiState.selectedDate
    val dateStr = date.toString()
    val myShiftConfig = uiState.shifts[dateStr]?.type?.takeIf { it.isNotBlank() }?.let { stm.getById(it) }
    val partnerShiftConfig = uiState.friendShifts[dateStr]?.type?.let { stm.getByIdForPartner(it) }
    val myShift = uiState.shifts[dateStr]?.let { ShiftType.fromString(it.type) }
    val partnerShift = uiState.friendShifts[dateStr]?.let { ShiftType.fromString(it.type) }
    val memo = uiState.shifts[dateStr]?.memo
    val events = uiState.events[dateStr] ?: emptyList()
    val mood = uiState.moods[dateStr]

    Column(modifier = Modifier.fillMaxSize()) {
        // Date navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                viewModel.selectDate(date.minusDays(1))
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전", tint = TextPrimary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("M월 d일 (E)")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (date == LocalDate.now()) {
                    Text("오늘", style = MaterialTheme.typography.bodySmall, color = Purple80)
                }
            }
            IconButton(onClick = {
                viewModel.selectDate(date.plusDays(1))
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음", tint = TextPrimary)
            }
        }

        // Shift summary cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // My shift
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = myShiftConfig?.bgColor ?: DarkSurface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("나", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    if (myShiftConfig != null) {
                        Text(myShiftConfig.emoji, fontSize = 24.sp)
                        Text(myShiftConfig.label, fontWeight = FontWeight.Bold, color = myShiftConfig.color, fontSize = 13.sp)
                        Text(myShiftConfig.defaultTimeRange, fontSize = 10.sp, color = myShiftConfig.color.copy(alpha = 0.7f))
                    } else {
                        Text("미설정", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
            // Partner shift
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = partnerShiftConfig?.bgColor ?: DarkSurface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("상대", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    if (partnerShiftConfig != null) {
                        Text(partnerShiftConfig.emoji, fontSize = 24.sp)
                        Text(partnerShiftConfig.label, fontWeight = FontWeight.Bold, color = partnerShiftConfig.color, fontSize = 13.sp)
                        Text(partnerShiftConfig.defaultTimeRange, fontSize = 10.sp, color = partnerShiftConfig.color.copy(alpha = 0.7f))
                    } else {
                        Text("미설정", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }

        // Mood
        if (mood != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = DarkCard
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(mood.emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(mood.note.ifBlank { "오늘의 감정" }, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 24h timeline
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            (0..23).forEach { hour ->
                val isMyWork = isWithinTimeRange(hour, myShiftConfig?.defaultTimeRange, myShift)
                val isPartnerWork = isWithinTimeRange(hour, partnerShiftConfig?.defaultTimeRange, partnerShift)
                val hourEvents = events.filter {
                    it.startTime?.substringBefore(":")?.toIntOrNull() == hour
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Text(
                        text = String.format("%02d:00", hour),
                        modifier = Modifier.width(48.dp),
                        fontSize = 11.sp,
                        color = TextMuted,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // My column
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isMyWork) (myShiftConfig?.color ?: Color.Gray).copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .border(0.5.dp, DarkBorder.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isMyWork && (myShiftConfig != null || myShift != null) && isStartHour(hour, myShiftConfig?.defaultTimeRange, myShift)) {
                            Text(
                                text = " ${myShiftConfig?.emoji ?: ""} ${myShiftConfig?.label ?: ""}",
                                fontSize = 10.sp,
                                color = myShiftConfig?.color ?: Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        hourEvents.forEach { ev ->
                            Text(
                                text = " ${ev.title}",
                                fontSize = 9.sp,
                                color = Color(ev.color)
                            )
                        }
                    }

                    // Partner column
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isPartnerWork) (partnerShiftConfig?.color ?: Color.Gray).copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .border(0.5.dp, DarkBorder.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isPartnerWork && (partnerShiftConfig != null || partnerShift != null) && isStartHour(hour, partnerShiftConfig?.defaultTimeRange, partnerShift)) {
                            Text(
                                text = " ${partnerShiftConfig?.emoji ?: ""} ${partnerShiftConfig?.label ?: ""}",
                                fontSize = 10.sp,
                                color = (partnerShiftConfig?.color ?: Color.Gray).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ══════════════════════════════════════════════
//  Compact Stats Bar (bottom of monthly view)
// ══════════════════════════════════════════════

@Composable
private fun CompactStatsBar(uiState: CalendarUiState, stm: ShiftTypeManager) {
    val allTypes by stm.allTypes.collectAsState()
    val counts = remember(uiState.shifts, allTypes) {
        val map = mutableMapOf<String, Int>()
        uiState.shifts.values.forEach { entity ->
            if (entity.type.isNotBlank()) {
                map[entity.type] = (map[entity.type] ?: 0) + 1
            }
            entity.getExtraShiftList().forEach { extraId ->
                map[extraId] = (map[extraId] ?: 0) + 1
            }
            if (entity.hasAlba && entity.extraShifts.isBlank()) {
                map["alba"] = (map["alba"] ?: 0) + 1
            }
        }
        map
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            allTypes.forEach { config ->
                StatChip(config.shortLabel, counts[config.id] ?: 0, config.color)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: Color) {
    Text("$label$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
}
