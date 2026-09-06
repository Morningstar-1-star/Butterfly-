package com.example.recommendation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.VideoItem
import com.example.util.SmartTagExtractor
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent User Activity & Long-Term Preference Memory Engine.
 *
 * Tracks, learns, and persists:
 * 1. Deep Likes & Dislikes (Channels, Keywords, Categories, Video IDs).
 * 2. Circadian Time-of-Day Preferences (24h histogram learning what user watches at which hours).
 * 3. Dwell & Completion Signals (High completion boosts affinity; fast skips decay interest).
 * 4. Search Intent & Click-Throughs (What user looks for and selects).
 * 5. Cross-Source Creator Affinity (Recognizes favorite creators across platforms).
 */
object UserActivityMemory {

    private const val TAG = "UserActivityMemory"
    private const val PREFS_NAME = "app_intelligence_activity_memory"

    // Disliked item metadata
    data class DislikedVideoInfo(
        val videoId: String,
        val title: String,
        val channelName: String,
        val tags: List<String>,
        val category: String,
        val timestamp: Long,
        val providerId: String? = null
    )

    // Liked item metadata
    data class LikedVideoInfo(
        val videoId: String,
        val title: String,
        val channelName: String,
        val tags: List<String>,
        val category: String,
        val timestamp: Long,
        val hourOfDay: Int,
        val providerId: String? = null
    )

    private val dislikedVideos = ConcurrentHashMap<String, DislikedVideoInfo>()
    private val dislikedChannels = ConcurrentHashMap.newKeySet<String>()
    private val dislikedCategories = ConcurrentHashMap<String, Int>()
    private val dislikedKeywords = ConcurrentHashMap.newKeySet<String>()

    private val likedVideos = ConcurrentHashMap<String, LikedVideoInfo>()
    private val favoriteChannels = ConcurrentHashMap<String, Float>() // channel -> affinity weight

    // 24-hour histogram: Hour (0..23) -> Category -> Watch count
    private val hourlyCategoryCounts = ConcurrentHashMap<Int, ConcurrentHashMap<String, Int>>()
    // 24-hour histogram: Hour (0..23) -> Channel -> Watch count
    private val hourlyChannelCounts = ConcurrentHashMap<Int, ConcurrentHashMap<String, Int>>()

    // Completion / Bounce tracking
    private val highCompletionChannels = ConcurrentHashMap<String, Int>()
    private val earlyBounceChannels = ConcurrentHashMap<String, Int>()

    @Volatile
    private var isInitialized = false

    private fun ensureInitialized() {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    try {
                        val appClass = Class.forName("com.example.MainApplication")
                        val appContextField = appClass.getDeclaredField("appContext")
                        appContextField.isAccessible = true
                        val ctx = appContextField.get(null) as? Context
                        if (ctx != null) {
                            init(ctx)
                        }
                    } catch (e: Exception) {
                        isInitialized = true // fallback to avoid retry loop
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                loadDislikes(prefs)
                loadLikes(prefs)
                loadHourlyPatterns(prefs)
                loadChannelAffinities(prefs)
                isInitialized = true
                Log.i(TAG, "UserActivityMemory initialized. Dislikes: ${dislikedVideos.size}, Likes: ${likedVideos.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing UserActivityMemory", e)
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ==========================================
    // DISLIKE RECORDING & NEGATIVE SIGNAL PURGING
    // ==========================================

    fun recordDislike(video: VideoItem, context: Context) {
        val vid = video.id.trim()
        if (vid.isEmpty()) return

        val channel = video.uploaderName.trim().lowercase(Locale.ROOT)
        val extractedTags = SmartTagExtractor.extractSemanticKeywords(video)
        val catTags = SmartTagExtractor.extractInternalCategoryTags(video)
        val mainCat = catTags.firstOrNull()?.category ?: "general"

        val info = DislikedVideoInfo(
            videoId = vid,
            title = video.title,
            channelName = channel,
            tags = extractedTags,
            category = mainCat,
            timestamp = System.currentTimeMillis(),
            providerId = video.providerId
        )
        dislikedVideos[vid] = info

        if (channel.isNotBlank() && channel != "butterfly stream") {
            dislikedChannels.add(channel)
        }

        dislikedCategories[mainCat] = (dislikedCategories[mainCat] ?: 0) + 1

        // Extract semantic negative keywords (filter common noise)
        val stopWords = setOf("the", "and", "for", "with", "video", "official", "movie", "trailer", "part", "hindi", "english")
        val keywords = video.title.lowercase(Locale.ROOT)
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length >= 4 && it !in stopWords }
        dislikedKeywords.addAll(keywords.take(6))

        // Also remove from likes if previously liked
        likedVideos.remove(vid)
        if (channel.isNotBlank()) {
            favoriteChannels[channel] = (favoriteChannels[channel] ?: 0f) - 20f
        }

        persistDislikes(context)
    }

    fun removeDislike(videoId: String, context: Context) {
        val vid = videoId.trim()
        val removed = dislikedVideos.remove(vid)
        if (removed != null) {
            val ch = removed.channelName
            // If no other disliked video shares this channel, optionally keep or reduce
            val otherDislikedWithChannel = dislikedVideos.values.any { it.channelName == ch }
            if (!otherDislikedWithChannel) {
                dislikedChannels.remove(ch)
            }
            persistDislikes(context)
        }
    }

    fun getDislikedVideoIds(): Set<String> { ensureInitialized(); return dislikedVideos.keys.toSet() }
    fun getDislikedChannels(): Set<String> { ensureInitialized(); return dislikedChannels.toSet() }
    fun getDislikedKeywords(): Set<String> { ensureInitialized(); return dislikedKeywords.toSet() }
    fun getDislikedCategories(): Map<String, Int> { ensureInitialized(); return dislikedCategories.toMap() }

    fun exportDislikedVideosJson(context: Context): String {
        return getPrefs(context).getString("disliked_videos_json", "[]") ?: "[]"
    }

    fun isDisliked(videoId: String): Boolean { ensureInitialized(); return dislikedVideos.containsKey(videoId.trim()) }

    // ==========================================
    // LIKE RECORDING & POSITIVE SIGNAL BOOSTING
    // ==========================================

    fun recordLike(video: VideoItem, context: Context) {
        val vid = video.id.trim()
        if (vid.isEmpty()) return

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val channel = video.uploaderName.trim().lowercase(Locale.ROOT)
        val extractedTags = SmartTagExtractor.extractSemanticKeywords(video)
        val catTags = SmartTagExtractor.extractInternalCategoryTags(video)
        val mainCat = catTags.firstOrNull()?.category ?: "general"

        val info = LikedVideoInfo(
            videoId = vid,
            title = video.title,
            channelName = channel,
            tags = extractedTags,
            category = mainCat,
            timestamp = System.currentTimeMillis(),
            hourOfDay = hour,
            providerId = video.providerId
        )
        likedVideos[vid] = info

        // Remove from dislikes if previously disliked
        dislikedVideos.remove(vid)
        if (channel.isNotBlank()) {
            dislikedChannels.remove(channel)
            favoriteChannels[channel] = (favoriteChannels[channel] ?: 0f) + 25f
        }

        // Record hourly positive preference
        recordHourlyActivity(hour, mainCat, channel)

        persistLikes(context)
        persistChannelAffinities(context)
        persistHourlyPatterns(context)
    }

    fun removeLike(videoId: String, context: Context) {
        val vid = videoId.trim()
        val removed = likedVideos.remove(vid)
        if (removed != null) {
            val ch = removed.channelName
            if (ch.isNotBlank()) {
                val current = favoriteChannels[ch] ?: 0f
                favoriteChannels[ch] = (current - 15f).coerceAtLeast(0f)
            }
            persistLikes(context)
            persistChannelAffinities(context)
        }
    }

    fun getLikedVideoIds(): Set<String> = likedVideos.keys.toSet()
    fun getLikedVideosList(): List<LikedVideoInfo> = likedVideos.values.toList()

    // ==========================================
    // WATCH DWELL, COMPLETION & TIME HISTOGRAMS
    // ==========================================

    fun recordWatchActivity(
        video: VideoItem,
        progressFraction: Float,
        currentPositionMs: Long,
        totalDurationMs: Long,
        context: Context
    ) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val channel = video.uploaderName.trim().lowercase(Locale.ROOT)
        val catTags = SmartTagExtractor.extractInternalCategoryTags(video)
        val mainCat = catTags.firstOrNull()?.category ?: "general"

        // Update hourly pattern
        recordHourlyActivity(hour, mainCat, channel)

        // Completion signals
        if (progressFraction >= 0.70f || currentPositionMs >= 180_000L) {
            // High completion / High intent
            if (channel.isNotBlank()) {
                highCompletionChannels[channel] = (highCompletionChannels[channel] ?: 0) + 1
                favoriteChannels[channel] = (favoriteChannels[channel] ?: 0f) + 4.0f
            }
        } else if (progressFraction < 0.12f && currentPositionMs in 5_000L..25_000L && totalDurationMs > 60_000L) {
            // Fast skip / early bounce
            if (channel.isNotBlank()) {
                earlyBounceChannels[channel] = (earlyBounceChannels[channel] ?: 0) + 1
                favoriteChannels[channel] = (favoriteChannels[channel] ?: 0f) - 3.0f
            }
        }

        // Periodically save
        if (System.currentTimeMillis() % 5 == 0L) {
            persistHourlyPatterns(context)
            persistChannelAffinities(context)
        }
    }

    private fun recordHourlyActivity(hour: Int, category: String, channel: String) {
        val catMap = hourlyCategoryCounts.getOrPut(hour) { ConcurrentHashMap() }
        catMap[category] = (catMap[category] ?: 0) + 1

        if (channel.isNotBlank()) {
            val chanMap = hourlyChannelCounts.getOrPut(hour) { ConcurrentHashMap() }
            chanMap[channel] = (chanMap[channel] ?: 0) + 1
        }
    }

    /**
     * Computes the user's learned category affinity weights for a specific hour of day (0..23).
     * Considers the target hour +/- 1 hour smoothing window.
     */
    fun getHourlyCategoryAffinity(targetHour: Int): Map<String, Float> {
        ensureInitialized()
        val result = mutableMapOf<String, Float>()
        val hoursToCheck = listOf(
            (targetHour + 23) % 24,
            targetHour,
            (targetHour + 1) % 24
        )

        for (h in hoursToCheck) {
            val counts = hourlyCategoryCounts[h] ?: continue
            val weight = if (h == targetHour) 1.5f else 0.8f
            for ((cat, count) in counts) {
                result[cat] = (result[cat] ?: 0f) + (count * weight)
            }
        }

        return result
    }

    /**
     * Computes the user's learned creator affinity weights for a specific hour of day (0..23).
     */
    fun getHourlyChannelAffinity(targetHour: Int): Map<String, Float> {
        ensureInitialized()
        val result = mutableMapOf<String, Float>()
        val hoursToCheck = listOf(
            (targetHour + 23) % 24,
            targetHour,
            (targetHour + 1) % 24
        )

        for (h in hoursToCheck) {
            val counts = hourlyChannelCounts[h] ?: continue
            val weight = if (h == targetHour) 1.5f else 0.8f
            for ((chan, count) in counts) {
                result[chan] = (result[chan] ?: 0f) + (count * weight)
            }
        }

        return result
    }

    fun getFavoriteChannels(): List<String> {
        ensureInitialized()
        return favoriteChannels.entries
            .filter { it.value > 8.0f }
            .sortedByDescending { it.value }
            .map { it.key }
    }

    // ==========================================
    // PERSISTENCE IMPLEMENTATION (SharedPreferences)
    // ==========================================

    private fun persistDislikes(context: Context) {
        try {
            val array = JSONArray()
            for (info in dislikedVideos.values.take(300)) {
                val obj = JSONObject()
                obj.put("id", info.videoId)
                obj.put("title", info.title)
                obj.put("channel", info.channelName)
                obj.put("tags", JSONArray(info.tags))
                obj.put("category", info.category)
                obj.put("timestamp", info.timestamp)
                obj.put("providerId", info.providerId ?: "")
                array.put(obj)
            }
            val prefs = getPrefs(context)
            prefs.edit()
                .putString("disliked_videos_json", array.toString())
                .putStringSet("disliked_channels_set", dislikedChannels)
                .putStringSet("disliked_keywords_set", dislikedKeywords)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist dislikes", e)
        }
    }

    private fun loadDislikes(prefs: SharedPreferences) {
        try {
            val jsonStr = prefs.getString("disliked_videos_json", null) ?: return
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                if (id.isNotBlank()) {
                    val tagsArray = obj.optJSONArray("tags")
                    val tagsList = mutableListOf<String>()
                    if (tagsArray != null) {
                        for (j in 0 until tagsArray.length()) {
                            tagsList.add(tagsArray.getString(j))
                        }
                    }
                    val info = DislikedVideoInfo(
                        videoId = id,
                        title = obj.optString("title"),
                        channelName = obj.optString("channel").lowercase(Locale.ROOT),
                        tags = tagsList,
                        category = obj.optString("category", "general"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        providerId = obj.optString("providerId").takeIf { it.isNotBlank() }
                    )
                    dislikedVideos[id] = info
                }
            }
            val chans = prefs.getStringSet("disliked_channels_set", emptySet()) ?: emptySet()
            dislikedChannels.addAll(chans)
            val keywords = prefs.getStringSet("disliked_keywords_set", emptySet()) ?: emptySet()
            dislikedKeywords.addAll(keywords)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dislikes", e)
        }
    }

    private fun persistLikes(context: Context) {
        try {
            val array = JSONArray()
            for (info in likedVideos.values.take(300)) {
                val obj = JSONObject()
                obj.put("id", info.videoId)
                obj.put("title", info.title)
                obj.put("channel", info.channelName)
                obj.put("tags", JSONArray(info.tags))
                obj.put("category", info.category)
                obj.put("timestamp", info.timestamp)
                obj.put("hourOfDay", info.hourOfDay)
                obj.put("providerId", info.providerId ?: "")
                array.put(obj)
            }
            getPrefs(context).edit().putString("liked_videos_json", array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist likes", e)
        }
    }

    private fun loadLikes(prefs: SharedPreferences) {
        try {
            val jsonStr = prefs.getString("liked_videos_json", null) ?: return
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                if (id.isNotBlank()) {
                    val tagsArray = obj.optJSONArray("tags")
                    val tagsList = mutableListOf<String>()
                    if (tagsArray != null) {
                        for (j in 0 until tagsArray.length()) {
                            tagsList.add(tagsArray.getString(j))
                        }
                    }
                    val info = LikedVideoInfo(
                        videoId = id,
                        title = obj.optString("title"),
                        channelName = obj.optString("channel").lowercase(Locale.ROOT),
                        tags = tagsList,
                        category = obj.optString("category", "general"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        hourOfDay = obj.optInt("hourOfDay", 12),
                        providerId = obj.optString("providerId").takeIf { it.isNotBlank() }
                    )
                    likedVideos[id] = info
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load likes", e)
        }
    }

    private fun persistHourlyPatterns(context: Context) {
        try {
            val catObj = JSONObject()
            for ((hour, counts) in hourlyCategoryCounts) {
                val hObj = JSONObject()
                for ((cat, count) in counts) {
                    hObj.put(cat, count)
                }
                catObj.put(hour.toString(), hObj)
            }
            getPrefs(context).edit().putString("hourly_categories_json", catObj.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist hourly patterns", e)
        }
    }

    private fun loadHourlyPatterns(prefs: SharedPreferences) {
        try {
            val catStr = prefs.getString("hourly_categories_json", null) ?: return
            val catObj = JSONObject(catStr)
            for (key in catObj.keys()) {
                val hour = key.toIntOrNull() ?: continue
                val hObj = catObj.getJSONObject(key)
                val map = ConcurrentHashMap<String, Int>()
                for (cat in hObj.keys()) {
                    map[cat] = hObj.getInt(cat)
                }
                hourlyCategoryCounts[hour] = map
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load hourly patterns", e)
        }
    }

    private fun persistChannelAffinities(context: Context) {
        try {
            val obj = JSONObject()
            for ((ch, score) in favoriteChannels) {
                obj.put(ch, score.toDouble())
            }
            getPrefs(context).edit().putString("channel_affinities_json", obj.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist channel affinities", e)
        }
    }

    private fun loadChannelAffinities(prefs: SharedPreferences) {
        try {
            val str = prefs.getString("channel_affinities_json", null) ?: return
            val obj = JSONObject(str)
            for (ch in obj.keys()) {
                favoriteChannels[ch] = obj.getDouble(ch).toFloat()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load channel affinities", e)
        }
    }
}
