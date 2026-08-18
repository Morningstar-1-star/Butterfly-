package com.example.util

import android.util.Log
import com.example.model.StreamData
import com.example.model.VideoItem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ReactionType {
    TRAILER_REACTION,
    MOVIE_REACTION,
    UNCUT_FULL,
    MULTI_PART,
    GENERAL_REACTION
}

data class ReactionGroup(
    val channelName: String,
    val mainItem: VideoItem,
    val reactionType: ReactionType,
    val partLabel: String,
    val partNumber: Int,
    val allParts: List<VideoItem> = emptyList()
)

object ReactionHelper {

    private const val TAG = "ReactionHelper"

    /**
     * Determines if the current video/media qualifies for showing the "Reactions" tab.
     * Criteria:
     * 1. Torrent / Debrid / TMDB Movies & TV Series
     * 2. YouTube Trailers, Teasers, First Looks, Special Looks, Promos
     * 3. Famous, Popular, or Viral Videos (high view count or well-known franchise titles)
     *
     * Returns false for obscure, random, or personal non-famous clips.
     */
    fun isReactionEligible(videoItem: VideoItem?, streamData: StreamData?): Boolean {
        if (videoItem == null && streamData == null) return false

        val title = (streamData?.title ?: videoItem?.title ?: "").lowercase()
        val channel = (streamData?.channelName ?: videoItem?.uploaderName ?: "").lowercase()
        val providerId = (streamData?.providerId ?: videoItem?.providerId ?: "").lowercase()
        val viewCount = streamData?.viewCount ?: videoItem?.viewCount ?: -1L
        val isTorrent = streamData?.isTorrent == true || providerId in listOf(
            "torrent", "debrid", "tmdb", "yts", "eztv", "comet", "torrentio", "mediafusion", "vega", "mal", "anilist", "jikan", "kitsu"
        )

        // 1. Torrent / Movies / TV Series -> ALWAYS ELIGIBLE
        if (isTorrent) return true

        // 2. Trailers / Teasers / Promos -> ALWAYS ELIGIBLE
        val trailerKeywords = listOf(
            "trailer", "official trailer", "teaser", "teaser trailer",
            "special look", "first look", "sneak peek", "promo",
            "announcement trailer", "gameplay trailer", "cinematic trailer", "tv spot"
        )
        if (trailerKeywords.any { title.contains(it) }) return true

        val studioBrands = listOf(
            "marvel", "dc", "sony pictures", "warner bros", "paramount",
            "universal pictures", "ign", "netflix", "rotten tomatoes",
            "disney", "a24", "hbo", "lionsgate", "star wars", "pixar",
            "rockstar", "bandai namco", "crunchyroll", "anime", "hulu", "apple tv"
        )
        if (studioBrands.any { channel.contains(it) || title.contains(it) }) return true

        // 3. Famous Franchises & Viral Hits
        val famousFranchises = listOf(
            "avengers", "doomsday", "secret wars", "spider-man", "batman",
            "superman", "deadpool", "wolverine", "gta", "gta 6", "stranger things",
            "house of the dragon", "game of thrones", "breaking bad", "squid game",
            "one piece", "demon slayer", "jujutsu kaisen", "attack on titan",
            "bleach", "dragon ball", "naruto", "solo leveling", "arcane"
        )
        if (famousFranchises.any { title.contains(it) }) return true

        // 4. Popular / High View Count (> 250,000 views)
        if (viewCount >= 250_000L) return true

        return false
    }

    /**
     * Cleans the video title to construct clean, high-precision search queries for YouTube reaction channels.
     */
    fun cleanSearchTitle(rawTitle: String): String {
        var cleaned = rawTitle
            .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), " ")
            .replace(Regex("(?i)4k|1080p|720p|hd|hdr|bluray|web-dl|x264|x265|yts|torrent"), " ")
            .replace(Regex("(?i)official trailer|teaser trailer|special look|in theaters|first look|sneak peek|tv spot|promo"), " ")
            .replace(Regex("(?i)marvel entertainment|sony pictures|warner bros|paramount|netflix"), " ")
            .replace(Regex("[|:_/-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.length < 3) {
            cleaned = rawTitle.replace(Regex("[|:_/-]"), " ").replace(Regex("\\s+"), " ").trim()
        }
        return cleaned
    }

    /**
     * Fetches reaction videos from YouTube based on the title & uploader.
     */
    suspend fun fetchReactions(
        title: String,
        uploaderName: String,
        isTorrent: Boolean
    ): List<ReactionGroup> = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSearchTitle(title)
        if (cleanTitle.isBlank()) return@withContext emptyList()

        val queries = listOf(
            "$cleanTitle trailer reaction",
            "$cleanTitle movie reaction",
            "$cleanTitle reaction full"
        )

        val rawFetchedVideos = mutableListOf<VideoItem>()
        

        

        // Deduplicate and filter out videos that are not authentic reactions
        val reactionKeywords = listOf("reaction", "react", "reacts", "reacting", "blind reaction", "first time watching", "trailer reaction", "movie reaction", "episode reaction", "part 1", "part 2", "uncut")

        val validReactionItems = rawFetchedVideos.distinctBy { it.id }.filter { video ->
            val vTitle = video.title.lowercase()
            val vChannel = video.uploaderName.lowercase()
            val isReactionTitle = reactionKeywords.any { vTitle.contains(it) || vChannel.contains(it) }
            val isSameAsOriginal = vTitle == title.lowercase()
            isReactionTitle && !isSameAsOriginal
        }

        // Group by Reaction Channel to group multi-part uploads (Part 1, Part 2, etc.)
        val groupedByChannel = validReactionItems.groupBy { video ->
            video.uploaderName.ifBlank { "Reaction Channel" }
        }

        val resultGroups = mutableListOf<ReactionGroup>()

        groupedByChannel.forEach { (channel, videos) ->
            val sortedVideos = videos.sortedWith { v1, v2 ->
                val p1 = extractPartNumber(v1.title)
                val p2 = extractPartNumber(v2.title)
                p1.compareTo(p2)
            }

            val primaryVideo = sortedVideos.first()
            val primaryTitle = primaryVideo.title.lowercase()

            val type = when {
                primaryTitle.contains("trailer") || primaryTitle.contains("teaser") -> ReactionType.TRAILER_REACTION
                primaryTitle.contains("full") || primaryTitle.contains("uncut") || primaryTitle.contains("movie") -> ReactionType.UNCUT_FULL
                sortedVideos.size > 1 || extractPartNumber(primaryTitle) > 0 -> ReactionType.MULTI_PART
                else -> ReactionType.GENERAL_REACTION
            }

            val partNum = extractPartNumber(primaryTitle)
            val partLabel = when {
                partNum > 0 -> "Part $partNum"
                type == ReactionType.TRAILER_REACTION -> "Trailer React"
                type == ReactionType.UNCUT_FULL -> "Full / Uncut"
                else -> "Reaction"
            }

            resultGroups.add(
                ReactionGroup(
                    channelName = channel,
                    mainItem = primaryVideo,
                    reactionType = type,
                    partLabel = partLabel,
                    partNumber = if (partNum > 0) partNum else 1,
                    allParts = sortedVideos
                )
            )
        }

        return@withContext resultGroups.sortedByDescending { it.allParts.size }
    }

    private fun extractPartNumber(title: String): Int {
        val regexes = listOf(
            Regex("(?i)part\\s*(\\d+)"),
            Regex("(?i)pt\\s*(\\d+)"),
            Regex("(?i)part\\s*0*(\\d+)"),
            Regex("(?i)ep\\s*(\\d+)"),
            Regex("(?i)episode\\s*(\\d+)")
        )
        for (r in regexes) {
            val match = r.find(title)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: 0
            }
        }
        return 0
    }
}
