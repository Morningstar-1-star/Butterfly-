package com.example.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.db.AppDatabase
import com.example.db.OfflineDownloadEntity
import com.example.extractor.YouTubeExtractorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class DownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_TITLE = "title"
        const val KEY_CHANNEL = "channel"
        const val KEY_THUMBNAIL = "thumbnail"
        const val KEY_QUALITY = "quality"
        const val KEY_URL = "download_url"
        const val KEY_HEADERS = "download_headers"
        const val KEY_LOCAL_PATH = "local_path"

        private const val CHANNEL_ID = "offline_downloads_channel"
        private const val NOTIFICATION_ID_BASE = 100000
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val notificationManager: NotificationManager? by lazy {
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress of offline video downloads"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun getNotificationId(videoId: String): Int {
        return NOTIFICATION_ID_BASE + (videoId.hashCode().and(0x7fffffff) % 50000)
    }

    private fun createForegroundInfo(
        notificationId: Int,
        title: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean,
        subText: String
    ): ForegroundInfo {
        createNotificationChannel()

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress, indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateProgressNotification(
        notificationId: Int,
        title: String,
        progress: Int,
        subText: String
    ) {
        try {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(subText)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress.coerceIn(0, 100), false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            notificationManager?.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification: ${e.message}")
        }
    }

    private fun showCompletedNotification(notificationId: Int, title: String) {
        try {
            createNotificationChannel()
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle("Download completed")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager?.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show completed notification: ${e.message}")
        }
    }

    private fun cancelNotification(notificationId: Int) {
        try {
            notificationManager?.cancel(notificationId)
        } catch (_: Exception) {}
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = inputData.getString(KEY_TITLE) ?: "Downloading video..."
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: "unknown"
        val notifId = getNotificationId(videoId)
        return createForegroundInfo(notifId, title, 0, 100, true, "Starting download...")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Video $videoId"
        val channelName = inputData.getString(KEY_CHANNEL) ?: ""
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL)
        val qualityLabel = inputData.getString(KEY_QUALITY) ?: "720p"
        var downloadUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val headersEncoded = inputData.getString(KEY_HEADERS) ?: ""
        val notifId = getNotificationId(videoId)

        val headersMap = mutableMapOf<String, String>()
        if (headersEncoded.isNotBlank()) {
            headersEncoded.split(";;").forEach { pair ->
                val parts = pair.split("::", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    headersMap[parts[0]] = parts[1]
                }
            }
        }
        if (!headersMap.containsKey("User-Agent")) {
            headersMap["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        }

        val db = AppDatabase.getInstance(appContext)
        val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "OfflineDownloads").apply { mkdirs() }
        val safeId = videoId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")

        // Promote to Foreground Service so Android does not kill the download
        try {
            setForeground(createForegroundInfo(notifId, title, 0, 100, true, "Preparing download..."))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to set foreground info: ${e.message}")
        }

        // Cache thumbnail locally for fully offline library viewing
        var finalThumbPath = thumbnailUrl
        if (!thumbnailUrl.isNullOrBlank() && (thumbnailUrl.startsWith("http://") || thumbnailUrl.startsWith("https://"))) {
            try {
                val thumbFile = File(dir, "thumb_$safeId.jpg")
                if (!thumbFile.exists() || thumbFile.length() == 0L) {
                    val thumbBytes = fetchBytes(thumbnailUrl, headersMap)
                    if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                        thumbFile.writeBytes(thumbBytes)
                    }
                }
                if (thumbFile.exists() && thumbFile.length() > 0L) {
                    finalThumbPath = thumbFile.absolutePath
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache thumbnail locally: ${e.message}")
            }
        }

        val isM3u8 = downloadUrl.contains(".m3u8", ignoreCase = true)
        if (isM3u8) {
            return@withContext downloadM3u8Stream(
                videoId = videoId,
                title = title,
                channelName = channelName,
                thumbnailUrl = finalThumbPath,
                qualityLabel = qualityLabel,
                m3u8Url = downloadUrl,
                headers = headersMap,
                outputDir = dir,
                safeId = safeId,
                notifId = notifId,
                db = db
            )
        }

        val targetFile = File(dir, "video_$safeId.mp4")
        var existingDownloaded = if (targetFile.exists()) targetFile.length() else 0L

        val initialEntity = OfflineDownloadEntity(
            videoId = videoId,
            title = title,
            channelName = channelName,
            thumbnailUrl = finalThumbPath,
            localFilePath = targetFile.absolutePath,
            qualityLabel = qualityLabel,
            totalBytes = 0L,
            downloadedBytes = existingDownloaded,
            status = "DOWNLOADING"
        )
        db.userDataDao().insertOrUpdateDownload(initialEntity)

        try {
            var currentUrl = downloadUrl
            var reqBuilder = Request.Builder().url(currentUrl)
            headersMap.forEach { (k, v) -> reqBuilder.header(k, v) }

            if (existingDownloaded > 0L) {
                reqBuilder.header("Range", "bytes=$existingDownloaded-")
            }

            var response = httpClient.newCall(reqBuilder.build()).execute()

            // If URL expired (HTTP 403 or 410), attempt to refresh stream from YouTube extractor
            if (!response.isSuccessful && (response.code == 403 || response.code == 410)) {
                Log.w(TAG, "Download URL expired (HTTP ${response.code}). Refreshing stream info for $videoId...")
                response.close()
                val refreshed = YouTubeExtractorHelper.fetchStreamData(videoId, appContext)
                if (refreshed is YouTubeExtractorHelper.ExtractionResult.Success) {
                    val candidate = refreshed.streamData.availableStreamOptions.firstOrNull {
                        it.isMuxed && it.qualityLabel.contains(qualityLabel.filter { c -> c.isDigit() })
                    } ?: refreshed.streamData.availableStreamOptions.firstOrNull { it.isMuxed }
                      ?: refreshed.streamData.selectedStreamOption

                    val newUrl = candidate?.videoUrl
                    if (!newUrl.isNullOrBlank()) {
                        currentUrl = newUrl
                        reqBuilder = Request.Builder().url(currentUrl)
                        headersMap.forEach { (k, v) -> reqBuilder.header(k, v) }
                        if (existingDownloaded > 0L) {
                            reqBuilder.header("Range", "bytes=$existingDownloaded-")
                        }
                        response = httpClient.newCall(reqBuilder.build()).execute()
                    }
                }
            }

            if (!response.isSuccessful && response.code != 206) {
                val code = response.code
                response.close()
                db.userDataDao().insertOrUpdateDownload(initialEntity.copy(status = "FAILED"))
                cancelNotification(notifId)
                return@withContext Result.failure(workDataOf("error" to "HTTP $code: Download request rejected"))
            }

            val body = response.body ?: run {
                response.close()
                db.userDataDao().insertOrUpdateDownload(initialEntity.copy(status = "FAILED"))
                cancelNotification(notifId)
                return@withContext Result.failure(workDataOf("error" to "Empty response body"))
            }

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
            val buffer = ByteArray(64 * 1024)
            var currentBytes = existingDownloaded
            var lastProgressUpdate = System.currentTimeMillis()
            var bytesSinceUpdate = 0L

            try {
                while (!isStopped) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break

                    if (outputStream is RandomAccessFile) {
                        outputStream.write(buffer, 0, read)
                    } else if (outputStream is FileOutputStream) {
                        outputStream.write(buffer, 0, read)
                    }

                    currentBytes += read
                    bytesSinceUpdate += read

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate >= 600L) {
                        val duration = (now - lastProgressUpdate).coerceAtLeast(1)
                        val speed = (bytesSinceUpdate * 1000L) / duration
                        bytesSinceUpdate = 0L
                        lastProgressUpdate = now

                        val pct = if (totalBytes > 0) ((currentBytes * 100L) / totalBytes).toInt() else 0
                        val subText = if (totalBytes > 0) {
                            "${formatBytes(currentBytes)} / ${formatBytes(totalBytes)} • ${formatSpeed(speed)}"
                        } else {
                            "${formatBytes(currentBytes)} • ${formatSpeed(speed)}"
                        }

                        updateProgressNotification(notifId, title, pct, subText)

                        setProgress(
                            workDataOf(
                                "downloadedBytes" to currentBytes,
                                "totalBytes" to totalBytes,
                                "speedBps" to speed,
                                "progressPercent" to pct
                            )
                        )

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
                try { response.close() } catch (_: Exception) {}
            }

            if (isStopped) {
                db.userDataDao().insertOrUpdateDownload(
                    initialEntity.copy(downloadedBytes = currentBytes, status = "PAUSED")
                )
                cancelNotification(notifId)
                return@withContext Result.retry()
            }

            val finalLength = targetFile.length()
            db.userDataDao().insertOrUpdateDownload(
                initialEntity.copy(
                    downloadedBytes = finalLength,
                    totalBytes = finalLength,
                    status = "COMPLETED"
                )
            )

            showCompletedNotification(notifId, title)
            Log.i(TAG, "Download successfully completed for $videoId: ${targetFile.absolutePath} ($finalLength bytes)")
            Result.success(workDataOf("localPath" to targetFile.absolutePath))

        } catch (e: Exception) {
            Log.e(TAG, "Download worker exception for $videoId: ${e.message}", e)
            db.userDataDao().insertOrUpdateDownload(initialEntity.copy(status = "FAILED"))
            cancelNotification(notifId)
            Result.failure(workDataOf("error" to (e.message ?: "Download failed")))
        }
    }

    private suspend fun downloadM3u8Stream(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        qualityLabel: String,
        m3u8Url: String,
        headers: Map<String, String>,
        outputDir: File,
        safeId: String,
        notifId: Int,
        db: AppDatabase
    ): Result = withContext(Dispatchers.IO) {
        val hlsFolder = File(outputDir, "hls_$safeId").apply { mkdirs() }
        val localIndexFile = File(hlsFolder, "index.m3u8")

        val initialEntity = OfflineDownloadEntity(
            videoId = videoId,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            localFilePath = localIndexFile.absolutePath,
            qualityLabel = qualityLabel,
            totalBytes = 0L,
            downloadedBytes = 0L,
            status = "DOWNLOADING"
        )
        db.userDataDao().insertOrUpdateDownload(initialEntity)

        try {
            val downloader = HlsSegmentDownloader(
                httpClient = httpClient,
                maxParallelWorkers = 4,
                maxRetriesPerSegment = 3
            )

            val downloadResult = downloader.downloadPlaylist(
                m3u8Url = m3u8Url,
                outputFolder = hlsFolder,
                headers = headers
            ) { progress ->
                if (isStopped) {
                    downloader.cancel()
                    db.userDataDao().insertOrUpdateDownload(
                        initialEntity.copy(
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.estimatedTotalBytes,
                            status = "PAUSED"
                        )
                    )
                } else {
                    val subText = "${progress.completedSegments}/${progress.totalSegments} segments • ${formatBytes(progress.downloadedBytes)} • ${formatSpeed(progress.speedBps)}"
                    updateProgressNotification(notifId, title, progress.progressPercent, subText)

                    setProgress(
                        workDataOf(
                            "downloadedBytes" to progress.downloadedBytes,
                            "totalBytes" to progress.estimatedTotalBytes,
                            "speedBps" to progress.speedBps,
                            "progressPercent" to progress.progressPercent
                        )
                    )

                    db.userDataDao().insertOrUpdateDownload(
                        initialEntity.copy(
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.estimatedTotalBytes,
                            status = "DOWNLOADING"
                        )
                    )
                }
            }

            if (isStopped) {
                cancelNotification(notifId)
                return@withContext Result.retry()
            }

            val savedIndex = downloadResult.getOrThrow()
            val totalSize = savedIndex.parentFile?.listFiles()?.sumOf { it.length() } ?: 1024L

            db.userDataDao().insertOrUpdateDownload(
                initialEntity.copy(
                    downloadedBytes = totalSize,
                    totalBytes = totalSize,
                    status = "COMPLETED"
                )
            )

            showCompletedNotification(notifId, title)
            Log.i(TAG, "M3U8 download finished: ${savedIndex.absolutePath} ($totalSize bytes)")
            Result.success(workDataOf("localPath" to savedIndex.absolutePath))

        } catch (e: Exception) {
            Log.e(TAG, "M3U8 download failed for $videoId: ${e.message}", e)
            db.userDataDao().insertOrUpdateDownload(initialEntity.copy(status = "FAILED"))
            cancelNotification(notifId)
            Result.failure(workDataOf("error" to (e.message ?: "M3U8 download failure")))
        }
    }

    private fun fetchBytes(url: String, headers: Map<String, String>): ByteArray? {
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        return try {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format("%.1f MB/s", mb)
        } else {
            String.format("%.0f KB/s", kb)
        }
    }
}
