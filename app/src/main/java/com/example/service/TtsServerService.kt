package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.data.RuleProcessResult
import com.example.data.SettingsEntity
import com.example.data.TextRuleProcessor
import com.example.data.toHitsJsonString
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder
import java.util.Locale

class TtsServerService : Service() {

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)

    // 【关键改动1】：使用 Ktor 的 ApplicationEngine (CIO 引擎) 替代原生的 Socket/ServerSocket
    private var ktorServer: ApplicationEngine? = null
    private var currentPort: Int = -1

    private var activeTts: TextToSpeech? = null
    private var activeEnginePackage: String? = null
    private val consecutiveFailureCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val synthesisMutex = Mutex()
    private val pendingUtterances = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    // 保留并发限制信号量
    private val requestSemaphore = Semaphore(4)
    private var settingsJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "TtsServerChannel"
        private const val NOTIFICATION_ID = 2026

        const val ACTION_START_SERVER = "com.example.ACTION_START_SERVER"
        const val ACTION_STOP_SERVER = "com.example.ACTION_STOP_SERVER"

        private val _isServerRunning = MutableStateFlow(false)
        val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

        private val _serverPort = MutableStateFlow(8080)
        val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

        private val _activeEngine = MutableStateFlow("")
        val activeEngine: StateFlow<String> = _activeEngine.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVER
        if (action == ACTION_STOP_SERVER) {
            settingsJob?.cancel()
            settingsJob = null
            stopServer()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        // 立即调用 startForeground 满足 Android 8.0+ / 12+ / 14+ 前台服务 5 秒超时限制，防止闪退
        val initialPort = if (_serverPort.value > 0) _serverPort.value else 8080
        val initialEngineLabel = if (_activeEngine.value.isNotBlank()) getEngineLabel(_activeEngine.value) else "系统默认"
        val initialNotification = buildNotification(initialPort, initialEngineLabel)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.appDao().getSettingsFlow().collect { settingsOpt ->
                val settings = settingsOpt ?: SettingsEntity()

                _serverPort.value = settings.port
                _activeEngine.value = settings.targetEnginePackage

                // 【关键改动2】：配置改变时，检测端口是否变更，自动重启 Ktor 嵌入式 HTTP 服务器
                if (ktorServer != null && currentPort != settings.port) {
                    startHttpServer(settings.port)
                } else if (ktorServer == null) {
                    startHttpServer(settings.port)
                }

                withContext(Dispatchers.Main) {
                    val engineLabel = getEngineLabel(settings.targetEnginePackage)
                    val notification = buildNotification(settings.port, engineLabel)

                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, notification)
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        activeTts?.shutdown()
        job.cancel()
        super.onDestroy()
    }

    private fun getEngineLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    // 【关键改动3】：通过 Ktor embeddedServer 启动 CIO 引擎服务器，并注册 GET/POST 路由
    private fun startHttpServer(port: Int) {
        try {
            stopServer()
            currentPort = port

            val server = embeddedServer(CIO, port = port) {
                routing {
                    // 【关键改动4】：同时支持 GET/POST 请求以及 /、/tts 与 /api/tts 路径
                    get("/") { handleTtsRequest(call) }
                    post("/") { handleTtsRequest(call) }
                    get("/tts") { handleTtsRequest(call) }
                    post("/tts") { handleTtsRequest(call) }
                    get("/api/tts") { handleTtsRequest(call) }
                    post("/api/tts") { handleTtsRequest(call) }
                }
            }

            server.start(wait = false)
            ktorServer = server
            _isServerRunning.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            _isServerRunning.value = false
        }
    }

    private fun stopServer() {
        try {
            ktorServer?.stop(1000, 2000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ktorServer = null
        currentPort = -1
        _isServerRunning.value = false
    }

    // 【关键改动5】：核心请求处理逻辑全流程迁移至 Ktor ApplicationCall
    private suspend fun handleTtsRequest(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        var text = ""
        var enginePackage = ""

        try {
            requestSemaphore.withPermit {
                // 【关键改动6】：64KB 请求体大小限制拦截（优先检查 Header，超过则直接报 400）
                val contentLength = call.request.contentLength() ?: 0L
                if (contentLength > 65536) {
                    call.respondText("Request Body Too Large (Max 64KB)", status = HttpStatusCode.BadRequest)
                    return@withPermit
                }

                // 【关键改动7】：针对 GET 请求或空 Body，跳过 receiveText 防止 Ktor CIO 管道异常挂起或报错
                val requestBody = if (call.request.httpMethod == HttpMethod.Get || contentLength == 0L) {
                    ""
                } else {
                    withTimeoutOrNull(8000) {
                        try {
                            call.receiveText()
                        } catch (e: Throwable) {
                            ""
                        }
                    } ?: ""
                }

                if (requestBody.toByteArray(Charsets.UTF_8).size > 65536) {
                    call.respondText("Request Body Too Large (Max 64KB)", status = HttpStatusCode.BadRequest)
                    return@withPermit
                }

                // 【关键改动8】：保留完整的参数合并逻辑（Query 参数 + JSON Body / Form Body，保持优先级与别名兼容）
                val params = mutableMapOf<String, String>()
                call.request.queryParameters.forEach { key, values ->
                    if (values.isNotEmpty()) {
                        params[key] = values.first()
                    }
                }

                if (requestBody.isNotBlank()) {
                    val trimmedBody = requestBody.trim()
                    if (trimmedBody.startsWith("{")) {
                        try {
                            val json = org.json.JSONObject(trimmedBody)
                            val keys = json.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                params[k] = json.optString(k)
                            }
                        } catch (e: Throwable) {
                            // Not valid JSON
                        }
                    } else if (trimmedBody.contains("=")) {
                        params.putAll(parseQueryParams(trimmedBody))
                    } else {
                        // 如果 POST Body 直接是纯文本（阅读 App 常用配置），直接作为朗读文本
                        params["text"] = trimmedBody
                    }
                }

                // 字段别名完全保持一致
                text = params["text"] ?: params["key"] ?: params["t"] ?: params["txt"] ?: params["speakText"] ?: ""
                if (text.isEmpty()) {
                    call.respondText("Error: 'text' or 'key' parameter is required.", status = HttpStatusCode.BadRequest)
                    return@withPermit
                }

                val db = AppDatabase.getDatabase(applicationContext)
                val originalText = text
                val ruleResult = processTextRules(originalText, db)
                text = ruleResult.processedText
                val hitsJson = ruleResult.hits.toHitsJsonString()

                val settings = db.appDao().getSettings() ?: SettingsEntity()

                val rawRateStr = params["rate"] ?: params["speed"] ?: params["speakSpeed"] ?: params["speechRate"] ?: params["r"] ?: params["s"]
                val rawRate = rawRateStr?.toFloatOrNull() ?: settings.speechRate

                val rawPitchStr = params["pitch"] ?: params["speakPitch"] ?: params["p"]
                val rawPitch = rawPitchStr?.toFloatOrNull() ?: settings.pitch

                val rate = normalizeRate(rawRate)
                val pitch = normalizePitch(rawPitch)
                enginePackage = params["engine"] ?: params["e"] ?: settings.targetEnginePackage

                if (text.isBlank()) {
                    val duration = System.currentTimeMillis() - startTime
                    logToDatabase(originalText, enginePackage, "SUCCESS", duration, "Empty text after rule processing (returned silence audio)", hitsJson)
                    val silenceWav = getSilenceWav()
                    call.respondBytes(silenceWav, ContentType("audio", "wav"))
                    return@withPermit
                }

                val resultPair = withTimeoutOrNull(30000) {
                    synthesisMutex.withLock {
                        synthesizeText(text, rate, pitch, enginePackage)
                    }
                }
                val (audioFile, errorDetails) = resultPair ?: Pair(null, "Server synthesis queue timeout (30s)")

                if (audioFile == null || !audioFile.exists()) {
                    val duration = System.currentTimeMillis() - startTime
                    val errorMsg = errorDetails ?: "Synthesis failed for text: $text"
                    logToDatabase(originalText, enginePackage, "FAILED", duration, errorMsg, hitsJson)
                    call.respondText("Error: Failed to synthesize audio. ($errorMsg)", status = HttpStatusCode.InternalServerError)
                    return@withPermit
                }

                try {
                    val fileBytes = try {
                        audioFile.readBytes()
                    } finally {
                        try { audioFile.delete() } catch (e: Throwable) {}
                    }

                    if (fileBytes.size <= 44) {
                        val duration = System.currentTimeMillis() - startTime
                        logToDatabase(originalText, enginePackage, "FAILED", duration, "Audio file is empty or invalid header only (${fileBytes.size} bytes)", hitsJson)
                        call.respondText("Error: Synthesized audio is empty.", status = HttpStatusCode.InternalServerError)
                        return@withPermit
                    }

                    call.respondBytes(fileBytes, ContentType("audio", "wav"))

                    val duration = System.currentTimeMillis() - startTime
                    logToDatabase(originalText, enginePackage, "SUCCESS", duration, null, hitsJson)
                } catch (e: Throwable) {
                    val duration = System.currentTimeMillis() - startTime
                    if (e is kotlinx.coroutines.CancellationException || e.javaClass.name.contains("Channel") || e.javaClass.name.contains("Socket")) {
                        logToDatabase(originalText, enginePackage, "CANCELLED", duration, e.message, hitsJson)
                    } else {
                        logToDatabase(originalText, enginePackage, "FAILED", duration, e.message, hitsJson)
                    }
                }
            }
        } catch (e: Throwable) {
            val duration = System.currentTimeMillis() - startTime
            val stackTrace = android.util.Log.getStackTraceString(e)
            val errorMsg = "CRASH IN REQUEST: ${e.javaClass.name}: ${e.message}\n$stackTrace"
            e.printStackTrace()
            logToDatabase(text.ifEmpty { "Legado Request" }, enginePackage.ifEmpty { "System" }, "CRASH", duration, errorMsg, null)
            try {
                call.respondText("Error: Internal server processing exception (${e.message})", status = HttpStatusCode.InternalServerError)
            } catch (ex: Throwable) {}
        }
    }

    private var cachedSilenceWav: ByteArray? = null

    private fun getSilenceWav(): ByteArray {
        return cachedSilenceWav ?: generateSilenceWav().also { cachedSilenceWav = it }
    }

    private fun generateSilenceWav(durationMs: Int = 200, sampleRate: Int = 16000): ByteArray {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val dataSize = numSamples * blockAlign
        val totalSize = 36 + dataSize

        val header = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(numChannels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        return header.array()
    }

    private fun normalizeRate(rate: Float): Float {
        if (rate <= 0f) return 1.0f
        val calculated = when {
            rate in 0.1f..4.0f -> rate
            rate in 4.01f..40.0f -> rate / 10f
            rate > 40.0f -> rate / 100f
            else -> rate
        }
        return calculated.coerceIn(0.1f, 4.0f)
    }

    private fun normalizePitch(pitch: Float): Float {
        if (pitch <= 0f) return 1.0f
        val calculated = when {
            pitch in 0.1f..2.5f -> pitch
            pitch in 2.51f..25.0f -> pitch / 10f
            pitch > 25.0f -> pitch / 100f
            else -> pitch
        }
        return calculated.coerceIn(0.1f, 2.5f)
    }

    private fun setupTtsListener(tts: TextToSpeech) {
        try {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) {}

                override fun onDone(id: String) {
                    pendingUtterances.remove(id)?.complete(true)
                }

                override fun onError(id: String) {
                    pendingUtterances.remove(id)?.complete(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String, errorCode: Int) {
                    pendingUtterances.remove(id)?.complete(false)
                }
            })
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private suspend fun handleSynthesizeFailure(
        tempFile: File?,
        errorMsg: String
    ): Pair<File?, String?> {
        // 1. 尝试删除孤儿临时文件
        if (tempFile != null && tempFile.exists()) {
            try { tempFile.delete() } catch (e: Throwable) {}
        }

        // 2. 连续失败计数自增
        val failures = consecutiveFailureCount.incrementAndGet()
        var finalErrorMsg = errorMsg

        // 3. 当连续失败达到 3 次时，在 Main 线程主动 shutdown 并清空引擎实例
        if (failures >= 3) {
            val currentPkg = activeEnginePackage ?: ""
            withContext(Dispatchers.Main) {
                try {
                    activeTts?.shutdown()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                activeTts = null
                activeEnginePackage = null

                // 立即尝试重新构建 TTS 实例，避免后续排队请求遇到 null 而触发连续跳段
                if (currentPkg.isNotBlank()) {
                    try {
                        getTtsInstance(currentPkg)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
            consecutiveFailureCount.set(0)
            finalErrorMsg += " [检测到引擎连续 3 次合成失败，已自动重置并重新构建 TTS 引擎实例]"
            android.util.Log.w("TtsServerService", finalErrorMsg)
        }

        return Pair(null, finalErrorMsg)
    }

    private suspend fun synthesizeText(
        text: String,
        rate: Float,
        pitch: Float,
        enginePackage: String
    ): Pair<File?, String?> {
        var lastError: String? = null
        for (attempt in 1..2) {
            val (file, err) = performSingleSynthesis(text, rate, pitch, enginePackage)
            if (file != null && file.exists() && file.length() > 44) {
                consecutiveFailureCount.set(0)
                return Pair(file, null)
            }
            lastError = err
            if (attempt == 1) {
                kotlinx.coroutines.delay(200)
            }
        }
        return handleSynthesizeFailure(null, lastError ?: "Synthesized audio file missing or empty after retry")
    }

    private suspend fun performSingleSynthesis(
        text: String,
        rate: Float,
        pitch: Float,
        enginePackage: String
    ): Pair<File?, String?> {
        var errorDetails: String? = null

        val tts = try {
            getTtsInstance(enginePackage)
        } catch (e: Throwable) {
            errorDetails = "getTtsInstance exception: ${e.javaClass.name}: ${e.message}"
            null
        }

        if (tts == null) {
            return Pair(null, errorDetails ?: "TTS engine initialization failed for package '$enginePackage'")
        }

        val utteranceId = "tts_" + System.currentTimeMillis() + "_" + (1000..9999).random()
        val tempFile = File(cacheDir, "$utteranceId.wav")
        if (tempFile.exists()) {
            try { tempFile.delete() } catch (e: Throwable) {}
        }
        val deferredResult = CompletableDeferred<Boolean>()
        pendingUtterances[utteranceId] = deferredResult

        val bundle = Bundle()
        try {
            bundle.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        } catch (e: Throwable) {
            // ignore bundle write errors
        }

        val synthResult = withContext(Dispatchers.Main) {
            try {
                // 设置语言 (加 try-catch 保护)
                try {
                    val hasChinese = text.any { it.code in 0x4E00..0x9FFF }
                    if (hasChinese) {
                        val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts.setLanguage(Locale.CHINESE)
                        }
                    } else {
                        tts.setLanguage(Locale.getDefault())
                    }
                } catch (e: Throwable) {
                    if (errorDetails == null) {
                        errorDetails = "setLanguage error: ${e.javaClass.name}: ${e.message}"
                    }
                }

                // 设置语速和音调 (加 try-catch 保护)
                try {
                    tts.setSpeechRate(rate)
                } catch (e: Throwable) {
                    if (errorDetails == null) {
                        errorDetails = "setSpeechRate error: ${e.javaClass.name}: ${e.message}"
                    }
                }

                try {
                    tts.setPitch(pitch)
                } catch (e: Throwable) {
                    if (errorDetails == null) {
                        errorDetails = "setPitch error: ${e.javaClass.name}: ${e.message}"
                    }
                }

                // 合成音频文件 (加 try-catch 保护)
                tts.synthesizeToFile(text, bundle, tempFile, utteranceId)
            } catch (e: Throwable) {
                errorDetails = "synthesizeToFile exception: ${e.javaClass.name}: ${e.message}"
                TextToSpeech.ERROR
            }
        }

        if (synthResult == TextToSpeech.ERROR) {
            pendingUtterances.remove(utteranceId)
            try { tempFile.delete() } catch (e: Throwable) {}
            return Pair(null, errorDetails ?: "TextToSpeech.ERROR returned from synthesizeToFile")
        }

        // 使用 withTimeoutOrNull (15秒) 进行单次合成超时保护
        val waitResult = try {
            withTimeoutOrNull(15000) {
                deferredResult.await()
            }
        } catch (e: Throwable) {
            if (errorDetails == null) {
                errorDetails = "deferredResult await exception: ${e.javaClass.name}: ${e.message}"
            }
            null
        } finally {
            pendingUtterances.remove(utteranceId)
        }

        // 如果明确收到 onError 回调，直接报错并清理
        if (waitResult == false) {
            try { tempFile.delete() } catch (e: Throwable) {}
            return Pair(null, errorDetails ?: "TTS engine returned onError callback for utterance")
        }

        val fileHasData = tempFile.exists() && tempFile.length() > 44

        if (waitResult == null && !fileHasData) {
            // 超时时在 Main 线程主动调用 tts.stop()，打断并清空 TTS 引擎内部任务队列
            withContext(Dispatchers.Main) {
                try {
                    tts.stop()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            if (errorDetails == null) {
                errorDetails = "UtteranceListener timeout (15s), tts.stop() invoked"
            }
        }

        return if (tempFile.exists() && tempFile.length() > 44) {
            Pair(tempFile, null)
        } else {
            try { tempFile.delete() } catch (e: Throwable) {}
            Pair(null, errorDetails ?: "Synthesized audio file missing or empty (<=44 bytes)")
        }
    }

    private suspend fun getTtsInstance(enginePackage: String): TextToSpeech? = withContext(Dispatchers.Main) {
        try {
            if (activeTts != null && activeEnginePackage == enginePackage) {
                return@withContext activeTts
            }

            try {
                activeTts?.shutdown()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            activeTts = null
            activeEnginePackage = null

            val deferred = CompletableDeferred<Int>()
            var tts: TextToSpeech? = null

            if (enginePackage.isNotBlank()) {
                try {
                    tts = TextToSpeech(applicationContext, { status ->
                        try { deferred.complete(status) } catch (e: Throwable) {}
                    }, enginePackage)
                } catch (e: Throwable) {
                    tts = null
                    e.printStackTrace()
                }
            }

            var isFallback = false
            var status = if (tts != null) {
                try {
                    withTimeoutOrNull(5000) {
                        deferred.await()
                    } ?: TextToSpeech.ERROR
                } catch (e: Throwable) {
                    TextToSpeech.ERROR
                }
            } else {
                TextToSpeech.ERROR
            }

            if (status != TextToSpeech.SUCCESS) {
                isFallback = true
                try { tts?.shutdown() } catch (e: Throwable) {}

                val fallbackDeferred = CompletableDeferred<Int>()
                tts = try {
                    TextToSpeech(applicationContext) { s ->
                        try { fallbackDeferred.complete(s) } catch (e: Throwable) {}
                    }
                } catch (e: Throwable) {
                    null
                }

                status = if (tts != null) {
                    try {
                        withTimeoutOrNull(5000) {
                            fallbackDeferred.await()
                        } ?: TextToSpeech.ERROR
                    } catch (e: Throwable) {
                        TextToSpeech.ERROR
                    }
                } else {
                    TextToSpeech.ERROR
                }
            }

            if (status == TextToSpeech.SUCCESS && tts != null) {
                setupTtsListener(tts)
                activeTts = tts
                activeEnginePackage = if (isFallback) {
                    try { tts.defaultEngine ?: enginePackage } catch (e: Throwable) { enginePackage }
                } else {
                    enginePackage
                }
                tts
            } else {
                try { tts?.shutdown() } catch (e: Throwable) {}
                null
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        val params = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val rawKey = pair.substring(0, idx)
                val rawValue = pair.substring(idx + 1)

                val key = try {
                    URLDecoder.decode(rawKey, "UTF-8")
                } catch (e: Exception) {
                    rawKey
                }

                val value = try {
                    URLDecoder.decode(rawValue, "UTF-8")
                } catch (e: Exception) {
                    rawValue
                }

                params[key] = value
            }
        }
        return params
    }

    private suspend fun logToDatabase(
        text: String,
        engine: String,
        status: String,
        durationMs: Long,
        errorMsg: String? = null,
        hitsJson: String? = null
    ) {
        val database = AppDatabase.getDatabase(applicationContext)
        val history = HistoryEntity(
            text = if (text.length > 150) text.substring(0, 147) + "..." else text,
            length = text.length,
            enginePackage = engine,
            timestamp = System.currentTimeMillis(),
            status = status,
            durationMs = durationMs,
            errorMsg = errorMsg,
            hitsJson = hitsJson
        )
        database.appDao().insertHistory(history)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TTS转发器后台服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "提供本地TTS转发接口，以便阅读APP朗读小说"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(port: Int, engineName: String): Notification {
        val stopIntent = Intent(this, TtsServerService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openActivityIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TTS 转发器已启动")
            .setContentText("正在监听端口: $port | 引擎: $engineName")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止服务", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private suspend fun processTextRules(originalText: String, db: AppDatabase): RuleProcessResult {
        return TextRuleProcessor.process(originalText, db.appDao(), applicationContext)
    }
}
