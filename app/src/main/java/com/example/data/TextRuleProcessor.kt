package com.example.data

object TextRuleProcessor {
    suspend fun process(originalText: String, appDao: AppDao): String {
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
                val matchWord = rule.matchWord
                
                if (target.isEmpty()) continue
                
                val sanitizedMatchWord = matchWord.split("|")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("|")
                
                if (sanitizedMatchWord.isNotEmpty()) {
                    if (rule.isForwardMatch) {
                        val regexStr = "(" + sanitizedMatchWord + ")" + java.util.regex.Pattern.quote(target)
                        try {
                            val regex = RulePatternCache.getOrCompile(regexStr)
                            processed = regex.matcher(processed).replaceAll("$1" + java.util.regex.Matcher.quoteReplacement(replacement))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        val regexStr = java.util.regex.Pattern.quote(target) + "(" + sanitizedMatchWord + ")"
                        try {
                            val regex = RulePatternCache.getOrCompile(regexStr)
                            processed = regex.matcher(processed).replaceAll(java.util.regex.Matcher.quoteReplacement(replacement) + "$1")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    processed = processed.replace(target, replacement)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        RuleCache.put(originalText, processed)
        return processed
    }
}
