package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendShiftDao {
    @Query("SELECT * FROM friend_shifts WHERE date BETWEEN :start AND :end ORDER BY date")
    fun getShiftsInRange(start: String, end: String): Flow<List<FriendShiftEntity>>

    @Upsert
    suspend fun upsertAll(shifts: List<FriendShiftEntity>)

    @Query("DELETE FROM friend_shifts")
    suspend fun deleteAll()
}
