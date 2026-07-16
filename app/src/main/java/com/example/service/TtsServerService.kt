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
import com.example.data.RuleCache
import com.example.data.RulePatternCache
import com.example.data.TextRuleProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale

class TtsServerService : Service() {

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)
    
    private var serverSocket: ServerSocket? = null
    private var activeTts: TextToSpeech? = null
    private var activeEnginePackage: String? = null
    private val synthesisMutex = Mutex()
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
                
                if (serverSocket != null && serverSocket?.localPort != settings.port) {
                    startHttpServer(settings.port)
                } else if (serverSocket == null) {
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

    private fun startHttpServer(port: Int) {
        try {
            stopServer()
            val socket = ServerSocket(port)
            serverSocket = socket
            _isServerRunning.value = true

            serviceScope.launch(Dispatchers.IO) {
                while (_isServerRunning.value) {
                    try {
                        val client = socket.accept()
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                requestSemaphore.withPermit {
                                    handleClient(client)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        // socket closed or error
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isServerRunning.value = false
        }
    }

    private fun stopServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        _isServerRunning.value = false
    }

    private suspend fun handleClient(client: Socket) {
        val startTime = System.currentTimeMillis()
        var text = ""
        var enginePackage = ""
        try {
            client.soTimeout = 8000 // Set an 8-second socket timeout to prevent connection hanging
            val inputStream = client.getInputStream()
            val output = BufferedOutputStream(client.getOutputStream())

            // Read the header section byte-by-byte up to \r\n\r\n or \n\n
            val headerBytes = java.io.ByteArrayOutputStream()
            var b: Int
            while (true) {
                b = inputStream.read()
                if (b == -1) break
                headerBytes.write(b)
                val bytes = headerBytes.toByteArray()
                val size = bytes.size
                if (size >= 4 &&
                    bytes[size - 4] == '\r'.code.toByte() && bytes[size - 3] == '\n'.code.toByte() &&
                    bytes[size - 2] == '\r'.code.toByte() && bytes[size - 1] == '\n'.code.toByte()) {
                    break
                }
                if (size >= 2 &&
                    bytes[size - 2] == '\n'.code.toByte() && bytes[size - 1] == '\n'.code.toByte()) {
                    break
                }
            }

            val headerStr = headerBytes.toString("US-ASCII")
            val lines = headerStr.split(Regex("\r?\n"))
            if (lines.isEmpty() || lines[0].isBlank()) {
                client.close()
                return
            }

            val reqLine = lines[0]
            val parts = reqLine.split(" ")
            if (parts.size < 2) {
                sendErrorResponse(output, 400, "Bad Request")
                client.close()
                return
            }

            val method = parts[0].uppercase(Locale.US)
            if (method != "GET" && method != "POST") {
                sendErrorResponse(output, 400, "Bad Request")
                client.close()
                return
            }

            var contentLength = 0
            for (i in 1 until lines.size) {
                val headerLine = lines[i]
                val lower = headerLine.lowercase(Locale.US)
                if (lower.startsWith("content-length:")) {
                    contentLength = lower.substringAfter("content-length:").trim().toIntOrNull() ?: 0
                }
            }

            if (contentLength > 65536) {
                sendErrorResponse(output, 400, "Request Body Too Large (Max 64KB)")
                client.close()
                return
            }

            var requestBody = ""
            if (contentLength > 0) {
                val bodyBytes = ByteArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = inputStream.read(bodyBytes, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                requestBody = String(bodyBytes, 0, totalRead, Charsets.UTF_8)
            }

            val uriStr = parts[1]
            val pathAndQuery = uriStr.split("?", limit = 2)
            val rawPath = pathAndQuery[0]
            val path = rawPath.replace("//", "/") // Handle redundant slashes gracefully
            val query = if (pathAndQuery.size > 1) pathAndQuery[1] else ""

            if (path != "/tts" && path != "/api/tts") {
                sendErrorResponse(output, 404, "Not Found")
                client.close()
                return
            }

            val params = parseQueryParams(query).toMutableMap()
            if (requestBody.isNotEmpty()) {
                if (requestBody.trim().startsWith("{")) {
                    try {
                        val json = org.json.JSONObject(requestBody)
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            params[key] = json.optString(key)
                        }
                    } catch (e: Exception) {
                        // Not valid JSON
                    }
                } else {
                    params.putAll(parseQueryParams(requestBody))
                }
            }
            text = params["text"] ?: params["key"] ?: params["t"] ?: params["txt"] ?: ""

            if (text.isEmpty()) {
                sendErrorResponse(output, 400, "Error: 'text' or 'key' parameter is required.")
                client.close()
                return
            }

            val db = AppDatabase.getDatabase(applicationContext)
            
            // Apply polyphone replacement rules
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
                sendErrorResponse(output, 400, "Error: Empty text after rule processing.")
                client.close()
                return
            }

            // Send chunked HTTP response headers immediately to allow low-latency streaming
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: audio/wav\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(header.toByteArray(Charsets.UTF_8))
            output.flush()

            // Setup a channel for pipelined synthesis (pre-synthesize up to 2 sentences)
            val fileChannel = kotlinx.coroutines.channels.Channel<File?>(capacity = 2)

            // Start background producer to synthesize sentences one by one
            val producerJob = serviceScope.launch(Dispatchers.Default) {
                try {
                    for (sentence in sentences) {
                        // Local TTS synthesis uses synthesisMutex, but online TTS can run concurrently!
                        val file = synthesisMutex.withLock {
                            synthesizeText(sentence, rate, pitch, enginePackage)
                        }
                        fileChannel.send(file)
                        if (file == null) {
                            break // Stop if synthesis fails
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    fileChannel.close()
                }
            }

            var isFirstSentence = true
            var overallSuccess = true

            try {
                for (audioFile in fileChannel) {
                    if (audioFile == null || !audioFile.exists()) {
                        overallSuccess = false
                        break
                    }

                    val wavDataOffset = if (isFirstSentence) {
                        0 // Send full WAV (with header) for the first sentence
                    } else {
                        getWavDataOffset(audioFile) // Skip 44-byte WAV header for subsequent sentences
                    }
                    isFirstSentence = false

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
                                val hexSize = java.lang.Integer.toHexString(bytesRead)
                                output.write("$hexSize\r\n".toByteArray(Charsets.UTF_8))
                                output.write(buffer, 0, bytesRead)
                                output.write("\r\n".toByteArray(Charsets.UTF_8))
                                output.flush() // Extremely important: flush immediately for zero-latency streaming!
                            }
                        }
                    }
                    // Delete temp file immediately after streaming it
                    audioFile.delete()
                }

                if (overallSuccess) {
                    // Send ending chunk of size 0
                    output.write("0\r\n\r\n".toByteArray(Charsets.UTF_8))
                    output.flush()

                    val duration = System.currentTimeMillis() - startTime
                    logToDatabase(originalText, enginePackage, "SUCCESS", duration)
                } else {
                    throw Exception("One or more sentences failed to synthesize.")
                }
            } catch (e: Exception) {
                producerJob.cancel()
                for (file in fileChannel) {
                    file?.delete()
                }
                throw e
            } finally {
                producerJob.join()
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            if (text.isNotEmpty() && enginePackage.isNotEmpty()) {
                logToDatabase(text, enginePackage, "FAILED", duration, e.message)
            }
            try {
                val output = BufferedOutputStream(client.getOutputStream())
                sendErrorResponse(output, 500, "Error: ${e.message}")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } finally {
            try {
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
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

    private fun sendErrorResponse(output: BufferedOutputStream, statusCode: Int, message: String) {
        val statusText = when (statusCode) {
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val responseBody = message.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${responseBody.size}\r\n" +
                "Connection: close\r\n\r\n"
        try {
            output.write(header.toByteArray(Charsets.UTF_8))
            output.write(responseBody)
            output.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun synthesizeText(
        text: String,
        rate: Float,
        pitch: Float,
        enginePackage: String
    ): File? {
        val tts = getTtsInstance(enginePackage) ?: return null

        withContext(Dispatchers.Main) {
            // Dynamically set language based on text contents
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
                // Synthesize started
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

        // Wait for completion with a generous 10-second timeout.
        // Use withTimeoutOrNull so that any timeout does NOT cancel our parent coroutine scope.
        val success = try {
            val waitResult = kotlinx.coroutines.withTimeoutOrNull(10000) {
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
            kotlinx.coroutines.withTimeout(5000) {
                deferred.await()
            }
        } catch (e: Exception) {
            TextToSpeech.ERROR
        }

        // Fallback to default system TTS engine if target engine initialization fails or times out
        if (status != TextToSpeech.SUCCESS) {
            isFallback = true
            try { tts.shutdown() } catch (e: Exception) {}
            
            val fallbackDeferred = CompletableDeferred<Int>()
            tts = TextToSpeech(applicationContext, { s ->
                fallbackDeferred.complete(s)
            }) // system default
            
            status = try {
                kotlinx.coroutines.withTimeout(5000) {
                    fallbackDeferred.await()
                }
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
        
        // Do not split by punctuation as requested (this prevents fragmentation and TTS software stopping early).
        // Standard TTS has a character limit (typically 4000 characters), so we only split when extremely long.
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

    private fun synthesizeToFlow(
        text: String,
        rate: Float,
        pitch: Float,
        enginePackage: String
    ): kotlinx.coroutines.flow.Flow<ByteArray> = kotlinx.coroutines.flow.flow {
        val audioFile = synthesisMutex.withLock {
            synthesizeText(text, rate, pitch, enginePackage)
        }
        if (audioFile != null && audioFile.exists()) {
            try {
                FileInputStream(audioFile).use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (bytesRead > 0) {
                            emit(buffer.copyOfRange(0, bytesRead))
                        }
                    }
                }
            } finally {
                audioFile.delete()
            }
        } else {
            throw Exception("TTS Synthesis failed")
        }
    }

    private suspend fun processTextRules(originalText: String, db: AppDatabase): String {
        return TextRuleProcessor.process(originalText, db.appDao(), applicationContext)
    }
}
