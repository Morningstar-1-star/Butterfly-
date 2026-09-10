package com.example.extractor

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.example.extractor.sextb.SextbError
import com.example.extractor.sextb.SextbException
import com.example.extractor.sextb.SextbNetwork
import com.example.extractor.sextb.SextbParser
import com.example.extractor.sextb.SextbResolver
import com.example.extractor.sextb.SextbVideoDetails
import com.example.extractor.sextb.SextbWebViewFallback
import com.example.extractor.sextb.VideoSource
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.util.SecureDnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Authoritative Native SEXТB Source / Provider for Butterfly.
 *
 * Implements the real upstream CloudStream 18+ pipeline:
 * 1. Category/Catalog: `/uncensored/pg-{page}`, `/censored/pg-{page}`, etc.
 * 2. Search: `/search/{query}/pg-{page}`
 * 3. Scoped card parsing: `.tray-item`, `.tray-item-title`, `.tray-item-thumbnail`
 * 4. Video Details: `.episode-list .btn-player` (data-id & data-source)
 * 5. Player Resolution: POST `/ajax/player` → iframe → Stbturbo (`#video_player[data-hash]`) → HLS
 * 6. Butterfly ExoPlayer integration.
 *
 * No fake mirrors or unrequested cross-provider fallbacks.
 */
object SextbProvider {

    private const val TAG = "SextbProvider"
    const val PROVIDER_ID = "sextb"
    const val DISPLAY_NAME = "SEXТB"

    const val DEFAULT_BASE_URL = "https://sextb.net"

    private val httpClient get() = SextbNetwork.httpClient

    // Safe In-memory Caching: Metadata & Search ONLY (Stream URLs are NEVER cached)
    private val searchCache = LruCache<String, List<VideoItem>>(50)
    private val detailsCache = LruCache<String, SextbVideoDetails>(100)
    private val knownPageUrlMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun registerPageUrl(idOrSlug: String, pageUrl: String) {
        if (idOrSlug.isNotBlank() && pageUrl.isNotBlank() && pageUrl.startsWith("http")) {
            knownPageUrlMap[idOrSlug.trim()] = pageUrl.trim()
            val slug = SextbParser.extractVideoIdFromUrl(pageUrl)
            if (slug.isNotBlank()) {
                knownPageUrlMap[slug] = pageUrl.trim()
            }
        }
    }

    /**
     * 1. search(query)
     * Upstream: GET https://sextb.net/search/{query}/pg-{page}
     */
    suspend fun search(
        query: String,
        limit: Int = 20,
        page: Int = 1,
        context: Context? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val cacheKey = "search:$cleanQuery:page:$page:limit:$limit"
        searchCache.get(cacheKey)?.let {
            Log.d(TAG, "SEXТB search: Serving from cache for '$cleanQuery'")
            return@withContext it
        }

        Log.i(TAG, "SEXТB search: Querying '$cleanQuery' (page $page)")
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
        val searchUrl = "$DEFAULT_BASE_URL/search/$encodedQuery/pg-$page"

        // 1. Native HTTP request
        try {
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", SextbResolver.DEFAULT_UA)
                .header("Referer", "$DEFAULT_BASE_URL/")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val items = SextbParser.parseSearchResults(html, DEFAULT_BASE_URL)
                if (items.isNotEmpty()) {
                    items.forEach { item ->
                        item.uploaderUrl?.let { registerPageUrl(item.id, it) }
                    }
                    val bounded = items.take(limit)
                    searchCache.put(cacheKey, bounded)
                    Log.i(TAG, "SEXТB search: Native HTTP found ${items.size} results for '$cleanQuery'")
                    return@withContext bounded
                }
            } else {
                Log.w(TAG, "SEXТB search: HTTP error ${resp.code} for $searchUrl")
            }
        } catch (e: Exception) {
            Log.w(TAG, "SEXТB search: HTTP exception: ${e.message}")
        }

        // 2. Controlled WebView catalog fallback if native HTTP failed (e.g. anti-bot challenge)
        if (context != null) {
            Log.i(TAG, "SEXТB search: Attempting WebView fallback for '$cleanQuery'")
            try {
                val webItems = SextbWebViewFallback.scrapeCatalog(context, searchUrl)
                if (webItems.isNotEmpty()) {
                    webItems.forEach { item ->
                        item.uploaderUrl?.let { registerPageUrl(item.id, it) }
                    }
                    val bounded = webItems.take(limit)
                    searchCache.put(cacheKey, bounded)
                    Log.i(TAG, "SEXТB search: WebView fallback found ${bounded.size} results")
                    return@withContext bounded
                }
            } catch (e: Exception) {
                Log.w(TAG, "SEXТB search: WebView fallback error: ${e.message}")
            }
        }

        Log.w(TAG, "SEXТB search: Search yielded no items (${SextbError.SEARCH_FAILED.code})")
        emptyList()
    }

    /**
     * Home / Popular catalog feed.
     * Upstream Categories:
     * - /uncensored/pg-{page} (Default Home)
     * - /censored/pg-{page}
     * - /amateur/pg-{page}
     * - /subtitle/pg-{page}
     */
    suspend fun getHome(
        limit: Int = 20,
        page: Int = 1,
        context: Context? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val cacheKey = "home:page:$page:limit:$limit"
        searchCache.get(cacheKey)?.let { return@withContext it }

        val categoryPaths = listOf(
            "$DEFAULT_BASE_URL/uncensored/pg-$page",
            "$DEFAULT_BASE_URL/censored/pg-$page",
            "$DEFAULT_BASE_URL/amateur/pg-$page",
            "$DEFAULT_BASE_URL/subtitle/pg-$page"
        )

        // 1. Native HTTP request to category endpoints
        for (url in categoryPaths) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", SextbResolver.DEFAULT_UA)
                    .header("Referer", "$DEFAULT_BASE_URL/")
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val items = SextbParser.parseSearchResults(html, DEFAULT_BASE_URL)
                    if (items.isNotEmpty()) {
                        items.forEach { item ->
                            item.uploaderUrl?.let { registerPageUrl(item.id, it) }
                        }
                        val bounded = items.take(limit)
                        searchCache.put(cacheKey, bounded)
                        Log.i(TAG, "SEXТB home: Loaded ${bounded.size} items from $url")
                        return@withContext bounded
                    }
                }
            } catch (e: Exception) {
                Log.v(TAG, "SEXТB home: Failed $url: ${e.message}")
            }
        }

        // 2. Controlled WebView catalog fallback if native HTTP returned empty/blocked
        if (context != null) {
            Log.i(TAG, "SEXТB home: Attempting headless WebView catalog extraction")
            try {
                val homeUrl = "$DEFAULT_BASE_URL/uncensored/pg-$page"
                val webItems = SextbWebViewFallback.scrapeCatalog(context, homeUrl)
                if (webItems.isNotEmpty()) {
                    webItems.forEach { item ->
                        item.uploaderUrl?.let { registerPageUrl(item.id, it) }
                    }
                    val bounded = webItems.take(limit)
                    searchCache.put(cacheKey, bounded)
                    return@withContext bounded
                }
            } catch (e: Exception) {
                Log.w(TAG, "SEXТB home: WebView scraper error: ${e.message}")
            }
        }

        Log.w(TAG, "SEXТB home: No videos found for page $page")
        emptyList()
    }

    /**
     * 2. loadDetails(url)
     */
    suspend fun loadDetails(urlOrId: String, context: Context? = null): SextbVideoDetails = withContext(Dispatchers.IO) {
        val pageUrl = normalizePageUrl(urlOrId)
        val cacheKey = "details:$pageUrl"

        detailsCache.get(cacheKey)?.let {
            Log.d(TAG, "SEXТB details: Serving from cache for $pageUrl")
            return@withContext it
        }

        Log.i(TAG, "SEXТB details: Fetching details for $pageUrl")

        val candidateUrls = getCandidateUrls(urlOrId)

        for (targetUrl in candidateUrls) {
            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", SextbResolver.DEFAULT_UA)
                    .header("Referer", "$DEFAULT_BASE_URL/")
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    if (!html.contains("404 Page Not Found") && !html.contains("Page Not Found | SEXTB") &&
                        !html.contains("Access Restricted", ignoreCase = true) && !html.contains("<title>404")
                    ) {
                        val details = SextbParser.parseDetailsPage(html, targetUrl)
                        if (details.title.isNotBlank() && !details.title.contains("404")) {
                            registerPageUrl(urlOrId, targetUrl)
                            detailsCache.put(cacheKey, details)
                            return@withContext details
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "SEXТB details: Network attempt failed for $targetUrl: ${e.message}")
            }
        }

        // Return baseline details with id and cleaned title from URL slug
        val id = SextbParser.extractVideoIdFromUrl(pageUrl)
        val cleanTitle = id.replace("-", " ").replace("_", " ").capitalizeWords()
        SextbVideoDetails(
            id = id,
            pageUrl = pageUrl,
            title = cleanTitle.ifBlank { "SEXТB Video" }
        )
    }

    /**
     * 3. loadEpisodes(url)
     */
    suspend fun loadEpisodes(urlOrId: String, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val details = loadDetails(urlOrId, context)
        details.episodes.map { it.toVideoItem(details.title) }
    }

    /**
     * 4. loadVideo(url) - Returns primary standardized VideoSource
     */
    suspend fun loadVideo(urlOrId: String, context: Context? = null): VideoSource = withContext(Dispatchers.IO) {
        val sources = resolveSource(urlOrId, context)
        sources.firstOrNull() ?: throw SextbException(
            SextbError.SOURCE_NOT_FOUND,
            "No playable stream could be found for $urlOrId"
        )
    }

    /**
     * 5. resolveSource(url) - Returns all available VideoSource variants (1080p, 720p, etc.)
     */
    suspend fun resolveSource(urlOrId: String, context: Context? = null): List<VideoSource> = withContext(Dispatchers.IO) {
        val candidateUrls = getCandidateUrls(urlOrId)
        Log.i(TAG, "SEXТB resolver: Resolving media sources for $urlOrId (candidates: ${candidateUrls.size})")

        for (targetUrl in candidateUrls) {
            val sources = SextbResolver.resolveVideoSources(targetUrl, context = context)
            if (sources.isNotEmpty()) {
                registerPageUrl(urlOrId, targetUrl)
                Log.i(TAG, "SEXТB resolver: Successfully resolved ${sources.size} stream variants from $targetUrl")
                return@withContext sources
            }
        }

        // Fallback: If slug was passed, try searching for the exact title/slug to discover the real page URL
        val cleanSlug = SextbParser.extractVideoIdFromUrl(urlOrId)
        if (cleanSlug.isNotBlank() && cleanSlug.length > 3 && !urlOrId.startsWith("http")) {
            try {
                val searchResults = search(cleanSlug, limit = 5, context = context)
                val matching = searchResults.firstOrNull { it.id.equals(cleanSlug, ignoreCase = true) || it.uploaderUrl?.contains(cleanSlug) == true }
                    ?: searchResults.firstOrNull()
                val realUrl = matching?.uploaderUrl
                if (!realUrl.isNullOrBlank()) {
                    Log.i(TAG, "SEXТB resolver: Discovered real URL through search: $realUrl")
                    val sources = SextbResolver.resolveVideoSources(realUrl, context = context)
                    if (sources.isNotEmpty()) {
                        registerPageUrl(urlOrId, realUrl)
                        return@withContext sources
                    }
                }
            } catch (_: Exception) {}
        }

        Log.w(TAG, "SEXТB resolver: Failed to resolve media sources (${SextbError.SOURCE_NOT_FOUND.code})")
        emptyList()
    }

    /**
     * 6. Standardized Butterfly StreamData for direct player integration.
     */
    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val pageUrl = normalizePageUrl(urlOrId)
        Log.i(TAG, "SEXТB extractor: getStreamData for $urlOrId -> $pageUrl")

        val details = loadDetails(urlOrId, context)
        val sources = resolveSource(urlOrId, context)
        if (sources.isNotEmpty()) {
            val validDetails = if (details.title.contains("404", ignoreCase = true) || details.title.isBlank() || details.title.equals("Video", ignoreCase = true)) {
                val cleanSlug = SextbParser.extractVideoIdFromUrl(pageUrl)
                    .replace("-", " ")
                    .capitalizeWords()
                details.copy(title = cleanSlug.ifBlank { "SEXТB Video" })
            } else {
                details
            }
            val streamData = validDetails.toStreamData(sources)
            Log.i(TAG, "SEXТB extractor: StreamData ready with ${streamData.availableStreamOptions.size} playback options.")
            return@withContext streamData
        }

        Log.w(TAG, "SEXТB extractor: No stream sources returned for $urlOrId")
        null
    }

    fun getCandidateUrls(urlOrId: String): List<String> {
        val trimmed = urlOrId.trim().removePrefix("sextb:").trim()
        val list = mutableListOf<String>()

        // 1. Direct URL if already absolute
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            list.add(trimmed)
            return list
        }

        // 2. Look up in knownPageUrlMap
        knownPageUrlMap[trimmed]?.let { list.add(it) }

        val cleanId = trimmed.removePrefix("/").removePrefix("video/").removePrefix("watch/").removePrefix("uncensored/").removePrefix("censored/").removeSuffix("/")
        knownPageUrlMap[cleanId]?.let { if (!list.contains(it)) list.add(it) }

        // 3. Primary canonical paths on sextb.net
        val candidates = listOf(
            "$DEFAULT_BASE_URL/$cleanId/",
            "$DEFAULT_BASE_URL/uncensored/$cleanId/",
            "$DEFAULT_BASE_URL/censored/$cleanId/",
            "$DEFAULT_BASE_URL/watch/$cleanId/",
            "$DEFAULT_BASE_URL/$cleanId.html",
            "$DEFAULT_BASE_URL/video/$cleanId/"
        )
        for (c in candidates) {
            if (!list.contains(c)) list.add(c)
        }

        return list
    }

    fun normalizePageUrl(urlOrId: String): String {
        val candidates = getCandidateUrls(urlOrId)
        return candidates.firstOrNull() ?: "$DEFAULT_BASE_URL/${urlOrId.trim().removePrefix("sextb:").trim()}/"
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
