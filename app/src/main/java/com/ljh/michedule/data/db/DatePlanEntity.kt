package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "date_plans")
data class DatePlanEntity(
    @PrimaryKey val date: String,
    val memo: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface DatePlanDao {
    @Query("SELECT * FROM date_plans WHERE date BETWEEN :start AND :end ORDER BY date")
    fun getPlansInRange(start: String, end: String): Flow<List<DatePlanEntity>>

    @Query("SELECT * FROM date_plans WHERE date = :date LIMIT 1")
    fun getPlanForDate(date: String): Flow<DatePlanEntity?>

    @Query("SELECT * FROM date_plans ORDER BY date")
    suspend fun getAllPlans(): List<DatePlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: DatePlanEntity)

    @Query("DELETE FROM date_plans WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<DatePlanEntity>)

    @Query("DELETE FROM date_plans")
    suspend fun deleteAll()
}
