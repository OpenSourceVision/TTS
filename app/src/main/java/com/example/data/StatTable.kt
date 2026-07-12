package com.example.data

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class ContextKey(val char: Char, val prevChar: Char?, val nextChar: Char?)

object StatTable {
    private var freqTable: Map<ContextKey, String> = emptyMap()
    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        try {
            val isStream = context.assets.open("stat_table.txt")
            val reader = BufferedReader(InputStreamReader(isStream))
            val newFreqTable = mutableMapOf<ContextKey, String>()
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank() && line.contains("->")) {
                        val mainParts = line.split("->")
                        val keyPart = mainParts[0].trim()
                        val reading = mainParts[1].trim()
                        val parts = keyPart.split(",")
                        if (parts.size >= 3) {
                            val char = parts[0].trim().firstOrNull() ?: return@forEach
                            val prevChar = parts[1].trim().firstOrNull().let { if (it == 'n' && parts[1].trim() == "null") null else it }
                            val nextChar = parts[2].trim().firstOrNull().let { if (it == 'n' && parts[2].trim() == "null") null else it }
                            newFreqTable[ContextKey(char, prevChar, nextChar)] = reading
                        }
                    }
                }
            }
            freqTable = newFreqTable
            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun lookup(char: Char, prev: Char?, next: Char?): String? {
        // 优先用前后各一字的完整上下文查，查不到再退化成只用后一字/前一字
        return freqTable[ContextKey(char, prev, next)]
            ?: freqTable[ContextKey(char, null, next)]
            ?: freqTable[ContextKey(char, prev, null)]
    }

    fun getAllRecords(): Map<ContextKey, String> = freqTable
}
