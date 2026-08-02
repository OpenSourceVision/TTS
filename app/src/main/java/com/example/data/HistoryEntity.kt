package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val length: Int,
    val enginePackage: String,
    val timestamp: Long,
    val status: String, // "SUCCESS", "FAILED"
    val durationMs: Long,
    val errorMsg: String? = null,
    val hitsJson: String? = null
) {
    fun parseHits(): List<RuleHit> {
        if (hitsJson.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(hitsJson)
            val list = mutableListOf<RuleHit>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RuleHit(
                        ruleTarget = obj.getString("ruleTarget"),
                        replacement = obj.getString("replacement")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}

fun List<RuleHit>.toHitsJsonString(): String? {
    if (isEmpty()) return null
    return try {
        val jsonArray = JSONArray()
        for (hit in this) {
            val obj = JSONObject()
            obj.put("ruleTarget", hit.ruleTarget)
            obj.put("replacement", hit.replacement)
            jsonArray.put(obj)
        }
        jsonArray.toString()
    } catch (e: Exception) {
        null
    }
}

