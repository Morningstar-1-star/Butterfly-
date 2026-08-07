package com.example.db

import android.content.Context
import androidx.room.*
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
    entities = [ResponseCacheEntity::class, SourceMetricsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun responseCacheDao(): ResponseCacheDao
    abstract fun sourceMetricsDao(): SourceMetricsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "butterfly_app_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
