package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE date BETWEEN :start AND :end ORDER BY date, startTime")
    fun getEventsInRange(start: String, end: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE date = :date ORDER BY startTime")
    fun getEventsForDate(date: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY date, startTime")
    suspend fun getAllEvents(): List<EventEntity>

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)
}
