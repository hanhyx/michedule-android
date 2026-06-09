package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE date BETWEEN :start AND :end ORDER BY date")
    fun getShiftsInRange(start: String, end: String): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE date = :date LIMIT 1")
    suspend fun getShift(date: String): ShiftEntity?

    @Query("SELECT * FROM shifts ORDER BY date")
    suspend fun getAllShifts(): List<ShiftEntity>

    @Upsert
    suspend fun upsert(shift: ShiftEntity)

    @Upsert
    suspend fun upsertAll(shifts: List<ShiftEntity>)

    @Query("DELETE FROM shifts WHERE date = :date")
    suspend fun delete(date: String)

    @Query("DELETE FROM shifts WHERE date BETWEEN :start AND :end")
    suspend fun deleteRange(start: String, end: String)
}
