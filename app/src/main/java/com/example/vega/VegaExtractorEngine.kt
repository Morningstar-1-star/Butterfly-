package com.example.vega

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object VegaExtractorEngine {
    private const val TAG = "VegaExtractorEngine"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noRedirectClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun extractStreams(rawLink: String, title: String? = null): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        val link = rawLink.trim()
        if (link.isBlank()) return@withContext emptyList()

        val results = mutableListOf<VegaStreamResult>()
        val lower = link.lowercase()

        try {
            when {
                lower.contains("hubcloud") || lower.contains("hubdrive") || lower.contains("vcloud") || lower.contains("fastdl") || lower.contains("nexdrive") -> {
                    results.addAll(extractHubCloud(link))
                }
                lower.contains("gdflix") || lower.contains("gdrive") || lower.contains("drivebot") -> {
                    results.addAll(extractGDFlix(link))
                }
                lower.contains("pixeldrain.com") -> {
                    extractPixelDrain(link)?.let { results.add(it) }
                }
                lower.contains("gofile.io") -> {
                    extractGofile(link)?.let { results.add(it) }
                }
                lower.contains("filepress") || lower.contains("filebee") -> {
                    results.addAll(extractFilepress(link))
                }
                lower.contains(".mkv") || lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains("video-downloads.googleusercontent.com") -> {
                    results.add(createDirectStream(link, "Direct Stream"))
                }
                else -> {
                    // Try generic page fetch or HubCloud fallback
                    val generic = extractHubCloud(link)
                    if (generic.isNotEmpty()) {
                        results.addAll(generic)
                    } else if (link.startsWith("http://") || link.startsWith("https://")) {
                        results.add(createDirectStream(link, "Direct Stream"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Extractor error on $link: ${e.message}")
        }

        return@withContext results.distinctBy { it.url }
    }

    private suspend fun extractHubCloud(targetUrl: String): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VegaStreamResult>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", getBaseDomain(targetUrl))
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext streams
                resp.body?.string().orEmpty()
            }

            if (html.isBlank()) return@withContext streams

            // 1. Look for double base64 decoded links: atob(atob('...'))
            val doubleAtobPattern = Pattern.compile("atob\\(atob\\(['\"]([^'\"]+)['\"]\\)\\)")
            val m = doubleAtobPattern.matcher(html)
            if (m.find()) {
                val b64 = m.group(1)
                try {
                    val first = String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8)
                    val second = String(Base64.decode(first, Base64.DEFAULT), StandardCharsets.UTF_8)
                    if (second.startsWith("http")) {
                        // Recurse or parse the resolved link
                        val subStreams = extractHubCloud(second)
                        if (subStreams.isNotEmpty()) return@withContext subStreams
                    }
                } catch (_: Exception) {}
            }

            // 2. Look for single base64 or var url = '...'
            val varUrlPattern = Pattern.compile("var\\s+url\\s*=\\s*['\"]([^'\"]+)['\"]")
            val mUrl = varUrlPattern.matcher(html)
            if (mUrl.find()) {
                val foundUrl = mUrl.group(1)
                if (foundUrl.startsWith("http")) {
                    streams.add(createDirectStream(foundUrl, "HubCloud High Speed", targetUrl))
                }
            }

            // 3. Look for Pixeldrain: var pxl = '...'
            val pxlPattern = Pattern.compile("var\\s+pxl\\s*=\\s*['\"]([^'\"]+)['\"]")
            val mPxl = pxlPattern.matcher(html)
            if (mPxl.find()) {
                val pxlVal = mPxl.group(1).trim()
                if (pxlVal.isNotBlank()) {
                    val pxlId = pxlVal.substringAfterLast("/").substringBefore("?")
                    streams.add(
                        VegaStreamResult(
                            server = "PixelDrain Fast CDN",
                            url = "https://pixeldrain.com/api/file/$pxlId?download",
                            quality = "1080p HD",
                            format = "mp4",
                            headers = mapOf("Referer" to "https://pixeldrain.com/")
                        )
                    )
                }
            }

            // 4. Parse DOM anchors: .server, .btn, buttons
            val doc = Jsoup.parse(html, targetUrl)
            val anchors = doc.select("a.server, a.btn, a[href*='pixeldrain'], a[href*='fastdl'], a[href*='vcloud'], a[href*='hubcloud'], a[href*='drive'], a[href*='gofile']")

            for (a in anchors) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                if (href.isBlank()) continue
                val text = a.text().lowercase()

                when {
                    href.contains("pixeldrain.com") -> {
                        extractPixelDrain(href)?.let { streams.add(it) }
                    }
                    href.contains("gofile.io") -> {
                        extractGofile(href)?.let { streams.add(it) }
                    }
                    href.contains("fastdl") || href.contains("fsl.") -> {
                        streams.add(createDirectStream(href, "FastDL CDN", targetUrl))
                    }
                    href.contains(".mkv") || href.contains(".mp4") -> {
                        streams.add(createDirectStream(href, "Direct Video CDN", targetUrl))
                    }
                    href.contains("cloudflarestorage") -> {
                        streams.add(createDirectStream(href, "Cloudflare R2 Direct", targetUrl))
                    }
                    text.contains("instant") || text.contains("download") || text.contains("fast") || text.contains("fhd") -> {
                        // Probe redirect location
                        val resolved = resolveRedirectTarget(href, targetUrl)
                        if (resolved.isNotBlank() && resolved != href && !resolved.contains(targetUrl)) {
                            streams.add(createDirectStream(resolved, "HubCloud Direct", targetUrl))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "HubCloud parse note: ${e.message}")
        }
        return@withContext streams
    }

    private suspend fun extractGDFlix(targetUrl: String): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VegaStreamResult>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", getBaseDomain(targetUrl))
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext streams
                resp.body?.string().orEmpty()
            }

            val doc = Jsoup.parse(html, targetUrl)

            // 1. ResumeCloud button
            val resumeLink = doc.select(".btn-secondary, .btn-success, a:contains(Resume)").firstOrNull()?.absUrl("href")
            if (!resumeLink.isNullOrBlank()) {
                val resolved = resolveRedirectTarget(resumeLink, targetUrl)
                if (resolved.isNotBlank()) {
                    streams.add(createDirectStream(resolved, "GDFlix ResumeCloud", targetUrl))
                }
            }

            // 2. Direct seed / instant link
            val seedLink = doc.select(".btn-danger, a:contains(Instant), a:contains(Direct)").firstOrNull()?.absUrl("href")
            if (!seedLink.isNullOrBlank()) {
                val direct = if (seedLink.contains("?url=")) {
                    seedLink.substringAfter("?url=").substringBefore("&")
                } else {
                    resolveRedirectTarget(seedLink, targetUrl)
                }
                if (direct.isNotBlank()) {
                    streams.add(createDirectStream(direct, "GDFlix Instant", targetUrl))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "GDFlix parse note: ${e.message}")
        }
        return@withContext streams
    }

    private fun extractPixelDrain(link: String): VegaStreamResult? {
        val lower = link.lowercase()
        val fileId = when {
            lower.contains("/u/") -> link.substringAfter("/u/").substringBefore("/").substringBefore("?")
            lower.contains("/d/") -> link.substringAfter("/d/").substringBefore("/").substringBefore("?")
            lower.contains("/api/file/") -> link.substringAfter("/api/file/").substringBefore("/").substringBefore("?")
            else -> link.substringAfterLast("/").substringBefore("?")
        }.trim()

        if (fileId.isBlank()) return null
        return VegaStreamResult(
            server = "PixelDrain High Speed Direct",
            url = "https://pixeldrain.com/api/file/$fileId?download",
            quality = "1080p HD",
            format = "mp4",
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://pixeldrain.com/"
            )
        )
    }

    private suspend fun extractGofile(link: String): VegaStreamResult? = withContext(Dispatchers.IO) {
        val fileId = link.substringAfterLast("/").substringBefore("?").trim()
        if (fileId.isBlank()) return@withContext null
        try {
            val req = Request.Builder()
                .url("https://api.gofile.io/contents/$fileId?wt=4fd6sg89d7s6")
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (json.optString("status") == "ok") {
                    val data = json.optJSONObject("data")
                    val children = data?.optJSONObject("children")
                    if (children != null) {
                        val firstKey = children.keys().asSequence().firstOrNull()
                        if (firstKey != null) {
                            val fileObj = children.optJSONObject(firstKey)
                            val linkUrl = fileObj?.optString("link")
                            if (!linkUrl.isNullOrBlank()) {
                                return@withContext VegaStreamResult(
                                    server = "Gofile Cloud CDN",
                                    url = linkUrl,
                                    quality = "1080p HD",
                                    format = "mp4",
                                    headers = mapOf(
                                        "User-Agent" to USER_AGENT,
                                        "Referer" to "https://gofile.io/"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    private suspend fun extractFilepress(link: String): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VegaStreamResult>()
        try {
            val baseUrl = getBaseDomain(link)
            val fileId = link.substringAfterLast("/").substringBefore("?").trim()
            if (fileId.isBlank()) return@withContext streams

            val jsonBody = JSONObject().apply {
                put("id", fileId)
                put("method", "indexDownlaod")
                put("captchaValue", null)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/api/file/downlaod/")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .header("Referer", baseUrl)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@withContext streams
                    val json = JSONObject(body)
                    val token = json.optString("data")
                    if (token.isNotBlank()) {
                        val req2 = Request.Builder()
                            .url("$baseUrl/api/file/downlaod2/")
                            .post(JSONObject().apply {
                                put("id", token)
                                put("method", "indexDownlaod")
                                put("captchaValue", null)
                            }.toString().toRequestBody("application/json".toMediaType()))
                            .header("User-Agent", USER_AGENT)
                            .header("Referer", baseUrl)
                            .build()

                        httpClient.newCall(req2).execute().use { resp2 ->
                            if (resp2.isSuccessful) {
                                val body2 = resp2.body?.string() ?: return@withContext streams
                                val json2 = JSONObject(body2)
                                val arr = json2.optJSONArray("data")
                                if (arr != null && arr.length() > 0) {
                                    val streamUrl = arr.optString(0)
                                    if (streamUrl.isNotBlank()) {
                                        streams.add(createDirectStream(streamUrl, "Filepress Cloud", baseUrl))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext streams
    }

    private fun resolveRedirectTarget(url: String, referer: String? = null): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", USER_AGENT)
                .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
                .build()
            noRedirectClient.newCall(req).execute().use { resp ->
                val loc = resp.header("Location")
                if (!loc.isNullOrBlank()) loc else resp.request.url.toString()
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun createDirectStream(url: String, serverName: String, referer: String? = null): VegaStreamResult {
        val lower = url.lowercase()
        val format = when {
            lower.contains(".m3u8") -> "hls"
            lower.contains(".mkv") -> "mkv"
            else -> "mp4"
        }
        val headers = mutableMapOf("User-Agent" to USER_AGENT)
        if (!referer.isNullOrBlank()) {
            headers["Referer"] = referer
        }
        return VegaStreamResult(
            server = serverName,
            url = url,
            quality = "1080p HD",
            format = format,
            headers = headers
        )
    }

    private fun getBaseDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) {
            url
        }
    }
}
