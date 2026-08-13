package com.example.engine

import com.example.db.WatchHistoryEntity
import com.example.model.VideoItem
import java.util.Calendar

data class CategoryAffinity(
    val category: String,
    val score: Float
)

data class RecommendationInsight(
    val topCategories: List<CategoryAffinity>,
    val preferredTimeSlot: String,
    val smartEngineActive: Boolean = true
)

object SmartRecommendationEngine {

    /**
     * Time Slots based on Circadian Rhythm:
     * - MORNING (06:00 - 12:00)
     * - AFTERNOON (12:00 - 18:00)
     * - EVENING (18:00 - 00:00)
     * - LATE_NIGHT (00:00 - 06:00)
     */
    fun getCurrentTimeSlot(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> "MORNING"
            in 12..17 -> "AFTERNOON"
            in 18..23 -> "EVENING"
            else -> "LATE_NIGHT"
        }
    }

    /**
     * Compute user insights from watch history and likes.
     */
    fun computeInsights(
        watchHistory: List<WatchHistoryEntity>,
        likedVideoIds: Set<String>,
        dislikedVideoIds: Set<String>
    ): RecommendationInsight {
        val categoryScores = mutableMapOf<String, Float>()
        val timeSlotCounts = mutableMapOf<String, Int>()

        watchHistory.forEach { entry ->
            val provider = entry.providerId?.ifEmpty { "general" } ?: "general"
            val timeSlot = getTimeSlotFromTimestamp(entry.timestamp)
            timeSlotCounts[timeSlot] = (timeSlotCounts[timeSlot] ?: 0) + 1

            // Completion weight: Watching > 70% adds high score
            val completionRatio = entry.progressFraction
            val watchScore = when {
                completionRatio > 0.7f -> 5.0f
                completionRatio < 0.1f -> -2.0f
                else -> 2.0f
            }

            // Like / Dislike weight
            val feedbackScore = when {
                likedVideoIds.contains(entry.videoId) -> 10.0f
                dislikedVideoIds.contains(entry.videoId) -> -15.0f
                else -> 0.0f
            }

            val totalWeight = watchScore + feedbackScore
            categoryScores[provider] = (categoryScores[provider] ?: 0f) + totalWeight
        }

        val topCats = categoryScores.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { CategoryAffinity(it.key, it.value) }

        val topTimeSlot = timeSlotCounts.maxByOrNull { it.value }?.key ?: getCurrentTimeSlot()

        return RecommendationInsight(
            topCategories = topCats,
            preferredTimeSlot = topTimeSlot
        )
    }

    /**
     * Smartly rank a candidate list of videos using RecommendationPipelineEngine.
     */
    fun rankVideos(
        candidateVideos: List<VideoItem>,
        watchHistory: List<WatchHistoryEntity>,
        likedVideoIds: Set<String>,
        dislikedVideoIds: Set<String>
    ): List<VideoItem> {
        if (candidateVideos.isEmpty()) return emptyList()

        val profile = RecommendationPipelineEngine.buildTasteProfile(
            watchHistory = watchHistory,
            bookmarks = emptyList(),
            likedVideoIds = likedVideoIds,
            dislikedVideoIds = dislikedVideoIds
        )

        return RecommendationPipelineEngine.processPipeline(
            candidates = candidateVideos,
            tasteProfile = profile,
            watchHistory = watchHistory,
            likedVideoIds = likedVideoIds,
            dislikedVideoIds = dislikedVideoIds
        )
    }

    private fun getTimeSlotFromTimestamp(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> "MORNING"
            in 12..17 -> "AFTERNOON"
            in 18..23 -> "EVENING"
            else -> "LATE_NIGHT"
        }
    }
}
