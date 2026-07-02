package com.example.data

import java.util.concurrent.ConcurrentHashMap

object RuleCache {
    private val cache = ConcurrentHashMap<String, String>()

    fun get(key: String): String? = cache[key]

    fun put(key: String, value: String) {
        if (cache.size > 1000) {
            cache.clear() // Prevent memory issues
        }
        cache[key] = value
    }

    fun clear() {
        cache.clear()
    }
}
