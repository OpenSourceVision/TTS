package com.example.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupPackage(
    val version: Int = 1,
    val rulesJson: String,
    val settingsJson: String? = null
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("version", version)
        obj.put("rules", JSONArray(rulesJson))
        if (settingsJson != null) {
            obj.put("settings", JSONObject(settingsJson))
        }
        return obj.toString(2)
    }

    companion object {
        fun fromJsonString(jsonStr: String): BackupPackage? {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[")) {
                return BackupPackage(
                    version = 1,
                    rulesJson = trimmed,
                    settingsJson = null
                )
            }
            return try {
                val obj = JSONObject(trimmed)
                val version = obj.optInt("version", 1)
                val rulesArray = obj.getJSONArray("rules")
                val settingsObj = obj.optJSONObject("settings")
                BackupPackage(
                    version = version,
                    rulesJson = rulesArray.toString(),
                    settingsJson = settingsObj?.toString()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

fun SettingsEntity.toJsonString(): String {
    val obj = JSONObject()
    obj.put("targetEnginePackage", targetEnginePackage)
    obj.put("port", port)
    obj.put("pitch", pitch.toDouble())
    obj.put("speechRate", speechRate.toDouble())
    obj.put("language", language)
    obj.put("country", country)
    obj.put("autoStartServer", autoStartServer)
    obj.put("themeMode", themeMode)
    obj.put("useDynamicColor", useDynamicColor)
    obj.put("webdavUrl", webdavUrl)
    obj.put("webdavUsername", webdavUsername)
    obj.put("webdavPassword", webdavPassword)
    obj.put("webdavPath", webdavPath)
    return obj.toString()
}

fun parseSettingsFromJson(jsonStr: String): SettingsEntity? {
    return try {
        val obj = JSONObject(jsonStr)
        SettingsEntity(
            id = 1,
            targetEnginePackage = obj.optString("targetEnginePackage", "com.google.android.tts"),
            port = obj.optInt("port", 8080),
            pitch = obj.optDouble("pitch", 1.0).toFloat(),
            speechRate = obj.optDouble("speechRate", 1.0).toFloat(),
            language = obj.optString("language", "zh"),
            country = obj.optString("country", "CN"),
            autoStartServer = obj.optBoolean("autoStartServer", true),
            themeMode = obj.optInt("themeMode", 0),
            useDynamicColor = obj.optBoolean("useDynamicColor", true),
            webdavUrl = obj.optString("webdavUrl", ""),
            webdavUsername = obj.optString("webdavUsername", ""),
            webdavPassword = obj.optString("webdavPassword", ""),
            webdavPath = obj.optString("webdavPath", "tts_rules_backup.json")
        )
    } catch (e: Exception) {
        null
    }
}
