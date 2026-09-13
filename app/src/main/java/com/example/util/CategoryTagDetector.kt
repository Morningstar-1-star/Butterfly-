package com.example.util

import java.net.URI
import java.util.Locale

object CategoryTagDetector {

    data class TagAnalysisResult(
        val isUrl: Boolean,
        val detectedTags: List<String>,
        val cleanTitle: String,
        val originalQuery: String,
        val detectedProviderId: String? = null,
        val isAdultTagDetected: Boolean = false
    )

    private val URL_REGEX = Regex(
        "(?i)^(https?://|www\\.|youtu\\.be/|dailymotion\\.com/|dai\\.ly/|pornhub\\.com/|xvideos\\.com/|bunkr\\.|vimeo\\.com/|twitch\\.tv/|bilibili\\.com/|b23\\.tv/)[^\\s]+"
    )

    private val ADULT_KEYWORDS = listOf(
        "milf", "hentai", "jav", "porn", "xxx", "nsfw", "18+", "erotic", "spankbang",
        "xvideos", "hqporner", "redtube", "xhamster", "youporn", "ecchi", "cam4",
        "chaturbate", "eporner", "tnaflix", "noodlemagazine", "thisvid", "playvid",
        "txxx", "3d hentai", "doujinshi", "uncensored"
    )

    private val ANIME_KEYWORDS = listOf(
        "anime", "manga", "otaku", "isekai", "shonen", "jikan", "crunchyroll",
        "one piece", "naruto", "demon slayer", "jujutsu kaisen", "solo leveling",
        "attack on titan", "bleach", "dragon ball"
    )

    private val GENRE_MAP = mapOf(
        "action" to "Action",
        "fantasy" to "Fantasy",
        "sci-fi" to "Sci-Fi",
        "scifi" to "Sci-Fi",
        "science fiction" to "Sci-Fi",
        "horror" to "Horror",
        "scary" to "Horror",
        "thriller" to "Thriller",
        "comedy" to "Comedy",
        "funny" to "Comedy",
        "romance" to "Romance",
        "romantic" to "Romance",
        "drama" to "Drama",
        "mystery" to "Mystery",
        "documentary" to "Documentary",
        "animation" to "Animation",
        "animated" to "Animation",
        "movie" to "Movies",
        "movies" to "Movies",
        "film" to "Movies",
        "tv show" to "TV Shows",
        "series" to "TV Shows",
        "season" to "TV Shows",
        "gaming" to "Gaming",
        "gameplay" to "Gaming",
        "music" to "Music",
        "song" to "Music",
        "concert" to "Music",
        "bollywood" to "Bollywood",
        "hollywood" to "Hollywood"
    )

    /**
     * Analyzes input query to detect video URLs, category tags, adult content keywords,
     * and provider hints.
     */
    fun analyzeQuery(rawQuery: String): TagAnalysisResult {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return TagAnalysisResult(
                isUrl = false,
                detectedTags = emptyList(),
                cleanTitle = "",
                originalQuery = rawQuery
            )
        }

        // 1. Check if URL
        val isUrl = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("www.", ignoreCase = true) ||
                URL_REGEX.containsMatchIn(trimmed)

        if (isUrl) {
            val providerId = detectProviderFromUrl(trimmed)
            val domainName = providerId?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } ?: "Direct Link"
            val tags = mutableListOf("Direct Link", domainName)
            if (providerId in listOf("eporner", "pornhub", "xvideos", "hqporner", "spankbang", "123av", "javtiful", "sextb")) {
                tags.add("18+ Adult")
            }
            return TagAnalysisResult(
                isUrl = true,
                detectedTags = tags.distinct(),
                cleanTitle = trimmed,
                originalQuery = trimmed,
                detectedProviderId = providerId,
                isAdultTagDetected = tags.contains("18+ Adult")
            )
        }

        // 2. Keyword & Tag Classification for Plain Text Queries
        val lower = trimmed.lowercase(Locale.ROOT)
        val tags = mutableListOf<String>()
        var isAdult = false

        // JAV code check
        val javCode = com.example.metadata.JavIdParser.parse(trimmed)
        if (javCode != null) {
            tags.add("18+ Adult")
            tags.add("JAV")
            isAdult = true
        }

        // Adult performer/model check
        val detectedModel = AdultModelMatcher.findModel(trimmed)
        if (detectedModel != null) {
            tags.add("18+ Adult")
            if (detectedModel.isJav) tags.add("JAV")
            tags.add("Model")
            isAdult = true
        }

        // Adult keyword check
        if (!isAdult) {
            for (kw in ADULT_KEYWORDS) {
                if (lower.contains(kw)) {
                    tags.add("18+ Adult")
                    if (kw == "hentai" || kw == "ecchi") tags.add("Anime")
                    isAdult = true
                    break
                }
            }
        }

        // Anime keyword check
        if (!tags.contains("Anime")) {
            for (kw in ANIME_KEYWORDS) {
                if (lower.contains(kw)) {
                    tags.add("Anime")
                    break
                }
            }
        }

        // Genre keyword map check
        GENRE_MAP.forEach { (kw, tagLabel) ->
            if (lower.contains(kw) && !tags.contains(tagLabel)) {
                tags.add(tagLabel)
            }
        }

        // Clean query: strip explicit category keywords if they leave subject matter intact
        var clean = trimmed
        if (tags.isNotEmpty() && clean.split(" ").size > 2) {
            ADULT_KEYWORDS.forEach { kw ->
                clean = clean.replace(Regex("(?i)\\b$kw\\b"), "").trim()
            }
            GENRE_MAP.keys.forEach { kw ->
                if (clean.split(" ").size > 2) {
                    clean = clean.replace(Regex("(?i)\\b$kw\\b"), "").trim()
                }
            }
        }
        clean = clean.replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) clean = trimmed

        return TagAnalysisResult(
            isUrl = false,
            detectedTags = tags.distinct(),
            cleanTitle = clean,
            originalQuery = trimmed,
            detectedProviderId = null,
            isAdultTagDetected = isAdult
        )
    }

    /**
     * Identifies provider from video URL hostname/path.
     */
    fun detectProviderFromUrl(url: String): String? {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            lower.contains("youtu.be") || lower.contains("youtube.com") -> "youtube"
            lower.contains("dailymotion.com") || lower.contains("dai.ly") -> "dailymotion"
            lower.contains("pornhub.com") -> "pornhub"
            lower.contains("xvideos.com") -> "xvideos"
            lower.contains("bunkr") -> "bunkr"
            lower.contains("vimeo.com") -> "vimeo"
            lower.contains("bilibili.com") || lower.contains("b23.tv") -> "bilibili"
            lower.contains("twitch.tv") -> "twitch"
            lower.contains("eporner.com") -> "eporner"
            lower.contains("archive.org") -> "archive_org"
            lower.contains("spankbang.com") -> "spankbang"
            lower.contains("xhamster.com") -> "xhamster"
            lower.contains("redtube.com") -> "redtube"
            lower.contains("youporn.com") -> "youporn"
            lower.contains("mega.nz") || lower.contains("mega.io") -> "mega"
            lower.contains("t.me") || lower.contains("telegram") -> "telegram"
            lower.contains("sextb.net") || lower.contains("sextb") || lower.contains("streamtb.me") -> "sextb"
            else -> "youtube"
        }
    }

    /**
     * Sanitizes resolved video title for cross-platform related search.
     */
    fun sanitizeTitleForRelatedSearch(title: String): String {
        var clean = title
        // Remove trailing noise like "- video", "(Official Video)", "[HD]", "| 1080p", etc.
        clean = clean.replace(Regex("(?i)[\\(\\[\\{].*?[\\)\\]\\}]"), " ")
        clean = clean.replace(Regex("(?i)\\b(official video|music video|full video|hd|1080p|4k|mv|video|dubbed|subbed)\\b"), " ")
        clean = clean.replace(Regex("[\\|\\-_:;\t\r\n]+"), " ")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return if (clean.length < 3) title else clean
    }
}
