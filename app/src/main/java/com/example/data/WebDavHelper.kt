package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object WebDavHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun getFolderUrl(url: String, dirName: String): String {
        var base = if (url.endsWith("/")) url else "$url/"
        if (dirName.isNotBlank()) {
            val cleanDirName = dirName.trim().removePrefix("/").removeSuffix("/")
            if (cleanDirName.isNotEmpty()) {
                base = "$base$cleanDirName/"
            }
        }
        return base
    }

    suspend fun createDirectory(url: String, username: String, password: String, dirName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (url.isBlank()) {
                return@withContext Result.failure(Exception("WebDav 服务器地址不能为空"))
            }
            if (dirName.isBlank()) {
                return@withContext Result.success(Unit)
            }
            
            val cleanDirName = dirName.trim().removePrefix("/").removeSuffix("/")
            if (cleanDirName.isEmpty()) {
                return@withContext Result.success(Unit)
            }

            val parts = cleanDirName.split("/")
            var currentPath = ""
            val credential = Credentials.basic(username, password)

            for (part in parts) {
                if (part.isBlank()) continue
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                
                val baseUrl = if (url.endsWith("/")) url else "$url/"
                val folderUrl = "$baseUrl$currentPath"
                
                val request = Request.Builder()
                    .url(folderUrl)
                    .method("MKCOL", null)
                    .header("Authorization", credential)
                    .build()

                client.newCall(request).execute().use { response ->
                    // 201: Created, 405: Already exists, 409: Conflict (already exists on some servers), 200/204: Success
                    if (response.isSuccessful || response.code == 201 || response.code == 405 || response.code == 409 || response.code == 200) {
                        // Success for this level
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("创建同步目录异常: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    suspend fun testConnection(url: String, username: String, password: String, dirName: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (url.isBlank()) {
                return@withContext Result.failure(Exception("WebDav 服务器地址不能为空"))
            }
            // Normalize URL with optional dirName
            val targetUrl = getFolderUrl(url, dirName)
            val credential = Credentials.basic(username, password)
            
            // Try standard PROPFIND first
            val request = Request.Builder()
                .url(targetUrl)
                .method("PROPFIND", null)
                .header("Authorization", credential)
                .header("Depth", "0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 207) {
                    Result.success(Unit)
                } else if (response.code == 401) {
                    Result.failure(Exception("验证失败，请检查用户名或密码"))
                } else {
                    // Fallback to simple GET request
                    val fallbackRequest = Request.Builder()
                        .url(targetUrl)
                        .get()
                        .header("Authorization", credential)
                        .build()
                    client.newCall(fallbackRequest).execute().use { fbResponse ->
                        if (fbResponse.isSuccessful) {
                            Result.success(Unit)
                        } else {
                            Result.failure(Exception("连接失败，服务器返回状态码: ${fbResponse.code}"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接异常: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    suspend fun uploadFile(url: String, username: String, password: String, dirName: String, fileName: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (url.isBlank()) {
                return@withContext Result.failure(Exception("WebDav 服务器地址不能为空"))
            }

            // Automatically make sure the directory exists
            if (dirName.isNotBlank()) {
                createDirectory(url, username, password, dirName)
            }

            val folderUrl = getFolderUrl(url, dirName)
            val targetUrl = folderUrl + fileName
            val credential = Credentials.basic(username, password)
            val body = content.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(targetUrl)
                .put(body)
                .header("Authorization", credential)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 201 || response.code == 204) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("上传失败，服务器返回状态码: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("上传异常: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    suspend fun downloadFile(url: String, username: String, password: String, dirName: String, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (url.isBlank()) {
                return@withContext Result.failure(Exception("WebDav 服务器地址不能为空"))
            }
            val folderUrl = getFolderUrl(url, dirName)
            val targetUrl = folderUrl + fileName
            val credential = Credentials.basic(username, password)

            val request = Request.Builder()
                .url(targetUrl)
                .get()
                .header("Authorization", credential)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Result.success(bodyString)
                } else if (response.code == 404) {
                    Result.failure(Exception("云端备份文件不存在"))
                } else {
                    Result.failure(Exception("下载失败，服务器返回状态码: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("下载异常: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }
}
