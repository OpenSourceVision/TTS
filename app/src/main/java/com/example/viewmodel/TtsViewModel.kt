package com.example.viewmodel

import java.io.File
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.data.SettingsEntity
import com.example.data.RuleGroupEntity
import com.example.data.RuleEntity
import com.example.data.RuleCache
import com.example.data.TextRuleProcessor
import com.example.data.WebDavHelper
import com.example.data.BackupPackage
import com.example.data.toJsonString
import com.example.data.parseSettingsFromJson
import com.example.service.TtsServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.map

data class TtsEngineInfo(
    val packageName: String,
    val label: String
)

enum class GroupSortOrder(val label: String) {
    TIME_ASC("创建时间正序"),
    TIME_DESC("创建时间倒序"),
    NAME_ASC("名称字母正序"),
    NAME_DESC("名称字母倒序"),
    COUNT_DESC("规则数量降序"),
    COUNT_ASC("规则数量升序")
}

class TtsViewModel(private val context: Context, private val database: AppDatabase) : ViewModel() {

    val appDao = database.appDao()

    private val _ruleSortOrder = MutableStateFlow(GroupSortOrder.TIME_ASC)
    val ruleSortOrder: StateFlow<GroupSortOrder> = _ruleSortOrder.asStateFlow()

    fun updateRuleSortOrder(order: GroupSortOrder) {
        _ruleSortOrder.value = order
    }

    val settingsState: StateFlow<SettingsEntity> = appDao.getSettingsFlow()
        .map { it ?: SettingsEntity() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsEntity()
        )

    val historyState: StateFlow<List<HistoryEntity>> = appDao.getHistoryFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val ruleGroupsState: StateFlow<List<RuleGroupEntity>> = appDao.getAllRuleGroupsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val rulesState: StateFlow<List<RuleEntity>> = appDao.getAllRulesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _engines = MutableStateFlow<List<TtsEngineInfo>>(emptyList())
    val engines: StateFlow<List<TtsEngineInfo>> = _engines.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var testTts: TextToSpeech? = null

    init {
        // Initialize default settings and preload polyphonic & default reading tables off the main thread
        viewModelScope.launch(Dispatchers.IO) {
            // Pre-warm tables in background to avoid future lookup lag
            com.example.data.PolyphonicTable.init(context)
            com.example.data.DefaultReadingTable.init(context)

            val cacheCount = appDao.getPolyphoneCacheCount()
            _polyphoneCacheCount.value = cacheCount

            val existing = appDao.getSettings()
            if (existing == null) {
                appDao.saveSettings(SettingsEntity())
            }
            
            // Populate default rules if there are no rule groups or if database has old literals
            val existingGroups = appDao.getAllRuleGroups()
            val allRules = appDao.getAllRules()
            val hasOldRules = allRules.any { it.target in listOf("重要", "重心", "一重", "二重", "三重") }
            if (existingGroups.isEmpty() || hasOldRules) {
                setupDefaultReferenceRules()
            }
        }
    }

    private suspend fun setupDefaultReferenceRules() {
        appDao.clearAllRuleGroups()
        appDao.clearAllRules()
        
        // 1. Group: "重" -> "众"
        val group1Id = appDao.insertRuleGroup(RuleGroupEntity(name = "重", replacement = "众"))
        appDao.insertRule(
            RuleEntity(
                groupId = group1Id,
                target = "重(?=要|心)",
                replacement = "众",
                matchWord = "",
                isForwardMatch = true,
                isEnabled = true
            )
        )

        // 2. Group: "重" -> "虫"
        val group2Id = appDao.insertRuleGroup(RuleGroupEntity(name = "重", replacement = "虫"))
        appDao.insertRule(
            RuleEntity(
                groupId = group2Id,
                target = "(?<=[一二三四五六七八九十])重",
                replacement = "虫",
                matchWord = "",
                isForwardMatch = true,
                isEnabled = true
            )
        )

        RuleCache.clear()
    }

    fun loadEngines(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val dummyTts = TextToSpeech(context, null)
            val list = dummyTts.engines.map { engine ->
                TtsEngineInfo(packageName = engine.name, label = engine.label)
            }
            dummyTts.shutdown()
            _engines.value = list

            // Update database default engine if current is empty or not in settings
            val settings = appDao.getSettings() ?: SettingsEntity()
            if (list.isNotEmpty() && (settings.targetEnginePackage.isEmpty() || list.none { it.packageName == settings.targetEnginePackage })) {
                appDao.saveSettings(settings.copy(targetEnginePackage = list.first().packageName))
            }
        }
    }

    fun updateSettings(settings: SettingsEntity) {
        viewModelScope.launch {
            appDao.saveSettings(settings)
        }
    }

    fun playTest(context: Context, text: String, enginePackage: String, rate: Float, pitch: Float) {
        viewModelScope.launch {
            _isTesting.value = true
            testTts?.stop()
            try {
                testTts?.shutdown()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Apply polyphone rules on the test text with cache!
            val processedText = TextRuleProcessor.process(text, appDao, context)

            testTts = TextToSpeech(context, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val tts = testTts
                    if (tts != null) {
                        try {
                            val langResult = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                                tts.setLanguage(Locale.CHINESE)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        tts.setSpeechRate(rate)
                        tts.setPitch(pitch)
                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            
                            override fun onDone(utteranceId: String?) {
                                viewModelScope.launch {
                                    _isTesting.value = false
                                    stopTest()
                                }
                            }
                            
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                viewModelScope.launch {
                                    _isTesting.value = false
                                    stopTest()
                                    _toastEvent.emit("播放出错")
                                }
                            }
                            
                            override fun onError(utteranceId: String?, errorCode: Int) {
                                viewModelScope.launch {
                                    _isTesting.value = false
                                    stopTest()
                                    _toastEvent.emit("播放出错: 错误码 $errorCode")
                                }
                            }
                            
                            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                viewModelScope.launch {
                                    _isTesting.value = false
                                    stopTest()
                                }
                            }
                        })
                        tts.speak(processedText, TextToSpeech.QUEUE_FLUSH, null, "test_utterance")
                    }
                } else {
                    viewModelScope.launch {
                        _isTesting.value = false
                        _toastEvent.emit("测试初始化失败")
                    }
                }
            }, enginePackage)
        }
    }

    fun stopTest() {
        _isTesting.value = false
        try {
            testTts?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            testTts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        testTts = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            appDao.clearHistory()
            _toastEvent.emit("日志已清空")
        }
    }

    fun copyLegadoConfig(context: Context, ipType: String = "127.0.0.1") {
        viewModelScope.launch {
            val settings = settingsState.value
            val ip = if (ipType == "127.0.0.1") "127.0.0.1" else getWifiIpAddress(context)
            
            val json = """
            {
              "name": "TTS转发",
              "url": "http://${ip}:${settings.port}/api/tts?text={{java.encodeURI(speakText)}}",
              "contentType": "audio/wav",
              "id": ${System.currentTimeMillis()}
            }
            """.trimIndent()

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Legado TTS Config", json)
            clipboard.setPrimaryClip(clip)
            _toastEvent.emit("已复制到剪切板")
        }
    }

    fun importToLegado(context: Context, ipType: String = "127.0.0.1") {
        viewModelScope.launch {
            val settings = settingsState.value
            val ip = if (ipType == "127.0.0.1") "127.0.0.1" else getWifiIpAddress(context)
            
            val json = """
            {
              "name": "TTS转发",
              "url": "http://${ip}:${settings.port}/api/tts?text={{java.encodeURI(speakText)}}",
              "contentType": "audio/wav",
              "id": ${System.currentTimeMillis()}
            }
            """.trimIndent()

            try {
                val encodedJson = java.net.URLEncoder.encode(json, "UTF-8")
                // Support both schemes (legado:// and yuedu://)
                val uriString = "legado://import/httpTTS?src=$encodedJson"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                _toastEvent.emit("已调起【阅读】App导入配置")
            } catch (e: Exception) {
                try {
                    val encodedJson = java.net.URLEncoder.encode(json, "UTF-8")
                    val uriString = "yuedu://import/httpTTS?src=$encodedJson"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    _toastEvent.emit("已调起【阅读】App导入配置")
                } catch (e2: Exception) {
                    // Fallback to copy configuration
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Legado TTS Config", json)
                    clipboard.setPrimaryClip(clip)
                    _toastEvent.emit("未检测到阅读App，已自动复制配置JSON到剪贴板，请在阅读App中手动导入。")
                }
            }
        }
    }

    fun getWifiIpAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress == 0) return "127.0.0.1"
            val ip = String.format(
                java.util.Locale.US,
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
            if (ip == "0.0.0.0") "127.0.0.1" else ip
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    fun getCacheSize(context: Context): String {
        return try {
            var size = 0L
            context.cacheDir?.let { size += getFolderSize(it) }
            context.externalCacheDir?.let { size += getFolderSize(it) }
            if (size <= 0) return "0.00 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            String.format(java.util.Locale.US, "%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        } catch (e: Exception) {
            "0.00 B"
        }
    }

    private fun getFolderSize(file: File): Long {
        var size = 0L
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    size += getFolderSize(child)
                }
            } else if (file.isFile) {
                size += file.length()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    fun clearCache(context: Context, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var count = 0
                context.cacheDir?.let { count += deleteFilesInside(it) }
                context.externalCacheDir?.let { count += deleteFilesInside(it) }
                withContext(Dispatchers.Main) {
                    onComplete()
                }
                _toastEvent.emit("成功清理了缓存")
            } catch (e: Exception) {
                _toastEvent.emit("清理缓存失败: ${e.message}")
            }
        }
    }

    private fun deleteFilesInside(file: File): Int {
        var count = 0
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    if (child.isDirectory) {
                        count += deleteFilesInside(child)
                        child.delete() // Try to delete the directory if it's empty
                    } else if (child.isFile) {
                        if (child.delete()) {
                            count++
                        }
                    }
                }
            } else if (file.isFile) {
                if (file.delete()) {
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val intent = Intent().apply {
                    action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent().apply {
                        action = android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    viewModelScope.launch {
                        _toastEvent.emit("请手动在系统设置中允许本应用后台运行（加入电池优化白名单）")
                    }
                }
            }
        } else {
            viewModelScope.launch {
                _toastEvent.emit("当前系统版本无需手动配置电池优化")
            }
        }
    }

    fun addRuleGroup(name: String, replacement: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _toastEvent.emit("分组名称不能为空")
                return@launch
            }
            appDao.insertRuleGroup(RuleGroupEntity(name = name, replacement = replacement))
            RuleCache.clear()
            _toastEvent.emit("新增分组 '$name' 成功")
        }
    }

    fun updateRuleGroup(groupId: Long, name: String, replacement: String) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _toastEvent.emit("分组名称不能为空")
                return@launch
            }
            val existing = appDao.getAllRuleGroups().find { it.id == groupId }
            if (existing != null) {
                appDao.insertRuleGroup(existing.copy(name = name, replacement = replacement))
                
                // Keep rule targets in sync if they matched the old group name
                val rules = appDao.getRulesForGroup(groupId)
                for (rule in rules) {
                    if (rule.target == existing.name) {
                        appDao.insertRule(rule.copy(target = name))
                    }
                }
                
                RuleCache.clear()
                _toastEvent.emit("修改分组成功")
            }
        }
    }

    fun deleteRuleGroup(groupId: Long) {
        viewModelScope.launch {
            appDao.deleteRuleGroupById(groupId)
            appDao.deleteRulesByGroupId(groupId)
            RuleCache.clear()
            _toastEvent.emit("已删除该分组及其全部规则")
        }
    }

    fun addRule(groupId: Long, target: String, replacement: String, matchWord: String, isForwardMatch: Boolean) {
        viewModelScope.launch {
            if (target.isBlank() || replacement.isBlank()) {
                _toastEvent.emit("目标字与替换字不能为空")
                return@launch
            }
            appDao.insertRule(
                RuleEntity(
                    groupId = groupId,
                    target = target,
                    replacement = replacement,
                    matchWord = matchWord,
                    isForwardMatch = isForwardMatch
                )
            )
            RuleCache.clear()
            _toastEvent.emit("新增规则成功")
        }
    }

    fun updateRule(ruleId: Long, target: String, replacement: String) {
        viewModelScope.launch {
            if (target.isBlank() || replacement.isBlank()) {
                _toastEvent.emit("目标正则与替换字不能为空")
                return@launch
            }
            val allRules = appDao.getAllRules()
            val existing = allRules.find { it.id == ruleId }
            if (existing != null) {
                appDao.insertRule(
                    existing.copy(
                        target = target,
                        replacement = replacement
                    )
                )
                RuleCache.clear()
                _toastEvent.emit("修改规则成功")
            }
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            appDao.deleteRuleById(ruleId)
            RuleCache.clear()
            _toastEvent.emit("删除规则成功")
        }
    }

    fun clearAllRules() {
        viewModelScope.launch {
            appDao.clearAllRuleGroups()
            appDao.clearAllRules()
            RuleCache.clear()
            _toastEvent.emit("所有发音与替换规则已清空")
        }
    }

    fun toggleRuleEnabled(rule: RuleEntity) {
        viewModelScope.launch {
            appDao.insertRule(rule.copy(isEnabled = !rule.isEnabled))
            RuleCache.clear()
            _toastEvent.emit("规则状态已更新")
        }
    }

    private val _polyphoneCacheCount = MutableStateFlow(0)
    val polyphoneCacheCount: StateFlow<Int> = _polyphoneCacheCount.asStateFlow()

    private val _polyphoneCacheList = MutableStateFlow<List<com.example.data.PolyphoneCacheRow>>(emptyList())
    val polyphoneCacheList: StateFlow<List<com.example.data.PolyphoneCacheRow>> = _polyphoneCacheList.asStateFlow()

    fun loadPolyphoneCacheList() {
        viewModelScope.launch {
            _polyphoneCacheList.value = appDao.getAllPolyphoneCache()
        }
    }

    fun refreshPolyphoneCacheCount() {
        viewModelScope.launch {
            _polyphoneCacheCount.value = appDao.getPolyphoneCacheCount()
        }
    }

    fun clearPolyphoneCache() {
        viewModelScope.launch {
            appDao.clearAllPolyphoneCache()
            _polyphoneCacheCount.value = 0
            _polyphoneCacheList.value = emptyList()
            _toastEvent.emit("自学习缓存已清空")
        }
    }

    fun deletePolyphoneCacheEntry(row: com.example.data.PolyphoneCacheRow) {
        viewModelScope.launch {
            appDao.deletePolyphoneCacheEntry(row.windowText, row.targetIndex)
            loadPolyphoneCacheList()
            refreshPolyphoneCacheCount()
            _toastEvent.emit("已删除该多音字缓存")
        }
    }

    fun refinePolyphoneCacheEntry(
        oldRow: com.example.data.PolyphoneCacheRow,
        newWindowText: String,
        newTargetIndex: Int
    ) {
        viewModelScope.launch {
            // Delete the old row first
            appDao.deletePolyphoneCacheEntry(oldRow.windowText, oldRow.targetIndex)
            
            // Insert the new row
            val newRow = com.example.data.PolyphoneCacheRow(
                windowText = newWindowText,
                targetIndex = newTargetIndex,
                pinyin = oldRow.pinyin,
                hitCount = oldRow.hitCount,
                updatedAt = System.currentTimeMillis()
            )
            appDao.upsertPolyphoneCache(newRow)
            
            loadPolyphoneCacheList()
            refreshPolyphoneCacheCount()
            _toastEvent.emit("多音字上下文已提炼成功")
        }
    }

    private val _presetPolyphoneList = MutableStateFlow<List<com.example.data.PresetPolyphoneEntity>>(emptyList())
    val presetPolyphoneList: StateFlow<List<com.example.data.PresetPolyphoneEntity>> = _presetPolyphoneList.asStateFlow()

    fun loadPresetPolyphoneList() {
        viewModelScope.launch {
            _presetPolyphoneList.value = appDao.getAllPresetPolyphones()
        }
    }

    fun upsertPresetPolyphone(char: String, readings: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            if (char.trim().length != 1) {
                _toastEvent.emit("多音字必须是单个字符")
                return@launch
            }
            if (readings.trim().isEmpty()) {
                _toastEvent.emit("候选拼音不能为空")
                return@launch
            }
            val entity = com.example.data.PresetPolyphoneEntity(char.trim(), readings.trim())
            appDao.insertPresetPolyphones(listOf(entity))
            com.example.data.PolyphonicTable.reload(context)
            loadPresetPolyphoneList()
            _toastEvent.emit("已保存预置多音字: $char")
        }
    }

    fun deletePresetPolyphone(char: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            appDao.deletePresetPolyphone(char)
            com.example.data.PolyphonicTable.reload(context)
            loadPresetPolyphoneList()
            _toastEvent.emit("已删除预置多音字: $char")
        }
    }

    fun resetPresetPolyphonesToDefault(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            appDao.clearAllPresetPolyphones()
            com.example.data.PolyphonicTable.reload(context)
            loadPresetPolyphoneList()
            _toastEvent.emit("多音字表已重置为系统默认")
        }
    }

    fun exportPolyphoneCache(context: Context, uri: android.net.Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rows = appDao.getAllPolyphoneCache()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    rows.forEach { row ->
                        writer.write(row.toJsonString())
                        writer.newLine()
                    }
                }
                withContext(Dispatchers.Main) {
                    onComplete(true, "导出成功，共 ${rows.size} 条数据")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "导出失败: ${e.message}")
                }
            }
        }
    }

    fun importPolyphoneCache(context: Context, uri: android.net.Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var added = 0
                var merged = 0
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            val row = com.example.data.PolyphoneCacheRow.fromJsonString(line)
                            if (row != null) {
                                val existing = appDao.findPolyphoneCache(row.windowText, row.targetIndex)
                                if (existing == null) {
                                    appDao.upsertPolyphoneCache(row)
                                    added++
                                } else if (row.hitCount > existing.hitCount) {
                                    appDao.upsertPolyphoneCache(row)
                                    merged++
                                }
                            }
                        }
                    }
                }
                refreshPolyphoneCacheCount()
                withContext(Dispatchers.Main) {
                    onComplete(true, "导入完成: 新增 $added 条，合并 $merged 条")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "导入失败: ${e.message}")
                }
            }
        }
    }

    fun testPolyphoneDisambiguation(context: Context, text: String, onResult: (List<com.example.data.DisambiguationCharResult>) -> Unit) {
        viewModelScope.launch {
            val resolver = com.example.data.PolyphoneResolver(appDao, context)
            val results = resolver.resolveWithDetails(text)
            refreshPolyphoneCacheCount()
            onResult(results)
        }
    }

    suspend fun exportRulesToJsonString(): String {
        return withContext(Dispatchers.IO) {
            try {
                val groups = appDao.getAllRuleGroups()
                val allRules = appDao.getAllRules()
                
                val jsonArray = org.json.JSONArray()
                for (group in groups) {
                    val groupObj = org.json.JSONObject()
                    groupObj.put("groupName", group.name)
                    groupObj.put("groupReplacement", group.replacement)
                    
                    val rulesArray = org.json.JSONArray()
                    val groupRules = allRules.filter { it.groupId == group.id }
                    for (rule in groupRules) {
                        val ruleObj = org.json.JSONObject()
                        ruleObj.put("target", rule.target)
                        ruleObj.put("replacement", rule.replacement)
                        ruleObj.put("matchWord", rule.matchWord)
                        ruleObj.put("isForwardMatch", rule.isForwardMatch)
                        ruleObj.put("isEnabled", rule.isEnabled)
                        rulesArray.put(ruleObj)
                    }
                    groupObj.put("rules", rulesArray)
                    jsonArray.put(groupObj)
                }
                jsonArray.toString(2)
            } catch (e: Exception) {
                ""
            }
        }
    }

    fun importRulesFromJson(jsonStr: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (jsonStr.isBlank()) {
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }
                val jsonArray = org.json.JSONArray(jsonStr)
                for (i in (jsonArray.length() - 1) downTo 0) {
                    val groupObj = jsonArray.getJSONObject(i)
                    val groupName = groupObj.getString("groupName")
                    val groupReplacement = groupObj.optString("groupReplacement", "")
                    
                    val existingGroups = appDao.getAllRuleGroups()
                    var groupId = existingGroups.find { it.name == groupName && it.replacement == groupReplacement }?.id
                    if (groupId == null) {
                        groupId = appDao.insertRuleGroup(RuleGroupEntity(name = groupName, replacement = groupReplacement))
                    }
                    
                    val rulesArray = groupObj.optJSONArray("rules") ?: org.json.JSONArray()
                    for (j in (rulesArray.length() - 1) downTo 0) {
                        val ruleObj = rulesArray.getJSONObject(j)
                        val target = ruleObj.getString("target")
                        val replacement = ruleObj.getString("replacement")
                        val matchWord = ruleObj.optString("matchWord", "")
                        val isForwardMatch = ruleObj.optBoolean("isForwardMatch", true)
                        val isEnabled = ruleObj.optBoolean("isEnabled", true)
                        
                        val existingRules = appDao.getRulesForGroup(groupId)
                        val duplicate = existingRules.any { 
                            it.target == target && 
                            it.replacement == replacement && 
                            it.matchWord == matchWord && 
                            it.isForwardMatch == isForwardMatch 
                        }
                        if (!duplicate) {
                            appDao.insertRule(
                                RuleEntity(
                                    groupId = groupId,
                                    target = target,
                                    replacement = replacement,
                                    matchWord = matchWord,
                                    isForwardMatch = isForwardMatch,
                                    isEnabled = isEnabled
                                )
                            )
                        }
                    }
                }
                RuleCache.clear()
                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
                _toastEvent.emit("导入规则成功")
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
                _toastEvent.emit("导入规则失败: 格式错误")
            }
        }
    }

    fun testWebDavConnection(url: String, username: String, password: String, dirName: String = "", onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = WebDavHelper.testConnection(url, username, password, dirName)
            onResult(result)
        }
    }

    fun backupToWebDav(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                val settings = appDao.getSettings() ?: SettingsEntity()
                if (settings.webdavUrl.isBlank()) {
                    onResult(Result.failure(Exception("请先配置 WebDav 服务器地址")))
                    return@launch
                }
                val rulesStr = exportRulesToJsonString()
                val settingsStr = settings.toJsonString()
                val pkg = BackupPackage(version = 1, rulesJson = rulesStr, settingsJson = settingsStr)
                val jsonContent = pkg.toJsonString()

                val result = WebDavHelper.uploadFile(
                    url = settings.webdavUrl,
                    username = settings.webdavUsername,
                    password = settings.webdavPassword,
                    dirName = settings.webdavDir,
                    fileName = settings.webdavPath,
                    content = jsonContent
                )
                if (result.isSuccess) {
                    _toastEvent.emit("云端备份成功")
                }
                onResult(result)
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun restoreFromWebDav(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                val settings = appDao.getSettings() ?: SettingsEntity()
                if (settings.webdavUrl.isBlank()) {
                    onResult(Result.failure(Exception("请先配置 WebDav 服务器地址")))
                    return@launch
                }

                val downloadResult = WebDavHelper.downloadFile(
                    url = settings.webdavUrl,
                    username = settings.webdavUsername,
                    password = settings.webdavPassword,
                    dirName = settings.webdavDir,
                    fileName = settings.webdavPath
                )

                if (downloadResult.isSuccess) {
                    val content = downloadResult.getOrThrow()
                    val pkg = BackupPackage.fromJsonString(content)
                    if (pkg == null) {
                        onResult(Result.failure(Exception("备份文件格式解析错误")))
                        return@launch
                    }

                    val success = importBackupPackage(pkg)
                    if (success) {
                        _toastEvent.emit("云端恢复成功")
                        onResult(Result.success(Unit))
                    } else {
                        onResult(Result.failure(Exception("导入备份数据失败")))
                    }
                } else {
                    onResult(Result.failure(downloadResult.exceptionOrNull() ?: Exception("下载备份失败")))
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    suspend fun backupToLocalString(): String {
        return withContext(Dispatchers.IO) {
            try {
                val settings = appDao.getSettings() ?: SettingsEntity()
                val rulesStr = exportRulesToJsonString()
                val settingsStr = settings.toJsonString()
                val pkg = BackupPackage(version = 1, rulesJson = rulesStr, settingsJson = settingsStr)
                pkg.toJsonString()
            } catch (e: Exception) {
                ""
            }
        }
    }

    fun restoreFromLocalString(jsonStr: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                val pkg = BackupPackage.fromJsonString(jsonStr)
                if (pkg == null) {
                    onResult(Result.failure(Exception("备份文件格式解析错误")))
                    return@launch
                }
                val success = importBackupPackage(pkg)
                if (success) {
                    _toastEvent.emit("本地恢复成功")
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(Exception("导入备份数据失败")))
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    private suspend fun importBackupPackage(backup: BackupPackage): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Restore settings if available
                backup.settingsJson?.let { sJson ->
                    val newSettings = com.example.data.parseSettingsFromJson(sJson)
                    if (newSettings != null) {
                        appDao.saveSettings(newSettings.copy(id = 1))
                    }
                }

                // 2. Clear and restore rules
                val rulesStr = backup.rulesJson
                if (rulesStr.isNotBlank()) {
                    val jsonArray = org.json.JSONArray(rulesStr)
                    appDao.clearAllRules()
                    appDao.clearAllRuleGroups()

                    for (i in (jsonArray.length() - 1) downTo 0) {
                        val groupObj = jsonArray.getJSONObject(i)
                        val groupName = groupObj.getString("groupName")
                        val groupReplacement = groupObj.optString("groupReplacement", "")

                        val groupId = appDao.insertRuleGroup(RuleGroupEntity(name = groupName, replacement = groupReplacement))

                        val rulesArray = groupObj.optJSONArray("rules") ?: org.json.JSONArray()
                        for (j in (rulesArray.length() - 1) downTo 0) {
                            val ruleObj = rulesArray.getJSONObject(j)
                            val target = ruleObj.getString("target")
                            val replacement = ruleObj.getString("replacement")
                            val matchWord = ruleObj.optString("matchWord", "")
                            val isForwardMatch = ruleObj.optBoolean("isForwardMatch", true)
                            val isEnabled = ruleObj.optBoolean("isEnabled", true)

                            appDao.insertRule(
                                RuleEntity(
                                    groupId = groupId,
                                    target = target,
                                    replacement = replacement,
                                    matchWord = matchWord,
                                    isForwardMatch = isForwardMatch,
                                    isEnabled = isEnabled
                                )
                            )
                        }
                    }
                }
                RuleCache.clear()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    override fun onCleared() {
        stopTest()
        super.onCleared()
    }
}

class TtsViewModelFactory(private val context: Context, private val database: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TtsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TtsViewModel(context.applicationContext, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
