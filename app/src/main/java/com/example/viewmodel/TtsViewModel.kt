package com.example.viewmodel

import java.io.File
import android.os.Build
import android.os.Environment
import android.content.ContentUris
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
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
import com.example.data.LocalBackupStorageHelper
import com.example.data.WebDavHelper
import com.example.data.BackupPackage
import com.example.data.WebDavConfigPackage
import com.example.data.CombinedBackupPackage
import com.example.data.toJsonString
import com.example.data.toRuleSettingsJsonString
import com.example.data.toWebDavConfigJsonString
import com.example.data.parseRuleSettingsFromJson
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
            // Ensure default local TTS backup directories exist
            LocalBackupStorageHelper.ensureLocalTtsDirs(context)

            // Pre-warm tables in background to avoid future lookup lag
            com.example.data.PolyphonicTable.init(context)
            com.example.data.DefaultReadingTable.init(context)

            val cacheCount = appDao.getPolyphoneCacheCount()
            _polyphoneCacheCount.value = cacheCount

            val existing = appDao.getSettings()
            if (existing == null) {
                appDao.saveSettings(SettingsEntity())
            }
            
            // Populate default or restored rules if there are no rule groups at all
            val existingGroups = appDao.getAllRuleGroups()
            if (existingGroups.isEmpty()) {
                // 1. 优先尝试从本地持久化备份（SharedPreferences / 下载目录 / 应用文档等）自动恢复
                val restoredLocally = tryRestoreFromLocalAutoBackup()
                if (!restoredLocally) {
                    // 2. 尝试从配置的 WebDAV 恢复
                    val settings = appDao.getSettings()
                    var restoredWebDav = false
                    if (settings != null && settings.webdavUrl.isNotBlank()) {
                        val rulesFileName = if (settings.webdavPath.isNotBlank()) settings.webdavPath else "tts_rules_backup.json"
                        val downloadRulesResult = WebDavHelper.downloadFile(
                            url = settings.webdavUrl,
                            username = settings.webdavUsername,
                            password = settings.webdavPassword,
                            dirName = settings.webdavDir,
                            fileName = rulesFileName
                        )
                        if (downloadRulesResult.isSuccess) {
                            val content = downloadRulesResult.getOrThrow()
                            restoredWebDav = tryParseAndImportBackup(content)
                        }
                    }
                    if (!restoredWebDav) {
                        setupDefaultReferenceRules()
                    }
                }
            } else {
                // 用户已有规则：严禁删减或修改！完整保留用户建立的所有规则，并更新多层本地持久化备份副本
                saveAutoBackupToExternalStorage()
            }
        }
    }

    suspend fun setupDefaultReferenceRules() {
        // 1. 分组: "重-虫"，只保留一个规则
        val group1Id = appDao.insertRuleGroup(RuleGroupEntity(name = "重-虫", replacement = ""))
        appDao.insertRule(
            RuleEntity(
                groupId = group1Id,
                target = "一重",
                replacement = "一虫",
                matchWord = "",
                isForwardMatch = true,
                isEnabled = true
            )
        )

        // 2. 分组: "重-众"，只保留一个规则
        val group2Id = appDao.insertRuleGroup(RuleGroupEntity(name = "重-众", replacement = ""))
        appDao.insertRule(
            RuleEntity(
                groupId = group2Id,
                target = "重要",
                replacement = "众要",
                matchWord = "",
                isForwardMatch = true,
                isEnabled = true
            )
        )

        RuleCache.clear()
    }

    fun resetToDefaultTemplate() {
        viewModelScope.launch(Dispatchers.IO) {
            appDao.clearAllRules()
            appDao.clearAllRuleGroups()
            setupDefaultReferenceRules()
            RuleCache.clear()
            _toastEvent.emit("已恢复默认规则（重-虫、重-众）")
        }
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
            val processedText = TextRuleProcessor.process(text, appDao, context).processedText

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
            com.example.util.CrashHandler.clearCrashLogs(context)
            _toastEvent.emit("所有请求日志和崩溃记录已清空")
        }
    }

    private fun buildLegadoConfigJson(ip: String, port: Int): String {
        val requestOptions = org.json.JSONObject().apply {
            put("method", "POST")
            put("headers", org.json.JSONObject().apply {
                put("Content-Type", "text/plain; charset=utf-8")
            })
            put("body", "text={{java.encodeURI(speakText)}}")
        }.toString().replace("\\/", "/")

        return org.json.JSONObject().apply {
            put("name", "TTS转发")
            put("url", "http://${ip}:${port}/api/tts,$requestOptions")
            put("contentType", "audio/wav")
            put("concurrentRate", "100")
            put("id", System.currentTimeMillis())
        }.toString(2).replace("\\/", "/")
    }

    fun copyLegadoConfig(context: Context, ipType: String = "127.0.0.1") {
        viewModelScope.launch {
            val settings = settingsState.value
            val ip = if (ipType == "127.0.0.1") "127.0.0.1" else getWifiIpAddress(context)
            val json = buildLegadoConfigJson(ip, settings.port)

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
            val json = buildLegadoConfigJson(ip, settings.port)

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
            saveAutoBackupToExternalStorage()
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
                saveAutoBackupToExternalStorage()
                _toastEvent.emit("修改分组成功")
            }
        }
    }

    fun deleteRuleGroup(groupId: Long) {
        viewModelScope.launch {
            appDao.deleteRuleGroupById(groupId)
            appDao.deleteRulesByGroupId(groupId)
            RuleCache.clear()
            saveAutoBackupToExternalStorage()
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
            saveAutoBackupToExternalStorage()
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
                saveAutoBackupToExternalStorage()
                _toastEvent.emit("修改规则成功")
            }
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            appDao.deleteRuleById(ruleId)
            RuleCache.clear()
            saveAutoBackupToExternalStorage()
            _toastEvent.emit("删除规则成功")
        }
    }

    fun clearAllRules() {
        viewModelScope.launch {
            appDao.clearAllRuleGroups()
            appDao.clearAllRules()
            RuleCache.clear()
            saveAutoBackupToExternalStorage()
            _toastEvent.emit("所有发音与替换规则已清空")
        }
    }

    fun toggleRuleEnabled(rule: RuleEntity) {
        viewModelScope.launch {
            appDao.insertRule(rule.copy(isEnabled = !rule.isEnabled))
            RuleCache.clear()
            saveAutoBackupToExternalStorage()
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
                saveAutoBackupToExternalStorage()
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

                // 1. 上传规则配置文件 (仅包含替换规则)
                val rulesStr = exportRulesToJsonString()
                val rulesPkg = BackupPackage(version = 1, rulesJson = rulesStr)
                val rulesFileName = if (settings.webdavPath.isNotBlank()) settings.webdavPath else "tts_rules_backup.json"
                val rulesResult = WebDavHelper.uploadFile(
                    url = settings.webdavUrl,
                    username = settings.webdavUsername,
                    password = settings.webdavPassword,
                    dirName = settings.webdavDir,
                    fileName = rulesFileName,
                    content = rulesPkg.toJsonString()
                )
                if (rulesResult.isFailure) {
                    onResult(rulesResult)
                    return@launch
                }

                // 2. 上传 WebDAV 配置文件
                val webdavPkg = WebDavConfigPackage(
                    version = 1,
                    webdavUrl = settings.webdavUrl,
                    webdavUsername = settings.webdavUsername,
                    webdavPassword = settings.webdavPassword,
                    webdavDir = settings.webdavDir,
                    webdavPath = settings.webdavPath
                )
                val webdavResult = WebDavHelper.uploadFile(
                    url = settings.webdavUrl,
                    username = settings.webdavUsername,
                    password = settings.webdavPassword,
                    dirName = settings.webdavDir,
                    fileName = "webdav_config.json",
                    content = webdavPkg.toJsonString()
                )

                if (webdavResult.isSuccess) {
                    _toastEvent.emit("云端备份成功")
                    onResult(Result.success(Unit))
                } else {
                    onResult(webdavResult)
                }
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

                var restoredRules = false
                var restoredWebDav = false

                // 1. 下载恢复规则配置文件
                val rulesFileName = if (settings.webdavPath.isNotBlank()) settings.webdavPath else "tts_rules_backup.json"
                val downloadRulesResult = WebDavHelper.downloadFile(
                    url = settings.webdavUrl,
                    username = settings.webdavUsername,
                    password = settings.webdavPassword,
                    dirName = settings.webdavDir,
                    fileName = rulesFileName
                )
                if (downloadRulesResult.isSuccess) {
                    val content = downloadRulesResult.getOrThrow()
                    val pkg = BackupPackage.fromJsonString(content)
                    if (pkg != null) {
                        restoredRules = importBackupPackage(pkg)
                    }
                }

                // 2. 下载恢复 WebDAV 配置文件
                val currentSettings = appDao.getSettings() ?: SettingsEntity()
                val downloadWebDavResult = WebDavHelper.downloadFile(
                    url = currentSettings.webdavUrl,
                    username = currentSettings.webdavUsername,
                    password = currentSettings.webdavPassword,
                    dirName = currentSettings.webdavDir,
                    fileName = "webdav_config.json"
                )
                if (downloadWebDavResult.isSuccess) {
                    val content = downloadWebDavResult.getOrThrow()
                    val pkg = WebDavConfigPackage.fromJsonString(content)
                    if (pkg != null) {
                        val updated = currentSettings.copy(
                            webdavUrl = pkg.webdavUrl,
                            webdavUsername = pkg.webdavUsername,
                            webdavPassword = pkg.webdavPassword,
                            webdavDir = pkg.webdavDir,
                            webdavPath = pkg.webdavPath
                        )
                        appDao.saveSettings(updated)
                        restoredWebDav = true
                    }
                }

                if (restoredRules || restoredWebDav) {
                    _toastEvent.emit("云端恢复成功")
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(Exception("下载或解析云端备份失败")))
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
                val rulesPkg = BackupPackage(version = 1, rulesJson = rulesStr)
                val webdavPkg = WebDavConfigPackage(
                    version = 1,
                    webdavUrl = settings.webdavUrl,
                    webdavUsername = settings.webdavUsername,
                    webdavPassword = settings.webdavPassword,
                    webdavDir = settings.webdavDir,
                    webdavPath = settings.webdavPath
                )
                val combined = CombinedBackupPackage(
                    version = 1,
                    rulesPackage = rulesPkg,
                    webdavPackage = webdavPkg
                )
                combined.toJsonString()
            } catch (e: Exception) {
                ""
            }
        }
    }

    fun backupToLocalDefaultDir(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val saveResult = withContext(Dispatchers.IO) {
                    LocalBackupStorageHelper.ensureLocalTtsDirs(context)
                    val jsonContent = backupToLocalString()
                    if (jsonContent.isBlank()) {
                        Result.failure(Exception("生成备份配置失败"))
                    } else {
                        LocalBackupStorageHelper.saveToTtsDirectory(
                            context = context,
                            content = jsonContent,
                            fileName = LocalBackupStorageHelper.BACKUP_FILE_NAME
                        )
                    }
                }
                if (saveResult.isSuccess) {
                    val path = saveResult.getOrThrow()
                    _toastEvent.emit("已覆盖备份至 $path")
                }
                onResult(saveResult)
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun restoreFromLocalDefaultDir(
        onNotFound: () -> Unit,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    LocalBackupStorageHelper.ensureLocalTtsDirs(context)
                    LocalBackupStorageHelper.readFromTtsDirectory(
                        context = context,
                        preferredFileNames = listOf(
                            LocalBackupStorageHelper.BACKUP_FILE_NAME,
                            LocalBackupStorageHelper.AUTO_BACKUP_FILE_NAME
                        )
                    )
                }
                if (found == null) {
                    onNotFound()
                    return@launch
                }
                val (content, source) = found
                restoreFromLocalString(content) { restoreResult ->
                    if (restoreResult.isSuccess) {
                        onResult(Result.success(source))
                    } else {
                        onResult(Result.failure(restoreResult.exceptionOrNull() ?: Exception("恢复配置失败")))
                    }
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun restoreFromLocalString(jsonStr: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                var restored = false

                // 1. 优先尝试作为 CombinedBackupPackage 完整合并恢复
                val combinedPkg = CombinedBackupPackage.fromJsonString(jsonStr)
                if (combinedPkg != null) {
                    if (combinedPkg.rulesPackage != null) {
                        restored = importBackupPackage(combinedPkg.rulesPackage) || restored
                    }
                    if (combinedPkg.webdavPackage != null) {
                        val currentSettings = appDao.getSettings() ?: SettingsEntity()
                        val updated = currentSettings.copy(
                            webdavUrl = combinedPkg.webdavPackage.webdavUrl,
                            webdavUsername = combinedPkg.webdavPackage.webdavUsername,
                            webdavPassword = combinedPkg.webdavPackage.webdavPassword,
                            webdavDir = combinedPkg.webdavPackage.webdavDir,
                            webdavPath = combinedPkg.webdavPackage.webdavPath
                        )
                        appDao.saveSettings(updated)
                        restored = true
                    }
                    if (restored) {
                        _toastEvent.emit("完整备份恢复成功")
                        onResult(Result.success(Unit))
                        return@launch
                    }
                }

                // 2. 尝试作为单独的规则配置文件恢复
                val rulesPkg = BackupPackage.fromJsonString(jsonStr)
                if (rulesPkg != null) {
                    val success = importBackupPackage(rulesPkg)
                    if (success) {
                        _toastEvent.emit("规则配置恢复成功")
                        onResult(Result.success(Unit))
                    } else {
                        onResult(Result.failure(Exception("导入规则备份数据失败")))
                    }
                    return@launch
                }

                // 3. 尝试作为单独的 WebDAV 配置文件恢复
                val webdavPkg = WebDavConfigPackage.fromJsonString(jsonStr)
                if (webdavPkg != null) {
                    val settings = appDao.getSettings() ?: SettingsEntity()
                    val updated = settings.copy(
                        webdavUrl = webdavPkg.webdavUrl,
                        webdavUsername = webdavPkg.webdavUsername,
                        webdavPassword = webdavPkg.webdavPassword,
                        webdavDir = webdavPkg.webdavDir,
                        webdavPath = webdavPkg.webdavPath
                    )
                    appDao.saveSettings(updated)
                    _toastEvent.emit("WebDAV 配置恢复成功")
                    onResult(Result.success(Unit))
                    return@launch
                }

                onResult(Result.failure(Exception("配置文件格式无法解析")))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    private suspend fun tryParseAndImportBackup(content: String): Boolean {
        if (content.isBlank()) return false
        try {
            // 1. 尝试作为 CombinedBackupPackage
            val combined = CombinedBackupPackage.fromJsonString(content)
            if (combined?.rulesPackage != null) {
                val res = importBackupPackageInternal(combined.rulesPackage)
                if (res) return true
            }

            // 2. 尝试作为 BackupPackage
            val pkg = BackupPackage.fromJsonString(content)
            if (pkg != null && pkg.rulesJson.isNotBlank() && pkg.rulesJson != "[]") {
                val res = importBackupPackageInternal(pkg)
                if (res) return true
            }

            // 3. 尝试作为原始 JSON 数组
            val trimmed = content.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val rawPkg = BackupPackage(version = 1, rulesJson = trimmed)
                val res = importBackupPackageInternal(rawPkg)
                if (res) return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private suspend fun saveAutoBackupToExternalStorage() {
        withContext(Dispatchers.IO) {
            try {
                val jsonStr = exportRulesToJsonString()
                if (jsonStr.isBlank() || jsonStr == "[]") return@withContext
                val pkgStr = BackupPackage(version = 1, rulesJson = jsonStr).toJsonString()

                // 1. 保存到 SharedPreferences（应用进程内最稳定，同时随系统云备份与设备换机迁移）
                try {
                    val prefs = context.getSharedPreferences("tts_persistent_backup", Context.MODE_PRIVATE)
                    prefs.edit().putString("auto_backup_rules", pkgStr).apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. 直接覆盖保存到本地 TTS 目录（Download/TTS/ 与应用 TTS 目录）
                LocalBackupStorageHelper.saveToTtsDirectory(
                    context = context,
                    content = pkgStr,
                    fileName = LocalBackupStorageHelper.AUTO_BACKUP_FILE_NAME
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun tryRestoreFromLocalAutoBackup(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 检查 SharedPreferences 持久化备份
                try {
                    val prefs = context.getSharedPreferences("tts_persistent_backup", Context.MODE_PRIVATE)
                    val prefContent = prefs.getString("auto_backup_rules", null)
                    if (!prefContent.isNullOrBlank()) {
                        val restored = tryParseAndImportBackup(prefContent)
                        if (restored) return@withContext true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. 优先检查本地 TTS 目录（Download/TTS/、应用 TTS 目录）
                try {
                    val ttsDirBackup = LocalBackupStorageHelper.readFromTtsDirectory(
                        context = context,
                        preferredFileNames = listOf(
                            LocalBackupStorageHelper.AUTO_BACKUP_FILE_NAME,
                            LocalBackupStorageHelper.BACKUP_FILE_NAME
                        )
                    )
                    if (ttsDirBackup != null) {
                        val restored = tryParseAndImportBackup(ttsDirBackup.first)
                        if (restored) return@withContext true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 3. 兼容旧版本：检查应用内部私有文件根目录
                try {
                    val internalFile = File(context.filesDir, "tts_rules_auto_backup.json")
                    if (internalFile.exists() && internalFile.length() > 0) {
                        val content = internalFile.readText(Charsets.UTF_8)
                        val restored = tryParseAndImportBackup(content)
                        if (restored) return@withContext true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 3. 检查应用专属外部存储
                try {
                    val extFilesDir = context.getExternalFilesDir(null)
                    if (extFilesDir != null) {
                        val candidates = listOf(
                            File(extFilesDir, "tts_rules_auto_backup.json"),
                            File(extFilesDir, "tts_rules_backup.json")
                        )
                        for (file in candidates) {
                            if (file.exists() && file.length() > 0) {
                                val content = file.readText(Charsets.UTF_8)
                                val restored = tryParseAndImportBackup(content)
                                if (restored) return@withContext true
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 4. 检查 MediaStore 外部下载目录（跨重装保留）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val resolver = context.contentResolver
                        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
                        val selectionArgs = arrayOf("%tts%backup%.json")
                        resolver.query(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            projection,
                            selection,
                            selectionArgs,
                            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                        )?.use { cursor ->
                            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                            while (cursor.moveToNext()) {
                                val id = cursor.getLong(idCol)
                                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                                try {
                                    resolver.openInputStream(uri)?.use { stream ->
                                        val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                                        val restored = tryParseAndImportBackup(content)
                                        if (restored) return@withContext true
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 5. 检查公开 Download 与 Documents 目录
                val publicDirs = listOfNotNull(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                )
                val candidateNames = listOf(
                    "tts_rules_auto_backup.json",
                    "tts_rules_backup.json",
                    "TTS_Rules_Backup.json",
                    "TTS_Forwarder_Backup.json"
                )
                for (dir in publicDirs) {
                    if (!dir.exists() || !dir.isDirectory) continue
                    for (name in candidateNames) {
                        val file = File(dir, name)
                        if (file.exists() && file.length() > 0) {
                            try {
                                val content = file.readText(Charsets.UTF_8)
                                val restored = tryParseAndImportBackup(content)
                                if (restored) return@withContext true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    try {
                        val matchingFiles = dir.listFiles { f ->
                            f.isFile && (f.name.startsWith("TTS_Forwarder_Backup") || f.name.contains("tts_rules")) && f.name.endsWith(".json")
                        }
                        matchingFiles?.sortByDescending { it.lastModified() }
                        matchingFiles?.forEach { file ->
                            if (file.length() > 0) {
                                val content = file.readText(Charsets.UTF_8)
                                val restored = tryParseAndImportBackup(content)
                                if (restored) return@withContext true
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun importBackupPackageInternal(backup: BackupPackage): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val rulesStr = backup.rulesJson
                if (rulesStr.isNotBlank() && rulesStr != "[]") {
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

    private suspend fun importBackupPackage(backup: BackupPackage): Boolean {
        val result = importBackupPackageInternal(backup)
        if (result) {
            saveAutoBackupToExternalStorage()
        }
        return result
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
