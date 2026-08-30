package com.example.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ResponseCacheDao {
    @Query("SELECT * FROM response_cache WHERE key = :key LIMIT 1")
    suspend fun getCache(key: String): ResponseCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: ResponseCacheEntity)

    @Query("DELETE FROM response_cache WHERE timestamp + ttlMs < :currentTime")
    suspend fun deleteExpired(currentTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM response_cache")
    suspend fun clearAll()
}

@Dao
interface SourceMetricsDao {
    @Query("SELECT * FROM source_metrics")
    fun getAllMetrics(): Flow<List<SourceMetricsEntity>>

    @Query("SELECT * FROM source_metrics")
    suspend fun getAllMetricsList(): List<SourceMetricsEntity>

    @Query("SELECT * FROM source_metrics WHERE metricKey = :key LIMIT 1")
    suspend fun getMetricsByKey(key: String): SourceMetricsEntity?

    @Query("SELECT * FROM source_metrics WHERE providerId = :providerId")
    suspend fun getMetricsForProvider(providerId: String): List<SourceMetricsEntity>

    @Query("SELECT * FROM source_metrics WHERE providerId = :providerId LIMIT 1")
    suspend fun getFirstMetricForProvider(providerId: String): SourceMetricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMetrics(metrics: SourceMetricsEntity)

    @Query("DELETE FROM source_metrics")
    suspend fun resetAllMetrics()
}

@Database(
    entities = [
        ResponseCacheEntity::class,
        SourceMetricsEntity::class,
        WatchHistoryEntity::class,
        BookmarkEntity::class,
        LikedVideoEntity::class,
        UserPlaylistEntity::class,
        OfflineDownloadEntity::class,
        SearchHistoryEntity::class,
        VideoMetadataCacheEntity::class,
        PreloadedVideoCacheEntity::class,
        com.example.bunkr.db.BunkrAlbumEntity::class,
        com.example.bunkr.db.BunkrFileEntity::class,
        com.example.cloudsocial.db.CloudSocialSourceEntity::class,
        com.example.cloudsocial.db.CloudSocialMediaEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun responseCacheDao(): ResponseCacheDao
    abstract fun sourceMetricsDao(): SourceMetricsDao
    abstract fun userDataDao(): UserDataDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun videoCacheDao(): VideoCacheDao
    abstract fun bunkrDao(): com.example.bunkr.db.BunkrDao
    abstract fun cloudSocialDao(): com.example.cloudsocial.db.CloudSocialDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `watch_history` (
                        `videoId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `duration` TEXT NOT NULL,
                        `progressFraction` REAL NOT NULL,
                        `providerId` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `watch_later_bookmarks` (
                        `videoId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `duration` TEXT NOT NULL,
                        `providerId` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `liked_videos` (
                        `videoId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `duration` TEXT NOT NULL,
                        `providerId` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_playlists` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `videosJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `source_metrics` ")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `source_metrics` (
                        `metricKey` TEXT NOT NULL,
                        `providerId` TEXT NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `quality` TEXT NOT NULL,
                        `networkResult` TEXT NOT NULL,
                        `startupLatencyMs` INTEGER NOT NULL,
                        `failureReason` TEXT NOT NULL,
                        `totalRequests` INTEGER NOT NULL,
                        `successfulRequests` INTEGER NOT NULL,
                        `totalStartupTimeMs` INTEGER NOT NULL,
                        `bufferingEventsCount` INTEGER NOT NULL,
                        `crashCount` INTEGER NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`metricKey`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `offline_downloads` (
                        `videoId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `localFilePath` TEXT NOT NULL,
                        `qualityLabel` TEXT NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `downloadedBytes` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `search_history` (
                        `query` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`query`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cached_video_metadata` (
                        `videoId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `description` TEXT,
                        `duration` TEXT NOT NULL,
                        `streamDataJson` TEXT,
                        `providerId` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        `ttlMs` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `preloaded_videos` (
                        `videoId` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL,
                        `hlsUrl` TEXT,
                        `qualityLabel` TEXT NOT NULL,
                        `cachedHeadersJson` TEXT,
                        `localCachePath` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bunkr_albums` (
                        `albumId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `lastScanTime` INTEGER NOT NULL,
                        `itemCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`albumId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bunkr_files` (
                        `fileId` TEXT NOT NULL,
                        `albumId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `mediaType` TEXT NOT NULL,
                        `duration` TEXT NOT NULL,
                        `resolution` TEXT NOT NULL,
                        `fileSize` TEXT NOT NULL,
                        `streamUrl` TEXT,
                        `streamUrlExpiry` INTEGER NOT NULL,
                        `isAvailable` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`fileId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bunkr_files_albumId` ON `bunkr_files` (`albumId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bunkr_files_sourceUrl` ON `bunkr_files` (`sourceUrl`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cloud_social_sources` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `lastSyncTimestamp` INTEGER NOT NULL,
                        `itemCount` INTEGER NOT NULL,
                        `newItemCount` INTEGER NOT NULL,
                        `extraConfigJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cloud_social_media` (
                        `id` TEXT NOT NULL,
                        `sourceId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `remoteId` TEXT NOT NULL,
                        `parentId` TEXT,
                        `title` TEXT NOT NULL,
                        `caption` TEXT,
                        `sourceUrl` TEXT NOT NULL,
                        `directStreamUrl` TEXT,
                        `thumbnailUrl` TEXT,
                        `mimeType` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `formattedSize` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `mediaCategory` TEXT NOT NULL,
                        `dateTimestamp` INTEGER NOT NULL,
                        `resolution` TEXT NOT NULL,
                        `headersJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_social_media_sourceId` ON `cloud_social_media` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_social_media_type` ON `cloud_social_media` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cloud_social_media_mediaCategory` ON `cloud_social_media` (`mediaCategory`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "butterfly_app_database.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
