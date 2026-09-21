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
        CHINESE("zh", "Chinese", "🇨🇳"),
        KOREAN("ko", "Korean", "🇰🇷"),
        SPANISH("es", "Spanish", "🇪🇸"),
        FRENCH("fr", "French", "🇫🇷"),
        GERMAN("de", "German", "🇩🇪"),
        RUSSIAN("ru", "Russian", "🇷🇺"),
        ARABIC("ar", "Arabic", "🇸🇦"),
        OTHER("other", "International", "🌐")
    }

    data class TasteVector(
        val categoryScores: Map<String, Float> = emptyMap(),
        val channelScores: Map<String, Float> = emptyMap(),
        val languageScores: Map<String, Float> = emptyMap(),
        val providerScores: Map<String, Float> = emptyMap(),
        val hashtagScores: Map<String, Float> = emptyMap(),
        val searchIntentTerms: List<String> = emptyList(),
        val searchTokens: Set<String> = emptySet(),
        val channelWatchCounts: Map<String, Int> = emptyMap(),
        val favoriteChannels: List<String> = emptyList(),
        val totalInteractions: Int = 0,
        val dislikedVideoIds: Set<String> = emptySet(),
        val dislikedChannels: Set<String> = emptySet(),
        val dislikedKeywords: Set<String> = emptySet(),
        val dislikedCategories: Map<String, Int> = emptyMap(),
        val hourlyCategoryAffinities: Map<String, Float> = emptyMap(),
        val hourlyChannelAffinities: Map<String, Float> = emptyMap(),
        val hourlyProviderAffinities: Map<String, Float> = emptyMap(),
        val hourlyLanguageAffinities: Map<String, Float> = emptyMap(),
        val providerTimeSpent: Map<String, Long> = emptyMap()
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
        val meta = VideoKnowledgeExtractor.detectLanguageDetailed(title, desc, uploader, video.tags, video.providerId)
        return when (meta.code) {
            "zh" -> ContentLanguage.CHINESE
            "ja" -> ContentLanguage.JAPANESE
            "ko" -> ContentLanguage.KOREAN
            "hi" -> ContentLanguage.HINDI
            "es" -> ContentLanguage.SPANISH
            "fr" -> ContentLanguage.FRENCH
            "de" -> ContentLanguage.GERMAN
            "ru" -> ContentLanguage.RUSSIAN
            "ar" -> ContentLanguage.ARABIC
            "en" -> ContentLanguage.ENGLISH
            else -> ContentLanguage.OTHER
        }
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

        // Baseline Language Preferences: Promote Chinese, Japanese, Hindi, English out of the box
        langScores["en"] = 8.0f
        langScores["hi"] = 14.0f
        langScores["ja"] = 14.0f
        langScores["zh"] = 14.0f
        langScores["ko"] = 10.0f
        langScores["other"] = 4.0f

        val providerScores = mutableMapOf<String, Float>()
        val hashtagScores = mutableMapOf<String, Float>()

        // Seed with learned affinities from persistent memory
        for ((p, aff) in UserActivityMemory.getProviderAffinities()) {
            providerScores[p] = aff
        }
        for ((ht, aff) in UserActivityMemory.getHashtagAffinities()) {
            hashtagScores[ht] = aff
        }
        for ((l, aff) in UserActivityMemory.getLanguageAffinities()) {
            langScores[l] = (langScores[l] ?: 0f) + aff
        }
        for ((c, aff) in UserActivityMemory.getCategoryAffinities()) {
            catScores[c] = (catScores[c] ?: 0f) + aff
        }

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
            val tokens = qLower.split(Regex("[^\\p{L}0-9]+")).filter { it.length > 2 && it !in stopWords }
            searchTokens.addAll(tokens)

            // Language & Provider inference from search
            when {
                qLower.contains("chinese") || qLower.contains("mandarin") || qLower.contains("donghua") || qLower.contains("cdrama") || qLower.contains("c-drama") -> {
                    langScores["zh"] = (langScores["zh"] ?: 0f) + recencyWeight
                    catScores["chinese_drama_donghua"] = (catScores["chinese_drama_donghua"] ?: 0f) + recencyWeight
                    providerScores["tencent"] = (providerScores["tencent"] ?: 0f) + recencyWeight
                }
                qLower.contains("stripchat") || qLower.contains("chaturbate") || qLower.contains("webcam") || qLower.contains("cam4") -> {
                    catScores["live_cams_webcams"] = (catScores["live_cams_webcams"] ?: 0f) + recencyWeight
                    if (qLower.contains("stripchat")) providerScores["stripchat"] = (providerScores["stripchat"] ?: 0f) + recencyWeight
                    if (qLower.contains("chaturbate")) providerScores["chaturbate"] = (providerScores["chaturbate"] ?: 0f) + recencyWeight
                }
                qLower.contains("xnxx") || qLower.contains("hellporno") || qLower.contains("pornhub") || qLower.contains("xvideos") -> {
                    catScores["adult_hd_tube"] = (catScores["adult_hd_tube"] ?: 0f) + recencyWeight
                    if (qLower.contains("xnxx")) providerScores["xnxx"] = (providerScores["xnxx"] ?: 0f) + recencyWeight
                    if (qLower.contains("hellporno")) providerScores["hellporno"] = (providerScores["hellporno"] ?: 0f) + recencyWeight
                }
                qLower.contains("hindi") || qLower.contains("bollywood") || qLower.contains("t-series") || qLower.contains("tseries") -> {
                    langScores["hi"] = (langScores["hi"] ?: 0f) + recencyWeight
                }
                qLower.contains("anime") || qLower.contains("japanese") || qLower.contains("manga") || qLower.contains("subbed") -> {
                    langScores["ja"] = (langScores["ja"] ?: 0f) + recencyWeight
                }
                qLower.contains("korean") || qLower.contains("k-drama") || qLower.contains("kdrama") || qLower.contains("kpop") -> {
                    langScores["ko"] = (langScores["ko"] ?: 0f) + recencyWeight
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
            val knowledge = VideoKnowledgeExtractor.extractKnowledge(video)
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
            catScores[knowledge.primaryCategory] = (catScores[knowledge.primaryCategory] ?: 0f) + weightMultiplier

            val channel = video.uploaderName.lowercase(Locale.ROOT).trim()
            if (channel.isNotBlank()) {
                chanScores[channel] = (chanScores[channel] ?: 0f) + weightMultiplier
                chanWatchCounts[channel] = (chanWatchCounts[channel] ?: 0) + 1
            }

            val prov = knowledge.providerId
            if (prov.isNotBlank()) {
                providerScores[prov] = (providerScores[prov] ?: 0f) + weightMultiplier
            }

            for (ht in knowledge.hashtags) {
                hashtagScores[ht] = (hashtagScores[ht] ?: 0f) + (weightMultiplier * 0.8f)
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
        val allDislikedVideoIds = (dislikedVideoIds + UserActivityMemory.getDislikedVideoIds()).toSet()
        val allDislikedChannels = (notInterestedChannels + UserActivityMemory.getDislikedChannels()).map { it.lowercase(Locale.ROOT).trim() }.toSet()
        val dislikedKeywords = UserActivityMemory.getDislikedKeywords()
        val dislikedCategories = UserActivityMemory.getDislikedCategories()

        for (dislikedId in allDislikedVideoIds) {
            interactions++
            val matchingVideo = allKnownVideos.firstOrNull { it.id == dislikedId }
            if (matchingVideo != null) {
                val tags = SmartTagExtractor.extractInternalCategoryTags(matchingVideo)
                val lang = detectLanguage(matchingVideo)
                for (tag in tags) {
                    catScores[tag.category] = (catScores[tag.category] ?: 0f) - 20.0f
                }
                val ch = matchingVideo.uploaderName.lowercase(Locale.ROOT).trim()
                if (ch.isNotBlank()) {
                    chanScores[ch] = (chanScores[ch] ?: 0f) - 40.0f
                }
                langScores[lang.code] = (langScores[lang.code] ?: 0f) - 8.0f
            }
        }

        // Apply disliked categories penalty from memory
        for ((cat, count) in dislikedCategories) {
            catScores[cat] = (catScores[cat] ?: 0f) - (count * 12.0f)
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

        // Extract Favorite Channels (Channels with high affinity score or from activity memory)
        val learnedFavChannels = UserActivityMemory.getFavoriteChannels()
        val favChannels = (chanScores.entries
            .filter { it.value >= 18.0f }
            .sortedByDescending { it.value }
            .map { it.key } + learnedFavChannels).distinct()

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hourlyCatAff = UserActivityMemory.getHourlyCategoryAffinity(currentHour)
        val hourlyChanAff = UserActivityMemory.getHourlyChannelAffinity(currentHour)
        val hourlyProvAff = UserActivityMemory.getHourlyProviderAffinity(currentHour)
        val hourlyLangAff = UserActivityMemory.getHourlyLanguageAffinity(currentHour)
        val provTimeSpent = UserActivityMemory.getProviderTimeSpentMap()

        return TasteVector(
            categoryScores = catScores,
            channelScores = chanScores,
            languageScores = langScores,
            providerScores = providerScores,
            hashtagScores = hashtagScores,
            searchIntentTerms = cleanSearchTerms,
            searchTokens = searchTokens,
            channelWatchCounts = chanWatchCounts,
            favoriteChannels = favChannels,
            totalInteractions = interactions,
            dislikedVideoIds = allDislikedVideoIds,
            dislikedChannels = allDislikedChannels,
            dislikedKeywords = dislikedKeywords,
            dislikedCategories = dislikedCategories,
            hourlyCategoryAffinities = hourlyCatAff,
            hourlyChannelAffinities = hourlyChanAff,
            hourlyProviderAffinities = hourlyProvAff,
            hourlyLanguageAffinities = hourlyLangAff,
            providerTimeSpent = provTimeSpent
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
        val vid = video.id.trim()
        val channel = video.uploaderName.lowercase(Locale.ROOT).trim()

        // 0. ABSOLUTE EXCLUSION: If user disliked this video or channel, drop immediately
        if (tasteVector.dislikedVideoIds.contains(vid) || UserActivityMemory.isDisliked(vid)) {
            return ScoredVideo(video, -9999.0f, "⛔ Disliked video")
        }
        if (channel.isNotBlank() && (tasteVector.dislikedChannels.contains(channel) || UserActivityMemory.getDislikedChannels().contains(channel))) {
            return ScoredVideo(video, -9999.0f, "⛔ Disliked creator")
        }

        var score = 10.0f
        val knowledge = VideoKnowledgeExtractor.extractKnowledge(video)
        val tags = SmartTagExtractor.extractInternalCategoryTags(video)
        val titleLower = (video.title ?: "").lowercase(Locale.ROOT).trim()
        val descLower = (video.description ?: "").lowercase(Locale.ROOT)
        val lang = detectLanguage(video)

        // Negative keyword suppression
        if (tasteVector.dislikedKeywords.isNotEmpty()) {
            for (negKw in tasteVector.dislikedKeywords) {
                if (titleLower.contains(negKw)) {
                    score -= 30.0f
                }
            }
        }

        // Negative category suppression
        for (tag in tags) {
            val negPenalty = tasteVector.dislikedCategories[tag.category] ?: 0
            if (negPenalty > 0) {
                score -= negPenalty * 15.0f
            }
        }

        // A. Category Alignment (Both standard and deep knowledge category)
        for (tag in tags) {
            val catW = tasteVector.categoryScores[tag.category] ?: 0f
            score += catW * 2.8f
        }
        val primaryCatW = tasteVector.categoryScores[knowledge.primaryCategory] ?: 0f
        score += primaryCatW * 2.5f

        // B. Channel Affinity & Creator Promotion (Boost source channels user loves!)
        if (channel.isNotBlank()) {
            val chanW = tasteVector.channelScores[channel] ?: 0f
            score += chanW * 4.5f

            // Bonus if channel is in user's top favorite channels!
            if (tasteVector.favoriteChannels.contains(channel)) {
                score += 30.0f
            }
        }

        // C. Provider / Source Affinity & Dwell Time Preference
        val prov = knowledge.providerId
        if (prov.isNotBlank()) {
            val provW = tasteVector.providerScores[prov] ?: 0f
            score += provW * 3.5f

            // Bonus if user has significant watch/dwell time on this provider
            val timeSpent = tasteVector.providerTimeSpent[prov] ?: 0L
            if (timeSpent > 300_000L) { // > 5 minutes of engagement
                score += 15.0f
            }
        }

        // D. Semantic Hashtag Intelligence (Internal deep tagging)
        for (ht in knowledge.hashtags) {
            val htW = tasteVector.hashtagScores[ht] ?: 0f
            if (htW > 0f) {
                score += (htW * 2.2f).coerceAtMost(30.0f)
            }
        }

        // E. Content Language Promotion (Chinese, Japanese, Hindi, English)
        val langW = tasteVector.languageScores[lang.code] ?: 0f
        score += langW * 3.0f
        if (lang == ContentLanguage.HINDI || lang == ContentLanguage.JAPANESE || lang == ContentLanguage.CHINESE || lang == ContentLanguage.ENGLISH) {
            score += 12.0f
        }

        // F. Direct Search Intent & Keyword Relevance Matching
        if (tasteVector.searchTokens.isNotEmpty()) {
            var tokenHits = 0
            for (token in tasteVector.searchTokens) {
                if (titleLower.contains(token)) {
                    tokenHits++
                } else if (channel.contains(token)) {
                    tokenHits++
                } else if (descLower.contains(token)) {
                    score += 4.0f
                }
            }
            if (tokenHits > 0) {
                score += tokenHits * 18.0f
            }
        }

        // G. Exact Search Phrase Match Bonus
        var matchedSearchTerm: String? = null
        for (searchTerm in tasteVector.searchIntentTerms) {
            val termLower = searchTerm.lowercase(Locale.ROOT)
            if (termLower.length >= 4 && (titleLower.contains(termLower) || channel.contains(termLower))) {
                score += 42.0f
                matchedSearchTerm = searchTerm
                break
            }
        }

        // H. Learned Circadian Time-of-Day Contextual Intelligence
        // 1) Dynamic learned preference at this specific hour of the day (Categories, Channels, Providers, Languages)
        var learnedHourHit = false
        for (tag in tags) {
            val learnedCatBoost = tasteVector.hourlyCategoryAffinities[tag.category] ?: 0f
            if (learnedCatBoost > 0f) {
                score += (learnedCatBoost * 6.0f).coerceAtMost(35.0f)
                learnedHourHit = true
            }
        }
        if (channel.isNotBlank()) {
            val learnedChanBoost = tasteVector.hourlyChannelAffinities[channel] ?: 0f
            if (learnedChanBoost > 0f) {
                score += (learnedChanBoost * 7.0f).coerceAtMost(40.0f)
                learnedHourHit = true
            }
        }
        if (prov.isNotBlank()) {
            val learnedProvBoost = tasteVector.hourlyProviderAffinities[prov] ?: 0f
            if (learnedProvBoost > 0f) {
                score += (learnedProvBoost * 5.0f).coerceAtMost(35.0f)
                learnedHourHit = true
            }
        }
        val learnedLangBoost = tasteVector.hourlyLanguageAffinities[lang.code] ?: 0f
        if (learnedLangBoost > 0f) {
            score += (learnedLangBoost * 4.0f).coerceAtMost(25.0f)
            learnedHourHit = true
        }

        // 2) Baseline circadian learning if no user history for this hour yet
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
                    if (cat in listOf("Movie Trailer", "Movie", "Video Essay", "Philosophy", "Anime", "Cinema", "movie", "movie_trailer", "anime", "series", "chinese_drama_donghua")) score += 6.0f
                }
                else -> { // Late night 0..5 AM
                    if (cat in listOf("Video Essay", "Philosophy", "Music", "Movie", "Podcast", "music", "podcast", "video_essay", "nsfw_adult", "adult_hd_tube", "live_cams_webcams", "asmr", "lofi")) score += 4.5f
                }
            }
        }

        // I. Contextual Player Match (Active Video Player)
        var isContextualRelated = false
        if (activeVideo != null) {
            val activeTags = SmartTagExtractor.extractInternalCategoryTags(activeVideo).map { it.category }.toSet()
            val candidateTags = tags.map { it.category }.toSet()
            val common = activeTags.intersect(candidateTags)
            if (common.isNotEmpty()) {
                score += common.size * 14.0f
                isContextualRelated = true
            }

            val activeChannel = activeVideo.uploaderName.lowercase(Locale.ROOT).trim()
            if (activeChannel.isNotBlank() && activeChannel == channel) {
                score += 26.0f // Promote more videos from same creator/channel!
                isContextualRelated = true
            }

            // Keyword overlap between current playing video title and candidate
            val activeKeywords = activeVideo.title.lowercase(Locale.ROOT)
                .split(Regex("[^\\p{L}0-9]+"))
                .filter { it.length >= 4 && it !in setOf("video", "official", "trailer", "full", "movie", "part", "hindi", "english") }
            var kwHits = 0
            for (kw in activeKeywords) {
                if (titleLower.contains(kw)) kwHits++
            }
            if (kwHits > 0) {
                score += kwHits * 20.0f
                isContextualRelated = true
            }

            val activeLang = detectLanguage(activeVideo)
            if (activeLang == lang) {
                score += 8.0f
            }

            if (activeVideo.providerId != null && activeVideo.providerId == video.providerId) {
                score += 10.0f
            }
        }

        // Build Intelligent Explanation Badge
        val timeLabel = when (hourOfDay) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..21 -> "Evening"
            else -> "Night"
        }
        val explanation = when {
            channel.isNotBlank() && tasteVector.favoriteChannels.contains(channel) ->
                "❤️ From your favorite creator ${video.uploaderName}"
            activeVideo != null && activeVideo.uploaderName.lowercase(Locale.ROOT).trim() == channel ->
                "📺 More from ${video.uploaderName}"
            activeVideo != null && isContextualRelated ->
                "🎯 Related to what you're watching"
            matchedSearchTerm != null ->
                "🔍 Matches search '$matchedSearchTerm'"
            learnedHourHit && tags.isNotEmpty() ->
                "🕒 Your $timeLabel pick (${tags.first().displayName})"
            lang == ContentLanguage.CHINESE && langW > 10f ->
                "🇨🇳 Top Chinese pick for you"
            lang == ContentLanguage.HINDI && langW > 10f ->
                "🇮🇳 Top Hindi pick for you"
            lang == ContentLanguage.JAPANESE && langW > 10f ->
                "🎌 Top Japanese release"
            tags.isNotEmpty() && (tasteVector.categoryScores[tags.first().category] ?: 0f) > 8f ->
                "🎬 Top pick in ${tags.first().displayName}"
            else ->
                "✨ Recommended for you"
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

        // Filter out blocked items and ALL disliked items
        val validCandidates = candidates
            .distinctBy { (it.providerId ?: "gen") + "_" + it.id }
            .filterNot { video ->
                val vid = video.id.trim()
                val ch = video.uploaderName?.lowercase(Locale.ROOT)?.trim() ?: ""
                blockedVideoIds.contains(vid) ||
                tasteVector.dislikedVideoIds.contains(vid) ||
                UserActivityMemory.isDisliked(vid) ||
                (ch.isNotEmpty() && (blockedChannels.contains(ch) || tasteVector.dislikedChannels.contains(ch) || UserActivityMemory.getDislikedChannels().contains(ch)))
            }

        if (validCandidates.isEmpty()) return emptyList()

        // Score all valid candidates
        val scoredList = validCandidates.map { video ->
            scoreVideo(video, tasteVector, activeVideo, hour)
        }.filter { it.score > -100f }
         .sortedByDescending { it.score }

        // Apply Channel Diversity Cap while allowing user's favorite channels to show up to maxChannelLimit times
        val channelCounts = mutableMapOf<String, Int>()
        val result = mutableListOf<VideoItem>()

        for (scored in scoredList) {
            val video = scored.video.copy(recommendationReason = scored.explanation)
            val ch = video.uploaderName?.lowercase(Locale.ROOT)?.trim() ?: "unknown"
            val count = channelCounts[ch] ?: 0
            val limit = if (tasteVector.favoriteChannels.contains(ch)) maxChannelLimit + 2 else maxChannelLimit

            if (count < limit) {
                result.add(video)
                channelCounts[ch] = count + 1
            }
        }

        // Fill remaining if needed
        if (result.size < scoredList.size) {
            for (scored in scoredList) {
                if (result.none { it.id == scored.video.id }) {
                    result.add(scored.video.copy(recommendationReason = scored.explanation))
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
