package com.example.data

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DisambiguationCharResult(
    val char: Char,
    val isPolyphonic: Boolean,
    val candidates: List<String>?,
    val originalPinyin: String?,
    val finalPinyin: String?,
    val tierType: String?
)

class PolyphoneResolver(
    private val appDao: AppDao,
    private val context: Context,
    private val inferenceTimeoutMs: Long = 1000, // ONNX models might take a bit longer on first execution
) {
    init {
        PolyphonicTable.init(context)
        DefaultReadingTable.init(context)
    }

    suspend fun resolveWithDetails(sentence: String): List<DisambiguationCharResult> {
        val results = mutableListOf<DisambiguationCharResult>()
        
        for (idx in sentence.indices) {
            val ch = sentence[idx]
            val isPoly = PolyphonicTable.isPolyphonic(ch)
            val candidates = PolyphonicTable.candidatesOf(ch)
            
            if (!isPoly || candidates == null) {
                results.add(
                    DisambiguationCharResult(
                        char = ch,
                        isPolyphonic = false,
                        candidates = null,
                        originalPinyin = null,
                        finalPinyin = null,
                        tierType = null
                    )
                )
                continue
            }
            
            // 1. Query Local Self-Learning Cache with smart flexible substring matching
            val cachedRow = findMatchingCache(sentence, idx, ch)
            var chosenPinyin: String? = null
            var tierType: String = "兜底拼音"
            
            if (cachedRow != null) {
                chosenPinyin = cachedRow.pinyin
                tierType = "自学习缓存"
                
                // Update stats
                val updatedRow = cachedRow.copy(
                    hitCount = cachedRow.hitCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
                appDao.upsertPolyphoneCache(updatedRow)
            } else {
                // 2. Cache Miss: Directly run Large Language Model (Cloud or Local API)
                val predicted = predictWithLLM(sentence, ch, candidates)
                if (predicted != null) {
                    chosenPinyin = predicted
                    tierType = if (appDao.getSettings()?.useLocalModel == true) "本地模型" else "云端模型"
                    
                    // Insert new prediction into cache. Initially cache the ENTIRE sentence.
                    val newRow = PolyphoneCacheRow(
                        windowText = sentence,
                        targetIndex = idx,
                        pinyin = predicted,
                        hitCount = 1,
                        updatedAt = System.currentTimeMillis()
                    )
                    appDao.upsertPolyphoneCache(newRow)
                    
                    // Prune cache if it grows beyond threshold
                    try {
                        val count = appDao.getPolyphoneCacheCount()
                        if (count > 50000) {
                            appDao.prunePolyphoneCache(10000) // Keep size within 40,000
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            // 3. Fallback to default high frequency reading
            if (chosenPinyin == null) {
                chosenPinyin = DefaultReadingTable.mostCommon(ch, candidates)
                tierType = "兜底拼音"
            }
            
            results.add(
                DisambiguationCharResult(
                    char = ch,
                    isPolyphonic = true,
                    candidates = candidates,
                    originalPinyin = candidates.firstOrNull(),
                    finalPinyin = chosenPinyin,
                    tierType = tierType
                )
            )
        }
        
        return results
    }

    suspend fun findMatchingCache(sentence: String, idx: Int, targetChar: Char): PolyphoneCacheRow? {
        val candidates = appDao.getPolyphoneCandidates(targetChar.toString())
            .filter { it.windowText.getOrNull(it.targetIndex) == targetChar }
            .sortedByDescending { it.windowText.length }
        
        for (row in candidates) {
            val start = idx - row.targetIndex
            val end = start + row.windowText.length
            if (start >= 0 && end <= sentence.length) {
                val sub = sentence.substring(start, end)
                if (sub == row.windowText) {
                    return row
                }
            }
        }
        return null
    }

    private suspend fun predictWithLLM(
        sentence: String,
        targetChar: Char,
        candidates: List<String>
    ): String? {
        val settings = appDao.getSettings() ?: SettingsEntity()
        return if (settings.useLocalModel) {
            predictWithLocalModel(sentence, targetChar, candidates, settings)
        } else {
            predictWithCloudModel(sentence, targetChar, candidates, settings)
        }
    }

    private suspend fun predictWithLocalModel(
        sentence: String,
        targetChar: Char,
        candidates: List<String>,
        settings: SettingsEntity
    ): String? = withContext(Dispatchers.IO) {
        val endpoint = settings.localModelEndpoint.ifBlank { "http://127.0.0.1:11434/v1/chat/completions" }
        val apiKey = settings.localModelApiKey
        val modelName = settings.localModelName.ifBlank { "llama3" }

        val prompt = """
            请为以下句子中的多音字选择正确的拼音（必须从给定的候选拼音中选择）。
            句子："$sentence"
            目标多音字："$targetChar"
            候选拼音列表：${candidates.joinToString(", ")}
            
            要求：
            1. 仔细分析该多音字在句子中的语境和词组。
            2. 必须且只能返回候选拼音列表中的其中一个拼音，不要包含任何声调符号以外的多余字符或标点，只返回拼音本身。
            例如，候选是 "chóng, zhòng"，如果正确读音是 chóng，只需返回 "chóng"。
        """.trimIndent()

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val jsonRequest = JSONObject().apply {
                put("model", modelName)
                val messagesArray = JSONArray().apply {
                    val messageObj = JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                    put(messageObj)
                }
                put("messages", messagesArray)
                put("temperature", 0.1)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonRequest.toString().toRequestBody(mediaType)

            val reqBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)

            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("LocalG2P", "Request failed with code: ${response.code}")
                return@withContext null
            }

            val bodyString = response.body?.string() ?: return@withContext null
            val responseJson = JSONObject(bodyString)
            val choices = responseJson.optJSONArray("choices") ?: return@withContext null
            val firstChoice = choices.optJSONObject(0) ?: return@withContext null
            val message = firstChoice.optJSONObject("message") ?: return@withContext null
            val textResult = message.optString("content")?.trim()?.lowercase() ?: return@withContext null

            val cleanedResult = textResult.replace("[^a-zA-Zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü]".toRegex(), "")
            val matchedCandidate = candidates.find { candidate ->
                candidate.lowercase().trim() == cleanedResult
            }
            matchedCandidate
        } catch (e: Exception) {
            android.util.Log.e("LocalG2P", "Error predicting with local model: ${e.message}", e)
            null
        }
    }

    private suspend fun predictWithCloudModel(
        sentence: String,
        targetChar: Char,
        candidates: List<String>,
        settings: SettingsEntity
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = settings.customGeminiApiKey.ifBlank { com.example.BuildConfig.GEMINI_API_KEY }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            android.util.Log.e("GeminiG2P", "Gemini API key is not configured or placeholder.")
            return@withContext null
        }

        val model = settings.customGeminiModel.ifBlank { "gemini-1.5-flash" }
        val customUrl = settings.customGeminiEndpoint.trim()
        val url = if (customUrl.isNotBlank()) {
            if (customUrl.contains("key=")) {
                customUrl
            } else {
                if (customUrl.contains("?")) "$customUrl&key=$apiKey" else "$customUrl?key=$apiKey"
            }
        } else {
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        }

        val prompt = """
            请为以下句子中的多音字选择正确的拼音（必须从给定的候选拼音中选择）。
            句子："$sentence"
            目标多音字："$targetChar"
            候选拼音列表：${candidates.joinToString(", ")}
            
            要求：
            1. 仔细分析该多音字在句子中的语境和词组。
            2. 必须且只能返回候选拼音列表中的其中一个拼音，不要包含任何声调符号以外的多余字符或标点，只返回拼音本身。
            例如，候选是 "chóng, zhòng"，如果正确读音是 chóng，只需返回 "chóng"。
        """.trimIndent()

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val jsonRequest = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            val partObj = JSONObject().apply {
                                put("text", prompt)
                            }
                            put(partObj)
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val config = JSONObject().apply {
                    put("temperature", 0.1)
                }
                put("generationConfig", config)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonRequest.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("GeminiG2P", "Request failed with code: ${response.code}")
                return@withContext null
            }

            val bodyString = response.body?.string() ?: return@withContext null
            val responseJson = JSONObject(bodyString)
            val candidatesArray = responseJson.optJSONArray("candidates") ?: return@withContext null
            val firstCandidate = candidatesArray.optJSONObject(0) ?: return@withContext null
            val content = firstCandidate.optJSONObject("content") ?: return@withContext null
            val parts = content.optJSONArray("parts") ?: return@withContext null
            val firstPart = parts.optJSONObject(0) ?: return@withContext null
            val textResult = firstPart.optString("text")?.trim()?.lowercase() ?: return@withContext null

            val cleanedResult = textResult.replace("[^a-zA-Zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü]".toRegex(), "")
            val matchedCandidate = candidates.find { candidate ->
                candidate.lowercase().trim() == cleanedResult
            }
            matchedCandidate
        } catch (e: Exception) {
            android.util.Log.e("GeminiG2P", "Error predicting with Gemini: ${e.message}", e)
            null
        }
    }
}
