package com.example.db

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CacheRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val cacheDao = db.responseCacheDao()
    
    // In-memory LRU Cache (Capacity = 150 items)
    private val memoryCache = LruCache<String, Pair<String, String?>>(150)

    suspend fun getCachedResponse(key: String): Pair<String, String?>? = withContext(Dispatchers.IO) {
        // 1. Check LRU memory cache
        val memHit = memoryCache.get(key)
        if (memHit != null) {
            return@withContext memHit
        }

        // 2. Check Room SQLite DB
        val dbHit = cacheDao.getCache(key) ?: return@withContext null
        
        // Check if expired
        val isExpired = System.currentTimeMillis() > (dbHit.timestamp + dbHit.ttlMs)
        if (isExpired) {
            return@withContext null
        }

        // Put in memory cache
        val pair = Pair(dbHit.responseBody, dbHit.eTag)
        memoryCache.put(key, pair)
        return@withContext pair
    }

    suspend fun saveCachedResponse(
        key: String,
        body: String,
        eTag: String? = null,
        ttlMs: Long = 300_000L
    ) = withContext(Dispatchers.IO) {
        val entity = ResponseCacheEntity(
            key = key,
            responseBody = body,
            statusCode = 200,
            eTag = eTag,
            timestamp = System.currentTimeMillis(),
            ttlMs = ttlMs
        )
        memoryCache.put(key, Pair(body, eTag))
        cacheDao.insertCache(entity)
        
        // Clean up expired entries in background
        cacheDao.deleteExpired()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        cacheDao.clearAll()
    }
}
