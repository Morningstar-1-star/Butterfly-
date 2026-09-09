package com.example.extractor.sextb

import android.content.Context
import android.util.Log
import com.example.model.CaptionOption
import com.example.util.JsUnpacker
import com.example.util.SecureDnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Resolves SEXТB page and iframe embed endpoints using the real upstream pipeline:
 *
 * Details Page
 *   → `.episode-list .btn-player` (data-id & data-source)
 *   → POST `/ajax/player`
 *   → Extract returned iframe URL
 *   → Stbturbo extractor (`#video_player[data-hash]`) or StreamTape extractor
 *   → HLS (.m3u8) / MP4 streams for Butterfly ExoPlayer.
 *
 * WebView fallback is strictly retained for when native HTTP/JS resolution fails (e.g. anti-bot challenge).
 */
object SextbResolver {

    private const val TAG = "SextbResolver"
    const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    const val MAIN_URL = "https://sextb.net"
    const val AJAX_PLAYER_URL = "https://sextb.net/ajax/player"

    private val httpClient get() = SextbNetwork.httpClient

    private val M3U8_URL_REGEX = Pattern.compile("""https?://[^"'\s<>]+\.m3u8(?:[^"'\s<>]*)?""", Pattern.CASE_INSENSITIVE)
    private val MP4_URL_REGEX = Pattern.compile("""https?://[^"'\s<>]+\.mp4(?:[^"'\s<>]*)?""", Pattern.CASE_INSENSITIVE)
    private val SOURCES_REGEX = Pattern.compile("""sources\s*:\s*(\[[^\]]+\])""", Pattern.CASE_INSENSITIVE)

    /**
     * Resolves all playable media sources for a SEXТB video page or direct embed URL.
     */
    suspend fun resolveVideoSources(
        pageOrEmbedUrl: String,
        pageHtml: String? = null,
        customHeaders: Map<String, String> = emptyMap(),
        context: Context? = null
    ): List<VideoSource> = withContext(Dispatchers.IO) {
        val resolvedSources = mutableListOf<VideoSource>()
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "SEXТB resolver: Resolving sources for target $pageOrEmbedUrl")

        // 1. Direct embed check: if the URL itself is already an iframe player
        if (isEmbedPlayerUrl(pageOrEmbedUrl)) {
            val directEmbedSources = resolveIframeEmbed(pageOrEmbedUrl, "https://sextb.net/")
            if (directEmbedSources.isNotEmpty()) {
                return@withContext normalizeAndDeduplicateSources(directEmbedSources)
            }
        }

        // 2. Fetch page HTML if not provided
        var html = pageHtml
        if (html == null) {
            html = fetchHtmlSafely(pageOrEmbedUrl, customHeaders)
        }

        // Check if native fetch failed or returned Cloudflare challenge / block / 404
        if (html == null || html.contains("<title>404") || html.contains("404 Page Not Found") || html.contains("Page Not Found | SEXTB") ||
            html.contains("Access Restricted", ignoreCase = true) || html.contains("Attention Required! | Cloudflare") || html.contains("Checking your browser")
        ) {
            Log.w(TAG, "SEXТB resolver: Native fetch blocked or failed for $pageOrEmbedUrl, falling back to WebView resolution")
            if (context != null) {
                val fallbackSource = SextbWebViewFallback.resolveWithFallback(context, pageOrEmbedUrl)
                if (fallbackSource != null) {
                    return@withContext listOf(fallbackSource)
                }
            }
            return@withContext emptyList()
        }

        val doc = Jsoup.parse(html, pageOrEmbedUrl)

        // 3. Upstream SEXТB Pipeline:
        // Details page -> .episode-list .btn-player -> data-id + data-source -> POST /ajax/player -> iframe
        val btnPlayers = doc.select(".episode-list .btn-player, .btn-player")
        val globalFilmId = doc.selectFirst(".episode-list .btn-player, .btn-player")?.attr("data-source")?.ifBlank { "" } ?: ""

        if (btnPlayers.isNotEmpty()) {
            Log.d(TAG, "SEXТB resolver: Found ${btnPlayers.size} player buttons in .episode-list")
            // Resolve buttons (deduplicated by data-id)
            val seenEpisodes = mutableSetOf<String>()
            for (btn in btnPlayers) {
                val episode = btn.attr("data-id").trim()
                val filmId = btn.attr("data-source").ifBlank { globalFilmId }.trim()
                if (episode.isBlank() || seenEpisodes.contains(episode)) continue
                seenEpisodes.add(episode)

                val iframes = resolveAjaxPlayerIframes(episode, filmId, pageOrEmbedUrl)
                for (iframeUrl in iframes) {
                    val iframeStreams = resolveIframeEmbed(iframeUrl, pageOrEmbedUrl)
                    resolvedSources.addAll(iframeStreams)
                }

                if (resolvedSources.isNotEmpty()) {
                    // Successfully extracted streams from first valid player button
                    break
                }
            }
        }

        // 4. Fallback: direct iframes in page HTML
        if (resolvedSources.isEmpty()) {
            val embedUrls = SextbParser.extractPlayerEmbedUrls(html, pageOrEmbedUrl)
            for (embedUrl in embedUrls) {
                try {
                    val streams = resolveIframeEmbed(embedUrl, pageOrEmbedUrl)
                    resolvedSources.addAll(streams)
                } catch (e: Exception) {
                    Log.w(TAG, "SEXТB resolver: Embed error for $embedUrl: ${e.message}")
                }
            }
        }

        // 5. Fallback: direct HTML5 video / script sources
        if (resolvedSources.isEmpty()) {
            val directSources = SextbParser.parseDirectVideoSources(doc, pageOrEmbedUrl)
            resolvedSources.addAll(directSources)

            val unpackedHtml = unpackAllScripts(html)
            val scriptSources = extractStreamUrlsFromText(unpackedHtml, pageOrEmbedUrl)
            resolvedSources.addAll(scriptSources)
        }

        // 6. Final fallback: Headless WebView if native resolution produced no sources
        if (resolvedSources.isEmpty() && context != null) {
            Log.i(TAG, "SEXТB resolver: Native resolution empty, triggering WebView fallback")
            val fallbackSource = SextbWebViewFallback.resolveWithFallback(context, pageOrEmbedUrl)
            if (fallbackSource != null) {
                resolvedSources.add(fallbackSource)
            }
        }

        val normalized = normalizeAndDeduplicateSources(resolvedSources)
        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "SEXТB resolver: Completed in ${elapsed}ms. Found ${normalized.size} distinct stream variants.")
        normalized
    }

    /**
     * Executes the upstream POST /ajax/player call.
     * Form parameters: episode = data-id, filmId = data-source
     * Returns list of extracted iframe URLs.
     */
    suspend fun resolveAjaxPlayerIframes(
        episode: String,
        filmId: String,
        refererUrl: String
    ): List<String> = withContext(Dispatchers.IO) {
        val iframes = mutableListOf<String>()
        try {
            val formBody = FormBody.Builder()
                .add("episode", episode)
                .add("filmId", filmId)
                .add("film_id", filmId)
                .build()

            val req = Request.Builder()
                .url(AJAX_PLAYER_URL)
                .post(formBody)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", refererUrl)
                .header("Origin", MAIN_URL)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val responseBody = resp.body?.string() ?: ""
                val extracted = extractIframeUrlsFromAjaxResponse(responseBody)
                iframes.addAll(extracted)
                Log.d(TAG, "POST /ajax/player extracted ${iframes.size} iframes: $iframes")
            } else {
                Log.w(TAG, "POST /ajax/player failed with HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed POST to /ajax/player (episode=$episode, filmId=$filmId): ${e.message}")
        }
        iframes
    }

    /**
     * Extracts iframe URLs from the AJAX response body (HTML snippet or JSON).
     */
    fun extractIframeUrlsFromAjaxResponse(responseBody: String): List<String> {
        val list = mutableListOf<String>()

        // 1. DOM parse with Jsoup
        val doc = Jsoup.parse(responseBody)
        val iframeElements = doc.select("iframe")
        for (iframe in iframeElements) {
            val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
            if (src.isNotBlank()) {
                val cleaned = cleanIframeUrl(src)
                if (cleaned.isNotBlank() && !list.contains(cleaned)) {
                    list.add(cleaned)
                }
            }
        }

        // 2. Regex fallback for escaped HTML/JSON responses
        val iframeRegex = Regex("""(?:<iframe[^>]+src=|"iframe"\s*:\s*|src=)\\?["']([^"'\\]+)\\?["']""", RegexOption.IGNORE_CASE)
        for (match in iframeRegex.findAll(responseBody)) {
            val src = match.groupValues[1]
            val cleaned = cleanIframeUrl(src)
            if (cleaned.isNotBlank() && !list.contains(cleaned)) {
                list.add(cleaned)
            }
        }

        // 3. Direct embed player URLs anywhere in response
        val embedRegex = Regex("""https?://[^"'\s<>]*(?:stbturbo|streamtape|streamtb)[^"'\s<>]*""", RegexOption.IGNORE_CASE)
        for (match in embedRegex.findAll(responseBody)) {
            val src = match.groupValues[0].replace("\\/", "/").trim()
            val cleaned = cleanIframeUrl(src)
            if (cleaned.isNotBlank() && !list.contains(cleaned)) {
                list.add(cleaned)
            }
        }

        return list
    }

    /**
     * Resolves an iframe embed URL using the appropriate extractor (Stbturbo, StreamTape, or fallback).
     */
    suspend fun resolveIframeEmbed(iframeUrl: String, parentReferer: String = "https://sextb.net/"): List<VideoSource> = withContext(Dispatchers.IO) {
        val lower = iframeUrl.lowercase()
        when {
            StbturboExtractor.canHandle(iframeUrl) -> {
                StbturboExtractor.extractStream(iframeUrl, parentReferer)
            }
            StreamTapeExtractor.canHandle(iframeUrl) -> {
                StreamTapeExtractor.extractStream(iframeUrl, parentReferer)
            }
            else -> {
                // First try Stbturbo extraction pattern (#video_player[data-hash])
                val stbStreams = StbturboExtractor.extractStream(iframeUrl, parentReferer)
                if (stbStreams.isNotEmpty()) {
                    stbStreams
                } else {
                    resolveGenericEmbed(iframeUrl, parentReferer)
                }
            }
        }
    }

    private suspend fun resolveGenericEmbed(embedUrl: String, parentReferer: String): List<VideoSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<VideoSource>()
        val embedHost = extractHost(embedUrl)

        val headers = mapOf(
            "User-Agent" to DEFAULT_UA,
            "Referer" to parentReferer,
            "Origin" to "https://$embedHost",
            "Accept" to "*/*"
        )

        val embedHtml = fetchHtmlSafely(embedUrl, headers) ?: return@withContext emptyList()
        val unpackedEmbed = unpackAllScripts(embedHtml)

        // Extract direct stream URLs from embed HTML/JS
        val streamUrls = extractStreamUrlsFromText(unpackedEmbed, embedUrl)
        sources.addAll(streamUrls)

        val playbackHeaders = mapOf(
            "User-Agent" to DEFAULT_UA,
            "Referer" to embedUrl,
            "Origin" to "https://$embedHost"
        )

        sources.map { it.copy(headers = playbackHeaders) }
    }

    /**
     * Extracts HLS (.m3u8) and MP4 URLs from text or unpacked scripts.
     */
    fun extractStreamUrlsFromText(text: String, referer: String): List<VideoSource> {
        val sources = mutableListOf<VideoSource>()
        val host = extractHost(referer)
        val playbackHeaders = mapOf(
            "User-Agent" to DEFAULT_UA,
            "Referer" to referer,
            "Origin" to "https://$host"
        )

        // 1. JSON sources block: sources: [{ file: "...", label: "1080p", type: "hls" }]
        val sourcesMatcher = SOURCES_REGEX.matcher(text)
        if (sourcesMatcher.find()) {
            val jsonText = sourcesMatcher.group(1)
            if (jsonText != null) {
                try {
                    val arr = JSONArray(jsonText)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val file = obj.optString("file").ifBlank { obj.optString("src") }
                        val label = obj.optString("label", "1080p")
                        val type = obj.optString("type")
                        if (file.isNotBlank() && file.startsWith("http")) {
                            val isHls = file.contains(".m3u8") || type.equals("hls", ignoreCase = true)
                            sources.add(
                                VideoSource(
                                    url = file,
                                    mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                                    quality = SextbParser.normalizeQualityLabel(label),
                                    isHls = isHls,
                                    headers = playbackHeaders,
                                    sourceName = "SEXТB Player"
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Direct regex search for .m3u8
        val m3u8Matcher = M3U8_URL_REGEX.matcher(text)
        while (m3u8Matcher.find()) {
            val url = m3u8Matcher.group(0)
            if (!url.isNullOrBlank() && !sources.any { it.url == url }) {
                sources.add(
                    VideoSource(
                        url = url,
                        mimeType = "application/x-mpegURL",
                        quality = "1080p",
                        isHls = true,
                        headers = playbackHeaders,
                        sourceName = "SEXТB HLS Master"
                    )
                )
            }
        }

        // 3. Direct regex search for .mp4
        val mp4Matcher = MP4_URL_REGEX.matcher(text)
        while (mp4Matcher.find()) {
            val url = mp4Matcher.group(0)
            if (!url.isNullOrBlank() && !sources.any { it.url == url }) {
                sources.add(
                    VideoSource(
                        url = url,
                        mimeType = "video/mp4",
                        quality = "1080p",
                        isHls = false,
                        headers = playbackHeaders,
                        sourceName = "SEXТB Direct MP4"
                    )
                )
            }
        }

        return sources
    }

    fun cleanIframeUrl(raw: String): String {
        val cleaned = raw.replace("\\\"", "").replace("\\/", "/").substringBefore("?").trim()
        return StbturboExtractor.httpsify(cleaned)
    }

    fun isEmbedPlayerUrl(url: String): Boolean {
        val lower = url.lowercase()
        return StbturboExtractor.canHandle(lower) ||
                StreamTapeExtractor.canHandle(lower) ||
                lower.contains("/embed/") ||
                lower.contains("/e/") ||
                lower.contains("streamtb") ||
                lower.contains("stbturbo")
    }

    private fun normalizeAndDeduplicateSources(sources: List<VideoSource>): List<VideoSource> {
        if (sources.isEmpty()) return emptyList()

        val qualityOrder = listOf("4K", "1080p", "720p", "480p", "360p", "Auto")
        return sources.distinctBy { it.url }
            .sortedWith(
                compareByDescending<VideoSource> { it.isHls }
                    .thenBy { qualityOrder.indexOf(it.quality).let { idx -> if (idx >= 0) idx else 99 } }
            )
    }

    private fun unpackAllScripts(html: String): String {
        val sb = StringBuilder(html)
        val scriptPattern = Pattern.compile("""<script[^>]*>(.*?)</script>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = scriptPattern.matcher(html)

        while (matcher.find()) {
            val code = matcher.group(1) ?: continue
            if (JsUnpacker.isPacked(code)) {
                val unpacked = JsUnpacker.unpack(code)
                sb.append("\n").append(unpacked)
            }
        }
        return sb.toString()
    }

    fun fetchHtmlSafely(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            if (!headers.containsKey("User-Agent")) reqBuilder.header("User-Agent", DEFAULT_UA)

            val resp = httpClient.newCall(reqBuilder.build()).execute()
            if (resp.isSuccessful) {
                resp.body?.string()
            } else {
                Log.w(TAG, "HTTP fetch failed for $url: ${resp.code}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network exception fetching $url: ${e.message}")
            null
        }
    }

    private fun extractHost(url: String): String {
        return try {
            URI(url).host ?: "stbturbo.xyz"
        } catch (_: Exception) {
            "stbturbo.xyz"
        }
    }
}
