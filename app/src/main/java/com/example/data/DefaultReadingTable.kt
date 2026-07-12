package com.example.data

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object DefaultReadingTable {
    private var table: Map<Char, String> = emptyMap()
    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        try {
            val isStream = context.assets.open("default_reading.tsv")
            val reader = BufferedReader(InputStreamReader(isStream))
            val newTable = mutableMapOf<Char, String>()
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = line.split("\t")
                        if (parts.size >= 2) {
                            val ch = parts[0].trim().firstOrNull()
                            if (ch != null) {
                                newTable[ch] = parts[1].trim()
                            }
                        }
                    }
                }
            }
            table = newTable
            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun mostCommon(ch: Char, candidates: List<String>): String {
        return table[ch] ?: candidates.firstOrNull() ?: ch.toString()
    }
}
