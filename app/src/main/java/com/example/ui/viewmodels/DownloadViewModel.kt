package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.OfflineDownloadEntity
import com.example.downloader.DownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository(application)

    val downloads: StateFlow<List<OfflineDownloadEntity>> = repository.allDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startDownload(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        qualityLabel: String,
        downloadUrl: String
    ) {
        repository.enqueueDownload(videoId, title, channelName, thumbnailUrl, qualityLabel, downloadUrl)
    }

    fun pauseDownload(videoId: String) {
        repository.pauseDownload(videoId)
    }

    fun resumeDownload(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        qualityLabel: String,
        downloadUrl: String
    ) {
        repository.resumeDownload(videoId, title, channelName, thumbnailUrl, qualityLabel, downloadUrl)
    }

    fun cancelDownload(videoId: String) {
        repository.cancelDownload(videoId)
    }

    fun deleteDownload(videoId: String, filePath: String?) {
        repository.deleteDownload(videoId, filePath)
    }
}
