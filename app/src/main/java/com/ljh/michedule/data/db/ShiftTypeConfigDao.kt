package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftTypeConfigDao {
    @Query("SELECT * FROM shift_type_configs WHERE owner = 'mine' ORDER BY sortOrder")
    fun getAllFlow(): Flow<List<ShiftTypeConfig>>

    @Query("SELECT * FROM shift_type_configs WHERE owner = 'mine' ORDER BY sortOrder")
    suspend fun getAll(): List<ShiftTypeConfig>

    @Query("SELECT * FROM shift_type_configs WHERE owner = 'partner' ORDER BY sortOrder")
    fun getPartnerFlow(): Flow<List<ShiftTypeConfig>>

    @Query("SELECT * FROM shift_type_configs WHERE owner = 'partner' ORDER BY sortOrder")
    suspend fun getPartner(): List<ShiftTypeConfig>

    @Query("SELECT * FROM shift_type_configs WHERE id = :id AND owner = 'mine' LIMIT 1")
    suspend fun getById(id: String): ShiftTypeConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ShiftTypeConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(configs: List<ShiftTypeConfig>)

    @Query("DELETE FROM shift_type_configs WHERE id = :id AND isBuiltIn = 0 AND owner = 'mine'")
    suspend fun deleteCustom(id: String)

    @Query("DELETE FROM shift_type_configs WHERE id = :id AND owner = 'mine'")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM shift_type_configs WHERE owner = 'partner'")
    suspend fun deleteAllPartner()

    @Query("SELECT COUNT(*) FROM shift_type_configs WHERE owner = 'mine'")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM shift_type_configs WHERE owner = 'mine'")
    suspend fun maxSortOrder(): Int?
}
