package com.example.db

import android.content.Context
import com.example.model.StreamData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

class VideoCacheRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val videoCacheDao = db.videoCacheDao()
    private val searchHistoryDao = db.searchHistoryDao()

    val searchHistoryFlow: Flow<List<String>> = searchHistoryDao.getSearchHistoryFlow().map { list ->
        list.map { it.query }
    }

    val cachedMetadataFlow: Flow<List<VideoMetadataCacheEntity>> = videoCacheDao.getAllCachedMetadataFlow()

    suspend fun getRecentSearches(): List<String> = withContext(Dispatchers.IO) {
        searchHistoryDao.getRecentQueriesList()
    }

    suspend fun addSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isNotBlank()) {
            searchHistoryDao.insertSearchQuery(SearchHistoryEntity(query = q))
        }
    }

    suspend fun removeSearchQuery(query: String) = withContext(Dispatchers.IO) {
        searchHistoryDao.deleteSearchQuery(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchHistoryDao.clearSearchHistory()
    }

    suspend fun cacheVideoMetadata(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        description: String?,
        duration: String = "",
        providerId: String? = null,
        streamDataJson: String? = null
    ) = withContext(Dispatchers.IO) {
        val entity = VideoMetadataCacheEntity(
            videoId = videoId,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            description = description,
            duration = duration,
            streamDataJson = streamDataJson,
            providerId = providerId,
            timestamp = System.currentTimeMillis()
        )
        videoCacheDao.insertVideoMetadata(entity)
    }

    suspend fun getCachedVideoMetadata(videoId: String): VideoMetadataCacheEntity? = withContext(Dispatchers.IO) {
        val entity = videoCacheDao.getVideoMetadata(videoId) ?: return@withContext null
        if (System.currentTimeMillis() > entity.timestamp + entity.ttlMs) {
            videoCacheDao.deleteVideoMetadata(videoId)
            return@withContext null
        }
        entity
    }

    suspend fun cachePreloadedStream(
        videoId: String,
        streamUrl: String,
        hlsUrl: String? = null,
        qualityLabel: String = "Auto",
        headersJson: String? = null,
        localCachePath: String? = null
    ) = withContext(Dispatchers.IO) {
        val entity = PreloadedVideoCacheEntity(
            videoId = videoId,
            streamUrl = streamUrl,
            hlsUrl = hlsUrl,
            qualityLabel = qualityLabel,
            cachedHeadersJson = headersJson,
            localCachePath = localCachePath,
            timestamp = System.currentTimeMillis()
        )
        videoCacheDao.insertPreloadedVideo(entity)
    }

    suspend fun getPreloadedStream(videoId: String): PreloadedVideoCacheEntity? = withContext(Dispatchers.IO) {
        videoCacheDao.getPreloadedVideo(videoId)
    }

    suspend fun clearAllVideoCaches() = withContext(Dispatchers.IO) {
        videoCacheDao.clearAllMetadata()
        videoCacheDao.clearAllPreloads()
    }
}
