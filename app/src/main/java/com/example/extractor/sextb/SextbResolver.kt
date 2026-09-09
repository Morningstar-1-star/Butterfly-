package com.example.extractor.sextb

import android.util.Log
import com.example.model.CaptionOption
import com.example.util.JsUnpacker
import com.example.util.SecureDnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Resolves SEXТB page and iframe embed endpoints (StreamTB / player AJAX / packed JS)
 * into standardized playable HLS (.m3u8) or MP4 VideoSource items.
 *
 * Implements CloudStream 18+ and JAVM resolution patterns adapted to Butterfly.
 */
object SextbResolver {

    private const val TAG = "SextbResolver"
    const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val M3U8_URL_REGEX = Pattern.compile("""https?://[^"'\s<>]+\.m3u8(?:[^"'\s<>]*)?""", Pattern.CASE_INSENSITIVE)
    private val MP4_URL_REGEX = Pattern.compile("""https?://[^"'\s<>]+\.mp4(?:[^"'\s<>]*)?""", Pattern.CASE_INSENSITIVE)
    private val SOURCES_REGEX = Pattern.compile("""sources\s*:\s*(\[[^\]]+\])""", Pattern.CASE_INSENSITIVE)
    private val FILE_KEY_REGEX = Pattern.compile("""["']?(?:file|src)["']?\s*:\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)

    /**
     * Resolves all playable media sources for a SEXТB video page or direct embed URL.
     */
    suspend fun resolveVideoSources(
        pageOrEmbedUrl: String,
        pageHtml: String? = null,
        customHeaders: Map<String, String> = emptyMap()
    ): List<VideoSource> = withContext(Dispatchers.IO) {
        val resolvedSources = mutableListOf<VideoSource>()
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "SEXТB player: Resolving sources for target $pageOrEmbedUrl")

        // 1. If page HTML was already fetched, inspect it for direct sources & embeds
        var html = pageHtml
        if (html == null) {
            html = fetchHtmlSafely(pageOrEmbedUrl, customHeaders)
        }

        if (html != null) {
            // Check if page returned a 404 or Not Found error
            if (html.contains("<title>404") || html.contains("404 Page Not Found") || html.contains("Page Not Found | SEXTB")) {
                Log.w(TAG, "SEXТB extractor: Page indicates 404 Not Found: $pageOrEmbedUrl")
                return@withContext emptyList()
            }

            // Check direct sources in HTML
            val doc = Jsoup.parse(html, pageOrEmbedUrl)
            val directSources = SextbParser.parseDirectVideoSources(doc, pageOrEmbedUrl)
            if (directSources.isNotEmpty()) {
                Log.i(TAG, "SEXТB extractor: Found ${directSources.size} direct sources in main page HTML")
                resolvedSources.addAll(directSources)
            }

            // Check if page contains packed JS
            val unpackedHtml = unpackAllScripts(html)
            val scriptSources = extractStreamUrlsFromText(unpackedHtml, pageOrEmbedUrl)
            resolvedSources.addAll(scriptSources)

            // Locate iframe / embed URLs
            val embedUrls = SextbParser.extractPlayerEmbedUrls(html, pageOrEmbedUrl)
            Log.d(TAG, "SEXТB player: Found ${embedUrls.size} embed/iframe endpoints")

            for (embedUrl in embedUrls) {
                try {
                    val embedSources = resolveEmbedUrl(embedUrl, pageOrEmbedUrl)
                    resolvedSources.addAll(embedSources)
                } catch (e: Exception) {
                    Log.w(TAG, "SEXТB player: Failed to resolve embed endpoint $embedUrl: ${e.message}")
                }
            }
        }

        // 2. If target is itself an embed player (e.g. streamtb.me/e/...), resolve it directly
        if (isEmbedPlayerUrl(pageOrEmbedUrl)) {
            val directEmbedSources = resolveEmbedUrl(pageOrEmbedUrl, "https://sextb.net/")
            resolvedSources.addAll(directEmbedSources)
        }

        // Normalize and deduplicate sources
        val normalized = normalizeAndDeduplicateSources(resolvedSources)
        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "SEXТB resolver: Completed in ${elapsed}ms. Found ${normalized.size} distinct stream variants.")

        normalized
    }

    /**
     * Resolves a StreamTB or player embed URL by fetching its HTML and API endpoints.
     */
    suspend fun resolveEmbedUrl(embedUrl: String, parentReferer: String): List<VideoSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<VideoSource>()
        val embedHost = extractHost(embedUrl)

        val headers = mapOf(
            "User-Agent" to DEFAULT_UA,
            "Referer" to parentReferer,
            "Origin" to extractOrigin(parentReferer),
            "Accept" to "*/*"
        )

        val embedHtml = fetchHtmlSafely(embedUrl, headers) ?: return@withContext emptyList()
        val unpackedEmbed = unpackAllScripts(embedHtml)

        // 1. Extract direct stream URLs from embed HTML/JS
        val streamUrls = extractStreamUrlsFromText(unpackedEmbed, embedUrl)
        sources.addAll(streamUrls)

        // 2. Check for StreamTB API endpoint: /api/source/{id} or /ajax/get_link/
        val embedId = extractEmbedId(embedUrl)
        if (embedId.isNotBlank() && (embedUrl.contains("streamtb") || embedUrl.contains("sextb"))) {
            val apiEndpoints = listOf(
                "https://$embedHost/api/source/$embedId",
                "https://$embedHost/ajax/get_link/$embedId",
                "https://$embedHost/api/player/$embedId"
            )

            val apiHeaders = headers + mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to embedUrl,
                "Origin" to "https://$embedHost"
            )

            for (endpoint in apiEndpoints) {
                try {
                    val jsonResp = fetchJsonSafely(endpoint, apiHeaders)
                    if (jsonResp != null) {
                        val parsed = parseApiSources(jsonResp, embedUrl, "https://$embedHost")
                        if (parsed.isNotEmpty()) {
                            Log.d(TAG, "SEXТB extractor: Successfully extracted ${parsed.size} streams from API endpoint")
                            sources.addAll(parsed)
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.v(TAG, "API endpoint check $endpoint non-critical: ${e.message}")
                }
            }
        }

        // Scope headers specifically for playback
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
                                    headers = playbackHeaders,
                                    sourceName = "SEXТB StreamTB"
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
                        headers = playbackHeaders,
                        sourceName = "SEXТB Direct MP4"
                    )
                )
            }
        }

        return sources
    }

    private fun parseApiSources(json: JSONObject, referer: String, origin: String): List<VideoSource> {
        val list = mutableListOf<VideoSource>()
        val playbackHeaders = mapOf(
            "User-Agent" to DEFAULT_UA,
            "Referer" to referer,
            "Origin" to origin
        )

        val sourcesArray = json.optJSONArray("sources")
            ?: json.optJSONArray("data")
            ?: json.optJSONObject("data")?.optJSONArray("sources")

        if (sourcesArray != null) {
            for (i in 0 until sourcesArray.length()) {
                val item = sourcesArray.optJSONObject(i) ?: continue
                val file = item.optString("file").ifBlank { item.optString("url") }
                val label = item.optString("label", "1080p")
                val type = item.optString("type")
                if (file.isNotBlank()) {
                    val isHls = file.contains(".m3u8") || type.contains("hls", ignoreCase = true)
                    list.add(
                        VideoSource(
                            url = file,
                            mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                            quality = SextbParser.normalizeQualityLabel(label),
                            headers = playbackHeaders,
                            sourceName = "SEXТB StreamTB ($label)"
                        )
                    )
                }
            }
        }

        // Check for direct file parameter in top-level JSON
        val singleFile = json.optString("file").ifBlank { json.optString("url") }
        if (singleFile.isNotBlank() && singleFile.startsWith("http")) {
            val isHls = singleFile.contains(".m3u8")
            list.add(
                VideoSource(
                    url = singleFile,
                    mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                    quality = "1080p",
                    headers = playbackHeaders,
                    sourceName = "SEXТB StreamTB"
                )
            )
        }

        return list
    }

    private fun normalizeAndDeduplicateSources(sources: List<VideoSource>): List<VideoSource> {
        if (sources.isEmpty()) return emptyList()

        // Prioritize HLS master playlists first, then 1080p, 720p, 480p
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

    private fun fetchHtmlSafely(url: String, headers: Map<String, String>): String? {
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

    private fun fetchJsonSafely(url: String, headers: Map<String, String>): JSONObject? {
        val body = fetchHtmlSafely(url, headers) ?: return null
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun isEmbedPlayerUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("streamtb") || lower.contains("/embed/") || lower.contains("/e/") || lower.contains("player")
    }

    private fun extractHost(url: String): String {
        return try {
            java.net.URI(url).host ?: "streamtb.me"
        } catch (_: Exception) {
            "streamtb.me"
        }
    }

    private fun extractOrigin(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: "sextb.net"
            "$scheme://$host"
        } catch (_: Exception) {
            "https://sextb.net"
        }
    }

    private fun extractEmbedId(url: String): String {
        val clean = url.substringBefore("?").removeSuffix("/")
        return clean.substringAfterLast("/")
    }
}
