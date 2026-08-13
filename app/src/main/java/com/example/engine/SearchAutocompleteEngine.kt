package com.example.engine

import android.util.Log
import com.example.model.SearchSuggestionItem
import com.example.util.TMDBHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SearchAutocompleteEngine {

    private const val TAG = "SearchAutocompleteEngine"

    suspend fun getSuggestions(
        query: String,
        recentHistory: List<String>,
        adultEnabled: Boolean = false
    ): List<SearchSuggestionItem> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        val cleanQuery = query.trim().lowercase()

        // 1. Filter matching recent search history first (YouTube-style top matching history)
        val matchedHistory = recentHistory
            .filter { it.lowercase().contains(cleanQuery) || cleanQuery.contains(it.lowercase()) }
            .distinctBy { it.lowercase() }
            .take(5)
            .map { SearchSuggestionItem(query = it, isHistory = true) }

        // 2. Fetch live suggestions in parallel across APIs
        val youtubeDeferred = async(Dispatchers.IO) { fetchYouTubeSuggestions(query) }
        val archiveDeferred = async(Dispatchers.IO) { fetchArchiveSuggestions(query) }
        val tmdbDeferred = async(Dispatchers.IO) { fetchTmdbSuggestions(query) }
        val adultDeferred = async(Dispatchers.IO) {
            if (adultEnabled) fetchAdultSuggestions(query) else emptyList()
        }

        val ytSuggestions = youtubeDeferred.await()
        val archiveSuggestions = archiveDeferred.await()
        val tmdbSuggestions = tmdbDeferred.await()
        val adultSuggestions = adultDeferred.await()

        // Combined live autocomplete items
        val liveSuggestions = mutableListOf<SearchSuggestionItem>()
        liveSuggestions.addAll(tmdbSuggestions)
        liveSuggestions.addAll(ytSuggestions)
        liveSuggestions.addAll(archiveSuggestions)
        liveSuggestions.addAll(adultSuggestions)

        // Deduplicate against matching history and distinct by query text
        val historyQuerySet = matchedHistory.map { it.query.lowercase() }.toSet()
        val filteredLive = liveSuggestions
            .filterNot { historyQuerySet.contains(it.query.lowercase()) }
            .distinctBy { it.query.lowercase() }
            .take(15)

        matchedHistory + filteredLive
    }

    private fun fetchYouTubeSuggestions(query: String): List<SearchSuggestionItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded"
            val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(responseText)
                if (jsonArray.length() >= 2) {
                    val suggestionsArray = jsonArray.getJSONArray(1)
                    val results = mutableListOf<SearchSuggestionItem>()
                    for (i in 0 until suggestionsArray.length()) {
                        val sug = suggestionsArray.getString(i)
                        if (sug.isNotBlank()) {
                            results.add(SearchSuggestionItem(query = sug, isHistory = false, providerBadge = "YouTube"))
                        }
                    }
                    return results
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching YouTube suggestions", e)
            emptyList()
        }
    }

    private fun fetchArchiveSuggestions(query: String): List<SearchSuggestionItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://archive.org/advancedsearch.php?q=title:($encoded*)&fl[]=title&output=json&rows=4"
            val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(responseText)
                val docs = jsonObj.optJSONObject("response")?.optJSONArray("docs")
                if (docs != null) {
                    val results = mutableListOf<SearchSuggestionItem>()
                    for (i in 0 until docs.length()) {
                        val title = docs.getJSONObject(i).optString("title")
                        if (title.isNotBlank()) {
                            results.add(SearchSuggestionItem(query = title, isHistory = false, providerBadge = "Archive"))
                        }
                    }
                    return results
                }
            }
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchTmdbSuggestions(query: String): List<SearchSuggestionItem> {
        return try {
            val details = TMDBHelper.fetchMediaDetails(query)
            if (details.title.isNotBlank()) {
                listOf(
                    SearchSuggestionItem(
                        query = details.title,
                        isHistory = false,
                        providerBadge = "TMDB",
                        subtitle = if (details.director.isNotBlank()) details.director else "Movie / Series"
                    )
                )
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchAdultSuggestions(query: String): List<SearchSuggestionItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://www.pornhub.com/search/autocomplete?q=$encoded"
            val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val results = mutableListOf<SearchSuggestionItem>()
                if (responseText.trim().startsWith("[")) {
                    val jsonArray = JSONArray(responseText)
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.get(i)
                        val str = if (item is JSONObject) item.optString("query", item.optString("keyword")) else item.toString()
                        if (str.isNotBlank()) {
                            results.add(SearchSuggestionItem(query = str, isHistory = false, providerBadge = "Adult"))
                        }
                    }
                }
                return results
            }
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
