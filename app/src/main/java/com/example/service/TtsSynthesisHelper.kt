package com.example.service

import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class WavDataInfo(
    val audioFormat: Int,
    val numChannels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val dataOffset: Int,
    val dataSize: Int
)

object TtsSynthesisHelper {

    private val KNOWN_PARAM_KEYS = setOf(
        "text", "key", "t", "txt", "speakText",
        "rate", "speed", "speakSpeed", "speechRate", "r", "s",
        "pitch", "speakPitch", "p",
        "engine", "e"
    )

    fun cleanAndSanitizeText(raw: String): String {
        return raw
            .replace(Regex("[\u200B-\u200D\uFEFF\u0000-\u0008\u000B\u000C\u000E-\u001F]"), "")
            .replace(Regex("^[\\s\\u3000]+|[\\s\\u3000]+$"), "")
            .trim()
    }

    fun isSpeakableText(text: String): Boolean {
        if (text.isBlank()) return false
        for (ch in text) {
            if (Character.isLetterOrDigit(ch)) return true
            val block = Character.UnicodeBlock.of(ch)
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Split long text into natural sentence chunks (<= maxChunkLength) so each single TTS
     * engine synthesis call completes well within the 10-second per-utterance timeout.
     */
    fun splitTextForSynthesis(text: String, maxChunkLength: Int = 200): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= maxChunkLength) {
            return if (isSpeakableText(trimmed)) listOf(trimmed) else emptyList()
        }

        val strongDelimiters = setOf('。', '！', '？', '；', '\n', '!', '?', ';', '…')
        val weakDelimiters = setOf('，', '、', ',', '：', ':', ' ')

        val sentences = splitPreservingDelimiters(trimmed, strongDelimiters, maxChunkLength, weakDelimiters)
        val merged = mutableListOf<String>()
        val current = StringBuilder()

        for (piece in sentences) {
            if (piece.isEmpty()) continue
            if (current.isNotEmpty() && current.length + piece.length > maxChunkLength) {
                val candidate = current.toString().trim()
                if (isSpeakableText(candidate)) {
                    merged.add(candidate)
                }
                current.clear()
            }
            current.append(piece)
        }

        if (current.isNotEmpty()) {
            val tail = current.toString().trim()
            if (isSpeakableText(tail)) {
                merged.add(tail)
            } else if (merged.isNotEmpty()) {
                // Attach trailing punctuation to the last speakable chunk if it fits reasonably
                merged[merged.lastIndex] = merged.last() + tail
            }
        }

        return merged
    }

    private fun splitPreservingDelimiters(
        text: String,
        strongDelimiters: Set<Char>,
        maxChunkLength: Int,
        weakDelimiters: Set<Char>
    ): List<String> {
        val rawSegments = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            sb.append(ch)
            val isAsciiPeriodBoundary = ch == '.' &&
                (i + 1 >= text.length || text[i + 1].isWhitespace() || !text[i + 1].isDigit())
            if (ch in strongDelimiters || isAsciiPeriodBoundary) {
                // Consume consecutive closing quotes/brackets or trailing delimiters
                while (i + 1 < text.length && text[i + 1] in charArrayOf('”', '’', '"', '\'', '」', '』', ')', '）', '。', '！', '？', '…')) {
                    i++
                    sb.append(text[i])
                }
                rawSegments.add(sb.toString())
                sb.clear()
            }
            i++
        }
        if (sb.isNotEmpty()) {
            rawSegments.add(sb.toString())
        }

        val result = mutableListOf<String>()
        for (seg in rawSegments) {
            if (seg.length <= maxChunkLength) {
                result.add(seg)
            } else {
                // Split oversized sentence on weak delimiters first
                val weakParts = splitOnWeakDelimiters(seg, weakDelimiters, maxChunkLength)
                for (part in weakParts) {
                    if (part.length <= maxChunkLength) {
                        result.add(part)
                    } else {
                        // Hard split respecting surrogate pairs
                        var start = 0
                        while (start < part.length) {
                            var end = (start + maxChunkLength).coerceAtMost(part.length)
                            if (end < part.length && Character.isHighSurrogate(part[end - 1])) {
                                end = (end - 1).coerceAtLeast(start + 1)
                            }
                            result.add(part.substring(start, end))
                            start = end
                        }
                    }
                }
            }
        }
        return result
    }

    private fun splitOnWeakDelimiters(
        text: String,
        weakDelimiters: Set<Char>,
        maxChunkLength: Int
    ): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(ch)
            if (ch in weakDelimiters) {
                parts.add(sb.toString())
                sb.clear()
            }
        }
        if (sb.isNotEmpty()) {
            parts.add(sb.toString())
        }

        val merged = mutableListOf<String>()
        val current = StringBuilder()
        for (p in parts) {
            if (current.isNotEmpty() && current.length + p.length > maxChunkLength) {
                merged.add(current.toString())
                current.clear()
            }
            current.append(p)
        }
        if (current.isNotEmpty()) {
            merged.add(current.toString())
        }
        return merged
    }

    /**
     * Parse RIFF/WAVE header and verify the WAV file is finalized with valid non-zero audio data.
     */
    fun parseWavInfo(bytes: ByteArray): WavDataInfo? {
        if (bytes.size <= 44) return null
        if (bytes[0] != 'R'.code.toByte() ||
            bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() ||
            bytes[3] != 'F'.code.toByte() ||
            bytes[8] != 'W'.code.toByte() ||
            bytes[9] != 'A'.code.toByte() ||
            bytes[10] != 'V'.code.toByte() ||
            bytes[11] != 'E'.code.toByte()
        ) {
            return null
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var audioFormat = -1
        var numChannels = -1
        var sampleRate = -1
        var bitsPerSample = -1
        var dataOffset = -1
        var dataSize = -1

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(offset + 4)
            if (chunkSize < 0) return null
            val payloadStart = offset + 8
            if (payloadStart > bytes.size) break

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16 || payloadStart + 16 > bytes.size) return null
                    audioFormat = buf.getShort(payloadStart).toInt() and 0xFFFF
                    numChannels = buf.getShort(payloadStart + 2).toInt() and 0xFFFF
                    sampleRate = buf.getInt(payloadStart + 4)
                    bitsPerSample = buf.getShort(payloadStart + 14).toInt() and 0xFFFF
                }
                "data" -> {
                    dataOffset = payloadStart
                    dataSize = chunkSize
                    break
                }
            }

            val paddedSize = chunkSize + (chunkSize and 1)
            val nextOffset = payloadStart.toLong() + paddedSize.toLong()
            if (nextOffset > bytes.size.toLong()) break
            offset = nextOffset.toInt()
        }

        if (audioFormat <= 0 || numChannels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) {
            return null
        }
        if (dataOffset < 0 || dataSize <= 0) {
            return null
        }
        val availableBytes = bytes.size - dataOffset
        if (availableBytes <= 0 || availableBytes < dataSize) {
            return null
        }

        return WavDataInfo(
            audioFormat = audioFormat,
            numChannels = numChannels,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            dataOffset = dataOffset,
            dataSize = dataSize
        )
    }

    fun isValidWav(bytes: ByteArray): Boolean {
        return parseWavInfo(bytes) != null
    }

    /**
     * Merge multiple finalized WAV byte arrays into a single canonical RIFF/WAVE PCM byte array.
     */
    fun mergeWavChunks(chunks: List<ByteArray>): ByteArray? {
        if (chunks.isEmpty()) return null
        if (chunks.size == 1) {
            return if (isValidWav(chunks.first())) chunks.first() else null
        }

        val parsed = chunks.map { bytes ->
            val info = parseWavInfo(bytes) ?: return null
            if (info.audioFormat != 1) return null
            Pair(bytes, info)
        }

        val firstInfo = parsed.first().second
        val targetSampleRate = firstInfo.sampleRate
        val targetChannels = firstInfo.numChannels
        val targetBitsPerSample = firstInfo.bitsPerSample

        val pcmSegments = mutableListOf<ByteArray>()
        var totalPcmSize = 0
        for ((bytes, info) in parsed) {
            val segmentPcm = if (
                info.sampleRate == targetSampleRate &&
                info.numChannels == targetChannels &&
                info.bitsPerSample == targetBitsPerSample
            ) {
                bytes.copyOfRange(info.dataOffset, info.dataOffset + info.dataSize)
            } else if (
                info.bitsPerSample == 16 &&
                targetBitsPerSample == 16 &&
                info.numChannels == targetChannels
            ) {
                resample16BitPcm(
                    srcBytes = bytes,
                    srcOffset = info.dataOffset,
                    srcSize = info.dataSize,
                    srcRate = info.sampleRate,
                    dstRate = targetSampleRate,
                    channels = targetChannels
                )
            } else {
                return null
            }
            pcmSegments.add(segmentPcm)
            totalPcmSize += segmentPcm.size
        }

        if (totalPcmSize <= 0) return null

        val blockAlign = targetChannels * (targetBitsPerSample / 8)
        val byteRate = targetSampleRate * blockAlign
        val out = ByteBuffer.allocate(44 + totalPcmSize).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt(36 + totalPcmSize)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(16)
        out.putShort(1.toShort())
        out.putShort(targetChannels.toShort())
        out.putInt(targetSampleRate)
        out.putInt(byteRate)
        out.putShort(blockAlign.toShort())
        out.putShort(targetBitsPerSample.toShort())
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(totalPcmSize)
        for (pcm in pcmSegments) {
            out.put(pcm)
        }
        return out.array()
    }

    private fun resample16BitPcm(
        srcBytes: ByteArray,
        srcOffset: Int,
        srcSize: Int,
        srcRate: Int,
        dstRate: Int,
        channels: Int
    ): ByteArray {
        val frameSize = channels * 2
        val srcFrames = srcSize / frameSize
        if (srcFrames <= 1 || srcRate <= 0 || dstRate <= 0) {
            return srcBytes.copyOfRange(srcOffset, srcOffset + srcSize)
        }
        val dstFrames = ((srcFrames.toLong() * dstRate) / srcRate).toInt().coerceAtLeast(1)
        val srcBuf = ByteBuffer.wrap(srcBytes, srcOffset, srcSize).order(ByteOrder.LITTLE_ENDIAN)
        val dstBuf = ByteBuffer.allocate(dstFrames * frameSize).order(ByteOrder.LITTLE_ENDIAN)

        for (df in 0 until dstFrames) {
            val srcPos = df.toDouble() * srcRate / dstRate
            val idx0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val idx1 = (idx0 + 1).coerceAtMost(srcFrames - 1)
            val frac = srcPos - idx0
            for (ch in 0 until channels) {
                val s0 = srcBuf.getShort(srcOffset + (idx0 * channels + ch) * 2).toInt()
                val s1 = srcBuf.getShort(srcOffset + (idx1 * channels + ch) * 2).toInt()
                val sample = (s0 + (s1 - s0) * frac).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                dstBuf.putShort(sample.toShort())
            }
        }
        return dstBuf.array()
    }

    /**
     * Parse form-urlencoded or query string safely even when unencoded '&' appears inside 'text=...'.
     */
    fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        val rawSegments = query.split("&")
        val combinedPairs = mutableListOf<String>()

        for (seg in rawSegments) {
            val eqIdx = seg.indexOf('=')
            val rawKey = if (eqIdx > 0) seg.substring(0, eqIdx) else null
            val decodedKey = rawKey?.let {
                try {
                    URLDecoder.decode(it, "UTF-8")
                } catch (_: Exception) {
                    it
                }
            }
            if (combinedPairs.isEmpty() || (decodedKey != null && decodedKey in KNOWN_PARAM_KEYS)) {
                combinedPairs.add(seg)
            } else {
                // Unencoded '&' belonged to the previous parameter's value (e.g. Legado java.encodeURI)
                combinedPairs[combinedPairs.lastIndex] = combinedPairs.last() + "&" + seg
            }
        }

        val params = mutableMapOf<String, String>()
        for (pair in combinedPairs) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val rawKey = pair.substring(0, idx)
                val rawValue = pair.substring(idx + 1)
                val key = try {
                    URLDecoder.decode(rawKey, "UTF-8")
                } catch (_: Exception) {
                    rawKey
                }
                val value = try {
                    URLDecoder.decode(rawValue, "UTF-8")
                } catch (_: Exception) {
                    rawValue
                }
                params[key] = value
            }
        }
        return params
    }
}
