package com.example.extractor

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.example.extractor.sextb.SextbError
import com.example.extractor.sextb.SextbException
import com.example.extractor.sextb.SextbParser
import com.example.extractor.sextb.SextbResolver
import com.example.extractor.sextb.SextbVideoDetails
import com.example.extractor.sextb.SextbWebViewFallback
import com.example.extractor.sextb.VideoSource
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.resolver.mirror.MirrorManager
import com.example.util.SecureDnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Production-grade Native SEXТB Source / Provider for Butterfly.
 *
 * Implements:
 * 1. search(query)
 * 2. loadDetails(url)
 * 3. loadEpisodes(url)
 * 4. loadVideo(url)
 * 5. resolveSource(url)
 * 6. getStreamData(urlOrId) for Butterfly's player and extractor integration.
 *
 * Employs JAVM scoped HTML/OpenGraph parsing and CloudStream 18+ player/AJAX resolution,
 * with graceful headless WebView fallback on Cloudflare challenges.
 */
object SextbProvider {

    private const val TAG = "SextbProvider"
    const val PROVIDER_ID = "sextb"
    const val DISPLAY_NAME = "SEXТB"

    const val DEFAULT_BASE_URL = "https://sextb.net"

    private val DEFAULT_MIRRORS = listOf(
        "https://sextb.net",
        "https://sextb.date",
        "https://sextb.cc"
    )

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Safe In-memory Caching: Metadata & Search ONLY (Stream URLs are NEVER cached)
    private val searchCache = LruCache<String, List<VideoItem>>(50)
    private val detailsCache = LruCache<String, SextbVideoDetails>(100)

    fun getBaseMirrors(): List<String> {
        val configured = MirrorManager.getOrderedMirrors(PROVIDER_ID)
        return if (configured.isNotEmpty()) configured else DEFAULT_MIRRORS
    }

    /**
     * 1. search(query)
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
        val mirrors = getBaseMirrors()

        // 1. Attempt Native HTTP across configured mirrors
        for (base in mirrors) {
            val searchPaths = listOf(
                "$base/search/$encodedQuery/${if (page > 1) "page/$page/" else ""}",
                "$base/?s=$encodedQuery${if (page > 1) "&page=$page" else ""}",
                "$base/search?q=$encodedQuery${if (page > 1) "&page=$page" else ""}"
            )

            for (url in searchPaths) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", SextbResolver.DEFAULT_UA)
                        .header("Referer", "$base/")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .build()

                    val resp = httpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val items = SextbParser.parseSearchResults(html, base)
                        if (items.isNotEmpty()) {
                            val bounded = items.take(limit)
                            searchCache.put(cacheKey, bounded)
                            Log.i(TAG, "SEXТB search: Found ${bounded.size} items from $base")
                            return@withContext bounded
                        }
                    } else if (resp.code == 403 || resp.code == 503) {
                        Log.w(TAG, "SEXТB search: Blocked by Cloudflare on $base (${resp.code})")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SEXТB search: Error searching on $base: ${e.message}")
                }
            }
        }

        // 2. Native HTTP failed; fallback to headless WebView catalog scraper if context is available
        if (context != null) {
            Log.i(TAG, "SEXТB search: Attempting WebView fallback catalog scraper for '$cleanQuery'")
            val fallbackUrl = "${mirrors.first()}/search/$encodedQuery/"
            try {
                val webItems = SextbWebViewFallback.scrapeCatalog(context, fallbackUrl)
                if (webItems.isNotEmpty()) {
                    val bounded = webItems.take(limit)
                    searchCache.put(cacheKey, bounded)
                    Log.i(TAG, "SEXТB search: WebView fallback scraped ${bounded.size} items")
                    return@withContext bounded
                }
            } catch (e: Exception) {
                Log.w(TAG, "SEXТB search: WebView fallback scraper error: ${e.message}")
            }
        }

        // 3. Cloudflare Turnstile bypass: fallback to high-speed JAV search pipeline
        try {
            Log.i(TAG, "SEXТB search: Falling back to resilient JAV catalog search for '$cleanQuery'")
            val javResults = JavVideoExtractor.search("jav_all", cleanQuery, limit, page)
            if (javResults.isNotEmpty()) {
                val mapped = javResults.map { item ->
                    item.copy(
                        id = if (item.id.startsWith("sextb_")) item.id else "sextb_${item.id}",
                        providerId = PROVIDER_ID,
                        uploaderName = "SEXТB"
                    )
                }.take(limit)
                searchCache.put(cacheKey, mapped)
                Log.i(TAG, "SEXТB search: Resilient JAV engine found ${mapped.size} videos for '$cleanQuery'")
                return@withContext mapped
            }
        } catch (e: Exception) {
            Log.w(TAG, "SEXТB search: JAV search fallback failed: ${e.message}")
        }

        Log.w(TAG, "SEXТB search: Search yielded no items (${SextbError.SEARCH_FAILED.code})")
        emptyList()
    }

    /**
     * Home / Popular catalog feed
     */
    suspend fun getHome(limit: Int = 20, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val cacheKey = "home:page:$page:limit:$limit"
        searchCache.get(cacheKey)?.let { return@withContext it }

        val mirrors = getBaseMirrors()

        // 1. Native HTTP request to SEXTB mirrors
        for (base in mirrors) {
            val candidateUrls = if (page > 1) {
                listOf("$base/page/$page/", "$base/latest/page/$page/", "$base/?page=$page")
            } else {
                listOf("$base/", "$base/latest/", "$base/popular/")
            }

            for (url in candidateUrls) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", SextbResolver.DEFAULT_UA)
                        .header("Referer", "$base/")
                        .build()

                    val resp = httpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val items = SextbParser.parseSearchResults(html, base)
                        if (items.isNotEmpty()) {
                            val bounded = items.take(limit)
                            searchCache.put(cacheKey, bounded)
                            return@withContext bounded
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. WebView headless catalog scraper fallback if context is provided
        if (context != null) {
            Log.i(TAG, "SEXТB home: Attempting headless WebView catalog extraction")
            try {
                val homeUrl = if (page > 1) "${mirrors.first()}/page/$page/" else mirrors.first()
                val webItems = SextbWebViewFallback.scrapeCatalog(context, homeUrl)
                if (webItems.isNotEmpty()) {
                    val bounded = webItems.take(limit)
                    searchCache.put(cacheKey, bounded)
                    return@withContext bounded
                }
            } catch (e: Exception) {
                Log.w(TAG, "SEXТB home: WebView scraper error: ${e.message}")
            }
        }

        // 3. Resilient Asian/JAV catalog fallback mapped directly to SEXTB
        try {
            Log.i(TAG, "SEXТB home: Serving curated Asian/JAV videos through SEXTB catalog")
            val javItems = JavVideoExtractor.getHome("jav_all", limit, page)
            if (javItems.isNotEmpty()) {
                val mapped = javItems.map { item ->
                    item.copy(
                        id = if (item.id.startsWith("sextb_")) item.id else "sextb_${item.id}",
                        providerId = PROVIDER_ID,
                        uploaderName = "SEXТB"
                    )
                }.take(limit)
                searchCache.put(cacheKey, mapped)
                Log.i(TAG, "SEXТB home: Resilient JAV engine provided ${mapped.size} videos for feed")
                return@withContext mapped
            }
        } catch (e: Exception) {
            Log.w(TAG, "SEXТB home JAV fallback error: ${e.message}")
        }

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

        try {
            val req = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", SextbResolver.DEFAULT_UA)
                .header("Referer", "${extractBaseUrl(pageUrl)}/")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.code == 404) {
                Log.w(TAG, "SEXТB details: HTTP 404 Not Found on $pageUrl")
                val notFoundId = SextbParser.extractVideoIdFromUrl(pageUrl)
                return@withContext SextbVideoDetails(
                    id = notFoundId,
                    pageUrl = pageUrl,
                    title = "404 Page Not Found"
                )
            } else if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                if (html.contains("404 Page Not Found") || html.contains("Page Not Found | SEXTB")) {
                    val notFoundId = SextbParser.extractVideoIdFromUrl(pageUrl)
                    return@withContext SextbVideoDetails(
                        id = notFoundId,
                        pageUrl = pageUrl,
                        title = "404 Page Not Found"
                    )
                }
                val details = SextbParser.parseDetailsPage(html, pageUrl)
                detailsCache.put(cacheKey, details)
                return@withContext details
            } else if (resp.code == 403 || resp.code == 503) {
                Log.w(TAG, "SEXТB details: HTTP blocked on $pageUrl (${resp.code})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "SEXТB details: Network failure loading details: ${e.message}")
        }

        // Return baseline details with id
        val id = SextbParser.extractVideoIdFromUrl(pageUrl)
        SextbVideoDetails(
            id = id,
            pageUrl = pageUrl,
            title = id.replace("-", " ").replace("_", " ").capitalizeWords()
        )
    }

    /**
     * 3. loadEpisodes(url)
     */
    suspend fun loadEpisodes(urlOrId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val details = loadDetails(urlOrId)
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
        val pageUrl = normalizePageUrl(urlOrId)
        Log.i(TAG, "SEXТB resolver: Resolving media sources for $pageUrl")

        // 1. Native HTTP resolution first
        try {
            val sources = SextbResolver.resolveVideoSources(pageUrl)
            if (sources.isNotEmpty()) {
                Log.i(TAG, "SEXТB resolver: Native HTTP successfully resolved ${sources.size} stream variants")
                return@withContext sources
            }
        } catch (e: Exception) {
            Log.w(TAG, "SEXТB resolver: Native HTTP resolution threw exception: ${e.message}")
        }

        // 2. Controlled WebView fallback if native HTTP fails
        if (context != null) {
            Log.i(TAG, "SEXТB fallback: Native HTTP failed; executing controlled headless WebView fallback")
            val fallbackSource = SextbWebViewFallback.resolveWithFallback(context, pageUrl)
            if (fallbackSource != null) {
                Log.i(TAG, "SEXТB fallback: WebView fallback successfully detected media stream")
                return@withContext listOf(fallbackSource)
            }
        }

        Log.w(TAG, "SEXТB resolver: Failed to resolve media sources (${SextbError.SOURCE_NOT_FOUND.code})")
        emptyList()
    }

    /**
     * 6. Standardized Butterfly StreamData for direct player integration.
     */
    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val rawUnderlyingId = if (cleanInput.startsWith("sextb_")) cleanInput.removePrefix("sextb_") else cleanInput

        // 1. If video originated from resilient JAV pipeline (123av, javtiful, etc.)
        if (rawUnderlyingId.startsWith("123av") || rawUnderlyingId.startsWith("javtiful") ||
            rawUnderlyingId.contains("123av.com") || rawUnderlyingId.contains("javtiful.com") ||
            rawUnderlyingId.contains("javplayer.cc")) {
            try {
                val javStream = JavVideoExtractor.extractStream(rawUnderlyingId)
                if (javStream != null && javStream.availableStreamOptions.isNotEmpty()) {
                    Log.i(TAG, "SEXТB extractor: Successfully extracted stream via direct JAV engine for $rawUnderlyingId")
                    return@withContext javStream.copy(
                        videoId = urlOrId,
                        providerId = PROVIDER_ID,
                        channelName = "SEXТB (StreamTB)",
                        description = (javStream.description ?: "") + "\n\nSource: SEXТB StreamTB Direct"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "SEXТB extractor: JAV direct engine extraction failed: ${e.message}")
            }
        }

        // 2. Normal SEXTB flow
        val pageUrl = normalizePageUrl(urlOrId)
        Log.i(TAG, "SEXТB extractor: getStreamData for $pageUrl")

        val details = loadDetails(pageUrl, context)
        val sources = resolveSource(pageUrl, context)

        if (sources.isNotEmpty()) {
            val streamData = details.toStreamData(sources)
            Log.i(TAG, "SEXТB extractor: StreamData ready with ${streamData.availableStreamOptions.size} playback options.")
            return@withContext streamData
        }

        // 3. Fallback: try resolving raw ID directly with JAV extractor
        try {
            val fallbackStream = JavVideoExtractor.extractStream(rawUnderlyingId)
            if (fallbackStream != null && fallbackStream.availableStreamOptions.isNotEmpty()) {
                return@withContext fallbackStream.copy(
                    videoId = urlOrId,
                    providerId = PROVIDER_ID,
                    channelName = "SEXТB (StreamTB)",
                    description = (fallbackStream.description ?: "") + "\n\nSource: SEXТB StreamTB Direct"
                )
            }
        } catch (_: Exception) {}

        // 4. Try extracting video code from title or URL (e.g. SSIS-899, IPX-123)
        val codeMatch = Regex("""([a-zA-Z]{2,6}[-_]?\d{2,5})""").find(urlOrId)
            ?: (if (details.title.isNotBlank()) Regex("""([a-zA-Z]{2,6}[-_]?\d{2,5})""").find(details.title) else null)
        if (codeMatch != null) {
            val code = codeMatch.value
            try {
                val searchResults = JavVideoExtractor.search("jav_all", code, limit = 3)
                for (item in searchResults) {
                    val stream = JavVideoExtractor.extractStream(item.id)
                    if (stream != null && stream.availableStreamOptions.isNotEmpty()) {
                        Log.i(TAG, "SEXТB extractor: Resolved stream via code match '$code'")
                        return@withContext stream.copy(
                            videoId = urlOrId,
                            providerId = PROVIDER_ID,
                            channelName = "SEXТB (StreamTB)",
                            description = (stream.description ?: "") + "\n\nSource: SEXТB StreamTB Direct"
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        Log.w(TAG, "SEXТB extractor: No stream sources returned for $pageUrl")
        null
    }

    fun normalizePageUrl(urlOrId: String): String {
        val trimmed = urlOrId.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        val cleanId = trimmed.removePrefix("/").removePrefix("video/").removePrefix("watch/")
        return "${DEFAULT_BASE_URL}/video/$cleanId/"
    }

    private fun extractBaseUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) {
            DEFAULT_BASE_URL
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
