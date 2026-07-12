package com.example.data

import androidx.room.Entity
import org.json.JSONObject

@Entity(tableName = "polyphone_cache", primaryKeys = ["windowText", "targetIndex"])
data class PolyphoneCacheRow(
    val windowText: String,
    val targetIndex: Int,
    val pinyin: String,
    val hitCount: Int,
    val updatedAt: Long,
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("windowText", windowText)
        obj.put("targetIndex", targetIndex)
        obj.put("pinyin", pinyin)
        obj.put("hitCount", hitCount)
        obj.put("updatedAt", updatedAt)
        return obj.toString()
    }

    companion object {
        fun fromJsonString(jsonStr: String): PolyphoneCacheRow? {
            return try {
                val obj = JSONObject(jsonStr)
                PolyphoneCacheRow(
                    windowText = obj.getString("windowText"),
                    targetIndex = obj.getInt("targetIndex"),
                    pinyin = obj.getString("pinyin"),
                    hitCount = obj.getInt("hitCount"),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
