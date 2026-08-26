package com.example.util

import android.content.Context
import android.util.Log
import com.example.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject

object HomeFeedCache {
    private const val TAG = "HomeFeedCache"
    private const val PREFS_NAME = "home_feed_cache_prefs"
    private const val KEY_FEED_JSON = "cached_home_videos_json"
    private const val KEY_CACHE_TIME = "cached_home_videos_time"

    fun saveFeed(context: Context, videos: List<VideoItem>) {
        if (videos.isEmpty()) return
        try {
            val jsonArray = JSONArray()
            val itemsToSave = videos.take(60)
            for (v in itemsToSave) {
                val obj = JSONObject().apply {
                    put("id", v.id)
                    put("title", v.title)
                    put("uploaderName", v.uploaderName)
                    put("thumbnailUrl", v.thumbnailUrl ?: "")
                    put("providerId", v.providerId ?: "youtube")
                    put("uploaderAvatarUrl", v.uploaderAvatarUrl ?: "")
                    put("durationSeconds", v.durationSeconds)
                    put("viewCount", v.viewCount)
                    put("uploadDate", v.uploadDate ?: "")
                }
                jsonArray.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_FEED_JSON, jsonArray.toString())
                .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Saved ${itemsToSave.size} items to home feed cache")
        } catch (e: Exception) {
            Log.w(TAG, "Error saving home feed cache: ${e.message}")
        }
    }

    fun loadFeed(context: Context): List<VideoItem> {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_FEED_JSON, null) ?: return emptyList()
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<VideoItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                if (id.isBlank()) continue
                list.add(
                    VideoItem(
                        id = id,
                        title = obj.optString("title", "Video"),
                        uploaderName = obj.optString("uploaderName", "Creator"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        providerId = obj.optString("providerId").takeIf { it.isNotBlank() } ?: "youtube",
                        uploaderAvatarUrl = obj.optString("uploaderAvatarUrl").takeIf { it.isNotBlank() },
                        durationSeconds = obj.optLong("durationSeconds", -1L),
                        viewCount = obj.optLong("viewCount", -1L),
                        uploadDate = obj.optString("uploadDate").takeIf { it.isNotBlank() }
                    )
                )
            }
            Log.d(TAG, "Loaded ${list.size} items from home feed cache")
            return list
        } catch (e: Exception) {
            Log.w(TAG, "Error loading home feed cache: ${e.message}")
            return emptyList()
        }
    }
}
