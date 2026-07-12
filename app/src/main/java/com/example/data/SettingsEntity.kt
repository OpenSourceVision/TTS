package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val targetEnginePackage: String = "com.google.android.tts",
    val port: Int = 8080,
    val pitch: Float = 1.0f,
    val speechRate: Float = 1.0f,
    val language: String = "zh",
    val country: String = "CN",
    val autoStartServer: Boolean = true,
    val themeMode: Int = 0, // 0 = Auto (System), 1 = Light, 2 = Dark
    val useDynamicColor: Boolean = true,
    val webdavUrl: String = "",
    val webdavUsername: String = "",
    val webdavPassword: String = "",
    val webdavPath: String = "tts_rules_backup.json",
    val webdavDir: String = "TTS",
    // Cloud model custom configurations
    val customGeminiApiKey: String = "",
    val customGeminiEndpoint: String = "",
    val customGeminiModel: String = "gemini-1.5-flash",
    // Local model configurations
    val useLocalModel: Boolean = false,
    val localModelEndpoint: String = "http://127.0.0.1:11434/v1/chat/completions",
    val localModelApiKey: String = "",
    val localModelName: String = "llama3"
)
