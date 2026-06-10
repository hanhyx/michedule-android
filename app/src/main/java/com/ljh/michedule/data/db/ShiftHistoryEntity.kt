package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "shift_history")
data class ShiftHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val oldType: String?,
    val newType: String?,
    val changedAt: Long = System.currentTimeMillis()
)

@Dao
interface ShiftHistoryDao {
    @Query("SELECT * FROM shift_history WHERE date = :date ORDER BY changedAt DESC")
    fun getHistoryForDate(date: String): Flow<List<ShiftHistoryEntity>>

    @Insert
    suspend fun insert(history: ShiftHistoryEntity)
}
