package com.example.data

import org.json.JSONArray
import org.json.JSONObject

// 规则配置备份包 (仅包含替换规则)
data class BackupPackage(
    val version: Int = 1,
    val rulesJson: String
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("version", version)
        obj.put("type", "rules_config")
        obj.put("rules", JSONArray(rulesJson))
        return obj.toString(2)
    }

    companion object {
        fun fromJsonString(jsonStr: String): BackupPackage? {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[")) {
                return BackupPackage(
                    version = 1,
                    rulesJson = trimmed
                )
            }
            return try {
                val obj = JSONObject(trimmed)
                val type = obj.optString("type")
                if (type == "webdav_config") return null
                val version = obj.optInt("version", 1)
                val rulesArray = obj.optJSONArray("rules")
                if (rulesArray == null && !obj.has("rules")) return null
                BackupPackage(
                    version = version,
                    rulesJson = rulesArray?.toString() ?: "[]"
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

// WebDAV 独立配置文件包
data class WebDavConfigPackage(
    val version: Int = 1,
    val webdavUrl: String = "",
    val webdavUsername: String = "",
    val webdavPassword: String = "",
    val webdavDir: String = "TTS",
    val webdavPath: String = "tts_rules_backup.json"
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("version", version)
        obj.put("type", "webdav_config")
        obj.put("webdavUrl", webdavUrl)
        obj.put("webdavUsername", webdavUsername)
        obj.put("webdavPassword", webdavPassword)
        obj.put("webdavDir", webdavDir)
        obj.put("webdavPath", webdavPath)
        return obj.toString(2)
    }

    companion object {
        fun fromJsonString(jsonStr: String): WebDavConfigPackage? {
            return try {
                val obj = JSONObject(jsonStr.trim())
                val type = obj.optString("type")
                if (type == "webdav_config" || (obj.has("webdavUrl") && !obj.has("rules"))) {
                    WebDavConfigPackage(
                        version = obj.optInt("version", 1),
                        webdavUrl = obj.optString("webdavUrl", ""),
                        webdavUsername = obj.optString("webdavUsername", ""),
                        webdavPassword = obj.optString("webdavPassword", ""),
                        webdavDir = obj.optString("webdavDir", "TTS"),
                        webdavPath = obj.optString("webdavPath", "tts_rules_backup.json")
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

// 包含两份配置（规则配置 + WebDAV 配置）的联合本地备份包
data class CombinedBackupPackage(
    val version: Int = 1,
    val rulesPackage: BackupPackage?,
    val webdavPackage: WebDavConfigPackage?
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("version", version)
        obj.put("type", "combined_backup")
        if (rulesPackage != null) {
            obj.put("rules_config", JSONObject(rulesPackage.toJsonString()))
        }
        if (webdavPackage != null) {
            obj.put("webdav_config", JSONObject(webdavPackage.toJsonString()))
        }
        return obj.toString(2)
    }

    companion object {
        fun fromJsonString(jsonStr: String): CombinedBackupPackage? {
            return try {
                val obj = JSONObject(jsonStr.trim())
                val type = obj.optString("type")
                if (type == "combined_backup" || obj.has("rules_config") || obj.has("webdav_config")) {
                    val rulesObj = obj.optJSONObject("rules_config")
                    val webdavObj = obj.optJSONObject("webdav_config")
                    CombinedBackupPackage(
                        version = obj.optInt("version", 1),
                        rulesPackage = rulesObj?.let { BackupPackage.fromJsonString(it.toString()) },
                        webdavPackage = webdavObj?.let { WebDavConfigPackage.fromJsonString(it.toString()) }
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

// 序列化规则相关的通用应用设置（排除 WebDAV 敏感账号与地址配置）
fun SettingsEntity.toRuleSettingsJsonString(): String {
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
    obj.put("customGeminiApiKey", customGeminiApiKey)
    obj.put("customGeminiEndpoint", customGeminiEndpoint)
    obj.put("customGeminiModel", customGeminiModel)
    obj.put("useLocalModel", useLocalModel)
    obj.put("localModelEndpoint", localModelEndpoint)
    obj.put("localModelApiKey", localModelApiKey)
    obj.put("localModelName", localModelName)
    return obj.toString()
}

// 序列化 WebDAV 配置
fun SettingsEntity.toWebDavConfigJsonString(): String {
    val pkg = WebDavConfigPackage(
        version = 1,
        webdavUrl = webdavUrl,
        webdavUsername = webdavUsername,
        webdavPassword = webdavPassword,
        webdavDir = webdavDir,
        webdavPath = webdavPath
    )
    return pkg.toJsonString()
}

// 解析通用规则设置，保留当前已有的 WebDAV 账号信息不被覆盖
fun parseRuleSettingsFromJson(jsonStr: String, currentSettings: SettingsEntity): SettingsEntity {
    return try {
        val obj = JSONObject(jsonStr)
        currentSettings.copy(
            targetEnginePackage = obj.optString("targetEnginePackage", currentSettings.targetEnginePackage),
            port = obj.optInt("port", currentSettings.port),
            pitch = obj.optDouble("pitch", currentSettings.pitch.toDouble()).toFloat(),
            speechRate = obj.optDouble("speechRate", currentSettings.speechRate.toDouble()).toFloat(),
            language = obj.optString("language", currentSettings.language),
            country = obj.optString("country", currentSettings.country),
            autoStartServer = obj.optBoolean("autoStartServer", currentSettings.autoStartServer),
            themeMode = obj.optInt("themeMode", currentSettings.themeMode),
            useDynamicColor = obj.optBoolean("useDynamicColor", currentSettings.useDynamicColor),
            customGeminiApiKey = obj.optString("customGeminiApiKey", currentSettings.customGeminiApiKey),
            customGeminiEndpoint = obj.optString("customGeminiEndpoint", currentSettings.customGeminiEndpoint),
            customGeminiModel = obj.optString("customGeminiModel", currentSettings.customGeminiModel),
            useLocalModel = obj.optBoolean("useLocalModel", currentSettings.useLocalModel),
            localModelEndpoint = obj.optString("localModelEndpoint", currentSettings.localModelEndpoint),
            localModelApiKey = obj.optString("localModelApiKey", currentSettings.localModelApiKey),
            localModelName = obj.optString("localModelName", currentSettings.localModelName)
        )
    } catch (e: Exception) {
        currentSettings
    }
}

// 保持兼容调用的辅助方法
fun SettingsEntity.toFullSettingsJsonString(): String = toRuleSettingsJsonString()
fun parseFullSettingsFromJson(jsonStr: String, currentSettings: SettingsEntity = SettingsEntity()): SettingsEntity =
    parseRuleSettingsFromJson(jsonStr, currentSettings)

fun SettingsEntity.toJsonString(): String = toRuleSettingsJsonString()
fun parseSettingsFromJson(jsonStr: String): SettingsEntity? =
    parseRuleSettingsFromJson(jsonStr, SettingsEntity())
