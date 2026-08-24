package com.example.vega

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object VegaProviderClient {
    private const val TAG = "VegaProviderClient"
    const val DEFAULT_SERVER_URL = "https://butterfly-mediaserver-1.onrender.com"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun formatProviderDisplayName(providerId: String): String {
        val trimmed = providerId.trim().lowercase()
        return when (trimmed) {
            "hdhub4u" -> "HDHub4U"
            "hianime" -> "HiAnime"
            "vega" -> "Vega"
            "netflixmirror" -> "NetflixMirror"
            "gogoanime" -> "GogoAnime"
            "animepahe" -> "AnimePahe"
            "kissasian" -> "KissAsian"
            "doodstream" -> "DoodStream"
            "streamtape" -> "StreamTape"
            "filmyfly" -> "FilmyFly"
            "bollyflix" -> "BollyFlix"
            "topmovies" -> "TopMovies"
            "allmovieshub" -> "AllMoviesHub"
            "modflix" -> "ModFlix"
            "katmoviehd" -> "KatMovieHD"
            else -> {
                // Capitalize tokens nicely
                trimmed.split('-', '_', ' ')
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { token ->
                        token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                    .ifEmpty { providerId }
            }
        }
    }

    suspend fun getAvailableProviders(baseUrl: String = DEFAULT_SERVER_URL): List<String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<String>()
        try {
            val cleanBase = baseUrl.trimEnd('/')
            val request = Request.Builder()
                .url("$cleanBase/providers")
                .header("User-Agent", "Butterfly/1.0 (Android)")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch providers, code: ${response.code}")
                    return@withContext list
                }

                val bodyStr = response.body?.string() ?: return@withContext list
                val trimmed = bodyStr.trim()

                if (trimmed.startsWith("[")) {
                    val array = JSONArray(trimmed)
                    for (i in 0 until array.length()) {
                        val item = array.opt(i)
                        when (item) {
                            is String -> if (item.isNotBlank()) list.add(item.trim())
                            is JSONObject -> {
                                val id = item.optString("id").ifBlank { item.optString("name") }
                                if (id.isNotBlank()) list.add(id.trim())
                            }
                        }
                    }
                } else if (trimmed.startsWith("{")) {
                    val json = JSONObject(trimmed)
                    val array = json.optJSONArray("providers")
                        ?: json.optJSONArray("data")
                        ?: json.optJSONArray("results")

                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val item = array.opt(i)
                            when (item) {
                                is String -> if (item.isNotBlank()) list.add(item.trim())
                                is JSONObject -> {
                                    val id = item.optString("id").ifBlank { item.optString("name") }
                                    if (id.isNotBlank()) list.add(id.trim())
                                }
                            }
                        }
                    } else {
                        // Keys might be the provider names
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            if (key != "status" && key != "message" && key != "success") {
                                list.add(key)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching providers from $baseUrl: ${e.message}")
        }
        return@withContext list.distinct()
    }

    suspend fun search(
        providerId: String,
        query: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): List<VegaSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VegaSearchResult>()
        if (providerId.isBlank() || query.isBlank()) return@withContext results

        try {
            val cleanBase = baseUrl.trimEnd('/')
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val encodedProvider = URLEncoder.encode(providerId, StandardCharsets.UTF_8.toString())
            val url = "$cleanBase/search/$encodedProvider?q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly/1.0 (Android)")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Search failed for provider $providerId, code: ${response.code}")
                    return@withContext results
                }

                val bodyStr = response.body?.string() ?: return@withContext results
                val trimmed = bodyStr.trim()

                val jsonArray = when {
                    trimmed.startsWith("[") -> JSONArray(trimmed)
                    trimmed.startsWith("{") -> {
                        val json = JSONObject(trimmed)
                        json.optJSONArray("results")
                            ?: json.optJSONArray("data")
                            ?: json.optJSONArray("items")
                            ?: JSONArray()
                    }
                    else -> JSONArray()
                }

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val title = obj.optString("title")
                        .ifBlank { obj.optString("name") }
                        .ifBlank { "Untitled" }
                    val link = obj.optString("link")
                        .ifBlank { obj.optString("url") }
                        .ifBlank { obj.optString("id") }

                    if (link.isNotBlank()) {
                        val image = obj.optString("image")
                            .ifBlank { obj.optString("poster") }
                            .ifBlank { obj.optString("thumbnail") }
                            .ifBlank { obj.optString("img") }
                            .ifBlank { null }
                        val extra = obj.optString("extra")
                            .ifBlank { obj.optString("quality") }
                            .ifBlank { obj.optString("year") }
                            .ifBlank { null }

                        results.add(
                            VegaSearchResult(
                                id = link,
                                title = title,
                                link = link,
                                imageUrl = image,
                                providerId = providerId,
                                extraInfo = extra
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing search for $providerId: ${e.message}")
        }
        return@withContext results
    }

    suspend fun getStream(
        providerId: String,
        link: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VegaStreamResult>()
        if (providerId.isBlank() || link.isBlank()) return@withContext streams

        try {
            val cleanBase = baseUrl.trimEnd('/')
            val encodedLink = URLEncoder.encode(link, StandardCharsets.UTF_8.toString())
            val encodedProvider = URLEncoder.encode(providerId, StandardCharsets.UTF_8.toString())
            val url = "$cleanBase/stream/$encodedProvider?link=$encodedLink"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly/1.0 (Android)")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Stream extraction failed for $providerId, code: ${response.code}")
                    return@withContext streams
                }

                val bodyStr = response.body?.string() ?: return@withContext streams
                val trimmed = bodyStr.trim()

                if (trimmed.startsWith("[")) {
                    val array = JSONArray(trimmed)
                    for (i in 0 until array.length()) {
                        val item = array.opt(i)
                        if (item is JSONObject) {
                            parseStreamObject(item)?.let { streams.add(it) }
                        } else if (item is String && item.startsWith("http")) {
                            streams.add(VegaStreamResult(url = item))
                        }
                    }
                } else if (trimmed.startsWith("{")) {
                    val json = JSONObject(trimmed)
                    val streamsArray = json.optJSONArray("streams")
                        ?: json.optJSONArray("data")
                        ?: json.optJSONArray("results")

                    if (streamsArray != null) {
                        for (i in 0 until streamsArray.length()) {
                            val item = streamsArray.optJSONObject(i)
                            if (item != null) {
                                parseStreamObject(item)?.let { streams.add(it) }
                            }
                        }
                    } else {
                        parseStreamObject(json)?.let { streams.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stream for $providerId: ${e.message}")
        }
        return@withContext streams
    }

    private fun parseStreamObject(obj: JSONObject): VegaStreamResult? {
        val url = obj.optString("url")
            .ifBlank { obj.optString("link") }
            .ifBlank { obj.optString("streamUrl") }
            .ifBlank { obj.optString("file") }

        if (url.isBlank()) return null

        val isMagnet = url.startsWith("magnet:") || obj.optBoolean("isTorrent", false) || obj.optString("type").equals("torrent", ignoreCase = true)
        val quality = obj.optString("quality").ifBlank { obj.optString("resolution") }.ifBlank { "Auto" }
        val format = obj.optString("format").ifBlank { obj.optString("type") }.ifBlank {
            if (url.contains(".m3u8")) "hls" else "mp4"
        }

        val headersMap = mutableMapOf<String, String>()
        val headersObj = obj.optJSONObject("headers")
        if (headersObj != null) {
            val keys = headersObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                headersMap[key] = headersObj.optString(key)
            }
        }

        val subtitles = mutableListOf<String>()
        val subsArray = obj.optJSONArray("subtitles") ?: obj.optJSONArray("subs")
        if (subsArray != null) {
            for (i in 0 until subsArray.length()) {
                val sub = subsArray.opt(i)
                if (sub is String && sub.isNotBlank()) {
                    subtitles.add(sub)
                } else if (sub is JSONObject) {
                    val subUrl = sub.optString("url").ifBlank { sub.optString("file") }
                    if (subUrl.isNotBlank()) subtitles.add(subUrl)
                }
            }
        }

        return VegaStreamResult(
            url = url,
            quality = quality,
            format = format,
            headers = headersMap,
            isTorrent = isMagnet,
            subtitleUrls = subtitles
        )
    }
}
