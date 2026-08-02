package com.example.data

import android.content.Context

data class RuleProcessResult(
    val processedText: String,
    val hits: List<RuleHit>
)

data class RuleHit(
    val ruleTarget: String,
    val replacement: String
)

object TextRuleProcessor {
    suspend fun process(originalText: String, appDao: AppDao, context: Context): RuleProcessResult {
        val cached = RuleCache.get(originalText)
        if (cached != null) {
            return cached
        }

        var processed = originalText
        val hits = mutableListOf<RuleHit>()
        
        try {
            val rules = appDao.getAllRules()
            // 按规则 target 长度降序排序（稳定排序），优先匹配更具体的长字符串，避免短规则提前拦截长规则匹配
            val activeRules = rules.filter { it.isEnabled }.sortedByDescending { it.target.length }
            for (rule in activeRules) {
                val target = rule.target
                val replacement = rule.replacement
                if (target.isEmpty()) continue
                try {
                    val regex = Regex(target)
                    var hit = false
                    val replaced = regex.replace(processed) { matchResult ->
                        hit = true
                        var res = replacement
                        if (res.contains('$')) {
                            for (i in matchResult.groupValues.indices.reversed()) {
                                res = res.replace("$$i", matchResult.groupValues[i])
                            }
                        }
                        res
                    }
                    if (hit) {
                        hits.add(RuleHit(target, replacement))
                        processed = replaced
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val result = RuleProcessResult(processedText = processed, hits = hits)
        RuleCache.put(originalText, result)
        return result
    }
}

