package com.example.util

import com.example.model.VideoItem
import com.example.recommendation.ClipToFullContentHelper
import java.util.Locale

/**
 * Intelligent Reaction Engine & Multi-Part Organization Helper.
 *
 * Detects, organizes, and classifies movie/series/video reaction videos into:
 * - Parts (Part 1, Part 2, Part 3, Part 4, etc.)
 * - Uncut & Full Feature Reactions
 * - Episode Reactions (Ep 1, Ep 2, etc.)
 * - Blind / First Time Reactions
 */
object ReactionHelper {

    data class ReactionMetadata(
        val isReaction: Boolean,
        val partLabel: String?,
        val partNumber: Int?,
        val totalParts: Int? = null,
        val isFullUncut: Boolean = false,
        val isFirstTime: Boolean = false,
        val seriesOrMovieTitle: String? = null,
        val reactorName: String? = null
    )

    data class MultiPartReactionGroup(
        val reactorName: String,
        val coreTitle: String,
        val parts: List<VideoItem>
    )

    private val PART_OF_REGEX = Regex("""(?i)\b(?:part|pt\.?)\s*(\d{1,2})\s*(?:of|\/)\s*(\d{1,2})\b""")
    private val PART_REGEX = Regex("""(?i)\b(?:part|pt\.?|volume|vol\.?)\s*#?\s*(\d{1,2})\b""")
    private val FRACTION_PART_REGEX = Regex("""(?i)\b(\d{1,2})\s*\/\s*(\d{1,2})\b""")
    private val EPISODE_REGEX = Regex("""(?i)\b(?:episode|ep\.?)\s*#?\s*(\d{1,3})\b""")

    private val REACTION_KEYWORDS = listOf(
        "reaction", "react", "reacts", "reacting", "reacted",
        "blind reaction", "first time watching", "first time seeing", "first time hearing",
        "watch along", "watch party", "review & reaction", "reaction & review",
        "movie reaction", "trailer reaction", "film reaction", "breakdown & reaction",
        "live reaction", "fan reaction", "theatre reaction", "audience reaction",
        "uncut reaction", "full reaction", "couple reacts", "watches for the first time"
    )

    /**
     * Determines whether a video item is genuinely a reaction video.
     */
    fun isReactionVideo(video: VideoItem): Boolean {
        val titleLower = (video.title ?: "").lowercase(Locale.ROOT)
        val descLower = (video.description ?: "").lowercase(Locale.ROOT)
        val uploaderLower = (video.uploaderName ?: "").lowercase(Locale.ROOT)
        val tagsLower = video.tags.map { it.lowercase(Locale.ROOT) }

        if (uploaderLower.contains("react") || uploaderLower.contains("reaction")) {
            return true
        }

        return REACTION_KEYWORDS.any { kw ->
            titleLower.contains(kw) || tagsLower.contains(kw) || descLower.take(200).contains(kw)
        }
    }

    /**
     * Extracts reaction metadata including Part number, Total parts, and Uncut status.
     */
    fun extractReactionMetadata(video: VideoItem): ReactionMetadata {
        val title = video.title ?: ""
        val titleLower = title.lowercase(Locale.ROOT)
        val uploader = video.uploaderName ?: "Creator"

        val isReaction = isReactionVideo(video)
        if (!isReaction) {
            return ReactionMetadata(
                isReaction = false,
                partLabel = null,
                partNumber = null,
                totalParts = null,
                isFullUncut = false,
                isFirstTime = false,
                seriesOrMovieTitle = null,
                reactorName = uploader
            )
        }

        val isFullUncut = titleLower.contains("uncut") ||
                titleLower.contains("full reaction") ||
                titleLower.contains("complete reaction") ||
                titleLower.contains("entire movie") ||
                titleLower.contains("full movie reaction") ||
                titleLower.contains("patreon uncut")

        val isFirstTime = titleLower.contains("first time") ||
                titleLower.contains("blind") ||
                titleLower.contains("never seen")

        val extractedCore = ClipToFullContentHelper.extractCleanMovieTitle(title)
            .replace(Regex("""(?i)\b(reaction|reacts|reacting|react|uncut|full|first time watching|blind)\b"""), " ")
            .trim()

        // 1. Check for "Part X of Y" (e.g. Part 1 of 3)
        val partOfMatch = PART_OF_REGEX.find(title)
        if (partOfMatch != null) {
            val partNum = partOfMatch.groupValues[1].toIntOrNull() ?: 1
            val totalParts = partOfMatch.groupValues[2].toIntOrNull()
            return ReactionMetadata(
                isReaction = true,
                partLabel = if (totalParts != null) "PART $partNum/$totalParts" else "PART $partNum",
                partNumber = partNum,
                totalParts = totalParts,
                isFullUncut = isFullUncut,
                isFirstTime = isFirstTime,
                seriesOrMovieTitle = extractedCore.takeIf { it.isNotBlank() },
                reactorName = uploader
            )
        }

        // 2. Check for standard Part N (e.g. Part 1, Pt 2, Volume 3)
        val partMatch = PART_REGEX.find(title)
        if (partMatch != null) {
            val num = partMatch.groupValues[1].toIntOrNull() ?: 1
            return ReactionMetadata(
                isReaction = true,
                partLabel = "PART $num",
                partNumber = num,
                totalParts = null,
                isFullUncut = isFullUncut,
                isFirstTime = isFirstTime,
                seriesOrMovieTitle = extractedCore.takeIf { it.isNotBlank() },
                reactorName = uploader
            )
        }

        // 3. Check for Fraction e.g. 1/3 or 2/4 in title
        val fracMatch = FRACTION_PART_REGEX.find(title)
        if (fracMatch != null) {
            val partNum = fracMatch.groupValues[1].toIntOrNull() ?: 1
            val total = fracMatch.groupValues[2].toIntOrNull()
            if (partNum in 1..9 && total != null && total in 2..9 && partNum <= total) {
                return ReactionMetadata(
                    isReaction = true,
                    partLabel = "PART $partNum/$total",
                    partNumber = partNum,
                    totalParts = total,
                    isFullUncut = isFullUncut,
                    isFirstTime = isFirstTime,
                    seriesOrMovieTitle = extractedCore.takeIf { it.isNotBlank() },
                    reactorName = uploader
                )
            }
        }

        // 4. Check for Episode N
        val epMatch = EPISODE_REGEX.find(title)
        if (epMatch != null) {
            val num = epMatch.groupValues[1].toIntOrNull() ?: 1
            return ReactionMetadata(
                isReaction = true,
                partLabel = "EP $num",
                partNumber = num,
                totalParts = null,
                isFullUncut = isFullUncut,
                isFirstTime = isFirstTime,
                seriesOrMovieTitle = extractedCore.takeIf { it.isNotBlank() },
                reactorName = uploader
            )
        }

        if (isFullUncut) {
            return ReactionMetadata(
                isReaction = true,
                partLabel = "FULL / UNCUT",
                partNumber = 0,
                totalParts = null,
                isFullUncut = true,
                isFirstTime = isFirstTime,
                seriesOrMovieTitle = extractedCore.takeIf { it.isNotBlank() },
                reactorName = uploader
            )
        }

        if (isFirstTime) {
            return ReactionMetadata(
                isReaction = true,
                partLabel = "FIRST TIME",
                partNumber = null,
                totalParts = null,
                isFullUncut = false,
                isFirstTime = true,
                seriesOrMovieTitle = extractedCore.takeIf { it.isNotBlank() },
                reactorName = uploader
            )
        }

        return ReactionMetadata(
            isReaction = true,
            partLabel = "REACTION",
            partNumber = null,
            totalParts = null,
            isFullUncut = false,
            isFirstTime = false,
            seriesOrMovieTitle = extractedCore.takeIf { it.isNotBlank() },
            reactorName = uploader
        )
    }

    /**
     * Build rich search queries to discover genuine reactions for any given video or clip.
     */
    fun buildReactionSearchQueries(displayTitle: String): List<String> {
        val cleanTitle = ClipToFullContentHelper.extractCleanMovieTitle(displayTitle)
            .ifBlank { displayTitle.take(30) }

        return listOf(
            "$cleanTitle reaction",
            "$cleanTitle movie reaction",
            "$cleanTitle reaction part 1",
            "$cleanTitle full reaction uncut",
            "$cleanTitle reaction part 2",
            "$cleanTitle reacts"
        )
    }

    /**
     * Compute the dynamic list of part filter chips available for the current reaction list.
     */
    fun computeAvailableFilterChips(videos: List<VideoItem>): List<String> {
        val chips = mutableListOf("All")
        val metaList = videos.map { extractReactionMetadata(it) }

        val hasPart1 = metaList.any { it.partNumber == 1 }
        val hasPart2 = metaList.any { it.partNumber == 2 }
        val hasPart3 = metaList.any { it.partNumber == 3 }
        val hasPart4Plus = metaList.any { it.partNumber != null && it.partNumber > 3 }
        val hasFullUncut = metaList.any { it.isFullUncut }

        if (hasPart1) chips.add("Part 1")
        if (hasPart2) chips.add("Part 2")
        if (hasPart3) chips.add("Part 3")
        if (hasPart4Plus) chips.add("Part 4+")
        if (hasFullUncut) chips.add("Full / Uncut")

        return chips
    }

    /**
     * Group multi-part reactions by creator channel so users can view and navigate sequential parts (Part 1, 2, 3...)
     */
    fun groupMultiPartReactions(videos: List<VideoItem>): List<MultiPartReactionGroup> {
        val groups = mutableMapOf<String, MutableList<VideoItem>>()

        for (v in videos) {
            val meta = extractReactionMetadata(v)
            if (meta.isReaction) {
                val creator = v.uploaderName?.trim()?.takeIf { it.isNotBlank() } ?: "Creator"
                groups.getOrPut(creator) { mutableListOf() }.add(v)
            }
        }

        return groups.filter { it.value.size >= 2 }.map { (creator, items) ->
            val sortedParts = items.sortedWith(compareBy(
                { extractReactionMetadata(it).partNumber ?: 99 },
                { it.title }
            ))
            val coreTitle = items.firstOrNull()?.let { extractReactionMetadata(it).seriesOrMovieTitle } ?: "Reaction"
            MultiPartReactionGroup(
                reactorName = creator,
                coreTitle = coreTitle,
                parts = sortedParts
            )
        }
    }

    /**
     * Filters reaction videos based on selected chip.
     */
    fun filterReactions(videos: List<VideoItem>, filter: String): List<VideoItem> {
        if (filter == "All") return videos

        return videos.filter { video ->
            val meta = extractReactionMetadata(video)
            when (filter) {
                "Part 1" -> meta.partNumber == 1
                "Part 2" -> meta.partNumber == 2
                "Part 3" -> meta.partNumber == 3
                "Part 4+" -> meta.partNumber != null && meta.partNumber >= 4
                "Full / Uncut" -> meta.isFullUncut
                else -> true
            }
        }
    }
}
