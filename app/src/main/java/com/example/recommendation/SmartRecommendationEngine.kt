package com.example.recommendation

import android.util.Log
import com.example.model.VideoItem
import com.example.util.SmartTagExtractor
import java.util.Calendar

object SmartRecommendationEngine {

    private const val TAG = "SmartRecommendationEngine"

    data class TasteVector(
        val categoryScores: Map<String, Float> = emptyMap(),
        val channelScores: Map<String, Float> = emptyMap(),
        val searchIntentTerms: List<String> = emptyList(),
        val searchTokens: Set<String> = emptySet(),
        val totalInteractions: Int = 0
    )

    /**
     * Compute a dynamic multi-signal user taste vector based on:
     * - Watch History & Completion Ratios (>=80% = high intent, <15% = abandonment)
     * - Recent Searches & Search Intent Tokens (+12.0 to +18.0 weight)
     * - Liked Videos (+8.0 weight)
     * - Disliked Videos (-8.0 weight)
     * - Watch Later / Bookmarks (+4.0 weight)
     * - Blocked / Not Interested Channels & Videos
     */
    fun computeTasteVector(
        watchHistory: List<VideoItem>,
        watchProgressMap: Map<String, Float>,
        likedVideoIds: Set<String>,
        dislikedVideoIds: Set<String>,
        bookmarks: List<VideoItem>,
        notInterestedChannels: Set<String>,
        recentSearches: List<String> = emptyList()
    ): TasteVector {
        val catScores = mutableMapOf<String, Float>()
        val chanScores = mutableMapOf<String, Float>()
        val searchTokens = mutableSetOf<String>()
        val cleanSearchTerms = mutableListOf<String>()
        var interactions = 0

        // 1. Process and Infer Intent from Recent Searches
        val stopWords = setOf(
            "the", "and", "for", "with", "from", "this", "that", "what", "how", "why",
            "full", "movie", "video", "official", "trailer", "episode", "season", "watch",
            "online", "free", "download", "stream", "hindi", "english", "dubbed", "dual"
        )

        for ((index, rawQuery) in recentSearches.take(10).withIndex() ) {
            val q = rawQuery.trim()
            if (q.isBlank()) continue
            cleanSearchTerms.add(q)
            interactions++

            // Recency weighting (most recent search has higher influence)
            val recencyWeight = (14.0f - (index * 1.5f)).coerceAtLeast(4.0f)

            val qLower = q.lowercase()
            val tokens = qLower.split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 2 && it !in stopWords }
            searchTokens.addAll(tokens)

            // Infer Categories from search query
            when {
                qLower.contains("trailer") || qLower.contains("teaser") || qLower.contains("first look") -> {
                    catScores["trailer"] = (catScores["trailer"] ?: 0f) + recencyWeight
                    catScores["movie_trailer"] = (catScores["movie_trailer"] ?: 0f) + recencyWeight
                }
                qLower.contains("movie") || qLower.contains("film") || qLower.contains("cinema") ||
                qLower.contains("spider") || qLower.contains("batman") || qLower.contains("dune") ||
                qLower.contains("interstellar") || qLower.contains("oppenheimer") || qLower.contains("deadpool") ||
                qLower.contains("avengers") || qLower.contains("marvel") || qLower.contains("dc") -> {
                    catScores["movie"] = (catScores["movie"] ?: 0f) + recencyWeight
                    catScores["movie_trailer"] = (catScores["movie_trailer"] ?: 0f) + (recencyWeight * 0.8f)
                }
                qLower.contains("series") || qLower.contains("season") || qLower.contains("episode") ||
                qLower.contains("reacher") || qLower.contains("silo") || qLower.contains("stranger things") ||
                qLower.contains("arcane") || qLower.contains("outer banks") || qLower.contains("show") -> {
                    catScores["series"] = (catScores["series"] ?: 0f) + recencyWeight
                }
                qLower.contains("anime") || qLower.contains("frieren") || qLower.contains("jujutsu") ||
                qLower.contains("solo leveling") || qLower.contains("one piece") || qLower.contains("naruto") ||
                qLower.contains("demon slayer") || qLower.contains("amv") || qLower.contains("manga") -> {
                    catScores["anime"] = (catScores["anime"] ?: 0f) + recencyWeight
                    catScores["anime_trailer"] = (catScores["anime_trailer"] ?: 0f) + (recencyWeight * 0.8f)
                }
                qLower.contains("game") || qLower.contains("gameplay") || qLower.contains("minecraft") ||
                qLower.contains("gta") || qLower.contains("roblox") || qLower.contains("fortnite") ||
                qLower.contains("valorant") || qLower.contains("walkthrough") -> {
                    catScores["gaming"] = (catScores["gaming"] ?: 0f) + recencyWeight
                    catScores["gameplay"] = (catScores["gameplay"] ?: 0f) + recencyWeight
                }
                qLower.contains("music") || qLower.contains("song") || qLower.contains("soundtrack") ||
                qLower.contains("ost") || qLower.contains("lo-fi") || qLower.contains("lyrics") ||
                qLower.contains("concert") || qLower.contains("album") -> {
                    catScores["music"] = (catScores["music"] ?: 0f) + recencyWeight
                }
                qLower.contains("podcast") || qLower.contains("interview") || qLower.contains("talk") ||
                qLower.contains("rogan") || qLower.contains("huberman") || qLower.contains("lex") -> {
                    catScores["podcast"] = (catScores["podcast"] ?: 0f) + recencyWeight
                }
                qLower.contains("comedy") || qLower.contains("stand up") || qLower.contains("funny") ||
                qLower.contains("meme") || qLower.contains("roast") || qLower.contains("parody") -> {
                    catScores["comedy"] = (catScores["comedy"] ?: 0f) + recencyWeight
                }
                qLower.contains("tech") || qLower.contains("iphone") || qLower.contains("unboxing") ||
                qLower.contains("review") || qLower.contains("samsung") || qLower.contains("pixel") ||
                qLower.contains("ai") || qLower.contains("chatgpt") || qLower.contains("coding") -> {
                    catScores["tech"] = (catScores["tech"] ?: 0f) + recencyWeight
                    catScores["ai"] = (catScores["ai"] ?: 0f) + (recencyWeight * 0.8f)
                }
                qLower.contains("science") || qLower.contains("space") || qLower.contains("physics") ||
                qLower.contains("quantum") || qLower.contains("nasa") || qLower.contains("explained") -> {
                    catScores["science"] = (catScores["science"] ?: 0f) + recencyWeight
                    catScores["education"] = (catScores["education"] ?: 0f) + recencyWeight
                }
            }
        }

        // 2. Evaluate Watch History & Progress Fractions
        for (video in watchHistory.take(50)) {
            interactions++
            val tags = SmartTagExtractor.extractTags(video)
            val prog = watchProgressMap[video.id] ?: 0.5f

            val weightMultiplier = when {
                prog >= 0.8f -> 5.0f
                prog >= 0.5f -> 3.0f
                prog >= 0.2f -> 1.0f
                else -> -2.0f // Early abandonment
            }

            for (tag in tags) {
                catScores[tag.category] = (catScores[tag.category] ?: 0f) + weightMultiplier
            }

            val channel = video.uploaderName.lowercase().trim()
            if (channel.isNotBlank()) {
                chanScores[channel] = (chanScores[channel] ?: 0f) + weightMultiplier
            }
        }

        // 3. Evaluate Liked Videos
        for (likedId in likedVideoIds) {
            interactions++
            val matchingVideo = watchHistory.firstOrNull { it.id == likedId }
                ?: bookmarks.firstOrNull { it.id == likedId }

            if (matchingVideo != null) {
                val tags = SmartTagExtractor.extractTags(matchingVideo)
                for (tag in tags) {
                    catScores[tag.category] = (catScores[tag.category] ?: 0f) + 8.0f
                }
                val ch = matchingVideo.uploaderName.lowercase().trim()
                if (ch.isNotBlank()) {
                    chanScores[ch] = (chanScores[ch] ?: 0f) + 10.0f
                }
            }
        }

        // 4. Evaluate Disliked Videos
        for (dislikedId in dislikedVideoIds) {
            interactions++
            val matchingVideo = watchHistory.firstOrNull { it.id == dislikedId }
            if (matchingVideo != null) {
                val tags = SmartTagExtractor.extractTags(matchingVideo)
                for (tag in tags) {
                    catScores[tag.category] = (catScores[tag.category] ?: 0f) - 8.0f
                }
                val ch = matchingVideo.uploaderName.lowercase().trim()
                if (ch.isNotBlank()) {
                    chanScores[ch] = (chanScores[ch] ?: 0f) - 12.0f
                }
            }
        }

        // 5. Evaluate Bookmarks / Watch Later
        for (bm in bookmarks.take(30)) {
            interactions++
            val tags = SmartTagExtractor.extractTags(bm)
            for (tag in tags) {
                catScores[tag.category] = (catScores[tag.category] ?: 0f) + 4.0f
            }
            val ch = bm.uploaderName.lowercase().trim()
            if (ch.isNotBlank()) {
                chanScores[ch] = (chanScores[ch] ?: 0f) + 4.0f
            }
        }

        // 6. Heavy Penalty for Not Interested / Blocked Channels
        for (blockedChan in notInterestedChannels) {
            val cleanCh = blockedChan.lowercase().trim()
            if (cleanCh.isNotBlank()) {
                chanScores[cleanCh] = -100.0f
            }
        }

        return TasteVector(
            categoryScores = catScores,
            channelScores = chanScores,
            searchIntentTerms = cleanSearchTerms,
            searchTokens = searchTokens,
            totalInteractions = interactions
        )
    }

    /**
     * Score a single candidate video using the user's taste vector,
     * time-of-day circadian learning, and active context (for player related content).
     */
    fun scoreVideo(
        video: VideoItem,
        tasteVector: TasteVector,
        activeVideo: VideoItem? = null,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): Float {
        var score = 10.0f

        val tags = SmartTagExtractor.extractTags(video)
        val channel = video.uploaderName.lowercase().trim()
        val titleLower = video.title.lowercase().trim()
        val descLower = (video.description ?: "").lowercase()

        // A. Category Alignment
        for (tag in tags) {
            val catW = tasteVector.categoryScores[tag.category] ?: 0f
            score += catW * 2.5f
        }

        // B. Channel Affinity
        if (channel.isNotBlank()) {
            val chanW = tasteVector.channelScores[channel] ?: 0f
            score += chanW * 3.8f
        }

        // C. Direct Search Intent & Keyword Relevance Matching
        if (tasteVector.searchTokens.isNotEmpty()) {
            var tokenHits = 0
            for (token in tasteVector.searchTokens) {
                if (titleLower.contains(token)) {
                    tokenHits++
                } else if (channel.contains(token)) {
                    tokenHits++
                } else if (descLower.contains(token)) {
                    score += 3.0f
                }
            }
            if (tokenHits > 0) {
                score += tokenHits * 14.0f // Significant boost for videos matching search terms
            }
        }

        // D. Exact Search Phrase Match Bonus
        for (searchTerm in tasteVector.searchIntentTerms) {
            val termLower = searchTerm.lowercase()
            if (termLower.length >= 4 && (titleLower.contains(termLower) || channel.contains(termLower))) {
                score += 32.0f // Huge relevance bonus for direct matches to recent searches!
                break
            }
        }

        // E. Circadian Time-of-Day Boosts
        for (tag in tags) {
            val cat = tag.category
            when (hourOfDay) {
                in 6..11 -> {
                    if (cat in listOf("News", "Tech", "Education", "Science", "news", "tech", "education", "science")) score += 4.5f
                }
                in 12..17 -> {
                    if (cat in listOf("Comedy", "Gaming", "Music", "Sports", "Auto", "Food", "comedy", "gaming", "music", "sports")) score += 4.5f
                }
                in 18..23 -> {
                    if (cat in listOf("Movie Trailer", "Movie", "Video Essay", "Philosophy", "Anime", "Cinema", "movie", "movie_trailer", "anime", "series")) score += 5.5f
                }
                else -> { // Late night 0..5 AM
                    if (cat in listOf("Video Essay", "Philosophy", "Music", "Movie", "Podcast", "music", "podcast", "video_essay")) score += 4.5f
                }
            }
        }

        // F. Contextual Match (Active Video Player)
        if (activeVideo != null) {
            val activeTags = SmartTagExtractor.extractTags(activeVideo).map { it.category }.toSet()
            val candidateTags = tags.map { it.category }.toSet()
            val common = activeTags.intersect(candidateTags)
            score += common.size * 9.0f

            val activeChannel = activeVideo.uploaderName.lowercase().trim()
            if (activeChannel.isNotBlank() && activeChannel == channel) {
                score += 12.0f // Same creator bonus
            }
        }

        return score
    }

    /**
     * Rank candidate videos using multi-signal scoring, channel diversity caps, and blockage filtering.
     */
    fun rankCandidateVideos(
        candidates: List<VideoItem>,
        tasteVector: TasteVector,
        activeVideo: VideoItem? = null,
        blockedVideoIds: Set<String> = emptySet(),
        blockedChannels: Set<String> = emptySet(),
        maxChannelLimit: Int = 2
    ): List<VideoItem> {
        if (candidates.isEmpty()) return emptyList()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Filter out blocked items
        val validCandidates = candidates
            .distinctBy { (it.providerId ?: "gen") + "_" + it.id }
            .filterNot { video ->
                val vid = video.id.trim()
                val ch = video.uploaderName?.lowercase()?.trim() ?: ""
                blockedVideoIds.contains(vid) || (ch.isNotEmpty() && blockedChannels.contains(ch))
            }

        if (validCandidates.isEmpty()) return emptyList()

        // Score all valid candidates
        val scoredList = validCandidates.map { video ->
            val score = scoreVideo(video, tasteVector, activeVideo, hour)
            video to score
        }.sortedByDescending { it.second }

        // Apply Channel Diversity Cap
        val channelCounts = mutableMapOf<String, Int>()
        val result = mutableListOf<VideoItem>()

        for ((video, _) in scoredList) {
            val ch = video.uploaderName?.lowercase()?.trim() ?: "unknown"
            val count = channelCounts[ch] ?: 0
            if (count < maxChannelLimit) {
                result.add(video)
                channelCounts[ch] = count + 1
            }
        }

        // Fill remaining if needed
        if (result.size < scoredList.size) {
            for ((video, _) in scoredList) {
                if (result.none { it.id == video.id }) {
                    result.add(video)
                }
            }
        }

        return result
    }
}
