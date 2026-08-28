package com.example.vega

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

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
            "world4u" -> "World4UFree"
            "zeefliz" -> "ZeeFliz"
            "eonmovies" -> "EonMovies"
            "cinefreak" -> "CineFreak"
            "showbox" -> "ShowBox"
            "gokuhd" -> "GokuHD"
            "flixhq" -> "FlixHQ"
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
        val cleanProv = providerId.trim().lowercase()
        val allResults = mutableListOf<VegaSearchResult>()

        // Diverse search queries covering movies, series, anime, and general keywords
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
            // Sequential fallback with additional terms and catalog/posts endpoints
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
        if (providerId.isBlank()) return@withContext emptyList()
        val cleanProv = providerId.trim().lowercase()
        val cleanQuery = query.trim()

        // 1. Primary search with given query
        var results = searchSingleQuery(cleanProv, cleanQuery.ifBlank { "2024" }, baseUrl)
        if (results.isNotEmpty()) return@withContext results

        // 2. Query expansion fallback if original search returned empty (capped fast fallback)
        val fallbackTerms = listOf("a", "movie")
            .filterNot { it.equals(cleanQuery, ignoreCase = true) }

        for (term in fallbackTerms) {
            results = searchSingleQuery(cleanProv, term, baseUrl)
            if (results.isNotEmpty()) {
                Log.d(TAG, "Search for '$cleanProv' succeeded with fallback term '$term' (${results.size} items)")
                return@withContext results
            }
        }

        return@withContext results
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

        // Endpoints to test: /search, /posts, /catalog
        val endpointsToTest = listOf(
            "$cleanBase/search/$encodedProvider?q=$encodedQuery",
            "$cleanBase/posts/$encodedProvider?page=1",
            "$cleanBase/catalog/$encodedProvider?page=1"
        )

        val queryClient = httpClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
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
     * Returns VegaMetaResult with Info.linkList and directLinks.
     */
    suspend fun getMeta(
        providerId: String,
        link: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): VegaMetaResult? = withContext(Dispatchers.IO) {
        if (providerId.isBlank() || link.isBlank()) return@withContext null

        try {
            val cleanBase = baseUrl.trimEnd('/')
            val encodedLink = URLEncoder.encode(link, StandardCharsets.UTF_8.toString())
            val encodedProvider = URLEncoder.encode(providerId, StandardCharsets.UTF_8.toString())
            val url = "$cleanBase/meta/$encodedProvider?link=$encodedLink"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly/1.0 (Android)")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Meta fetch failed for $providerId (code: ${response.code})")
                    return@withContext null
                }

                val bodyStr = response.body?.string() ?: return@withContext null
                val trimmed = bodyStr.trim()
                if (!trimmed.startsWith("{")) return@withContext null

                val json = JSONObject(trimmed)
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
                            // Single direct link on item itself
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

                        if (directLinksList.isNotEmpty()) {
                            linkList.add(
                                VegaLinkList(
                                    title = itemTitle,
                                    quality = quality,
                                    directLinks = directLinksList
                                )
                            )
                        }
                    }
                }

                return@withContext VegaMetaResult(
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
                    webUrl = webUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting meta for $providerId: ${e.message}")
        }
        return@withContext null
    }

    /**
     * Correct Vega Step 3: Fetch playable Stream from directLink.
     * Endpoint: /stream/{provider}?link={encodedDirectLink}
     * Returns VegaStreamResult list.
     */
    suspend fun getStream(
        providerId: String,
        directLink: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VegaStreamResult>()
        if (providerId.isBlank() || directLink.isBlank()) return@withContext streams

        try {
            val cleanBase = baseUrl.trimEnd('/')
            val encodedLink = URLEncoder.encode(directLink, StandardCharsets.UTF_8.toString())
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

    /**
     * Resolves the complete Vega chain:
     * 1. Search post link -> 2. Meta linkList -> 3. directLinks -> 4. Playable Stream URL.
     */
    suspend fun resolveFullVegaPlayback(
        providerId: String,
        postOrDirectLink: String,
        baseUrl: String = DEFAULT_SERVER_URL
    ): VegaPlaybackResolution = withContext(Dispatchers.IO) {
        val cleanProv = providerId.trim().lowercase()

        // Check if the link is already a direct link (e.g. hubcloud.cx/drive, pixeldrain, etc.)
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

        // 2. Extract directLinks across available qualities
        val allDirectLinks = mutableListOf<Pair<VegaLinkList, VegaDirectLink>>()
        meta.linkList.forEach { qualityGroup ->
            qualityGroup.directLinks.forEach { direct ->
                allDirectLinks.add(Pair(qualityGroup, direct))
            }
        }

        if (allDirectLinks.isEmpty()) {
            return@withContext VegaPlaybackResolution(
                success = false,
                meta = meta,
                errorMessage = "[Vega Stage 3: DirectLinks Empty] Found ${meta.linkList.size} quality groups, but no direct resolver links.",
                stageReached = "DIRECT_LINKS"
            )
        }

        // 3. Resolve streams from direct links (query directLinks in parallel with a concurrency limit)
        val resolvedStreams = mutableListOf<VegaStreamResult>()
        val directLinksToTest = allDirectLinks.take(6) // Test top directLinks (e.g. 1080p, 720p, 480p)

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

        if (resolvedStreams.isEmpty()) {
            // Try one more direct link sequentially if parallel had any network timeout
            if (allDirectLinks.isNotEmpty()) {
                val fallback = getStream(cleanProv, allDirectLinks.first().second.link, baseUrl)
                resolvedStreams.addAll(fallback)
            }
        }

        val playableStreams = resolvedStreams
            .filterNot { it.isTorrent }
            .distinctBy { it.url }
            .sortedByDescending { rankStream(it) }

        if (playableStreams.isEmpty()) {
            // Check if any direct link can be extracted via local fallback
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
                            server = "PixelDrain Direct",
                            url = "https://pixeldrain.com/api/file/$fileId",
                            quality = "1080p",
                            format = "mp4"
                        )
                    )
                }
            }
            if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.contains(".m3u8")) {
                list.add(
                    VegaStreamResult(
                        server = "Direct Stream",
                        url = directLink,
                        quality = "HD",
                        format = if (lower.contains(".m3u8")) "hls" else if (lower.endsWith(".mkv")) "mkv" else "mp4"
                    )
                )
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

        return VegaStreamResult(
            server = server,
            url = url,
            quality = quality,
            format = format,
            headers = headersMap,
            isTorrent = isMagnet,
            subtitleUrls = subtitles
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
