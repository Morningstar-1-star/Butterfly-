package com.example.util

import android.util.Log
import com.example.extractor.HellPornoProvider
import com.example.extractor.SpankBangProvider
import com.example.extractor.XnxxProvider
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
 * Fetches accurate autocomplete suggestions across all sources with strict isolation
 * between 18+ adult sources and normal sources, and provides rich thumbnail suggestions across all providers.
 */
object UniversalSearchSuggestionEngine {
    private const val TAG = "UniversalSearchSuggest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3500L, TimeUnit.MILLISECONDS)
        .readTimeout(3500L, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    fun isAdultProvider(providerId: String?): Boolean {
        if (providerId.isNullOrBlank()) return false
        val lower = providerId.lowercase()
        return lower in setOf(
            "eporner", "spankbang", "xnxx", "hellporno", "pornhub", "xvideos", "stripchat",
            "xhamster", "redtube", "youporn", "beeg", "hanime1", "4tube",
            "rule34video", "thisvid", "tnaflix", "noodlemagazine", "playvid", "txxx",
            "supjav", "123av", "javtiful", "jav_all", "sextb", "cam4", "cammodels", "chaturbate"
        )
    }

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

        val cleanActiveProv = activeProviderId.trim().lowercase()
        val isSingleAdultSource = isAdultProvider(cleanActiveProv)
        val isAdultContext = adultEnabled || isSingleAdultSource || AdultModelMatcher.isAdultQuery(q)

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

        // 2. Recent Search History matches matching current context
        val historyMatches = recentSearches
            .filter { hist ->
                val matchesQ = hist.contains(q, ignoreCase = true) || hist.contains(sanitized.cleanQuery, ignoreCase = true)
                val isAdultHist = AdultModelMatcher.isAdultQuery(hist)
                if (isAdultContext) {
                    matchesQ && isAdultHist
                } else {
                    matchesQ && !isAdultHist
                }
            }
            .take(3)

        historyMatches.forEach { historyQuery ->
            addSuggestion(
                SearchSuggestionItem(
                    query = historyQuery,
                    isHistory = true
                )
            )
        }

        // 3. JAV Code or Model Name Instant Autocomplete (Only in 18+ mode)
        if (isAdultContext) {
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

        // 4. Provider Suggestions with Single Source Isolation & Zero Cross-Contamination
        try {
            withTimeoutOrNull(2800L) {
                coroutineScope {
                    val searchTarget = sanitized.cleanQuery.ifBlank { q }

                    if (isAdultContext) {
                        // STRICTLY ADULT SOURCES - NO YOUTUBE / NORMAL SOURCES!
                        when (cleanActiveProv) {
                            "eporner" -> {
                                val epList = fetchEpornerSuggestions(searchTarget)
                                epList.forEach { addSuggestion(it) }
                            }
                            "spankbang" -> {
                                val sbList = fetchSpankBangSuggestions(searchTarget)
                                sbList.forEach { addSuggestion(it) }
                            }
                            "xnxx" -> {
                                val xnList = fetchXnxxSuggestions(searchTarget)
                                xnList.forEach { addSuggestion(it) }
                            }
                            "hellporno" -> {
                                val hpList = fetchHellPornoSuggestions(searchTarget)
                                hpList.forEach { addSuggestion(it) }
                            }
                            "pornhub" -> {
                                val phList = fetchPornhubAutocomplete(searchTarget)
                                phList.forEach { addSuggestion(it) }
                            }
                            "xvideos" -> {
                                val xvList = fetchXvideosAutocomplete(searchTarget)
                                xvList.forEach { addSuggestion(it) }
                            }
                            else -> {
                                // Multi Adult Sources: query rich suggestions across top adult providers in parallel
                                val epDef = async { fetchEpornerSuggestions(searchTarget) }
                                val sbDef = async { fetchSpankBangSuggestions(searchTarget) }
                                val xnDef = async { fetchXnxxSuggestions(searchTarget) }
                                val phDef = async { fetchPornhubAutocomplete(searchTarget) }

                                val epList = epDef.await()
                                val sbList = sbDef.await()
                                val xnList = xnDef.await()
                                val phList = phDef.await()

                                epList.forEach { addSuggestion(it) }
                                sbList.forEach { addSuggestion(it) }
                                xnList.forEach { addSuggestion(it) }
                                phList.forEach { addSuggestion(it) }
                            }
                        }
                    } else {
                        // NORMAL SOURCES ONLY - NO ADULT SOURCES!
                        when (cleanActiveProv) {
                            "bilibili" -> {
                                val biliList = fetchBilibiliSuggestions(searchTarget)
                                biliList.forEach { addSuggestion(it) }
                            }
                            "tmdb", "tmdb_embed" -> {
                                val tmdbList = fetchTmdbMediaSuggestions(searchTarget)
                                tmdbList.forEach { addSuggestion(it) }
                            }
                            "youtube" -> {
                                val ytList = fetchGoogleYouTubeSuggestions(searchTarget)
                                ytList.forEach { addSuggestion(it) }
                            }
                            else -> {
                                // Default Normal: YouTube + TMDB Explore Media
                                val ytDef = async { fetchGoogleYouTubeSuggestions(searchTarget) }
                                val tmdbDef = async { fetchTmdbMediaSuggestions(searchTarget) }

                                val tmdbList = tmdbDef.await()
                                val ytList = ytDef.await()

                                tmdbList.forEach { addSuggestion(it) }
                                ytList.forEach { addSuggestion(it) }
                            }
                        }
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

    private suspend fun fetchBilibiliSuggestions(query: String): List<SearchSuggestionItem> {
        val list = mutableListOf<SearchSuggestionItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://s.search.bilibili.com/main/suggest?term=$encoded&main_ver=v1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://www.bilibili.com/")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val json = JSONObject(jsonStr)
            val result = json.optJSONArray("result")
            if (result != null) {
                for (i in 0 until result.length().coerceAtMost(8)) {
                    val item = result.optJSONObject(i) ?: continue
                    val value = item.optString("value")
                    if (value.isNotBlank()) {
                        list.add(
                            SearchSuggestionItem(
                                query = value,
                                isHistory = false,
                                providerBadge = "Bilibili"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Bilibili suggestion note: ${e.message}")
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
            for (i in 0 until videos.length().coerceAtMost(6)) {
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

    private suspend fun fetchSpankBangSuggestions(query: String): List<SearchSuggestionItem> {
        val list = mutableListOf<SearchSuggestionItem>()
        try {
            val items = SpankBangProvider.search(query, 1).take(5)
            items.forEach { video ->
                list.add(
                    SearchSuggestionItem(
                        query = video.title,
                        isHistory = false,
                        providerBadge = "SpankBang",
                        thumbnailUrl = video.thumbnailUrl
                    )
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "SpankBang suggestion note: ${e.message}")
        }
        return list
    }

    private suspend fun fetchXnxxSuggestions(query: String): List<SearchSuggestionItem> {
        val list = mutableListOf<SearchSuggestionItem>()
        try {
            val items = XnxxProvider.search(query, 1).take(5)
            items.forEach { video ->
                list.add(
                    SearchSuggestionItem(
                        query = video.title,
                        isHistory = false,
                        providerBadge = "XNXX",
                        thumbnailUrl = video.thumbnailUrl
                    )
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "XNXX suggestion note: ${e.message}")
        }
        return list
    }

    private suspend fun fetchHellPornoSuggestions(query: String): List<SearchSuggestionItem> {
        val list = mutableListOf<SearchSuggestionItem>()
        try {
            val items = HellPornoProvider.search(query, 1).take(5)
            items.forEach { video ->
                list.add(
                    SearchSuggestionItem(
                        query = video.title,
                        isHistory = false,
                        providerBadge = "HellPorno",
                        thumbnailUrl = video.thumbnailUrl
                    )
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "HellPorno suggestion note: ${e.message}")
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

    private fun fetchXvideosAutocomplete(query: String): List<SearchSuggestionItem> {
        val list = mutableListOf<SearchSuggestionItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.xvideos.com/video/autocomplete?q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://www.xvideos.com/")
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
                            providerBadge = "Xvideos"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Xvideos autocomplete note: ${e.message}")
        }
        return list
    }
}
