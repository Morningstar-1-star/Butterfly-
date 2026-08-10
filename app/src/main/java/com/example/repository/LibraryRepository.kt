package com.example.repository

import android.content.Context
import com.example.db.AppDatabase
import com.example.db.BookmarkEntity
import com.example.db.LikedVideoEntity
import com.example.db.UserDataDao
import com.example.db.UserPlaylistEntity
import com.example.db.WatchHistoryEntity
import com.example.model.UserPlaylist
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LibraryRepository(context: Context) {

    private val dao: UserDataDao = AppDatabase.getInstance(context).userDataDao()

    val watchHistoryFlow: Flow<List<VideoItem>> = dao.getWatchHistoryFlow().map { list ->
        list.map { entity ->
            VideoItem(
                id = entity.videoId,
                title = entity.title,
                uploaderName = entity.channelName,
                thumbnailUrl = entity.thumbnailUrl,
                providerId = entity.providerId,
                durationSeconds = 0L
            )
        }
    }

    val watchLaterFlow: Flow<List<VideoItem>> = dao.getBookmarksFlow().map { list ->
        list.map { entity ->
            VideoItem(
                id = entity.videoId,
                title = entity.title,
                uploaderName = entity.channelName,
                thumbnailUrl = entity.thumbnailUrl,
                providerId = entity.providerId,
                durationSeconds = 0L
            )
        }
    }

    val likedVideosFlow: Flow<List<VideoItem>> = dao.getLikedVideosFlow().map { list ->
        list.map { entity ->
            VideoItem(
                id = entity.videoId,
                title = entity.title,
                uploaderName = entity.channelName,
                thumbnailUrl = entity.thumbnailUrl,
                providerId = entity.providerId,
                durationSeconds = 0L
            )
        }
    }

    val userPlaylistsFlow: Flow<List<UserPlaylist>> = dao.getPlaylistsFlow().map { list ->
        list.map { entity ->
            UserPlaylist(
                id = entity.id,
                title = entity.title,
                videos = deserializeVideos(entity.videosJson)
            )
        }
    }

    suspend fun recordWatchHistory(video: VideoItem) = withContext(Dispatchers.IO) {
        val entity = WatchHistoryEntity(
            videoId = video.id,
            title = video.title,
            channelName = video.uploaderName,
            thumbnailUrl = video.thumbnailUrl,
            providerId = video.providerId
        )
        dao.insertWatchHistory(entity)
    }

    suspend fun clearWatchHistory() = withContext(Dispatchers.IO) {
        dao.clearWatchHistory()
    }

    suspend fun addBookmark(video: VideoItem) = withContext(Dispatchers.IO) {
        dao.insertBookmark(
            BookmarkEntity(
                videoId = video.id,
                title = video.title,
                channelName = video.uploaderName,
                thumbnailUrl = video.thumbnailUrl,
                providerId = video.providerId
            )
        )
    }

    suspend fun removeBookmark(videoId: String) = withContext(Dispatchers.IO) {
        dao.deleteBookmark(videoId)
    }

    suspend fun addLikedVideo(video: VideoItem) = withContext(Dispatchers.IO) {
        dao.insertLikedVideo(
            LikedVideoEntity(
                videoId = video.id,
                title = video.title,
                channelName = video.uploaderName,
                thumbnailUrl = video.thumbnailUrl,
                providerId = video.providerId
            )
        )
    }

    suspend fun removeLikedVideo(videoId: String) = withContext(Dispatchers.IO) {
        dao.deleteLikedVideo(videoId)
    }

    suspend fun createPlaylist(title: String, initialVideo: VideoItem? = null) = withContext(Dispatchers.IO) {
        val id = "pl_${System.currentTimeMillis()}"
        val initialList = if (initialVideo != null) listOf(initialVideo) else emptyList()
        val entity = UserPlaylistEntity(
            id = id,
            title = title.ifBlank { "New Playlist" },
            videosJson = serializeVideos(initialList)
        )
        dao.insertOrUpdatePlaylist(entity)
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlistId)
    }

    private fun serializeVideos(videos: List<VideoItem>): String {
        val arr = JSONArray()
        videos.forEach { v ->
            val obj = JSONObject()
            obj.put("id", v.id)
            obj.put("title", v.title)
            obj.put("uploaderName", v.uploaderName)
            obj.put("thumbnailUrl", v.thumbnailUrl)
            obj.put("providerId", v.providerId)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserializeVideos(jsonStr: String): List<VideoItem> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<VideoItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VideoItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        uploaderName = obj.optString("uploaderName"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        providerId = obj.optString("providerId").takeIf { !it.isNullOrEmpty() },
                        durationSeconds = 0L
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
