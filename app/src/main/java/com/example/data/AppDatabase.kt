package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SettingsEntity::class, HistoryEntity::class, RuleGroupEntity::class, RuleEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureRuleGroupsTable(db)
                ensureRulesTable(db)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureRuleGroupsTable(db)
                ensureRulesTable(db)
                ensureSettingsFields(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureRuleGroupsTable(db)
                ensureRulesTable(db)
                ensureSettingsFields(db)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureRuleGroupsTable(db)
                ensureRulesTable(db)
                ensureSettingsFields(db)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureRuleGroupsTable(db)
                ensureRulesTable(db)
                ensureSettingsFields(db)
            }
        }

        private fun ensureRuleGroupsTable(db: SupportSQLiteDatabase) {
            if (!hasTable(db, "rule_groups")) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `rule_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `replacement` TEXT NOT NULL DEFAULT '')")
            } else {
                if (!hasColumn(db, "rule_groups", "replacement")) {
                    db.execSQL("ALTER TABLE `rule_groups` ADD COLUMN `replacement` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        private fun ensureRulesTable(db: SupportSQLiteDatabase) {
            if (!hasTable(db, "rules")) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupId` INTEGER NOT NULL, `target` TEXT NOT NULL, `replacement` TEXT NOT NULL, `matchWord` TEXT NOT NULL DEFAULT '', `isForwardMatch` INTEGER NOT NULL DEFAULT 1, `isEnabled` INTEGER NOT NULL DEFAULT 1)")
            } else {
                if (!hasColumn(db, "rules", "matchWord")) {
                    db.execSQL("ALTER TABLE `rules` ADD COLUMN `matchWord` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "rules", "isForwardMatch")) {
                    db.execSQL("ALTER TABLE `rules` ADD COLUMN `isForwardMatch` INTEGER NOT NULL DEFAULT 1")
                }
                if (!hasColumn(db, "rules", "isEnabled")) {
                    db.execSQL("ALTER TABLE `rules` ADD COLUMN `isEnabled` INTEGER NOT NULL DEFAULT 1")
                }
            }
        }

        private fun ensureSettingsFields(db: SupportSQLiteDatabase) {
            if (hasTable(db, "settings")) {
                if (!hasColumn(db, "settings", "themeMode")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `themeMode` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "settings", "useDynamicColor")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `useDynamicColor` INTEGER NOT NULL DEFAULT 1")
                }
            }
        }

        private fun hasTable(db: SupportSQLiteDatabase, tableName: String): Boolean {
            var cursor: android.database.Cursor? = null
            try {
                cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'", emptyArray<Any?>())
                return cursor != null && cursor.count > 0
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
            return false
        }

        private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            var cursor: android.database.Cursor? = null
            try {
                cursor = db.query("PRAGMA table_info(`$tableName`)", emptyArray<Any?>())
                if (cursor != null) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1) {
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIndex) == columnName) {
                                return true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
            return false
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tts_forwarder_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
