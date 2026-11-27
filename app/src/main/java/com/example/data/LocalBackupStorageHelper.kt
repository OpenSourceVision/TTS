package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object LocalBackupStorageHelper {
    const val TTS_DIR_NAME = "TTS"
    const val BACKUP_FILE_NAME = "tts_backup.json"
    const val AUTO_BACKUP_FILE_NAME = "tts_rules_auto_backup.json"
    const val PUBLIC_BACKUP_DISPLAY_PATH = "Download/TTS/tts_backup.json"

    fun ensureLocalTtsDirs(context: Context) {
        try {
            val internalDir = File(context.filesDir, TTS_DIR_NAME)
            if (!internalDir.exists()) internalDir.mkdirs()
        } catch (_: Exception) {
        }
        try {
            val extFilesDir = context.getExternalFilesDir(null)
            if (extFilesDir != null) {
                val extTtsDir = File(extFilesDir, TTS_DIR_NAME)
                if (!extTtsDir.exists()) extTtsDir.mkdirs()
            }
        } catch (_: Exception) {
        }
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) {
                val publicTtsDir = File(downloadsDir, TTS_DIR_NAME)
                if (!publicTtsDir.exists()) publicTtsDir.mkdirs()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Write and overwrite [fileName] directly inside the TTS directory:
     * 1. Public Download/TTS/<fileName> (via MediaStore on Android 10+ and direct File fallback)
     * 2. App-internal filesDir/TTS/<fileName> & externalFilesDir/TTS/<fileName>
     */
    fun saveToTtsDirectory(
        context: Context,
        content: String,
        fileName: String = BACKUP_FILE_NAME
    ): Result<String> {
        var publicWritten = false
        var privateWritten = false

        // 1. Android 10+ MediaStore.Downloads -> Download/TTS/<fileName> (zero permission, overwrites existing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                publicWritten = writeViaMediaStoreDownloads(context, content, fileName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Direct public Download/TTS/<fileName> (Android 9 and below, or ROMs allowing direct access)
        if (!publicWritten || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null) {
                    val ttsDir = File(downloadsDir, TTS_DIR_NAME)
                    if (!ttsDir.exists()) ttsDir.mkdirs()
                    if (ttsDir.exists() && ttsDir.isDirectory) {
                        File(ttsDir, fileName).writeText(content, Charsets.UTF_8)
                        publicWritten = true
                    }
                }
            } catch (_: Exception) {
                // Ignored when scoped storage blocks raw File path on Android 10+
            }
        }

        // 3. App internal files/TTS/<fileName>
        try {
            val internalTtsDir = File(context.filesDir, TTS_DIR_NAME)
            if (!internalTtsDir.exists()) internalTtsDir.mkdirs()
            File(internalTtsDir, fileName).writeText(content, Charsets.UTF_8)
            privateWritten = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. App external files/TTS/<fileName>
        try {
            val extFilesDir = context.getExternalFilesDir(null)
            if (extFilesDir != null) {
                val extTtsDir = File(extFilesDir, TTS_DIR_NAME)
                if (!extTtsDir.exists()) extTtsDir.mkdirs()
                File(extTtsDir, fileName).writeText(content, Charsets.UTF_8)
                privateWritten = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return when {
            publicWritten -> Result.success("${Environment.DIRECTORY_DOWNLOADS}/$TTS_DIR_NAME/$fileName")
            privateWritten -> Result.success("$TTS_DIR_NAME/$fileName")
            else -> Result.failure(Exception("无法写入本地 TTS 备份目录"))
        }
    }

    private fun writeViaMediaStoreDownloads(
        context: Context,
        content: String,
        fileName: String
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/$TTS_DIR_NAME"
        val bytes = content.toByteArray(Charsets.UTF_8)

        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(fileName, "%$relativeDir%")

        val existingUri = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            } else {
                null
            }
        }

        if (existingUri != null) {
            try {
                val stream = resolver.openOutputStream(existingUri, "wt")
                if (stream != null) {
                    stream.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    return true
                }
            } catch (_: Exception) {
            }
            // Stale MediaStore entry (e.g. file deleted externally); remove and re-insert cleanly
            try {
                resolver.delete(existingUri, null, null)
            } catch (_: Exception) {
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val insertedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(insertedUri, "wt")?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: return false
            val doneValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(insertedUri, doneValues, null, null)
            true
        } catch (e: Exception) {
            try {
                resolver.delete(insertedUri, null, null)
            } catch (_: Exception) {
            }
            throw e
        }
    }

    /**
     * Read backup content from the TTS directory (Download/TTS first, then Documents/TTS and app TTS dirs).
     * Returns pair of (content, displaySource) if found.
     */
    fun readFromTtsDirectory(
        context: Context,
        preferredFileNames: List<String> = listOf(BACKUP_FILE_NAME, AUTO_BACKUP_FILE_NAME)
    ): Pair<String, String>? {
        // 1. MediaStore Downloads/TTS/<fileName> (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/$TTS_DIR_NAME"
            for (name in preferredFileNames) {
                try {
                    val projection = arrayOf(MediaStore.MediaColumns._ID)
                    val selection =
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf(name, "%$relativeDir%")
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
                                val text = resolver.openInputStream(uri)?.use { stream ->
                                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                                }
                                if (!text.isNullOrBlank()) {
                                    return Pair(text, "${Environment.DIRECTORY_DOWNLOADS}/$TTS_DIR_NAME/$name")
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // 2. Direct Download/TTS & Documents/TTS folders
        val publicTtsDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, TTS_DIR_NAME) },
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, TTS_DIR_NAME) }
        )
        for (dir in publicTtsDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            for (name in preferredFileNames) {
                val file = File(dir, name)
                if (file.exists() && file.isFile && file.length() > 0) {
                    try {
                        val text = file.readText(Charsets.UTF_8)
                        if (text.isNotBlank()) {
                            return Pair(text, "${dir.parentFile?.name ?: ""}/$TTS_DIR_NAME/$name".trimStart('/'))
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // 3. App external files/TTS & internal files/TTS
        val appTtsDirs = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, TTS_DIR_NAME) },
            File(context.filesDir, TTS_DIR_NAME)
        )
        for (dir in appTtsDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            for (name in preferredFileNames) {
                val file = File(dir, name)
                if (file.exists() && file.isFile && file.length() > 0) {
                    try {
                        val text = file.readText(Charsets.UTF_8)
                        if (text.isNotBlank()) {
                            return Pair(text, "$TTS_DIR_NAME/$name")
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        return null
    }
}
