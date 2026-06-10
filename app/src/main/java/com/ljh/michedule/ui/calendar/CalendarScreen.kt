package com.ljh.michedule.ui.calendar

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ljh.michedule.data.db.EventEntity
import com.ljh.michedule.data.db.FriendShiftEntity
import com.ljh.michedule.model.ShiftType
import com.ljh.michedule.ui.theme.*
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        TodayHeroBanner(uiState)

        ViewModeToggle(
            mode = uiState.viewMode,
            onModeChange = { viewModel.setViewMode(it) }
        )

        when (uiState.viewMode) {
            ViewMode.MONTHLY -> MonthlyScreen(viewModel, uiState, onNavigateToAddEvent)
            ViewMode.WEEKLY -> WeeklyTimelineScreen(viewModel, uiState)
            ViewMode.DAILY -> DailyTimelineScreen(viewModel, uiState)
        }
    }

    if (uiState.showDayDetail) {
        DayDetailSheet(
            date = uiState.selectedDate,
            shift = uiState.shifts[uiState.selectedDate.toString()],
            events = uiState.events[uiState.selectedDate.toString()] ?: emptyList(),
            friendShift = uiState.friendShifts[uiState.selectedDate.toString()],
            todos = uiState.todos,
            mood = uiState.currentMood,
            shiftHistory = uiState.shiftHistory,
            onDismiss = { viewModel.closeDayDetail() },
            onShiftSelect = { type -> viewModel.setShift(uiState.selectedDate, type) },
            onShiftClear = { viewModel.clearShift(uiState.selectedDate) },
            onMemoChange = { memo -> viewModel.setMemo(uiState.selectedDate, memo) },
            onAddEvent = { onNavigateToAddEvent(uiState.selectedDate.toString()) },
            onDeleteEvent = { viewModel.deleteEvent(it) },
            onAddTodo = { title, time, isHabit -> viewModel.addTodo(title, time, isHabit) },
            onToggleTodo = { id, done -> viewModel.toggleTodo(id, done) },
            onDeleteTodo = { viewModel.deleteTodo(it) },
            onMoodSelect = { emoji, note -> viewModel.setMood(emoji, note) }
        )
    }
}

// ── Today's Schedule Compact Banner ──

@Composable
private fun TodayHeroBanner(uiState: CalendarUiState) {
    val today = LocalDate.now()
    val todayStr = today.toString()
    val myShift = uiState.shifts[todayStr]?.let { ShiftType.fromString(it.type) }
    val partnerShift = uiState.friendShifts[todayStr]?.let { ShiftType.fromString(it.type) }
    val mood = uiState.moods[todayStr]
    val todayMemo = uiState.shifts[todayStr]?.memo
    val todayEvents = uiState.events[todayStr]?.size ?: 0

    val accentColor = myShift?.color ?: Purple80

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE")),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (myShift != null) {
                        Text(myShift.emoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${myShift.label} ${myShift.timeRange}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = myShift.color
                        )
                    } else {
                        Text("오늘 근무 미설정", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!todayMemo.isNullOrBlank() || todayEvents > 0) {
                    Text(
                        text = buildString {
                            if (!todayMemo.isNullOrBlank()) append("📝 $todayMemo")
                            if (todayEvents > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("📅 일정 ${todayEvents}개")
                            }
                        },
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (partnerShift != null) {
                VerticalDivider(
                    modifier = Modifier.height(36.dp).padding(horizontal = 8.dp),
                    color = DarkBorder
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("상대", fontSize = 9.sp, color = TextMuted)
                    Text(partnerShift.emoji, fontSize = 16.sp)
                    Text(
                        partnerShift.shortLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = partnerShift.color
                    )
                }
            }

            if (mood != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(mood.emoji, fontSize = 18.sp)
            }
        }
    }
}

// ── View Mode Toggle (3 tabs) ──

@Composable
private fun ViewModeToggle(mode: ViewMode, onModeChange: (ViewMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
            modifier = Modifier.border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
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
                            .padding(2.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (mode == m) Purple40 else Color.Transparent
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
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
    onNavigateToAddEvent: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MonthHeader(
            yearMonth = uiState.currentMonth,
            onPrev = { viewModel.navigateMonth(-1) },
            onNext = { viewModel.navigateMonth(1) }
        )
        MonthlyCalendarGrid(
            uiState = uiState,
            onShiftCycle = { date ->
                val current = uiState.shifts[date.toString()]?.let { ShiftType.fromString(it.type) }
                val next = cycleShift(current)
                if (next != null) viewModel.setShift(date, next)
                else viewModel.clearShift(date)
            },
            onDateLongPress = { viewModel.selectDate(it) },
            modifier = Modifier.weight(1f)
        )
        CompactStatsBar(uiState)
    }
}

@Composable
private fun MonthHeader(yearMonth: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전", tint = TextPrimary)
        }
        Text(
            text = "${yearMonth.year}년 ${yearMonth.monthValue}월",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음", tint = TextPrimary)
        }
    }
}

private fun cycleShift(current: ShiftType?): ShiftType? = when (current) {
    null -> ShiftType.DAY
    ShiftType.DAY -> ShiftType.NIGHT
    ShiftType.NIGHT -> ShiftType.NIGHT_EARLY
    ShiftType.NIGHT_EARLY -> ShiftType.OFF
    ShiftType.OFF -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthlyCalendarGrid(
    uiState: CalendarUiState,
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
                        val shift = uiState.shifts[dateStr]?.let { ShiftType.fromString(it.type) }
                        val memo = uiState.shifts[dateStr]?.memo
                        val events = uiState.events[dateStr]
                        val friendShift = uiState.friendShifts[dateStr]?.let { ShiftType.fromString(it.type) }
                        val mood = uiState.moods[dateStr]

                        CoupleCell(
                            day = dayNum,
                            myShift = shift,
                            partnerShift = friendShift,
                            memo = memo,
                            eventCount = events?.size ?: 0,
                            mood = mood?.emoji,
                            isToday = date == today,
                            isSunday = col == 0,
                            isSaturday = col == 6,
                            onClick = { onShiftCycle(date) },
                            onLongClick = { onDateLongPress(date) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoupleCell(
    day: Int,
    myShift: ShiftType?,
    partnerShift: ShiftType?,
    memo: String?,
    eventCount: Int,
    mood: String?,
    isToday: Boolean,
    isSunday: Boolean,
    isSaturday: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        myShift != null -> myShift.bgColor
        else -> Color.Transparent
    }
    val borderMod = if (isToday) {
        Modifier.border(2.dp, Purple80, RoundedCornerShape(6.dp))
    } else Modifier

    val partnerBarColor = partnerShift?.color?.copy(alpha = 0.6f)

    Column(
        modifier = modifier
            .padding(0.5.dp)
            .then(borderMod)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .then(
                if (partnerBarColor != null) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = partnerBarColor,
                            topLeft = Offset(0f, size.height - 3.dp.toPx()),
                            size = Size(size.width, 3.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                    }
                } else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$day",
                fontSize = 11.sp,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                color = when {
                    isSunday -> Color(0xFFF87171)
                    isSaturday -> Color(0xFF60A5FA)
                    else -> TextPrimary
                }
            )
            if (mood != null) {
                Text(text = mood, fontSize = 9.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (myShift != null) {
            Text(myShift.emoji, fontSize = 16.sp)
            Text(
                text = myShift.shortLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = myShift.color,
                maxLines = 1
            )
        }

        if (partnerShift != null) {
            Text(
                text = "♥ ${partnerShift.shortLabel}",
                fontSize = 9.sp,
                color = partnerShift.color.copy(alpha = 0.8f),
                maxLines = 1
            )
        }

        val infoText = when {
            !memo.isNullOrBlank() -> memo
            eventCount > 0 -> "📅${eventCount}"
            else -> null
        }
        if (infoText != null) {
            Text(
                text = infoText,
                fontSize = 8.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.weight(0.5f))

        if (partnerShift != null) {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

// ══════════════════════════════════════════════
//  WEEKLY TIMELINE VIEW
// ══════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeeklyTimelineScreen(viewModel: CalendarViewModel, uiState: CalendarUiState) {
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
                        val shift = uiState.shifts[dateStr]?.let { ShiftType.fromString(it.type) }
                        val friendShift = uiState.friendShifts[dateStr]?.let { ShiftType.fromString(it.type) }

                        val isMyWorkHour = shift != null && isWithinShift(hour, shift)
                        val isPartnerWorkHour = friendShift != null && isWithinShift(hour, friendShift)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(0.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        isMyWorkHour && isPartnerWorkHour ->
                                            shift!!.color.copy(alpha = 0.4f)
                                        isMyWorkHour ->
                                            shift!!.color.copy(alpha = 0.25f)
                                        isPartnerWorkHour ->
                                            friendShift!!.color.copy(alpha = 0.1f)
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
                            if (isMyWorkHour && isShiftStartHour(hour, shift!!)) {
                                Text(
                                    text = shift.shortLabel,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = shift.color
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

private fun isWithinShift(hour: Int, shift: ShiftType): Boolean = when (shift) {
    ShiftType.DAY -> hour in 8..16
    ShiftType.NIGHT -> hour >= 18 || hour < 6
    ShiftType.NIGHT_EARLY -> hour >= 16 || hour < 4
    ShiftType.OFF -> false
}

private fun isShiftStartHour(hour: Int, shift: ShiftType): Boolean = when (shift) {
    ShiftType.DAY -> hour == 8
    ShiftType.NIGHT -> hour == 18
    ShiftType.NIGHT_EARLY -> hour == 16
    ShiftType.OFF -> false
}

// ══════════════════════════════════════════════
//  DAILY TIMELINE VIEW
// ══════════════════════════════════════════════

@Composable
private fun DailyTimelineScreen(viewModel: CalendarViewModel, uiState: CalendarUiState) {
    val date = uiState.selectedDate
    val dateStr = date.toString()
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
                color = myShift?.bgColor ?: DarkSurface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("나", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    if (myShift != null) {
                        Text(myShift.emoji, fontSize = 24.sp)
                        Text(myShift.label, fontWeight = FontWeight.Bold, color = myShift.color, fontSize = 13.sp)
                        Text(myShift.timeRange, fontSize = 10.sp, color = myShift.color.copy(alpha = 0.7f))
                    } else {
                        Text("미설정", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
            // Partner shift
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = partnerShift?.bgColor ?: DarkSurface
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("상대", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    if (partnerShift != null) {
                        Text(partnerShift.emoji, fontSize = 24.sp)
                        Text(partnerShift.label, fontWeight = FontWeight.Bold, color = partnerShift.color, fontSize = 13.sp)
                        Text(partnerShift.timeRange, fontSize = 10.sp, color = partnerShift.color.copy(alpha = 0.7f))
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
                val isMyWork = myShift != null && isWithinShift(hour, myShift)
                val isPartnerWork = partnerShift != null && isWithinShift(hour, partnerShift)
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
                                if (isMyWork) myShift!!.color.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .border(0.5.dp, DarkBorder.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isMyWork && isShiftStartHour(hour, myShift!!)) {
                            Text(
                                text = " ${myShift.emoji} ${myShift.label}",
                                fontSize = 10.sp,
                                color = myShift.color,
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
                                if (isPartnerWork) partnerShift!!.color.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .border(0.5.dp, DarkBorder.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isPartnerWork && isShiftStartHour(hour, partnerShift!!)) {
                            Text(
                                text = " ${partnerShift.emoji} ${partnerShift.label}",
                                fontSize = 10.sp,
                                color = partnerShift.color.copy(alpha = 0.7f)
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
private fun CompactStatsBar(uiState: CalendarUiState) {
    val stats = remember(uiState.shifts) {
        var day = 0; var night = 0; var nightEarly = 0; var off = 0
        uiState.shifts.values.forEach {
            when (ShiftType.fromString(it.type)) {
                ShiftType.DAY -> day++
                ShiftType.NIGHT -> night++
                ShiftType.NIGHT_EARLY -> nightEarly++
                ShiftType.OFF -> off++
                null -> {}
            }
        }
        MonthStats(day, night, nightEarly, off)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkCard,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatChip("☀️주", stats.dayCount, ShiftDay)
                StatChip("🌙야", stats.nightCount, ShiftNight)
                StatChip("🌇조", stats.nightEarlyCount, ShiftNightEarly)
                StatChip("😴비", stats.offCount, ShiftOff)
                Text(
                    text = "총 ${stats.totalWork + stats.offCount}일",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            ) {
                listOf(
                    stats.dayCount to ShiftDay,
                    stats.nightCount to ShiftNight,
                    stats.nightEarlyCount to ShiftNightEarly,
                    stats.offCount to ShiftOff
                ).forEach { (count, color) ->
                    if (count > 0) {
                        Box(modifier = Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label $count",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
