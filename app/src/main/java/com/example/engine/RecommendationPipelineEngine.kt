package com.example.engine

import com.example.db.BookmarkEntity
import com.example.db.WatchHistoryEntity
import com.example.model.VideoItem
import java.util.Calendar

data class UserTasteProfile(
    val channelScores: Map<String, Float> = emptyMap(),
    val keywordScores: Map<String, Float> = emptyMap(),
    val providerScores: Map<String, Float> = emptyMap(),
    val durationPreferenceSec: Long = 300L,
    val timeSlotDurationMap: Map<String, Long> = emptyMap(),
    val notInterestedChannels: Set<String> = emptySet(),
    val notInterestedVideoIds: Set<String> = emptySet(),
    val recentSearchQueries: List<String> = emptyList(),
    val isColdStart: Boolean = true
)

object RecommendationPipelineEngine {

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
     * Builds a learned taste profile from watch history, bookmarks, likes, dislikes, and explicit "Not Interested" marks.
     */
    fun buildTasteProfile(
        watchHistory: List<WatchHistoryEntity>,
        bookmarks: List<BookmarkEntity>,
        likedVideoIds: Set<String>,
        dislikedVideoIds: Set<String>,
        notInterestedChannels: Set<String> = emptySet(),
        notInterestedVideoIds: Set<String> = emptySet(),
        recentSearches: List<String> = emptyList()
    ): UserTasteProfile {
        if (watchHistory.isEmpty() && bookmarks.isEmpty() && likedVideoIds.isEmpty()) {
            return UserTasteProfile(
                notInterestedChannels = notInterestedChannels,
                notInterestedVideoIds = notInterestedVideoIds,
                recentSearchQueries = recentSearches,
                isColdStart = true
            )
        }

        val channelMap = mutableMapOf<String, Float>()
        val keywordMap = mutableMapOf<String, Float>()
        val providerMap = mutableMapOf<String, Float>()
        val timeSlotDurations = mutableMapOf<String, MutableList<Long>>()

        watchHistory.forEach { entry ->
            val channel = entry.channelName.lowercase().trim()
            val provider = (entry.providerId ?: "youtube").lowercase()
            val timeSlot = getTimeSlotFromTimestamp(entry.timestamp)

            // Positive vs Negative Signals
            val progress = entry.progressFraction
            val signalWeight = when {
                progress >= 0.85f -> 6.0f   // High completion
                progress in 0.3f..0.84f -> 3.0f // Partial view
                progress in 0.05f..0.29f -> -2.0f // Abandoned early
                progress < 0.05f -> -5.0f   // Instant skip (<10s)
                else -> 1.0f
            }

            // Feedback weight
            val feedbackWeight = when {
                likedVideoIds.contains(entry.videoId) -> 12.0f
                dislikedVideoIds.contains(entry.videoId) -> -18.0f
                else -> 0.0f
            }

            val totalWeight = signalWeight + feedbackWeight

            if (channel.isNotBlank()) {
                channelMap[channel] = (channelMap[channel] ?: 0f) + totalWeight
            }
            if (provider.isNotBlank()) {
                providerMap[provider] = (providerMap[provider] ?: 0f) + (totalWeight * 0.5f)
            }

            // Title keywords
            val keywords = entry.title.lowercase()
                .split(" ", "-", "_", "|")
                .filter { it.length > 3 && !isStopWord(it) }
            for (kw in keywords) {
                keywordMap[kw] = (keywordMap[kw] ?: 0f) + (totalWeight * 0.3f)
            }

            // Record duration for time slot
            val approxDurationSec = if (entry.duration.isNotBlank()) parseDurationToSeconds(entry.duration) else 300L
            timeSlotDurations.getOrPut(timeSlot) { mutableListOf() }.add(approxDurationSec)
        }

        // Add Bookmark signals
        bookmarks.forEach { bm ->
            val channel = bm.channelName.lowercase().trim()
            if (channel.isNotBlank()) {
                channelMap[channel] = (channelMap[channel] ?: 0f) + 8.0f
            }
            val keywords = bm.title.lowercase().split(" ", "-", "_").filter { it.length > 3 && !isStopWord(it) }
            for (kw in keywords) {
                keywordMap[kw] = (keywordMap[kw] ?: 0f) + 2.0f
            }
        }

        // Add Search query signals
        recentSearches.forEach { query ->
            val keywords = query.lowercase().split(" ").filter { it.length > 2 && !isStopWord(it) }
            for (kw in keywords) {
                keywordMap[kw] = (keywordMap[kw] ?: 0f) + 5.0f
            }
        }

        val avgTimeSlotDurations = timeSlotDurations.mapValues { entry ->
            if (entry.value.isNotEmpty()) entry.value.average().toLong() else 300L
        }

        return UserTasteProfile(
            channelScores = channelMap,
            keywordScores = keywordMap,
            providerScores = providerMap,
            durationPreferenceSec = if (watchHistory.isNotEmpty()) 600L else 300L,
            timeSlotDurationMap = avgTimeSlotDurations,
            notInterestedChannels = notInterestedChannels.map { it.lowercase().trim() }.toSet(),
            notInterestedVideoIds = notInterestedVideoIds,
            recentSearchQueries = recentSearches,
            isColdStart = false
        )
    }

    /**
     * Executes the full 7-stage recommendation pipeline on candidate videos.
     */
    fun processPipeline(
        candidates: List<VideoItem>,
        tasteProfile: UserTasteProfile,
        watchHistory: List<WatchHistoryEntity>,
        likedVideoIds: Set<String>,
        dislikedVideoIds: Set<String>,
        coldStartSelectedTopics: List<String> = emptyList()
    ): List<VideoItem> {
        if (candidates.isEmpty()) return emptyList()

        val watchedIds = watchHistory.map { it.videoId }.toSet()
        val currentTimeSlot = getCurrentTimeSlot()
        val targetDurationSec = tasteProfile.timeSlotDurationMap[currentTimeSlot] ?: 400L

        // STAGE 1 & 2: Hard Filtering
        val filteredCandidates = candidates.filter { video ->
            val vidId = video.id
            val channelLower = (video.uploaderName ?: "").lowercase().trim()

            !dislikedVideoIds.contains(vidId) &&
            !tasteProfile.notInterestedVideoIds.contains(vidId) &&
            !tasteProfile.notInterestedChannels.contains(channelLower)
        }

        if (filteredCandidates.isEmpty()) return emptyList()

        // STAGE 3 & 4: Personalization + Contextual Ranking
        val scoredItems = filteredCandidates.map { video ->
            var score = 50.0f // Base score

            if (tasteProfile.isColdStart) {
                // Cold-Start scoring using initial interest topics and provider quality
                val titleLower = video.title.lowercase()
                for (topic in coldStartSelectedTopics) {
                    if (titleLower.contains(topic.lowercase())) {
                        score += 15.0f
                    }
                }
                if ((video.viewCount ?: 0L) > 10_000L) score += 5.0f
            } else {
                // 1. Channel / Creator Affinity
                val channelLower = (video.uploaderName ?: "").lowercase().trim()
                val channelScore = tasteProfile.channelScores[channelLower] ?: 0f
                score += (channelScore * 1.5f).coerceIn(-30f, 40f)

                // 2. Keyword & Topic Match
                val titleLower = video.title.lowercase()
                tasteProfile.keywordScores.forEach { (kw, kwScore) ->
                    if (titleLower.contains(kw)) {
                        score += (kwScore * 0.8f).coerceIn(-10f, 15f)
                    }
                }

                // 3. Provider Preference
                val providerLower = (video.providerId ?: "").lowercase()
                val providerScore = tasteProfile.providerScores[providerLower] ?: 0f
                score += (providerScore * 0.5f).coerceIn(-10f, 15f)

                // 4. Like Boost
                if (likedVideoIds.contains(video.id)) {
                    score += 20.0f
                }

                // 5. Circadian Duration Context Boost
                val videoDurationSec = video.durationSeconds ?: 300L
                val durationDiff = Math.abs(videoDurationSec - targetDurationSec)
                if (durationDiff < 300L) {
                    score += 10.0f
                } else if (durationDiff < 600L) {
                    score += 5.0f
                }
            }

            // STAGE 5: Freshness & Overexposure Penalty
            if (watchedIds.contains(video.id)) {
                score -= 25.0f // Watched item penalty
            }

            Pair(video, score)
        }.sortedByDescending { it.second }

        // STAGE 6: Channel Diversity & Overexposure Cap (Max 2 consecutive from same channel)
        val finalFeed = mutableListOf<VideoItem>()
        var lastChannel = ""
        var consecutiveCount = 0

        for ((video, _) in scoredItems) {
            val channel = (video.uploaderName ?: "").lowercase().trim()
            if (channel.isNotBlank() && channel == lastChannel) {
                consecutiveCount++
                if (consecutiveCount <= 2) {
                    finalFeed.add(video)
                }
            } else {
                lastChannel = channel
                consecutiveCount = 1
                finalFeed.add(video)
            }
        }

        return finalFeed
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

    private fun parseDurationToSeconds(duration: String): Long {
        return try {
            val parts = duration.split(":").mapNotNull { it.toLongOrNull() }
            when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 300L
            }
        } catch (t: Throwable) {
            300L
        }
    }

    private fun isStopWord(word: String): Boolean {
        val stopWords = setOf("the", "this", "that", "with", "from", "and", "for", "you", "your", "video", "official", "hd", "4k", "full", "movie", "song", "episode")
        return stopWords.contains(word)
    }
}
