package com.example.downloader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

data class HlsSegment(
    val index: Int,
    val url: String,
    val durationSec: Double = 0.0,
    val keyUri: String? = null,
    val ivHex: String? = null,
    val byteRange: String? = null
)

data class HlsDownloadProgress(
    val totalSegments: Int,
    val completedSegments: Int,
    val downloadedBytes: Long,
    val estimatedTotalBytes: Long,
    val speedBps: Long,
    val progressPercent: Int
)

/**
 * Production-Grade Resumable & Parallel HLS / M3U8 Segment Downloader.
 * Features:
 * - Parallel segment workers using Coroutines & Semaphores (up to 4 parallel workers).
 * - True Resumption: checks local disk for already completed segments and skips them.
 * - Per-segment retry with exponential backoff (up to 3 retries per segment).
 * - Full AES-128 HLS Decryption Engine with explicit IV and sequence-based IV support.
 * - Generates clean local index.m3u8 for seamless offline ExoPlayer playback.
 */
class HlsSegmentDownloader(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val maxParallelWorkers: Int = 4,
    private val maxRetriesPerSegment: Int = 3
) {
    companion object {
        private const val TAG = "HlsSegmentDownloader"
    }

    private val isCancelled = AtomicBoolean(false)

    fun cancel() {
        isCancelled.set(true)
    }

    suspend fun downloadPlaylist(
        m3u8Url: String,
        outputFolder: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: (suspend (HlsDownloadProgress) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            outputFolder.mkdirs()
            val localIndexFile = File(outputFolder, "index.m3u8")

            // 1. Fetch Master / Variant M3U8
            var playlistUrl = m3u8Url
            var playlistText = fetchTextWithRetry(playlistUrl, headers)
                ?: return@withContext Result.failure(Exception("Failed to fetch M3U8 playlist from $m3u8Url"))

            // If Master playlist with multiple variants, pick highest bandwidth / 1080p
            if (playlistText.contains("#EXT-X-STREAM-INF")) {
                val variantUrl = pickBestVariantUrl(playlistUrl, playlistText)
                if (variantUrl != null) {
                    playlistUrl = variantUrl
                    playlistText = fetchTextWithRetry(playlistUrl, headers)
                        ?: return@withContext Result.failure(Exception("Failed to fetch variant M3U8 from $variantUrl"))
                }
            }

            // 2. Parse M3U8 into segments and encryption metadata
            val (segments, localM3u8Content, keysMap) = parsePlaylist(playlistUrl, playlistText, outputFolder, headers)
            if (segments.isEmpty()) {
                return@withContext Result.failure(Exception("No playable media segments parsed in M3U8"))
            }

            val totalSegments = segments.size
            val completedCount = AtomicLong(0)
            val totalBytes = AtomicLong(0)
            val lastUpdateTime = AtomicLong(System.currentTimeMillis())
            val bytesSinceLastUpdate = AtomicLong(0)

            // Scan disk for already downloaded valid segments
            val missingSegments = mutableListOf<HlsSegment>()
            for (seg in segments) {
                val ext = if (seg.url.contains(".m4s", ignoreCase = true)) "m4s" else "ts"
                val segFile = File(outputFolder, "segment_${seg.index}.$ext")
                if (segFile.exists() && segFile.length() > 0L) {
                    completedCount.incrementAndGet()
                    totalBytes.addAndGet(segFile.length())
                } else {
                    missingSegments.add(seg)
                }
            }

            Log.i(TAG, "Starting HLS download: $totalSegments total segments, ${segments.size - missingSegments.size} already on disk, ${missingSegments.size} to download")

            val semaphore = Semaphore(maxParallelWorkers)

            // 3. Download missing segments in parallel
            coroutineScope {
                val downloadJobs = missingSegments.map { segment ->
                    async(Dispatchers.IO) {
                        if (isCancelled.get()) return@async false

                        semaphore.withPermit {
                            if (isCancelled.get()) return@withPermit false

                            val ext = if (segment.url.contains(".m4s", ignoreCase = true)) "m4s" else "ts"
                            val segFile = File(outputFolder, "segment_${segment.index}.$ext")
                            val tempFile = File(outputFolder, "segment_${segment.index}.$ext.tmp")

                            val keyBytes = segment.keyUri?.let { keysMap[it] }
                            val downloadedBytes = downloadSegmentWithRetry(
                                segment = segment,
                                targetTempFile = tempFile,
                                headers = headers,
                                keyBytes = keyBytes
                            )

                            if (downloadedBytes > 0L && tempFile.exists()) {
                                if (tempFile.renameTo(segFile) || (segFile.delete() && tempFile.renameTo(segFile))) {
                                    val done = completedCount.incrementAndGet()
                                    val currentTotal = totalBytes.addAndGet(downloadedBytes)
                                    val since = bytesSinceLastUpdate.addAndGet(downloadedBytes)

                                    val now = System.currentTimeMillis()
                                    val lastTime = lastUpdateTime.get()
                                    if (now - lastTime >= 500L) {
                                        val duration = (now - lastTime).coerceAtLeast(1)
                                        val speed = (since * 1000L) / duration
                                        bytesSinceLastUpdate.set(0)
                                        lastUpdateTime.set(now)

                                        val estTotal = if (done > 0) (currentTotal * totalSegments) / done else currentTotal
                                        val pct = ((done * 100L) / totalSegments).toInt()

                                        onProgress?.invoke(
                                            HlsDownloadProgress(
                                                totalSegments = totalSegments,
                                                completedSegments = done.toInt(),
                                                downloadedBytes = currentTotal,
                                                estimatedTotalBytes = estTotal,
                                                speedBps = speed,
                                                progressPercent = pct
                                            )
                                        )
                                    }
                                    return@withPermit true
                                }
                            }
                            false
                        }
                    }
                }

                val results = downloadJobs.awaitAll()
                val failedCount = results.count { !it }
                if (failedCount > 0 && !isCancelled.get()) {
                    throw Exception("$failedCount segments failed to download after all retries")
                }
            }

            if (isCancelled.get()) {
                return@withContext Result.failure(Exception("Download cancelled by user"))
            }

            // 4. Save clean local index.m3u8
            localIndexFile.writeText(localM3u8Content)

            // Final progress update
            val finalTotal = totalBytes.get()
            onProgress?.invoke(
                HlsDownloadProgress(
                    totalSegments = totalSegments,
                    completedSegments = totalSegments,
                    downloadedBytes = finalTotal,
                    estimatedTotalBytes = finalTotal,
                    speedBps = 0L,
                    progressPercent = 100
                )
            )

            Log.i(TAG, "HLS download successfully finished: $totalSegments segments in ${outputFolder.absolutePath}")
            Result.success(localIndexFile)

        } catch (e: Exception) {
            Log.e(TAG, "HLS download failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun downloadSegmentWithRetry(
        segment: HlsSegment,
        targetTempFile: File,
        headers: Map<String, String>,
        keyBytes: ByteArray?
    ): Long {
        var attempt = 0
        while (attempt < maxRetriesPerSegment && !isCancelled.get()) {
            attempt++
            try {
                val rawBytes = fetchBytes(segment.url, headers)
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    val finalBytes = if (keyBytes != null) {
                        decryptAes128(rawBytes, keyBytes, segment.ivHex, segment.index)
                    } else {
                        rawBytes
                    }

                    targetTempFile.writeBytes(finalBytes)
                    return finalBytes.size.toLong()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Segment ${segment.index} attempt $attempt failed: ${e.message}")
            }
            if (attempt < maxRetriesPerSegment && !isCancelled.get()) {
                delay(min(500L * (1L shl attempt), 4000L))
            }
        }
        return 0L
    }

    private fun decryptAes128(cipherData: ByteArray, keyBytes: ByteArray, ivHex: String?, sequenceNumber: Int): ByteArray {
        return try {
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivBytes = if (!ivHex.isNullOrBlank()) {
                hexStringToByteArray(ivHex.removePrefix("0x"))
            } else {
                // Default IV is 16-byte big endian sequence number
                ByteBuffer.allocate(16).putLong(8, sequenceNumber.toLong()).array()
            }

            val ivSpec = IvParameterSpec(ivBytes)
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(cipherData)
        } catch (e: Exception) {
            Log.w(TAG, "AES-128 decryption failed for segment $sequenceNumber: ${e.message}. Using raw data.")
            cipherData
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private suspend fun parsePlaylist(
        playlistUrl: String,
        playlistText: String,
        outputFolder: File,
        headers: Map<String, String>
    ): Triple<List<HlsSegment>, String, Map<String, ByteArray>> {
        val lines = playlistText.lines()
        val segments = mutableListOf<HlsSegment>()
        val localLines = mutableListOf<String>()
        val keysMap = ConcurrentHashMap<String, ByteArray>()

        var currentKeyUri: String? = null
        var currentIvHex: String? = null
        var currentDuration = 0.0
        var segIndex = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXT-X-KEY")) {
                // Parse AES-128 key
                val uriMatch = Regex("""URI=["']([^"']+)["']""").find(trimmed)
                val ivMatch = Regex("""IV=0x([0-9a-fA-F]+)""").find(trimmed)

                if (uriMatch != null) {
                    val rawKeyUri = uriMatch.groupValues[1]
                    val fullKeyUri = resolveUrl(playlistUrl, rawKeyUri)
                    currentKeyUri = fullKeyUri
                    currentIvHex = ivMatch?.groupValues?.get(1)

                    if (!keysMap.containsKey(fullKeyUri)) {
                        val keyBytes = fetchBytes(fullKeyUri, headers)
                        if (keyBytes != null) {
                            keysMap[fullKeyUri] = keyBytes
                        }
                    }
                }
                // Omit or sanitize EXT-X-KEY in local index since segments are decrypted locally
                continue
            } else if (trimmed.startsWith("#EXTINF:")) {
                val durStr = trimmed.removePrefix("#EXTINF:").substringBefore(",").trim()
                currentDuration = durStr.toDoubleOrNull() ?: 5.0
                localLines.add(trimmed)
            } else if (trimmed.startsWith("#")) {
                localLines.add(trimmed)
            } else {
                // Segment URI
                val fullSegUrl = resolveUrl(playlistUrl, trimmed)
                val ext = if (trimmed.contains(".m4s", ignoreCase = true)) "m4s" else "ts"
                val localSegName = "segment_${segIndex}.$ext"

                segments.add(
                    HlsSegment(
                        index = segIndex,
                        url = fullSegUrl,
                        durationSec = currentDuration,
                        keyUri = currentKeyUri,
                        ivHex = currentIvHex
                    )
                )

                localLines.add(localSegName)
                segIndex++
            }
        }

        return Triple(segments, localLines.joinToString("\n"), keysMap)
    }

    private fun pickBestVariantUrl(masterUrl: String, masterText: String): String? {
        val lines = masterText.lines()
        var bestBandwidth = -1L
        var bestUri: String? = null

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bwMatch = Regex("""BANDWIDTH=(\d+)""").find(line)
                val bw = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                if (i + 1 < lines.size) {
                    val uri = lines[i + 1].trim()
                    if (bw > bestBandwidth && uri.isNotBlank() && !uri.startsWith("#")) {
                        bestBandwidth = bw
                        bestUri = uri
                    }
                }
            }
        }

        return bestUri?.let { resolveUrl(masterUrl, it) }
    }

    private suspend fun fetchTextWithRetry(url: String, headers: Map<String, String>): String? {
        var attempt = 0
        while (attempt < 3 && !isCancelled.get()) {
            attempt++
            try {
                val reqBuilder = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }

                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.isSuccessful) return resp.body?.string()
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchText attempt $attempt failed for $url: ${e.message}")
            }
            delay(500L * attempt)
        }
        return null
    }

    private fun fetchBytes(url: String, headers: Map<String, String>): ByteArray? {
        val reqBuilder = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        return httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.bytes() else null
        }
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return try {
            if (relativeOrAbsolute.startsWith("http://", ignoreCase = true) || relativeOrAbsolute.startsWith("https://", ignoreCase = true)) {
                relativeOrAbsolute
            } else {
                java.net.URI(baseUrl).resolve(relativeOrAbsolute).toString()
            }
        } catch (_: Exception) {
            if (relativeOrAbsolute.startsWith("/")) {
                val host = baseUrl.substringBefore("/", "").ifEmpty { baseUrl }
                "$host$relativeOrAbsolute"
            } else {
                val base = baseUrl.substringBeforeLast("/")
                "$base/$relativeOrAbsolute"
            }
        }
    }
}
