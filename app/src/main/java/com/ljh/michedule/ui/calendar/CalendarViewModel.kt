package com.ljh.michedule.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ljh.michedule.MicheduleApp
import com.ljh.michedule.alarm.ShiftAlarmManager
import com.ljh.michedule.data.ShiftTypeManager
import com.ljh.michedule.data.db.*
import com.ljh.michedule.data.ocr.OcrScheduleResult
import com.ljh.michedule.model.ShiftType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class ViewMode { MONTHLY, WEEKLY, DAILY }

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val shifts: Map<String, ShiftEntity> = emptyMap(),
    val events: Map<String, List<EventEntity>> = emptyMap(),
    val friendShifts: Map<String, FriendShiftEntity> = emptyMap(),
    val moods: Map<String, MoodEntity> = emptyMap(),
    val todos: List<TodoEntity> = emptyList(),
    val shiftHistory: List<ShiftHistoryEntity> = emptyList(),
    val currentMood: MoodEntity? = null,
    val viewMode: ViewMode = ViewMode.MONTHLY,
    val showDayDetail: Boolean = false,
    val myName: String = "",
    val partnerName: String = "",
    val isLocked: Boolean = false,
    val viewingPartner: Boolean = false,
    val connectionMutual: Boolean = false,
    val datePlans: Map<String, DatePlanEntity> = emptyMap(),
    val currentDatePlan: DatePlanEntity? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MicheduleApp
    private val repo = app.repository
    val shiftTypeManager: ShiftTypeManager = app.shiftTypeManager

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.map { it.currentMonth }
                .distinctUntilChanged()
                .flatMapLatest { month -> repo.getShiftsForMonth(month) }
                .collect { shifts ->
                    _uiState.update { it.copy(shifts = shifts.associateBy { s -> s.date }) }
                }
        }

        viewModelScope.launch {
            _uiState.map { it.currentMonth }
                .distinctUntilChanged()
                .flatMapLatest { month -> repo.getEventsForMonth(month) }
                .collect { events ->
                    _uiState.update { it.copy(events = events.groupBy { e -> e.date }) }
                }
        }

        viewModelScope.launch {
            _uiState.map { it.currentMonth }
                .distinctUntilChanged()
                .flatMapLatest { month -> repo.getFriendShiftsForMonth(month) }
                .collect { shifts ->
                    _uiState.update { state ->
                        state.copy(friendShifts = shifts.associateBy { s -> s.date })
                    }
                }
        }

        viewModelScope.launch {
            _uiState.map { it.currentMonth }
                .distinctUntilChanged()
                .flatMapLatest { month -> repo.getMoodsForMonth(month) }
                .collect { moods ->
                    _uiState.update { it.copy(moods = moods.associateBy { m -> m.date }) }
                }
        }

        viewModelScope.launch {
            _uiState.map { it.selectedDate }
                .distinctUntilChanged()
                .flatMapLatest { date -> repo.getTodosForDate(date) }
                .collect { todos -> _uiState.update { it.copy(todos = todos) } }
        }

        viewModelScope.launch {
            _uiState.map { it.selectedDate }
                .distinctUntilChanged()
                .flatMapLatest { date -> repo.getMoodForDate(date) }
                .collect { mood -> _uiState.update { it.copy(currentMood = mood) } }
        }

        viewModelScope.launch {
            _uiState.map { it.selectedDate }
                .distinctUntilChanged()
                .flatMapLatest { date -> repo.getHistoryForDate(date) }
                .collect { history -> _uiState.update { it.copy(shiftHistory = history) } }
        }

        viewModelScope.launch {
            app.prefsManager.myName.collect { name ->
                _uiState.update { it.copy(myName = name) }
            }
        }

        viewModelScope.launch {
            app.prefsManager.calendarLocked.collect { locked ->
                _uiState.update { it.copy(isLocked = locked) }
            }
        }

        viewModelScope.launch {
            app.prefsManager.partnerName.collect { name ->
                _uiState.update { it.copy(partnerName = name) }
            }
        }

        viewModelScope.launch {
            app.prefsManager.partnerCode.collect { code ->
                if (code.isBlank()) {
                    _uiState.update { it.copy(viewingPartner = false, friendShifts = emptyMap(), connectionMutual = false) }
                }
            }
        }

        viewModelScope.launch {
            app.prefsManager.connectionMutual.collect { mutual ->
                _uiState.update { state ->
                    if (!mutual && state.viewingPartner) {
                        state.copy(connectionMutual = false, viewingPartner = false)
                    } else {
                        state.copy(connectionMutual = mutual)
                    }
                }
            }
        }

        viewModelScope.launch {
            _uiState.map { it.currentMonth }
                .distinctUntilChanged()
                .flatMapLatest { month -> repo.getDatePlansForMonth(month) }
                .collect { plans ->
                    _uiState.update { it.copy(datePlans = plans.associateBy { p -> p.date }) }
                }
        }

        viewModelScope.launch {
            _uiState.map { it.selectedDate }
                .distinctUntilChanged()
                .flatMapLatest { date -> repo.getDatePlanForDate(date) }
                .collect { plan ->
                    _uiState.update { it.copy(currentDatePlan = plan) }
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

    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun toggleLock() {
        viewModelScope.launch {
            val newLocked = !_uiState.value.isLocked
            app.prefsManager.setCalendarLocked(newLocked)
        }
    }

    fun toggleViewingPartner() {
        _uiState.update { it.copy(viewingPartner = !it.viewingPartner) }
    }

    fun setDatePlan(date: LocalDate, memo: String) {
        viewModelScope.launch {
            val myName = _uiState.value.myName.ifBlank { "나" }
            repo.setDatePlan(date, memo, myName)
            app.triggerUpload()
            app.sendDatePlanPush(date.toString(), memo)
        }
    }

    fun deleteDatePlan(date: LocalDate) {
        viewModelScope.launch {
            repo.deleteDatePlan(date)
            app.triggerUpload()
        }
    }

    fun setShift(date: LocalDate, type: ShiftType) {
        setShiftById(date, ShiftType.toDbString(type))
    }

    fun setShiftById(date: LocalDate, typeId: String) {
        viewModelScope.launch {
            val oldShift = repo.getShift(date)
            val oldType = oldShift?.type

            if (oldType != typeId) {
                repo.recordShiftChange(date, oldType, typeId)
            }
            repo.setShift(date, typeId)
            app.triggerUpload()

            try {
                val shiftType = ShiftType.fromString(typeId)
                val enabled = app.prefsManager.alarmEnabled.first()
                if (enabled && shiftType != null) {
                    val disabledTypes = app.prefsManager.alarmDisabledTypes.first()
                    if (typeId !in disabledTypes) {
                        val hours = app.prefsManager.alarmHoursBefore.first()
                        ShiftAlarmManager.scheduleAlarm(app, date, shiftType, hours)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun clearShift(date: LocalDate) {
        viewModelScope.launch {
            val old = repo.getShift(date)
            if (old?.type?.isNotBlank() == true) {
                repo.recordShiftChange(date, old.type, null)
            }
            repo.clearShift(date)
        }
    }

    fun updateShiftTimeRange(type: ShiftType, timeRange: String) {
        viewModelScope.launch {
            if (timeRange == type.defaultTimeRange) {
                app.prefsManager.clearCustomTimeRange(ShiftType.toDbString(type))
            } else {
                app.prefsManager.setCustomTimeRange(ShiftType.toDbString(type), timeRange)
            }
        }
    }

    fun toggleAlba(date: LocalDate, hasAlba: Boolean) {
        viewModelScope.launch {
            repo.setAlba(date, hasAlba)
            app.triggerUpload()
        }
    }

    fun setMemo(date: LocalDate, memo: String?) {
        viewModelScope.launch { repo.setMemo(date, memo) }
    }

    fun addEvent(event: EventEntity) {
        viewModelScope.launch { repo.addEvent(event) }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch { repo.deleteEvent(id) }
    }

    fun addTodo(title: String, time: String? = null, isHabit: Boolean = false) {
        viewModelScope.launch {
            repo.addTodo(TodoEntity(
                date = _uiState.value.selectedDate.toString(),
                title = title, time = time, isHabit = isHabit
            ))
        }
    }

    fun toggleTodo(id: Long, done: Boolean) {
        viewModelScope.launch { repo.toggleTodo(id, done) }
    }

    fun deleteTodo(id: Long) {
        viewModelScope.launch { repo.deleteTodo(id) }
    }

    fun setMood(emoji: String, note: String) {
        viewModelScope.launch {
            repo.setMood(_uiState.value.selectedDate, emoji, note)
        }
    }

    fun autofillPattern(patternCodes: List<String>, startDate: LocalDate, endDate: LocalDate) {
        if (patternCodes.isEmpty()) return
        viewModelScope.launch {
            val shifts = mutableListOf<ShiftEntity>()
            var current = startDate
            var idx = 0
            while (!current.isAfter(endDate)) {
                shifts.add(ShiftEntity(date = current.toString(), type = patternCodes[idx % patternCodes.size]))
                current = current.plusDays(1)
                idx++
            }
            repo.bulkSetShifts(shifts)
        }
    }

    fun clearMonth() {
        viewModelScope.launch { repo.clearMonth(_uiState.value.currentMonth) }
    }

    fun applyOcrResult(result: OcrScheduleResult): Int {
        val count = result.shifts.size
        viewModelScope.launch {
            result.shifts.forEach { (date, typeId) ->
                repo.setShift(date, typeId)
                repo.recordShiftChange(date, null, typeId)
            }
            _uiState.update { it.copy(currentMonth = result.yearMonth) }
            app.triggerUpload()
        }
        return count
    }

    fun getDdayInfo(): DdayInfo {
        val today = LocalDate.now()
        val shifts = _uiState.value.shifts
        var nextOff: Int? = null
        var nextWork: Int? = null
        var consecutiveWork = 0

        var check = today
        while (true) {
            val shift = shifts[check.toString()]?.let { ShiftType.fromString(it.type) }
            if (shift != null && shift != ShiftType.OFF) { consecutiveWork++; check = check.minusDays(1) }
            else break
        }
        for (i in 1..60) {
            val d = today.plusDays(i.toLong())
            val shift = shifts[d.toString()]?.let { ShiftType.fromString(it.type) }
            if (shift != null) {
                if (shift == ShiftType.OFF && nextOff == null) nextOff = i
                if (shift != ShiftType.OFF && nextWork == null) nextWork = i
            }
            if (nextOff != null && nextWork != null) break
        }
        return DdayInfo(nextOff, nextWork, consecutiveWork)
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
    val offCount: Int = 0,
    val albaCount: Int = 0
) {
    val totalWork get() = dayCount + nightCount + nightEarlyCount + albaCount
}
