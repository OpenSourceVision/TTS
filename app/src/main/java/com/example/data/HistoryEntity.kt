package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val length: Int,
    val enginePackage: String,
    val timestamp: Long,
    val status: String, // "SUCCESS", "FAILED"
    val durationMs: Long,
    val errorMsg: String? = null
)
