package com.example.downloader

import android.content.Context
import androidx.work.*
import com.example.db.AppDatabase
import com.example.db.OfflineDownloadEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

class DownloadRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val allDownloads: Flow<List<OfflineDownloadEntity>> = db.userDataDao().getOfflineDownloadsFlow()

    fun enqueueDownload(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        qualityLabel: String,
        downloadUrl: String
    ) {
        val workData = workDataOf(
            DownloadWorker.KEY_VIDEO_ID to videoId,
            DownloadWorker.KEY_TITLE to title,
            DownloadWorker.KEY_CHANNEL to channelName,
            DownloadWorker.KEY_THUMBNAIL to thumbnailUrl,
            DownloadWorker.KEY_QUALITY to qualityLabel,
            DownloadWorker.KEY_URL to downloadUrl
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadWork = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag("download_$videoId")
            .build()

        workManager.enqueueUniqueWork(
            "download_$videoId",
            ExistingWorkPolicy.REPLACE,
            downloadWork
        )
    }

    fun pauseDownload(videoId: String) {
        workManager.cancelUniqueWork("download_$videoId")
        scope.launch {
            val existing = db.userDataDao().getDownloadById(videoId)
            if (existing != null) {
                db.userDataDao().insertOrUpdateDownload(existing.copy(status = "PAUSED"))
            }
        }
    }

    fun resumeDownload(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        qualityLabel: String,
        downloadUrl: String
    ) {
        enqueueDownload(videoId, title, channelName, thumbnailUrl, qualityLabel, downloadUrl)
    }

    fun cancelDownload(videoId: String) {
        workManager.cancelUniqueWork("download_$videoId")
        scope.launch {
            db.userDataDao().deleteDownload(videoId)
        }
    }

    fun deleteDownload(videoId: String, filePath: String?) {
        workManager.cancelUniqueWork("download_$videoId")
        scope.launch {
            if (!filePath.isNullOrBlank()) {
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            db.userDataDao().deleteDownload(videoId)
        }
    }
}
