package com.example.downloader

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.db.AppDatabase
import com.example.db.OfflineDownloadEntity
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
        const val KEY_LOCAL_PATH = "local_path"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Video $videoId"
        val channelName = inputData.getString(KEY_CHANNEL) ?: ""
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL)
        val qualityLabel = inputData.getString(KEY_QUALITY) ?: "720p"
        val downloadUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure()

        val db = AppDatabase.getInstance(appContext)

        if (!downloadUrl.startsWith("http://", ignoreCase = true) && !downloadUrl.startsWith("https://", ignoreCase = true)) {
            Log.e(TAG, "Invalid download URL: $downloadUrl")
            return@withContext Result.failure(workDataOf("error" to "Invalid network download URL"))
        }

        val isM3u8 = downloadUrl.contains(".m3u8", ignoreCase = true)
        val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "OfflineDownloads").apply { mkdirs() }
        val safeId = videoId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")

        if (isM3u8) {
            return@withContext downloadM3u8Stream(
                videoId = videoId,
                title = title,
                channelName = channelName,
                thumbnailUrl = thumbnailUrl,
                qualityLabel = qualityLabel,
                m3u8Url = downloadUrl,
                outputDir = dir,
                safeId = safeId,
                db = db
            )
        }

        val targetFile = File(dir, "video_$safeId.mp4")
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

            if (existingDownloaded > 0L) {
                reqBuilder.header("Range", "bytes=$existingDownloaded-")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 403 || response.code == 410) {
                    db.userDataDao().insertOrUpdateDownload(initialEntity.copy(status = "FAILED"))
                    return@withContext Result.failure(workDataOf("error" to "Signed URL expired (HTTP ${response.code})"))
                }
                throw Exception("HTTP ${response.code}: ${response.message}")
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
            var bytesSinceUpdate = 0L
            val md5Digest = MessageDigest.getInstance("MD5")

            try {
                while (!isStopped) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break

                    if (outputStream is RandomAccessFile) {
                        outputStream.write(buffer, 0, read)
                    } else if (outputStream is FileOutputStream) {
                        outputStream.write(buffer, 0, read)
                    }

                    md5Digest.update(buffer, 0, read)
                    currentBytes += read
                    bytesSinceUpdate += read

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate >= 500) {
                        val speed = (bytesSinceUpdate * 1000L) / (now - lastProgressUpdate).coerceAtLeast(1)
                        bytesSinceUpdate = 0L
                        lastProgressUpdate = now

                        setProgress(workDataOf(
                            "downloadedBytes" to currentBytes,
                            "totalBytes" to totalBytes,
                            "speedBps" to speed
                        ))

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

            if (isStopped) {
                db.userDataDao().insertOrUpdateDownload(
                    initialEntity.copy(downloadedBytes = currentBytes, status = "PAUSED")
                )
                return@withContext Result.retry()
            }

            val sha256Hex = calculateFileSha256(targetFile)

            db.userDataDao().insertOrUpdateDownload(
                initialEntity.copy(
                    downloadedBytes = currentBytes,
                    totalBytes = if (totalBytes > 0) totalBytes else currentBytes,
                    status = "COMPLETED"
                )
            )

            Log.i(TAG, "Download completed for $videoId ($currentBytes bytes, SHA-256: $sha256Hex)")
            Result.success(workDataOf("localPath" to targetFile.absolutePath, "checksum" to sha256Hex))

        } catch (e: Exception) {
            Log.e(TAG, "Download worker failed for $videoId: ${e.message}", e)
            db.userDataDao().insertOrUpdateDownload(initialEntity.copy(status = "FAILED"))
            Result.failure(workDataOf("error" to (e.message ?: "Unknown download failure")))
        }
    }

    private suspend fun downloadM3u8Stream(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        qualityLabel: String,
        m3u8Url: String,
        outputDir: File,
        safeId: String,
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
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to m3u8Url
                )
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
                return@withContext Result.retry()
            }

            val savedIndex = downloadResult.getOrThrow()

            db.userDataDao().insertOrUpdateDownload(
                initialEntity.copy(
                    downloadedBytes = savedIndex.parentFile?.listFiles()?.sumOf { it.length() } ?: 1024L,
                    totalBytes = savedIndex.parentFile?.listFiles()?.sumOf { it.length() } ?: 1024L,
                    status = "COMPLETED"
                )
            )

            Log.i(TAG, "M3U8 segmented download completed successfully for $videoId at ${savedIndex.absolutePath}")
            Result.success(workDataOf("localPath" to savedIndex.absolutePath))

        } catch (e: Exception) {
            Log.e(TAG, "M3U8 download failed for $videoId: ${e.message}", e)
            db.userDataDao().insertOrUpdateDownload(initialEntity.copy(status = "FAILED"))
            Result.failure(workDataOf("error" to (e.message ?: "M3U8 download failure")))
        }
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return try {
            if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
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

    private fun fetchText(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        return httpClient.newCall(req).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }

    private fun fetchBytes(url: String): ByteArray? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        return httpClient.newCall(req).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }

    private fun calculateFileSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buf = ByteArray(64 * 1024)
                var bytesRead: Int
                while (stream.read(buf).also { bytesRead = it } != -1) {
                    digest.update(buf, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "sha256_error"
        }
    }
}
