package com.example.cloudsocial.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class CloudSocialType {
    TELEGRAM,
    MEGA,
    BUNKR
}

@Entity(tableName = "cloud_social_sources")
data class CloudSocialSourceEntity(
    @PrimaryKey val id: String,
    val type: String, // "TELEGRAM", "MEGA", "BUNKR"
    val name: String,
    val sourceUrl: String,
    val enabled: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
    val itemCount: Int = 0,
    val newItemCount: Int = 0,
    val extraConfigJson: String = ""
)

@Entity(
    tableName = "cloud_social_media",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["type"]),
        Index(value = ["mediaCategory"])
    ]
)
data class CloudSocialMediaEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val type: String, // "TELEGRAM", "MEGA", "BUNKR"
    val remoteId: String,
    val parentId: String? = null, // Album ID / Folder ID / Chat ID
    val title: String,
    val caption: String? = null,
    val sourceUrl: String,
    val directStreamUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mimeType: String = "video/mp4",
    val fileSize: Long = 0L,
    val formattedSize: String = "",
    val durationMs: Long = 0L,
    val mediaCategory: String = "video", // "video", "image", "audio", "document"
    val dateTimestamp: Long = System.currentTimeMillis(),
    val resolution: String = "HD",
    val headersJson: String = "{}"
)

@Dao
interface CloudSocialDao {
    @Query("SELECT * FROM cloud_social_sources ORDER BY lastSyncTimestamp DESC")
    fun getAllSourcesFlow(): Flow<List<CloudSocialSourceEntity>>

    @Query("SELECT * FROM cloud_social_sources WHERE id = :id LIMIT 1")
    suspend fun getSourceById(id: String): CloudSocialSourceEntity?

    @Query("SELECT * FROM cloud_social_sources WHERE type = :type")
    suspend fun getSourcesByType(type: String): List<CloudSocialSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: CloudSocialSourceEntity)

    @Query("DELETE FROM cloud_social_sources WHERE id = :id")
    suspend fun deleteSource(id: String)

    @Query("DELETE FROM cloud_social_media WHERE sourceId = :sourceId")
    suspend fun deleteMediaBySource(sourceId: String)

    @Query("SELECT * FROM cloud_social_media ORDER BY dateTimestamp DESC")
    fun getAllMediaFlow(): Flow<List<CloudSocialMediaEntity>>

    @Query("SELECT * FROM cloud_social_media ORDER BY dateTimestamp DESC")
    suspend fun getAllMediaList(): List<CloudSocialMediaEntity>

    @Query("SELECT * FROM cloud_social_media WHERE type = :type ORDER BY dateTimestamp DESC")
    fun getMediaByTypeFlow(type: String): Flow<List<CloudSocialMediaEntity>>

    @Query("SELECT * FROM cloud_social_media WHERE sourceId = :sourceId ORDER BY dateTimestamp DESC")
    fun getMediaBySourceFlow(sourceId: String): Flow<List<CloudSocialMediaEntity>>

    @Query("SELECT * FROM cloud_social_media WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: String): CloudSocialMediaEntity?

    @Query("SELECT * FROM cloud_social_media WHERE title LIKE '%' || :query || '%' OR caption LIKE '%' || :query || '%' ORDER BY dateTimestamp DESC")
    suspend fun searchMedia(query: String): List<CloudSocialMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaBatch(items: List<CloudSocialMediaEntity>)

    @Query("DELETE FROM cloud_social_media WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Query("DELETE FROM cloud_social_media")
    suspend fun clearAllMedia()

    @Query("DELETE FROM cloud_social_sources")
    suspend fun clearAllSources()
}
