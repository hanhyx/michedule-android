package com.ljh.michedule.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ShiftEntity::class, EventEntity::class, FriendShiftEntity::class,
        TodoEntity::class, MoodEntity::class, ShiftHistoryEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun eventDao(): EventDao
    abstract fun friendShiftDao(): FriendShiftDao
    abstract fun todoDao(): TodoDao
    abstract fun moodDao(): MoodDao
    abstract fun shiftHistoryDao(): ShiftHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "michedule.db"
                ).fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
