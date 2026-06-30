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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
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
                            handleClient(client)
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
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val output = BufferedOutputStream(client.getOutputStream())

            val reqLine = reader.readLine() ?: return
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
            var headerLine: String?
            while (true) {
                headerLine = reader.readLine()
                if (headerLine.isNullOrEmpty()) {
                    break
                }
                val lower = headerLine.lowercase(Locale.US)
                if (lower.startsWith("content-length:")) {
                    contentLength = lower.substringAfter("content-length:").trim().toIntOrNull() ?: 0
                }
            }

            var requestBody = ""
            if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(bodyChars, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                requestBody = String(bodyChars, 0, totalRead)
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
            val settings = db.appDao().getSettings() ?: SettingsEntity()

            val rawRateStr = params["rate"] ?: params["speed"] ?: params["speakSpeed"] ?: params["speechRate"] ?: params["r"] ?: params["s"]
            val rawRate = rawRateStr?.toFloatOrNull() ?: settings.speechRate
            
            val rawPitchStr = params["pitch"] ?: params["speakPitch"] ?: params["p"]
            val rawPitch = rawPitchStr?.toFloatOrNull() ?: settings.pitch
            
            val rate = normalizeRate(rawRate)
            val pitch = normalizePitch(rawPitch)
            enginePackage = params["engine"] ?: params["e"] ?: settings.targetEnginePackage

            val audioFile = synthesisMutex.withLock {
                synthesizeText(text, rate, pitch, enginePackage)
            }

            if (audioFile != null && audioFile.exists()) {
                val duration = System.currentTimeMillis() - startTime
                logToDatabase(text, enginePackage, "SUCCESS", duration)

                val fileLen = audioFile.length()
                val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: audio/wav\r\n" +
                        "Content-Length: $fileLen\r\n" +
                        "Cache-Control: no-cache\r\n" +
                        "Connection: close\r\n\r\n"
                output.write(header.toByteArray(Charsets.UTF_8))

                FileInputStream(audioFile).use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
                output.flush()
                audioFile.delete()
            } else {
                throw Exception("TTS Synthesis failed or file not created.")
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
            try {
                val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val result2 = tts.setLanguage(Locale.CHINESE)
                    if (result2 == TextToSpeech.LANG_MISSING_DATA || result2 == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(Locale.getDefault())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
}
