package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SettingsEntity::class, HistoryEntity::class, RuleGroupEntity::class, RuleEntity::class, PolyphoneCacheRow::class, PresetPolyphoneEntity::class], version = 11, exportSchema = false)
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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureRuleGroupsTable(db)
                ensureRulesTable(db)
                ensureSettingsFields(db)
                ensureWebdavFields(db)
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureRuleGroupsTable(db)
                ensureRulesTable(db)
                ensureSettingsFields(db)
                ensureWebdavFields(db)
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `polyphone_cache` (`windowText` TEXT NOT NULL, `targetIndex` INTEGER NOT NULL, `pinyin` TEXT NOT NULL, `hitCount` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`windowText`, `targetIndex`))")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `preset_polyphones` (`char` TEXT NOT NULL, `readings` TEXT NOT NULL, PRIMARY KEY(`char`))")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureModelManagementFields(db)
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

        private fun ensureWebdavFields(db: SupportSQLiteDatabase) {
            if (hasTable(db, "settings")) {
                if (!hasColumn(db, "settings", "webdavUrl")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `webdavUrl` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "settings", "webdavUsername")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `webdavUsername` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "settings", "webdavPassword")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `webdavPassword` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "settings", "webdavPath")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `webdavPath` TEXT NOT NULL DEFAULT 'tts_rules_backup.json'")
                }
                if (!hasColumn(db, "settings", "webdavDir")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `webdavDir` TEXT NOT NULL DEFAULT 'TTS'")
                }
            }
        }

        private fun ensureModelManagementFields(db: SupportSQLiteDatabase) {
            if (hasTable(db, "settings")) {
                if (!hasColumn(db, "settings", "customGeminiApiKey")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `customGeminiApiKey` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "settings", "customGeminiEndpoint")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `customGeminiEndpoint` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "settings", "customGeminiModel")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `customGeminiModel` TEXT NOT NULL DEFAULT 'gemini-1.5-flash'")
                }
                if (!hasColumn(db, "settings", "useLocalModel")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `useLocalModel` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "settings", "localModelEndpoint")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `localModelEndpoint` TEXT NOT NULL DEFAULT 'http://127.0.0.1:11434/v1/chat/completions'")
                }
                if (!hasColumn(db, "settings", "localModelApiKey")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `localModelApiKey` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "settings", "localModelName")) {
                    db.execSQL("ALTER TABLE `settings` ADD COLUMN `localModelName` TEXT NOT NULL DEFAULT 'llama3'")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
