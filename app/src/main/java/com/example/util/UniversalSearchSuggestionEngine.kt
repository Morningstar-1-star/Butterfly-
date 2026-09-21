package com.example.util

import android.util.Log
import com.example.metadata.JavIdParser
import com.example.model.SearchSuggestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Universal real-time search suggestion and autocomplete engine.
 * Fetches accurate autocomplete suggestions across all sources (normal & adult).
 */
object UniversalSearchSuggestionEngine {
    private const val TAG = "UniversalSearchSuggest"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getSuggestions(
        query: String,
        adultEnabled: Boolean,
        activeProviderId: String,
        recentSearches: List<String> = emptyList()
    ): List<SearchSuggestionItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        val results = mutableListOf<SearchSuggestionItem>()
        val seenQueries = mutableSetOf<String>()

        fun addSuggestion(item: SearchSuggestionItem) {
            val key = item.query.lowercase().trim()
            if (key.isNotBlank() && seenQueries.add(key)) {
                results.add(item)
            }
        }

        // 1. Check Typo Correction / AI Sanitization
        val sanitized = SmartSearchSanitizer.sanitizeQuery(q)
        if (sanitized.wasCleaned && sanitized.cleanQuery.isNotBlank()) {
            addSuggestion(
                SearchSuggestionItem(
                    query = sanitized.cleanQuery,
                    isHistory = false,
                    providerBadge = if (sanitized.didYouMean != null) "Did You Mean" else "Cleaned"
                )
            )
        }

        // 2. Recent Search History matches
        val historyMatches = recentSearches
            .filter { it.contains(q, ignoreCase = true) || it.contains(sanitized.cleanQuery, ignoreCase = true) }
            .take(3)
        historyMatches.forEach { historyQuery ->
            addSuggestion(
                SearchSuggestionItem(
                    query = historyQuery,
                    isHistory = true
                )
            )
        }

        // 3. JAV Code or Model Name Instant Autocomplete
        if (adultEnabled) {
            val javCode = JavIdParser.parse(q)
            if (javCode != null) {
                addSuggestion(
                    SearchSuggestionItem(
                        query = javCode,
                        isHistory = false,
                        providerBadge = "JAV Code"
                    )
                )
            }

            val matchingModels = AdultModelMatcher.getAllKnownModels()
                .filter { model ->
                    model.primaryName.contains(q, ignoreCase = true) ||
                    model.aliases.any { it.contains(q, ignoreCase = true) }
                }
                .take(3)

            matchingModels.forEach { model ->
                addSuggestion(
                    SearchSuggestionItem(
                        query = model.primaryName,
                        isHistory = false,
                        providerBadge = if (model.isJav) "JAV Actress" else "Model",
                        thumbnailUrl = model.imageUrl,
                        subtitle = if (model.aliases.isNotEmpty()) model.aliases.first() else null
                    )
                )
            }
        }

        // 4. Parallel Live Suggestion Network Calls with 2.5s Timeout
        try {
            withTimeoutOrNull(2500L) {
                coroutineScope {
                    val searchTarget = sanitized.cleanQuery.ifBlank { q }

                    if (!adultEnabled) {
                        // Normal mode: Google/YouTube + TMDB Explore Media
                        val ytDef = async { fetchGoogleYouTubeSuggestions(searchTarget) }
                        val tmdbDef = async { fetchTmdbMediaSuggestions(searchTarget) }

                        val ytList = ytDef.await()
                        val tmdbList = tmdbDef.await()

                        tmdbList.forEach { addSuggestion(it) }
                        ytList.forEach { addSuggestion(it) }
                    } else {
                        // Adult mode: Eporner API + Pornhub Autocomplete + Google YT
                        val epDef = async { fetchEpornerSuggestions(searchTarget) }
                        val phDef = async { fetchPornhubAutocomplete(searchTarget) }
                        val ytDef = async { fetchGoogleYouTubeSuggestions(searchTarget) }

                        val epList = epDef.await()
                        val phList = phDef.await()
                        val ytList = ytDef.await()

                        epList.forEach { addSuggestion(it) }
                        phList.forEach { addSuggestion(it) }
                        ytList.forEach { addSuggestion(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parallel suggestions fetch note: ${e.message}")
        }

        results.take(15)
    }

    private fun fetchGoogleYouTubeSuggestions(query: String): List<SearchSuggestionItem> {
        val list = mutableListOf<SearchSuggestionItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val jsonArr = JSONArray(jsonStr)
            if (jsonArr.length() > 1) {
                val suggestionArr = jsonArr.optJSONArray(1)
                if (suggestionArr != null) {
                    for (i in 0 until suggestionArr.length().coerceAtMost(8)) {
                        val sug = suggestionArr.optString(i)
                        if (sug.isNotBlank()) {
                            list.add(
                                SearchSuggestionItem(
                                    query = sug,
                                    isHistory = false,
                                    providerBadge = "YouTube"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Google suggestion fetch note: ${e.message}")
        }
        return list
    }

    private suspend fun fetchTmdbMediaSuggestions(query: String): List<SearchSuggestionItem> {
        val list = mutableListOf<SearchSuggestionItem>()
        try {
            val exploreItems = ExploreMediaHelper.searchAll(query).take(4)
            exploreItems.forEach { item ->
                val year = if (!item.releaseYear.isNullOrBlank()) " (${item.releaseYear})" else ""
                val badge = if (item.mediaType == com.example.model.ExploreMediaType.MOVIE) "Movie" else "TV Series"
                list.add(
                    SearchSuggestionItem(
                        query = "${item.title}$year",
                        isHistory = false,
                        providerBadge = badge,
                        thumbnailUrl = item.posterUrl,
                        subtitle = item.overview?.take(60)
                    )
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "TMDB suggestion note: ${e.message}")
        }
        return list
    }

    private fun fetchEpornerSuggestions(query: String): List<SearchSuggestionItem> {
        val list = mutableListOf<SearchSuggestionItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.eporner.com/api/v2/video/search/?query=$encoded&per_page=6"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val json = JSONObject(jsonStr)
            val videos = json.optJSONArray("videos") ?: return list
            for (i in 0 until videos.length()) {
                val v = videos.optJSONObject(i) ?: continue
                val title = v.optString("title")
                val thumb = v.optJSONObject("default_thumb")?.optString("src")
                if (title.isNotBlank()) {
                    list.add(
                        SearchSuggestionItem(
                            query = title,
                            isHistory = false,
                            providerBadge = "Eporner",
                            thumbnailUrl = thumb
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Eporner suggestion note: ${e.message}")
        }
        return list
    }

    private fun fetchPornhubAutocomplete(query: String): List<SearchSuggestionItem> {
        val list = mutableListOf<SearchSuggestionItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.pornhub.com/video/autocomplete?q=$encoded&orientation=straight"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://www.pornhub.com/")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length().coerceAtMost(6)) {
                val sug = arr.optString(i)
                if (sug.isNotBlank()) {
                    list.add(
                        SearchSuggestionItem(
                            query = sug,
                            isHistory = false,
                            providerBadge = "Pornhub"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Pornhub autocomplete note: ${e.message}")
        }
        return list
    }
}
