package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettingsFlow(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: SettingsEntity)

    @Query("SELECT * FROM (SELECT * FROM history ORDER BY timestamp DESC LIMIT 100) ORDER BY timestamp ASC")
    fun getHistoryFlow(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryRaw(history: HistoryEntity)

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT 500)")
    suspend fun trimHistory()

    @androidx.room.Transaction
    suspend fun insertHistory(history: HistoryEntity) {
        insertHistoryRaw(history)
        trimHistory()
    }

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("SELECT * FROM rule_groups ORDER BY id DESC")
    fun getAllRuleGroupsFlow(): Flow<List<RuleGroupEntity>>

    @Query("SELECT * FROM rule_groups ORDER BY id DESC")
    suspend fun getAllRuleGroups(): List<RuleGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRuleGroup(group: RuleGroupEntity): Long

    @Query("DELETE FROM rule_groups WHERE id = :groupId")
    suspend fun deleteRuleGroupById(groupId: Long)

    @Query("SELECT * FROM rules ORDER BY id DESC")
    fun getAllRulesFlow(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY id DESC")
    suspend fun getAllRules(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE groupId = :groupId ORDER BY id DESC")
    fun getRulesForGroupFlow(groupId: Long): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE groupId = :groupId ORDER BY id DESC")
    suspend fun getRulesForGroup(groupId: Long): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Query("DELETE FROM rules WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: Long)

    @Query("DELETE FROM rules WHERE groupId = :groupId")
    suspend fun deleteRulesByGroupId(groupId: Long)

    @Query("DELETE FROM rule_groups")
    suspend fun clearAllRuleGroups()

    @Query("DELETE FROM rules")
    suspend fun clearAllRules()

    @Query("SELECT * FROM polyphone_cache WHERE windowText = :window AND targetIndex = :idx")
    suspend fun findPolyphoneCache(window: String, idx: Int): PolyphoneCacheRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPolyphoneCache(row: PolyphoneCacheRow)

    @Query("SELECT * FROM polyphone_cache")
    suspend fun getAllPolyphoneCache(): List<PolyphoneCacheRow>

    @Query("SELECT * FROM polyphone_cache WHERE windowText LIKE '%' || :char || '%'")
    suspend fun getPolyphoneCandidates(char: String): List<PolyphoneCacheRow>

    @Query("DELETE FROM polyphone_cache")
    suspend fun clearAllPolyphoneCache()

    @Query("DELETE FROM polyphone_cache WHERE windowText = :windowText AND targetIndex = :targetIndex")
    suspend fun deletePolyphoneCacheEntry(windowText: String, targetIndex: Int)

    @Query("SELECT COUNT(*) FROM polyphone_cache")
    suspend fun getPolyphoneCacheCount(): Int

    @Query("DELETE FROM polyphone_cache WHERE (windowText || '_' || targetIndex) IN (SELECT (windowText || '_' || targetIndex) FROM polyphone_cache ORDER BY hitCount ASC, updatedAt ASC LIMIT :limit)")
    suspend fun prunePolyphoneCache(limit: Int)

    @Query("SELECT * FROM preset_polyphones")
    suspend fun getAllPresetPolyphones(): List<PresetPolyphoneEntity>

    @Query("SELECT * FROM preset_polyphones")
    fun getAllPresetPolyphonesFlow(): Flow<List<PresetPolyphoneEntity>>

    @Query("SELECT * FROM preset_polyphones WHERE char = :ch")
    suspend fun getPresetPolyphone(ch: String): PresetPolyphoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresetPolyphones(list: List<PresetPolyphoneEntity>)

    @Query("DELETE FROM preset_polyphones WHERE char = :ch")
    suspend fun deletePresetPolyphone(ch: String)

    @Query("DELETE FROM preset_polyphones")
    suspend fun clearAllPresetPolyphones()

    @Query("SELECT COUNT(*) FROM preset_polyphones")
    suspend fun getPresetPolyphonesCount(): Int
}
