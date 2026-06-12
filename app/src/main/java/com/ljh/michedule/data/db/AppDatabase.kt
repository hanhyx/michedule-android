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
        ShiftTypeConfig::class, ChatMessageEntity::class,
        TimelineEntity::class, TimelinePlaceEntity::class, TimelinePhotoEntity::class, TimelineStickerEntity::class],
    version = 16,
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
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun timelineDao(): TimelineDao

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

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `friend_shifts` ADD COLUMN `todoTexts` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `friend_shifts` ADD COLUMN `moodNote` TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE date_plans ADD COLUMN response TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `timelines` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `roomCode` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `title` TEXT NOT NULL DEFAULT '',
                        `createdBy` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `timeline_places` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `timelineId` TEXT NOT NULL,
                        `placeName` TEXT NOT NULL,
                        `time` TEXT NOT NULL DEFAULT '',
                        `memo` TEXT NOT NULL DEFAULT '',
                        `sortOrder` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `timeline_photos` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `placeId` TEXT NOT NULL,
                        `timelineId` TEXT NOT NULL,
                        `imageUrl` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `timeline_stickers` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `timelineId` TEXT NOT NULL,
                        `placeId` TEXT,
                        `photoId` TEXT,
                        `stickerType` TEXT NOT NULL DEFAULT 'emoji',
                        `stickerValue` TEXT NOT NULL,
                        `posX` REAL NOT NULL,
                        `posY` REAL NOT NULL,
                        `scale` REAL NOT NULL DEFAULT 1.0,
                        `rotation` REAL NOT NULL DEFAULT 0.0,
                        `placedBy` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shift_type_configs ADD COLUMN fontColorHex TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `roomCode` TEXT NOT NULL,
                        `senderCode` TEXT NOT NULL,
                        `messageType` TEXT NOT NULL DEFAULT 'text',
                        `content` TEXT NOT NULL DEFAULT '',
                        `imageUrl` TEXT,
                        `reactions` TEXT NOT NULL DEFAULT '{}',
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "michedule.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        ShiftTypeConfig.DEFAULTS.forEach { c ->
                            db.execSQL(
                                "INSERT OR IGNORE INTO shift_type_configs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                arrayOf(c.id, c.label, c.shortLabel, c.emoji, c.colorHex, c.bgColorHex,
                                    c.defaultTimeRange, c.sortOrder, if (c.inCycle) 1 else 0, if (c.isBuiltIn) 1 else 0, c.category, c.owner, c.fontColorHex)
                            )
                        }
                    }
                })
                .build().also { INSTANCE = it }
            }
        }
    }
}
