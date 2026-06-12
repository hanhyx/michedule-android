package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "date_plans")
data class DatePlanEntity(
    @PrimaryKey val date: String,
    val memo: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val response: String = ""
)

@Dao
interface DatePlanDao {
    @Query("SELECT * FROM date_plans WHERE date BETWEEN :start AND :end ORDER BY date")
    fun getPlansInRange(start: String, end: String): Flow<List<DatePlanEntity>>

    @Query("SELECT * FROM date_plans WHERE date = :date LIMIT 1")
    fun getPlanForDate(date: String): Flow<DatePlanEntity?>

    @Query("SELECT * FROM date_plans WHERE date = :date LIMIT 1")
    suspend fun getPlanOnce(date: String): DatePlanEntity?

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

    @Query("UPDATE date_plans SET createdBy = :partnerCode WHERE createdBy = :partnerName AND :partnerName != '' AND :partnerCode != ''")
    suspend fun migratePartnerNamesToCode(partnerCode: String, partnerName: String)

    @Query("UPDATE date_plans SET createdBy = :myCode WHERE createdBy != :partnerCode AND createdBy != :myCode AND createdBy != ''")
    suspend fun migrateMyNamesToCode(myCode: String, partnerCode: String)
}
