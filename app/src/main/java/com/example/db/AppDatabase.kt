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
        PreloadedVideoCacheEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun responseCacheDao(): ResponseCacheDao
    abstract fun sourceMetricsDao(): SourceMetricsDao
    abstract fun userDataDao(): UserDataDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun videoCacheDao(): VideoCacheDao

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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "butterfly_app_database.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
