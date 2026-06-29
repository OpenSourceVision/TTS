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
    val themeMode: Int = 0 // 0 = Auto (System), 1 = Light, 2 = Dark
)
