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

    @Query("SELECT * FROM source_metrics WHERE providerId = :providerId LIMIT 1")
    suspend fun getMetricsForProvider(providerId: String): SourceMetricsEntity?

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
        UserPlaylistEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun responseCacheDao(): ResponseCacheDao
    abstract fun sourceMetricsDao(): SourceMetricsDao
    abstract fun userDataDao(): UserDataDao

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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "butterfly_app_database.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
