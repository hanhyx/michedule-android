package com.ljh.michedule.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ljh.michedule.MicheduleApp
import com.ljh.michedule.data.db.EventEntity
import com.ljh.michedule.data.db.FriendShiftEntity
import com.ljh.michedule.data.db.ShiftEntity
import com.ljh.michedule.model.ShiftType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val shifts: Map<String, ShiftEntity> = emptyMap(),
    val events: Map<String, List<EventEntity>> = emptyMap(),
    val friendShifts: Map<String, FriendShiftEntity> = emptyMap(),
    val isWeeklyView: Boolean = false,
    val showDayDetail: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MicheduleApp
    private val repo = app.repository

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.map { it.currentMonth }
                .distinctUntilChanged()
                .flatMapLatest { month -> repo.getShiftsForMonth(month) }
                .collect { shifts ->
                    _uiState.update { state ->
                        state.copy(shifts = shifts.associateBy { it.date })
                    }
                }
        }

        viewModelScope.launch {
            _uiState.map { it.currentMonth }
                .distinctUntilChanged()
                .flatMapLatest { month -> repo.getEventsForMonth(month) }
                .collect { events ->
                    _uiState.update { state ->
                        state.copy(events = events.groupBy { it.date })
                    }
                }
        }

        viewModelScope.launch {
            _uiState.map { it.currentMonth }
                .distinctUntilChanged()
                .flatMapLatest { month -> repo.getFriendShiftsForMonth(month) }
                .collect { shifts ->
                    _uiState.update { state ->
                        state.copy(friendShifts = shifts.associateBy { it.date })
                    }
                }
        }
    }

    fun navigateMonth(delta: Int) {
        _uiState.update { it.copy(currentMonth = it.currentMonth.plusMonths(delta.toLong())) }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date, showDayDetail = true) }
    }

    fun closeDayDetail() {
        _uiState.update { it.copy(showDayDetail = false) }
    }

    fun toggleView() {
        _uiState.update { it.copy(isWeeklyView = !it.isWeeklyView) }
    }

    fun setShift(date: LocalDate, type: ShiftType) {
        viewModelScope.launch {
            repo.setShift(date, ShiftType.toDbString(type))
        }
    }

    fun clearShift(date: LocalDate) {
        viewModelScope.launch {
            repo.clearShift(date)
        }
    }

    fun setMemo(date: LocalDate, memo: String?) {
        viewModelScope.launch {
            repo.setMemo(date, memo)
        }
    }

    fun addEvent(event: EventEntity) {
        viewModelScope.launch {
            repo.addEvent(event)
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            repo.deleteEvent(id)
        }
    }

    fun autofillPattern(patternCodes: List<String>, startDate: LocalDate, endDate: LocalDate) {
        if (patternCodes.isEmpty()) return

        viewModelScope.launch {
            val shifts = mutableListOf<ShiftEntity>()
            var current = startDate
            var idx = 0
            while (!current.isAfter(endDate)) {
                shifts.add(
                    ShiftEntity(
                        date = current.toString(),
                        type = patternCodes[idx % patternCodes.size]
                    )
                )
                current = current.plusDays(1)
                idx++
            }
            repo.bulkSetShifts(shifts)
        }
    }

    fun clearMonth() {
        viewModelScope.launch {
            repo.clearMonth(_uiState.value.currentMonth)
        }
    }

    fun getTodayShift(): ShiftType? {
        val today = LocalDate.now().toString()
        return _uiState.value.shifts[today]?.let { ShiftType.fromString(it.type) }
    }

    fun getDdayInfo(): DdayInfo {
        val today = LocalDate.now()
        val shifts = _uiState.value.shifts

        var nextOff: Int? = null
        var nextWork: Int? = null
        var consecutiveWork = 0

        // Count consecutive work days ending today
        var check = today
        while (true) {
            val shift = shifts[check.toString()]?.let { ShiftType.fromString(it.type) }
            if (shift != null && shift != ShiftType.OFF) {
                consecutiveWork++
                check = check.minusDays(1)
            } else break
        }

        // Find next off & next work
        for (i in 1..60) {
            val d = today.plusDays(i.toLong())
            val shift = shifts[d.toString()]?.let { ShiftType.fromString(it.type) }
            if (shift != null) {
                if (shift == ShiftType.OFF && nextOff == null) nextOff = i
                if (shift != ShiftType.OFF && nextWork == null) nextWork = i
            }
            if (nextOff != null && nextWork != null) break
        }

        return DdayInfo(
            nextOffDays = nextOff,
            nextWorkDays = nextWork,
            consecutiveWorkDays = consecutiveWork
        )
    }

    fun getMonthStats(): MonthStats {
        val shifts = _uiState.value.shifts
        var day = 0; var night = 0; var nightEarly = 0; var off = 0
        shifts.values.forEach {
            when (ShiftType.fromString(it.type)) {
                ShiftType.DAY -> day++
                ShiftType.NIGHT -> night++
                ShiftType.NIGHT_EARLY -> nightEarly++
                ShiftType.OFF -> off++
                null -> {}
            }
        }
        return MonthStats(day, night, nightEarly, off)
    }
}

data class DdayInfo(
    val nextOffDays: Int? = null,
    val nextWorkDays: Int? = null,
    val consecutiveWorkDays: Int = 0
)

data class MonthStats(
    val dayCount: Int = 0,
    val nightCount: Int = 0,
    val nightEarlyCount: Int = 0,
    val offCount: Int = 0
) {
    val totalWork get() = dayCount + nightCount + nightEarlyCount
}
