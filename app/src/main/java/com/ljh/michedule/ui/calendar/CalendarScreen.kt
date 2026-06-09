package com.ljh.michedule.ui.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        HeroCard(viewModel, uiState)

        ViewToggle(
            isWeekly = uiState.isWeeklyView,
            onToggle = { viewModel.toggleView() }
        )

        if (uiState.isWeeklyView) {
            WeeklyView(
                uiState = uiState,
                onDateClick = { viewModel.selectDate(it) },
                onNavigateToAddEvent = onNavigateToAddEvent
            )
        } else {
            MonthHeader(
                yearMonth = uiState.currentMonth,
                onPrev = { viewModel.navigateMonth(-1) },
                onNext = { viewModel.navigateMonth(1) }
            )
            MonthlyCalendar(
                uiState = uiState,
                onDateClick = { viewModel.selectDate(it) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatsCard(uiState)
        }
    }

    if (uiState.showDayDetail) {
        DayDetailSheet(
            date = uiState.selectedDate,
            shift = uiState.shifts[uiState.selectedDate.toString()],
            events = uiState.events[uiState.selectedDate.toString()] ?: emptyList(),
            friendShift = uiState.friendShifts[uiState.selectedDate.toString()],
            onDismiss = { viewModel.closeDayDetail() },
            onShiftSelect = { type -> viewModel.setShift(uiState.selectedDate, type) },
            onShiftClear = { viewModel.clearShift(uiState.selectedDate) },
            onMemoChange = { memo -> viewModel.setMemo(uiState.selectedDate, memo) },
            onAddEvent = { onNavigateToAddEvent(uiState.selectedDate.toString()) },
            onDeleteEvent = { viewModel.deleteEvent(it) }
        )
    }
}

@Composable
private fun HeroCard(viewModel: CalendarViewModel, uiState: CalendarUiState) {
    val today = LocalDate.now()
    val todayShift = uiState.shifts[today.toString()]?.let { ShiftType.fromString(it.type) }
    val dday = viewModel.getDdayInfo()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = DarkCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Today info (left)
            Text(
                text = todayShift?.emoji ?: "📅",
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = today.format(DateTimeFormatter.ofPattern("M/d (E)")),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Text(
                    text = if (todayShift != null) "${todayShift.label} ${todayShift.timeRange}" else "근무 미설정",
                    style = MaterialTheme.typography.labelLarge,
                    color = todayShift?.color ?: TextSecondary
                )
            }
            // D-day chips (right)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DdayMini(
                    value = dday.nextOffDays?.let { "D-$it" } ?: "-",
                    color = ShiftOff
                )
                DdayMini(
                    value = "${dday.consecutiveWorkDays}연속",
                    color = Purple80
                )
            }
        }
    }
}

@Composable
private fun DdayMini(value: String, color: Color) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun ViewToggle(isWeekly: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
            modifier = Modifier.border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
        ) {
            Row {
                ToggleButton(
                    text = "월간",
                    selected = !isWeekly,
                    onClick = { if (isWeekly) onToggle() }
                )
                ToggleButton(
                    text = "주간",
                    selected = isWeekly,
                    onClick = { if (!isWeekly) onToggle() }
                )
            }
        }
    }
}

@Composable
private fun ToggleButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(2.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Purple40 else Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else TextMuted
        )
    }
}

@Composable
private fun MonthHeader(
    yearMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "이전 달",
                tint = TextPrimary
            )
        }
        Text(
            text = "${yearMonth.year}년 ${yearMonth.monthValue}월",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "다음 달",
                tint = TextPrimary
            )
        }
    }
}

@Composable
fun MonthlyCalendar(uiState: CalendarUiState, onDateClick: (LocalDate) -> Unit) {
    val yearMonth = uiState.currentMonth
    val firstDay = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val startDow = (firstDay.dayOfWeek.value % 7) // Sun=0
    val today = LocalDate.now()

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Day-of-week header
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { dow ->
                Text(
                    text = dow,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (dow) {
                        "일" -> Color(0xFFF87171)
                        "토" -> Color(0xFF60A5FA)
                        else -> TextMuted
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Calendar grid
        val totalCells = startDow + daysInMonth
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val cellIdx = row * 7 + col
                    val dayNum = cellIdx - startDow + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = yearMonth.atDay(dayNum)
                        val dateStr = date.toString()
                        val shift = uiState.shifts[dateStr]?.let { ShiftType.fromString(it.type) }
                        val hasMemo = !uiState.shifts[dateStr]?.memo.isNullOrBlank()
                        val hasEvent = uiState.events[dateStr]?.isNotEmpty() == true
                        val friendShift = uiState.friendShifts[dateStr]?.let {
                            ShiftType.fromString(it.type)
                        }
                        val isToday = date == today
                        val isSelected = date == uiState.selectedDate

                        DayCell(
                            day = dayNum,
                            shift = shift,
                            hasMemo = hasMemo,
                            hasEvent = hasEvent,
                            friendShift = friendShift,
                            isToday = isToday,
                            isSelected = isSelected,
                            isSunday = col == 0,
                            isSaturday = col == 6,
                            onClick = { onDateClick(date) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    shift: ShiftType?,
    hasMemo: Boolean,
    hasEvent: Boolean,
    friendShift: ShiftType?,
    isToday: Boolean,
    isSelected: Boolean,
    isSunday: Boolean,
    isSaturday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isSelected -> Purple40.copy(alpha = 0.4f)
        shift != null -> shift.bgColor
        else -> Color.Transparent
    }
    val borderMod = if (isToday) {
        Modifier.border(1.5.dp, Purple80, RoundedCornerShape(10.dp))
    } else Modifier

    Column(
        modifier = modifier
            .padding(2.dp)
            .then(borderMod)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$day",
            fontSize = 13.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSunday -> Color(0xFFF87171)
                isSaturday -> Color(0xFF60A5FA)
                else -> TextPrimary
            }
        )
        if (shift != null) {
            Text(
                text = shift.shortLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = shift.color
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
        // Indicator dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(6.dp)
        ) {
            if (hasMemo) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Purple80)
                )
            }
            if (hasEvent) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(EventPersonal)
                )
            }
            if (friendShift != null) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(friendShift.color.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@Composable
fun WeeklyView(
    uiState: CalendarUiState,
    onDateClick: (LocalDate) -> Unit,
    onNavigateToAddEvent: (String) -> Unit
) {
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items((0..6).toList()) { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val dateStr = date.toString()
            val shift = uiState.shifts[dateStr]?.let { ShiftType.fromString(it.type) }
            val memo = uiState.shifts[dateStr]?.memo
            val events = uiState.events[dateStr] ?: emptyList()
            val friendShift = uiState.friendShifts[dateStr]
            val isToday = date == today

            WeekDayCard(
                date = date,
                shift = shift,
                memo = memo,
                events = events,
                friendShift = friendShift,
                isToday = isToday,
                onClick = { onDateClick(date) }
            )
        }
    }
}

@Composable
private fun WeekDayCard(
    date: LocalDate,
    shift: ShiftType?,
    memo: String?,
    events: List<com.ljh.michedule.data.db.EventEntity>,
    friendShift: com.ljh.michedule.data.db.FriendShiftEntity?,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isToday) Purple80 else DarkBorder

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isToday) Modifier.border(1.5.dp, Purple80, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("M/d (E)")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) Purple80 else TextPrimary
                    )
                    if (isToday) {
                        Text(
                            text = "오늘",
                            style = MaterialTheme.typography.bodySmall,
                            color = Purple80
                        )
                    }
                }
                if (shift != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = shift.bgColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = shift.emoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = shift.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = shift.color
                                )
                                Text(
                                    text = shift.timeRange,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = shift.color.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Memo
            if (!memo.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Notes,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Purple80
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = memo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Events
            events.forEach { event ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(event.color))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildString {
                            if (event.startTime != null) append("${event.startTime} ")
                            append(event.title)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Friend shift
            if (friendShift != null) {
                val fType = ShiftType.fromString(friendShift.type)
                if (fType != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = fType.color.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${friendShift.friendName}: ${fType.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = fType.color.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(uiState: CalendarUiState) {
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "이번 달 통계",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("주간", stats.dayCount, ShiftDay)
                StatItem("야간", stats.nightCount, ShiftNight)
                StatItem("야간(조)", stats.nightEarlyCount, ShiftNightEarly)
                StatItem("비번", stats.offCount, ShiftOff)
                StatItem("총 근무", stats.totalWork, Purple80)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}
