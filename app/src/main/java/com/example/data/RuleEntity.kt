package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val target: String,
    val replacement: String,
    val matchWord: String,
    val isForwardMatch: Boolean = true,
    val isEnabled: Boolean = true
)
