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
    suspend fun insertHistory(history: HistoryEntity)

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
}
