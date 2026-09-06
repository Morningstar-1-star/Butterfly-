package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "response_cache")
data class ResponseCacheEntity(
    @PrimaryKey val key: String,
    val responseBody: String,
    val statusCode: Int = 200,
    val eTag: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val ttlMs: Long = 300_000L // Default 5 minutes
)

@Entity(tableName = "source_metrics")
data class SourceMetricsEntity(
    @PrimaryKey val metricKey: String,
    val providerId: String,
    val mediaType: String = "unknown",
    val quality: String = "auto",
    val networkResult: String = "SUCCESS",
    val startupLatencyMs: Long = 0L,
    val failureReason: String = "",
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val totalStartupTimeMs: Long = 0L,
    val bufferingEventsCount: Int = 0,
    val crashCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val duration: String = "",
    val progressFraction: Float = 0f,
    val providerId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val originalTitle: String = title,
    val translatedTitleEN: String? = null,
    val translatedTitleHI: String? = null,
    val detectedLanguage: String? = null
)

@Entity(tableName = "watch_later_bookmarks")
data class BookmarkEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val duration: String = "",
    val providerId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val originalTitle: String = title,
    val translatedTitleEN: String? = null,
    val translatedTitleHI: String? = null,
    val detectedLanguage: String? = null
)

@Entity(tableName = "liked_videos")
data class LikedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val duration: String = "",
    val providerId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val originalTitle: String = title,
    val translatedTitleEN: String? = null,
    val translatedTitleHI: String? = null,
    val detectedLanguage: String? = null
)

@Entity(tableName = "user_playlists")
data class UserPlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val videosJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "offline_downloads")
data class OfflineDownloadEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val localFilePath: String = "",
    val qualityLabel: String = "Auto",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = "COMPLETED", // DOWNLOADING, COMPLETED, FAILED
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_video_metadata")
data class VideoMetadataCacheEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val duration: String = "",
    val streamDataJson: String? = null,
    val providerId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val ttlMs: Long = 86_400_000L, // Default 24 hours
    val originalTitle: String = title,
    val translatedTitleEN: String? = null,
    val translatedTitleHI: String? = null,
    val detectedLanguage: String? = null,
    val translatedDescriptionEN: String? = null,
    val translatedDescriptionHI: String? = null
)

@Entity(tableName = "translation_cache", primaryKeys = ["sourceText", "targetLang"])
data class TranslationCacheEntity(
    val sourceText: String,
    val targetLang: String,
    val translatedText: String,
    val detectedLanguage: String? = null,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE sourceText = :text AND targetLang = :targetLang LIMIT 1")
    suspend fun getTranslation(text: String, targetLang: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(entity: TranslationCacheEntity)

    @Query("DELETE FROM translation_cache")
    suspend fun clearTranslationCache()

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun getCacheCount(): Int
}

@Entity(tableName = "preloaded_videos")
data class PreloadedVideoCacheEntity(
    @PrimaryKey val videoId: String,
    val streamUrl: String,
    val hlsUrl: String? = null,
    val qualityLabel: String = "Auto",
    val cachedHeadersJson: String? = null,
    val localCachePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    fun getSearchHistoryFlow(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    suspend fun getAllSearchHistoryList(): List<SearchHistoryEntity>

    @Query("SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentQueriesList(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Dao
interface VideoCacheDao {
    @Query("SELECT * FROM cached_video_metadata WHERE videoId = :videoId LIMIT 1")
    suspend fun getVideoMetadata(videoId: String): VideoMetadataCacheEntity?

    @Query("SELECT * FROM cached_video_metadata ORDER BY timestamp DESC LIMIT 50")
    fun getAllCachedMetadataFlow(): Flow<List<VideoMetadataCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoMetadata(item: VideoMetadataCacheEntity)

    @Query("DELETE FROM cached_video_metadata WHERE videoId = :videoId")
    suspend fun deleteVideoMetadata(videoId: String)

    @Query("DELETE FROM cached_video_metadata")
    suspend fun clearAllMetadata()

    @Query("SELECT * FROM preloaded_videos WHERE videoId = :videoId LIMIT 1")
    suspend fun getPreloadedVideo(videoId: String): PreloadedVideoCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreloadedVideo(item: PreloadedVideoCacheEntity)

    @Query("DELETE FROM preloaded_videos WHERE videoId = :videoId")
    suspend fun deletePreloadedVideo(videoId: String)

    @Query("DELETE FROM preloaded_videos")
    suspend fun clearAllPreloads()
}

@Dao
interface UserDataDao {
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getWatchHistoryFlow(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    suspend fun getAllWatchHistoryList(): List<WatchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(item: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE videoId NOT IN (SELECT videoId FROM watch_history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trimWatchHistory(limit: Int = 50)

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun deleteWatchHistory(videoId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearWatchHistory()

    @Query("SELECT * FROM watch_later_bookmarks ORDER BY timestamp DESC")
    fun getBookmarksFlow(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM watch_later_bookmarks ORDER BY timestamp DESC")
    suspend fun getAllBookmarksList(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(item: BookmarkEntity)

    @Query("DELETE FROM watch_later_bookmarks WHERE videoId = :videoId")
    suspend fun deleteBookmark(videoId: String)

    @Query("DELETE FROM watch_later_bookmarks")
    suspend fun clearBookmarks()

    @Query("SELECT * FROM liked_videos ORDER BY timestamp DESC")
    fun getLikedVideosFlow(): Flow<List<LikedVideoEntity>>

    @Query("SELECT * FROM liked_videos ORDER BY timestamp DESC")
    suspend fun getAllLikedVideosList(): List<LikedVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedVideo(item: LikedVideoEntity)

    @Query("DELETE FROM liked_videos WHERE videoId = :videoId")
    suspend fun deleteLikedVideo(videoId: String)

    @Query("SELECT * FROM user_playlists ORDER BY createdAt DESC")
    fun getPlaylistsFlow(): Flow<List<UserPlaylistEntity>>

    @Query("SELECT * FROM user_playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylistsList(): List<UserPlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePlaylist(playlist: UserPlaylistEntity)

    @Query("DELETE FROM user_playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("SELECT * FROM offline_downloads ORDER BY timestamp DESC")
    fun getOfflineDownloadsFlow(): Flow<List<OfflineDownloadEntity>>

    @Query("SELECT * FROM offline_downloads ORDER BY timestamp DESC")
    suspend fun getAllOfflineDownloadsList(): List<OfflineDownloadEntity>

    @Query("SELECT * FROM offline_downloads WHERE videoId = :videoId LIMIT 1")
    suspend fun getDownloadById(videoId: String): OfflineDownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDownload(item: OfflineDownloadEntity)

    @Query("DELETE FROM offline_downloads WHERE videoId = :videoId")
    suspend fun deleteDownload(videoId: String)

    @Query("DELETE FROM offline_downloads")
    suspend fun clearAllDownloads()
}
