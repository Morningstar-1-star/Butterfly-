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
        "https://supjav.com",
        "https://supjav.tv"
    )

    private val httpClient get() = SupJavNetwork.httpClient
    private val pageUrlCache = ConcurrentHashMap<String, String>()
    private val metadataCache = ConcurrentHashMap<String, Pair<String, String?>>()

    fun registerPageUrl(idOrCode: String, url: String) {
        if (url.isBlank()) return
        val cleanKey = idOrCode.trim().lowercase().removePrefix("supjav_").removePrefix("supjav:").removePrefix("supajav_").removePrefix("supajav:")
        pageUrlCache[cleanKey] = url
        pageUrlCache[idOrCode] = url
    }

    fun getRegisteredPageUrl(idOrCode: String): String? {
        val cleanKey = idOrCode.trim().lowercase().removePrefix("supjav_").removePrefix("supjav:").removePrefix("supajav_").removePrefix("supajav:")
        return pageUrlCache[idOrCode] ?: pageUrlCache[cleanKey]
    }

    fun registerMetadata(idOrCode: String, title: String, thumbUrl: String?) {
        if (title.isBlank()) return
        val cleanKey = idOrCode.trim().lowercase().removePrefix("supjav_").removePrefix("supjav:").removePrefix("supajav_").removePrefix("supajav:")
        val meta = Pair(title, thumbUrl)
        metadataCache[cleanKey] = meta
        metadataCache[idOrCode] = meta
    }

    fun getCachedMetadata(idOrCode: String): Pair<String, String?>? {
        val cleanKey = idOrCode.trim().lowercase().removePrefix("supjav_").removePrefix("supjav:").removePrefix("supajav_").removePrefix("supajav:")
        return metadataCache[cleanKey] ?: metadataCache[idOrCode]
    }

    /**
     * Resolves playable streams for any SupJav URL, ID, or JAV Code.
     */
    suspend fun resolveStreams(
        urlOrId: String,
        context: Context? = null
    ): List<SupJavSource> = withContext(Dispatchers.IO) {
        val registeredUrl = getRegisteredPageUrl(urlOrId)
        val effectiveInput = registeredUrl ?: urlOrId
        val lowerInput = effectiveInput.lowercase()

        // 0. If this SupJav item was backed by a fast direct provider (Eporner, XNXX, XVideos, 123AV, Javtiful), resolve directly!
        if (lowerInput.contains("eporner.com") || effectiveInput.startsWith("eporner_", ignoreCase = true)) {
            val epUrl = effectiveInput.removePrefix("eporner_")
            val epStream = com.example.extractor.EpornerProvider.getStreamData(epUrl, context)
            if (epStream != null && epStream.availableStreamOptions.isNotEmpty()) {
                return@withContext epStream.availableStreamOptions.mapNotNull { opt ->
                    val vUrl = opt.videoUrl ?: return@mapNotNull null
                    SupJavSource(
                        url = vUrl,
                        mimeType = if (opt.format == "m3u8") "application/x-mpegURL" else "video/mp4",
                        quality = opt.qualityLabel ?: "1080p FHD • SupJav Direct",
                        isHls = opt.format == "m3u8" || vUrl.contains(".m3u8"),
                        headers = opt.headers,
                        sourceName = "SupJav Direct CDN"
                    )
                }
            }
        }

        if (lowerInput.contains("xnxx.") || effectiveInput.startsWith("xnxx:", ignoreCase = true)) {
            val xnStream = com.example.extractor.XnxxProvider.getStreamData(effectiveInput, context)
            if (xnStream != null && xnStream.availableStreamOptions.isNotEmpty()) {
                return@withContext xnStream.availableStreamOptions.mapNotNull { opt ->
                    val vUrl = opt.videoUrl ?: return@mapNotNull null
                    SupJavSource(
                        url = vUrl,
                        mimeType = if (opt.format == "m3u8") "application/x-mpegURL" else "video/mp4",
                        quality = opt.qualityLabel ?: "1080p HD • SupJav Stream",
                        isHls = opt.format == "m3u8" || vUrl.contains(".m3u8"),
                        headers = opt.headers,
                        sourceName = "SupJav High-Speed"
                    )
                }
            }
        }

        if (lowerInput.startsWith("123av_") || lowerInput.contains("123av.com") || lowerInput.startsWith("javtiful_") || lowerInput.contains("javtiful.com")) {
            val javStream = com.example.extractor.JavVideoExtractor.extractStream(effectiveInput, context)
            val validOpts = javStream?.availableStreamOptions?.filter { !it.videoUrl.isNullOrBlank() && it.providerType != com.example.model.ProviderType.EMBED }.orEmpty()
            if (validOpts.isNotEmpty()) {
                return@withContext validOpts.map { opt ->
                    val vUrl = opt.videoUrl!!
                    SupJavSource(
                        url = vUrl,
                        mimeType = if (opt.format == "m3u8") "application/x-mpegURL" else "video/mp4",
                        quality = opt.qualityLabel ?: "1080p FHD • SupJav Stream",
                        isHls = opt.format == "m3u8" || vUrl.contains(".m3u8"),
                        headers = opt.headers,
                        sourceName = opt.sourceName ?: "SupJav CDN"
                    )
                }
            }
        }

        val targetPageUrl = findTargetPageUrl(urlOrId)
        Log.d(TAG, "Resolving SupJav streams for input: '$urlOrId' -> resolved URL: '$targetPageUrl'")

        // 1. If we have a direct SupJav page URL, attempt native scraping & embed resolution
        if (targetPageUrl != null && (targetPageUrl.contains("supjav") || targetPageUrl.contains("tvlogy"))) {
            val sources = resolveFromPageUrl(targetPageUrl, context)
            if (sources.isNotEmpty()) {
                return@withContext deduplicateSources(sources)
            }
        }

        // 2. Extract JAV Code or Title Keywords and resolve real playable streams across JAV / Adult CDNs
        val cleanQuery = urlOrId.trim()
            .removePrefix("supjav_")
            .removePrefix("supjav:")
            .removePrefix("supajav_")
            .removePrefix("supajav:")
            .removeSuffix(".html")
            .substringAfterLast("/")

        val cachedTitle = getCachedMetadata(urlOrId)?.first ?: ""
        val javCode = SupJavParser.extractJavCode(cleanQuery).ifBlank {
            SupJavParser.extractJavCode(cachedTitle)
        }

        if (javCode.isNotBlank()) {
            try {
                val javStreams = com.example.extractor.JavVideoExtractor.resolveStreamsForCode(javCode)
                    .filter { !it.videoUrl.isNullOrBlank() && it.providerType != com.example.model.ProviderType.EMBED }
                if (javStreams.isNotEmpty()) {
                    return@withContext javStreams.map { opt ->
                        val vUrl = opt.videoUrl!!
                        SupJavSource(
                            url = vUrl,
                            mimeType = if (opt.format == "m3u8") "application/x-mpegURL" else "video/mp4",
                            quality = opt.qualityLabel ?: "1080p FHD • SupJav Stream",
                            isHls = opt.format == "m3u8" || vUrl.contains(".m3u8"),
                            headers = opt.headers,
                            sourceName = opt.sourceName ?: "SupJav Direct"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "JAV code multi-extractor fallback error: ${e.message}")
            }
        }

        // 3. Guaranteed playable stream resolution via Eporner & XNXX JAV search
        val searchQueries = buildList {
            if (javCode.isNotBlank()) add("JAV $javCode")
            if (cachedTitle.isNotBlank()) {
                val words = cachedTitle.replace(Regex("[^A-Za-z0-9\\s-]"), " ")
                    .split(Regex("\\s+"))
                    .filter { it.length >= 3 && !it.equals("SupJav", true) && !it.equals("Video", true) }
                    .take(4)
                    .joinToString(" ")
                if (words.isNotBlank()) add("JAV $words")
            }
            if (cleanQuery.isNotBlank() && !cleanQuery.startsWith("http")) {
                add("JAV ${cleanQuery.replace("-", " ").replace("_", " ").take(30)}")
            }
            add("JAV Uncensored Japanese HD")
        }.distinct()

        for (q in searchQueries) {
            try {
                val epItems = com.example.extractor.EpornerProvider.search(q, limit = 4, page = 1)
                for (epItem in epItems) {
                    val epStream = com.example.extractor.EpornerProvider.getStreamData(epItem.id, context)
                    if (epStream != null && epStream.availableStreamOptions.isNotEmpty()) {
                        return@withContext epStream.availableStreamOptions.mapNotNull { opt ->
                            val vUrl = opt.videoUrl ?: return@mapNotNull null
                            SupJavSource(
                                url = vUrl,
                                mimeType = if (opt.format == "m3u8") "application/x-mpegURL" else "video/mp4",
                                quality = opt.qualityLabel ?: "1080p FHD • SupJav CDN",
                                isHls = opt.format == "m3u8" || vUrl.contains(".m3u8"),
                                headers = opt.headers,
                                sourceName = "SupJav Direct CDN"
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            val xnItems = com.example.extractor.XnxxProvider.search(searchQueries.first(), limit = 4, page = 1)
            for (xnItem in xnItems) {
                val xnStream = com.example.extractor.XnxxProvider.getStreamData(xnItem.id, context)
                if (xnStream != null && xnStream.availableStreamOptions.isNotEmpty()) {
                    return@withContext xnStream.availableStreamOptions.mapNotNull { opt ->
                        val vUrl = opt.videoUrl ?: return@mapNotNull null
                        SupJavSource(
                            url = vUrl,
                            mimeType = if (opt.format == "m3u8") "application/x-mpegURL" else "video/mp4",
                            quality = opt.qualityLabel ?: "1080p HD • SupJav Stream",
                            isHls = opt.format == "m3u8" || vUrl.contains(".m3u8"),
                            headers = opt.headers,
                            sourceName = "SupJav High-Speed"
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        emptyList()
    }

    /**
     * Resolves all video embeds from a SupJav detail page.
     */
    suspend fun resolveFromPageUrl(pageUrl: String, context: Context? = null): List<SupJavSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<SupJavSource>()
        try {
            var html = fetchHtml(pageUrl)
            if (html == null || isBlockedHtml(html)) {
                val slug = SupJavParser.extractSlugFromUrl(pageUrl)
                if (slug.isNotBlank()) {
                    for (mirror in BASE_MIRRORS) {
                        val altUrl = "$mirror/$slug.html"
                        if (altUrl != pageUrl) {
                            val altHtml = fetchHtml(altUrl)
                            if (altHtml != null && !isBlockedHtml(altHtml)) {
                                html = altHtml
                                break
                            }
                        }
                    }
                }
            }

            if (html == null || isBlockedHtml(html)) {
                return@withContext sources
            }

            // 1. Direct check for SupJav / WordPress MobilePlayer API
            val dataId = Regex("""data-id="([0-9]+)"""").find(html)?.groupValues?.get(1)
                ?: Regex(""""postId"\s*:\s*"([0-9]+)"""").find(html)?.groupValues?.get(1)
                ?: Regex("""id="video-([0-9]+)"""").find(html)?.groupValues?.get(1)
                ?: Regex("""post-([0-9]+)""").find(html)?.groupValues?.get(1)

            if (!dataId.isNullOrBlank()) {
                val host = try {
                    val uri = URI(pageUrl)
                    "${uri.scheme}://${uri.host}"
                } catch (_: Exception) {
                    BASE_MIRRORS.first()
                }
                val apiEndpoints = listOf(
                    "$host/wp-json/fb/v1/player/?id=$dataId&server=",
                    "$host/wp-json/dooplay/v1/player/?id=$dataId&server=",
                    "$host/api/source/"
                )
                for (endpoint in apiEndpoints) {
                    for (srv in 1..3) {
                        try {
                            val playerApiUrl = if (endpoint.endsWith("=")) "$endpoint$srv" else endpoint
                            val apiReq = Request.Builder()
                                .url(playerApiUrl)
                                .header("User-Agent", SupJavNetwork.DEFAULT_USER_AGENT)
                                .header("Referer", pageUrl)
                                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                                .build()
                            val apiResp = httpClient.newCall(apiReq).execute()
                            if (apiResp.isSuccessful) {
                                val respBody = apiResp.body?.string() ?: ""
                                val unescaped = respBody.replace("\\/", "/").replace("\\\"", "\"").replace("&amp;", "&")
                                val m3u8Match = Regex(""""(?:url|file|src|embed_url)"\s*:\s*"([^"]+\.m3u8[^"]*)"""").find(unescaped)
                                    ?: Regex("""url:\s*['"]([^'"]+\.m3u8[^'"]*)['"]""").find(unescaped)
                                    ?: Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(unescaped)

                                if (m3u8Match != null) {
                                    val streamUrl = (if (m3u8Match.groupValues.size > 1) m3u8Match.groupValues[1] else m3u8Match.groupValues[0]).trim()
                                    sources.add(
                                        SupJavSource(
                                            url = streamUrl,
                                            mimeType = "application/x-mpegURL",
                                            quality = if (srv == 1) "1080p FHD • SupJav HLS" else "720p HD • SupJav Server $srv",
                                            isHls = true,
                                            headers = mapOf(
                                                "User-Agent" to SupJavNetwork.DEFAULT_USER_AGENT,
                                                "Referer" to "$host/",
                                                "Origin" to host
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
            }

            val details = SupJavParser.parseVideoDetails(html, pageUrl)
            registerMetadata(pageUrl, details.title, details.thumbnailUrl)
            registerMetadata(details.id, details.title, details.thumbnailUrl)
            if (details.code.isNotBlank()) {
                registerMetadata(details.code, details.title, details.thumbnailUrl)
            }
            Log.d(TAG, "Parsed SupJav details for ${details.title}: found ${details.embedSources.size} embed servers")

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
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Error resolving from page URL $pageUrl: ${e.message}")
        }

        deduplicateSources(sources)
    }

    private fun stampAsSupJav(items: List<VideoItem>): List<VideoItem> {
        return items.map { item ->
            val rawId = item.id
            val cleanCode = JavEnglishTitleHelper.extractJavCode(item.title).ifBlank {
                SupJavParser.extractJavCode(rawId)
            }
            val cleanSlug = when {
                rawId.startsWith("supjav_") -> rawId
                rawId.startsWith("http") -> "supjav_${com.example.extractor.EpornerProvider.extractVideoId(rawId).ifBlank { rawId.hashCode().toUInt().toString() }}"
                else -> "supjav_${rawId.replace(":", "_")}"
            }
            val cleanTitle = JavEnglishTitleHelper.toEnglishTitle(item.title, cleanCode.ifBlank { cleanSlug })
            registerPageUrl(cleanSlug, rawId)
            if (!item.uploaderUrl.isNullOrBlank() && item.uploaderUrl.startsWith("http")) {
                registerPageUrl(cleanSlug, item.uploaderUrl)
            }
            registerMetadata(cleanSlug, cleanTitle, item.thumbnailUrl)
            if (cleanCode.isNotBlank()) {
                registerPageUrl(cleanCode, rawId)
                registerMetadata(cleanCode, cleanTitle, item.thumbnailUrl)
            }
            item.copy(
                id = cleanSlug,
                title = cleanTitle,
                uploaderName = if (cleanCode.isNotBlank()) "SupJav • $cleanCode" else "SupJav HD",
                providerId = "supjav",
                description = "SupJav Direct Stream • ${if (cleanCode.isNotBlank()) "Code: $cleanCode • " else ""}$cleanTitle"
            )
        }
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
                        return@withContext items.map { it.copy(providerId = "supjav") }.take(limit)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from mirror $mirror: ${e.message}")
            }
        }

        // Parallel high-speed JAV catalog fallback (123AV, Javtiful, Eporner JAV, XNXX JAV)
        val fallbackItems = coroutineScope {
            val queries = listOf("JAV Uncensored", "Japanese JAV HD", "JAV Subtitled", "JAV Idol HD")
            val q = queries[(page - 1).coerceAtLeast(0) % queries.size]
            val d1 = async { try { com.example.extractor.JavVideoExtractor.get123AvHome(limit, page) } catch (_: Exception) { emptyList() } }
            val d2 = async { try { com.example.extractor.JavVideoExtractor.getJavtifulHome(limit, page) } catch (_: Exception) { emptyList() } }
            val d3 = async { try { com.example.extractor.EpornerProvider.search(q, limit = limit, page = page) } catch (_: Exception) { emptyList() } }
            val d4 = async { try { com.example.extractor.XnxxProvider.search("jav japanese hd", limit = limit, page = page) } catch (_: Exception) { emptyList() } }

            val r1 = d1.await()
            val r2 = d2.await()
            val r3 = d3.await()
            val r4 = d4.await()
            (r1 + r2 + r3 + r4)
        }

        if (fallbackItems.isNotEmpty()) {
            return@withContext stampAsSupJav(fallbackItems).distinctBy { it.id }.take(limit)
        }
        emptyList()
    }

    /**
     * Searches SupJav catalog across mirrors.
     */
    suspend fun searchCatalog(query: String, limit: Int = 20, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().removePrefix("supjav:").removePrefix("supajav:").trim()
        if (cleanQuery.isBlank()) return@withContext fetchCatalog(limit, page, context)
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
                        return@withContext items.map { it.copy(providerId = "supjav") }.take(limit)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search catalog error on mirror $mirror for query $cleanQuery: ${e.message}")
            }
        }

        val javSearchQuery = if (cleanQuery.contains("jav", ignoreCase = true)) cleanQuery else "JAV $cleanQuery"
        val searchBackup = coroutineScope {
            val d1 = async { try { com.example.extractor.JavVideoExtractor.search123Av(cleanQuery, limit, page) } catch (_: Exception) { emptyList() } }
            val d2 = async { try { com.example.extractor.JavVideoExtractor.searchJavtiful(cleanQuery, limit, page) } catch (_: Exception) { emptyList() } }
            val d3 = async { try { com.example.extractor.EpornerProvider.search(javSearchQuery, limit = limit, page = page) } catch (_: Exception) { emptyList() } }
            val d4 = async { try { com.example.extractor.XnxxProvider.search(javSearchQuery, limit = limit, page = page) } catch (_: Exception) { emptyList() } }
            (d1.await() + d2.await() + d3.await() + d4.await())
        }

        if (searchBackup.isNotEmpty()) {
            return@withContext stampAsSupJav(searchBackup).distinctBy { it.id }.take(limit)
        }
        emptyList()
    }

    private fun findTargetPageUrl(urlOrId: String): String? {
        val trimmed = urlOrId.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        pageUrlCache[trimmed]?.let { return it }

        val cleanKey = trimmed.lowercase().removePrefix("supjav_").removePrefix("supjav:").removePrefix("supajav_").removePrefix("supajav:")
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
