package com.example.data

import java.util.concurrent.ConcurrentHashMap

object RuleCache {
    private val cache = ConcurrentHashMap<String, RuleProcessResult>()

    fun get(key: String): RuleProcessResult? = cache[key]

    fun put(key: String, value: RuleProcessResult) {
        if (cache.size > 1000) {
            cache.clear() // Prevent memory issues
        }
        cache[key] = value
    }

    fun clear() {
        cache.clear()
        RulePatternCache.clear()
    }
}

object RulePatternCache {
    private val cache = ConcurrentHashMap<String, java.util.regex.Pattern>()

    fun getOrCompile(regexStr: String): java.util.regex.Pattern {
        return cache.getOrPut(regexStr) {
            java.util.regex.Pattern.compile(regexStr)
        }
    }

    fun clear() {
        cache.clear()
    }
}

