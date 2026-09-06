package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["entityType", "entityId"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entityType: String, // "WATCH_HISTORY", "BOOKMARK", "LIKED_VIDEO", "USER_PLAYLIST", "SEARCH_HISTORY", "BUNKR_ALBUM", "BUNKR_FILE", "CLOUD_SOCIAL_SOURCE", "CLOUD_SOCIAL_MEDIA", "USER_PROFILE", "APP_PREFERENCES", "DOWNLOAD_METADATA", "BEHAVIOR_SIGNAL", "PREFERENCE_PROFILE"
    val entityId: String,
    val action: String, // "UPSERT", "DELETE"
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue ORDER BY id ASC LIMIT :limit")
    suspend fun peek(limit: Int = 50): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun recordFailure(id: Long, error: String)

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getQueueSize(): Int

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun getQueueSizeFlow(): Flow<Int>

    @Query("DELETE FROM sync_queue")
    suspend fun clearQueue()
}
