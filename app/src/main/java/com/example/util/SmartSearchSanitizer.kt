package com.example.util

import java.util.Locale
import kotlin.math.min

object SmartSearchSanitizer {

    data class CleanQueryResult(
        val originalQuery: String,
        val cleanQuery: String,
        val wasCleaned: Boolean,
        val didYouMean: String? = null,
        val noiseDescription: String? = null
    )

    // Comprehensive dictionary of popular movie, TV show, anime, and media titles for fuzzy matching
    private val POPULAR_TITLES = listOf(
        // Sci-Fi & Blockbusters
        "Interstellar", "Inception", "Oppenheimer", "The Dark Knight", "Dune", "Dune Part Two",
        "Tenet", "The Matrix", "Avatar", "Avatar The Way of Water", "Gladiator", "Titanic",
        "Fight Club", "Pulp Fiction", "Forrest Gump", "The Godfather", "Shawshank Redemption",
        "Blade Runner 2049", "Jurassic Park", "Ready Player One", "Spiderman", "Spider-Man Into the Spider-Verse",
        "Spider-Man Across the Spider-Verse", "Spider-Man No Way Home", "Avengers Endgame",
        "Avengers Infinity War", "Iron Man", "Captain America", "Thor Ragnarok", "Guardians of the Galaxy",
        "Deadpool", "Deadpool & Wolverine", "Black Panther", "The Batman", "Joker",

        // TV Series
        "Outer Banks", "Stranger Things", "Breaking Bad", "Better Call Saul", "Game of Thrones",
        "House of the Dragon", "The Boys", "Squid Game", "Money Heist", "Wednesday", "Loki",
        "The Mandalorian", "The Witcher", "Peaky Blinders", "Sherlock", "Succession", "The Office",
        "Friends", "How I Met Your Mother", "The Big Bang Theory", "Dark", "Mindhunter",
        "Narcos", "Ozark", "The Crown", "True Detective", "Westworld", "Fargo", "Black Mirror",
        "Vikings", "Primal", "Succession", "Ted Lasso", "The Bear", "Shogun", "Fallout",

        // Anime & Animation
        "Arcane", "Cyberpunk Edgerunners", "Attack on Titan", "Demon Slayer", "Jujutsu Kaisen",
        "One Piece", "Naruto Shippuden", "Death Note", "Bleach", "Bleach Thousand-Year Blood War",
        "Dragon Ball Super", "Solo Leveling", "Fullmetal Alchemist Brotherhood", "Hunter x Hunter",
        "My Hero Academia", "Vinland Saga", "Chainsaw Man", "Spy x Family", "Tokyo Ghoul",
        "Steins Gate", "Monster", "Code Geass", "Evangelion", "Mob Psycho 100", "One Punch Man"
    )

    // Patterns for release noise tags
    private val NOISE_PATTERNS = listOf(
        // Download / Streaming keywords
        Regex("(?i)\\b(download|watch online|free download|torrent|direct link|gdrive|mega link|magnet)\\b"),
        // Release formats & Rip types
        Regex("(?i)\\b(web-dl|webrip|hdrip|bluray|brrip|camrip|hdcam|dvdrip|hdtv|bdrip|remux|v2)\\b"),
        // Audio & Subtitle details
        Regex("(?i)\\b(dual audio|multi audio|hindi dubbed|english dubbed|hindi-english|english-hindi|esub|softsub|hardsub|subbed|dubbed)\\b"),
        // Encodings & Specs
        Regex("(?i)\\b(x264|x265|hevc|10bit|8bit|aac|ac3|5\\.1|7\\.1|dts|flac|mkv|mp4|avi)\\b"),
        // Resolutions
        Regex("(?i)\\b(480p|720p|1080p|1080i|2160p|4k|uhd|hdr|fhd|hd)\\b"),
        // File sizes (e.g. 300MB, 1.3GB, 500MB)
        Regex("(?i)\\b(\\d+(?:\\.\\d+)?\\s*(?:mb|gb))\\b"),
        // Seasons / Episodes tags like Season 1-5, S01E02, S1-5, etc.
        Regex("(?i)\\b(season\\s*\\d+(?:-\\d+)?|seasons?\\s*\\d+|s\\d+e\\d+|s\\d+|e\\d+|ep\\d+|episode\\s*\\d+)\\b"),
        // Anything in brackets like [300MB], {English-Hindi}, (Season 1-5)
        Regex("[\\[\\{].*?[\\]\\}]"),
        // Pipe delimiters
        Regex("\\|\\|")
    )

    /**
     * Cleans raw input queries (strips release noise, torrent metadata, file sizes)
     * and performs fuzzy matching against known media titles to heal typos.
     */
    fun sanitizeQuery(rawQuery: String): CleanQueryResult {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return CleanQueryResult(rawQuery, rawQuery, false)
        }

        // If query starts with explicit extractor search prefixes (e.g. bilisearch:, bilisearch10:, ytsearch:), preserve as-is
        if (trimmed.startsWith("bilisearch", ignoreCase = true) ||
            trimmed.startsWith("ytsearch", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return CleanQueryResult(trimmed, trimmed, wasCleaned = false)
        }

        // Step 1: Strip Torrent & Technical Release Noise
        var cleaned = trimmed
        var strippedCount = 0

        for (pattern in NOISE_PATTERNS) {
            val before = cleaned
            cleaned = pattern.replace(cleaned, " ")
            if (cleaned != before) strippedCount++
        }

        // Strip remaining special symbols and multi-spaces
        cleaned = cleaned.replace(Regex("[\\|\\-_:;\t\r\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // If cleaning wiped out almost everything (e.g., if query was only "1080p"), fallback to raw without brackets
        if (cleaned.length < 2) {
            cleaned = trimmed.replace(Regex("[\\[\\]\\{\\}\\(\\)]"), "").trim()
        }

        val wasNoiseStripped = cleaned.lowercase(Locale.ROOT) != trimmed.lowercase(Locale.ROOT)

        // Step 2: Fuzzy Typo Matching
        val fuzzyMatch = findFuzzyMatch(cleaned)

        val didYouMean = if (fuzzyMatch != null && !fuzzyMatch.equals(cleaned, ignoreCase = true)) {
            fuzzyMatch
        } else null

        val finalCleanQuery = fuzzyMatch ?: cleaned

        val noiseDesc = if (wasNoiseStripped) {
            "Stripped release tags & technical info"
        } else if (didYouMean != null) {
            "Auto-corrected spelling"
        } else null

        return CleanQueryResult(
            originalQuery = trimmed,
            cleanQuery = finalCleanQuery,
            wasCleaned = wasNoiseStripped || (didYouMean != null),
            didYouMean = didYouMean,
            noiseDescription = noiseDesc
        )
    }

    /**
     * Finds fuzzy match for query in popular titles using Levenshtein distance and token similarity.
     */
    private fun findFuzzyMatch(query: String): String? {
        val q = query.lowercase(Locale.ROOT).trim()
        if (q.length < 3) return null

        // 1. Direct case-insensitive match
        for (title in POPULAR_TITLES) {
            if (title.equals(q, ignoreCase = true)) return title
        }

        // 2. Token / word similarity match
        val queryTokens = q.split(" ").filter { it.length > 1 }

        var bestMatch: String? = null
        var minDistance = Int.MAX_VALUE

        for (title in POPULAR_TITLES) {
            val tLower = title.lowercase(Locale.ROOT)

            // If title contains query or query contains full title
            if (tLower.contains(q) || (q.length > 5 && q.contains(tLower))) {
                return title
            }

            // Word-level fuzzy check (e.g., "intter steller" -> "interstellar")
            val distance = levenshteinDistance(q.replace(" ", ""), tLower.replace(" ", ""))

            // Max allowed edit distance based on string length
            val maxAllowedDistance = when {
                q.length <= 5 -> 2
                q.length <= 10 -> 3
                q.length <= 15 -> 4
                else -> 5
            }

            if (distance <= maxAllowedDistance && distance < minDistance) {
                minDistance = distance
                bestMatch = title
            }
        }

        return bestMatch
    }

    /**
     * Standard Levenshtein Distance implementation.
     */
    fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
