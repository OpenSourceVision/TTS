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
import com.example.data.SettingsEntity
import com.example.data.TextRuleProcessor
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveText
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
    private val synthesisMutex = Mutex()
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
            stopSelf()
            return START_NOT_STICKY
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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startForeground(
                                NOTIFICATION_ID,
                                notification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                            )
                        } else {
                            startForeground(
                                NOTIFICATION_ID,
                                notification
                            )
                        }
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
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
                    // 【关键改动4】：同时支持 GET/POST 请求以及 /tts 与 /api/tts 两个路径
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

        requestSemaphore.withPermit {
            // 【关键改动6】：64KB 请求体大小限制拦截（优先检查 Header，超过则直接报 400）
            val contentLength = call.request.contentLength() ?: 0L
            if (contentLength > 65536) {
                call.respondText("Request Body Too Large (Max 64KB)", status = HttpStatusCode.BadRequest)
                return@withPermit
            }

            // 【关键改动7】：8 秒超时读取请求体，防止挂起
            val requestBody = withTimeoutOrNull(8000) {
                try {
                    call.receiveText()
                } catch (e: Exception) {
                    ""
                }
            } ?: ""

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
                    } catch (e: Exception) {
                        // Not valid JSON
                    }
                } else {
                    params.putAll(parseQueryParams(trimmedBody))
                }
            }

            // 字段别名完全保持一致
            text = params["text"] ?: params["key"] ?: params["t"] ?: params["txt"] ?: ""
            if (text.isEmpty()) {
                call.respondText("Error: 'text' or 'key' parameter is required.", status = HttpStatusCode.BadRequest)
                return@withPermit
            }

            val db = AppDatabase.getDatabase(applicationContext)
            val originalText = text
            text = processTextRules(originalText, db)

            val settings = db.appDao().getSettings() ?: SettingsEntity()

            val rawRateStr = params["rate"] ?: params["speed"] ?: params["speakSpeed"] ?: params["speechRate"] ?: params["r"] ?: params["s"]
            val rawRate = rawRateStr?.toFloatOrNull() ?: settings.speechRate

            val rawPitchStr = params["pitch"] ?: params["speakPitch"] ?: params["p"]
            val rawPitch = rawPitchStr?.toFloatOrNull() ?: settings.pitch

            val rate = normalizeRate(rawRate)
            val pitch = normalizePitch(rawPitch)
            enginePackage = params["engine"] ?: params["e"] ?: settings.targetEnginePackage

            val sentences = splitTextIntoSentences(text)
            if (sentences.isEmpty()) {
                call.respondText("Error: Empty text after rule processing.", status = HttpStatusCode.BadRequest)
                return@withPermit
            }

            // 【关键改动9】：第一句单独处理。在开启响应流之前尝试合成第一句。
            // 只有当“第一句”合成失败时，才允许返回 4xx/5xx HTTP 错误状态码。
            val firstSentence = sentences[0]
            val firstAudioFile = synthesisMutex.withLock {
                synthesizeText(firstSentence, rate, pitch, enginePackage)
            }

            if (firstAudioFile == null || !firstAudioFile.exists()) {
                val duration = System.currentTimeMillis() - startTime
                logToDatabase(originalText, enginePackage, "FAILED", duration, "First sentence synthesis failed: $firstSentence")
                call.respondText("Error: Failed to synthesize initial audio.", status = HttpStatusCode.InternalServerError)
                return@withPermit
            }

            // 【关键改动10】：第一句成功后，使用 Ktor 的 respondBytesWriter 原生流式输出（Content-Type: audio/wav）。
            // 彻底摒弃手写的 16 进制 Chunk 长度和 CRLF 块控制符，从根本上解决流污染问题。
            try {
                call.respondBytesWriter(contentType = ContentType("audio", "wav")) {
                    // 推送第一句音频（0 偏移，保留完整 44 字节 WAV 头）
                    try {
                        FileInputStream(firstAudioFile).use { input ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (bytesRead > 0) {
                                    writeFully(buffer, 0, bytesRead)
                                    flush()
                                }
                            }
                        }
                    } finally {
                        firstAudioFile.delete()
                    }

                    // 【关键改动11】：后续单句合成失败容错处理。
                    // 一旦已经开始写响应体，后续任何句子失败都只记录 SENTENCE_FAILED 日志并“跳过该句”，
                    // 绝不再尝试写入状态行或错误文本，保证推流数据的干净完整。
                    var hasSentenceErrors = false
                    for (i in 1 until sentences.size) {
                        val sentence = sentences[i]
                        val audioFile = synthesisMutex.withLock {
                            synthesizeText(sentence, rate, pitch, enginePackage)
                        }

                        if (audioFile == null || !audioFile.exists()) {
                            hasSentenceErrors = true
                            logToDatabase(
                                text = sentence,
                                engine = enginePackage,
                                status = "SENTENCE_FAILED",
                                durationMs = 0,
                                errorMsg = "Failed to synthesize sentence ${i + 1}: $sentence"
                            )
                            // 跳过失败句，继续下一句
                            continue
                        }

                        try {
                            // 后续句子跳过 WAV 44 字节头
                            val wavDataOffset = getWavDataOffset(audioFile)
                            FileInputStream(audioFile).use { input ->
                                if (wavDataOffset > 0) {
                                    val skipped = input.skip(wavDataOffset.toLong())
                                    if (skipped < wavDataOffset) {
                                        val diff = wavDataOffset - skipped.toInt()
                                        if (diff > 0) {
                                            input.read(ByteArray(diff))
                                        }
                                    }
                                }

                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    if (bytesRead > 0) {
                                        writeFully(buffer, 0, bytesRead)
                                        flush()
                                    }
                                }
                            }
                        } finally {
                            audioFile.delete()
                        }
                    }

                    val duration = System.currentTimeMillis() - startTime
                    val finalStatus = if (hasSentenceErrors) "PARTIAL_SUCCESS" else "SUCCESS"
                    logToDatabase(originalText, enginePackage, finalStatus, duration)
                }
            } catch (e: Exception) {
                // 网络中断或客户端主动断开
                val duration = System.currentTimeMillis() - startTime
                logToDatabase(originalText, enginePackage, "FAILED", duration, e.message)
            }
        }
    }

    private fun normalizeRate(rate: Float): Float {
        if (rate <= 0f) return 1.0f
        val calculated = when {
            rate in 0.1f..4.0f -> rate
            rate in 5.0f..40.0f -> rate / 10f
            rate >= 50.0f -> rate / 100f
            else -> rate
        }
        return calculated.coerceIn(0.1f, 4.0f)
    }

    private fun normalizePitch(pitch: Float): Float {
        if (pitch <= 0f) return 1.0f
        val calculated = when {
            pitch in 0.1f..2.0f -> pitch
            pitch in 5.0f..20.0f -> pitch / 10f
            pitch >= 50.0f -> pitch / 100f
            else -> pitch
        }
        return calculated.coerceIn(0.1f, 2.0f)
    }

    private suspend fun synthesizeText(
        text: String,
        rate: Float,
        pitch: Float,
        enginePackage: String
    ): File? {
        val tts = getTtsInstance(enginePackage) ?: return null

        withContext(Dispatchers.Main) {
            val hasChinese = text.any { it.code in 0x4E00..0x9FFF }
            if (hasChinese) {
                val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.CHINESE)
                }
            } else {
                tts.setLanguage(Locale.getDefault())
            }
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
        }

        val utteranceId = "tts_" + System.currentTimeMillis() + "_" + (1000..9999).random()
        val tempFile = File(cacheDir, "$utteranceId.wav")
        try {
            if (!tempFile.exists()) {
                tempFile.createNewFile()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val deferredResult = CompletableDeferred<Boolean>()

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {
            }

            override fun onDone(id: String) {
                if (id == utteranceId) {
                    deferredResult.complete(true)
                }
            }

            override fun onError(id: String) {
                if (id == utteranceId) {
                    deferredResult.complete(false)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String, errorCode: Int) {
                if (id == utteranceId) {
                    deferredResult.complete(false)
                }
            }
        })

        val bundle = Bundle()
        bundle.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

        val synthResult = withContext(Dispatchers.Main) {
            tts.synthesizeToFile(text, bundle, tempFile, utteranceId)
        }

        if (synthResult == TextToSpeech.ERROR) {
            return null
        }

        // 使用 withTimeoutOrNull (10秒) 进行单次合成超时保护
        val success = try {
            val waitResult = withTimeoutOrNull(10000) {
                deferredResult.await()
            }
            waitResult == true || (tempFile.exists() && tempFile.length() > 0)
        } catch (e: Exception) {
            tempFile.exists() && tempFile.length() > 0
        }

        return if (success && tempFile.exists()) tempFile else null
    }

    private suspend fun getTtsInstance(enginePackage: String): TextToSpeech? = withContext(Dispatchers.Main) {
        if (activeTts != null && activeEnginePackage == enginePackage) {
            return@withContext activeTts
        }

        activeTts?.shutdown()
        activeTts = null
        activeEnginePackage = null

        val deferred = CompletableDeferred<Int>()
        var tts = TextToSpeech(applicationContext, { status ->
            deferred.complete(status)
        }, enginePackage)

        var isFallback = false
        var status = try {
            withTimeoutOrNull(5000) {
                deferred.await()
            } ?: TextToSpeech.ERROR
        } catch (e: Exception) {
            TextToSpeech.ERROR
        }

        if (status != TextToSpeech.SUCCESS) {
            isFallback = true
            try { tts.shutdown() } catch (e: Exception) {}

            val fallbackDeferred = CompletableDeferred<Int>()
            tts = TextToSpeech(applicationContext, { s ->
                fallbackDeferred.complete(s)
            })

            status = try {
                withTimeoutOrNull(5000) {
                    fallbackDeferred.await()
                } ?: TextToSpeech.ERROR
            } catch (e: Exception) {
                TextToSpeech.ERROR
            }
        }

        if (status == TextToSpeech.SUCCESS) {
            activeTts = tts
            activeEnginePackage = if (isFallback) (tts.defaultEngine ?: enginePackage) else enginePackage
            tts
        } else {
            try { tts.shutdown() } catch (e: Exception) {}
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
        errorMsg: String? = null
    ) {
        val database = AppDatabase.getDatabase(applicationContext)
        val history = HistoryEntity(
            text = if (text.length > 150) text.substring(0, 147) + "..." else text,
            length = text.length,
            enginePackage = engine,
            timestamp = System.currentTimeMillis(),
            status = status,
            durationMs = durationMs,
            errorMsg = errorMsg
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

    private fun splitTextIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val maxLength = 2000
        if (text.length <= maxLength) {
            return listOf(text.trim())
        }

        val result = mutableListOf<String>()
        val paragraphs = text.split("\n")
        val currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue

            if (currentChunk.isNotEmpty() && currentChunk.length + trimmed.length + 1 > maxLength) {
                result.add(currentChunk.toString())
                currentChunk.clear()
            }

            if (trimmed.length > maxLength) {
                if (currentChunk.isNotEmpty()) {
                    result.add(currentChunk.toString())
                    currentChunk.clear()
                }
                var start = 0
                while (start < trimmed.length) {
                    val end = if (start + maxLength < trimmed.length) start + maxLength else trimmed.length
                    result.add(trimmed.substring(start, end))
                    start = end
                }
            } else {
                if (currentChunk.isNotEmpty()) {
                    currentChunk.append("\n")
                }
                currentChunk.append(trimmed)
            }
        }
        if (currentChunk.isNotEmpty()) {
            result.add(currentChunk.toString())
        }
        return if (result.isEmpty()) listOf(text.trim()) else result
    }

    private fun getWavDataOffset(file: File): Int {
        if (!file.exists() || file.length() < 44) return 0
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(44)
                val read = fis.read(header)
                if (read >= 12 &&
                    header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() && header[9] == 'A'.code.toByte() && header[10] == 'V'.code.toByte() && header[11] == 'E'.code.toByte()) {

                    if (read >= 44 && header[36] == 'd'.code.toByte() && header[37] == 'a'.code.toByte() && header[38] == 't'.code.toByte() && header[39] == 'a'.code.toByte()) {
                        return 44
                    }

                    fis.close()
                    FileInputStream(file).use { fis2 ->
                        val buffer = ByteArray(400)
                        val bytesRead = fis2.read(buffer)
                        for (idx in 12 until bytesRead - 8) {
                            if (buffer[idx] == 'd'.code.toByte() && buffer[idx+1] == 'a'.code.toByte() && buffer[idx+2] == 't'.code.toByte() && buffer[idx+3] == 'a'.code.toByte()) {
                                return idx + 8
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 44
    }

    private suspend fun processTextRules(originalText: String, db: AppDatabase): String {
        return TextRuleProcessor.process(originalText, db.appDao(), applicationContext)
    }
}
