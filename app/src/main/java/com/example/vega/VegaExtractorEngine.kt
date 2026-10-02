package com.example.vega

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
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
                // Direct video formats
                isValidDirectMediaUrl(link) -> {
                    results.add(createDirectStream(link, "Direct Video Stream"))
                }

                // PixelDrain
                lower.contains("pixeldrain.com") -> {
                    extractPixelDrain(link)?.let { results.add(it) }
                }

                // Gofile
                lower.contains("gofile.io") -> {
                    extractGofile(link)?.let { results.add(it) }
                }

                // Filepress / Filebee
                lower.contains("filepress") || lower.contains("filebee") -> {
                    results.addAll(extractFilepress(link))
                }

                // GDFlix / DriveBot / ResumeCloud
                lower.contains("gdflix") || lower.contains("gdrive") || lower.contains("drivebot") -> {
                    results.addAll(extractGDFlix(link))
                }

                // FastDL embed page -> extract underlying video source!
                lower.contains("fastdl.zip/embed") || lower.contains("fastdl.me/embed") || lower.contains("/embed.php") || lower.contains("/player.php") -> {
                    val fromEmbed = extractVideoFromEmbedPage(link)
                    if (fromEmbed.isNotEmpty()) {
                        results.addAll(fromEmbed)
                    } else {
                        results.addAll(extractHubCloud(link))
                    }
                }

                // HubCloud / HubDrive / VCloud / FastDL / NexDrive / VegaDrive / GreenMotors
                lower.contains("hubcloud") || lower.contains("hubdrive") || lower.contains("vcloud") ||
                lower.contains("fastdl") || lower.contains("nexdrive") || lower.contains("vegadrive") ||
                lower.contains("greenmotors") || lower.contains("homelander") || lower.contains("driveseed") ||
                lower.contains("driveleech") || lower.contains("video-seed") -> {
                    results.addAll(extractHubCloud(link))
                }

                // StreamTape
                lower.contains("streamtape.com") || lower.contains("streamtape.to") || lower.contains("streamtape.net") -> {
                    extractStreamTape(link)?.let { results.add(it) }
                }

                // DoodStream / Dsafepal
                lower.contains("dood") || lower.contains("ds2play") || lower.contains("dsvid") -> {
                    extractDoodStream(link)?.let { results.add(it) }
                }

                // Generic HTML inspection for download buttons or video tags
                else -> {
                    val generic = extractHubCloud(link)
                    if (generic.isNotEmpty()) {
                        results.addAll(generic)
                    } else {
                        val embedStreams = extractVideoFromEmbedPage(link)
                        if (embedStreams.isNotEmpty()) {
                            results.addAll(embedStreams)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Extractor error on $link: ${e.message}")
        }

        // Filter out any invalid non-video HTML URLs to prevent ExoPlayer hanging
        val cleanList = results
            .filter { isValidPlayableStreamResult(it) }
            .distinctBy { it.url }

        return@withContext cleanList
    }

    private fun isValidDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".m3u8") ||
                lower.contains(".m3u8?") || lower.contains(".mp4?") || lower.contains(".mkv?") ||
                lower.contains("video-downloads.googleusercontent.com") ||
                lower.contains("storage.googleapis.com") ||
                lower.contains(".r2.cloudflarestorage.com") ||
                lower.contains("pixeldrain.com/api/file/")
    }

    private fun isValidPlayableStreamResult(st: VegaStreamResult): Boolean {
        val u = st.url.trim()
        if (u.isBlank() || !u.startsWith("http")) return false
        val lower = u.lowercase()
        // Reject raw html embed pages or shorteners masquerading as video URLs
        if (lower.contains("embed.php?") || lower.contains("player.php?") || lower.endsWith(".html") || lower.endsWith(".php")) {
            return false
        }
        if (lower.contains("gadgetswebsite") || lower.contains("techmny") || lower.contains("dulink")) {
            return false
        }
        return true
    }

    suspend fun extractHubCloud(targetUrl: String, depth: Int = 0): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        if (depth > 3) return@withContext emptyList()
        val streams = mutableListOf<VegaStreamResult>()
        try {
            var currentUrl = targetUrl.trim()

            // 1. Handle video-seed / driveseed / driveleech
            if (currentUrl.contains("video-seed") || currentUrl.contains("videoseed") || currentUrl.contains("?url=")) {
                val seedRes = resolveVideoSeed(currentUrl)
                if (seedRes != null) return@withContext listOf(seedRes)
            }
            if (currentUrl.contains("driveseed") || currentUrl.contains("driveleech")) {
                val driveRes = resolveDriveseed(currentUrl)
                if (driveRes != null) return@withContext listOf(driveRes)
            }

            // 2. Handle landing bypass if needed
            if (currentUrl.contains("href.li/?") || currentUrl.contains("landing") || currentUrl.contains("dulink")) {
                val bypassed = bypassLanding(currentUrl)
                if (!bypassed.isNullOrBlank()) {
                    currentUrl = bypassed
                }
            }

            val req = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", getBaseDomain(currentUrl))
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext streams
                resp.body?.string().orEmpty()
            }

            if (html.isBlank()) return@withContext streams

            // 3. Check for double base64 decoded links: atob(atob('...'))
            val doubleAtobPattern = Pattern.compile("atob\\(atob\\(['\"]([^'\"]+)['\"]\\)\\)")
            val m = doubleAtobPattern.matcher(html)
            if (m.find()) {
                val b64 = m.group(1)
                try {
                    val first = String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8)
                    val second = String(Base64.decode(first, Base64.DEFAULT), StandardCharsets.UTF_8)
                    if (second.startsWith("http")) {
                        val subStreams = extractHubCloud(second, depth + 1)
                        if (subStreams.isNotEmpty()) return@withContext subStreams
                    }
                } catch (_: Exception) {}
            }

            // 4. Look for single base64 or var url = '...'
            val varUrlPattern = Pattern.compile("var\\s+url\\s*=\\s*['\"]([^'\"]+)['\"]")
            val mUrl = varUrlPattern.matcher(html)
            if (mUrl.find()) {
                val foundUrl = mUrl.group(1).orEmpty()
                if (foundUrl.startsWith("http")) {
                    if (isValidDirectMediaUrl(foundUrl)) {
                        streams.add(createDirectStream(foundUrl, "HubCloud High Speed", currentUrl))
                    } else if (depth < 2) {
                        streams.addAll(extractHubCloud(foundUrl, depth + 1))
                    }
                }
            }

            // 5. Look for Pixeldrain: var pxl = '...' or direct pixeldrain links
            val pxlPattern = Pattern.compile("var\\s+pxl\\s*=\\s*['\"]([^'\"]+)['\"]")
            val mPxl = pxlPattern.matcher(html)
            if (mPxl.find()) {
                val pxlVal = mPxl.group(1).orEmpty().trim()
                if (pxlVal.isNotBlank()) {
                    val pxlId = pxlVal.substringAfterLast("/").substringBefore("?")
                    streams.add(
                        VegaStreamResult(
                            server = "PixelDrain Fast CDN",
                            url = "https://pixeldrain.com/api/file/$pxlId?download",
                            quality = "1080p HD",
                            format = "mp4",
                            headers = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to "https://pixeldrain.com/"
                            )
                        )
                    )
                }
            }

            // 6. Look for Fast Cloud / Direct Download buttons in HTML DOM
            val doc = Jsoup.parse(html, currentUrl)
            val directMediaButtons = doc.select(
                "a[href*=/stream/], a[href*=.m3u8], a[href*=.mp4], a[href*=.mkv], a[href*='cloudflarestorage'], " +
                "a.btn-success, a.btn-primary, a.server, a.btn, " +
                "a:contains(Instant Download), a:contains(Fast Cloud), a:contains(Cloud Download), a:contains(Download), a:contains(Stream)"
            )

            for (a in directMediaButtons) {
                val href = a.absUrl("href").ifBlank { a.attr("href") }
                if (href.isBlank() || href == currentUrl) continue
                val btnText = a.text().lowercase()

                when {
                    href.contains("pixeldrain.com") -> {
                        extractPixelDrain(href)?.let { streams.add(it) }
                    }
                    href.contains("gofile.io") -> {
                        extractGofile(href)?.let { streams.add(it) }
                    }
                    href.endsWith(".mkv") || href.endsWith(".mp4") || href.contains(".m3u8") || href.contains("/stream/") -> {
                        streams.add(createDirectStream(href, if (btnText.contains("fast")) "Fast Cloud CDN" else "Direct Video Stream", currentUrl))
                    }
                    href.contains("cloudflarestorage") -> {
                        streams.add(createDirectStream(href, "Cloudflare R2 Direct", currentUrl))
                    }
                    href.contains("/embed.php") || href.contains("/player.php") -> {
                        val embedStreams = extractVideoFromEmbedPage(href)
                        streams.addAll(embedStreams)
                    }
                    depth < 2 && (href.contains("hubcloud") || href.contains("fastdl") || href.contains("vcloud") || href.contains("nexdrive") || href.contains("/video/") || href.contains("/file/")) -> {
                        val sub = extractHubCloud(href, depth + 1)
                        streams.addAll(sub)
                    }
                }
            }

            // 7. Check for embedded video tags or inline JS stream files
            val inlineStream = extractInlineVideoFromHtml(html, currentUrl)
            if (inlineStream != null) {
                streams.add(inlineStream)
            }

        } catch (e: Exception) {
            Log.d(TAG, "HubCloud parse note: ${e.message}")
        }
        return@withContext streams
    }

    suspend fun extractVideoFromEmbedPage(embedUrl: String): List<VegaStreamResult> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VegaStreamResult>()
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", getBaseDomain(embedUrl))
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext streams
                resp.body?.string().orEmpty()
            }

            if (html.isBlank()) return@withContext streams

            val inline = extractInlineVideoFromHtml(html, embedUrl)
            if (inline != null) {
                streams.add(inline)
            }

            // Check if there are iframe sources
            val doc = Jsoup.parse(html, embedUrl)
            val iframes = doc.select("iframe[src]")
            for (iframe in iframes) {
                val src = iframe.absUrl("src")
                if (src.isNotBlank() && !src.contains("ads") && !src.contains("pop")) {
                    val sub = extractVideoFromEmbedPage(src)
                    streams.addAll(sub)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Embed extraction note for $embedUrl: ${e.message}")
        }
        return@withContext streams
    }

    private fun extractInlineVideoFromHtml(html: String, pageUrl: String): VegaStreamResult? {
        try {
            // 1. Regex file patterns
            val patterns = listOf(
                Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)["']"""),
                Regex("""file\s*:\s*["']([^"']+\.(?:m3u8|mp4|mkv)[^"']*)["']"""),
                Regex("""source\s*src\s*=\s*["']([^"']+\.(?:m3u8|mp4|mkv)[^"']*)["']"""),
                Regex("""<video[^>]+src=["']([^"']+)["']"""),
                Regex("""(?:src|url)\s*:\s*["']([^"']+\.(?:m3u8|mp4|mkv)[^"']*)["']""")
            )

            for (p in patterns) {
                val match = p.find(html)?.groupValues?.get(1)
                if (!match.isNullOrBlank()) {
                    val cleanUrl = match.replace("\\/", "/").trim()
                    val absUrl = if (cleanUrl.startsWith("http")) cleanUrl else {
                        val base = getBaseDomain(pageUrl)
                        if (cleanUrl.startsWith("/")) "$base$cleanUrl" else "$base/$cleanUrl"
                    }
                    if (absUrl.startsWith("http")) {
                        return createDirectStream(absUrl, "FastDL Media Stream", pageUrl)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private suspend fun resolveVideoSeed(url: String): VegaStreamResult? = withContext(Dispatchers.IO) {
        try {
            val uri = URI(url)
            val host = uri.host ?: "video-seed.xyz"
            val token = if (url.contains("?url=")) url.substringAfter("?url=") else ""
            if (token.isBlank()) return@withContext null

            val formBody = FormBody.Builder()
                .add("keys", token)
                .build()

            val req = Request.Builder()
                .url("https://$host/api")
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("x-token", host)
                .header("Referer", url)
                .build()

            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: ""
            val urlMatch = Regex("""url["']?\s*:\s*["']([^"']+)["']""").find(text)?.groupValues?.get(1)
            if (!urlMatch.isNullOrBlank()) {
                val directUrl = urlMatch.replace("\\/", "/")
                return@withContext createDirectStream(directUrl, "VideoSeed Direct Stream", url)
            }
        } catch (e: Exception) {
            Log.d(TAG, "video-seed resolution note: ${e.message}")
        }
        null
    }

    private suspend fun resolveDriveseed(url: String): VegaStreamResult? = withContext(Dispatchers.IO) {
        try {
            var pageUrl = url
            if (pageUrl.contains("r?key=")) {
                val rReq = Request.Builder().url(pageUrl).header("User-Agent", USER_AGENT).build()
                val rResp = httpClient.newCall(rReq).execute()
                val rHtml = rResp.body?.string() ?: ""
                val redirectMatch = Regex("""replace\(["']([^"']+)["']\)""").find(rHtml)?.groupValues?.get(1)
                if (redirectMatch != null) {
                    val uri = URI(pageUrl)
                    pageUrl = "${uri.scheme}://${uri.host}$redirectMatch"
                }
            }

            val req = Request.Builder().url(pageUrl).header("User-Agent", USER_AGENT).build()
            val resp = httpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html, pageUrl)

            val buttons = doc.select("a.btn-success, a.btn-primary, div.text-center a, a:contains(Instant Download), a:contains(Resume Cloud), a:contains(Cloud Download)")
            for (btn in buttons) {
                val text = btn.text().lowercase()
                val href = btn.attr("abs:href")
                if (href.isBlank()) continue

                if (text.contains("instant download") || text.contains("cloud download") || text.contains("resume cloud") || href.endsWith(".mp4") || href.endsWith(".mkv")) {
                    return@withContext createDirectStream(href, "DriveSeed Cloud Stream", pageUrl)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "driveseed resolution note: ${e.message}")
        }
        null
    }

    private fun bypassLanding(url: String): String? {
        try {
            val cleanUrl = if (url.contains("href.li/?")) url.substringAfter("href.li/?") else url
            val req = Request.Builder().url(cleanUrl).header("User-Agent", USER_AGENT).build()
            val resp = httpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return null
            val doc = Jsoup.parse(html, cleanUrl)

            val form = doc.select("form#landing").firstOrNull()
            if (form != null) {
                val action = form.attr("abs:action")
                val formBody = FormBody.Builder()
                form.select("input").forEach { input ->
                    val name = input.attr("name")
                    val value = input.attr("value")
                    if (name.isNotBlank()) formBody.add(name, value)
                }
                val postReq = Request.Builder().url(action).post(formBody.build()).header("User-Agent", USER_AGENT).header("Referer", cleanUrl).build()
                val postResp = httpClient.newCall(postReq).execute()
                val postHtml = postResp.body?.string() ?: return null
                val postDoc = Jsoup.parse(postHtml, action)
                val metaRefresh = postDoc.select("meta[http-equiv=refresh]").attr("content")
                if (metaRefresh.contains("url=")) {
                    return metaRefresh.substringAfter("url=").trim()
                }
            }

            val metaRefresh = doc.select("meta[http-equiv=refresh]").attr("content")
            if (metaRefresh.contains("url=")) {
                return metaRefresh.substringAfter("url=").trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in bypassLanding: ${e.message}")
        }
        return null
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

    private suspend fun extractStreamTape(url: String): VegaStreamResult? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val html = httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }
            val regex = Regex("""document\.getElementById\('robotlink'\)\.innerHTML\s*=\s*'([^']+)'\s*\+\s*'([^']+)'""")
            val match = regex.find(html)
            if (match != null) {
                val part1 = match.groupValues[1]
                val part2 = match.groupValues[2]
                val direct = "https:" + part1 + part2
                return@withContext createDirectStream(direct, "StreamTape Fast", url)
            }
        } catch (_: Exception) {}
        null
    }

    private suspend fun extractDoodStream(url: String): VegaStreamResult? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            val html = httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }
            val passRegex = Regex("""/pass_md5/[^"']+""")
            val passMatch = passRegex.find(html)?.value
            if (passMatch != null) {
                val passUrl = "https://dood.to$passMatch"
                val passReq = Request.Builder().url(passUrl).header("User-Agent", USER_AGENT).header("Referer", url).build()
                val md5Resp = httpClient.newCall(passReq).execute().use { it.body?.string().orEmpty() }
                if (md5Resp.isNotBlank()) {
                    val finalUrl = md5Resp + "zuid" + (System.currentTimeMillis() % 10000000000L)
                    return@withContext createDirectStream(finalUrl, "DoodStream CDN", url)
                }
            }
        } catch (_: Exception) {}
        null
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
        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Accept-Encoding" to "identity"
        )
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
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) {
            url
        }
    }
}
