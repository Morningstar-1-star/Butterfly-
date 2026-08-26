package com.example.util

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages video playback position persistence across app restarts, navigation, and quality switches.
 * Provides both instant synchronous memory lookups and persistent SharedPreferences storage.
 */
object PlaybackResumeManager {
    private const val TAG = "PlaybackResumeManager"
    private const val PREFS_NAME = "video_playback_resume_positions"
    private const val KEY_PREFIX_POS = "pos_"
    private const val KEY_PREFIX_DUR = "dur_"
    private const val KEY_PREFIX_TIME = "time_"

    private val positionMemoryCache = ConcurrentHashMap<String, Long>()
    private val durationMemoryCache = ConcurrentHashMap<String, Long>()
    private var isCacheInitialized = false

    private fun initCacheIfNeeded(context: Context) {
        if (isCacheInitialized) return
        synchronized(this) {
            if (isCacheInitialized) return
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val all = prefs.all
                for ((key, value) in all) {
                    if (key.startsWith(KEY_PREFIX_POS) && value is Long) {
                        val videoId = key.removePrefix(KEY_PREFIX_POS)
                        positionMemoryCache[videoId] = value
                    } else if (key.startsWith(KEY_PREFIX_DUR) && value is Long) {
                        val videoId = key.removePrefix(KEY_PREFIX_DUR)
                        durationMemoryCache[videoId] = value
                    }
                }
                isCacheInitialized = true
            } catch (e: Exception) {
                Log.w(TAG, "Error initializing memory cache: ${e.message}")
            }
        }
    }

    /**
     * Persist the playback position and total duration for a given video ID.
     */
    fun savePosition(context: Context, videoId: String, positionMs: Long, durationMs: Long) {
        val cleanId = videoId.trim()
        if (cleanId.isEmpty() || positionMs < 0L) return

        val fraction = if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f

        // If the user watched >= 95% of the video or is within 5 seconds of the end,
        // treat it as completed so subsequent clicks start from the beginning.
        // If position is less than 3 seconds, also start from beginning.
        val targetPos = when {
            durationMs > 0L && fraction >= 0.95f -> 0L
            durationMs > 0L && (durationMs - positionMs) <= 5000L -> 0L
            positionMs < 3000L -> 0L
            else -> positionMs
        }

        positionMemoryCache[cleanId] = targetPos
        if (durationMs > 0L) {
            durationMemoryCache[cleanId] = durationMs
        }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("$KEY_PREFIX_POS$cleanId", targetPos)
                .putLong("$KEY_PREFIX_DUR$cleanId", durationMs)
                .putLong("$KEY_PREFIX_TIME$cleanId", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error persisting position for $cleanId: ${e.message}")
        }
    }

    /**
     * Retrieve the saved playback position in milliseconds. Returns 0L if none or completed.
     */
    fun getSavedPosition(context: Context, videoId: String): Long {
        val cleanId = videoId.trim()
        if (cleanId.isEmpty()) return 0L
        initCacheIfNeeded(context)

        val cachedPos = positionMemoryCache[cleanId]
        if (cachedPos != null) {
            val dur = durationMemoryCache[cleanId] ?: 0L
            if (dur > 0L && cachedPos > 0L) {
                val fraction = cachedPos.toFloat() / dur.toFloat()
                if (fraction >= 0.95f) return 0L
            }
            return cachedPos
        }

        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val pos = prefs.getLong("$KEY_PREFIX_POS$cleanId", 0L)
            val dur = prefs.getLong("$KEY_PREFIX_DUR$cleanId", 0L)
            positionMemoryCache[cleanId] = pos
            if (dur > 0L) durationMemoryCache[cleanId] = dur

            if (dur > 0L && pos > 0L) {
                val fraction = pos.toFloat() / dur.toFloat()
                if (fraction >= 0.95f) 0L else pos
            } else {
                pos
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Retrieve the saved progress fraction (0f - 1f) for UI progress indicators.
     */
    fun getSavedFraction(context: Context, videoId: String): Float {
        val cleanId = videoId.trim()
        if (cleanId.isEmpty()) return 0f
        val pos = getSavedPosition(context, cleanId)
        val dur = durationMemoryCache[cleanId] ?: 0L
        return if (dur > 0L && pos > 0L) {
            (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    /**
     * Retrieve all saved progress fractions as a map of videoId -> Float (0f - 1f).
     */
    fun getAllSavedFractions(context: Context): Map<String, Float> {
        initCacheIfNeeded(context)
        val result = mutableMapOf<String, Float>()
        for ((vid, pos) in positionMemoryCache) {
            val dur = durationMemoryCache[vid] ?: 0L
            if (dur > 0L && pos > 0L) {
                val fraction = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                result[vid] = fraction
            }
        }
        return result
    }

    /**
     * Retrieve all saved positions in milliseconds as a map of videoId -> Long.
     */
    fun getAllSavedPositions(context: Context): Map<String, Long> {
        initCacheIfNeeded(context)
        return positionMemoryCache.toMap()
    }

    /**
     * Format milliseconds into a human-readable string (e.g. "03:45" or "1:15:30").
     */
    fun formatTimestamp(positionMs: Long): String {
        if (positionMs <= 0L) return "0:00"
        val totalSecs = positionMs / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Clear saved position for a specific video ID.
     */
    fun clearPosition(context: Context, videoId: String) {
        val cleanId = videoId.trim()
        if (cleanId.isEmpty()) return
        positionMemoryCache.remove(cleanId)
        durationMemoryCache.remove(cleanId)
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove("$KEY_PREFIX_POS$cleanId")
                .remove("$KEY_PREFIX_DUR$cleanId")
                .remove("$KEY_PREFIX_TIME$cleanId")
                .apply()
        } catch (_: Exception) {}
    }
}
