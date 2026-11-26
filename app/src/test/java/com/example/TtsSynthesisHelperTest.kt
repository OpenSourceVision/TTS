package com.example

import com.example.service.TtsSynthesisHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TtsSynthesisHelperTest {

    private fun buildPcmWav(sampleRate: Int, pcmData: ByteArray, declaredDataSize: Int = pcmData.size): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val blockAlign = channels * (bitsPerSample / 8)
        val byteRate = sampleRate * blockAlign
        val buf = ByteBuffer.allocate(44 + pcmData.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + declaredDataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1.toShort())
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(declaredDataSize)
        buf.put(pcmData)
        return buf.array()
    }

    @Test
    fun isValidWav_rejectsUnfinalizedOrTruncatedFiles() {
        val headerOnly = buildPcmWav(16000, ByteArray(0))
        assertFalse(TtsSynthesisHelper.isValidWav(headerOnly))

        val zeroDeclaredSize = buildPcmWav(16000, ByteArray(320), declaredDataSize = 0)
        assertFalse(TtsSynthesisHelper.isValidWav(zeroDeclaredSize))

        val truncatedPayload = buildPcmWav(16000, ByteArray(100), declaredDataSize = 640)
        assertFalse(TtsSynthesisHelper.isValidWav(truncatedPayload))

        val valid = buildPcmWav(16000, ByteArray(320) { 1 })
        assertTrue(TtsSynthesisHelper.isValidWav(valid))
    }

    @Test
    fun mergeWavChunks_combinesPcmPayloadsAndResamples() {
        val wav1 = buildPcmWav(16000, byteArrayOf(1, 2, 3, 4))
        val wav2 = buildPcmWav(16000, byteArrayOf(5, 6, 7, 8))
        val merged = TtsSynthesisHelper.mergeWavChunks(listOf(wav1, wav2))
        assertNotNull(merged)
        val info = TtsSynthesisHelper.parseWavInfo(merged!!)
        assertNotNull(info)
        assertEquals(8, info!!.dataSize)
        assertEquals(16000, info.sampleRate)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5, 6, 7, 8), merged.drop(info.dataOffset))

        // Different sample rates still merge cleanly without failing
        val wavOtherRate = buildPcmWav(8000, ByteArray(160))
        val mergedResampled = TtsSynthesisHelper.mergeWavChunks(listOf(wav1, wavOtherRate))
        assertNotNull(mergedResampled)
        assertTrue(TtsSynthesisHelper.isValidWav(mergedResampled!!))
    }

    @Test
    fun splitTextForSynthesis_splitsLongParagraphSafely() {
        val shortText = "这是一句短话。"
        assertEquals(listOf(shortText), TtsSynthesisHelper.splitTextForSynthesis(shortText, 200))

        val sentence = "夜色渐深，山风呼啸而过，树影在窗前摇曳不停。"
        val longParagraph = buildString {
            repeat(15) { append(sentence) }
        }
        val chunks = TtsSynthesisHelper.splitTextForSynthesis(longParagraph, 200)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.all { it.length <= 200 })
        assertTrue(chunks.all { TtsSynthesisHelper.isSpeakableText(it) })
        assertEquals(longParagraph, chunks.joinToString(""))
    }

    @Test
    fun parseQueryParams_preservesUnencodedAmpersandInsideText() {
        val parsed = TtsSynthesisHelper.parseQueryParams("text=Tom & Jerry & Friends&rate=1.25&pitch=1.0")
        assertEquals("Tom & Jerry & Friends", parsed["text"])
        assertEquals("1.25", parsed["rate"])
        assertEquals("1.0", parsed["pitch"])
    }
}
