package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocalBackupStorageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupStorageHelperTest {

    @Test
    fun `ensureLocalTtsDirs creates TTS folders`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LocalBackupStorageHelper.ensureLocalTtsDirs(context)

        val internalTtsDir = File(context.filesDir, LocalBackupStorageHelper.TTS_DIR_NAME)
        assertTrue(internalTtsDir.exists() && internalTtsDir.isDirectory)

        val extFilesDir = context.getExternalFilesDir(null)
        if (extFilesDir != null) {
            val extTtsDir = File(extFilesDir, LocalBackupStorageHelper.TTS_DIR_NAME)
            assertTrue(extTtsDir.exists() && extTtsDir.isDirectory)
        }
    }

    @Test
    fun `saveToTtsDirectory directly overwrites config file without duplicates`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstContent = """{"version":1,"step":"first"}"""
        val secondContent = """{"version":1,"step":"overwritten"}"""

        val firstResult = LocalBackupStorageHelper.saveToTtsDirectory(context, firstContent)
        assertTrue(firstResult.isSuccess)

        val secondResult = LocalBackupStorageHelper.saveToTtsDirectory(context, secondContent)
        assertTrue(secondResult.isSuccess)

        val internalTtsDir = File(context.filesDir, LocalBackupStorageHelper.TTS_DIR_NAME)
        val jsonFiles = internalTtsDir.listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()
        assertEquals(listOf(LocalBackupStorageHelper.BACKUP_FILE_NAME), jsonFiles.map { it.name })
        assertEquals(secondContent, jsonFiles.first().readText(Charsets.UTF_8))

        val readBack = LocalBackupStorageHelper.readFromTtsDirectory(context)
        assertNotNull(readBack)
        assertEquals(secondContent, readBack!!.first)
    }

    @Test
    fun `readFromTtsDirectory prefers manual backup over auto backup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val autoContent = """{"version":1,"source":"auto"}"""
        val manualContent = """{"version":1,"source":"manual"}"""

        assertTrue(
            LocalBackupStorageHelper.saveToTtsDirectory(
                context = context,
                content = autoContent,
                fileName = LocalBackupStorageHelper.AUTO_BACKUP_FILE_NAME
            ).isSuccess
        )
        assertTrue(
            LocalBackupStorageHelper.saveToTtsDirectory(
                context = context,
                content = manualContent,
                fileName = LocalBackupStorageHelper.BACKUP_FILE_NAME
            ).isSuccess
        )

        val readBack = LocalBackupStorageHelper.readFromTtsDirectory(context)
        assertNotNull(readBack)
        assertEquals(manualContent, readBack!!.first)
    }
}
