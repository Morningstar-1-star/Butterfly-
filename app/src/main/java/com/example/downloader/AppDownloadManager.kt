package com.example.downloader

import android.content.Context
import android.util.Log
import com.example.db.AppDatabase
import com.example.db.OfflineDownloadEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadProgressState(
    val videoId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progress: Float,
    val speedBytesPerSec: Long,
    val status: String // "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"
)

object AppDownloadManager {
    private const val TAG = "AppDownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val maxConcurrentDownloads = kotlinx.coroutines.sync.Semaphore(3)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedVideos = ConcurrentHashMap.newKeySet<String>()

    private val _progressMap = MutableStateFlow<Map<String, DownloadProgressState>>(emptyMap())
    val progressMap: StateFlow<Map<String, DownloadProgressState>> = _progressMap.asStateFlow()

    fun getDownloadDirectory(context: Context): File {
        val dir = File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun startDownload(
        context: Context,
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        qualityLabel: String,
        downloadUrl: String
    ) {
        if (activeJobs.containsKey(videoId)) return
        pausedVideos.remove(videoId)

        // Validate that downloadUrl is a valid http/https network URL
        if (!downloadUrl.startsWith("http://", ignoreCase = true) && !downloadUrl.startsWith("https://", ignoreCase = true)) {
            Log.e(TAG, "Invalid network download URL for $videoId: $downloadUrl")
            updateProgress(videoId, 0L, 0L, "FAILED", 0L)
            return
        }

        val job = scope.launch {
            maxConcurrentDownloads.acquire()
            try {
                executeDownloadTask(context, videoId, title, channelName, thumbnailUrl, qualityLabel, downloadUrl)
            } finally {
                maxConcurrentDownloads.release()
            }
        }
        activeJobs[videoId] = job
    }

    private suspend fun executeDownloadTask(
        context: Context,
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        qualityLabel: String,
        downloadUrl: String
    ) {
        val db = AppDatabase.getInstance(context)
        val dir = getDownloadDirectory(context)
        val isM3u8 = downloadUrl.contains(".m3u8", ignoreCase = true)
        val ext = if (isM3u8) "m3u8" else "mp4"
        val safeFileName = "video_${videoId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.$ext"
        val targetFile = File(dir, safeFileName)

        var existingDownloaded = if (targetFile.exists()) targetFile.length() else 0L

        val initialEntity = OfflineDownloadEntity(
            videoId = videoId,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            localFilePath = targetFile.absolutePath,
            qualityLabel = qualityLabel,
            totalBytes = 0L,
            downloadedBytes = existingDownloaded,
            status = "DOWNLOADING"
        )
        db.userDataDao().insertOrUpdateDownload(initialEntity)

        try {
            val reqBuilder = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            if (existingDownloaded > 0L && !isM3u8) {
                reqBuilder.header("Range", "bytes=$existingDownloaded-")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val contentType = response.header("Content-Type", "") ?: ""
            if (contentType.contains("mpegurl", ignoreCase = true) || contentType.contains("m3u8", ignoreCase = true)) {
                Log.w(TAG, "Download URL is HLS M3U8 playlist ($contentType). Saving playlist reference.")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val responseLength = body.contentLength()
            val totalBytes = if (response.code == 206 && responseLength > 0L) {
                existingDownloaded + responseLength
            } else if (responseLength > 0L) {
                existingDownloaded = 0L
                responseLength
            } else {
                -1L
            }

            val outputStream = if (existingDownloaded > 0L && response.code == 206) {
                val raf = RandomAccessFile(targetFile, "rw")
                raf.seek(existingDownloaded)
                raf
            } else {
                FileOutputStream(targetFile, false)
            }

            val inputStream: InputStream = body.byteStream()
            val buffer = ByteArray(32 * 1024)
            var currentBytes = existingDownloaded
            var lastProgressUpdate = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L
            var currentSpeed = 0L

            try {
                while (scope.isActive) {
                    if (pausedVideos.contains(videoId)) {
                        db.userDataDao().insertOrUpdateDownload(
                            initialEntity.copy(
                                downloadedBytes = currentBytes,
                                totalBytes = if (totalBytes > 0) totalBytes else currentBytes,
                                status = "PAUSED"
                            )
                        )
                        updateProgress(videoId, currentBytes, totalBytes, "PAUSED", 0L)
                        return
                    }

                    val read = inputStream.read(buffer)
                    if (read == -1) break

                    if (outputStream is RandomAccessFile) {
                        outputStream.write(buffer, 0, read)
                    } else if (outputStream is FileOutputStream) {
                        outputStream.write(buffer, 0, read)
                    }

                    currentBytes += read
                    bytesSinceLastUpdate += read

                    val now = System.currentTimeMillis()
                    val dt = now - lastProgressUpdate
                    if (dt >= 500) {
                        currentSpeed = (bytesSinceLastUpdate * 1000L) / dt.coerceAtLeast(1L)
                        bytesSinceLastUpdate = 0L
                        lastProgressUpdate = now

                        updateProgress(videoId, currentBytes, totalBytes, "DOWNLOADING", currentSpeed)
                        db.userDataDao().insertOrUpdateDownload(
                            initialEntity.copy(
                                downloadedBytes = currentBytes,
                                totalBytes = if (totalBytes > 0) totalBytes else currentBytes,
                                status = "DOWNLOADING"
                            )
                        )
                    }
                }
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
                try {
                    if (outputStream is RandomAccessFile) outputStream.close()
                    else if (outputStream is FileOutputStream) outputStream.close()
                } catch (_: Exception) {}
            }

            if (!pausedVideos.contains(videoId)) {
                val completedEntity = initialEntity.copy(
                    downloadedBytes = currentBytes,
                    totalBytes = if (totalBytes > 0) totalBytes else currentBytes,
                    status = "COMPLETED"
                )
                db.userDataDao().insertOrUpdateDownload(completedEntity)
                updateProgress(videoId, currentBytes, if (totalBytes > 0) totalBytes else currentBytes, "COMPLETED", 0L)
                Log.i(TAG, "Download completed: $title (${targetFile.length()} bytes)")
            }
        } catch (e: CancellationException) {
            // Cancelled
        } catch (e: Throwable) {
            Log.e(TAG, "Download error for $videoId: ${e.message}", e)
            db.userDataDao().insertOrUpdateDownload(
                initialEntity.copy(status = "FAILED")
            )
            updateProgress(videoId, existingDownloaded, 0L, "FAILED", 0L)
        } finally {
            activeJobs.remove(videoId)
        }
    }

    fun pauseDownload(videoId: String) {
        pausedVideos.add(videoId)
        activeJobs[videoId]?.cancel()
        activeJobs.remove(videoId)
    }

    fun resumeDownload(
        context: Context,
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        qualityLabel: String,
        downloadUrl: String
    ) {
        startDownload(context, videoId, title, channelName, thumbnailUrl, qualityLabel, downloadUrl)
    }

    fun deleteDownload(context: Context, videoId: String, localFilePath: String? = null) {
        pauseDownload(videoId)
        scope.launch {
            val db = AppDatabase.getInstance(context)
            db.userDataDao().deleteDownload(videoId)
            if (!localFilePath.isNullOrBlank()) {
                val file = File(localFilePath)
                if (file.exists()) file.delete()
            } else {
                val dir = getDownloadDirectory(context)
                val safeFileName = "video_${videoId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.mp4"
                val file = File(dir, safeFileName)
                if (file.exists()) file.delete()
            }
            val current = _progressMap.value.toMutableMap()
            current.remove(videoId)
            _progressMap.value = current
        }
    }

    fun clearAllDownloads(context: Context) {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        pausedVideos.clear()
        scope.launch {
            val db = AppDatabase.getInstance(context)
            db.userDataDao().clearAllDownloads()
            val dir = getDownloadDirectory(context)
            dir.listFiles()?.forEach { it.delete() }
            _progressMap.value = emptyMap()
        }
    }

    private fun updateProgress(
        videoId: String,
        downloaded: Long,
        total: Long,
        status: String,
        speed: Long
    ) {
        val fraction = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
        val state = DownloadProgressState(
            videoId = videoId,
            downloadedBytes = downloaded,
            totalBytes = total,
            progress = fraction,
            speedBytesPerSec = speed,
            status = status
        )
        val current = _progressMap.value.toMutableMap()
        current[videoId] = state
        _progressMap.value = current
    }
}
