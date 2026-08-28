package com.example.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.AppDatabase
import com.example.db.BookmarkEntity
import com.example.db.LikedVideoEntity
import com.example.db.OfflineDownloadEntity
import com.example.db.UserPlaylistEntity
import com.example.db.WatchHistoryEntity
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Dedicated ViewModel managing watch history, bookmarks/favorites, liked videos, playlists, and offline downloads.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "LibraryViewModel"
    private val userDataDao = AppDatabase.getInstance(application).userDataDao()

    val watchHistory = userDataDao.getWatchHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val bookmarks = userDataDao.getBookmarksFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val likedVideos = userDataDao.getLikedVideosFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playlists = userDataDao.getPlaylistsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val offlineDownloads = userDataDao.getOfflineDownloadsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addToHistory(item: VideoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = WatchHistoryEntity(
                    videoId = item.id,
                    title = item.title,
                    channelName = item.uploaderName ?: "Unknown",
                    thumbnailUrl = item.thumbnailUrl,
                    duration = item.formattedDuration,
                    progressFraction = 0f,
                    providerId = item.providerId,
                    timestamp = System.currentTimeMillis()
                )
                userDataDao.insertWatchHistory(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save watch history", e)
            }
        }
    }

    fun toggleBookmark(item: VideoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = BookmarkEntity(
                    videoId = item.id,
                    title = item.title,
                    channelName = item.uploaderName ?: "Unknown",
                    thumbnailUrl = item.thumbnailUrl,
                    duration = item.formattedDuration,
                    providerId = item.providerId,
                    timestamp = System.currentTimeMillis()
                )
                userDataDao.insertBookmark(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle bookmark", e)
            }
        }
    }

    fun toggleLike(item: VideoItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = LikedVideoEntity(
                    videoId = item.id,
                    title = item.title,
                    channelName = item.uploaderName ?: "Unknown",
                    thumbnailUrl = item.thumbnailUrl,
                    duration = item.formattedDuration,
                    providerId = item.providerId,
                    timestamp = System.currentTimeMillis()
                )
                userDataDao.insertLikedVideo(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle like", e)
            }
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userDataDao.clearWatchHistory()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear history", e)
            }
        }
    }
}
