package com.example.util

import com.example.plugin.bridge.HttpBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class RYDVoteData(
    val likes: Long = 0,
    val dislikes: Long = 0,
    val rating: Double = 0.0,
    val viewCount: Long = 0
)

object ReturnYouTubeDislikeHelper {
    private val http = HttpBridge()

    suspend fun getVotes(videoId: String): RYDVoteData? = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || videoId.contains("/") || videoId.length != 11) return@withContext null
        try {
            val url = "https://returnyoutubedislikeapi.com/votes?videoId=$videoId"
            val resp = http.get(url)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val likes = json.optLong("likes", 0)
                val dislikes = json.optLong("dislikes", 0)
                val rating = json.optDouble("rating", 0.0)
                val viewCount = json.optLong("viewCount", 0)
                return@withContext RYDVoteData(likes, dislikes, rating, viewCount)
            }
        } catch (e: Exception) {
            // Silently fallback if RYD API is unavailable
        }
        return@withContext null
    }

    fun formatNumber(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            count > 0 -> "$count"
            else -> "0"
        }
    }
}
