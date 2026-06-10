package com.ljh.michedule.data.repository

import com.ljh.michedule.data.db.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

class ScheduleRepository(private val db: AppDatabase) {

    private val shiftDao = db.shiftDao()
    private val eventDao = db.eventDao()
    private val friendShiftDao = db.friendShiftDao()
    private val todoDao = db.todoDao()
    private val moodDao = db.moodDao()
    private val historyDao = db.shiftHistoryDao()
    private val datePlanDao = db.datePlanDao()

    fun getShiftsForMonth(yearMonth: YearMonth): Flow<List<ShiftEntity>> {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        return shiftDao.getShiftsInRange(start, end)
    }

    fun getShiftsInRange(start: LocalDate, end: LocalDate): Flow<List<ShiftEntity>> {
        return shiftDao.getShiftsInRange(start.toString(), end.toString())
    }

    suspend fun getShift(date: LocalDate): ShiftEntity? {
        return shiftDao.getShift(date.toString())
    }

    suspend fun getAllShifts(): List<ShiftEntity> = shiftDao.getAllShifts()
    suspend fun getAllMoods(): List<MoodEntity> = moodDao.getAllMoods()
    suspend fun getAllTodos(): List<TodoEntity> = todoDao.getAllTodos()
    suspend fun getAllDatePlans(): List<DatePlanEntity> = datePlanDao.getAllPlans()

    fun getDatePlansForMonth(yearMonth: YearMonth): Flow<List<DatePlanEntity>> {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        return datePlanDao.getPlansInRange(start, end)
    }

    fun getDatePlanForDate(date: LocalDate): Flow<DatePlanEntity?> {
        return datePlanDao.getPlanForDate(date.toString())
    }

    suspend fun setDatePlan(date: LocalDate, memo: String, createdBy: String) {
        datePlanDao.upsert(DatePlanEntity(date = date.toString(), memo = memo, createdBy = createdBy))
    }

    suspend fun deleteDatePlan(date: LocalDate) {
        datePlanDao.deleteForDate(date.toString())
    }

    suspend fun syncDatePlans(plans: List<DatePlanEntity>) {
        plans.forEach { datePlanDao.upsert(it) }
    }

    suspend fun setShift(date: LocalDate, type: String) {
        val existing = shiftDao.getShift(date.toString())
        shiftDao.upsert(ShiftEntity(
            date = date.toString(),
            type = type,
            memo = existing?.memo,
            hasAlba = existing?.hasAlba ?: false
        ))
    }

    suspend fun setShiftWithMemo(date: LocalDate, type: String, memo: String?) {
        val existing = shiftDao.getShift(date.toString())
        shiftDao.upsert(
            ShiftEntity(
                date = date.toString(),
                type = type,
                memo = memo ?: existing?.memo
            )
        )
    }

    suspend fun setAlba(date: LocalDate, hasAlba: Boolean) {
        val existing = shiftDao.getShift(date.toString())
        if (existing != null) {
            shiftDao.upsert(existing.copy(hasAlba = hasAlba))
        } else {
            shiftDao.upsert(ShiftEntity(date = date.toString(), type = "", hasAlba = hasAlba))
        }
    }

    suspend fun setMemo(date: LocalDate, memo: String?) {
        val existing = shiftDao.getShift(date.toString())
        if (existing != null) {
            shiftDao.upsert(existing.copy(memo = memo))
        } else if (memo != null) {
            shiftDao.upsert(ShiftEntity(date = date.toString(), type = "", memo = memo))
        }
    }

    suspend fun clearShift(date: LocalDate) {
        val existing = shiftDao.getShift(date.toString())
        if (existing?.memo.isNullOrBlank()) {
            shiftDao.delete(date.toString())
        } else {
            shiftDao.upsert(existing!!.copy(type = ""))
        }
    }

    suspend fun bulkSetShifts(shifts: List<ShiftEntity>) {
        shiftDao.upsertAll(shifts)
    }

    suspend fun clearMonth(yearMonth: YearMonth) {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        shiftDao.deleteRange(start, end)
    }

    // Events
    fun getEventsForMonth(yearMonth: YearMonth): Flow<List<EventEntity>> {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        return eventDao.getEventsInRange(start, end)
    }

    fun getEventsForDate(date: LocalDate): Flow<List<EventEntity>> {
        return eventDao.getEventsForDate(date.toString())
    }

    suspend fun getAllEvents(): List<EventEntity> = eventDao.getAllEvents()

    suspend fun addEvent(event: EventEntity): Long {
        return eventDao.insert(event)
    }

    suspend fun updateEvent(event: EventEntity) {
        eventDao.update(event)
    }

    suspend fun deleteEvent(id: Long) {
        eventDao.deleteById(id)
    }

    // Friend shifts
    fun getFriendShiftsForMonth(yearMonth: YearMonth): Flow<List<FriendShiftEntity>> {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        return friendShiftDao.getShiftsInRange(start, end)
    }

    suspend fun syncFriendShifts(shifts: List<FriendShiftEntity>) {
        friendShiftDao.deleteAll()
        friendShiftDao.upsertAll(shifts)
    }

    // Todos
    fun getTodosForDate(date: LocalDate): Flow<List<TodoEntity>> {
        return todoDao.getTodosForDate(date.toString())
    }

    suspend fun addTodo(todo: TodoEntity): Long = todoDao.insert(todo)

    suspend fun toggleTodo(id: Long, done: Boolean) = todoDao.setDone(id, done)

    suspend fun deleteTodo(id: Long) = todoDao.deleteById(id)

    // Mood
    fun getMoodForDate(date: LocalDate): Flow<MoodEntity?> = moodDao.getMoodForDate(date.toString())

    fun getMoodsForMonth(yearMonth: YearMonth): Flow<List<MoodEntity>> {
        val start = yearMonth.atDay(1).toString()
        val end = yearMonth.atEndOfMonth().toString()
        return moodDao.getMoodsInRange(start, end)
    }

    suspend fun setMood(date: LocalDate, emoji: String, note: String) {
        moodDao.upsert(MoodEntity(date = date.toString(), emoji = emoji, note = note))
    }

    // Shift History
    fun getHistoryForDate(date: LocalDate): Flow<List<ShiftHistoryEntity>> =
        historyDao.getHistoryForDate(date.toString())

    suspend fun recordShiftChange(date: LocalDate, oldType: String?, newType: String?) {
        historyDao.insert(ShiftHistoryEntity(date = date.toString(), oldType = oldType, newType = newType))
    }
}
