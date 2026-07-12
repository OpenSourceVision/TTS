package com.example.data

import android.content.Context

object TextRuleProcessor {
    suspend fun process(originalText: String, appDao: AppDao, context: Context): String {
        val cached = RuleCache.get(originalText)
        if (cached != null) {
            return cached
        }

        var processed = originalText
        
        try {
            val rules = appDao.getAllRules()
            val activeRules = rules.filter { it.isEnabled }
            for (rule in activeRules) {
                val target = rule.target
                val replacement = rule.replacement
                if (target.isEmpty()) continue
                try {
                    processed = processed.replace(Regex(target), replacement)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        RuleCache.put(originalText, processed)
        return processed
    }
}
