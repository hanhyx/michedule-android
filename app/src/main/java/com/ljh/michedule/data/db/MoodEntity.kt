package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "moods")
data class MoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val emoji: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MoodDao {
    @Query("SELECT * FROM moods ORDER BY date")
    suspend fun getAllMoods(): List<MoodEntity>

    @Query("SELECT * FROM moods WHERE date = :date ORDER BY createdAt DESC LIMIT 1")
    fun getMoodForDate(date: String): Flow<MoodEntity?>

    @Query("SELECT * FROM moods WHERE date BETWEEN :start AND :end ORDER BY date")
    fun getMoodsInRange(start: String, end: String): Flow<List<MoodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mood: MoodEntity)

    @Query("DELETE FROM moods WHERE date = :date")
    suspend fun deleteForDate(date: String)
}
