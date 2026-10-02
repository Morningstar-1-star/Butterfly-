package com.example.extractor.nuvio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object NuvioHubCloudResolver {
    private const val TAG = "NuvioHubCloudResolver"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val DEFAULT_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    suspend fun resolveStreamUrl(initialUrl: String, referer: String? = null): NuvioStreamResult? = withContext(Dispatchers.IO) {
        if (initialUrl.isBlank()) return@withContext null

        try {
            var currentUrl = initialUrl.trim()
            val headers = DEFAULT_HEADERS.toMutableMap()
            if (!referer.isNullOrBlank()) {
                headers["Referer"] = referer
            }

            // Direct playable media extensions
            if (currentUrl.endsWith(".m3u8") || currentUrl.endsWith(".mp4") || currentUrl.endsWith(".mkv") || currentUrl.contains("/stream/")) {
                return@withContext NuvioStreamResult(
                    name = "Nuvio Direct",
                    title = "Direct Video Stream",
                    url = currentUrl,
                    quality = "1080p",
                    headers = headers
                )
            }

            // 1. Handle video-seed
            if (currentUrl.contains("video-seed") || currentUrl.contains("videoseed") || currentUrl.contains("?url=")) {
                val seedRes = resolveVideoSeed(currentUrl)
                if (seedRes != null) return@withContext seedRes
            }

            // 2. Handle driveseed / driveleech
            if (currentUrl.contains("driveseed") || currentUrl.contains("driveleech")) {
                val driveRes = resolveDriveseed(currentUrl)
                if (driveRes != null) return@withContext driveRes
            }

            // 3. Handle landing page / shortener bypass (href.li, unblockedgames, techmny, gadgets, wp-http)
            if (currentUrl.contains("href.li/?") || currentUrl.contains("unblockedgames") || currentUrl.contains("landing") ||
                currentUrl.contains("techmny") || currentUrl.contains("gadgets") || currentUrl.contains("wp-http") || currentUrl.contains("dulink")) {
                val bypassed = bypassLanding(currentUrl)
                if (!bypassed.isNullOrBlank()) {
                    currentUrl = bypassed
                }
            }

            // 4. Resolve HubCloud / GDFlix / FastDL / NexDrive / VCloud
            val reqBuilder = Request.Builder().url(currentUrl)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            val resp = httpClient.newCall(reqBuilder.build()).execute()
            val html = resp.body?.string() ?: return@withContext null
            val finalHost = resp.request.url.toString()

            val doc = Jsoup.parse(html, finalHost)

            // Look for direct download / fast cloud buttons
            val streamLinkEl = doc.select(
                "a[href*=/stream/], a[href*=.m3u8], a[href*=.mp4], a[href*=.mkv], a.btn-success, a.btn-primary, " +
                "a:contains(Instant Download), a:contains(Fast Cloud), a:contains(Cloud Download), a:contains(Download), a:contains(Stream)"
            ).firstOrNull()

            if (streamLinkEl != null) {
                var streamUrl = streamLinkEl.attr("abs:href")
                if (streamUrl.isNotBlank()) {
                    if (streamUrl.contains("hubcloud") || streamUrl.contains("/download") || streamUrl.contains("drive")) {
                        val subReq = Request.Builder()
                            .url(streamUrl)
                            .header("User-Agent", headers["User-Agent"]!!)
                            .header("Referer", finalHost)
                            .build()
                        val subResp = httpClient.newCall(subReq).execute()
                        val subHtml = subResp.body?.string() ?: ""
                        val subDoc = Jsoup.parse(subHtml, streamUrl)
                        val finalBtn = subDoc.select("a[href*=.mkv], a[href*=.mp4], a[href*=/stream/], a:contains(Fast Cloud), a:contains(Instant Download), a:contains(Download)").firstOrNull()
                        if (finalBtn != null) {
                            val direct = finalBtn.attr("abs:href")
                            if (direct.isNotBlank()) streamUrl = direct
                        }
                    }

                    return@withContext NuvioStreamResult(
                        name = "Nuvio HubCloud",
                        title = streamLinkEl.text().ifBlank { "Fast Cloud Stream" },
                        url = streamUrl,
                        quality = "1080p",
                        headers = mapOf(
                            "User-Agent" to headers["User-Agent"]!!,
                            "Referer" to finalHost
                        )
                    )
                }
            }

            // Check for embedded video or script URLs
            val videoSrc = doc.select("video source, video").attr("abs:src").takeIf { it.isNotBlank() }
                ?: Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4|mkv))["']""").find(html)?.groupValues?.get(1)
                ?: Regex("""source\s*src=["']([^"']+)["']""").find(html)?.groupValues?.get(1)

            if (!videoSrc.isNullOrBlank()) {
                val absUrl = if (videoSrc.startsWith("http")) videoSrc else "${URI(finalHost).scheme}://${URI(finalHost).host}$videoSrc"
                return@withContext NuvioStreamResult(
                    name = "Nuvio Cloud",
                    title = "Cloud Video Stream",
                    url = absUrl,
                    quality = "1080p",
                    headers = mapOf(
                        "User-Agent" to headers["User-Agent"]!!,
                        "Referer" to finalHost
                    )
                )
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed resolving HubCloud link $initialUrl: ${e.message}")
            null
        }
    }

    private suspend fun resolveVideoSeed(url: String): NuvioStreamResult? = withContext(Dispatchers.IO) {
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
                .header("User-Agent", DEFAULT_HEADERS["User-Agent"]!!)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("x-token", host)
                .header("Referer", url)
                .build()

            val resp = httpClient.newCall(req).execute()
            val text = resp.body?.string() ?: ""
            val urlMatch = Regex("""url["']?\s*:\s*["']([^"']+)["']""").find(text)?.groupValues?.get(1)
            if (!urlMatch.isNullOrBlank()) {
                val directUrl = urlMatch.replace("\\/", "/")
                return@withContext NuvioStreamResult(
                    name = "Nuvio VideoSeed",
                    title = "VideoSeed Direct Stream",
                    url = directUrl,
                    quality = "1080p",
                    headers = mapOf(
                        "User-Agent" to DEFAULT_HEADERS["User-Agent"]!!,
                        "Referer" to url
                    )
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "video-seed resolution note: ${e.message}")
        }
        null
    }

    private suspend fun resolveDriveseed(url: String): NuvioStreamResult? = withContext(Dispatchers.IO) {
        try {
            var pageUrl = url
            if (pageUrl.contains("r?key=")) {
                val rReq = Request.Builder().url(pageUrl).header("User-Agent", DEFAULT_HEADERS["User-Agent"]!!).build()
                val rResp = httpClient.newCall(rReq).execute()
                val rHtml = rResp.body?.string() ?: ""
                val redirectMatch = Regex("""replace\(["']([^"']+)["']\)""").find(rHtml)?.groupValues?.get(1)
                if (redirectMatch != null) {
                    val uri = URI(pageUrl)
                    pageUrl = "${uri.scheme}://${uri.host}$redirectMatch"
                }
            }

            val req = Request.Builder().url(pageUrl).header("User-Agent", DEFAULT_HEADERS["User-Agent"]!!).build()
            val resp = httpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html, pageUrl)

            val buttons = doc.select("a.btn-success, a.btn-primary, div.text-center a, a:contains(Instant Download), a:contains(Resume Cloud), a:contains(Cloud Download)")
            for (btn in buttons) {
                val text = btn.text().lowercase()
                val href = btn.attr("abs:href")
                if (href.isBlank()) continue

                if (text.contains("instant download") || text.contains("cloud download") || text.contains("resume cloud")) {
                    return@withContext NuvioStreamResult(
                        name = "Nuvio DriveSeed",
                        title = "DriveSeed Cloud Stream",
                        url = href,
                        quality = "1080p",
                        headers = mapOf(
                            "User-Agent" to DEFAULT_HEADERS["User-Agent"]!!,
                            "Referer" to pageUrl
                        )
                    )
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
            val host = try {
                val u = URI(cleanUrl)
                "${u.scheme}://${u.host}"
            } catch (e: Exception) { "" }

            val req = Request.Builder().url(cleanUrl).header("User-Agent", DEFAULT_HEADERS["User-Agent"]!!).build()
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
                val postReq = Request.Builder().url(action).post(formBody.build()).header("User-Agent", DEFAULT_HEADERS["User-Agent"]!!).header("Referer", cleanUrl).build()
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
}
