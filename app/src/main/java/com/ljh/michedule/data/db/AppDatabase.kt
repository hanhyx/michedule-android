package com.ljh.michedule.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ShiftEntity::class, EventEntity::class, FriendShiftEntity::class,
        TodoEntity::class, MoodEntity::class, ShiftHistoryEntity::class, DatePlanEntity::class,
        ShiftTypeConfig::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun eventDao(): EventDao
    abstract fun friendShiftDao(): FriendShiftDao
    abstract fun todoDao(): TodoDao
    abstract fun moodDao(): MoodDao
    abstract fun shiftHistoryDao(): ShiftHistoryDao
    abstract fun datePlanDao(): DatePlanDao
    abstract fun shiftTypeConfigDao(): ShiftTypeConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `todos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `time` TEXT,
                        `isDone` INTEGER NOT NULL DEFAULT 0,
                        `isHabit` INTEGER NOT NULL DEFAULT 0,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `moods` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `note` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `shift_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `oldType` TEXT,
                        `newType` TEXT,
                        `changedAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `hasAlba` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `friend_shifts` ADD COLUMN `hasAlba` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `friend_shifts` ADD COLUMN `memo` TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `friend_shifts` ADD COLUMN `mood` TEXT")
                db.execSQL("ALTER TABLE `friend_shifts` ADD COLUMN `todoCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `date_plans` (
                        `date` TEXT NOT NULL PRIMARY KEY,
                        `memo` TEXT NOT NULL DEFAULT '',
                        `createdBy` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `shift_type_configs` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `label` TEXT NOT NULL,
                        `shortLabel` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `bgColorHex` TEXT NOT NULL,
                        `defaultTimeRange` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `inCycle` INTEGER NOT NULL,
                        `isBuiltIn` INTEGER NOT NULL
                    )
                """.trimIndent())
                ShiftTypeConfig.DEFAULTS.forEach { c ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO shift_type_configs (id, label, shortLabel, emoji, colorHex, bgColorHex, defaultTimeRange, sortOrder, inCycle, isBuiltIn) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(c.id, c.label, c.shortLabel, c.emoji, c.colorHex, c.bgColorHex,
                            c.defaultTimeRange, c.sortOrder, if (c.inCycle) 1 else 0, if (c.isBuiltIn) 1 else 0)
                    )
                }
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `shift_type_configs` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'primary'")
                db.execSQL("UPDATE `shift_type_configs` SET `category` = 'extra' WHERE `id` = 'alba'")
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `extraShifts` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `shifts` SET `extraShifts` = 'alba' WHERE `hasAlba` = 1")
                db.execSQL("ALTER TABLE `friend_shifts` ADD COLUMN `extraShifts` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `friend_shifts` SET `extraShifts` = 'alba' WHERE `hasAlba` = 1")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `shift_type_configs` ADD COLUMN `owner` TEXT NOT NULL DEFAULT 'mine'")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `shift_type_configs_new` (
                        `id` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `shortLabel` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `bgColorHex` TEXT NOT NULL,
                        `defaultTimeRange` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `inCycle` INTEGER NOT NULL,
                        `isBuiltIn` INTEGER NOT NULL,
                        `category` TEXT NOT NULL DEFAULT 'primary',
                        `owner` TEXT NOT NULL DEFAULT 'mine',
                        PRIMARY KEY(`id`, `owner`)
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO `shift_type_configs_new` SELECT `id`, `label`, `shortLabel`, `emoji`, `colorHex`, `bgColorHex`, `defaultTimeRange`, `sortOrder`, `inCycle`, `isBuiltIn`, `category`, 'mine' FROM `shift_type_configs`")
                db.execSQL("DROP TABLE `shift_type_configs`")
                db.execSQL("ALTER TABLE `shift_type_configs_new` RENAME TO `shift_type_configs`")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "michedule.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        ShiftTypeConfig.DEFAULTS.forEach { c ->
                            db.execSQL(
                                "INSERT OR IGNORE INTO shift_type_configs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                arrayOf(c.id, c.label, c.shortLabel, c.emoji, c.colorHex, c.bgColorHex,
                                    c.defaultTimeRange, c.sortOrder, if (c.inCycle) 1 else 0, if (c.isBuiltIn) 1 else 0, c.category, c.owner)
                            )
                        }
                    }
                })
                .build().also { INSTANCE = it }
            }
        }
    }
}
