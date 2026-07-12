package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preset_polyphones")
data class PresetPolyphoneEntity(
    @PrimaryKey val char: String, // The Chinese character, e.g., "重"
    val readings: String          // Comma-separated readings, e.g., "chóng,zhòng"
)
