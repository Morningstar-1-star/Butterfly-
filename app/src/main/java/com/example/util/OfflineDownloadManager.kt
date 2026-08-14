package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.db.AppDatabase
import com.example.db.OfflineDownloadEntity
import com.example.model.PlayableStreamOption
import com.example.model.VideoItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadProgressInfo(
    val videoId: String,
    val progress: Float, // 0.0 to 1.0
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedKbps: Long = 0L,
    val status: String = "DOWNLOADING" // DOWNLOADING, PAUSED, COMPLETED, FAILED
)

object OfflineDownloadManager {

    private const val TAG = "OfflineDownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // In-memory active download jobs and progress for immediate UI reactivity
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedFlags = ConcurrentHashMap<String, Boolean>()
    
    private val _liveProgress = MutableStateFlow<Map<String, DownloadProgressInfo>>(emptyMap())
    val liveProgress = _liveProgress.asStateFlow()

    fun getOfflineDownloads(context: Context): Flow<List<OfflineDownloadEntity>> {
        val db = AppDatabase.getInstance(context)
        return db.userDataDao().getOfflineDownloadsFlow()
    }

    /**
     * Start or queue a download using direct stream URL
     */
    fun downloadVideo(
        context: Context,
        videoId: String,
        title: String,
        channelName: String,
        videoUrl: String,
        thumbnailUrl: String? = null,
        qualityLabel: String = "720p",
        headers: Map<String, String> = emptyMap()
    ) {
        if (videoUrl.isBlank()) {
            Log.e(TAG, "Cannot download with blank videoUrl for videoId: $videoId")
            return
        }

        // Cancel existing job if running to restart cleanly
        activeJobs[videoId]?.cancel()
        pausedFlags[videoId] = false

        val appContext = context.applicationContext
        val db = AppDatabase.getInstance(appContext)

        val job = scope.launch {
            try {
                val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(50)
                val fileName = "${sanitizedTitle}_${videoId.take(8)}_${qualityLabel.replace(" ", "_")}.mp4"
                
                val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) 
                    ?: appContext.filesDir
                val targetFile = File(downloadsDir, fileName)

                val initialEntity = OfflineDownloadEntity(
                    videoId = videoId,
                    title = title,
                    channelName = channelName,
                    thumbnailUrl = thumbnailUrl,
                    localFilePath = targetFile.absolutePath,
                    qualityLabel = qualityLabel,
                    totalBytes = 0L,
                    downloadedBytes = if (targetFile.exists()) targetFile.length() else 0L,
                    status = "DOWNLOADING",
                    timestamp = System.currentTimeMillis()
                )
                db.userDataDao().insertOrUpdateDownload(initialEntity)

                updateProgress(
                    videoId = videoId,
                    progress = 0f,
                    downloaded = initialEntity.downloadedBytes,
                    total = 0L,
                    status = "DOWNLOADING"
                )

                // Execute chunked stream download
                performDownload(
                    appContext = appContext,
                    videoId = videoId,
                    url = videoUrl,
                    targetFile = targetFile,
                    headers = headers,
                    initialEntity = initialEntity
                )

            } catch (e: CancellationException) {
                Log.d(TAG, "Download cancelled for $videoId")
            } catch (e: Exception) {
                Log.e(TAG, "Download error for $videoId: ${e.message}", e)
                val failedEntity = OfflineDownloadEntity(
                    videoId = videoId,
                    title = title,
                    channelName = channelName,
                    thumbnailUrl = thumbnailUrl,
                    qualityLabel = qualityLabel,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis()
                )
                db.userDataDao().insertOrUpdateDownload(failedEntity)
                updateProgress(
                    videoId = videoId,
                    progress = 0f,
                    downloaded = 0L,
                    total = 0L,
                    status = "FAILED"
                )
            } finally {
                activeJobs.remove(videoId)
            }
        }

        activeJobs[videoId] = job
    }

    private suspend fun performDownload(
        appContext: Context,
        videoId: String,
        url: String,
        targetFile: File,
        headers: Map<String, String>,
        initialEntity: OfflineDownloadEntity
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(appContext)
        val existingLength = if (targetFile.exists()) targetFile.length() else 0L

        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        if (existingLength > 0L) {
            reqBuilder.header("Range", "bytes=$existingLength-")
        }

        val request = reqBuilder.build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful && response.code != 206) {
            // If Range request failed with 416 (Range Not Satisfiable), delete file and retry fresh
            if (response.code == 416 || response.code == 403 || response.code == 400) {
                targetFile.delete()
                val freshReq = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val freshResp = httpClient.newCall(freshReq).execute()
                if (!freshResp.isSuccessful) {
                    throw IllegalStateException("HTTP ${freshResp.code}: ${freshResp.message}")
                }
                processBodyStream(freshResp, targetFile, 0L, videoId, initialEntity, db)
                return@withContext
            }
            throw IllegalStateException("HTTP ${response.code}: ${response.message}")
        }

        val isPartial = response.code == 206
        val startingOffset = if (isPartial) existingLength else 0L
        if (!isPartial && targetFile.exists()) {
            targetFile.delete()
        }

        processBodyStream(response, targetFile, startingOffset, videoId, initialEntity, db)
    }

    private suspend fun processBodyStream(
        response: okhttp3.Response,
        targetFile: File,
        startingOffset: Long,
        videoId: String,
        initialEntity: OfflineDownloadEntity,
        db: AppDatabase
    ) = withContext(Dispatchers.IO) {
        val body = response.body ?: throw IllegalStateException("Empty response body")
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0) startingOffset + contentLength else 0L

        val outputStream = if (startingOffset > 0L) {
            FileOutputStream(targetFile, true)
        } else {
            FileOutputStream(targetFile, false)
        }

        val buffer = ByteArray(64 * 1024) // 64 KB buffer
        var downloaded = startingOffset
        val inputStream = body.byteStream()

        var lastDbUpdateMs = System.currentTimeMillis()
        var lastSpeedCalcMs = System.currentTimeMillis()
        var bytesSinceLastSpeedCalc = 0L
        var currentSpeedKbps = 0L

        try {
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                // Check if user paused download
                if (pausedFlags[videoId] == true) {
                    outputStream.flush()
                    val pausedEntity = initialEntity.copy(
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes,
                        status = "PAUSED"
                    )
                    db.userDataDao().insertOrUpdateDownload(pausedEntity)
                    val prog = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0.5f
                    updateProgress(videoId, prog, downloaded, totalBytes, "PAUSED")
                    return@withContext
                }

                outputStream.write(buffer, 0, read)
                downloaded += read
                bytesSinceLastSpeedCalc += read

                val now = System.currentTimeMillis()

                // Calculate speed every 800ms
                if (now - lastSpeedCalcMs >= 800) {
                    val timeDeltaSec = (now - lastSpeedCalcMs) / 1000.0
                    currentSpeedKbps = if (timeDeltaSec > 0) ((bytesSinceLastSpeedCalc / 1024.0) / timeDeltaSec).toLong() else 0L
                    lastSpeedCalcMs = now
                    bytesSinceLastSpeedCalc = 0L
                }

                // Update UI state & Database periodically (every 700ms or when significant progress is made)
                if (now - lastDbUpdateMs >= 700) {
                    outputStream.flush() // Flush so player can read immediately for progressive playback!
                    val progressFraction = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                    updateProgress(
                        videoId = videoId,
                        progress = progressFraction,
                        downloaded = downloaded,
                        total = totalBytes,
                        speedKbps = currentSpeedKbps,
                        status = "DOWNLOADING"
                    )

                    val updatedEntity = initialEntity.copy(
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes,
                        status = "DOWNLOADING"
                    )
                    db.userDataDao().insertOrUpdateDownload(updatedEntity)
                    lastDbUpdateMs = now
                }
            }

            outputStream.flush()

            // Download Complete!
            val completedEntity = initialEntity.copy(
                downloadedBytes = downloaded,
                totalBytes = if (totalBytes > 0) totalBytes else downloaded,
                status = "COMPLETED",
                timestamp = System.currentTimeMillis()
            )
            db.userDataDao().insertOrUpdateDownload(completedEntity)

            updateProgress(
                videoId = videoId,
                progress = 1.0f,
                downloaded = downloaded,
                total = downloaded,
                status = "COMPLETED"
            )
            Log.d(TAG, "Download finished successfully for $videoId: ${targetFile.length()} bytes")

        } finally {
            try {
                outputStream.close()
                inputStream.close()
                response.close()
            } catch (e: Exception) {
                // ignore close errors
            }
        }
    }

    private fun updateProgress(
        videoId: String,
        progress: Float,
        downloaded: Long,
        total: Long,
        status: String,
        speedKbps: Long = 0L
    ) {
        val current = _liveProgress.value.toMutableMap()
        current[videoId] = DownloadProgressInfo(
            videoId = videoId,
            progress = progress.coerceIn(0f, 1f),
            downloadedBytes = downloaded,
            totalBytes = total,
            speedKbps = speedKbps,
            status = status
        )
        _liveProgress.value = current
    }

    fun pauseDownload(context: Context, videoId: String) {
        pausedFlags[videoId] = true
        activeJobs[videoId]?.cancel()
        activeJobs.remove(videoId)

        scope.launch {
            val db = AppDatabase.getInstance(context)
            val existing = db.userDataDao().getDownloadById(videoId)
            if (existing != null) {
                db.userDataDao().insertOrUpdateDownload(existing.copy(status = "PAUSED"))
                val prog = if (existing.totalBytes > 0) existing.downloadedBytes.toFloat() / existing.totalBytes else 0f
                updateProgress(videoId, prog, existing.downloadedBytes, existing.totalBytes, "PAUSED")
            }
        }
    }

    fun resumeDownload(context: Context, videoId: String) {
        scope.launch {
            val db = AppDatabase.getInstance(context)
            val existing = db.userDataDao().getDownloadById(videoId) ?: return@launch
            pausedFlags[videoId] = false

            // Try resuming using same stream or trigger extraction if needed
            downloadVideo(
                context = context,
                videoId = existing.videoId,
                title = existing.title,
                channelName = existing.channelName,
                videoUrl = existing.localFilePath, // Will re-verify or fallback
                thumbnailUrl = existing.thumbnailUrl,
                qualityLabel = existing.qualityLabel
            )
        }
    }

    fun deleteDownload(context: Context, videoId: String, localFilePath: String? = null) {
        // Cancel active job
        pausedFlags[videoId] = true
        activeJobs[videoId]?.cancel()
        activeJobs.remove(videoId)

        val current = _liveProgress.value.toMutableMap()
        current.remove(videoId)
        _liveProgress.value = current

        val db = AppDatabase.getInstance(context)
        scope.launch {
            try {
                val path = localFilePath ?: db.userDataDao().getDownloadById(videoId)?.localFilePath
                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                db.userDataDao().deleteDownload(videoId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete download: ${e.message}")
            }
        }
    }

    fun clearAllDownloads(context: Context) {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        pausedFlags.clear()
        _liveProgress.value = emptyMap()

        val db = AppDatabase.getInstance(context)
        scope.launch {
            try {
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
                downloadsDir.listFiles()?.forEach { f ->
                    if (f.name.endsWith(".mp4") || f.name.endsWith(".m4a")) {
                        f.delete()
                    }
                }
                db.userDataDao().clearAllDownloads()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear all downloads: ${e.message}")
            }
        }
    }

    /**
     * Formats bytes into human readable MB/GB string
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}
