package com.example.downloader

enum class DownloadStatus {
    IDLE,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadState(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val qualityLabel: String,
    val downloadUrl: String,
    val localFilePath: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBps: Long = 0L,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val errorMessage: String? = null,
    val isM3u8Playlist: Boolean = false,
    val checksumMd5: String? = null
)
