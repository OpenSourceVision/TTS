package com.example.data

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class CharTokenizer(vocabPath: String, context: Context) {
    private val vocab: Map<String, Int>

    init {
        val vocabMap = mutableMapOf<String, Int>()
        try {
            val isStream = context.assets.open(vocabPath)
            val reader = BufferedReader(InputStreamReader(isStream))
            var idx = 0
            reader.forEachLine { line ->
                val token = line.trim()
                if (token.isNotEmpty()) {
                    vocabMap[token] = idx
                    idx++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vocab = vocabMap
    }

    fun encode(text: String): LongArray {
        val unkId = vocab["[UNK]"] ?: 1
        return text.map { 
            (vocab[it.toString()] ?: unkId).toLong() 
        }.toLongArray()
    }
}
