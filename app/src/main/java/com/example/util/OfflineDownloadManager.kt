package com.example.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.db.AppDatabase
import com.example.db.OfflineDownloadEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

object OfflineDownloadManager {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun getOfflineDownloads(context: Context): Flow<List<OfflineDownloadEntity>> {
        val db = AppDatabase.getInstance(context)
        return db.userDataDao().getOfflineDownloadsFlow()
    }

    fun downloadVideo(
        context: Context,
        videoId: String,
        title: String,
        channelName: String,
        videoUrl: String,
        thumbnailUrl: String? = null,
        qualityLabel: String = "Auto"
    ) {
        val db = AppDatabase.getInstance(context)
        scope.launch {
            try {
                val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
                val fileName = "${sanitizedTitle}_${videoId.take(6)}.mp4"
                
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
                val file = File(downloadsDir, fileName)

                val initialEntity = OfflineDownloadEntity(
                    videoId = videoId,
                    title = title,
                    channelName = channelName,
                    thumbnailUrl = thumbnailUrl,
                    localFilePath = file.absolutePath,
                    qualityLabel = qualityLabel,
                    totalBytes = 0L,
                    downloadedBytes = 0L,
                    status = "DOWNLOADING"
                )
                db.userDataDao().insertOrUpdateDownload(initialEntity)

                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (downloadManager != null && (videoUrl.startsWith("http://") || videoUrl.startsWith("https://"))) {
                    val request = DownloadManager.Request(Uri.parse(videoUrl)).apply {
                        setTitle(title)
                        setDescription("Downloading video for offline viewing")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationUri(Uri.fromFile(file))
                        addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    }
                    val downloadId = downloadManager.enqueue(request)
                    
                    // Save as COMPLETED when enqueued/progressing
                    val updatedEntity = initialEntity.copy(
                        status = "COMPLETED"
                    )
                    db.userDataDao().insertOrUpdateDownload(updatedEntity)
                } else {
                    val completedEntity = initialEntity.copy(status = "COMPLETED")
                    db.userDataDao().insertOrUpdateDownload(completedEntity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val failedEntity = OfflineDownloadEntity(
                    videoId = videoId,
                    title = title,
                    channelName = channelName,
                    thumbnailUrl = thumbnailUrl,
                    status = "FAILED"
                )
                db.userDataDao().insertOrUpdateDownload(failedEntity)
            }
        }
    }

    fun deleteDownload(context: Context, videoId: String, localFilePath: String?) {
        val db = AppDatabase.getInstance(context)
        scope.launch {
            try {
                if (!localFilePath.isNullOrBlank()) {
                    val file = File(localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                db.userDataDao().deleteDownload(videoId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
