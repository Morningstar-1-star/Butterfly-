package com.example.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class YouTubeVoteMetrics(
    val videoId: String,
    val likes: Long,
    val dislikes: Long,
    val viewCount: Long,
    val rating: Double
)

object ReturnYouTubeDislikeHelper {
    private const val TAG = "RYDHelper"
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private val cache = ConcurrentHashMap<String, YouTubeVoteMetrics>()

    /**
     * Fetches real-time YouTube likes, dislikes, view count, and rating from Return YouTube Dislike API.
     */
    suspend fun fetchVotes(videoId: String): YouTubeVoteMetrics? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        cache[videoId]?.let { return@withContext it }

        try {
            val url = "https://returnyoutubedislikeapi.com/votes?videoId=$videoId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    if (bodyStr.startsWith("{")) {
                        val json = JSONObject(bodyStr)
                        val likes = json.optLong("likes", -1L)
                        val dislikes = json.optLong("dislikes", -1L)
                        val viewCount = json.optLong("viewCount", -1L)
                        val rating = json.optDouble("rating", 0.0)

                        val metrics = YouTubeVoteMetrics(
                            videoId = videoId,
                            likes = likes,
                            dislikes = dislikes,
                            viewCount = viewCount,
                            rating = rating
                        )
                        cache[videoId] = metrics
                        return@withContext metrics
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "RYD fetch note for $videoId: ${e.message}")
        }
        null
    }
}
