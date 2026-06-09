package com.ljh.michedule.data.repository

import com.ljh.michedule.data.db.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

class ScheduleRepository(private val db: AppDatabase) {

    private val shiftDao = db.shiftDao()
    private val eventDao = db.eventDao()
    private val friendShiftDao = db.friendShiftDao()

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

    suspend fun setShift(date: LocalDate, type: String) {
        shiftDao.upsert(ShiftEntity(date = date.toString(), type = type))
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
}
