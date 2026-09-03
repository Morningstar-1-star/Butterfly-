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
            "https?://(media-files|cdn|get|server|stream|down|storage)[a-zA-Z0-9_\\-.]*\\.bunkr\\.[a-z]{2,8}/[^\"'\\s>]+",
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

        val fileId = parsed.id
        val domainsToTry = (listOf(parsed.domain) + BunkrUrlUtils.KNOWN_BUNKR_DOMAINS).distinct()

        var lastError: Exception? = null
        for (domain in domainsToTry) {
            try {
                val candidateUrl = "https://$domain/f/$fileId"
                val result = tryResolveFromUrl(candidateUrl, fileId, albumId, domain)
                if (result != null) {
                    return@withContext result
                }
            } catch (e: Exception) {
                lastError = e
                Log.d(TAG, "Domain $domain failed for file $fileId: ${e.message}")
            }
        }

        throw lastError ?: BunkrException.ResolutionFailedException(fileId, "Could not extract direct stream URL from any Bunkr mirror")
    }

    private fun tryResolveFromUrl(
        canonicalUrl: String,
        fileId: String,
        albumId: String,
        domain: String
    ): BunkrStreamResult? {
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
                    return null
                }
                response.body?.string() ?: ""
            }
        } catch (_: Exception) {
            return null
        }

        if (html.isBlank()) return null

        val doc = Jsoup.parse(html, canonicalUrl)

        var directMediaUrl: String? = null
        var title: String = ""
        var rawThumb: String? = null
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

                    rawThumb = fileObj.optString("thumbnail").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("poster").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("icon").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("preview").takeIf { it.isNotBlank() }

                    directMediaUrl = fileObj.optString("mediaUrl").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("cdnUrl").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("downloadUrl").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("streamUrl").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("src").takeIf { it.isNotBlank() }
                        ?: fileObj.optString("url").takeIf { it.isNotBlank() }

                    // Check server/cdn attributes
                    if (directMediaUrl.isNullOrBlank()) {
                        val server = fileObj.optString("server").takeIf { it.isNotBlank() }
                        val cdn = fileObj.optString("cdn").takeIf { it.isNotBlank() }
                        val identifier = fileObj.optString("identifier").takeIf { it.isNotBlank() } ?: fileObj.optString("name")
                        if (!cdn.isNullOrBlank() && identifier.isNotBlank()) {
                            directMediaUrl = "https://$cdn.$domain/$identifier"
                        } else if (!server.isNullOrBlank() && identifier.isNotBlank()) {
                            directMediaUrl = "https://media-files$server.$domain/$identifier"
                        }
                    }
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
            val downloadLink = doc.select("a[href*='media-files'], a[href*='cdn.'], a[href*='get.'], a[href*='stream.'], a[href*='down.'], a[download], a.download-btn, a:contains(Download)").firstOrNull()
            if (downloadLink != null) {
                val href = downloadLink.attr("abs:href").ifBlank { downloadLink.attr("href") }
                if (href.isNotBlank() && (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("//"))) {
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
            while (mp4Matcher.find()) {
                val candidate = mp4Matcher.group(0)
                if (!candidate.contains("/f/") && !candidate.contains("/a/")) {
                    directMediaUrl = candidate
                    break
                }
            }
        }

        if (directMediaUrl.isNullOrBlank()) {
            return null
        }

        // Fix relative URLs
        if (directMediaUrl.startsWith("//")) {
            directMediaUrl = "https:$directMediaUrl"
        } else if (directMediaUrl.startsWith("/")) {
            directMediaUrl = "https://$domain$directMediaUrl"
        }

        // Normalize title
        if (title.isBlank()) {
            val docTitle = doc.title().trim()
            title = docTitle.replace(Regex(" - Bunkr.*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("Bunkr - ", RegexOption.IGNORE_CASE), "")
                .trim()
        }
        if (title.isBlank()) {
            title = "Bunkr Video $fileId"
        }

        // MimeType check
        if (directMediaUrl.contains(".m3u8", ignoreCase = true)) {
            mimeType = "application/x-mpegURL"
        }

        val thumbnailUrl = BunkrUrlUtils.normalizeThumbnailUrl(rawThumb, domain, fileId)

        val requiredHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer" to "https://$domain/",
            "Origin" to "https://$domain",
            "Accept" to "*/*"
        )

        return BunkrStreamResult(
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
