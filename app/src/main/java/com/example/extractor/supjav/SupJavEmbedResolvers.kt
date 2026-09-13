package com.example.extractor.supjav

import android.util.Base64
import android.util.Log
import com.example.extractor.sextb.StreamTapeExtractor
import com.example.util.JsUnpacker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.regex.Pattern

/**
 * High-performance native embed resolvers for video hosts used by SupJav:
 * - TVLogy / SupPlayer
 * - StreamWish / WishEmbed / AWish / DWish
 * - VOE
 * - DoodStream / DS2Play
 * - StreamTape
 * - Fembed / Fasting / Filelions / Mixdrop
 */
object SupJavEmbedResolvers {

    private const val TAG = "SupJavEmbedResolvers"
    private val httpClient get() = SupJavNetwork.httpClient

    private val M3U8_REGEX = Pattern.compile("""https?://[^\s"'<>]+\.m3u8(?:[^\s"'<>]*)?""", Pattern.CASE_INSENSITIVE)
    private val MP4_REGEX = Pattern.compile("""https?://[^\s"'<>]+\.mp4(?:[^\s"'<>]*)?""", Pattern.CASE_INSENSITIVE)
    private val SOURCES_FILE_REGEX = Pattern.compile("""(?:file|source|src)\s*:\s*["'](https?://[^"']+)["']""", Pattern.CASE_INSENSITIVE)

    /**
     * Resolves a video embed URL or server link into playable SupJavSource instances.
     */
    suspend fun resolveEmbed(embedUrl: String, parentPageUrl: String): List<SupJavSource> = withContext(Dispatchers.IO) {
        val lower = embedUrl.lowercase()
        val sources = mutableListOf<SupJavSource>()

        try {
            when {
                // 1. TVLogy / SupPlayer
                lower.contains("tvlogy") || lower.contains("play.supjav") -> {
                    sources.addAll(resolveTvlogy(embedUrl, parentPageUrl))
                }

                // 2. StreamWish / Wishembed / Awish / Dwish / Strwish / Cdnwish
                lower.contains("streamwish") || lower.contains("wishembed") || lower.contains("awish") ||
                        lower.contains("dwish") || lower.contains("embedwish") || lower.contains("strwish") ||
                        lower.contains("cdnwish") || lower.contains("sfastwish") || lower.contains("filelions") -> {
                    sources.addAll(resolveStreamwish(embedUrl, parentPageUrl))
                }

                // 3. VOE
                lower.contains("voe.sx") || lower.contains("voe-unblock") || lower.contains("audaciousdefaulthouse") -> {
                    sources.addAll(resolveVoe(embedUrl, parentPageUrl))
                }

                // 4. DoodStream / DS2Play
                lower.contains("dood") || lower.contains("ds2play") -> {
                    sources.addAll(resolveDoodStream(embedUrl, parentPageUrl))
                }

                // 5. StreamTape
                StreamTapeExtractor.canHandle(embedUrl) -> {
                    val stSources = StreamTapeExtractor.extractStream(embedUrl, parentPageUrl, SupJavNetwork.DEFAULT_USER_AGENT)
                    for (st in stSources) {
                        sources.add(
                            SupJavSource(
                                url = st.url,
                                mimeType = st.mimeType,
                                quality = st.quality,
                                isHls = st.isHls,
                                headers = st.headers,
                                sourceName = "SupJav (StreamTape)"
                            )
                        )
                    }
                }

                // 6. Direct HLS or MP4 stream
                lower.contains(".m3u8") || lower.contains(".mp4") -> {
                    val isHls = lower.contains(".m3u8")
                    sources.add(
                        SupJavSource(
                            url = embedUrl,
                            mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                            quality = if (isHls) "1080p FHD" else "720p HD",
                            isHls = isHls,
                            headers = mapOf(
                                "User-Agent" to SupJavNetwork.DEFAULT_USER_AGENT,
                                "Referer" to parentPageUrl
                            ),
                            sourceName = if (isHls) "SupJav Direct HLS" else "SupJav Direct MP4"
                        )
                    )
                }

                // 7. Generic HTML Scraper for other video hosts (Fembed, Fasting, Filelions, Mixdrop, etc.)
                else -> {
                    sources.addAll(resolveGenericHost(embedUrl, parentPageUrl))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving embed $embedUrl: ${e.message}")
        }

        sources
    }

    // ----------------------------------------------------
    // TVLogy Extractor
    // ----------------------------------------------------
    private suspend fun resolveTvlogy(embedUrl: String, parentPageUrl: String): List<SupJavSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<SupJavSource>()
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", SupJavNetwork.DEFAULT_USER_AGENT)
                .header("Referer", parentPageUrl)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val html = resp.body?.string() ?: return@withContext emptyList()
            var processed = html
            if (JsUnpacker.isPacked(html)) {
                processed = JsUnpacker.unpack(html)
            }
            processed = processed.replace("\\/", "/")

            val host = extractHost(embedUrl)
            val headers = mapOf(
                "User-Agent" to SupJavNetwork.DEFAULT_USER_AGENT,
                "Referer" to "https://$host/",
                "Origin" to "https://$host"
            )

            // 1. Check sources:[{file:"..."}] or file:"..."
            val fileMatcher = SOURCES_FILE_REGEX.matcher(processed)
            while (fileMatcher.find()) {
                val streamUrl = fileMatcher.group(1)
                val isHls = streamUrl.contains(".m3u8")
                sources.add(
                    SupJavSource(
                        url = streamUrl,
                        mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                        quality = if (isHls) "1080p FHD • TVLogy HLS" else "720p HD • TVLogy MP4",
                        isHls = isHls,
                        headers = headers,
                        sourceName = "TVLogy (SupJav Server)"
                    )
                )
            }

            // 2. Match m3u8 regex
            if (sources.isEmpty()) {
                val m3u8Matcher = M3U8_REGEX.matcher(processed)
                while (m3u8Matcher.find()) {
                    val streamUrl = m3u8Matcher.group()
                    sources.add(
                        SupJavSource(
                            url = streamUrl,
                            mimeType = "application/x-mpegURL",
                            quality = "1080p FHD • TVLogy HLS",
                            isHls = true,
                            headers = headers,
                            sourceName = "TVLogy (SupJav Master HLS)"
                        )
                    )
                }
            }

            // Fallback match MP4
            if (sources.isEmpty()) {
                val mp4Matcher = MP4_REGEX.matcher(processed)
                while (mp4Matcher.find()) {
                    val streamUrl = mp4Matcher.group()
                    sources.add(
                        SupJavSource(
                            url = streamUrl,
                            mimeType = "video/mp4",
                            quality = "720p HD • TVLogy MP4",
                            isHls = false,
                            headers = headers,
                            sourceName = "TVLogy (SupJav MP4)"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TVLogy resolution error for $embedUrl: ${e.message}")
        }
        sources
    }

    // ----------------------------------------------------
    // StreamWish / Wishembed / Awish / Dwish Extractor
    // ----------------------------------------------------
    private suspend fun resolveStreamwish(embedUrl: String, parentPageUrl: String): List<SupJavSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<SupJavSource>()
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", SupJavNetwork.DEFAULT_USER_AGENT)
                .header("Referer", parentPageUrl)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val html = resp.body?.string() ?: return@withContext emptyList()
            var processed = html

            // Unpack Dean Edwards javascript
            val scriptBlocks = html.split("<script")
            for (block in scriptBlocks) {
                if (JsUnpacker.isPacked(block)) {
                    val unpacked = JsUnpacker.unpack(block)
                    processed += "\n$unpacked"
                }
            }
            processed = processed.replace("\\/", "/")

            val host = extractHost(embedUrl)
            val headers = mapOf(
                "User-Agent" to SupJavNetwork.DEFAULT_USER_AGENT,
                "Referer" to embedUrl,
                "Origin" to "https://$host"
            )

            // 1. Sources array pattern: sources:[{file:"..."}]
            val fileMatcher = SOURCES_FILE_REGEX.matcher(processed)
            while (fileMatcher.find()) {
                val streamUrl = fileMatcher.group(1)
                val isHls = streamUrl.contains(".m3u8")
                sources.add(
                    SupJavSource(
                        url = streamUrl,
                        mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                        quality = if (isHls) "1080p FHD • StreamWish HLS" else "720p HD • StreamWish MP4",
                        isHls = isHls,
                        headers = headers,
                        sourceName = "StreamWish (SupJav Server)"
                    )
                )
            }

            // 2. Direct regex search in processed text
            if (sources.isEmpty()) {
                val m3u8Matcher = M3U8_REGEX.matcher(processed)
                while (m3u8Matcher.find()) {
                    val streamUrl = m3u8Matcher.group()
                    sources.add(
                        SupJavSource(
                            url = streamUrl,
                            mimeType = "application/x-mpegURL",
                            quality = "1080p FHD • StreamWish HLS",
                            isHls = true,
                            headers = headers,
                            sourceName = "StreamWish (SupJav HLS)"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "StreamWish resolution error for $embedUrl: ${e.message}")
        }
        sources
    }

    // ----------------------------------------------------
    // VOE Extractor
    // ----------------------------------------------------
    private suspend fun resolveVoe(embedUrl: String, parentPageUrl: String): List<SupJavSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<SupJavSource>()
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", SupJavNetwork.DEFAULT_USER_AGENT)
                .header("Referer", parentPageUrl)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val html = resp.body?.string() ?: return@withContext emptyList()
            val host = extractHost(embedUrl)
            val headers = mapOf(
                "User-Agent" to SupJavNetwork.DEFAULT_USER_AGENT,
                "Referer" to embedUrl,
                "Origin" to "https://$host"
            )

            // Check for HLS source in VOE scripts: 'hls': '...' or "hls": "..."
            val hlsRegex = Pattern.compile("""['"]hls['"]\s*:\s*['"](https?://[^'"]+)['"]""")
            val hlsMatcher = hlsRegex.matcher(html)
            if (hlsMatcher.find()) {
                val streamUrl = hlsMatcher.group(1)
                sources.add(
                    SupJavSource(
                        url = streamUrl,
                        mimeType = "application/x-mpegURL",
                        quality = "1080p FHD • VOE HLS",
                        isHls = true,
                        headers = headers,
                        sourceName = "VOE (SupJav Server)"
                    )
                )
                return@withContext sources
            }

            // Check for base64 encoded stream token in VOE
            val b64Regex = Pattern.compile("""(?:sources|mp4|source)\s*=\s*\[?['"]([A-Za-z0-9+/=]{20,})['"]\]?""")
            val b64Matcher = b64Regex.matcher(html)
            if (b64Matcher.find()) {
                val decoded = try {
                    String(Base64.decode(b64Matcher.group(1), Base64.DEFAULT), Charsets.UTF_8)
                } catch (e: Exception) { "" }
                if (decoded.startsWith("http")) {
                    val isHls = decoded.contains(".m3u8")
                    sources.add(
                        SupJavSource(
                            url = decoded,
                            mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                            quality = if (isHls) "1080p FHD • VOE HLS" else "720p HD",
                            isHls = isHls,
                            headers = headers,
                            sourceName = "VOE (SupJav Decoded)"
                        )
                    )
                    return@withContext sources
                }
            }

            // Standard m3u8 scan
            val m3u8Matcher = M3U8_REGEX.matcher(html)
            if (m3u8Matcher.find()) {
                sources.add(
                    SupJavSource(
                        url = m3u8Matcher.group(),
                        mimeType = "application/x-mpegURL",
                        quality = "1080p FHD • VOE HLS",
                        isHls = true,
                        headers = headers,
                        sourceName = "VOE (SupJav HLS)"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "VOE resolution error for $embedUrl: ${e.message}")
        }
        sources
    }

    // ----------------------------------------------------
    // DoodStream / DS2Play Extractor
    // ----------------------------------------------------
    private suspend fun resolveDoodStream(embedUrl: String, parentPageUrl: String): List<SupJavSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<SupJavSource>()
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", SupJavNetwork.DEFAULT_USER_AGENT)
                .header("Referer", parentPageUrl)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val html = resp.body?.string() ?: return@withContext emptyList()
            val host = extractHost(embedUrl)

            // DoodStream /pass_md5/ endpoint extraction
            val passMd5Regex = Pattern.compile("""/pass_md5/([a-zA-Z0-9_\-]+)""")
            val passMatcher = passMd5Regex.matcher(html)
            if (passMatcher.find()) {
                val token = passMatcher.group(1)
                val passUrl = "https://$host/pass_md5/$token"

                val passReq = Request.Builder()
                    .url(passUrl)
                    .header("User-Agent", SupJavNetwork.DEFAULT_USER_AGENT)
                    .header("Referer", embedUrl)
                    .build()

                val passResp = httpClient.newCall(passReq).execute()
                if (passResp.isSuccessful) {
                    val passBody = passResp.body?.string() ?: ""
                    if (passBody.isNotBlank()) {
                        val randomStr = generateRandomString(10)
                        val expiry = System.currentTimeMillis()
                        val streamUrl = "$passBody$randomStr?token=$token&expiry=$expiry"

                        sources.add(
                            SupJavSource(
                                url = streamUrl,
                                mimeType = "video/mp4",
                                quality = "1080p HD • DoodStream",
                                isHls = false,
                                headers = mapOf(
                                    "User-Agent" to SupJavNetwork.DEFAULT_USER_AGENT,
                                    "Referer" to embedUrl
                                ),
                                sourceName = "DoodStream (SupJav Server)"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DoodStream resolution error for $embedUrl: ${e.message}")
        }
        sources
    }

    // ----------------------------------------------------
    // Generic Video Host Extractor (Fembed, Fasting, Filelions, etc.)
    // ----------------------------------------------------
    private suspend fun resolveGenericHost(embedUrl: String, parentPageUrl: String): List<SupJavSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<SupJavSource>()
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", SupJavNetwork.DEFAULT_USER_AGENT)
                .header("Referer", parentPageUrl)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val html = resp.body?.string() ?: return@withContext emptyList()
            var processed = html

            if (JsUnpacker.isPacked(html)) {
                processed = JsUnpacker.unpack(html)
            }
            processed = processed.replace("\\/", "/")

            val host = extractHost(embedUrl)
            val headers = mapOf(
                "User-Agent" to SupJavNetwork.DEFAULT_USER_AGENT,
                "Referer" to embedUrl,
                "Origin" to "https://$host"
            )

            val m3u8Matcher = M3U8_REGEX.matcher(processed)
            while (m3u8Matcher.find()) {
                val url = m3u8Matcher.group()
                sources.add(
                    SupJavSource(
                        url = url,
                        mimeType = "application/x-mpegURL",
                        quality = "1080p FHD • HLS",
                        isHls = true,
                        headers = headers,
                        sourceName = "SupJav Host ($host)"
                    )
                )
            }

            if (sources.isEmpty()) {
                val mp4Matcher = MP4_REGEX.matcher(processed)
                while (mp4Matcher.find()) {
                    val url = mp4Matcher.group()
                    sources.add(
                        SupJavSource(
                            url = url,
                            mimeType = "video/mp4",
                            quality = "720p HD • MP4",
                            isHls = false,
                            headers = headers,
                            sourceName = "SupJav Host ($host)"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Generic host resolution error for $embedUrl: ${e.message}")
        }
        sources
    }

    private fun extractHost(url: String): String {
        return try {
            URI(url).host ?: "supjav.com"
        } catch (e: Exception) {
            "supjav.com"
        }
    }

    private fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}
