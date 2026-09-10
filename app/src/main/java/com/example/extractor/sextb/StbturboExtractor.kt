package com.example.extractor.sextb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.regex.Pattern

/**
 * Authoritative Stbturbo extractor for SEXТB.
 *
 * Upstream Reference: CloudStream 18+ SextbExtractors.kt
 *
 * Pattern:
 * 1. GET embed URL (e.g. stbturbo.xyz/e/... or stbturbo.xyz/embed/...)
 * 2. Handle JS token redirect if present (`window.location.replace(...)`)
 * 3. Scoped DOM extraction of `#video_player[data-hash]`
 * 4. Httpsify data-hash into an HLS (.m3u8) master playlist URL
 * 5. Package into VideoSource with required Referer/Origin headers.
 */
object StbturboExtractor {

    private const val TAG = "StbturboExtractor"
    const val MAIN_URL = "https://stbturbo.xyz/"

    private val httpClient get() = SextbNetwork.httpClient

    fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("stbturbo.xyz") || lower.contains("stbturbo") || lower.contains("stb") ||
                lower.contains("streamtb")
    }

    /**
     * Resolves playable HLS stream from an Stbturbo iframe/embed URL.
     */
    suspend fun extractStream(
        url: String,
        referer: String? = "https://sextb.net/",
        userAgent: String = SextbResolver.DEFAULT_UA
    ): List<VideoSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<VideoSource>()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", referer ?: "https://sextb.net/")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} fetching Stbturbo embed at $url")
                return@withContext emptyList()
            }

            var html = resp.body?.string() ?: ""

            // Handle JS challenge redirect: window.location.replace('https://stbturbo.xyz/?ch=1&js=...&sid=...')
            val redirRegex = Regex("""window\.location\.replace\(['"]([^'"]+)['"]\)""")
            val match = redirRegex.find(html)
            if (match != null) {
                var redirectUrl = match.groupValues[1]
                if (redirectUrl.startsWith("//")) redirectUrl = "https:$redirectUrl"
                else if (redirectUrl.startsWith("/")) redirectUrl = "https://${extractHost(url)}$redirectUrl"

                Log.d(TAG, "Stbturbo: Detected JS challenge redirect to $redirectUrl")
                val redirReq = Request.Builder()
                    .url(redirectUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", url)
                    .build()

                val redirResp = httpClient.newCall(redirReq).execute()
                if (redirResp.isSuccessful) {
                    val redirHtml = redirResp.body?.string() ?: ""
                    val redirSources = extractFromHtml(redirHtml, url, userAgent)
                    if (redirSources.isNotEmpty()) {
                        sources.addAll(redirSources)
                        return@withContext sources
                    }
                }

                // After following token redirect, re-request original embed URL with set cookies
                val retryReq = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Referer", referer ?: "https://sextb.net/")
                    .build()

                val retryResp = httpClient.newCall(retryReq).execute()
                if (retryResp.isSuccessful) {
                    html = retryResp.body?.string() ?: html
                }
            }

            val extracted = extractFromHtml(html, url, userAgent)
            sources.addAll(extracted)
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving Stbturbo stream for $url: ${e.message}")
        }
        sources
    }

    /**
     * Extracts HLS stream directly from Stbturbo HTML content.
     * Pure function for unit testability and robust DOM processing.
     */
    fun extractFromHtml(
        html: String,
        embedUrl: String,
        userAgent: String = SextbResolver.DEFAULT_UA
    ): List<VideoSource> {
        val sources = mutableListOf<VideoSource>()
        val doc = Jsoup.parse(html, embedUrl)

        val host = extractHost(embedUrl)
        val playbackHeaders = mapOf(
            "User-Agent" to userAgent,
            "Referer" to embedUrl,
            "Origin" to "https://$host"
        )

        // 1. Primary upstream selector: #video_player[data-hash]
        val playerEl = doc.selectFirst("#video_player") ?: doc.selectFirst("[data-hash]") ?: doc.selectFirst(".player-wrapper [data-hash]")
        val dataHash = playerEl?.attr("data-hash")?.trim() ?: playerEl?.attr("data-src")?.trim()

        if (!dataHash.isNullOrBlank()) {
            val hlsUrl = httpsify(dataHash)
            if (hlsUrl.isNotBlank()) {
                val isHls = hlsUrl.contains(".m3u8") || !hlsUrl.contains(".mp4")
                sources.add(
                    VideoSource(
                        url = hlsUrl,
                        mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                        quality = "1080p",
                        isHls = isHls,
                        headers = playbackHeaders,
                        sourceName = "Stbturbo"
                    )
                )
                Log.i(TAG, "Resolved Stbturbo stream: $hlsUrl")
                return sources
            }
        }

        // 2. Direct HTML5 video / source fallback
        val videoSrc = doc.selectFirst("video source[src], video[src]")?.let {
            it.attr("abs:src").ifBlank { it.attr("src") }
        }
        if (!videoSrc.isNullOrBlank()) {
            val streamUrl = httpsify(videoSrc)
            val isHls = streamUrl.contains(".m3u8")
            sources.add(
                VideoSource(
                    url = streamUrl,
                    mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                    quality = "1080p",
                    isHls = isHls,
                    headers = playbackHeaders,
                    sourceName = "Stbturbo Video"
                )
            )
            return sources
        }

        // 3. Fallback: Search in script tags and unpacked JavaScript
        val unpackedHtml = SextbResolver.unpackAllScripts(html)
        val scriptSources = SextbResolver.extractStreamUrlsFromText(unpackedHtml, embedUrl)
        if (scriptSources.isNotEmpty()) {
            sources.addAll(scriptSources)
            return sources
        }

        // 4. Fallback regex search for master.m3u8 or .m3u8 / .mp4 anywhere in HTML
        val m3u8Regex = Pattern.compile("""https?://[^"'\s<>]+\.(?:m3u8|mp4)(?:[^"'\s<>]*)?""", Pattern.CASE_INSENSITIVE)
        val matcher = m3u8Regex.matcher(unpackedHtml)
        while (matcher.find()) {
            val foundUrl = httpsify(matcher.group(0))
            if (foundUrl.isNotBlank() && !foundUrl.contains("thumb") && !foundUrl.contains("preview")) {
                val isHls = foundUrl.contains(".m3u8")
                sources.add(
                    VideoSource(
                        url = foundUrl,
                        mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                        quality = "1080p",
                        isHls = isHls,
                        headers = playbackHeaders,
                        sourceName = "Stbturbo Stream"
                    )
                )
                return sources
            }
        }

        return sources
    }

    fun httpsify(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.isNotBlank() -> "https://$trimmed"
            else -> ""
        }
    }

    fun extractHost(url: String): String {
        return try {
            URI(url).host ?: "stbturbo.xyz"
        } catch (_: Exception) {
            "stbturbo.xyz"
        }
    }
}
