package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

object SmartSearchSanitizer {

    private const val TAG = "SmartSearchSanitizer"

    data class CleanQueryResult(
        val originalQuery: String,
        val cleanQuery: String,
        val wasCleaned: Boolean,
        val didYouMean: String? = null,
        val noiseDescription: String? = null
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1200, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .build()
    }

    // Comprehensive dictionary of popular movie, TV show, anime, music, and media titles for fuzzy matching
    private val POPULAR_TITLES = listOf(
        // Sci-Fi, Action & Blockbusters
        "Interstellar", "Inception", "Oppenheimer", "The Dark Knight", "The Dark Knight Rises", "Batman Begins",
        "Dune", "Dune Part Two", "Tenet", "The Matrix", "The Matrix Reloaded", "Avatar", "Avatar The Way of Water",
        "Gladiator", "Gladiator 2", "Titanic", "Fight Club", "Pulp Fiction", "Forrest Gump", "The Godfather",
        "Shawshank Redemption", "Blade Runner 2049", "Blade Runner", "Jurassic Park", "Jurassic World",
        "Ready Player One", "Spiderman", "Spider-Man", "Spider-Man Into the Spider-Verse",
        "Spider-Man Across the Spider-Verse", "Spider-Man No Way Home", "Spider-Man Far From Home", "Spider-Man Homecoming",
        "Avengers", "Avengers Endgame", "Avengers Infinity War", "Avengers Age of Ultron", "Iron Man", "Captain America",
        "Thor Ragnarok", "Thor Love and Thunder", "Guardians of the Galaxy", "Doctor Strange", "Doctor Strange in the Multiverse of Madness",
        "Deadpool", "Deadpool 2", "Deadpool & Wolverine", "Black Panther", "The Batman", "Joker", "Joker Folie a Deux",
        "John Wick", "John Wick Chapter 4", "Mission Impossible", "Mission Impossible Dead Reckoning", "Top Gun Maverick",
        "Mad Max Fury Road", "Furiosa A Mad Max Saga", "Everything Everywhere All at Once", "Alien Romulus",
        "Kingdom of the Planet of the Apes", "Planet of the Apes", "Transformers", "Godzilla x Kong", "Godzilla Minus One",
        "Star Wars", "Star Wars The Force Awakens", "Star Wars The Empire Strikes Back", "Star Wars Return of the Jedi",
        "Harry Potter", "Harry Potter and the Sorcerer's Stone", "Lord of the Rings", "The Hobbit",
        "Fast and Furious", "Fast X", "Oppenheimer", "Barbie", "Civil War", "Twisters", "Dune",

        // TV Series & Dramas
        "Outer Banks", "Stranger Things", "Breaking Bad", "Better Call Saul", "Game of Thrones",
        "House of the Dragon", "The Boys", "Squid Game", "Money Heist", "Wednesday", "Loki",
        "The Mandalorian", "The Witcher", "Peaky Blinders", "Sherlock", "Succession", "The Office",
        "Friends", "How I Met Your Mother", "The Big Bang Theory", "Dark", "Mindhunter",
        "Narcos", "Ozark", "The Crown", "True Detective", "Westworld", "Fargo", "Black Mirror",
        "Vikings", "Primal", "Ted Lasso", "The Bear", "Shogun", "Fallout", "The Last of Us",
        "Severance", "Silo", "Reacher", "Slow Horses", "Presumed Innocent", "The Penguin", "Invincible",
        "Dexter", "Lost", "Prison Break", "Suits", "Lucifer", "Supernatural", "The Walking Dead",
        "Euphoria", "White Lotus", "Yellowstone", "Mayor of Kingstown", "Cobra Kai",

        // Anime & Animation
        "Arcane", "Cyberpunk Edgerunners", "Attack on Titan", "Demon Slayer", "Demon Slayer Kimetsu no Yaiba",
        "Jujutsu Kaisen", "One Piece", "Naruto", "Naruto Shippuden", "Death Note", "Bleach",
        "Bleach Thousand-Year Blood War", "Dragon Ball Z", "Dragon Ball Super", "Solo Leveling",
        "Fullmetal Alchemist Brotherhood", "Hunter x Hunter", "My Hero Academia", "Vinland Saga",
        "Chainsaw Man", "Spy x Family", "Tokyo Ghoul", "Steins Gate", "Monster", "Code Geass",
        "Neon Genesis Evangelion", "Mob Psycho 100", "One Punch Man", "Frieren Beyond Journey's End",
        "Mashle Magic and Muscles", "Kaiju No 8", "Dandadan", "Blue Lock", "Haikyuu", "Baki Hanma",
        "Dr Stone", "Black Clover", "JoJo's Bizarre Adventure", "Cowboy Bebop", "Spirited Away",
        "Princess Mononoke", "Your Name", "Weathering With You", "Suzume", "A Silent Voice",

        // Popular Creators, Channels & Terms
        "MrBeast", "PewDiePie", "Markiplier", "Veritasium", "Kurzgesagt", "Vsauce",
        "TED", "National Geographic", "Discovery Channel", "BBC Earth"
    )

    // Patterns for release noise tags
    private val NOISE_PATTERNS = listOf(
        Regex("(?i)\\b(download|watch online|free download|torrent|direct link|gdrive|mega link|magnet)\\b"),
        Regex("(?i)\\b(web-dl|webrip|hdrip|bluray|brrip|camrip|hdcam|dvdrip|hdtv|bdrip|remux|v2)\\b"),
        Regex("(?i)\\b(dual audio|multi audio|hindi dubbed|english dubbed|hindi-english|english-hindi|esub|softsub|hardsub|subbed|dubbed)\\b"),
        Regex("(?i)\\b(x264|x265|hevc|10bit|8bit|aac|ac3|5\\.1|7\\.1|dts|flac|mkv|mp4|avi)\\b"),
        Regex("(?i)\\b(480p|720p|1080p|1080i|2160p|4k|uhd|hdr|fhd|hd)\\b"),
        Regex("(?i)\\b(\\d+(?:\\.\\d+)?\\s*(?:mb|gb))\\b"),
        Regex("(?i)\\b(season\\s*\\d+(?:-\\d+)?|seasons?\\s*\\d+|s\\d+e\\d+|s\\d+|e\\d+|ep\\d+|episode\\s*\\d+)\\b"),
        Regex("[\\[\\{].*?[\\]\\}]"),
        Regex("\\|\\|")
    )

    /**
     * Cleans raw input queries (strips release noise, torrent metadata, file sizes)
     * and performs synchronous fuzzy matching against known media titles to heal typos.
     */
    fun sanitizeQuery(rawQuery: String): CleanQueryResult {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return CleanQueryResult(rawQuery, rawQuery, false)
        }

        // If query starts with explicit extractor search prefixes, preserve as-is
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

        cleaned = cleaned.replace(Regex("[\\|\\-_:;\t\r\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.length < 2) {
            cleaned = trimmed.replace(Regex("[\\[\\]\\{\\}\\(\\)]"), "").trim()
        }

        val wasNoiseStripped = cleaned.lowercase(Locale.ROOT) != trimmed.lowercase(Locale.ROOT)

        // Step 2: Intelligent Offline Fuzzy Typo Matching
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
     * Resolves smart query with asynchronous online typo correction fallback (Google / DuckDuckGo suggest).
     * Guaranteed to complete within ~350ms or fallback to synchronous sanitizeQuery.
     */
    suspend fun resolveSmartQuery(rawQuery: String): CleanQueryResult = withContext(Dispatchers.IO) {
        val syncResult = sanitizeQuery(rawQuery)
        // If already matched a title locally or is a URL/special prefix, return immediately
        if (syncResult.didYouMean != null || !syncResult.wasCleaned && (rawQuery.startsWith("http") || rawQuery.startsWith("bili"))) {
            return@withContext syncResult
        }

        val target = syncResult.cleanQuery
        if (target.length < 3) return@withContext syncResult

        // Quick check: if target looks like a possible typo, query fast suggest API with 350ms timeout
        val onlineSuggestion = withTimeoutOrNull(400L) {
            fetchOnlineTypoCorrection(target)
        }

        if (onlineSuggestion != null && !onlineSuggestion.equals(target, ignoreCase = true)) {
            return@withContext CleanQueryResult(
                originalQuery = rawQuery.trim(),
                cleanQuery = onlineSuggestion,
                wasCleaned = true,
                didYouMean = onlineSuggestion,
                noiseDescription = "Smart search match"
            )
        }

        syncResult
    }

    /**
     * Fast online suggest check via Google / DuckDuckGo Suggest.
     */
    private fun fetchOnlineTypoCorrection(query: String): String? {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=chrome&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return null
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() > 1) {
                        val suggestions = jsonArray.getJSONArray(1)
                        if (suggestions.length() > 0) {
                            val top = suggestions.getString(0).trim()
                            // If top suggestion is a valid multi-char string and not identical
                            if (top.isNotBlank() && !top.equals(query, ignoreCase = true)) {
                                // Extract the primary subject/title if it has long suffix
                                return cleanSuggestionTitle(top)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Google suggest typo check note: ${e.message}")
        }

        // Fallback: DuckDuckGo autocomplete
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val ddgUrl = "https://duckduckgo.com/ac/?q=$encoded&type=list"
            val req = Request.Builder()
                .url(ddgUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return null
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() > 1) {
                        val suggestions = jsonArray.getJSONArray(1)
                        if (suggestions.length() > 0) {
                            val top = suggestions.getString(0).trim()
                            if (top.isNotBlank() && !top.equals(query, ignoreCase = true)) {
                                return cleanSuggestionTitle(top)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "DDG suggest typo check note: ${e.message}")
        }

        return null
    }

    private fun cleanSuggestionTitle(suggestion: String): String {
        // Strip common query suffixes like "movie", "cast", "trailer", "streaming", "meaning" if user didn't ask for them
        val stripped = suggestion.replace(Regex("(?i)\\s+(movie|cast|trailer|streaming|meaning|director|release date|netflix|online|songs?|wiki)$"), "")
        return stripped.ifBlank { suggestion }
    }

    /**
     * Finds fuzzy match for query in popular titles using Levenshtein distance,
     * compound space joining ("inter stell" -> "interstellar"), and token similarity.
     */
    fun findFuzzyMatch(query: String): String? {
        val q = query.lowercase(Locale.ROOT).trim()
        if (q.length < 3) return null

        // 1. Direct case-insensitive match
        for (title in POPULAR_TITLES) {
            if (title.equals(q, ignoreCase = true)) return title
        }

        // 2. Space-removed / collapsed match (e.g. "inter stell" -> "interstell" vs "interstellar")
        val qNoSpace = q.replace(" ", "")
        for (title in POPULAR_TITLES) {
            val tNoSpace = title.lowercase(Locale.ROOT).replace(" ", "").replace("-", "")
            if (tNoSpace == qNoSpace) return title

            // Check if collapsed query is prefix of title (e.g. "interstell" -> "Interstellar")
            if (tNoSpace.startsWith(qNoSpace) && qNoSpace.length >= 6) {
                return title
            }
        }

        // 3. Substring full match
        for (title in POPULAR_TITLES) {
            val tLower = title.lowercase(Locale.ROOT)
            if (tLower.contains(q) || (q.length >= 6 && q.contains(tLower))) {
                return title
            }
        }

        // 4. Word-level & Levenshtein fuzzy check (e.g. "intersteelarg" -> "Interstellar")
        var bestMatch: String? = null
        var minDistance = Int.MAX_VALUE

        for (title in POPULAR_TITLES) {
            val tLower = title.lowercase(Locale.ROOT)
            val tNoSpace = tLower.replace(" ", "").replace("-", "")

            val distance = levenshteinDistance(qNoSpace, tNoSpace)

            // Dynamic allowed distance based on query length
            val maxAllowedDistance = when {
                qNoSpace.length <= 4 -> 1
                qNoSpace.length <= 8 -> 2
                qNoSpace.length <= 13 -> 3
                else -> 4
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
