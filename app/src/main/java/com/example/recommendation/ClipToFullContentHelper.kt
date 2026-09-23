package com.example.recommendation

import com.example.model.VideoItem
import java.util.Locale

/**
 * Intelligent Clip-to-Full Content Resolver & Recommender.
 *
 * Detects whether the user is watching an excerpt, clip, scene, trailer,
 * or match highlight, extracts the root media title / matchup,
 * queries for the full movie, full match, or full episode,
 * and up-ranks the complete long-form video to the top of recommendations.
 */
object ClipToFullContentHelper {

    enum class ClipType {
        MOVIE_CLIP,
        MATCH_HIGHLIGHT,
        SERIES_CLIP,
        GENERAL_CLIP,
        REACTION_PART,
        NOT_A_CLIP
    }

    data class ClipAnalysis(
        val type: ClipType,
        val coreTitle: String,
        val secondaryEntity: String? = null, // e.g. Episode number, Year, or Part number
        val searchQueries: List<String> = emptyList()
    )

    private val CLIP_KEYWORDS = setOf(
        "clip", "clips", "scene", "scenes", "best scene", "fight scene", "ending scene",
        "final scene", "trailer", "official trailer", "teaser", "teaser trailer",
        "blooper", "bloopers", "deleted scene", "deleted scenes", "preview",
        "moment", "moments", "movie clip", "official clip", "exclusive clip", "short",
        "opening scene", "intro scene", "climax scene", "chase scene", "post credit",
        "end credit", "death scene", "funny scene", "epic scene", "iconic scene"
    )

    private val HIGHLIGHT_KEYWORDS = setOf(
        "highlight", "highlights", "extended highlights", "full highlights", "match highlights",
        "game highlights", "recap", "all goals", "goals", "wickets", "knockout",
        "round 1", "round by round", "condensed game", "game recap", "fight replay",
        "post fight", "match summary", "penalty shootout", "overtime", "all wickets"
    )

    private val SPORTS_LEAGUES_KEYWORDS = setOf(
        "champions league", "ucl", "premier league", "la liga", "serie a", "bundesliga",
        "world cup", "euro 20", "copa america", "nba", "nfl", "ufc", "wwe", "boxing",
        "ipl", "t20", "cricket", "f1", "formula 1", "tennis", "wimbledon", "super bowl"
    )

    private val SERIES_PATTERNS = listOf(
        Regex("""(?i)\b(s\d{1,2}\s*e\d{1,2})\b"""),
        Regex("""(?i)\b(season\s*\d{1,2}\s*episode\s*\d{1,2})\b"""),
        Regex("""(?i)\b(ep\s*\d{1,3})\b"""),
        Regex("""(?i)\b(episode\s*\d{1,3})\b""")
    )

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "in", "of", "to", "for", "with", "on", "at", "by", "from",
        "part", "clip", "scene", "official", "full", "movie", "hd", "4k", "video", "trailer"
    )

    /**
     * Analyze an active video to determine if it is a clip/highlight and extract its root entity.
     */
    fun analyzeVideo(video: VideoItem?): ClipAnalysis {
        if (video == null) return ClipAnalysis(ClipType.NOT_A_CLIP, "")
        val rawTitle = video.title.trim()
        if (rawTitle.isBlank()) return ClipAnalysis(ClipType.NOT_A_CLIP, "")

        val titleLower = rawTitle.lowercase(Locale.ROOT)
        val tagsLower = video.tags.map { it.lowercase(Locale.ROOT) }
        val duration = video.durationSeconds

        // 0. Check for Reaction Video & Multi-Part Reaction
        if (com.example.util.ReactionHelper.isReactionVideo(video)) {
            val meta = com.example.util.ReactionHelper.extractReactionMetadata(video)
            val rootMedia = meta.seriesOrMovieTitle ?: extractCleanMovieTitle(rawTitle)
                .replace(Regex("""(?i)\b(reaction|reacts|reacting|react|uncut|full|first time)\b"""), " ")
                .trim()

            if (meta.partNumber != null && meta.partNumber > 0) {
                val nextPart = meta.partNumber + 1
                val queries = listOf(
                    "$rootMedia reaction part $nextPart",
                    "$rootMedia reaction part ${meta.partNumber + 2}",
                    "$rootMedia full uncut reaction",
                    "$rootMedia full movie"
                )
                return ClipAnalysis(
                    type = ClipType.REACTION_PART,
                    coreTitle = rootMedia,
                    secondaryEntity = meta.partNumber.toString(),
                    searchQueries = queries
                )
            }
        }

        // 1. Check for Sports / Match Highlights
        val hasHighlightKeyword = HIGHLIGHT_KEYWORDS.any { titleLower.contains(it) } || tagsLower.any { it in HIGHLIGHT_KEYWORDS }
        val hasSportsLeague = SPORTS_LEAGUES_KEYWORDS.any { titleLower.contains(it) } || tagsLower.any { it in SPORTS_LEAGUES_KEYWORDS }
        val hasMatchupPattern = titleLower.contains(" vs ") || titleLower.contains(" v/s ") || titleLower.contains(" v ") || titleLower.contains(" against ")
        
        if (hasHighlightKeyword || (hasMatchupPattern && (hasSportsLeague || titleLower.contains("match") || titleLower.contains("game") || titleLower.contains("cup") || titleLower.contains("league") || titleLower.contains("ufc") || titleLower.contains("boxing")))) {
            val matchup = extractMatchup(rawTitle)
            if (matchup.isNotBlank()) {
                val queries = listOf(
                    "$matchup full match",
                    "$matchup full match replay",
                    "$matchup full game",
                    "$matchup complete match",
                    "$matchup match replay"
                )
                return ClipAnalysis(
                    type = ClipType.MATCH_HIGHLIGHT,
                    coreTitle = matchup,
                    searchQueries = queries
                )
            }
        }

        // 2. Check for Series / Anime Episodes
        val seriesMatch = SERIES_PATTERNS.firstNotNullOfOrNull { it.find(rawTitle) }
        if (seriesMatch != null) {
            val epIndicator = seriesMatch.value
            val showName = rawTitle.substring(0, seriesMatch.range.first)
                .replace(Regex("""(?i)\[.*?\]|\(.*?\)|-|\|"""), " ")
                .trim()
            if (showName.length >= 3) {
                val queries = listOf(
                    "$showName $epIndicator full episode",
                    "$showName $epIndicator full",
                    "$showName full episode"
                )
                return ClipAnalysis(
                    type = ClipType.SERIES_CLIP,
                    coreTitle = showName,
                    secondaryEntity = epIndicator,
                    searchQueries = queries
                )
            }
        }

        // 3. Check for Movie Clip / Scene / Trailer
        val isExplicitClip = CLIP_KEYWORDS.any { titleLower.contains(it) } || tagsLower.any { it in CLIP_KEYWORDS }
        val isShortDuration = duration in 1..900 // under 15 minutes
        val hasMovieIndicators = titleLower.contains("movie") || titleLower.contains("film") ||
                titleLower.contains("cinema") || titleLower.contains("official") ||
                Regex("""\((19|20)\d{2}\)""").containsMatchIn(rawTitle) // Has year e.g. (2024)

        if (isExplicitClip || (isShortDuration && hasMovieIndicators)) {
            val cleanMovieTitle = extractCleanMovieTitle(rawTitle)
            if (cleanMovieTitle.length >= 3) {
                val queries = listOf(
                    "$cleanMovieTitle full movie",
                    "$cleanMovieTitle movie full",
                    "$cleanMovieTitle complete movie",
                    "$cleanMovieTitle full movie english",
                    "$cleanMovieTitle pelicula completa"
                )
                return ClipAnalysis(
                    type = ClipType.MOVIE_CLIP,
                    coreTitle = cleanMovieTitle,
                    searchQueries = queries
                )
            }
        }

        // 4. Check for General Part / Excerpt
        if (titleLower.contains("part 1") || titleLower.contains("pt 1") || titleLower.contains("pt. 1") || titleLower.contains("clip")) {
            val cleanTitle = cleanGeneralTitle(rawTitle)
            if (cleanTitle.length >= 4) {
                return ClipAnalysis(
                    type = ClipType.GENERAL_CLIP,
                    coreTitle = cleanTitle,
                    searchQueries = listOf("$cleanTitle full video", "$cleanTitle full episode", "$cleanTitle full")
                )
            }
        }

        return ClipAnalysis(ClipType.NOT_A_CLIP, cleanGeneralTitle(rawTitle))
    }

    /**
     * Clean Movie Title from raw video title by removing brackets, resolutions, and scene labels.
     */
    fun extractCleanMovieTitle(raw: String): String {
        return raw
            // Remove brackets like [4K], (Official Trailer), etc.
            .replace(Regex("""\[.*?\]|\(.*?\)|【.*?】|「.*?」"""), " ")
            // Remove resolutions & formats
            .replace(Regex("""(?i)\b(4k|1080p|720p|hdr|uhd|60fps|hd|blu-?ray|web-?dl)\b"""), " ")
            // Remove clip indicators
            .replace(Regex("""(?i)\b(official trailer|teaser trailer|trailer|teaser|movie clip|exclusive clip|official clip|clip|clips|fight scene|ending scene|final scene|best scenes|best scene|scene|scenes|deleted scene|blooper|bloopers|preview|featurette)\b"""), " ")
            // Remove common separators and noise
            .replace(Regex("""[-|–—:•]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Extract Sports Matchup (e.g. "Real Madrid vs Barcelona").
     */
    private fun extractMatchup(raw: String): String {
        val cleaned = raw
            .replace(Regex("""\[.*?\]|\(.*?\)|【.*?】"""), " ")
            .replace(Regex("""(?i)\b(4k|1080p|720p|hd|60fps)\b"""), " ")
            .replace(Regex("""(?i)\b(highlights|highlight|extended highlights|full highlights|match highlights|game highlights|recap|all goals|goals|wickets|condensed game)\b"""), " ")
            .replace(Regex("""[-|–—•]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val vsMatch = Regex("""(?i)(.+?)\s+(?:vs|v\/s|v\.)\s+(.+?)(?:\s+(?:20\d{2}|premier|la liga|champions|ucl|nba|nfl|ufc|cup|final|semi-final|leg\s*\d|round\s*\d).*|$|\s*$)""").find(cleaned)
        if (vsMatch != null) {
            val team1 = vsMatch.groupValues[1].trim()
            val team2 = vsMatch.groupValues[2].trim()
            if (team1.isNotBlank() && team2.isNotBlank()) {
                return "$team1 vs $team2"
            }
        }
        return cleaned.take(40)
    }

    private fun cleanGeneralTitle(raw: String): String {
        return raw
            .replace(Regex("""\[.*?\]|\(.*?\)|【.*?】"""), " ")
            .replace(Regex("""(?i)\b(4k|1080p|720p|hd|official|video|audio)\b"""), " ")
            .replace(Regex("""[-|–—•]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Determines whether a candidate video is the confirmed "Full" counterpart of the active clip.
     *
     * Returns a Pair:
     * - Boolean: true if it is a verified full counterpart
     * - String: the badge / explanation to display on the recommendation card
     */
    fun evaluateFullCounterpart(activeVideo: VideoItem, candidate: VideoItem): Pair<Boolean, String?> {
        val analysis = analyzeVideo(activeVideo)
        if (analysis.type == ClipType.NOT_A_CLIP) return Pair(false, null)

        val candTitleLower = (candidate.title ?: "").lowercase(Locale.ROOT)
        val candDescLower = (candidate.description ?: "").lowercase(Locale.ROOT)
        val candDuration = candidate.durationSeconds

        // Never match candidate if it is also a clip/trailer/teaser/scene unless evaluating reaction parts
        val isCandClipOrTrailer = candTitleLower.contains("clip") ||
                candTitleLower.contains("scene") ||
                candTitleLower.contains("trailer") ||
                candTitleLower.contains("teaser") ||
                candTitleLower.contains("ending explained") ||
                candTitleLower.contains("breakdown")

        val cleanCore = analysis.coreTitle.lowercase(Locale.ROOT).trim()
        val coreTokens = cleanCore
            .split(Regex("""[^a-zA-Z0-9]+"""))
            .filter { it.length >= 3 && it !in STOP_WORDS }

        val matchesCoreEntity = when {
            cleanCore.length >= 4 && (candTitleLower.contains(cleanCore) || candDescLower.contains(cleanCore)) -> true
            coreTokens.isEmpty() -> candTitleLower.contains(cleanCore)
            coreTokens.size == 1 -> candTitleLower.contains(coreTokens.first()) || candDescLower.contains(coreTokens.first())
            else -> {
                val matchCount = coreTokens.count { candTitleLower.contains(it) || candDescLower.contains(it) }
                matchCount >= 2 || (matchCount.toFloat() / coreTokens.size) >= 0.6f
            }
        }

        when (analysis.type) {
            ClipType.REACTION_PART -> {
                val currentPart = analysis.secondaryEntity?.toIntOrNull() ?: 1
                val candMeta = com.example.util.ReactionHelper.extractReactionMetadata(candidate)
                val sameCreator = !activeVideo.uploaderName.isNullOrBlank() &&
                        candidate.uploaderName.equals(activeVideo.uploaderName, ignoreCase = true)

                if (candMeta.isReaction && matchesCoreEntity) {
                    if (candMeta.partNumber == currentPart + 1) {
                        val creatorNote = if (sameCreator) " by ${candidate.uploaderName}" else ""
                        return Pair(true, "🍿 Next Part • Reaction Part ${currentPart + 1}$creatorNote")
                    } else if (candMeta.isFullUncut) {
                        return Pair(true, "🍿 Full Uncut Reaction")
                    }
                }

                // Or recommend the original complete movie / match
                if (!isCandClipOrTrailer && !candMeta.isReaction && matchesCoreEntity) {
                    if (candDuration >= 2400 || candTitleLower.contains("full movie")) {
                        return Pair(true, "🎬 Full Movie • Original Film")
                    }
                }
            }

            ClipType.MOVIE_CLIP -> {
                if (isCandClipOrTrailer || candTitleLower.contains("reaction")) return Pair(false, null)
                val hasFullMovieKeyword = candTitleLower.contains("full movie") ||
                        candTitleLower.contains("complete movie") ||
                        candTitleLower.contains("pelicula completa") ||
                        candTitleLower.contains("ganzer film") ||
                        candTitleLower.contains("film complet") ||
                        candTitleLower.contains("full film") ||
                        candTitleLower.contains("movie full")

                val isFeatureLength = candDuration >= 2400 // 40+ minutes

                if ((hasFullMovieKeyword || isFeatureLength) && matchesCoreEntity) {
                    return Pair(true, "🎬 Full Movie • Complete Film")
                }
            }

            ClipType.MATCH_HIGHLIGHT -> {
                if (candTitleLower.contains("highlight") || candTitleLower.contains("reaction")) return Pair(false, null)
                val hasFullMatchKeyword = candTitleLower.contains("full match") ||
                        candTitleLower.contains("full game") ||
                        candTitleLower.contains("match replay") ||
                        candTitleLower.contains("full replay") ||
                        candTitleLower.contains("complete match") ||
                        candTitleLower.contains("full fight") ||
                        candTitleLower.contains("entire match")

                val isMatchLength = candDuration >= 1800 // 30+ minutes

                if ((hasFullMatchKeyword || isMatchLength) && matchesCoreEntity) {
                    return Pair(true, "⚽ Full Match • Complete Game Replay")
                }
            }

            ClipType.SERIES_CLIP -> {
                if (candTitleLower.contains("clip") || candTitleLower.contains("reaction")) return Pair(false, null)
                val hasFullEpKeyword = candTitleLower.contains("full episode") ||
                        candTitleLower.contains("complete episode") ||
                        (candTitleLower.contains("episode") && candDuration >= 1000)

                val matchesEp = analysis.secondaryEntity?.let { candTitleLower.contains(it.lowercase(Locale.ROOT)) } ?: true

                if ((hasFullEpKeyword || candDuration >= 1000) && matchesCoreEntity && matchesEp) {
                    return Pair(true, "📺 Full Episode • Complete Episode")
                }
            }

            ClipType.GENERAL_CLIP -> {
                if (isCandClipOrTrailer) return Pair(false, null)
                if ((candTitleLower.contains("full") || candDuration >= 900) && matchesCoreEntity) {
                    return Pair(true, "▶️ Full Video • Complete Version")
                }
            }

            ClipType.NOT_A_CLIP -> {}
        }

        return Pair(false, null)
    }
}
