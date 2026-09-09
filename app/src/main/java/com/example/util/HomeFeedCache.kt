package com.example.util

import android.content.Context
import com.example.model.VideoItem

/**
 * Consolidated HomeFeedCache facade delegating to [HomeFeedCacheManager].
 * Preserves legacy API compatibility while eliminating redundant cache logic.
 */
object HomeFeedCache {
    fun saveFeed(context: Context, videos: List<VideoItem>) {
        HomeFeedCacheManager.saveCachedFeed(context, videos)
    }

    fun loadFeed(context: Context): List<VideoItem> {
        return HomeFeedCacheManager.loadCachedFeed(context)
    }
}
