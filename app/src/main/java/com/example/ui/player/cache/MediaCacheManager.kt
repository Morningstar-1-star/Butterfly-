package com.example.ui.player.cache

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.ui.player.metrics.PlaybackMetricsTracker
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Centralized Media Byte Cache for Butterfly.
 * Implements a shared LRU SimpleCache (512 MB) on disk to eliminate duplicate network downloads,
 * enable instant seeking within cached segments, and drastically reduce mobile data consumption
 * without compressing or re-encoding video streams.
 *
 * Designed with fail-safe error isolation so cache failures (e.g. disk full, permission)
 * never crash playback or interrupt the user.
 */
@OptIn(UnstableApi::class)
object MediaCacheManager {
    private const val TAG = "MediaCacheManager"
    private const val CACHE_DIR_NAME = "butterfly_media_cache"
    
    // 512 MB LRU cache size - provides ample room for full HD chunks while preventing runaway disk usage
    const val MAX_CACHE_SIZE_BYTES = 512L * 1024L * 1024L
    
    // 4 MB fragment size for progressive streams and chunk caching
    const val CACHE_FRAGMENT_SIZE = 4L * 1024L * 1024L

    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    @Volatile
    private var databaseProvider: DatabaseProvider? = null

    val totalBytesReadFromCache = AtomicLong(0L)
    val totalBytesReadFromNetwork = AtomicLong(0L)

    @Synchronized
    fun getCache(context: Context): SimpleCache? {
        val existing = simpleCacheInstance
        if (existing != null) return existing

        return try {
            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            if (SimpleCache.isCacheFolderLocked(cacheDir)) {
                Log.w(TAG, "Cache folder ${cacheDir.absolutePath} is currently locked by another instance; using existing or fallback")
                return simpleCacheInstance
            }

            val dbProvider = databaseProvider ?: StandaloneDatabaseProvider(appContext).also {
                databaseProvider = it
            }

            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            val cache = SimpleCache(cacheDir, evictor, dbProvider)
            simpleCacheInstance = cache
            Log.i(TAG, "Initialized SimpleCache in ${cacheDir.absolutePath} (max=${MAX_CACHE_SIZE_BYTES / (1024 * 1024)}MB)")
            cache
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize SimpleCache (safe fallback to non-cached): ${t.message}")
            null
        }
    }

    val cacheEventListener = object : CacheDataSource.EventListener {
        override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
            totalBytesReadFromCache.addAndGet(cachedBytesRead)
            PlaybackMetricsTracker.recordCachedBytes(cachedBytesRead)
        }

        override fun onCacheIgnored(reason: Int) {
            Log.d(TAG, "Cache ignored: reason=$reason")
        }
    }

    /**
     * Builds a CacheDataSource.Factory wrapping the upstream DataSource.Factory.
     * Uses FLAG_IGNORE_CACHE_ON_ERROR to guarantee uninterrupted playback even if disk writes encounter issues.
     * If the cache cannot be opened, returns the upstreamFactory directly without crashing.
     */
    fun createCacheDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): DataSource.Factory {
        val cache = getCache(context) ?: return upstreamFactory
        return try {
            val cacheReadFactory = FileDataSource.Factory()
            val cacheWriteSinkFactory = CacheDataSink.Factory()
                .setCache(cache)
                .setFragmentSize(CACHE_FRAGMENT_SIZE)

            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheReadDataSourceFactory(cacheReadFactory)
                .setCacheWriteDataSinkFactory(cacheWriteSinkFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .setEventListener(cacheEventListener)
        } catch (t: Throwable) {
            Log.w(TAG, "CacheDataSource.Factory creation exception (falling back to upstream): ${t.message}")
            upstreamFactory
        }
    }

    /**
     * Returns current cached size in bytes safely.
     */
    fun getCacheSizeBytes(context: Context): Long {
        return try {
            getCache(context)?.cacheSpace ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Clears all cached media bytes from disk.
     */
    fun clearCache(context: Context) {
        try {
            val cache = getCache(context) ?: return
            for (key in cache.keys) {
                try {
                    cache.removeResource(key)
                } catch (_: Throwable) {}
            }
            totalBytesReadFromCache.set(0L)
            totalBytesReadFromNetwork.set(0L)
            PlaybackMetricsTracker.resetCacheMetrics()
            Log.i(TAG, "Media cache cleared successfully")
        } catch (e: Throwable) {
            Log.w(TAG, "Error clearing media cache: ${e.message}")
        }
    }
}
