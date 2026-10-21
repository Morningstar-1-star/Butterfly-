package com.example.vega

import android.util.Log
import com.example.util.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Pure REST client for Butterfly-MediaServer (https://github.com/Morningstar-1-star/Butterfly-MediaServer).
 * Uses canonical Butterfly-MediaServer routes only:
 * - GET /health
 * - GET /providers
 * - GET /search/:provider?q=<query>&page=<page>
 * - GET /meta/:provider?link=<url>
 * - GET /episodes/:provider?url=<episodesLink>
 * - GET /stream/:provider?link=<url>&type=<movie|series>
 * - GET /resolve/:provider?link=<postPageUrl>&type=<movie|series>&episodeIndex=0&tryAll=true
 * - GET /diagnose?q=<query>
 */
object VegaProviderClient {
    private const val TAG = "VegaProviderClient"

    const val DEFAULT_SERVER_URL = "https://butterfly-mediaserver.onrender.com"

    @Volatile
    var isVegaGloballyEnabled: Boolean = true

    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getDefaultServerUrl(): String {
        return AppConfig.getVegaServerUrl().trimEnd('/').ifBlank { DEFAULT_SERVER_URL }
    }

    fun formatProviderDisplayName(id: String): String {
        val registered = VegaProviderRegistry.getProviderInfo(id)
        if (registered != null && registered.displayName.isNotBlank()) {
            return registered.displayName
        }
        return id.replace(Regex("([a-z])([A-Z0-9])"), "$1 $2")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    suspend fun checkServerHealth(serverUrl: String = getDefaultServerUrl()): Boolean = withContext(Dispatchers.IO) {
        val base = serverUrl.trimEnd('/')
        if (base.isBlank()) return@withContext false
        try {
            val req = Request.Builder()
                .url("$base/health")
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getAvailableProviders(serverUrl: String = getDefaultServerUrl()): List<String> = withContext(Dispatchers.IO) {
        val base = serverUrl.trimEnd('/')
        if (base.isNotBlank()) {
            try {
                val request = Request.Builder()
                    .url("$base/providers")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val root = JSONObject(body)
                        val arr = root.optJSONArray("providers")
                        if (arr != null && arr.length() > 0) {
                            val list = mutableListOf<String>()
                            for (i in 0 until arr.length()) {
                                val item = arr.opt(i)
                                when (item) {
                                    is JSONObject -> {
                                        val id = item.optString("id").trim()
                                        if (id.isNotBlank()) list.add(id)
                                    }
                                    is String -> {
                                        if (item.isNotBlank()) list.add(item.trim())
                                    }
                                }
                            }
                            if (list.isNotEmpty()) {
                                VegaProviderRegistry.updateProvidersFromServer(arr)
                                return@withContext list
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Butterfly-MediaServer /providers fallback to cached registry: ${e.message}")
            }
        }
        VegaProviderRegistry.getAllProviderIds()
    }

    suspend fun getHomeContent(
        providerId: String,
        serverUrl: String = getDefaultServerUrl()
    ): List<VegaSearchResult> = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled) return@withContext emptyList()
        val base = serverUrl.trimEnd('/')
        if (base.isBlank()) return@withContext emptyList()

        // Use Butterfly-MediaServer canonical /search/:provider route with current year catalog query
        search(providerId, "2025", base, 1).ifEmpty {
            search(providerId, "2024", base, 1)
        }
    }

    suspend fun search(
        providerId: String,
        query: String,
        serverUrl: String = getDefaultServerUrl(),
        page: Int = 1
    ): List<VegaSearchResult> = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled || query.isBlank()) return@withContext emptyList()
        val base = serverUrl.trimEnd('/')
        if (base.isBlank()) return@withContext emptyList()

        try {
            val encProv = URLEncoder.encode(providerId.trim(), StandardCharsets.UTF_8.name())
            val encQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
            val url = "$base/search/$encProv?q=$encQuery&page=$page"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    return@withContext parseSearchItems(body, providerId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Butterfly-MediaServer search($providerId, $query) failed: ${e.message}")
        }
        emptyList()
    }

    suspend fun searchAllProviders(
        query: String,
        providerIds: List<String>,
        serverUrl: String = getDefaultServerUrl()
    ): List<VegaSearchResult> = coroutineScope {
        if (!isVegaGloballyEnabled || query.isBlank() || providerIds.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(4)
        providerIds.take(8).map { id ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    search(id, query, serverUrl)
                }
            }
        }.awaitAll().flatten()
    }

    suspend fun getMeta(
        providerId: String,
        link: String,
        serverUrl: String = getDefaultServerUrl()
    ): VegaMetaResult? = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled || link.isBlank()) return@withContext null
        val base = serverUrl.trimEnd('/')
        if (base.isBlank()) return@withContext null

        try {
            val encProv = URLEncoder.encode(providerId.trim(), StandardCharsets.UTF_8.name())
            val encLink = URLEncoder.encode(link.trim(), StandardCharsets.UTF_8.name())
            val url = "$base/meta/$encProv?link=$encLink"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@withContext null
                    val root = JSONObject(body)
                    val metaObj = root.optJSONObject("result")
                        ?: root.optJSONObject("metadata")
                        ?: root
                    return@withContext parseMetaObject(metaObj)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Butterfly-MediaServer getMeta($providerId) failed: ${e.message}")
        }
        null
    }

    suspend fun getEpisodes(
        providerId: String,
        episodesUrl: String,
        serverUrl: String = getDefaultServerUrl()
    ): List<VegaEpisode> = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled || episodesUrl.isBlank()) return@withContext emptyList()
        val base = serverUrl.trimEnd('/')
        if (base.isBlank()) return@withContext emptyList()

        try {
            val encProv = URLEncoder.encode(providerId.trim(), StandardCharsets.UTF_8.name())
            val encUrl = URLEncoder.encode(episodesUrl.trim(), StandardCharsets.UTF_8.name())
            val url = "$base/episodes/$encProv?url=$encUrl"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    val root = JSONObject(body)
                    val arr = root.optJSONArray("episodes") ?: return@withContext emptyList()
                    val list = mutableListOf<VegaEpisode>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val epLink = obj.optString("link").ifBlank { obj.optString("url") }.trim()
                        if (epLink.isBlank()) continue
                        val epTitle = obj.optString("title").ifBlank { "Episode ${i + 1}" }
                        val epNum = Regex("""(?i)(?:e|ep|episode)\s*(\d+)""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull() ?: (i + 1)
                        list.add(
                            VegaEpisode(
                                title = epTitle,
                                link = epLink,
                                episodeNumber = epNum
                            )
                        )
                    }
                    return@withContext list
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Butterfly-MediaServer getEpisodes($providerId) failed: ${e.message}")
        }
        emptyList()
    }

    suspend fun getStream(
        providerId: String,
        link: String,
        serverUrl: String = getDefaultServerUrl(),
        type: String = "movie"
    ): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled || link.isBlank()) return@withContext emptyList()
        val base = serverUrl.trimEnd('/')
        if (base.isBlank()) return@withContext emptyList()

        try {
            val encProv = URLEncoder.encode(providerId.trim(), StandardCharsets.UTF_8.name())
            val encLink = URLEncoder.encode(link.trim(), StandardCharsets.UTF_8.name())
            val encType = URLEncoder.encode(type, StandardCharsets.UTF_8.name())
            val url = "$base/stream/$encProv?link=$encLink&type=$encType"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    val root = JSONObject(body)
                    val streamsArr = when {
                        root.has("streams") && root.opt("streams") is JSONArray -> root.optJSONArray("streams")
                        root.has("streams") && root.opt("streams") is JSONObject -> root.optJSONObject("streams")?.optJSONArray("streams")
                        else -> null
                    } ?: return@withContext emptyList()
                    return@withContext parseStreamsArray(streamsArr)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Butterfly-MediaServer getStream($providerId) failed: ${e.message}")
        }
        emptyList()
    }

    /**
     * Resolves a full playback pipeline via Butterfly-MediaServer's canonical routes:
     * - If given a search query, searches `/search/:provider?q=` first to get the post page URL.
     * - Calls `/resolve/:provider?link=<postPageUrl>&episodeIndex=<episodeIndex>&tryAll=true`
     * - Falls back to `/meta/:provider` -> `/episodes/:provider` -> `/stream/:provider` if needed.
     */
    suspend fun resolveFullVegaPlayback(
        providerId: String,
        linkOrQuery: String,
        serverUrl: String = getDefaultServerUrl(),
        episodeIndex: Int = 0
    ): VegaPlaybackResolution = withContext(Dispatchers.IO) {
        if (!isVegaGloballyEnabled) {
            return@withContext VegaPlaybackResolution(
                success = false,
                stageReached = "DISABLED",
                errorMessage = "Butterfly-MediaServer providers are disabled"
            )
        }
        val base = serverUrl.trimEnd('/')
        val cleanInput = linkOrQuery.trim()
        val encProv = URLEncoder.encode(providerId.trim(), StandardCharsets.UTF_8.name())

        // 1. If input is a search query rather than a post page URL, resolve the post page link via /search/:provider first
        val targetLink = if (!cleanInput.startsWith("http://", ignoreCase = true) && !cleanInput.startsWith("https://", ignoreCase = true)) {
            val searchHits = search(providerId, cleanInput, base)
            searchHits.firstOrNull()?.link ?: return@withContext VegaPlaybackResolution(
                success = false,
                stageReached = "SEARCH_EMPTY",
                errorMessage = "No search results found on $providerId for '$cleanInput'"
            )
        } else {
            cleanInput
        }

        // 2. Call Butterfly-MediaServer's canonical /resolve/:provider?link=<postPageUrl>
        try {
            val encLink = URLEncoder.encode(targetLink, StandardCharsets.UTF_8.name())
            val resolveUrl = "$base/resolve/$encProv?link=$encLink&episodeIndex=$episodeIndex&tryAll=true"
            val req = Request.Builder()
                .url(resolveUrl)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: "{}"
                val root = JSONObject(body)
                if (resp.isSuccessful && root.optBoolean("success", false)) {
                    val streamsArr = root.optJSONArray("streams") ?: JSONArray()
                    val streams = parseStreamsArray(streamsArr)
                    if (streams.isNotEmpty()) {
                        val meta = VegaMetaResult(
                            title = root.optString("title", cleanInput),
                            synopsis = root.optString("sourceTitle", ""),
                            image = null,
                            poster = null,
                            imdbId = "",
                            type = root.optString("type", "movie"),
                            linkList = emptyList()
                        )
                        return@withContext VegaPlaybackResolution(
                            success = true,
                            streams = streams,
                            meta = meta,
                            stageReached = "STREAM_FOUND"
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Butterfly-MediaServer /resolve/$providerId note: ${e.message}")
        }

        // 3. Fallback: If targetLink is already a direct stream-source candidate URL, try /stream/:provider directly
        try {
            val directStreams = getStream(providerId, targetLink, base)
            if (directStreams.isNotEmpty()) {
                return@withContext VegaPlaybackResolution(
                    success = true,
                    streams = directStreams,
                    stageReached = "DIRECT_STREAM"
                )
            }

            // 4. Step-by-step /meta/:provider -> /episodes/:provider -> /stream/:provider
            val meta = getMeta(providerId, targetLink, base)
                ?: return@withContext VegaPlaybackResolution(
                    success = false,
                    stageReached = "META_EMPTY",
                    errorMessage = "Butterfly-MediaServer returned empty metadata for $providerId"
                )

            val candidateLinks = mutableListOf<Pair<String, String>>()
            for (group in meta.linkList) {
                for (dl in group.directLinks) {
                    if (dl.link.isNotBlank()) {
                        candidateLinks.add(dl.link to (dl.type.ifBlank { meta.type.ifBlank { "movie" } }))
                    }
                }
                if (!group.episodesLink.isNullOrBlank()) {
                    val episodes = getEpisodes(providerId, group.episodesLink, base)
                    val selectedEp = episodes.getOrNull(episodeIndex) ?: episodes.firstOrNull()
                    if (selectedEp != null && selectedEp.link.isNotBlank()) {
                        candidateLinks.add(selectedEp.link to "series")
                    }
                }
            }

            for ((candUrl, candType) in candidateLinks.take(3)) {
                val streams = getStream(providerId, candUrl, base, candType)
                if (streams.isNotEmpty()) {
                    return@withContext VegaPlaybackResolution(
                        success = true,
                        streams = streams,
                        meta = meta,
                        stageReached = "STREAM_FOUND"
                    )
                }
            }

            VegaPlaybackResolution(
                success = false,
                meta = meta,
                stageReached = "STREAM_NOT_FOUND",
                errorMessage = "No playable streams returned by Butterfly-MediaServer for ${meta.title}"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VegaPlaybackResolution(
                success = false,
                stageReached = "EXCEPTION",
                errorMessage = e.message ?: "Error communicating with Butterfly-MediaServer"
            )
        }
    }

    private fun parseSearchItems(jsonStr: String, providerId: String): List<VegaSearchResult> {
        val list = mutableListOf<VegaSearchResult>()
        try {
            val root = JSONObject(jsonStr)
            val dataArr = when {
                root.has("results") && root.opt("results") is JSONArray -> root.optJSONArray("results")
                root.has("results") && root.opt("results") is JSONObject -> {
                    val resObj = root.optJSONObject("results")
                    resObj?.optJSONArray("posts")
                        ?: resObj?.optJSONArray("results")
                        ?: resObj?.optJSONArray("items")
                        ?: resObj?.optJSONArray("data")
                }
                root.has("data") && root.opt("data") is JSONArray -> root.optJSONArray("data")
                root.has("data") && root.opt("data") is JSONObject -> root.optJSONObject("data")?.optJSONArray("posts")
                root.has("posts") && root.opt("posts") is JSONArray -> root.optJSONArray("posts")
                else -> null
            } ?: return emptyList()

            for (i in 0 until dataArr.length()) {
                val obj = dataArr.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                val link = obj.optString("link").ifBlank { obj.optString("url") }.trim()
                val image = obj.optString("image").ifBlank { obj.optString("poster") }.trim().ifBlank { null }
                if (title.isNotBlank() && link.isNotBlank()) {
                    list.add(
                        VegaSearchResult(
                            id = "${providerId}_$i",
                            title = title,
                            link = link,
                            imageUrl = image,
                            providerId = providerId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing search items for $providerId: ${e.message}")
        }
        return list
    }

    private fun parseMetaObject(metaObj: JSONObject): VegaMetaResult {
        val title = metaObj.optString("title", "")
        val synopsis = metaObj.optString("synopsis").ifBlank { metaObj.optString("description", "") }
        val image = metaObj.optString("image").ifBlank { null }
        val poster = metaObj.optString("poster").ifBlank { image }
        val imdbId = metaObj.optString("imdbId", "")
        val type = metaObj.optString("type", "movie")

        val groups = mutableListOf<VegaLinkList>()
        val linkListArr = metaObj.optJSONArray("linkList")
        if (linkListArr != null) {
            for (i in 0 until linkListArr.length()) {
                val grp = linkListArr.optJSONObject(i) ?: continue
                val grpTitle = grp.optString("title", "Links")
                val quality = grp.optString("quality", "")
                val epLink = grp.optString("episodesLink").ifBlank { null }
                val directLinks = mutableListOf<VegaDirectLink>()
                val dlArr = grp.optJSONArray("directLinks")
                if (dlArr != null) {
                    for (j in 0 until dlArr.length()) {
                        val dl = dlArr.optJSONObject(j) ?: continue
                        val dlLink = dl.optString("link").ifBlank { dl.optString("url") }.trim()
                        if (dlLink.isNotBlank()) {
                            directLinks.add(
                                VegaDirectLink(
                                    title = dl.optString("title", grpTitle),
                                    link = dlLink,
                                    type = dl.optString("type", type)
                                )
                            )
                        }
                    }
                }
                groups.add(
                    VegaLinkList(
                        title = grpTitle,
                        quality = quality,
                        directLinks = directLinks,
                        episodesLink = epLink
                    )
                )
            }
        }

        return VegaMetaResult(
            title = title,
            synopsis = synopsis,
            image = image,
            poster = poster,
            imdbId = imdbId,
            type = type,
            linkList = groups
        )
    }

    private fun parseStreamsArray(streamsArr: JSONArray): List<VegaStreamResult> {
        val list = mutableListOf<VegaStreamResult>()
        for (i in 0 until streamsArr.length()) {
            val st = streamsArr.optJSONObject(i) ?: continue
            val streamUrl = st.optString("link")
                .ifBlank { st.optString("url") }
                .ifBlank { st.optString("file") }
                .ifBlank { st.optString("streamUrl") }
                .ifBlank { st.optString("m3u8") }
                .trim()
            if (streamUrl.isBlank()) continue

            val server = st.optString("server", "Butterfly Server")
            val rawType = st.optString("type", "").lowercase()
            val format = when {
                rawType.contains("m3u8") || rawType.contains("hls") || streamUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
                rawType.contains("mpd") || rawType.contains("dash") || streamUrl.contains(".mpd", ignoreCase = true) -> "mpd"
                rawType.contains("mkv") || streamUrl.contains(".mkv", ignoreCase = true) -> "mkv"
                else -> "mp4"
            }
            val quality = st.optString("quality", "1080p").ifBlank { "1080p" }

            val headersMap = mutableMapOf<String, String>()
            val headersObj = st.optJSONObject("headers")
            if (headersObj != null) {
                val keys = headersObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = headersObj.optString(k)
                    if (v.isNotBlank()) headersMap[k] = v
                }
            }

            val subs = mutableListOf<String>()
            val subsArr = st.optJSONArray("subtitles")
            if (subsArr != null) {
                for (s in 0 until subsArr.length()) {
                    val subItem = subsArr.opt(s)
                    when (subItem) {
                        is JSONObject -> {
                            val u = subItem.optString("url").ifBlank { subItem.optString("link") }.trim()
                            if (u.isNotBlank()) subs.add(u)
                        }
                        is String -> if (subItem.isNotBlank()) subs.add(subItem.trim())
                    }
                }
            }

            list.add(
                VegaStreamResult(
                    server = server,
                    url = streamUrl,
                    format = format,
                    quality = quality,
                    headers = headersMap,
                    subtitleUrls = subs
                )
            )
        }
        return list
    }
}
