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

class TtsViewModel(private val database: AppDatabase) : ViewModel() {

    private val appDao = database.appDao()

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

    private val _engines = MutableStateFlow<List<TtsEngineInfo>>(emptyList())
    val engines: StateFlow<List<TtsEngineInfo>> = _engines.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var testTts: TextToSpeech? = null

    init {
        // Initialize default settings if not exists
        viewModelScope.launch {
            val existing = appDao.getSettings()
            if (existing == null) {
                appDao.saveSettings(SettingsEntity())
            }
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
            
            // If server is running, restart it to apply new settings
            if (TtsServerService.isServerRunning.value) {
                // Restart service
                val context = database.appDao().getSettings() // dummy context usage or pass context, but we will call from UI or restart manually
            }
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
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "test_utterance")
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
              "name": "TTS转发 [跟随App选择]",
              "url": "http://${ip}:${settings.port}/api/tts?text={{java.encodeURI(speakText)}}&rate={{speakSpeed}}",
              "contentType": "audio/wav",
              "id": ${System.currentTimeMillis()}
            }
            """.trimIndent()

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Legado TTS Config", json)
            clipboard.setPrimaryClip(clip)
            _toastEvent.emit("Legado配置已复制到剪贴板，可在阅读App中一键导入。")
        }
    }

    fun importToLegado(context: Context, ipType: String = "127.0.0.1") {
        viewModelScope.launch {
            val settings = settingsState.value
            val ip = if (ipType == "127.0.0.1") "127.0.0.1" else getWifiIpAddress(context)
            
            val json = """
            {
              "name": "TTS转发 [跟随App选择]",
              "url": "http://${ip}:${settings.port}/api/tts?text={{java.encodeURI(speakText)}}&rate={{speakSpeed}}",
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

    override fun onCleared() {
        stopTest()
        super.onCleared()
    }
}

class TtsViewModelFactory(private val database: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TtsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TtsViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
