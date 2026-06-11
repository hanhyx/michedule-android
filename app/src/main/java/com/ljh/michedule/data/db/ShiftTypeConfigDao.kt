package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftTypeConfigDao {
    @Query("SELECT * FROM shift_type_configs ORDER BY sortOrder")
    fun getAllFlow(): Flow<List<ShiftTypeConfig>>

    @Query("SELECT * FROM shift_type_configs ORDER BY sortOrder")
    suspend fun getAll(): List<ShiftTypeConfig>

    @Query("SELECT * FROM shift_type_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ShiftTypeConfig?

    @Upsert
    suspend fun upsert(config: ShiftTypeConfig)

    @Upsert
    suspend fun upsertAll(configs: List<ShiftTypeConfig>)

    @Query("DELETE FROM shift_type_configs WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustom(id: String)

    @Query("DELETE FROM shift_type_configs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM shift_type_configs")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM shift_type_configs")
    suspend fun maxSortOrder(): Int?
}
