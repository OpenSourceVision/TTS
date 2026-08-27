package com.example.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashReport = buildCrashReport(thread, throwable)
            Log.e("CrashHandler", crashReport, throwable)

            // 1. 保存崩溃日志文件到 app 内部存储
            saveCrashLogFile(context, crashReport)

            // 2. 将崩溃日志尝试记录到 Room 数据库
            saveCrashToDb(context, throwable, crashReport)

            // 短暂休眠以保证文件与数据库写完
            Thread.sleep(300)
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            // 传递给系统默认 handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashReport(thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val timeStr = dateFormat.format(Date())

        sb.append("=================== CRASH REPORT ===================\n")
        sb.append("Time: ").append(timeStr).append("\n")
        sb.append("Thread: ").append(thread.name).append(" (id: ").append(thread.id).append(")\n")
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        sb.append("Android SDK: ").append(Build.VERSION.SDK_INT).append(" (Release: ").append(Build.VERSION.RELEASE).append(")\n")
        sb.append("Exception: ").append(throwable.javaClass.name).append(": ").append(throwable.message).append("\n")
        sb.append("------------------- STACK TRACE -------------------\n")

        val writer = java.io.StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        sb.append(writer.toString())
        sb.append("\n====================================================\n")

        return sb.toString()
    }

    companion object {
        fun init(context: Context) {
            CrashHandler(context.applicationContext)
        }

        fun saveCrashLogFile(context: Context, report: String) {
            try {
                val file = File(context.filesDir, "crash_logs.txt")
                FileWriter(file, true).use { writer ->
                    writer.write(report)
                    writer.write("\n\n")
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        private fun saveCrashToDb(context: Context, throwable: Throwable, report: String) {
            try {
                val db = AppDatabase.getDatabase(context)
                val history = HistoryEntity(
                    text = "【APP崩溃闪退】Thread: ${Thread.currentThread().name}",
                    length = 0,
                    enginePackage = "System Crash",
                    timestamp = System.currentTimeMillis(),
                    status = "CRASH",
                    durationMs = 0,
                    errorMsg = report
                )
                runCatching {
                    val dao = db.appDao()
                    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        kotlinx.coroutines.withTimeoutOrNull(1000) {
                            dao.insertHistory(history)
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        fun getCrashLogsFile(context: Context): File {
            return File(context.filesDir, "crash_logs.txt")
        }

        fun readCrashLogs(context: Context): String {
            val file = getCrashLogsFile(context)
            return if (file.exists() && file.length() > 0) {
                file.readText()
            } else {
                "暂无本地崩溃堆栈记录"
            }
        }

        fun clearCrashLogs(context: Context) {
            try {
                val file = getCrashLogsFile(context)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
