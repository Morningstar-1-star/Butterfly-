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
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_later_bookmarks")
data class BookmarkEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val duration: String = "",
    val providerId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "liked_videos")
data class LikedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val duration: String = "",
    val providerId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_playlists")
data class UserPlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val videosJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface UserDataDao {
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getWatchHistoryFlow(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(item: WatchHistoryEntity)

    @Query("DELETE FROM watch_history")
    suspend fun clearWatchHistory()

    @Query("SELECT * FROM watch_later_bookmarks ORDER BY timestamp DESC")
    fun getBookmarksFlow(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(item: BookmarkEntity)

    @Query("DELETE FROM watch_later_bookmarks WHERE videoId = :videoId")
    suspend fun deleteBookmark(videoId: String)

    @Query("SELECT * FROM liked_videos ORDER BY timestamp DESC")
    fun getLikedVideosFlow(): Flow<List<LikedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedVideo(item: LikedVideoEntity)

    @Query("DELETE FROM liked_videos WHERE videoId = :videoId")
    suspend fun deleteLikedVideo(videoId: String)

    @Query("SELECT * FROM user_playlists ORDER BY createdAt DESC")
    fun getPlaylistsFlow(): Flow<List<UserPlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePlaylist(playlist: UserPlaylistEntity)

    @Query("DELETE FROM user_playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)
}
