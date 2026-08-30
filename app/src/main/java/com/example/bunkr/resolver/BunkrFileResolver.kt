package com.example.bunkr.resolver

import android.util.Log
import com.example.bunkr.model.BunkrException
import com.example.bunkr.model.BunkrStreamResult
import com.example.bunkr.model.BunkrUrlType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class BunkrFileResolver(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "BunkrFileResolver"

        private val DIRECT_CDN_PATTERN = Pattern.compile(
            "https?://(media-files|cdn|get|server|stream)[a-zA-Z0-9_\\-.]*\\.bunkr\\.[a-z]{2,8}/[^\"'\\s>]+",
            Pattern.CASE_INSENSITIVE
        )

        private val MP4_URL_PATTERN = Pattern.compile(
            "https?://[^\"'\\s>]+\\.(mp4|mkv|mov|webm|m3u8)(\\?[^\"'\\s>]*)?",
            Pattern.CASE_INSENSITIVE
        )
    }

    suspend fun resolveFile(fileUrl: String, albumId: String = "standalone"): BunkrStreamResult = withContext(Dispatchers.IO) {
        val parsed = BunkrUrlUtils.parseUrl(fileUrl)
            ?: throw BunkrException.InvalidUrlException(fileUrl)

        val canonicalUrl = parsed.canonicalUrl
        val fileId = parsed.id

        val request = Request.Builder()
            .url(canonicalUrl)
            .apply {
                BunkrUrlUtils.buildDefaultHeaders(canonicalUrl).forEach { (k, v) ->
                    addHeader(k, v)
                }
            }
            .build()

        val html = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        throw BunkrException.RateLimitedException(parsed.domain)
                    }
                    throw BunkrException.FileUnavailableException(fileId, "HTTP ${response.code}")
                }
                response.body?.string() ?: ""
            }
        } catch (e: Exception) {
            if (e is BunkrException) throw e
            throw BunkrException.NetworkTimeoutException(canonicalUrl)
        }

        if (html.isBlank()) {
            throw BunkrException.FileUnavailableException(fileId, "Empty HTML response")
        }

        val doc = Jsoup.parse(html, canonicalUrl)

        var directMediaUrl: String? = null
        var title: String = ""
        var thumbnailUrl: String? = null
        var mimeType = "video/mp4"

        // 1. Check Next.js __NEXT_DATA__
        val nextDataScript = doc.select("script#__NEXT_DATA__").firstOrNull()
        if (nextDataScript != null) {
            try {
                val jsonStr = nextDataScript.data()
                val json = JSONObject(jsonStr)
                val pageProps = json.optJSONObject("props")?.optJSONObject("pageProps")
                val fileObj = pageProps?.optJSONObject("file") ?: pageProps?.optJSONObject("media")

                if (fileObj != null) {
                    title = fileObj.optString("name").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("title").takeIf { it.isNotBlank() }
                        ?: ""

                    thumbnailUrl = fileObj.optString("thumbnail").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("poster").takeIf { it.isNotBlank() }

                    directMediaUrl = fileObj.optString("mediaUrl").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("cdnUrl").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("downloadUrl").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("url").takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed __NEXT_DATA__ parse for file $fileId: ${e.message}")
            }
        }

        // 2. Jsoup DOM parsing for video/source/download links
        if (directMediaUrl.isNullOrBlank()) {
            val videoElem = doc.select("video source[src], video[src]").firstOrNull()
            if (videoElem != null) {
                val src = videoElem.attr("abs:src").ifBlank { videoElem.attr("src") }
                if (src.isNotBlank()) {
                    directMediaUrl = src
                }
            }
        }

        if (directMediaUrl.isNullOrBlank()) {
            val downloadLink = doc.select("a[href*='media-files'], a[href*='cdn.'], a[download], a.download-btn, a:contains(Download)").firstOrNull()
            if (downloadLink != null) {
                val href = downloadLink.attr("abs:href").ifBlank { downloadLink.attr("href") }
                if (href.isNotBlank() && (href.startsWith("http://") || href.startsWith("https://"))) {
                    directMediaUrl = href
                }
            }
        }

        // 3. Regex search for direct CDN URLs in raw HTML
        if (directMediaUrl.isNullOrBlank()) {
            val cdnMatcher = DIRECT_CDN_PATTERN.matcher(html)
            if (cdnMatcher.find()) {
                directMediaUrl = cdnMatcher.group(0)
            }
        }

        if (directMediaUrl.isNullOrBlank()) {
            val mp4Matcher = MP4_URL_PATTERN.matcher(html)
            if (mp4Matcher.find()) {
                val candidate = mp4Matcher.group(0)
                if (!candidate.contains("bunkr.cr/f/") && !candidate.contains("bunkr.cr/a/")) {
                    directMediaUrl = candidate
                }
            }
        }

        if (directMediaUrl.isNullOrBlank()) {
            throw BunkrException.ResolutionFailedException(fileId, "Could not extract direct media stream URL from file page")
        }

        // Normalize title
        if (title.isBlank()) {
            val docTitle = doc.title().trim()
            title = docTitle.replace(" - Bunkr", "", ignoreCase = true)
                .replace(" - Bunkr.cr", "", ignoreCase = true)
                .trim()
        }
        if (title.isBlank()) {
            title = "Bunkr Video $fileId"
        }

        // MimeType check
        if (directMediaUrl.contains(".m3u8", ignoreCase = true)) {
            mimeType = "application/x-mpegURL"
        }

        val requiredHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Referer" to canonicalUrl
        )

        return@withContext BunkrStreamResult(
            source = "Bunkr",
            fileId = fileId,
            albumId = albumId,
            title = title,
            streamUrl = directMediaUrl,
            thumbnailUrl = thumbnailUrl,
            mimeType = mimeType,
            headers = requiredHeaders
        )
    }
}
