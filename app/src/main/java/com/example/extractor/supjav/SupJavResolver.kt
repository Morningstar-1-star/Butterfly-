package com.example.extractor.supjav

import android.content.Context
import android.util.Log
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Core orchestrator for resolving SupJav catalogs, metadata, and playable video streams.
 */
object SupJavResolver {

    private const val TAG = "SupJavResolver"

    val BASE_MIRRORS = listOf(
        "https://supjav.mom",
        "https://supjav.biz",
        "https://supjav.com",
        "https://supjav.net",
        "https://supjav.org",
        "https://supjav.cc",
        "https://supjav.tv"
    )

    private val httpClient get() = SupJavNetwork.httpClient
    private val pageUrlCache = ConcurrentHashMap<String, String>()
    private val metadataCache = ConcurrentHashMap<String, Pair<String, String?>>()

    fun registerPageUrl(idOrCode: String, url: String) {
        val cleanKey = idOrCode.trim().lowercase().removePrefix("supjav_").removePrefix("supjav:")
        pageUrlCache[cleanKey] = url
        pageUrlCache[idOrCode] = url
    }

    fun registerMetadata(idOrCode: String, title: String, thumbUrl: String?) {
        if (title.isBlank()) return
        val cleanKey = idOrCode.trim().lowercase().removePrefix("supjav_").removePrefix("supjav:")
        val meta = Pair(title, thumbUrl)
        metadataCache[cleanKey] = meta
        metadataCache[idOrCode] = meta
    }

    fun getCachedMetadata(idOrCode: String): Pair<String, String?>? {
        val cleanKey = idOrCode.trim().lowercase().removePrefix("supjav_").removePrefix("supjav:")
        return metadataCache[cleanKey] ?: metadataCache[idOrCode]
    }

    /**
     * Resolves playable streams for any SupJav URL, ID, or JAV Code.
     */
    suspend fun resolveStreams(
        urlOrId: String,
        context: Context? = null
    ): List<SupJavSource> = withContext(Dispatchers.IO) {
        val resolved = mutableListOf<SupJavSource>()
        val targetPageUrl = findTargetPageUrl(urlOrId)

        Log.d(TAG, "Resolving SupJav streams for input: '$urlOrId' -> resolved URL: '$targetPageUrl'")

        // 1. If we have a direct page URL, attempt native scraping & embed resolution
        if (targetPageUrl != null) {
            val sources = resolveFromPageUrl(targetPageUrl, context)
            if (sources.isNotEmpty()) {
                return@withContext deduplicateSources(sources)
            }
        }

        // 2. If no direct page URL or resolution failed, search across SupJav mirrors for the query/code
        val cleanQuery = urlOrId.trim()
            .removePrefix("supjav_")
            .removePrefix("supjav:")
            .removeSuffix(".html")
            .substringAfterLast("/")

        for (mirror in BASE_MIRRORS) {
            try {
                val searchUrl = if (cleanQuery.isBlank()) mirror else "$mirror/?s=${URLEncoder.encode(cleanQuery, "UTF-8")}"
                val html = fetchHtml(searchUrl)
                if (html != null && !isBlockedHtml(html)) {
                    val cards = SupJavParser.parseVideoCards(html, mirror)
                    val matchingCard = cards.firstOrNull { card ->
                        val codeMatch = SupJavParser.extractJavCode(cleanQuery)
                        if (codeMatch.isNotBlank()) {
                            card.title.contains(codeMatch, ignoreCase = true) ||
                                    card.uploaderUrl?.contains(codeMatch, ignoreCase = true) == true
                        } else {
                            card.id.contains(cleanQuery, ignoreCase = true) ||
                                    card.uploaderUrl?.contains(cleanQuery, ignoreCase = true) == true
                        }
                    } ?: cards.firstOrNull()

                    val cardUrl = matchingCard?.uploaderUrl
                    if (cardUrl != null) {
                        registerPageUrl(urlOrId, cardUrl)
                        val pageSources = resolveFromPageUrl(cardUrl, context)
                        if (pageSources.isNotEmpty()) {
                            return@withContext deduplicateSources(pageSources)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search fallback error on mirror $mirror for $cleanQuery: ${e.message}")
            }
        }

        // 3. Last-resort fallback to WebView capture if context is available
        if (context != null && targetPageUrl != null) {
            val fallbackSource = SupJavWebViewFallback.resolveWithFallback(context, targetPageUrl)
            if (fallbackSource != null) {
                return@withContext listOf(fallbackSource)
            }
        }

        emptyList()
    }

    /**
     * Resolves all video embeds from a SupJav detail page.
     */
    suspend fun resolveFromPageUrl(pageUrl: String, context: Context? = null): List<SupJavSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<SupJavSource>()
        try {
            val html = fetchHtml(pageUrl)
            if (html == null || isBlockedHtml(html)) {
                Log.w(TAG, "Native page fetch blocked or empty for $pageUrl, trying WebView fallback")
                if (context != null) {
                    val fallback = SupJavWebViewFallback.resolveWithFallback(context, pageUrl)
                    if (fallback != null) sources.add(fallback)
                }
                return@withContext sources
            }

            // 1. Direct check for SupJav Mom / WordPress MobilePlayer API
            val dataId = Regex("""data-id="([0-9]+)"""").find(html)?.groupValues?.get(1)
                ?: Regex(""""postId"\s*:\s*"([0-9]+)"""").find(html)?.groupValues?.get(1)
                ?: Regex("""id="video-([0-9]+)"""").find(html)?.groupValues?.get(1)

            if (!dataId.isNullOrBlank()) {
                val host = try {
                    val uri = URI(pageUrl)
                    "${uri.scheme}://${uri.host}"
                } catch (_: Exception) {
                    "https://supjav.mom"
                }
                for (srv in 1..2) {
                    try {
                        val playerApiUrl = "$host/wp-json/fb/v1/player/?id=$dataId&server=$srv"
                        val apiReq = Request.Builder()
                            .url(playerApiUrl)
                            .header("User-Agent", SupJavNetwork.DEFAULT_USER_AGENT)
                            .header("Referer", pageUrl)
                            .build()
                        val apiResp = httpClient.newCall(apiReq).execute()
                        if (apiResp.isSuccessful) {
                            val respBody = apiResp.body?.string() ?: ""
                            val m3u8Match = Regex("""url:\s*'([^']+\.m3u8[^']*)'""").find(respBody)
                                ?: Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(respBody)
                            if (m3u8Match != null) {
                                val streamUrl = m3u8Match.groupValues[1].replace("\\/", "/")
                                sources.add(
                                    SupJavSource(
                                        url = streamUrl,
                                        mimeType = "application/x-mpegURL",
                                        quality = if (srv == 1) "1080p FHD • SupJav HLS" else "720p HD • SupJav Backup HLS",
                                        isHls = true,
                                        headers = mapOf(
                                            "User-Agent" to SupJavNetwork.DEFAULT_USER_AGENT,
                                            "Referer" to "$host/"
                                        ),
                                        sourceName = "SupJav HLS (Server $srv)"
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed player API server $srv for ID $dataId: ${e.message}")
                    }
                }
            }

            val details = SupJavParser.parseVideoDetails(html, pageUrl)
            registerMetadata(pageUrl, details.title, details.thumbnailUrl)
            registerMetadata(details.id, details.title, details.thumbnailUrl)
            if (details.code.isNotBlank()) {
                registerMetadata(details.code, details.title, details.thumbnailUrl)
            }
            Log.d(TAG, "Parsed SupJav details for ${details.title}: found ${details.embedSources.size} embed servers")

            // Parallel resolution of all found embed servers
            coroutineScope {
                val deferreds = details.embedSources.map { embed ->
                    async {
                        try {
                            SupJavEmbedResolvers.resolveEmbed(embed.embedUrl, pageUrl)
                        } catch (e: Exception) {
                            emptyList<SupJavSource>()
                        }
                    }
                }
                val results = deferreds.awaitAll()
                for (r in results) {
                    sources.addAll(r)
                }
            }

            // If embed resolution yielded no streams, check WebView fallback
            if (sources.isEmpty() && context != null) {
                val fallback = SupJavWebViewFallback.resolveWithFallback(context, pageUrl)
                if (fallback != null) sources.add(fallback)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Error resolving from page URL $pageUrl: ${e.message}")
        }

        deduplicateSources(sources)
    }

    /**
     * Fetches catalog / home trending video items from SupJav.
     */
    suspend fun fetchCatalog(limit: Int = 20, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        for (mirror in BASE_MIRRORS) {
            try {
                val targetUrl = if (page <= 1) mirror else "$mirror/page/$page/"
                val html = fetchHtml(targetUrl)
                if (html != null && !isBlockedHtml(html)) {
                    val items = SupJavParser.parseVideoCards(html, mirror)
                    if (items.isNotEmpty()) {
                        items.forEach {
                            registerPageUrl(it.id, it.uploaderUrl ?: "")
                            registerMetadata(it.id, it.title, it.thumbnailUrl)
                            val code = SupJavParser.extractJavCode(it.title).ifBlank { SupJavParser.extractJavCode(it.id) }
                            if (code.isNotBlank()) registerMetadata(code, it.title, it.thumbnailUrl)
                        }
                        return@withContext items.take(limit)
                    }
                }
                if (context != null && (html == null || isBlockedHtml(html))) {
                    val fallbackItems = SupJavWebViewFallback.fetchCatalogWithFallback(context, targetUrl)
                    if (fallbackItems.isNotEmpty()) {
                        fallbackItems.forEach {
                            registerPageUrl(it.id, it.uploaderUrl ?: "")
                            registerMetadata(it.id, it.title, it.thumbnailUrl)
                        }
                        return@withContext fallbackItems.take(limit)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Catalog fetch error on mirror $mirror: ${e.message}")
            }
        }
        emptyList()
    }

    /**
     * Searches SupJav catalog across mirrors.
     */
    suspend fun searchCatalog(query: String, limit: Int = 20, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()
        val encoded = try { URLEncoder.encode(cleanQuery, "UTF-8") } catch (e: Exception) { cleanQuery }

        for (mirror in BASE_MIRRORS) {
            try {
                val targetUrl = if (page <= 1) {
                    "$mirror/?s=$encoded"
                } else {
                    "$mirror/page/$page/?s=$encoded"
                }
                val html = fetchHtml(targetUrl)
                if (html != null && !isBlockedHtml(html)) {
                    val items = SupJavParser.parseVideoCards(html, mirror)
                    if (items.isNotEmpty()) {
                        items.forEach {
                            registerPageUrl(it.id, it.uploaderUrl ?: "")
                            registerMetadata(it.id, it.title, it.thumbnailUrl)
                            val code = SupJavParser.extractJavCode(it.title).ifBlank { SupJavParser.extractJavCode(it.id) }
                            if (code.isNotBlank()) registerMetadata(code, it.title, it.thumbnailUrl)
                        }
                        return@withContext items.take(limit)
                    }
                }
                if (context != null && (html == null || isBlockedHtml(html))) {
                    val fallbackItems = SupJavWebViewFallback.fetchCatalogWithFallback(context, targetUrl)
                    if (fallbackItems.isNotEmpty()) {
                        fallbackItems.forEach {
                            registerPageUrl(it.id, it.uploaderUrl ?: "")
                            registerMetadata(it.id, it.title, it.thumbnailUrl)
                        }
                        return@withContext fallbackItems.take(limit)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search catalog error on mirror $mirror for query $cleanQuery: ${e.message}")
            }
        }
        emptyList()
    }

    private fun findTargetPageUrl(urlOrId: String): String? {
        val trimmed = urlOrId.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        pageUrlCache[trimmed]?.let { return it }

        val cleanKey = trimmed.lowercase().removePrefix("supjav_").removePrefix("supjav:")
        pageUrlCache[cleanKey]?.let { return it }

        // Construct standard URL if slug-like
        if (cleanKey.contains("-") || cleanKey.contains("_")) {
            return "${BASE_MIRRORS.first()}/$cleanKey.html"
        }

        return null
    }

    private fun fetchHtml(url: String): String? {
        return try {
            val origin = try {
                val uri = URI(url)
                "${uri.scheme}://${uri.host}/"
            } catch (_: Exception) {
                "https://supjav.mom/"
            }
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", SupJavNetwork.DEFAULT_USER_AGENT)
                .header("Referer", origin)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful || resp.code == 403 || resp.code == 503) {
                resp.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP fetch failed for $url: ${e.message}")
            null
        }
    }

    private fun isBlockedHtml(html: String): Boolean {
        return html.contains("Attention Required! | Cloudflare") ||
                html.contains("Checking your browser before accessing") ||
                html.contains("cf-browser-verification") ||
                html.contains("Just a moment...") ||
                html.contains("Access denied") ||
                html.contains("<title>404") ||
                html.contains("404 Not Found")
    }

    private fun deduplicateSources(sources: List<SupJavSource>): List<SupJavSource> {
        val seenUrls = mutableSetOf<String>()
        val distinct = mutableListOf<SupJavSource>()

        // Prioritize HLS 1080p -> HLS 720p -> MP4
        val sorted = sources.sortedByDescending { src ->
            var score = 0
            if (src.isHls) score += 100
            if (src.quality.contains("1080")) score += 50
            if (src.quality.contains("720")) score += 20
            if (src.sourceName.contains("TVLogy")) score += 15
            if (src.sourceName.contains("StreamWish")) score += 10
            score
        }

        for (src in sorted) {
            val cleanUrl = src.url.trim()
            if (cleanUrl.isNotBlank() && seenUrls.add(cleanUrl)) {
                distinct.add(src)
            }
        }
        return distinct
    }
}
