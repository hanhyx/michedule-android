package com.ljh.michedule.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val title: String,
    val time: String? = null,
    val isDone: Boolean = false,
    val isHabit: Boolean = false,
    val sortOrder: Int = 0
)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY date")
    suspend fun getAllTodos(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE date = :date ORDER BY sortOrder, time, id")
    fun getTodosForDate(date: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE date BETWEEN :start AND :end ORDER BY sortOrder, time, id")
    fun getTodosInRange(start: String, end: String): Flow<List<TodoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE todos SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)
}
