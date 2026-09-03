package com.example.recommendation

import android.util.Log
import com.example.model.VideoItem
import com.example.util.SmartTagExtractor
import java.util.Calendar
import java.util.Locale

/**
 * Intelligent Deep Recommendation & Taste Engine for Butterfly.
 * 
 * Features:
 * - Multi-Signal User Preference Learning (Likes, Dislikes, Watch Progress, Dwell Time)
 * - Channel & Creator Affinity Boosting (Promotes channels user likes or re-watches)
 * - Language Intelligence & Preference Promotion (English, Hindi, Japanese, International)
 * - Search Intent & Token N-Gram Matching
 * - Circadian Time-of-Day Contextual Adaptation
 * - Negative Signal Filtering (Early abandonments, Not Interested, Dislikes)
 * - Human-Readable Intelligent Recommendation Badges & Explanations
 */
object SmartRecommendationEngine {

    private const val TAG = "SmartRecommendationEngine"

    enum class ContentLanguage(val code: String, val displayName: String, val emoji: String) {
        ENGLISH("en", "English", "🇬🇧"),
        HINDI("hi", "Hindi", "🇮🇳"),
        JAPANESE("ja", "Japanese", "🇯🇵"),
        OTHER("other", "International", "🌐")
    }

    data class TasteVector(
        val categoryScores: Map<String, Float> = emptyMap(),
        val channelScores: Map<String, Float> = emptyMap(),
        val languageScores: Map<String, Float> = emptyMap(),
        val searchIntentTerms: List<String> = emptyList(),
        val searchTokens: Set<String> = emptySet(),
        val channelWatchCounts: Map<String, Int> = emptyMap(),
        val favoriteChannels: List<String> = emptyList(),
        val totalInteractions: Int = 0
    )

    data class ScoredVideo(
        val video: VideoItem,
        val score: Float,
        val explanation: String
    )

    /**
     * Detects language of a video item based on script, title keywords, creator name, and tags.
     */
    fun detectLanguage(video: VideoItem): ContentLanguage {
        val title = video.title ?: ""
        val uploader = video.uploaderName ?: ""
        val desc = video.description ?: ""
        val fullText = "$title $uploader $desc ${video.tags.joinToString(" ")}".lowercase(Locale.ROOT)

        // 1. Devanagari script check for Hindi
        val hasDevanagari = title.any { it in '\u0900'..'\u097F' } || uploader.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return ContentLanguage.HINDI

        // 2. Japanese Hiragana/Katakana/Kanji script check
        val hasJapanese = title.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' || it in '\u4E00'..'\u9FAF' } ||
                uploader.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' || it in '\u4E00'..'\u9FAF' }
        if (hasJapanese) return ContentLanguage.JAPANESE

        // 3. Hindi Keywords & Studios
        val hindiKeywords = listOf(
            "hindi", "bollywood", "dubbed in hindi", "hindi dubbed", "t-series", "tseries",
            "zee music", "goldmines", "shemaroo", "starplus", "sab tv", "sony liv", "voot",
            "hotstar", "aaj tak", "colors tv", "yash raj", "dharma", "bhansali", "south hindi",
            "filmy", "geet", "gaana", "b4u", "ultra movie", "desi", "hindi song", "hindi movie"
        )
        if (hindiKeywords.any { fullText.contains(it) }) return ContentLanguage.HINDI

        // 4. Japanese Keywords & Anime Studios
        val japaneseKeywords = listOf(
            "japanese", "anime", "subbed", "english sub", "raw", "pv ", "jp ", "toei",
            "mappa", "ghibli", "kadokawa", "crunchyroll", "aniplex", "toho", "madhouse",
            "kyoto animation", "shonen", "manga", "seiyuu", "j-pop", "voiceworks",
            "otaku", "demonslayer", "jujutsu", "naruto", "one piece", "dragon ball",
            "frieren", "solo leveling", "attack on titan", "my hero academia", "bleach"
        )
        if (japaneseKeywords.any { fullText.contains(it) }) return ContentLanguage.JAPANESE

        // 5. English Keywords
        if (fullText.contains("english") || fullText.contains("hollywood") || fullText.contains("official") || uploader.isNotBlank()) {
            return ContentLanguage.ENGLISH
        }

        return ContentLanguage.OTHER
    }

    /**
     * Compute dynamic multi-signal user taste vector based on:
     * - Watch History, Spend Time & Completion Ratios (>=75% = high intent, <15% = early abandonment)
     * - Recent Searches & Search Intent Tokens (+12.0 to +18.0 weight)
     * - Liked Videos (+30.0 channel boost, +10.0 category & language weight)
     * - Disliked Videos (-20.0 channel penalty, -10.0 category weight)
     * - Watch Later / Bookmarks (+5.0 weight)
     * - Creator / Channel Affinity & Favorites
     * - Preferred Content Languages (English, Hindi, Japanese)
     */
    fun computeTasteVector(
        watchHistory: List<VideoItem>,
        watchProgressMap: Map<String, Float>,
        likedVideoIds: Set<String>,
        dislikedVideoIds: Set<String>,
        bookmarks: List<VideoItem>,
        notInterestedChannels: Set<String>,
        recentSearches: List<String> = emptyList(),
        watchPositionMsMap: Map<String, Long> = emptyMap(),
        userPlaylists: List<com.example.model.UserPlaylist> = emptyList(),
        candidatePool: List<VideoItem> = emptyList()
    ): TasteVector {
        val catScores = mutableMapOf<String, Float>()
        val chanScores = mutableMapOf<String, Float>()
        val langScores = mutableMapOf<String, Float>()
        val chanWatchCounts = mutableMapOf<String, Int>()
        val searchTokens = mutableSetOf<String>()
        val cleanSearchTerms = mutableListOf<String>()
        var interactions = 0

        // Baseline Language Preferences: Promote English, Hindi, Japanese out of the box
        langScores["en"] = 8.0f
        langScores["hi"] = 14.0f
        langScores["ja"] = 14.0f
        langScores["other"] = 4.0f

        // 1. Process and Infer Intent from Recent Searches
        val stopWords = setOf(
            "the", "and", "for", "with", "from", "this", "that", "what", "how", "why",
            "full", "movie", "video", "official", "trailer", "episode", "season", "watch",
            "online", "free", "download", "stream", "hindi", "english", "dubbed", "dual"
        )

        for ((index, rawQuery) in recentSearches.take(12).withIndex()) {
            val q = rawQuery.trim()
            if (q.isBlank()) continue
            cleanSearchTerms.add(q)
            interactions++

            val recencyWeight = (16.0f - (index * 1.2f)).coerceAtLeast(4.0f)
            val qLower = q.lowercase(Locale.ROOT)
            val tokens = qLower.split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 2 && it !in stopWords }
            searchTokens.addAll(tokens)

            // Language inference from search
            when {
                qLower.contains("hindi") || qLower.contains("bollywood") || qLower.contains("t-series") || qLower.contains("tseries") -> {
                    langScores["hi"] = (langScores["hi"] ?: 0f) + recencyWeight
                }
                qLower.contains("anime") || qLower.contains("japanese") || qLower.contains("manga") || qLower.contains("subbed") -> {
                    langScores["ja"] = (langScores["ja"] ?: 0f) + recencyWeight
                }
                qLower.contains("english") || qLower.contains("hollywood") -> {
                    langScores["en"] = (langScores["en"] ?: 0f) + recencyWeight
                }
            }

            // Category inference from search query
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
                qLower.contains("porn") || qLower.contains("xxx") || qLower.contains("hentai") ||
                qLower.contains("erotic") || qLower.contains("nsfw") -> {
                    catScores["nsfw_adult"] = (catScores["nsfw_adult"] ?: 0f) + recencyWeight
                }
            }
        }

        // 2. Evaluate Watch History & Spend Time / Dwell Duration
        for (video in watchHistory.take(60)) {
            interactions++
            val tags = SmartTagExtractor.extractInternalCategoryTags(video)
            val prog = watchProgressMap[video.id] ?: 0.5f
            val posMs = watchPositionMsMap[video.id] ?: 0L
            val lang = detectLanguage(video)

            val weightMultiplier = when {
                prog >= 0.75f || posMs >= 180_000L -> 6.0f // Heavy watch time/completion -> High intent
                prog >= 0.35f || posMs >= 45_000L -> 3.5f
                prog >= 0.15f -> 1.0f
                else -> -3.5f // Early abandonment / skipped quickly -> Decay penalty
            }

            for (tag in tags) {
                catScores[tag.category] = (catScores[tag.category] ?: 0f) + weightMultiplier
            }

            val channel = video.uploaderName.lowercase(Locale.ROOT).trim()
            if (channel.isNotBlank()) {
                chanScores[channel] = (chanScores[channel] ?: 0f) + weightMultiplier
                chanWatchCounts[channel] = (chanWatchCounts[channel] ?: 0) + 1
            }

            langScores[lang.code] = (langScores[lang.code] ?: 0f) + weightMultiplier
        }

        val allKnownVideos = (watchHistory + bookmarks + userPlaylists.flatMap { it.videos } + candidatePool).distinctBy { it.id }

        // 3. Evaluate Liked Videos (Massive Channel & Category Boost)
        for (likedId in likedVideoIds) {
            interactions++
            val matchingVideo = allKnownVideos.firstOrNull { it.id == likedId }

            if (matchingVideo != null) {
                val tags = SmartTagExtractor.extractInternalCategoryTags(matchingVideo)
                val lang = detectLanguage(matchingVideo)
                for (tag in tags) {
                    catScores[tag.category] = (catScores[tag.category] ?: 0f) + 12.0f
                }
                val ch = matchingVideo.uploaderName.lowercase(Locale.ROOT).trim()
                if (ch.isNotBlank()) {
                    chanScores[ch] = (chanScores[ch] ?: 0f) + 30.0f
                }
                langScores[lang.code] = (langScores[lang.code] ?: 0f) + 12.0f
            }
        }

        // 4. Evaluate Disliked Videos (Penalize Channel & Category)
        for (dislikedId in dislikedVideoIds) {
            interactions++
            val matchingVideo = allKnownVideos.firstOrNull { it.id == dislikedId }
            if (matchingVideo != null) {
                val tags = SmartTagExtractor.extractInternalCategoryTags(matchingVideo)
                val lang = detectLanguage(matchingVideo)
                for (tag in tags) {
                    catScores[tag.category] = (catScores[tag.category] ?: 0f) - 15.0f
                }
                val ch = matchingVideo.uploaderName.lowercase(Locale.ROOT).trim()
                if (ch.isNotBlank()) {
                    chanScores[ch] = (chanScores[ch] ?: 0f) - 25.0f
                }
                langScores[lang.code] = (langScores[lang.code] ?: 0f) - 6.0f
            }
        }

        // 5. Evaluate Bookmarks / Watch Later
        for (bm in bookmarks.take(30)) {
            interactions++
            val tags = SmartTagExtractor.extractInternalCategoryTags(bm)
            val lang = detectLanguage(bm)
            for (tag in tags) {
                catScores[tag.category] = (catScores[tag.category] ?: 0f) + 5.0f
            }
            val ch = bm.uploaderName.lowercase(Locale.ROOT).trim()
            if (ch.isNotBlank()) {
                chanScores[ch] = (chanScores[ch] ?: 0f) + 6.0f
            }
            langScores[lang.code] = (langScores[lang.code] ?: 0f) + 5.0f
        }

        // 5b. Evaluate User Playlists (High-Intent Curated Collections)
        for (playlist in userPlaylists) {
            val plTitleLower = playlist.title.lowercase(Locale.ROOT)
            for (plVideo in playlist.videos) {
                interactions++
                val tags = SmartTagExtractor.extractInternalCategoryTags(plVideo)
                for (tag in tags) {
                    catScores[tag.category] = (catScores[tag.category] ?: 0f) + 8.0f
                }
                val ch = plVideo.uploaderName.lowercase(Locale.ROOT).trim()
                if (ch.isNotBlank()) {
                    chanScores[ch] = (chanScores[ch] ?: 0f) + 10.0f
                }
            }
            when {
                plTitleLower.contains("anime") -> catScores["anime"] = (catScores["anime"] ?: 0f) + 15.0f
                plTitleLower.contains("movie") || plTitleLower.contains("film") -> catScores["movie"] = (catScores["movie"] ?: 0f) + 15.0f
                plTitleLower.contains("music") || plTitleLower.contains("song") -> catScores["music"] = (catScores["music"] ?: 0f) + 15.0f
                plTitleLower.contains("game") || plTitleLower.contains("gaming") -> catScores["gaming"] = (catScores["gaming"] ?: 0f) + 15.0f
                plTitleLower.contains("tech") || plTitleLower.contains("code") -> catScores["tech"] = (catScores["tech"] ?: 0f) + 15.0f
                plTitleLower.contains("porn") || plTitleLower.contains("18+") || plTitleLower.contains("nsfw") -> catScores["nsfw_adult"] = (catScores["nsfw_adult"] ?: 0f) + 15.0f
            }
        }

        // 6. Heavy Exclusion Penalty for Not Interested / Blocked Channels
        for (blockedChan in notInterestedChannels) {
            val cleanCh = blockedChan.lowercase(Locale.ROOT).trim()
            if (cleanCh.isNotBlank()) {
                chanScores[cleanCh] = -100.0f
            }
        }

        // Extract Favorite Channels (Channels with high affinity score)
        val favChannels = chanScores.entries
            .filter { it.value >= 18.0f }
            .sortedByDescending { it.value }
            .map { it.key }

        return TasteVector(
            categoryScores = catScores,
            channelScores = chanScores,
            languageScores = langScores,
            searchIntentTerms = cleanSearchTerms,
            searchTokens = searchTokens,
            channelWatchCounts = chanWatchCounts,
            favoriteChannels = favChannels,
            totalInteractions = interactions
        )
    }

    /**
     * Score a single candidate video using dynamic AI multi-signal weighting:
     * - Creator / Channel Affinity (Boosts channels user likes)
     * - Content Language Alignment (Promotes English, Hindi, Japanese)
     * - Search Intent & Keyword Relevance Matching
     * - Category Alignment
     * - Time-of-Day Circadian Context
     * - Contextual Match (Active Video Player)
     */
    fun scoreVideo(
        video: VideoItem,
        tasteVector: TasteVector,
        activeVideo: VideoItem? = null,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): ScoredVideo {
        var score = 10.0f
        val tags = SmartTagExtractor.extractInternalCategoryTags(video)
        val channel = video.uploaderName.lowercase(Locale.ROOT).trim()
        val titleLower = (video.title ?: "").lowercase(Locale.ROOT).trim()
        val descLower = (video.description ?: "").lowercase(Locale.ROOT)
        val lang = detectLanguage(video)

        // A. Category Alignment
        for (tag in tags) {
            val catW = tasteVector.categoryScores[tag.category] ?: 0f
            score += catW * 2.5f
        }

        // B. Channel Affinity & Creator Promotion (Boost source channels user loves!)
        if (channel.isNotBlank()) {
            val chanW = tasteVector.channelScores[channel] ?: 0f
            score += chanW * 4.5f

            // Bonus if channel is in user's top favorite channels!
            if (tasteVector.favoriteChannels.contains(channel)) {
                score += 25.0f
            }
        }

        // C. Content Language Promotion (English, Hindi, Japanese)
        val langW = tasteVector.languageScores[lang.code] ?: 0f
        score += langW * 3.0f
        if (lang == ContentLanguage.HINDI || lang == ContentLanguage.JAPANESE || lang == ContentLanguage.ENGLISH) {
            score += 12.0f
        }

        // D. Direct Search Intent & Keyword Relevance Matching
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
                score += tokenHits * 16.0f
            }
        }

        // E. Exact Search Phrase Match Bonus
        var matchedSearchTerm: String? = null
        for (searchTerm in tasteVector.searchIntentTerms) {
            val termLower = searchTerm.lowercase(Locale.ROOT)
            if (termLower.length >= 4 && (titleLower.contains(termLower) || channel.contains(termLower))) {
                score += 38.0f
                matchedSearchTerm = searchTerm
                break
            }
        }

        // F. Circadian Time-of-Day Contextual Learning
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
                    if (cat in listOf("Movie Trailer", "Movie", "Video Essay", "Philosophy", "Anime", "Cinema", "movie", "movie_trailer", "anime", "series")) score += 6.0f
                }
                else -> { // Late night 0..5 AM
                    if (cat in listOf("Video Essay", "Philosophy", "Music", "Movie", "Podcast", "music", "podcast", "video_essay", "nsfw_adult", "asmr", "lofi")) score += 4.5f
                }
            }
        }

        // G. Contextual Player Match (Active Video Player)
        if (activeVideo != null) {
            val activeTags = SmartTagExtractor.extractInternalCategoryTags(activeVideo).map { it.category }.toSet()
            val candidateTags = tags.map { it.category }.toSet()
            val common = activeTags.intersect(candidateTags)
            score += common.size * 10.0f

            val activeChannel = activeVideo.uploaderName.lowercase(Locale.ROOT).trim()
            if (activeChannel.isNotBlank() && activeChannel == channel) {
                score += 20.0f // Promote more videos from same creator/channel!
            }

            val activeLang = detectLanguage(activeVideo)
            if (activeLang == lang) {
                score += 8.0f
            }
        }

        // Build Intelligent Explanation Badge
        val explanation = when {
            channel.isNotBlank() && tasteVector.favoriteChannels.contains(channel) ->
                "🌟 Promoted from ${video.uploaderName}"
            matchedSearchTerm != null ->
                "🎯 Matches search '$matchedSearchTerm'"
            lang == ContentLanguage.HINDI && langW > 10f ->
                "🇮🇳 Recommended Hindi Release"
            lang == ContentLanguage.JAPANESE && langW > 10f ->
                "🎌 Recommended Japanese Selection"
            activeVideo != null && activeVideo.uploaderName.lowercase(Locale.ROOT).trim() == channel ->
                "📺 More from ${video.uploaderName}"
            tags.isNotEmpty() && (tasteVector.categoryScores[tags.first().category] ?: 0f) > 8f ->
                "🎬 Top pick in ${tags.first().displayName}"
            else ->
                "✨ Recommended For You"
        }

        return ScoredVideo(video, score, explanation)
    }

    /**
     * Ranks candidate videos using AI multi-signal scoring, creator promotion,
     * channel diversity caps, and blockage filtering.
     */
    fun rankCandidateVideos(
        candidates: List<VideoItem>,
        tasteVector: TasteVector,
        activeVideo: VideoItem? = null,
        blockedVideoIds: Set<String> = emptySet(),
        blockedChannels: Set<String> = emptySet(),
        maxChannelLimit: Int = 3
    ): List<VideoItem> {
        if (candidates.isEmpty()) return emptyList()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Filter out blocked items
        val validCandidates = candidates
            .distinctBy { (it.providerId ?: "gen") + "_" + it.id }
            .filterNot { video ->
                val vid = video.id.trim()
                val ch = video.uploaderName?.lowercase(Locale.ROOT)?.trim() ?: ""
                blockedVideoIds.contains(vid) || (ch.isNotEmpty() && blockedChannels.contains(ch))
            }

        if (validCandidates.isEmpty()) return emptyList()

        // Score all valid candidates
        val scoredList = validCandidates.map { video ->
            scoreVideo(video, tasteVector, activeVideo, hour)
        }.sortedByDescending { it.score }

        // Apply Channel Diversity Cap while allowing user's favorite channels to show up to maxChannelLimit times
        val channelCounts = mutableMapOf<String, Int>()
        val result = mutableListOf<VideoItem>()

        for (scored in scoredList) {
            val video = scored.video
            val ch = video.uploaderName?.lowercase(Locale.ROOT)?.trim() ?: "unknown"
            val count = channelCounts[ch] ?: 0
            val limit = if (tasteVector.favoriteChannels.contains(ch)) maxChannelLimit + 1 else maxChannelLimit

            if (count < limit) {
                result.add(video)
                channelCounts[ch] = count + 1
            }
        }

        // Fill remaining if needed
        if (result.size < scoredList.size) {
            for (scored in scoredList) {
                if (result.none { it.id == scored.video.id }) {
                    result.add(scored.video)
                }
            }
        }

        return result
    }

    /**
     * Ranks candidate videos and returns ScoredVideo items with intelligent explanation badges.
     */
    fun rankCandidateVideosWithExplanations(
        candidates: List<VideoItem>,
        tasteVector: TasteVector,
        activeVideo: VideoItem? = null,
        blockedVideoIds: Set<String> = emptySet(),
        blockedChannels: Set<String> = emptySet(),
        maxChannelLimit: Int = 3
    ): List<ScoredVideo> {
        val ranked = rankCandidateVideos(candidates, tasteVector, activeVideo, blockedVideoIds, blockedChannels, maxChannelLimit)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return ranked.map { video ->
            scoreVideo(video, tasteVector, activeVideo, hour)
        }
    }

    /**
     * Extracts top promoted channels based on user taste vector.
     */
    fun getPromotedChannels(tasteVector: TasteVector, limit: Int = 5): List<String> {
        return tasteVector.favoriteChannels.take(limit)
    }

    data class TasteSummary(
        val topPositiveCategories: List<String>,
        val negativeCategories: List<String>,
        val topLanguages: List<String>,
        val favoriteChannels: List<String>,
        val topSearchTerms: List<String>
    )

    /**
     * Builds a human-readable intelligence profile summary from the user's taste vector.
     */
    fun buildTasteSummary(tasteVector: TasteVector): TasteSummary {
        val topCats = tasteVector.categoryScores.entries
            .filter { it.value > 4.0f }
            .sortedByDescending { it.value }
            .map { it.key.replace("_", " ").replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }
            .take(6)

        val negCats = tasteVector.categoryScores.entries
            .filter { it.value < -2.5f }
            .sortedBy { it.value }
            .map { it.key.replace("_", " ").replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }
            .take(6)

        val topLangs = tasteVector.languageScores.entries
            .filter { it.value > 5.0f }
            .sortedByDescending { it.value }
            .map { 
                when(it.key) {
                    "hi" -> "Hindi 🇮🇳"
                    "ja" -> "Japanese 🎌"
                    "en" -> "English 🇬🇧"
                    else -> "International 🌐"
                }
            }

        return TasteSummary(
            topPositiveCategories = topCats,
            negativeCategories = negCats,
            topLanguages = topLangs,
            favoriteChannels = tasteVector.favoriteChannels,
            topSearchTerms = tasteVector.searchIntentTerms.take(5)
        )
    }
}
