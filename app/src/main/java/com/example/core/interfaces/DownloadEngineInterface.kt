package com.example.core.interfaces

import com.example.db.OfflineDownloadEntity
import kotlinx.coroutines.flow.Flow

interface DownloadEngineInterface {
    val allDownloads: Flow<List<OfflineDownloadEntity>>
    fun enqueueDownload(videoId: String, title: String, channelName: String, thumbnailUrl: String?, qualityLabel: String, downloadUrl: String)
    fun pauseDownload(videoId: String)
    fun resumeDownload(videoId: String, title: String, channelName: String, thumbnailUrl: String?, qualityLabel: String, downloadUrl: String)
    fun cancelDownload(videoId: String)
    fun deleteDownload(videoId: String, filePath: String?)
}
