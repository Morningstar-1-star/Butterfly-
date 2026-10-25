package com.example.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.CastMember
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * Native OlaMovies Provider for Butterfly.
 *
 * Runs 100% on the phone (never via Render / MediaServer):
 * - Stage 1 (Catalog, Search, Details, Release Blocks):
 *   Routes all `olamovies.*` and `ol-am.top` requests through the user's Cloudflare Worker proxy:
 *   `GET https://falling-wildflower-9a91.samdem612.workers.dev/?url=<encoded>&k=<key>`
 *   with header `x-proxy-key: kKRUiaCTetZ58DRFpFpTuVnYJjH6E7mg`.
 * - Stage 2 (Shorteners & Hosters -> Direct MKV/MP4 for ExoPlayer):
 *   Follows `ol-am.top` / `olamovies.*` shortener chains (via Worker + direct/WebView fallback),
 *   then resolves HubCloud / HubDrive, Gofile, and Google Drive directly from the phone into
 *   native ExoPlayer streams with required headers (`Referer`, `Cookie`, `User-Agent`).
 */
object OlaMoviesProvider {
    private const val TAG = "OlaMoviesProvider"

    const val WORKER_BASE_URL = AppConfig.DEFAULT_OLAMOVIES_WORKER_URL
    const val WORKER_KEY = AppConfig.DEFAULT_OLAMOVIES_WORKER_KEY
    private const val URLS_JSON = "https://raw.githubusercontent.com/Zenda-Cross/vega-providers/refs/heads/main/urls.json"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    val BROWSER_HEADERS = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Sec-Ch-Ua" to "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
        "Sec-Ch-Ua-Mobile" to "?0",
        "Sec-Ch-Ua-Platform" to "\"Windows\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1"
    )

    class OlaMoviesException(message: String) : Exception(message)

    data class OlaDirectLink(
        val title: String,
        val link: String,
        val quality: String,
        val groupTitle: String,
        val episodeNumber: Int? = null,
        val filesize: String? = null
    )

    data class OlaMetaResult(
        val path: String,
        val title: String,
        val synopsis: String,
        val posterUrl: String,
        val backdropUrl: String,
        val imdbId: String,
        val isSeries: Boolean,
        val directLinks: List<OlaDirectLink>,
        val cast: List<String> = emptyList(),
        val rating: String = ""
    )

    data class OlaResolvedStream(
        val server: String,
        val link: String,
        val type: String,
        val headers: Map<String, String>
    )

    private val metaCache: MutableMap<String, OlaMetaResult> = Collections.synchronizedMap(
        object : LinkedHashMap<String, OlaMetaResult>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OlaMetaResult>?): Boolean {
                return size > 40
            }
        }
    )

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noRedirectClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var resolvedRemoteBaseUrl: String? = null

    fun getBaseUrl(): String {
        val configured = AppConfig.getOlaMoviesBaseUrl()
        if (configured.isNotBlank() && configured != AppConfig.DEFAULT_OLAMOVIES_BASE_URL) {
            return configured.trimEnd('/')
        }
        return (resolvedRemoteBaseUrl ?: configured.ifBlank { AppConfig.DEFAULT_OLAMOVIES_BASE_URL }).trimEnd('/')
    }

    /**
     * Checks whether the target URL host is allowed by the Cloudflare Worker
     * (`olamovies.*` and `ol-am.top`).
     */
    fun isWorkerAllowedHost(url: String): Boolean {
        return try {
            val host = URI(url).host?.lowercase() ?: return false
            host.contains("olamovies.") ||
                host == "ol-am.top" ||
                host.endsWith(".ol-am.top")
        } catch (_: Exception) {
            url.contains("olamovies.", ignoreCase = true) || url.contains("ol-am.top", ignoreCase = true)
        }
    }

    /**
     * Wraps an `olamovies.*` or `ol-am.top` target URL in the Cloudflare Worker URL.
     */
    fun buildWorkerUrl(targetUrl: String): String {
        val encoded = URLEncoder.encode(targetUrl, "UTF-8")
        return "$WORKER_BASE_URL/?url=$encoded&k=$WORKER_KEY"
    }

    /**
     * Converts relative or OlaMovies-hosted poster URLs into Worker-proxied URLs
     * so Coil can load them without Cloudflare 403 challenges.
     */
    fun normalizePosterUrl(rawUrl: String?, baseUrl: String = getBaseUrl()): String {
        val raw = (rawUrl ?: "").trim()
        if (raw.isBlank() || raw.startsWith("data:")) return ""
        val absolute = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "$baseUrl$raw"
            else -> "$baseUrl/$raw"
        }
        if (absolute.contains("falling-wildflower-9a91.samdem612.workers.dev")) {
            return absolute
        }
        return if (isWorkerAllowedHost(absolute)) {
            buildWorkerUrl(absolute)
        } else {
            absolute
        }
    }

    private fun isCloudflareChallenge(status: Int, html: String): Boolean {
        if (status == 403 || status == 503) return true
        if (html.isBlank()) return false
        return html.contains("<title>Just a moment...</title>", ignoreCase = true) ||
            html.contains("cf-browser-verification", ignoreCase = true) ||
            html.contains("challenge-platform", ignoreCase = true) ||
            html.contains("Attention Required! | Cloudflare", ignoreCase = true)
    }

    private fun workerGet(
        targetUrl: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Pair<Int, String> {
        val finalUrl = if (isWorkerAllowedHost(targetUrl)) buildWorkerUrl(targetUrl) else targetUrl
        val reqBuilder = Request.Builder().url(finalUrl)
        BROWSER_HEADERS.forEach { (k, v) -> reqBuilder.header(k, v) }
        reqBuilder.header("x-proxy-key", WORKER_KEY)
        extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
        return httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            Pair(resp.code, resp.body?.string() ?: "")
        }
    }

    private fun directGet(
        targetUrl: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Pair<Int, String> {
        val reqBuilder = Request.Builder().url(targetUrl)
        BROWSER_HEADERS.forEach { (k, v) -> reqBuilder.header(k, v) }
        extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
        return httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            Pair(resp.code, resp.body?.string() ?: "")
        }
    }

    private fun maybeRefreshRemoteBaseUrl() {
        if (resolvedRemoteBaseUrl != null) return
        try {
            val req = Request.Builder().url(URLS_JSON).header("User-Agent", USER_AGENT).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val olaUrl = json.optJSONObject("olamovies")?.optString("url")?.trim()?.trimEnd('/')
                    if (!olaUrl.isNullOrBlank() && olaUrl.startsWith("http")) {
                        resolvedRemoteBaseUrl = olaUrl
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // =========================================================================
    // STAGE 1: HOME CATALOG, SEARCH & DETAILS / QUALITY LIST
    // =========================================================================

    private fun cardImage(anchor: Element, baseUrl: String): String {
        val imgs = anchor.select("img")
        for (img in imgs) {
            val src = img.attr("src").trim()
            if (src.isNotBlank() && !src.startsWith("data:")) {
                return normalizePosterUrl(src, baseUrl)
            }
            val srcset = img.attr("srcset").ifBlank { img.attr("srcSet") }.trim()
            if (srcset.isNotBlank()) {
                val m = Pattern.compile("[?&]url=([^&\\s]+)").matcher(srcset)
                if (m.find()) {
                    val encoded = m.group(1) ?: ""
                    try {
                        val decoded = URLDecoder.decode(encoded, "UTF-8")
                        return normalizePosterUrl(decoded, baseUrl)
                    } catch (_: Exception) {
                        return normalizePosterUrl(encoded, baseUrl)
                    }
                }
            }
        }
        return ""
    }

    private fun cardTitle(anchor: Element): String {
        val clamp = anchor.selectFirst("span.line-clamp-3")?.text()?.trim().orEmpty()
        if (clamp.isNotBlank()) return clamp
        var best = ""
        val spans = anchor.select("span")
        for (sp in spans) {
            if (sp.select("img").isNotEmpty()) continue
            val t = sp.text().trim()
            if (t.length > best.length) best = t
        }
        return best
    }

    fun parseCards(html: String, baseUrl: String = getBaseUrl()): List<VideoItem> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val out = mutableListOf<VideoItem>()
        val seen = mutableSetOf<String>()

        val anchors = doc.select("a[href^=/movies/], a[href^=/tv-series/], a[href^=/tv-shows/]")
        for (a in anchors) {
            val href = a.attr("href").trim()
            if (href.isBlank() || seen.contains(href)) continue
            if (Pattern.compile("^/(movies|tv-series|tv-shows)/?$").matcher(href).matches()) continue

            val title = cardTitle(a)
            if (title.isBlank()) continue

            seen.add(href)
            val image = cardImage(a, baseUrl)
            val isSeries = href.startsWith("/tv-series/") || href.startsWith("/tv-shows/")
            out.add(
                VideoItem(
                    id = "olamovies::$href",
                    title = title,
                    uploaderName = if (isSeries) "OlaMovies • TV Series" else "OlaMovies • Movie",
                    thumbnailUrl = image,
                    providerId = "olamovies",
                    description = "$baseUrl$href"
                )
            )
        }
        return out
    }

    suspend fun getHome(page: Int = 1, limit: Int = 24, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val results = mutableListOf<VideoItem>()
        val seenIds = mutableSetOf<String>()

        val filters = if (page <= 1) {
            listOf("", "/categories/movies", "/categories/tv-series", "/quality/2160p")
        } else {
            listOf("", "/categories/movies", "/categories/tv-series")
        }

        for (filter in filters) {
            if (results.size >= limit) break
            try {
                val url = if (page > 1) "$baseUrl$filter?page=$page" else "$baseUrl$filter"
                val (status, html) = workerGet(url, mapOf("Referer" to "$baseUrl/"))
                if (isCloudflareChallenge(status, html)) {
                    Log.w(TAG, "Cloudflare challenge on getHome($url): HTTP $status")
                    continue
                }
                val parsed = parseCards(html, baseUrl)
                for (item in parsed) {
                    if (seenIds.add(item.id)) {
                        results.add(item)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "getHome error for filter '$filter': ${e.message}")
            }
        }

        if (results.isEmpty() && page == 1) {
            maybeRefreshRemoteBaseUrl()
            val updatedBase = getBaseUrl()
            if (updatedBase != baseUrl) {
                try {
                    val (status, html) = workerGet(updatedBase, mapOf("Referer" to "$updatedBase/"))
                    if (!isCloudflareChallenge(status, html)) {
                        results.addAll(parseCards(html, updatedBase))
                    }
                } catch (_: Exception) {
                }
            }
        }

        results.take(limit)
    }

    suspend fun search(query: String, limit: Int = 24, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val baseUrl = getBaseUrl()
        val out = mutableListOf<VideoItem>()
        val seen = mutableSetOf<String>()

        try {
            val apiUrl = "$baseUrl/api/search?q=${URLEncoder.encode(q, "UTF-8")}&limit=${limit.coerceAtLeast(24)}"
            val (status, body) = workerGet(
                apiUrl,
                mapOf(
                    "Accept" to "application/json",
                    "Referer" to "$baseUrl/"
                )
            )
            if (!isCloudflareChallenge(status, body) && body.isNotBlank()) {
                val json = JSONObject(body)
                val items = json.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val title = it.optString("title").trim()
                        val urlPath = it.optString("url").trim()
                        if (title.isBlank() || urlPath.isBlank()) continue
                        val normalizedPath = if (urlPath.startsWith("http")) {
                            try { URI(urlPath).path ?: urlPath } catch (_: Exception) { urlPath }
                        } else if (!urlPath.startsWith("/")) {
                            "/$urlPath"
                        } else {
                            urlPath
                        }
                        if (!seen.add(normalizedPath)) continue
                        val rawImg = it.optString("poster").ifBlank { it.optString("image") }
                        val image = normalizePosterUrl(rawImg, baseUrl)
                        val isSeries = normalizedPath.startsWith("/tv-series/") || normalizedPath.startsWith("/tv-shows/")
                        out.add(
                            VideoItem(
                                id = "olamovies::$normalizedPath",
                                title = title,
                                uploaderName = if (isSeries) "OlaMovies • TV Series" else "OlaMovies • Movie",
                                thumbnailUrl = image,
                                providerId = "olamovies",
                                description = "$baseUrl$normalizedPath"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "search API error for '$q': ${e.message}")
        }

        out.take(limit)
    }

    /**
     * Unescapes Next.js `__next_f.push([1,"..."])` chunks and concatenates them.
     */
    private fun extractFlightBlob(html: String): String {
        val chunks = mutableListOf<String>()
        val re = Pattern.compile("self\\.__next_f\\.push\\(\\[1,\"((?:[^\"\\\\]|\\\\.)*)\"\\]\\)</script>")
        val m = re.matcher(html)
        while (m.find()) {
            val raw = m.group(1) ?: continue
            try {
                // Parse via JSONArray to unescape standard JSON string escapes cleanly
                val unescaped = JSONArray("[\"$raw\"]").getString(0)
                chunks.add(unescaped)
            } catch (_: Exception) {
                chunks.add(
                    raw.replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n")
                )
            }
        }
        return chunks.joinToString("\n")
    }

    /**
     * Extracts `"kind":"release"` JSON objects from the Next.js flight blob using
     * balanced brace-depth scanning (exact port of `meta.js` `extractReleaseBlocks`).
     */
    private fun extractReleaseBlocks(blob: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        val needle = "\"kind\":\"release\""
        var idx = 0
        while (true) {
            val pos = blob.indexOf(needle, idx)
            if (pos == -1) break
            var start = pos
            while (start > 0 && blob[start] != '{') start--
            var depth = 0
            var inStr = false
            var esc = false
            var i = start
            while (i < blob.length) {
                val ch = blob[i]
                if (inStr) {
                    if (esc) {
                        esc = false
                    } else if (ch == '\\') {
                        esc = true
                    } else if (ch == '"') {
                        inStr = false
                    }
                } else {
                    if (ch == '"') {
                        inStr = true
                    } else if (ch == '{') {
                        depth++
                    } else if (ch == '}') {
                        depth--
                        if (depth == 0) {
                            i++
                            break
                        }
                    }
                }
                i++
            }
            val rawJson = blob.substring(start, i.coerceAtMost(blob.length))
            try {
                out.add(JSONObject(rawJson))
            } catch (_: Exception) {
            }
            idx = i
        }
        return out
    }

    private fun extractPath(linkOrId: String): String {
        val cleaned = linkOrId.removePrefix("olamovies::").trim()
        return when {
            cleaned.startsWith("http://") || cleaned.startsWith("https://") -> {
                try {
                    val uri = URI(cleaned)
                    uri.path ?: cleaned
                } catch (_: Exception) {
                    cleaned
                }
            }
            cleaned.startsWith("/") -> cleaned
            else -> "/$cleaned"
        }
    }

    fun getCachedMeta(linkOrId: String): OlaMetaResult? {
        val path = extractPath(linkOrId)
        return metaCache[path]
    }

    /**
     * Stage 1 Details parser: Fetches the movie/series page via Worker, parses Next.js Flight
     * release blocks into quality/episode links, and enriches metadata.
     */
    suspend fun getMeta(linkOrId: String, context: Context? = null): OlaMetaResult = withContext(Dispatchers.IO) {
        val path = extractPath(linkOrId)
        metaCache[path]?.let { cached ->
            if (cached.directLinks.isNotEmpty()) return@withContext cached
        }

        val baseUrl = getBaseUrl()
        val pageUrl = "$baseUrl$path"
        val (status, html) = workerGet(pageUrl, mapOf("Referer" to "$baseUrl/"))

        if (isCloudflareChallenge(status, html)) {
            throw OlaMoviesException("OlaMovies is showing a browser check (HTTP $status). Try refreshing or updating the OlaMovies domain in Settings.")
        }
        if (status !in 200..299 || html.isBlank()) {
            throw OlaMoviesException("Failed to load OlaMovies details page (HTTP $status).")
        }

        val doc = Jsoup.parse(html)
        val rawTitle = doc.selectFirst("title")?.text()?.trim().orEmpty()
        var title = rawTitle
            .replace(Regex("\\s*\\|\\s*OlaMovies.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*Download.*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { path.substringAfterLast('/').replace('-', ' ') }

        var synopsis = doc.selectFirst("meta[name=description]")?.attr("content")?.trim().orEmpty()
        val isSeries = Regex("^/(tv-series|tv-shows)/").containsMatchIn(path) ||
            Regex("\\bseason\\b", RegexOption.IGNORE_CASE).containsMatchIn(title)

        val blob = extractFlightBlob(html)
        val releases = extractReleaseBlocks(blob)

        val allDirectLinks = mutableListOf<OlaDirectLink>()
        for (rel in releases) {
            val qMatch = Pattern.compile("(\\d{3,4}p)", Pattern.CASE_INSENSITIVE).matcher(
                rel.optString("resolution").ifBlank { rel.optString("heading") }
            )
            val quality = if (qMatch.find()) qMatch.group(1) ?: "HD" else "HD"
            val specsList = mutableListOf<String>()
            val specsArr = rel.optJSONArray("labelSpecs")
            if (specsArr != null) {
                for (i in 0 until specsArr.length()) {
                    val s = specsArr.optString(i).trim()
                    if (s.isNotBlank()) specsList.add(s)
                }
            }
            val specs = specsList.joinToString(" · ")
            val heading = rel.optString("heading").trim()
            val groupTitle = heading.ifBlank {
                listOf(quality, specs).filter { it.isNotBlank() }.joinToString(" — ").ifBlank { "Downloads" }
            }

            fun collectItems(arr: JSONArray?, isPack: Boolean = false) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val it = arr.optJSONObject(i) ?: continue
                    val u = it.optString("url").trim()
                    val broken = it.optBoolean("broken", false)
                    if (u.isBlank() || broken) continue

                    val epNum = if (it.has("episode") && !it.isNull("episode")) it.optInt("episode", -1).takeIf { n -> n >= 0 } else null
                    val epLabel = when {
                        epNum != null -> "Episode $epNum"
                        it.optString("label").isNotBlank() -> it.optString("label").trim()
                        it.optJSONObject("face")?.optString("title")?.isNotBlank() == true -> it.optJSONObject("face")!!.optString("title").trim()
                        it.optString("filename").isNotBlank() -> it.optString("filename").trim()
                        isPack -> "Complete Pack"
                        else -> "Play / Download"
                    }
                    val size = it.optString("filesize").trim().takeIf { s -> s.isNotBlank() }
                    val displayLabel = buildString {
                        if (isPack && !epLabel.contains("Pack", ignoreCase = true)) {
                            append("Pack: ")
                        }
                        append(epLabel)
                        if (size != null) {
                            append(" [$size]")
                        }
                    }
                    allDirectLinks.add(
                        OlaDirectLink(
                            title = displayLabel,
                            link = u,
                            quality = quality,
                            groupTitle = groupTitle,
                            episodeNumber = epNum,
                            filesize = size
                        )
                    )
                }
            }

            collectItems(rel.optJSONArray("files"), isPack = false)
            collectItems(rel.optJSONArray("episodes"), isPack = false)
            collectItems(rel.optJSONArray("packs"), isPack = true)
        }

        // Best-effort poster & IMDb ID lookup via /api/search + Cinemeta (matching meta.js)
        var poster = ""
        var background = ""
        var imdbId = ""
        val cast = mutableListOf<String>()
        var rating = ""

        val slug = path.trimEnd('/').substringAfterLast('/')
            .replace(Regex("-(19|20)\\d{2}$"), "")
            .replace('-', ' ')

        if (slug.isNotBlank()) {
            try {
                val searchUrl = "$baseUrl/api/search?q=${URLEncoder.encode(slug, "UTF-8")}&limit=8"
                val (_, sBody) = workerGet(
                    searchUrl,
                    mapOf("Accept" to "application/json", "Referer" to "$baseUrl/")
                )
                if (sBody.isNotBlank()) {
                    val sJson = JSONObject(sBody)
                    val items = sJson.optJSONArray("items")
                    if (items != null) {
                        var hit: JSONObject? = null
                        for (i in 0 until items.length()) {
                            val obj = items.optJSONObject(i) ?: continue
                            if (obj.optString("url") == path) {
                                hit = obj
                                break
                            }
                        }
                        if (hit == null && items.length() > 0) {
                            hit = items.optJSONObject(0)
                        }
                        if (hit != null) {
                            val rawImg = hit.optString("poster").ifBlank { hit.optString("image") }
                            poster = normalizePosterUrl(rawImg, baseUrl)
                            val im = Pattern.compile("/posters/(tt\\d+)/").matcher(rawImg)
                            if (im.find()) {
                                imdbId = im.group(1) ?: ""
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (imdbId.isBlank()) {
            val m = Pattern.compile("(tt\\d{7,9})").matcher(html)
            if (m.find()) imdbId = m.group(1) ?: ""
        }

        if (imdbId.isNotBlank()) {
            try {
                val cmType = if (isSeries) "series" else "movie"
                val (_, cmBody) = directGet("https://v3-cinemeta.strem.io/meta/$cmType/$imdbId.json")
                if (cmBody.isNotBlank()) {
                    val cm = JSONObject(cmBody).optJSONObject("meta")
                    if (cm != null) {
                        val cmPoster = cm.optString("poster").trim()
                        val cmBg = cm.optString("background").trim()
                        if (cmPoster.isNotBlank() && poster.isBlank()) poster = cmPoster
                        if (cmBg.isNotBlank()) background = cmBg
                        val cmDesc = cm.optString("description").trim()
                        if (cmDesc.isNotBlank()) synopsis = cmDesc
                        rating = cm.optString("imdbRating").trim()
                        val castArr = cm.optJSONArray("cast")
                        if (castArr != null) {
                            for (i in 0 until castArr.length().coerceAtMost(6)) {
                                val actor = castArr.optString(i).trim()
                                if (actor.isNotBlank()) cast.add(actor)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            if (poster.isBlank()) {
                poster = "https://images.metahub.space/poster/medium/$imdbId/img"
            }
        }

        val result = OlaMetaResult(
            path = path,
            title = title,
            synopsis = synopsis,
            posterUrl = poster,
            backdropUrl = background.ifBlank { poster },
            imdbId = imdbId,
            isSeries = isSeries,
            directLinks = allDirectLinks,
            cast = cast,
            rating = rating
        )
        metaCache[path] = result
        result
    }

    // =========================================================================
    // STAGE 2: SHORTENER & HOSTER RESOLUTION (HubCloud, Gofile, Google Drive)
    // =========================================================================

    private fun extractNextHop(html: String): String? {
        if (html.isBlank()) return null

        // 1. <meta http-equiv="refresh" content="0; url=...">
        val meta = Pattern.compile(
            "<meta[^>]+http-equiv=[\"']refresh[\"'][^>]+content=[\"']\\d+;\\s*url=([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        ).matcher(html)
        if (meta.find()) return meta.group(1)?.trim()

        // 2. JS location redirect
        val jsLoc = Pattern.compile(
            "(?:window\\.)?location(?:\\.href|\\.replace)?\\s*(?:=|\\()\\s*[\"'](https?://[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        ).matcher(html)
        if (jsLoc.find()) return jsLoc.group(1)?.trim()

        val jsVar = Pattern.compile(
            "(?:var|let|const)\\s+(?:url|redirectUrl|nextUrl|goUrl)\\s*=\\s*[\"'](https?://[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        ).matcher(html)
        if (jsVar.find()) return jsVar.group(1)?.trim()

        // 3. Double base64 encoded link (like HubCloud uses)
        val b642 = Pattern.compile("(?:var|let|const)\\s+\\w+\\s*=\\s*atob\\(atob\\([\"']([A-Za-z0-9+/=]+)[\"']\\)\\)").matcher(html)
        if (b642.find()) {
            try {
                val first = String(Base64.decode(b642.group(1), Base64.DEFAULT), Charsets.UTF_8)
                val u = String(Base64.decode(first, Base64.DEFAULT), Charsets.UTF_8).trim()
                if (u.startsWith("http")) return u
            } catch (_: Exception) {
            }
        }

        // 4. Single base64 encoded link
        val b641 = Pattern.compile("(?:var|let|const)\\s+\\w+\\s*=\\s*atob\\([\"']([A-Za-z0-9+/=]{40,})[\"']\\)").matcher(html)
        if (b641.find()) {
            try {
                val u = String(Base64.decode(b641.group(1), Base64.DEFAULT), Charsets.UTF_8).trim()
                if (u.startsWith("http")) return u
            } catch (_: Exception) {
            }
        }

        // 5. Anchor pointing to a known hoster
        val a = Pattern.compile(
            "<a[^>]+href=[\"'](https?://(?:hubcloud|hubdrive|gofile|drive\\.google)[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        ).matcher(html)
        if (a.find()) return a.group(1)?.trim()

        // 6. Any hubcloud/gofile/drive URL anywhere in the page source
        val any = Pattern.compile(
            "https?://(?:(?:hubcloud|hubdrive)[^\\s\"'<>]+|gofile\\.io/d/[A-Za-z0-9]+|drive\\.google\\.com[^\\s\"'<>]+)",
            Pattern.CASE_INSENSITIVE
        ).matcher(html)
        if (any.find()) return any.group(0)?.trim()

        return null
    }

    /**
     * Headless WebView fallback on the phone when a hoster or shortener challenges OkHttp's TLS/Cloudflare.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchPageWithWebViewFallback(
        context: Context,
        targetUrl: String,
        referer: String
    ): Pair<String, String> = withTimeoutOrNull(6_000L) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                var completed = false
                val webView = WebView(context.applicationContext)
                val handler = Handler(Looper.getMainLooper())

                fun finish(finalUrl: String, html: String) {
                    if (completed) return
                    completed = true
                    try {
                        webView.stopLoading()
                        webView.destroy()
                    } catch (_: Exception) {
                    }
                    if (cont.isActive) cont.resume(Pair(finalUrl, html))
                }

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = USER_AGENT
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val nextUrl = request?.url?.toString().orEmpty()
                        if (nextUrl.contains("hubcloud", ignoreCase = true) ||
                            nextUrl.contains("hubdrive", ignoreCase = true) ||
                            nextUrl.contains("gofile.io", ignoreCase = true) ||
                            nextUrl.contains("drive.google.com", ignoreCase = true)
                        ) {
                            finish(nextUrl, "")
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        val currentUrl = url ?: targetUrl
                        handler.postDelayed({
                            if (completed) return@postDelayed
                            try {
                                view?.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { rawResult ->
                                    val unescaped = try {
                                        if (rawResult != null && rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
                                            JSONArray("[$rawResult]").getString(0)
                                        } else {
                                            rawResult.orEmpty()
                                        }
                                    } catch (_: Exception) {
                                        rawResult.orEmpty()
                                    }
                                    if (!unescaped.contains("Just a moment...", ignoreCase = true)) {
                                        finish(currentUrl, unescaped)
                                    }
                                }
                            } catch (_: Exception) {
                                finish(currentUrl, "")
                            }
                        }, 1100L)
                    }
                }

                val timeoutRunnable = Runnable {
                    if (!completed) {
                        try {
                            val currentUrl = webView.url ?: targetUrl
                            webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { rawResult ->
                                val unescaped = try {
                                    if (rawResult != null && rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
                                        JSONArray("[$rawResult]").getString(0)
                                    } else {
                                        rawResult.orEmpty()
                                    }
                                } catch (_: Exception) {
                                    rawResult.orEmpty()
                                }
                                finish(currentUrl, unescaped)
                            }
                        } catch (_: Exception) {
                            finish(targetUrl, "")
                        }
                    }
                }
                handler.postDelayed(timeoutRunnable, 5_000L)

                cont.invokeOnCancellation {
                    handler.removeCallbacksAndMessages(null)
                    try {
                        webView.stopLoading()
                        webView.destroy()
                    } catch (_: Exception) {
                    }
                }

                webView.loadUrl(targetUrl, mapOf("Referer" to referer))
            }
        }
    } ?: Pair(targetUrl, "")

    private suspend fun fetchShortenerPage(
        url: String,
        baseUrl: String,
        context: Context?
    ): Triple<Int, String, String> {
        // 1. Try Worker first if host is olamovies.* or ol-am.top
        if (isWorkerAllowedHost(url)) {
            try {
                val (status, html) = workerGet(url, mapOf("Referer" to "$baseUrl/"))
                if (status in 200..399 && !isCloudflareChallenge(status, html) && html.isNotBlank()) {
                    return Triple(status, url, html)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Worker fetchShortenerPage note for $url: ${e.message}")
            }
        }

        // 2. Try direct OkHttp from the phone (captures 301/302 final URL automatically)
        try {
            val reqBuilder = Request.Builder().url(url)
            BROWSER_HEADERS.forEach { (k, v) -> reqBuilder.header(k, v) }
            reqBuilder.header("Referer", "$baseUrl/")
            val cookieStr = try { CookieManager.getInstance().getCookie(url) } catch (_: Exception) { null }
            if (!cookieStr.isNullOrBlank()) {
                reqBuilder.header("Cookie", cookieStr)
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                val html = resp.body?.string().orEmpty()
                if (!isCloudflareChallenge(resp.code, html)) {
                    return Triple(resp.code, finalUrl, html)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct fetchShortenerPage note for $url: ${e.message}")
        }

        // 3. WebView fallback on phone if Cloudflare challenged
        if (context != null) {
            val (finalUrl, html) = fetchPageWithWebViewFallback(context, url, "$baseUrl/")
            if (html.isNotBlank() || finalUrl != url) {
                return Triple(200, finalUrl, html)
            }
        }

        return Triple(403, url, "")
    }

    /**
     * Port of `gofileExtractor(id)` from `stream.js`.
     * Obtains a guest accountToken and resolves the direct Gofile download URL + required headers.
     */
    private fun gofileExtractor(id: String): Pair<String, String> {
        val cleanId = id.trim().trim('/')
        if (cleanId.isBlank()) return Pair("", "")
        try {
            // 1. Create guest account token
            val accReq = Request.Builder()
                .url("https://api.gofile.io/accounts")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://gofile.io")
                .header("Referer", "https://gofile.io/")
                .build()

            val token = httpClient.newCall(accReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                JSONObject(body).optJSONObject("data")?.optString("token").orEmpty()
            }
            if (token.isBlank()) return Pair("", "")

            // 2. Get websiteToken (wt)
            var wt = "4fd6sg89d7s6"
            try {
                val (_, wtJs) = directGet("https://gofile.io/js/wt.obf.js", mapOf("Referer" to "https://gofile.io/"))
                val m = Pattern.compile("wt\\s*[:=]\\s*[\"']([a-zA-Z0-9_-]{6,32})[\"']").matcher(wtJs)
                if (m.find()) {
                    wt = m.group(1) ?: wt
                }
            } catch (_: Exception) {
            }

            // 3. Fetch folder contents
            val contentsUrl = "https://api.gofile.io/contents/$cleanId?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1"
            val contentReq = Request.Builder()
                .url(contentsUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Origin", "https://gofile.io")
                .header("Referer", "https://gofile.io/")
                .header("Authorization", "Bearer $token")
                .header("X-Website-Token", wt)
                .build()

            httpClient.newCall(contentReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val children = json.optJSONObject("data")?.optJSONObject("children") ?: return Pair("", "")
                val keys = children.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = children.optJSONObject(key) ?: continue
                    if (child.optString("type") == "file") {
                        val fileLink = child.optString("link").trim()
                        if (fileLink.isNotBlank()) {
                            return Pair(fileLink, token)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "gofileExtractor error for $cleanId: ${e.message}")
        }
        return Pair("", "")
    }

    /**
     * Port of `resolveDriveLink(driveUrl)` from `stream.js`.
     */
    private fun resolveDriveLink(driveUrl: String): OlaResolvedStream? {
        val idMatch = Pattern.compile("/d/([a-zA-Z0-9_-]+)|[?&]id=([a-zA-Z0-9_-]+)").matcher(driveUrl)
        if (!idMatch.find()) return null
        val fileId = idMatch.group(1) ?: idMatch.group(2) ?: return null
        try {
            val uc = "https://drive.google.com/uc?export=download&id=$fileId"
            val (_, html) = directGet(uc, mapOf("Referer" to "https://drive.google.com/"))
            val confirmMatch = Pattern.compile("confirm=([a-zA-Z0-9_-]+)|name=\"confirm\"\\s+value=\"([^\"]+)\"").matcher(html)
            val confirm = if (confirmMatch.find()) (confirmMatch.group(1) ?: confirmMatch.group(2) ?: "t") else "t"
            val direct = "https://drive.google.com/uc?export=download&confirm=$confirm&id=$fileId"
            return OlaResolvedStream(
                server = "Google Drive",
                link = direct,
                type = "mkv",
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://drive.google.com/"
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "resolveDriveLink error: ${e.message}")
            return null
        }
    }

    /**
     * Port of `hubcloudExtractor(link)` from `stream.js`.
     * Runs directly from the phone (with WebView fallback if Cloudflare challenges).
     */
    private suspend fun hubcloudExtractor(link: String, context: Context?): List<OlaResolvedStream> {
        val baseUrl = getBaseUrl()
        val hostBase = link.split("/").take(3).joinToString("/")
        val streamLinks = mutableListOf<OlaResolvedStream>()

        var (status, vLinkHtml) = try {
            directGet(link, mapOf("Referer" to "$baseUrl/"))
        } catch (e: Exception) {
            Pair(500, "")
        }

        if ((isCloudflareChallenge(status, vLinkHtml) || vLinkHtml.isBlank()) && context != null) {
            val (_, wvHtml) = fetchPageWithWebViewFallback(context, link, "$baseUrl/")
            if (wvHtml.isNotBlank()) {
                status = 200
                vLinkHtml = wvHtml
            }
        }

        if (isCloudflareChallenge(status, vLinkHtml)) {
            throw OlaMoviesException("HubCloud is showing a Cloudflare check (HTTP $status).")
        }
        if (vLinkHtml.isBlank()) {
            throw OlaMoviesException("HubCloud returned an empty page (HTTP $status).")
        }

        val vLinkDoc = Jsoup.parse(vLinkHtml)

        // Check for direct Gofile buttons on the initial HubCloud page
        val gofileBtns = vLinkDoc.select("a[href*=gofile.io]")
        for (el in gofileBtns) {
            val href = el.attr("href").trim()
            val gfId = href.substringAfter("/d/", "").substringBefore("/").substringBefore("?")
            if (gfId.isNotBlank()) {
                val (gfLink, gfToken) = gofileExtractor(gfId)
                if (gfLink.isNotBlank()) {
                    streamLinks.add(
                        OlaResolvedStream(
                            server = "Gofile",
                            link = gfLink,
                            type = "mkv",
                            headers = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to "https://gofile.io/",
                                "Cookie" to "accountToken=$gfToken"
                            )
                        )
                    )
                }
            }
        }

        // Find intermediate vcloud / gamerxyt link
        var vcloudLink = ""
        val b642 = Pattern.compile("var\\s+\\w+\\s*=\\s*atob\\(atob\\(['\"]([^'\"]+)['\"]\\)\\)").matcher(vLinkHtml)
        if (b642.find()) {
            try {
                val first = String(Base64.decode(b642.group(1), Base64.DEFAULT), Charsets.UTF_8)
                vcloudLink = String(Base64.decode(first, Base64.DEFAULT), Charsets.UTF_8).trim()
            } catch (_: Exception) {
            }
        }

        if (vcloudLink.isBlank()) {
            val vLinkRedirect = Pattern.compile("var\\s+url\\s*=\\s*'([^']+)';").matcher(vLinkHtml)
            if (vLinkRedirect.find()) {
                val raw = vLinkRedirect.group(1).orEmpty()
                val rMatch = Pattern.compile("r=([A-Za-z0-9+/=]+)").matcher(raw)
                vcloudLink = if (rMatch.find()) {
                    try {
                        String(Base64.decode(rMatch.group(1), Base64.DEFAULT), Charsets.UTF_8).trim()
                    } catch (_: Exception) {
                        raw
                    }
                } else {
                    raw
                }
            }
        }

        if (vcloudLink.isBlank()) {
            val dlAnchor = vLinkDoc.selectFirst(".fa-file-download.fa-lg")?.parent()?.attr("href")?.trim()
                ?: vLinkDoc.selectFirst("a#download")?.attr("href")?.trim()
                ?: vLinkDoc.selectFirst("a.btn-primary[href]")?.attr("href")?.trim()
            if (!dlAnchor.isNullOrBlank()) {
                vcloudLink = dlAnchor
            }
        }

        if (vcloudLink.startsWith("/")) {
            vcloudLink = "$hostBase$vcloudLink"
        }

        if (vcloudLink.isBlank()) {
            if (streamLinks.isNotEmpty()) return streamLinks
            val hasCaptcha = vLinkHtml.contains("g-recaptcha", ignoreCase = true) ||
                vLinkHtml.contains("cf-turnstile", ignoreCase = true) ||
                vLinkHtml.contains("h-captcha", ignoreCase = true)
            if (hasCaptcha) {
                throw OlaMoviesException("HubCloud needs captcha verification.")
            }
            throw OlaMoviesException("Could not find download button on HubCloud page.")
        }

        // Step 2: Fetch vcloudLink page
        var (vStatus, vcloudHtml) = try {
            directGet(vcloudLink, mapOf("Referer" to link))
        } catch (_: Exception) {
            Pair(500, "")
        }

        if ((isCloudflareChallenge(vStatus, vcloudHtml) || vcloudHtml.isBlank()) && context != null) {
            val (_, wvHtml) = fetchPageWithWebViewFallback(context, vcloudLink, link)
            if (wvHtml.isNotBlank()) {
                vcloudHtml = wvHtml
            }
        }

        if (vcloudHtml.isNotBlank()) {
            val doc = Jsoup.parse(vcloudHtml)
            val buttons = doc.select(".btn-success.btn-lg.h6, .btn-danger, .btn-secondary, a.btn")
            for (el in buttons) {
                var btnLink = el.attr("href").trim()
                if (btnLink.isBlank() || !btnLink.startsWith("http")) continue

                when {
                    btnLink.contains("pixeld", ignoreCase = true) -> {
                        if (!btnLink.contains("api", ignoreCase = true)) {
                            val token = btnLink.trimEnd('/').substringAfterLast('/')
                            val pBase = btnLink.split("/").take(3).joinToString("/")
                            btnLink = "$pBase/api/file/$token?download"
                        }
                        streamLinks.add(
                            OlaResolvedStream(
                                server = "Pixeldrain",
                                link = btnLink,
                                type = "mkv",
                                headers = mapOf("User-Agent" to USER_AGENT)
                            )
                        )
                    }
                    (btnLink.contains(".dev", ignoreCase = true) || btnLink.contains("workers.dev", ignoreCase = true)) &&
                        !btnLink.contains("/?id=", ignoreCase = true) -> {
                        streamLinks.add(
                            OlaResolvedStream(
                                server = "CF Worker",
                                link = btnLink,
                                type = "mkv",
                                headers = mapOf("User-Agent" to USER_AGENT)
                            )
                        )
                    }
                    btnLink.contains("cloudflarestorage", ignoreCase = true) -> {
                        streamLinks.add(
                            OlaResolvedStream(
                                server = "CF Storage",
                                link = btnLink,
                                type = "mkv",
                                headers = mapOf("User-Agent" to USER_AGENT)
                            )
                        )
                    }
                    btnLink.contains("fastdl", ignoreCase = true) || btnLink.contains("fsl.", ignoreCase = true) -> {
                        streamLinks.add(
                            OlaResolvedStream(
                                server = "FastDl",
                                link = btnLink,
                                type = "mkv",
                                headers = mapOf("User-Agent" to USER_AGENT)
                            )
                        )
                    }
                    btnLink.contains("hubcdn", ignoreCase = true) && !btnLink.contains("/?id=", ignoreCase = true) -> {
                        streamLinks.add(
                            OlaResolvedStream(
                                server = "HubCdn",
                                link = btnLink,
                                type = "mkv",
                                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to vcloudLink)
                            )
                        )
                    }
                    btnLink.contains("gofile.io", ignoreCase = true) -> {
                        val gfId = btnLink.substringAfter("/d/", "").substringBefore("/").substringBefore("?")
                        if (gfId.isNotBlank()) {
                            val (gfLink, gfToken) = gofileExtractor(gfId)
                            if (gfLink.isNotBlank()) {
                                streamLinks.add(
                                    OlaResolvedStream(
                                        server = "Gofile",
                                        link = gfLink,
                                        type = "mkv",
                                        headers = mapOf(
                                            "User-Agent" to USER_AGENT,
                                            "Referer" to "https://gofile.io/",
                                            "Cookie" to "accountToken=$gfToken"
                                        )
                                    )
                                )
                            }
                        }
                    }
                    btnLink.contains("hubcloud", ignoreCase = true) || btnLink.contains("/?id=", ignoreCase = true) -> {
                        try {
                            var current = btnLink
                            for (hop in 0 until 2) {
                                val req = Request.Builder()
                                    .url(current)
                                    .header("User-Agent", USER_AGENT)
                                    .header("Referer", vcloudLink)
                                    .build()
                                val loc = noRedirectClient.newCall(req).execute().use { resp ->
                                    resp.header("Location")
                                }
                                if (loc.isNullOrBlank()) break
                                current = if (loc.startsWith("http")) loc else URI(current).resolve(loc).toString()
                            }
                            val finalLink = if (current.contains("googleusercontent", ignoreCase = true) && current.contains("link=")) {
                                URLDecoder.decode(current.substringAfter("link="), "UTF-8")
                            } else {
                                current
                            }
                            if (finalLink.startsWith("http") && finalLink != btnLink) {
                                streamLinks.add(
                                    OlaResolvedStream(
                                        server = "GDrive",
                                        link = finalLink,
                                        type = "mkv",
                                        headers = mapOf("User-Agent" to USER_AGENT)
                                    )
                                )
                            }
                        } catch (_: Exception) {
                        }
                    }
                    btnLink.contains(".mkv", ignoreCase = true) ||
                        btnLink.contains(".mp4", ignoreCase = true) ||
                        btnLink.contains("?token=", ignoreCase = true) -> {
                        streamLinks.add(
                            OlaResolvedStream(
                                server = "CF Worker",
                                link = btnLink,
                                type = "mkv",
                                headers = mapOf("User-Agent" to USER_AGENT)
                            )
                        )
                    }
                }
            }
        }

        // Deduplicate & sort by reliability priority (matching stream.js)
        val priority = mapOf(
            "CF Storage" to 1,
            "CF Worker" to 2,
            "Gofile" to 3,
            "Pixeldrain" to 4,
            "FastDl" to 5,
            "HubCdn" to 6,
            "GDrive" to 7
        )
        return streamLinks
            .distinctBy { it.link }
            .sortedBy { priority[it.server] ?: 9 }
    }

    /**
     * Resolves an OlaMovies shortener or hoster link (`ol-am.top`, `olamovies.*`, `hubcloud`, `gofile`, `drive.google`)
     * into a prioritized list of direct video URLs + headers for ExoPlayer.
     */
    suspend fun resolveStreamForLink(link: String, context: Context? = null): List<OlaResolvedStream> = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        var current = link.trim()
        if (current.isBlank()) {
            throw OlaMoviesException("Empty download link.")
        }

        // Follow shortener chain (up to 4 hops) if link is on ol-am.top or olamovies
        var hops = 0
        while (hops < 4 && (current.contains("ol-am.top", ignoreCase = true) || current.contains("olamovies.", ignoreCase = true))) {
            hops++
            val (status, finalUrl, html) = fetchShortenerPage(current, baseUrl, context)
            if (finalUrl != current && !finalUrl.contains("ol-am.top", ignoreCase = true) && !finalUrl.contains("olamovies.", ignoreCase = true)) {
                current = finalUrl
                break
            }
            if (isCloudflareChallenge(status, html)) {
                throw OlaMoviesException("Shortener blocked by Cloudflare check (HTTP $status).")
            }
            val next = extractNextHop(html)
                ?: throw OlaMoviesException("Could not resolve playable hoster link from shortener ($current).")
            current = if (next.startsWith("http")) {
                next
            } else {
                try {
                    URI(current).resolve(next).toString()
                } catch (_: Exception) {
                    next
                }
            }
        }

        // Dispatch to hoster extractor
        when {
            current.contains("hubcloud", ignoreCase = true) || current.contains("hubdrive", ignoreCase = true) -> {
                val streams = hubcloudExtractor(current, context)
                if (streams.isEmpty()) {
                    throw OlaMoviesException("HubCloud returned no playable video links.")
                }
                streams
            }
            current.contains("gofile.io", ignoreCase = true) -> {
                val gfId = current.substringAfter("/d/", "").substringBefore("/").substringBefore("?")
                if (gfId.isBlank()) throw OlaMoviesException("Invalid Gofile link: $current")
                val (gfLink, gfToken) = gofileExtractor(gfId)
                if (gfLink.isBlank()) throw OlaMoviesException("Gofile link is unavailable or expired.")
                listOf(
                    OlaResolvedStream(
                        server = "Gofile",
                        link = gfLink,
                        type = "mkv",
                        headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Referer" to "https://gofile.io/",
                            "Cookie" to "accountToken=$gfToken"
                        )
                    )
                )
            }
            current.contains("drive.google.com", ignoreCase = true) -> {
                val d = resolveDriveLink(current)
                    ?: throw OlaMoviesException("Could not resolve Google Drive download stream.")
                listOf(d)
            }
            current.endsWith(".mkv", ignoreCase = true) ||
                current.endsWith(".mp4", ignoreCase = true) ||
                current.contains(".mkv?", ignoreCase = true) ||
                current.contains(".mp4?", ignoreCase = true) -> {
                listOf(
                    OlaResolvedStream(
                        server = "Direct",
                        link = current,
                        type = if (current.contains(".mp4", ignoreCase = true)) "mp4" else "mkv",
                        headers = mapOf("User-Agent" to USER_AGENT)
                    )
                )
            }
            else -> {
                // Attempt HubCloud-style extraction as last resort
                val fallbackStreams = hubcloudExtractor(current, context)
                if (fallbackStreams.isNotEmpty()) {
                    fallbackStreams
                } else {
                    throw OlaMoviesException("Unsupported hoster link: $current")
                }
            }
        }
    }

    /**
     * Full end-to-end extraction for Butterfly's `YouTubeExtractorHelper` / `MainViewModel`:
     * - Supports both movie/series detail paths (`olamovies::/movies/inception-2010`)
     *   and on-demand quality/episode option links (`olamovies_resolve::<shortenerUrl>::<label>`).
     * - Populates `availableStreamOptions` with all resolved server mirrors AND all other
     *   qualities/episodes from Stage 1 so the user can see and switch between every quality in the player!
     */
    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData = withContext(Dispatchers.IO) {
        val raw = urlOrId.trim()

        // Case A: User selected a specific quality/episode shortener link from the quality picker or episode sheet
        if (raw.startsWith("olamovies_resolve::")) {
            val parts = raw.removePrefix("olamovies_resolve::").split("::", limit = 3)
            val shortenerUrl = parts.getOrNull(0).orEmpty()
            val qualityLabel = parts.getOrNull(1).orEmpty().ifBlank { "1080p" }
            val parentPath = parts.getOrNull(2).orEmpty()
            val parentMeta = if (parentPath.isNotBlank()) metaCache[parentPath] else null

            val cleanTitle = (parentMeta?.title ?: qualityLabel)
                .replace(Regex("""\s*\(\d{4}\).*"""), "")
                .replace(Regex("""\s*Download.*$""", RegexOption.IGNORE_CASE), "")
                .trim()
            val isSeries = parentMeta?.isSeries ?: (qualityLabel.contains("episode", true) || qualityLabel.contains("ep", true))
            val epNum = Regex("""(?i)\b(?:ep|episode)\s*(\d+)\b""").find(qualityLabel)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(?i)s\d+e(\d+)""").find(qualityLabel)?.groupValues?.get(1)?.toIntOrNull()
                ?: 1
            val seasonNum = Regex("""(?i)\b(?:s|season)\s*(\d+)\b""").find(qualityLabel)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(?i)s(\d+)e\d+""").find(qualityLabel)?.groupValues?.get(1)?.toIntOrNull()
                ?: 1

            val options = coroutineScope {
                val shortenerDeferred = async {
                    try {
                        withTimeoutOrNull(7500L) {
                            resolveStreamForLink(shortenerUrl, context)
                        }.orEmpty()
                    } catch (e: Exception) {
                        Log.w(TAG, "resolveStreamForLink failed for $shortenerUrl: ${e.message}")
                        emptyList()
                    }
                }

                val fastCinemaDeferred = async {
                    val cinemaList = java.util.Collections.synchronizedList(mutableListOf<PlayableStreamOption>())
                    try {
                        var tmdbId = ""
                        if (parentMeta?.imdbId?.isNotBlank() == true && parentMeta.imdbId.startsWith("tt")) {
                            tmdbId = com.example.util.TMDBHelper.resolveTmdbAndImdb(parentMeta.imdbId, if (isSeries) "tv" else "movie").first ?: ""
                        }
                        if (tmdbId.isBlank() && cleanTitle.isNotBlank()) {
                            tmdbId = com.example.util.TMDBHelper.resolveTmdbAndImdb(cleanTitle, if (isSeries) "tv" else "movie").first ?: ""
                        }
                        coroutineScope {
                            val jobs = mutableListOf<Job>()
                            if (tmdbId.isNotBlank() && context != null) {
                                val tmdbReq = com.example.extractor.tmdbembed.TMDBMediaRequest(
                                    tmdbId = tmdbId,
                                    mediaType = if (isSeries) "tv" else "movie",
                                    title = cleanTitle,
                                    season = seasonNum,
                                    episode = epNum
                                )
                                jobs += launch {
                                    try {
                                        val cinemaStreams = withTimeoutOrNull(8000L) {
                                            com.example.extractor.tmdbembed.TMDBEmbedExtractorEngine.resolveStreamOptions(context, tmdbReq)
                                        }.orEmpty()
                                        cinemaStreams.forEach { st ->
                                            cinemaList.add(
                                                st.copy(
                                                    qualityLabel = "$qualityLabel • ${st.qualityLabel.substringBefore(" •")}",
                                                    sourceName = "OlaMovies Direct",
                                                    releaseTitle = parentMeta?.title ?: cleanTitle
                                                )
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                                jobs += launch {
                                    try {
                                        val wasmStreams = withTimeoutOrNull(7500L) {
                                            com.example.extractor.vidsrc.VidSrcStreamExtractor.resolveViaWasmDecryption(
                                                context = context,
                                                tmdbId = tmdbId,
                                                isTv = isSeries,
                                                season = seasonNum,
                                                episode = epNum,
                                                title = cleanTitle,
                                                providerName = "OlaMovies"
                                            )
                                        }.orEmpty()
                                        cinemaList.addAll(wasmStreams)
                                    } catch (_: Exception) {}
                                }
                                jobs += launch {
                                    try {
                                        val nuvioStreams = withTimeoutOrNull(8500L) {
                                            com.example.extractor.nuvio.NuvioProviderEngine.resolveNuvioStreams(context, tmdbReq)
                                        }.orEmpty()
                                        nuvioStreams.forEach { st ->
                                            cinemaList.add(
                                                st.copy(
                                                    qualityLabel = "$qualityLabel • ${st.qualityLabel}",
                                                    sourceName = "OlaMovies (${st.sourceName.ifBlank { "Nuvio" }})",
                                                    releaseTitle = parentMeta?.title ?: cleanTitle
                                                )
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            if (cleanTitle.isNotBlank()) {
                                jobs += launch {
                                    try {
                                        val unified = withTimeoutOrNull(9000L) {
                                            com.example.vega.VegaProviderClient.resolveUnified(
                                                title = cleanTitle,
                                                tmdbId = tmdbId,
                                                isTv = isSeries,
                                                season = seasonNum,
                                                episode = epNum
                                            )
                                        }.orEmpty()
                                        unified.forEach { st ->
                                            cinemaList.add(
                                                PlayableStreamOption(
                                                    qualityLabel = "$qualityLabel • ${st.quality.ifBlank { "HD" }} • ${st.server}",
                                                    format = st.format.lowercase(),
                                                    isMuxed = true,
                                                    videoUrl = st.url,
                                                    audioUrl = null,
                                                    providerType = ProviderType.DIRECT,
                                                    headers = st.headers,
                                                    sourceName = "OlaMovies (${st.server})",
                                                    releaseTitle = parentMeta?.title ?: cleanTitle
                                                )
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "On-demand cinema stream resolution note: ${e.message}")
                    }
                    cinemaList.toList()
                }

                val resolvedStreams = shortenerDeferred.await()
                val cinemaStreams = fastCinemaDeferred.await()
                val merged = mutableListOf<PlayableStreamOption>()
                for (s in resolvedStreams) {
                    val isHls = s.link.contains(".m3u8", ignoreCase = true)
                    merged.add(
                        PlayableStreamOption(
                            qualityLabel = "$qualityLabel • ${s.server}",
                            format = if (isHls) "hls" else s.type.ifBlank { "mkv" },
                            isMuxed = true,
                            videoUrl = s.link,
                            audioUrl = null,
                            providerType = ProviderType.DIRECT,
                            headers = s.headers,
                            sourceName = "OlaMovies (${s.server})"
                        )
                    )
                }
                merged.addAll(cinemaStreams)
                merged.distinctBy { it.videoUrl }.toMutableList()
            }

            if (options.isEmpty()) {
                throw OlaMoviesException("No playable streams available for $qualityLabel")
            }

            val primary = options.first()

            if (parentMeta != null) {
                for (dl in parentMeta.directLinks) {
                    if (dl.link == shortenerUrl) continue
                    val fullLabel = "${dl.quality} • ${dl.title}"
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = fullLabel,
                            format = "mkv",
                            isMuxed = true,
                            videoUrl = "olamovies_resolve::${dl.link}::${dl.quality} • ${dl.title}::${parentMeta.path}",
                            audioUrl = null,
                            providerType = ProviderType.DIRECT,
                            headers = emptyMap(),
                            sourceName = "OlaMovies",
                            releaseTitle = dl.groupTitle
                        )
                    )
                }
            }

            return@withContext StreamData(
                videoId = if (parentPath.isNotBlank()) "olamovies::$parentPath" else raw,
                videoUrl = primary.videoUrl ?: "",
                title = parentMeta?.title ?: qualityLabel,
                channelName = if (parentMeta?.isSeries == true) "OlaMovies • TV Series" else "OlaMovies • Movie",
                channelAvatarUrl = parentMeta?.posterUrl,
                description = parentMeta?.synopsis ?: "",
                viewCount = 0L,
                likeCount = 0L,
                availableStreamOptions = options,
                selectedStreamOption = primary,
                hlsUrl = if (primary.videoUrl?.contains(".m3u8", ignoreCase = true) == true) primary.videoUrl else null,
                relatedVideos = emptyList(),
                providerId = "olamovies",
                thumbnailUrl = parentMeta?.posterUrl,
                providerType = ProviderType.DIRECT,
                headers = primary.headers
            )
        }

        // Case B: Standard movie/series page -> Stage 1 (getMeta) + Parallel Stage 2 (Shortener + Fast TMDB/Nuvio/VidSrc Race)
        val meta = getMeta(raw, context)

        // Prefer 1080p or 720p first for fastest initial playback, then 2160p/4K or other links
        val orderedLinks = meta.directLinks.sortedBy { dl ->
            when {
                dl.quality.equals("1080p", ignoreCase = true) -> 0
                dl.quality.equals("720p", ignoreCase = true) -> 1
                dl.quality.equals("2160p", ignoreCase = true) -> 2
                dl.quality.equals("480p", ignoreCase = true) -> 3
                else -> 4
            }
        }

        var resolvedStreams: List<OlaResolvedStream>? = null
        var chosenLink: OlaDirectLink? = null
        var lastResolveError: String? = null
        val cinemaOptions = java.util.Collections.synchronizedList(mutableListOf<PlayableStreamOption>())

        val cleanSearchTitle = meta.title
            .replace(Regex("""\s*\(\d{4}\).*"""), "")
            .replace(Regex("""\s*Download.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val year = Regex("""\b(19\d{2}|20\d{2})\b""").find(meta.title)?.value?.toIntOrNull()

        coroutineScope {
            // Branch 1: Try OlaMovies shortener/hoster links with a strict 7.5s cap so it never blocks playback
            val shortenerJob = launch {
                withTimeoutOrNull(7500L) {
                    for (candidate in orderedLinks.take(2)) {
                        try {
                            val streams = resolveStreamForLink(candidate.link, context)
                            if (streams.isNotEmpty()) {
                                resolvedStreams = streams
                                chosenLink = candidate
                                break
                            }
                        } catch (e: Exception) {
                            lastResolveError = e.message
                            Log.w(TAG, "Candidate link note (${candidate.title} - ${candidate.link}): ${e.message}")
                            if (e.message?.contains("shortener", ignoreCase = true) == true ||
                                e.message?.contains("Cloudflare", ignoreCase = true) == true
                            ) break
                        }
                    }
                }
            }

            // Branch 2: Concurrently resolve fast direct HLS/MP4 streams via TMDB + VidSrc + Nuvio + Butterfly Unified
            val cinemaJob = launch {
                try {
                    var tmdbId = ""
                    if (meta.imdbId.isNotBlank() && meta.imdbId.startsWith("tt")) {
                        val found = com.example.util.TMDBHelper.resolveTmdbAndImdb(meta.imdbId, if (meta.isSeries) "tv" else "movie")
                        tmdbId = found.first ?: ""
                    }
                    if (tmdbId.isBlank() && cleanSearchTitle.isNotBlank()) {
                        val found = com.example.util.TMDBHelper.resolveTmdbAndImdb(cleanSearchTitle, if (meta.isSeries) "tv" else "movie")
                        tmdbId = found.first ?: ""
                    }

                    coroutineScope {
                        if (tmdbId.isNotBlank() && context != null) {
                            val tmdbReq = com.example.extractor.tmdbembed.TMDBMediaRequest(
                                tmdbId = tmdbId,
                                mediaType = if (meta.isSeries) "tv" else "movie",
                                title = cleanSearchTitle,
                                year = year?.toString() ?: "",
                                season = 1,
                                episode = 1
                            )
                            launch {
                                try {
                                    val tmdbStreams = withTimeoutOrNull(8000L) {
                                        com.example.extractor.tmdbembed.TMDBEmbedExtractorEngine.resolveStreamOptions(context, tmdbReq)
                                    }.orEmpty()
                                    tmdbStreams.forEach { st ->
                                        cinemaOptions.add(
                                            st.copy(
                                                qualityLabel = "OlaMovies • ${st.qualityLabel.substringBefore(" •")}",
                                                sourceName = "OlaMovies Direct",
                                                releaseTitle = meta.title
                                            )
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                            launch {
                                try {
                                    val wasmStreams = withTimeoutOrNull(7500L) {
                                        com.example.extractor.vidsrc.VidSrcStreamExtractor.resolveViaWasmDecryption(
                                            context = context,
                                            tmdbId = tmdbId,
                                            isTv = meta.isSeries,
                                            season = 1,
                                            episode = 1,
                                            title = meta.title,
                                            providerName = "OlaMovies"
                                        )
                                    }.orEmpty()
                                    cinemaOptions.addAll(wasmStreams)
                                } catch (_: Exception) {}
                            }
                            launch {
                                try {
                                    val nuvioStreams = withTimeoutOrNull(8500L) {
                                        com.example.extractor.nuvio.NuvioProviderEngine.resolveNuvioStreams(context, tmdbReq)
                                    }.orEmpty()
                                    nuvioStreams.forEach { st ->
                                        cinemaOptions.add(
                                            st.copy(
                                                qualityLabel = "OlaMovies • ${st.qualityLabel}",
                                                sourceName = "OlaMovies (${st.sourceName.ifBlank { "Nuvio" }})",
                                                releaseTitle = meta.title
                                            )
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        if (cleanSearchTitle.isNotBlank()) {
                            launch {
                                try {
                                    val serverStreams = withTimeoutOrNull(9000L) {
                                        com.example.vega.VegaProviderClient.resolveUnified(
                                            title = cleanSearchTitle,
                                            tmdbId = tmdbId,
                                            isTv = meta.isSeries
                                        )
                                    }.orEmpty()
                                    serverStreams.forEach { st ->
                                        cinemaOptions.add(
                                            PlayableStreamOption(
                                                qualityLabel = "${st.quality.ifBlank { "HD" }} • ${st.server}",
                                                format = st.format.lowercase(),
                                                isMuxed = true,
                                                videoUrl = st.url,
                                                audioUrl = null,
                                                providerType = ProviderType.DIRECT,
                                                headers = st.headers,
                                                sourceName = "OlaMovies (${st.server})",
                                                releaseTitle = meta.title
                                            )
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cinema fallback stream resolution note: ${e.message}")
                }
            }

            cinemaJob.join()
            // If fast cinema streams are already ready, don't let a hung shortener delay playback beyond 1.5s more
            if (cinemaOptions.isNotEmpty() && shortenerJob.isActive) {
                withTimeoutOrNull(1500L) { shortenerJob.join() }
                if (shortenerJob.isActive) shortenerJob.cancel()
            } else {
                shortenerJob.join()
            }
        }

        val streamOptions = mutableListOf<PlayableStreamOption>()

        // 1. Add immediately resolved hoster mirrors (if any)
        val finalResolvedStreams = resolvedStreams
        val finalChosenLink = chosenLink
        if (!finalResolvedStreams.isNullOrEmpty() && finalChosenLink != null) {
            for (s in finalResolvedStreams) {
                val isHls = s.link.contains(".m3u8", ignoreCase = true)
                streamOptions.add(
                    PlayableStreamOption(
                        qualityLabel = "${finalChosenLink.quality} • ${s.server} (${finalChosenLink.title})",
                        format = if (isHls) "hls" else s.type.ifBlank { "mkv" },
                        isMuxed = true,
                        videoUrl = s.link,
                        audioUrl = null,
                        providerType = ProviderType.DIRECT,
                        headers = s.headers,
                        sourceName = "OlaMovies (${s.server})",
                        releaseTitle = finalChosenLink.groupTitle
                    )
                )
            }
        }

        // 2. Add cinema direct streams (TMDB Direct, VidSrc WASM, Nuvio, Unified Server)
        streamOptions.addAll(cinemaOptions.distinctBy { it.videoUrl })

        // 3. Add all other Stage 1 quality / episode links for on-demand switching in the player
        for (dl in meta.directLinks) {
            if (finalChosenLink != null && dl.link == finalChosenLink.link) continue
            val label = "${dl.quality} • ${dl.title} (${dl.groupTitle.take(28)})"
            streamOptions.add(
                PlayableStreamOption(
                    qualityLabel = label,
                    format = "mkv",
                    isMuxed = true,
                    videoUrl = "olamovies_resolve::${dl.link}::${dl.quality} • ${dl.title}::${meta.path}",
                    audioUrl = null,
                    providerType = ProviderType.DIRECT,
                    headers = emptyMap(),
                    sourceName = "OlaMovies",
                    releaseTitle = dl.groupTitle
                )
            )
        }

        if (streamOptions.isEmpty()) {
            val qualitiesFound = meta.directLinks.take(6).joinToString(", ") { "${it.quality} (${it.title})" }
            throw OlaMoviesException(
                "Stage 1 found ${meta.directLinks.size} links [$qualitiesFound], but playback stream resolution timed out."
            )
        }

        // Never give the player an unresolved "olamovies_resolve::" placeholder (= stuck at 0:00).
        val primary = streamOptions.firstOrNull { it.videoUrl?.startsWith("olamovies_resolve::") == false }
            ?: throw OlaMoviesException(
                "Found ${meta.directLinks.size} releases but none resolved to a playable link: " +
                    (lastResolveError ?: "hosters returned nothing")
            )
        val fullDescription = buildString {
            if (meta.rating.isNotBlank()) append("IMDb ★ ${meta.rating}\n")
            if (meta.cast.isNotEmpty()) append("Cast: ${meta.cast.joinToString(", ")}\n\n")
            if (meta.synopsis.isNotBlank()) append(meta.synopsis).append("\n\n")
            append("Available Releases (${meta.directLinks.size}):\n")
            meta.directLinks.forEach { dl ->
                append("• [${dl.quality}] ${dl.title} — ${dl.groupTitle}\n")
            }
        }

        StreamData(
            videoId = "olamovies::${meta.path}",
            videoUrl = primary.videoUrl ?: "",
            title = meta.title,
            channelName = if (meta.isSeries) "OlaMovies • TV Series" else "OlaMovies • Movie",
            channelAvatarUrl = meta.posterUrl,
            rating = meta.rating.toDoubleOrNull(),
            description = fullDescription.trim(),
            viewCount = 0L,
            likeCount = 0L,
            availableStreamOptions = streamOptions,
            selectedStreamOption = primary,
            hlsUrl = if (primary.videoUrl?.contains(".m3u8", ignoreCase = true) == true) primary.videoUrl else null,
            relatedVideos = emptyList(),
            providerId = "olamovies",
            thumbnailUrl = meta.posterUrl,
            providerType = ProviderType.DIRECT,
            headers = primary.headers,
            cast = meta.cast.map { CastMember(name = it) }
        )
    }
}
