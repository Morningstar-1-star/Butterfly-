package com.example.subtitles

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.example.util.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance 2-tier cache for subtitle files and translated cue sets.
 * Tier 1: Fast Memory LRU Cache (Cues & Translations)
 * Tier 2: Disk Cache for raw subtitle files (.srt/.vtt)
 */
object SubtitleCache {
    private const val TAG = "SubtitleCache"

    // Key: "mediaKey|subId" -> List<SubtitleCue>
    private val cueCache = LruCache<String, List<SubtitleCue>>(50)

    // Key: "mediaKey|subId|targetLang" -> List<SubtitleCue>
    private val translatedCueCache = LruCache<String, List<SubtitleCue>>(100)

    fun getMemoryCues(key: String): List<SubtitleCue>? = cueCache.get(key)

    fun putMemoryCues(key: String, cues: List<SubtitleCue>) {
        cueCache.put(key, cues)
    }

    fun getTranslatedCues(sourceKey: String, targetLang: String): List<SubtitleCue>? {
        return translatedCueCache.get("$sourceKey|$targetLang")
    }

    fun putTranslatedCues(sourceKey: String, targetLang: String, cues: List<SubtitleCue>) {
        translatedCueCache.put("$sourceKey|$targetLang", cues)
    }

    suspend fun getDiskCachedSubtitle(context: Context, key: String): String? = withContext(Dispatchers.IO) {
        try {
            val safeKey = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(context.cacheDir, "subtitles/$safeKey.sub")
            if (file.exists() && file.length() > 0) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading disk subtitle cache: ${e.message}")
            null
        }
    }

    suspend fun saveDiskCachedSubtitle(context: Context, key: String, content: String) = withContext(Dispatchers.IO) {
        try {
            val safeKey = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val dir = File(context.cacheDir, "subtitles")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$safeKey.sub")
            file.writeText(content)
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving disk subtitle cache: ${e.message}")
        }
    }

    fun clear() {
        cueCache.evictAll()
        translatedCueCache.evictAll()
    }
}
