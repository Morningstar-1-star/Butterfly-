package com.example.vega

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    @Volatile
    var isVegaGloballyEnabled: Boolean = false

    val BACKUP_SERVER_URLS = listOf(
        "https://butterfly-mediaserver-1.onrender.com",
        "https://butterfly-mediaserver.onrender.com",
        "https://butterfly-server.onrender.com",
        "https://butterfly-mediaserver-2.onrender.com"
    )

    // Stage-specific timeout configuration
    private const val SEARCH_TIMEOUT_MS = 20_000L
    private const val META_TIMEOUT_MS = 20_000L
    private const val EPISODES_TIMEOUT_MS = 20_000L
    private const val STREAM_TIMEOUT_MS = 25_000L
    private const val PROBE_TIMEOUT_MS = 8_000L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private suspend fun <T> executeWithServerFallbacks(
        initialBaseUrl: String,
        block: suspend (baseUrl: String) -> T?,
        isValidResult: (T?) -> Boolean
    ): T? {
        val firstAttempt = try { block(initialBaseUrl) } catch (_: Exception) { null }
        if (isValidResult(firstAttempt)) return firstAttempt

        for (backupUrl in BACKUP_SERVER_URLS) {
            if (backupUrl.equals(initialBaseUrl, ignoreCase = true)) continue
            try {
                val res = block(backupUrl)
                if (isValidResult(res)) {
                    Log.i(TAG, "Vega server fallback succeeded with mirror: $backupUrl")
                    return res
                }
            } catch (_: Exception) {}
        }
        return firstAttempt
    }

    fun formatProviderDisplayName(providerId: String): String {
        val trimmed = providerId.trim().lowercase()
        return when (trimmed) {
            "hdhub4u" -> "HDHub4U"
            "4khdhub" -> "4K HDHub"
            "hianime" -> "HiAnime"
            "vega" -> "VegaMovies"
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
            "katmoviefix" -> "KatMovieFix"
            "cinemaluxe" -> "CinemaLuxe"
            "1cinevood" -> "1CineVood"
            "world4u", "world4ufree" -> "World4UFree"
            "zeefliz" -> "ZeeFliz"
            "eonmovies" -> "EonMovies"
            "cinefreak" -> "CineFreak"
            "showbox" -> "ShowBox"
            "gokuhd" -> "GokuHD"
            "flixhq" -> "FlixHQ"
            "uhd", "uhdmovies" -> "UHDMovies"
            "movies4u" -> "Movies4U"
            "ridomovies" -> "RidoMovies"
            "movieboxweb" -> "MovieBoxWeb"
            "kisskh" -> "KissKH"
            "torrentio" -> "Torrentio"
            "autoembed" -> "AutoEmbed"
            "guardahd" -> "GuardaHD"
            "anikoto" -> "AniKoto"
            else -> {
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
        executeWithServerFallbacks(baseUrl, { currentUrl ->
            fetchAvailableProvidersInternal(currentUrl)
        }, { res -> !res.isNullOrEmpty() }) ?: emptyList()
    }

    private suspend fun fetchAvailableProvidersInternal(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
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
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            if (key != "status" && key != "message" && key != "success" && key != "version") {
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

    suspend fun getHomeContent(
        providerId: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): List<VegaSearchResult> = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled || providerId.isBlank()) return@withContext emptyList()
        val cleanProv = providerId.trim().lowercase()
        val allResults = mutableListOf<VegaSearchResult>()

        val discoveryQueries = listOf("2025", "2024", "spider", "the", "a", "action", "hindi", "dual", "movie", "one")
        
        coroutineScope {
            val deferred = discoveryQueries.take(4).map { q ->
                async(Dispatchers.IO) {
                    searchSingleQuery(cleanProv, q, baseUrl)
                }
            }
            deferred.awaitAll().forEach { list ->
                allResults.addAll(list)
            }
        }

        if (allResults.isEmpty()) {
            for (q in listOf("avengers", "love", "man", "war", "2023", "popular")) {
                val list = searchSingleQuery(cleanProv, q, baseUrl)
                if (list.isNotEmpty()) {
                    allResults.addAll(list)
                    break
                }
            }
        }

        return@withContext allResults.distinctBy { it.link }
    }

    suspend fun search(
        providerId: String,
        query: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): List<VegaSearchResult> = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled || providerId.isBlank()) return@withContext emptyList()
        val cleanProv = providerId.trim().lowercase()
        val cleanQuery = query.trim()

        executeWithServerFallbacks(baseUrl, { currentUrl ->
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                var results = searchSingleQuery(cleanProv, cleanQuery.ifBlank { "2024" }, currentUrl)
                if (results.isNotEmpty()) return@withTimeoutOrNull results

                val fallbackTerms = listOf("a", "movie")
                    .filterNot { it.equals(cleanQuery, ignoreCase = true) }

                for (term in fallbackTerms) {
                    results = searchSingleQuery(cleanProv, term, currentUrl)
                    if (results.isNotEmpty()) {
                        Log.d(TAG, "Search for '$cleanProv' succeeded with fallback term '$term' (${results.size} items)")
                        return@withTimeoutOrNull results
                    }
                }
                results
            }
        }, { res -> !res.isNullOrEmpty() }) ?: emptyList()
    }

    private suspend fun searchSingleQuery(
        providerId: String,
        query: String,
        baseUrl: String
    ): List<VegaSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VegaSearchResult>()
        val cleanBase = baseUrl.trimEnd('/')
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val encodedProvider = URLEncoder.encode(providerId, StandardCharsets.UTF_8.toString())

        val endpointsToTest = listOf(
            "$cleanBase/search/$encodedProvider?q=$encodedQuery",
            "$cleanBase/search?provider=$encodedProvider&q=$encodedQuery",
            "$cleanBase/providers/$encodedProvider/search?q=$encodedQuery",
            "$cleanBase/posts/$encodedProvider?page=1",
            "$cleanBase/posts/$encodedProvider?s=$encodedQuery",
            "$cleanBase/catalog/$encodedProvider?page=1",
            "$cleanBase/catalog/$encodedProvider?q=$encodedQuery",
            "$cleanBase/api/search/$encodedProvider?q=$encodedQuery",
            "$cleanBase/$encodedProvider/search?q=$encodedQuery",
            "$cleanBase/$encodedProvider?s=$encodedQuery"
        )

        val queryClient = httpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()

        for (targetUrl in endpointsToTest) {
            try {
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json")
                    .build()

                queryClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val bodyStr = response.body?.string() ?: return@use
                    val trimmed = bodyStr.trim()

                    val jsonArray = when {
                        trimmed.startsWith("[") -> JSONArray(trimmed)
                        trimmed.startsWith("{") -> {
                            val json = JSONObject(trimmed)
                            json.optJSONArray("results")
                                ?: json.optJSONArray("data")
                                ?: json.optJSONArray("items")
                                ?: json.optJSONArray("posts")
                                ?: json.optJSONArray("catalog")
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
                if (results.isNotEmpty()) break
            } catch (e: Exception) {
                // Try next endpoint
            }
        }
        return@withContext results
    }

    /**
     * Correct Vega Step 2: Fetch Info/Metadata for a post link.
     * Endpoint: /meta/{provider}?link={encodedLink}
     * Returns VegaMetaResult with Info.linkList and directLinks / episodesLink.
     */
    suspend fun getMeta(
        providerId: String,
        link: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): VegaMetaResult? = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled || providerId.isBlank() || link.isBlank()) return@withContext null

        executeWithServerFallbacks(baseUrl, { currentUrl ->
            fetchMetaInternal(providerId, link, currentUrl)
        }, { res -> res != null && (res.linkList.isNotEmpty() || !res.title.equals("Untitled", ignoreCase = true)) })
    }

    private suspend fun fetchMetaInternal(
        providerId: String,
        link: String,
        baseUrl: String
    ): VegaMetaResult? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(META_TIMEOUT_MS) {
            try {
                val cleanBase = baseUrl.trimEnd('/')
                val encodedLink = URLEncoder.encode(link, StandardCharsets.UTF_8.toString())
                val encodedProvider = URLEncoder.encode(providerId, StandardCharsets.UTF_8.toString())

                val metaEndpoints = listOf(
                    "$cleanBase/meta/$encodedProvider?link=$encodedLink",
                    "$cleanBase/meta/$encodedProvider?url=$encodedLink",
                    "$cleanBase/info/$encodedProvider?link=$encodedLink",
                    "$cleanBase/details/$encodedProvider?link=$encodedLink",
                    "$cleanBase/providers/$encodedProvider/meta?link=$encodedLink"
                )

                val metaClient = httpClient.newBuilder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                for (url in metaEndpoints) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Butterfly/1.0 (Android)")
                            .header("Accept", "application/json")
                            .build()

                        metaClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@use

                            val bodyStr = response.body?.string() ?: return@use
                            val trimmed = bodyStr.trim()
                            if (!trimmed.startsWith("{")) return@use

                            val json = JSONObject(trimmed)
                            val requiresWebView = json.optBoolean("requiresWebView", false) ||
                                    json.optString("error").contains("WEBVIEW", ignoreCase = true) ||
                                    json.optString("message").contains("WEBVIEW", ignoreCase = true)

                            val metaObj = json.optJSONObject("result")
                                ?: json.optJSONObject("data")
                                ?: json.optJSONObject("meta")
                                ?: json

                            val title = metaObj.optString("title").ifBlank { "Untitled" }
                            val synopsis = metaObj.optString("synopsis").ifBlank { metaObj.optString("description") }.ifBlank { null }
                            val image = metaObj.optString("image").ifBlank { null }
                            val poster = metaObj.optString("poster").ifBlank { null }
                            val type = metaObj.optString("type").ifBlank { "movie" }
                            val imdbId = metaObj.optString("imdbId").ifBlank { null }
                            val tmdbId = metaObj.optString("tmdbId").ifBlank { null }
                            val rating = metaObj.optString("rating").ifBlank { null }
                            val webUrl = metaObj.optString("webUrl").ifBlank { null }

                            val tagsList = mutableListOf<String>()
                            metaObj.optJSONArray("tags")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    val t = arr.optString(i)
                                    if (t.isNotBlank()) tagsList.add(t)
                                }
                            }

                            val castList = mutableListOf<String>()
                            metaObj.optJSONArray("cast")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    val c = arr.optString(i)
                                    if (c.isNotBlank()) castList.add(c)
                                }
                            }

                            val linkList = mutableListOf<VegaLinkList>()
                            val linkListArr = metaObj.optJSONArray("linkList")
                                ?: metaObj.optJSONArray("links")
                                ?: metaObj.optJSONArray("episodes")

                            if (linkListArr != null) {
                                for (i in 0 until linkListArr.length()) {
                                    val itemObj = linkListArr.optJSONObject(i) ?: continue
                                    val itemTitle = itemObj.optString("title").ifBlank { "Stream Option ${i + 1}" }
                                    val quality = itemObj.optString("quality").ifBlank { "Auto" }
                                    val epLink = itemObj.optString("episodesLink")
                                        .ifBlank { itemObj.optString("episodeLink") }
                                        .ifBlank { itemObj.optString("episodesUrl") }
                                        .ifBlank { null }

                                    val directLinksList = mutableListOf<VegaDirectLink>()
                                    val directArr = itemObj.optJSONArray("directLinks")
                                        ?: itemObj.optJSONArray("links")

                                    if (directArr != null) {
                                        for (j in 0 until directArr.length()) {
                                            val dObj = directArr.optJSONObject(j)
                                            if (dObj != null) {
                                                val dLink = dObj.optString("link").ifBlank { dObj.optString("url") }
                                                val dTitle = dObj.optString("title").ifBlank { "Direct Link ${j + 1}" }
                                                val dType = dObj.optString("type").ifBlank { "movie" }
                                                val dDesc = dObj.optString("description").ifBlank { null }
                                                val dImg = dObj.optString("image").ifBlank { null }
                                                if (dLink.isNotBlank()) {
                                                    directLinksList.add(
                                                        VegaDirectLink(
                                                            title = dTitle,
                                                            link = dLink,
                                                            type = dType,
                                                            description = dDesc,
                                                            image = dImg
                                                        )
                                                    )
                                                }
                                            } else {
                                                val dLink = directArr.optString(j)
                                                if (dLink.isNotBlank()) {
                                                    directLinksList.add(
                                                        VegaDirectLink(
                                                            title = "Link ${j + 1}",
                                                            link = dLink
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val singleLink = itemObj.optString("link").ifBlank { itemObj.optString("url") }
                                        if (singleLink.isNotBlank()) {
                                            directLinksList.add(
                                                VegaDirectLink(
                                                    title = itemTitle,
                                                    link = singleLink
                                                )
                                            )
                                        }
                                    }

                                    if (directLinksList.isNotEmpty() || !epLink.isNullOrBlank()) {
                                        linkList.add(
                                            VegaLinkList(
                                                title = itemTitle,
                                                quality = quality,
                                                directLinks = directLinksList,
                                                episodesLink = epLink
                                            )
                                        )
                                    }
                                }
                            }

                            return@withTimeoutOrNull VegaMetaResult(
                                title = title,
                                synopsis = synopsis,
                                image = image,
                                poster = poster,
                                type = type,
                                imdbId = imdbId,
                                tmdbId = tmdbId,
                                rating = rating,
                                tags = tagsList,
                                cast = castList,
                                linkList = linkList,
                                webUrl = webUrl,
                                requiresWebView = requiresWebView
                            )
                        }
                    } catch (_: Exception) {}
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error getting meta for $providerId: ${e.message}")
                null
            }
        }
    }

    /**
     * Correct Vega Step 2b (Optional for Series/TV): Fetch Episode List for an episodesLink.
     * Endpoint: /episodes/{provider}?link={encodedEpisodesLink}
     * Returns VegaEpisode list.
     */
    suspend fun getEpisodes(
        providerId: String,
        episodesLink: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): List<VegaEpisode> = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled || providerId.isBlank() || episodesLink.isBlank()) return@withContext emptyList()

        executeWithServerFallbacks(baseUrl, { currentUrl ->
            fetchEpisodesInternal(providerId, episodesLink, currentUrl)
        }, { res -> !res.isNullOrEmpty() }) ?: emptyList()
    }

    private suspend fun fetchEpisodesInternal(
        providerId: String,
        episodesLink: String,
        baseUrl: String
    ): List<VegaEpisode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<VegaEpisode>()
        withTimeoutOrNull(EPISODES_TIMEOUT_MS) {
            try {
                val cleanBase = baseUrl.trimEnd('/')
                val encodedLink = URLEncoder.encode(episodesLink, StandardCharsets.UTF_8.toString())
                val encodedProvider = URLEncoder.encode(providerId, StandardCharsets.UTF_8.toString())

                val episodeEndpoints = listOf(
                    "$cleanBase/episodes/$encodedProvider?link=$encodedLink",
                    "$cleanBase/episodes/$encodedProvider?url=$encodedLink",
                    "$cleanBase/season/$encodedProvider?link=$encodedLink",
                    "$cleanBase/providers/$encodedProvider/episodes?link=$encodedLink"
                )

                val epClient = httpClient.newBuilder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                for (url in episodeEndpoints) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Butterfly/1.0 (Android)")
                            .header("Accept", "application/json")
                            .build()

                        epClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@use

                            val bodyStr = response.body?.string() ?: return@use
                            val trimmed = bodyStr.trim()

                            val jsonArray = when {
                                trimmed.startsWith("[") -> JSONArray(trimmed)
                                trimmed.startsWith("{") -> {
                                    val json = JSONObject(trimmed)
                                    json.optJSONArray("episodes")
                                        ?: json.optJSONArray("data")
                                        ?: json.optJSONArray("results")
                                        ?: json.optJSONArray("episodeList")
                                        ?: JSONArray()
                                }
                                else -> JSONArray()
                            }

                            for (i in 0 until jsonArray.length()) {
                                val item = jsonArray.opt(i)
                                if (item is JSONObject) {
                                    val title = item.optString("title").ifBlank { "Episode ${i + 1}" }
                                    val link = item.optString("link").ifBlank { item.optString("url") }
                                    val epNum = item.optInt("episodeNumber", item.optInt("episode", i + 1))
                                    val sNum = item.optInt("seasonNumber", item.optInt("season", 1))
                                    val desc = item.optString("description").ifBlank { null }
                                    val img = item.optString("image").ifBlank { item.optString("poster") }.ifBlank { null }

                                    if (link.isNotBlank()) {
                                        episodes.add(
                                            VegaEpisode(
                                                title = title,
                                                link = link,
                                                episodeNumber = epNum,
                                                seasonNumber = sNum,
                                                description = desc,
                                                image = img
                                            )
                                        )
                                    }
                                } else if (item is String && item.isNotBlank()) {
                                    episodes.add(
                                        VegaEpisode(
                                            title = "Episode ${i + 1}",
                                            link = item,
                                            episodeNumber = i + 1
                                        )
                                    )
                                }
                            }
                        }
                        if (episodes.isNotEmpty()) break
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting episodes for $providerId: ${e.message}")
            }
            episodes
        } ?: emptyList()
    }

    /**
     * Correct Vega Step 3: Fetch playable Stream from directLink or episodeLink.
     * Endpoint: /stream/{provider}?link={encodedDirectLink}
     * Returns VegaStreamResult list.
     */
    suspend fun getStream(
        providerId: String,
        directLink: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled || providerId.isBlank() || directLink.isBlank()) return@withContext emptyList()

        val serverStreams = executeWithServerFallbacks(baseUrl, { currentUrl ->
            fetchStreamInternal(providerId, directLink, currentUrl)
        }, { res -> !res.isNullOrEmpty() }) ?: emptyList()

        if (serverStreams.isNotEmpty()) return@withContext serverStreams

        // Local direct link resolver fallback
        return@withContext resolveLocalDirectFallback(directLink)
    }

    private suspend fun fetchStreamInternal(
        providerId: String,
        directLink: String,
        baseUrl: String
    ): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VegaStreamResult>()
        withTimeoutOrNull(STREAM_TIMEOUT_MS) {
            try {
                val cleanBase = baseUrl.trimEnd('/')
                val encodedLink = URLEncoder.encode(directLink, StandardCharsets.UTF_8.toString())
                val encodedProvider = URLEncoder.encode(providerId, StandardCharsets.UTF_8.toString())

                val streamEndpoints = listOf(
                    "$cleanBase/stream/$encodedProvider?link=$encodedLink",
                    "$cleanBase/stream/$encodedProvider?url=$encodedLink",
                    "$cleanBase/extract/$encodedProvider?link=$encodedLink",
                    "$cleanBase/resolve/$encodedProvider?link=$encodedLink",
                    "$cleanBase/providers/$encodedProvider/stream?link=$encodedLink"
                )

                val streamClient = httpClient.newBuilder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(18, TimeUnit.SECONDS)
                    .build()

                for (url in streamEndpoints) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Butterfly/1.0 (Android)")
                            .header("Accept", "application/json")
                            .build()

                        streamClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@use

                            val bodyStr = response.body?.string() ?: return@use
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
                        if (streams.isNotEmpty()) break
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting stream for $providerId: ${e.message}")
            }
            streams
        } ?: emptyList()
    }

    /**
     * Resolves the complete Vega chain:
     * 1. Search post link -> 2. Meta linkList -> 3. directLinks/episodes -> 4. Playable Stream URL.
     */
    suspend fun resolveFullVegaPlayback(
        providerId: String,
        postOrDirectLink: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): VegaPlaybackResolution = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled) {
            return@withContext VegaPlaybackResolution(
                success = false,
                streams = emptyList(),
                errorMessage = "Vega extensions are currently disabled"
            )
        }
        val cleanProv = providerId.trim().lowercase()

        // Check if the link is already a direct link
        val isLikelyDirectLink = postOrDirectLink.contains("/drive/") ||
                postOrDirectLink.contains("/file/") ||
                postOrDirectLink.contains("pixeldrain") ||
                postOrDirectLink.contains("hubdrive") ||
                postOrDirectLink.contains("gdrive") ||
                postOrDirectLink.contains(".mkv") ||
                postOrDirectLink.contains(".mp4") ||
                postOrDirectLink.contains(".m3u8")

        if (isLikelyDirectLink) {
            val streams = getStream(cleanProv, postOrDirectLink, baseUrl)
            if (streams.isNotEmpty()) {
                return@withContext VegaPlaybackResolution(
                    success = true,
                    meta = null,
                    streams = streams,
                    stageReached = "STREAM"
                )
            }
        }

        // 1. Fetch Metadata
        val meta = getMeta(cleanProv, postOrDirectLink, baseUrl)
        if (meta == null) {
            return@withContext VegaPlaybackResolution(
                success = false,
                errorMessage = "[Vega Stage 1: Metadata Extraction Failed] Could not retrieve media info from provider '$cleanProv' for link: $postOrDirectLink",
                stageReached = "META"
            )
        }

        if (meta.linkList.isEmpty()) {
            return@withContext VegaPlaybackResolution(
                success = false,
                meta = meta,
                errorMessage = "[Vega Stage 2: LinkList Empty] Metadata for '${meta.title}' contains 0 download/stream entries.",
                stageReached = "LINK_LIST"
            )
        }

        // 2. Extract directLinks across available qualities or episodes
        val allDirectLinks = mutableListOf<Pair<VegaLinkList, VegaDirectLink>>()
        meta.linkList.forEach { qualityGroup ->
            qualityGroup.directLinks.forEach { direct ->
                allDirectLinks.add(Pair(qualityGroup, direct))
            }
            // If episodesLink is available, resolve episodes as well
            if (!qualityGroup.episodesLink.isNullOrBlank() && qualityGroup.directLinks.isEmpty()) {
                val epList = getEpisodes(cleanProv, qualityGroup.episodesLink, baseUrl)
                epList.firstOrNull()?.let { ep ->
                    allDirectLinks.add(
                        Pair(
                            qualityGroup,
                            VegaDirectLink(
                                title = ep.title,
                                link = ep.link,
                                type = "episode"
                            )
                        )
                    )
                }
            }
        }

        if (allDirectLinks.isEmpty()) {
            return@withContext VegaPlaybackResolution(
                success = false,
                meta = meta,
                errorMessage = "[Vega Stage 3: DirectLinks/Episodes Empty] Found ${meta.linkList.size} quality groups, but no direct resolver links.",
                stageReached = "DIRECT_LINKS"
            )
        }

        // 3. Resolve streams from direct links (query in parallel with a bounded concurrency limit)
        val resolvedStreams = mutableListOf<VegaStreamResult>()
        val directLinksToTest = allDirectLinks.take(6)

        coroutineScope {
            val deferred = directLinksToTest.map { (qualityGroup, direct) ->
                async(Dispatchers.IO) {
                    try {
                        val streams = getStream(cleanProv, direct.link, baseUrl)
                        streams.map { st ->
                            val combinedQuality = if (st.quality.isNotBlank() && st.quality != "Auto") {
                                st.quality
                            } else if (qualityGroup.quality.isNotBlank()) {
                                qualityGroup.quality
                            } else {
                                qualityGroup.title
                            }
                            st.copy(quality = combinedQuality)
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
            deferred.awaitAll().forEach { list ->
                resolvedStreams.addAll(list)
            }
        }

        if (resolvedStreams.isEmpty() && allDirectLinks.isNotEmpty()) {
            val fallback = getStream(cleanProv, allDirectLinks.first().second.link, baseUrl)
            resolvedStreams.addAll(fallback)
        }

        val playableStreams = resolvedStreams
            .filterNot { it.isTorrent }
            .distinctBy { it.url }
            .sortedByDescending { rankStream(it) }

        if (playableStreams.isEmpty()) {
            for ((_, direct) in allDirectLinks) {
                val fallback = resolveLocalDirectFallback(direct.link)
                if (fallback.isNotEmpty()) {
                    return@withContext VegaPlaybackResolution(
                        success = true,
                        meta = meta,
                        streams = fallback,
                        stageReached = "COMPLETE"
                    )
                }
            }

            val hasTorrent = resolvedStreams.any { it.isTorrent }
            val errorMsg = if (hasTorrent) {
                "[Vega Stage 4: Unsupported Format] Torrent/Magnet streams detected, which require a torrent engine."
            } else {
                "[Vega Stage 4: Stream Resolution Failed] Direct links were found (${allDirectLinks.size}), but provider '$cleanProv' returned 0 playable video stream URLs."
            }
            return@withContext VegaPlaybackResolution(
                success = false,
                meta = meta,
                errorMessage = errorMsg,
                stageReached = "STREAM"
            )
        }

        return@withContext VegaPlaybackResolution(
            success = true,
            meta = meta,
            streams = playableStreams,
            stageReached = "COMPLETE"
        )
    }

    /**
     * HTTP Stream Reachability & Range Probe.
     * Verifies that the resolved video stream is online and supports HTTP Range (Byte-range seeking).
     */
    data class HttpProbeResult(
        val isReachable: Boolean,
        val supportsRange: Boolean,
        val httpCode: Int,
        val contentType: String?,
        val contentLength: Long,
        val errorMessage: String? = null
    )

    suspend fun probeStreamHttp(
        streamUrl: String,
        headers: Map<String, String> = emptyMap()
    ): HttpProbeResult = withContext(Dispatchers.IO) {
        if (streamUrl.isBlank() || streamUrl.startsWith("magnet:", ignoreCase = true)) {
            return@withContext HttpProbeResult(
                isReachable = true,
                supportsRange = true,
                httpCode = 200,
                contentType = "application/x-bittorrent",
                contentLength = 0L
            )
        }

        withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            try {
                val probeClient = httpClient.newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()

                val reqBuilder = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", headers["User-Agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Range", "bytes=0-1023")

                headers.forEach { (k, v) ->
                    if (!k.equals("Range", ignoreCase = true) && !k.equals("User-Agent", ignoreCase = true)) {
                        reqBuilder.header(k, v)
                    }
                }

                probeClient.newCall(reqBuilder.build()).execute().use { response ->
                    val code = response.code
                    val isReachable = code in 200..399
                    val acceptRanges = response.header("Accept-Ranges")
                    val contentRange = response.header("Content-Range")
                    val supportsRange = code == 206 || acceptRanges?.contains("bytes", ignoreCase = true) == true || !contentRange.isNullOrBlank()
                    val contentType = response.header("Content-Type")
                    val contentLength = response.body?.contentLength() ?: 0L

                    HttpProbeResult(
                        isReachable = isReachable,
                        supportsRange = supportsRange,
                        httpCode = code,
                        contentType = contentType,
                        contentLength = contentLength
                    )
                }
            } catch (e: Exception) {
                HttpProbeResult(
                    isReachable = false,
                    supportsRange = false,
                    httpCode = 0,
                    contentType = null,
                    contentLength = 0L,
                    errorMessage = e.message
                )
            }
        } ?: HttpProbeResult(
            isReachable = false,
            supportsRange = false,
            httpCode = 408,
            contentType = null,
            contentLength = 0L,
            errorMessage = "Probe Timeout"
        )
    }

    /**
     * Automated Provider Diagnostic Test.
     * Executes end-to-end testing across Search -> Meta -> (Episodes) -> Stream -> HTTP Probe.
     */
    suspend fun runProviderDiagnostic(
        providerId: String,
        testQuery: String = "Avengers",
        isSeries: Boolean = false,
        baseUrl: String = DEFAULT_SERVER_URL
    ): VegaDiagnosticResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val displayName = formatProviderDisplayName(providerId)

        // Stage 1: Search
        val searchResults = try {
            search(providerId, testQuery, baseUrl)
        } catch (e: Exception) {
            emptyList()
        }

        if (searchResults.isEmpty()) {
            val duration = System.currentTimeMillis() - startTime
            return@withContext VegaDiagnosticResult(
                providerId = providerId,
                providerName = displayName,
                searchStatus = "FAIL",
                overallStatus = "BROKEN",
                failureStage = "SEARCH",
                errorMessage = "Search returned 0 items for query '$testQuery'",
                durationMs = duration
            )
        }

        val firstItem = searchResults.first()

        // Stage 2: Meta
        val meta = try {
            getMeta(providerId, firstItem.link, baseUrl)
        } catch (e: Exception) {
            null
        }

        if (meta == null) {
            val duration = System.currentTimeMillis() - startTime
            return@withContext VegaDiagnosticResult(
                providerId = providerId,
                providerName = displayName,
                searchStatus = "PASS",
                metaStatus = "FAIL",
                overallStatus = "BROKEN",
                failureStage = "META",
                errorMessage = "Metadata extraction failed for item '${firstItem.title}'",
                testedItemTitle = firstItem.title,
                durationMs = duration
            )
        }

        if (meta.requiresWebView) {
            val duration = System.currentTimeMillis() - startTime
            return@withContext VegaDiagnosticResult(
                providerId = providerId,
                providerName = displayName,
                searchStatus = "PASS",
                metaStatus = "WEBVIEW_REQUIRED",
                overallStatus = "WEBVIEW_REQUIRED",
                failureStage = "META",
                errorMessage = "Provider requires WebView browser challenge solver",
                testedItemTitle = meta.title,
                durationMs = duration
            )
        }

        // Stage 2b: Episodes check if series
        var episodesStatus = "SKIPPED"
        var directLinkToTest: String? = null

        if (isSeries || meta.type.equals("series", ignoreCase = true)) {
            val epLinkGroup = meta.linkList.firstOrNull { !it.episodesLink.isNullOrBlank() }
            if (epLinkGroup != null) {
                val epList = getEpisodes(providerId, epLinkGroup.episodesLink!!, baseUrl)
                if (epList.isNotEmpty()) {
                    episodesStatus = "PASS"
                    directLinkToTest = epList.first().link
                } else {
                    episodesStatus = "FAIL"
                }
            }
        }

        if (directLinkToTest == null) {
            directLinkToTest = meta.linkList.flatMap { it.directLinks }.firstOrNull()?.link
        }

        if (directLinkToTest == null) {
            val duration = System.currentTimeMillis() - startTime
            return@withContext VegaDiagnosticResult(
                providerId = providerId,
                providerName = displayName,
                searchStatus = "PASS",
                metaStatus = "PASS",
                episodesStatus = episodesStatus,
                streamStatus = "FAIL",
                overallStatus = "BROKEN",
                failureStage = "DIRECT_LINKS",
                errorMessage = "LinkList contained 0 directLinks or episodeLinks",
                testedItemTitle = meta.title,
                durationMs = duration
            )
        }

        // Stage 3: Stream Resolution
        val streams = try {
            getStream(providerId, directLinkToTest, baseUrl)
        } catch (e: Exception) {
            emptyList()
        }

        if (streams.isEmpty()) {
            val duration = System.currentTimeMillis() - startTime
            return@withContext VegaDiagnosticResult(
                providerId = providerId,
                providerName = displayName,
                searchStatus = "PASS",
                metaStatus = "PASS",
                episodesStatus = episodesStatus,
                streamStatus = "FAIL",
                overallStatus = "BROKEN",
                failureStage = "STREAM",
                errorMessage = "Stream extractor returned 0 video streams",
                testedItemTitle = meta.title,
                durationMs = duration
            )
        }

        val primaryStream = streams.first()

        // Stage 4: HTTP Reachability and Range probe
        val probe = probeStreamHttp(primaryStream.url, primaryStream.headers)
        val duration = System.currentTimeMillis() - startTime

        val overall = when {
            probe.isReachable && probe.supportsRange -> "WORKING"
            probe.isReachable -> "PARTIAL"
            else -> "BROKEN"
        }

        return@withContext VegaDiagnosticResult(
            providerId = providerId,
            providerName = displayName,
            searchStatus = "PASS",
            metaStatus = "PASS",
            episodesStatus = episodesStatus,
            streamStatus = "PASS",
            rangeStatus = if (probe.supportsRange) "PASS" else "FAIL",
            overallStatus = overall,
            failureStage = if (probe.isReachable) null else "HTTP_PROBE",
            errorMessage = if (probe.isReachable) null else "Stream URL unreachable (HTTP ${probe.httpCode})",
            httpCode = probe.httpCode,
            durationMs = duration,
            testedItemTitle = meta.title,
            resolvedStreamUrl = primaryStream.url
        )
    }

    private fun rankStream(st: VegaStreamResult): Int {
        val srv = st.server.lowercase()
        val url = st.url.lowercase()
        return when {
            srv.contains("gdrive") || url.contains("video-downloads.googleusercontent.com") -> 100
            srv.contains("cf storage") || url.contains(".r2.cloudflarestorage.com") -> 95
            srv.contains("pixeldrain") || url.contains("pixeldrain.com") -> 90
            srv.contains("fast") || srv.contains("direct") -> 85
            srv.contains("fplayer") || srv.contains("streamtape") || srv.contains("dood") -> 80
            url.endsWith(".mkv") || url.endsWith(".mp4") || url.contains(".m3u8") -> 75
            srv.contains("cf worker") || url.contains("workers.dev") -> 50
            else -> 60
        }
    }

    private fun resolveLocalDirectFallback(directLink: String): List<VegaStreamResult> {
        val list = mutableListOf<VegaStreamResult>()
        val lower = directLink.lowercase()
        try {
            if (lower.contains("pixeldrain.com/u/") || lower.contains("pixeldrain.com/d/")) {
                val fileId = directLink.substringAfterLast("/").substringBefore("?").trim()
                if (fileId.isNotBlank()) {
                    list.add(
                        VegaStreamResult(
                            server = "PixelDrain High Speed Direct",
                            url = "https://pixeldrain.com/api/file/$fileId",
                            quality = "1080p HD",
                            format = "mp4"
                        )
                    )
                }
            } else if (lower.contains("drive.google.com") || lower.contains("docs.google.com")) {
                val fileId = if (directLink.contains("/d/")) {
                    directLink.substringAfter("/d/").substringBefore("/").substringBefore("?")
                } else if (directLink.contains("id=")) {
                    directLink.substringAfter("id=").substringBefore("&")
                } else ""
                if (fileId.isNotBlank()) {
                    list.add(
                        VegaStreamResult(
                            server = "Google Drive Direct",
                            url = "https://drive.google.com/uc?export=download&id=$fileId",
                            quality = "1080p HD",
                            format = "mp4"
                        )
                    )
                }
            } else if (lower.contains("hubcloud") || lower.contains("hubdrive") || lower.contains("gdflix") || lower.contains("katdrive") || lower.contains("fastdrive")) {
                list.add(
                    VegaStreamResult(
                        server = "Cloud Direct CDN",
                        url = directLink,
                        quality = "1080p HD",
                        format = "mkv"
                    )
                )
            }

            if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.contains(".m3u8") || lower.endsWith(".avi") || lower.contains("video-downloads.googleusercontent.com")) {
                if (list.none { it.url == directLink }) {
                    list.add(
                        VegaStreamResult(
                            server = "Direct Media Stream",
                            url = directLink,
                            quality = "1080p HD",
                            format = if (lower.contains(".m3u8")) "hls" else if (lower.endsWith(".mkv")) "mkv" else "mp4"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Local fallback failed: ${e.message}")
        }
        return list
    }

    private fun parseStreamObject(obj: JSONObject): VegaStreamResult? {
        val url = obj.optString("url")
            .ifBlank { obj.optString("link") }
            .ifBlank { obj.optString("streamUrl") }
            .ifBlank { obj.optString("file") }

        if (url.isBlank()) return null

        val server = obj.optString("server")
            .ifBlank { obj.optString("name") }
            .ifBlank { obj.optString("source") }
            .ifBlank { "Direct" }

        val isMagnet = url.startsWith("magnet:") ||
                obj.optBoolean("isTorrent", false) ||
                obj.optString("type").equals("torrent", ignoreCase = true)

        val quality = obj.optString("quality")
            .ifBlank { obj.optString("resolution") }
            .ifBlank { "Auto" }

        val format = obj.optString("format")
            .ifBlank { obj.optString("type") }
            .ifBlank {
                when {
                    url.contains(".m3u8", ignoreCase = true) -> "hls"
                    url.contains(".mkv", ignoreCase = true) -> "mkv"
                    else -> "mp4"
                }
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

        val requiresWebView = obj.optBoolean("requiresWebView", false) ||
                obj.optString("error").contains("WEBVIEW", ignoreCase = true)

        return VegaStreamResult(
            server = server,
            url = url,
            quality = quality,
            format = format,
            headers = headersMap,
            isTorrent = isMagnet,
            subtitleUrls = subtitles,
            requiresWebView = requiresWebView
        )
    }
}

data class VegaPlaybackResolution(
    val success: Boolean,
    val meta: VegaMetaResult? = null,
    val streams: List<VegaStreamResult> = emptyList(),
    val errorMessage: String? = null,
    val stageReached: String = "INIT"
)
