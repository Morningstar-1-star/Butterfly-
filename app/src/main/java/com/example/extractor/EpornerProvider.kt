package com.example.extractor

import android.net.Uri
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object EpornerProvider {
    private const val TAG = "EpornerProvider"
    const val PROVIDER_ID = "eporner"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            var request = chain.request()
            val builder = request.newBuilder()
            if (request.header("User-Agent") == null) {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            }
            if (request.header("Referer") == null) {
                builder.header("Referer", "https://www.eporner.com/")
            }
            chain.proceed(builder.build())
        }
        .build()

    fun extractVideoId(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        if (trimmed.contains("video-")) {
            val part = trimmed.substringAfter("video-").substringBefore("/")
            val idMatch = Regex("^([a-zA-Z0-9]{4,15})").find(part)
            if (idMatch != null) return idMatch.groupValues[1]
            return part.substringBefore("-")
        }
        if (trimmed.contains("/dwn/")) {
            val part = trimmed.substringAfter("/dwn/").substringBefore("/")
            val idMatch = Regex("^([a-zA-Z0-9]{4,15})").find(part)
            if (idMatch != null) return idMatch.groupValues[1]
            return part
        }
        val lastSegment = trimmed.substringAfterLast("/")
        val idMatch = Regex("([a-zA-Z0-9]{4,15})").find(lastSegment)
        if (idMatch != null) return idMatch.groupValues[1]
        return lastSegment
    }

    private fun resolveCdnDirectMp4Url(dwnUrl: String, headersMap: Map<String, String>): String? {
        return try {
            val redirectClient = httpClient.newBuilder().followRedirects(true).build()
            val req = Request.Builder()
                .url(dwnUrl)
                .header("Range", "bytes=0-1024")
                .headers(okhttp3.Headers.Builder().apply { headersMap.forEach { (k, v) -> add(k, v) } }.build())
                .build()
            redirectClient.newCall(req).execute().use { resp ->
                val code = resp.code
                val finalUrl = resp.request.url.toString()
                if ((code == 200 || code == 206) && !finalUrl.contains("/dwn/") && !finalUrl.contains("/dload/")) {
                    finalUrl
                } else if (resp.header("Location") != null) {
                    val loc = resp.header("Location")!!
                    if (loc.startsWith("/")) "https://www.eporner.com$loc" else loc
                } else finalUrl
            }
        } catch (e: Exception) {
            dwnUrl
        }
    }

    private fun validateAndResolveMediaUrl(rawUrl: String, headersMap: Map<String, String>): String? {
        if (rawUrl.isBlank()) return null
        val fullUrl = when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("/") -> "https://www.eporner.com$rawUrl"
            else -> rawUrl
        }
        val targetUrl = if (fullUrl.contains("/dwn/") || fullUrl.contains("/dload/")) {
            resolveCdnDirectMp4Url(fullUrl, headersMap) ?: fullUrl
        } else fullUrl

        return try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("Range", "bytes=0-1024")
                .headers(okhttp3.Headers.Builder().apply { headersMap.forEach { (k, v) -> add(k, v) } }.build())
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val code = resp.code
                val finalUrl = resp.request.url.toString()
                val mime = resp.header("Content-Type") ?: ""
                val host = try { Uri.parse(finalUrl).host ?: "unknown" } catch (_: Exception) { "unknown" }
                if ((resp.isSuccessful || code == 206 || code == 302) && (mime.contains("video", ignoreCase = true) || mime.contains("octet-stream", ignoreCase = true) || finalUrl.contains(".mp4") || targetUrl.contains(".mp4"))) {
                    Log.i(TAG, "[EPORNER_VALIDATION_SUCCESS] host=$host, status=$code, mime=$mime, url=$finalUrl")
                    finalUrl
                } else {
                    Log.w(TAG, "[EPORNER_VALIDATION_REJECTED] host=$host, status=$code, mime=$mime")
                    null
                }
            }
        } catch (e: Exception) {
            if (targetUrl.contains(".mp4")) {
                val host = try { Uri.parse(targetUrl).host ?: "unknown" } catch (_: Exception) { "unknown" }
                Log.i(TAG, "[EPORNER_VALIDATION_FALLBACK] host=$host, stream URL accepted by format check")
                targetUrl
            } else null
        }
    }

    suspend fun getStreamData(urlOrId: String, context: android.content.Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrId)
        val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.eporner.com/video-$videoId/"
        val defaultHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Referer" to "https://www.eporner.com/"
        )

        // Step 1: Use current yt-dlp Eporner extractor first
        if (context != null) {
            try {
                Log.i(TAG, "Extracting Eporner video via YtDlpResolver: $targetUrl")
                val res = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (res is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext res.streamData.copy(
                        providerId = PROVIDER_ID
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlpResolver Eporner extraction failed: ${e.message}")
            }
        }

        // Step 2: Direct Eporner API + HTML Fallback if yt-dlp fails
        try {
            Log.i(TAG, "Attempting direct Eporner API/HTML fallback for ID: $videoId")
            val apiReq = Request.Builder()
                .url("https://www.eporner.com/api/v2/video/id/?id=$videoId&thumbsize=big")
                .header("User-Agent", defaultHeaders["User-Agent"]!!)
                .header("Referer", "https://www.eporner.com/")
                .build()

            val apiJsonStr = httpClient.newCall(apiReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            var title = "Eporner Video #$videoId"
            var thumb = ""
            if (!apiJsonStr.isNullOrBlank()) {
                val apiJson = org.json.JSONObject(apiJsonStr)
                title = apiJson.optString("title", title)
                val thumbObj = apiJson.optJSONObject("default_thumb")
                thumb = thumbObj?.optString("src", "") ?: apiJson.optString("thumb", "")
            }

            // Parse video page for direct streams
            val pageReq = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", defaultHeaders["User-Agent"]!!)
                .header("Referer", "https://www.eporner.com/")
                .build()

            var pageHtml = httpClient.newCall(pageReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: ""

            // If video page returns empty or short HTML, attempt embed page
            if (pageHtml.length < 500) {
                val embedReq = Request.Builder()
                    .url("https://www.eporner.com/embed/$videoId/")
                    .header("User-Agent", defaultHeaders["User-Agent"]!!)
                    .header("Referer", "https://www.eporner.com/")
                    .build()
                pageHtml = httpClient.newCall(embedReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                } ?: pageHtml
            }

            val dwnLinks = mutableListOf<String>()

            // 1. Match /dload/ and /dwn/ links in page
            val dloadMatcher = Pattern.compile("(?:href|src|file|url)=[\"']([^\"']*(?:/dload/|/dwn/)[^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(pageHtml)
            while (dloadMatcher.find()) {
                val path = dloadMatcher.group(1) ?: continue
                val fullUrl = when {
                    path.startsWith("//") -> "https:$path"
                    path.startsWith("/") -> "https://www.eporner.com$path"
                    else -> path
                }
                if (!dwnLinks.contains(fullUrl)) dwnLinks.add(fullUrl)
            }

            // 2. Match direct .mp4 URLs in page attributes or JS
            val mp4Matcher = Pattern.compile("(?:href|src|file|url)=[\"']([^\"']+\\.mp4[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(pageHtml)
            while (mp4Matcher.find()) {
                val src = mp4Matcher.group(1) ?: continue
                val fullUrl = when {
                    src.startsWith("//") -> "https:$src"
                    src.startsWith("/") -> "https://www.eporner.com$src"
                    else -> src
                }
                if (!dwnLinks.contains(fullUrl)) dwnLinks.add(fullUrl)
            }

            // 3. Match raw http(s) .mp4 URLs embedded in JS config
            val rawMp4Regex = Regex("""https?:[\\\/]+[^\s"'<>]+\.mp4[^\s"'<>]*""")
            for (m in rawMp4Regex.findAll(pageHtml)) {
                val cleanUrl = m.value.replace("\\/", "/")
                if (!dwnLinks.contains(cleanUrl)) dwnLinks.add(cleanUrl)
            }

            val validOptions = mutableListOf<PlayableStreamOption>()
            for ((idx, rawStreamUrl) in dwnLinks.withIndex()) {
                val resolvedUrl = validateAndResolveMediaUrl(rawStreamUrl, defaultHeaders) ?: continue
                val qualityLabel = when {
                    rawStreamUrl.contains("1080p", ignoreCase = true) || resolvedUrl.contains("1080p", ignoreCase = true) -> "1080p Full HD (mp4)"
                    rawStreamUrl.contains("720p", ignoreCase = true) || resolvedUrl.contains("720p", ignoreCase = true) -> "720p HD (mp4)"
                    rawStreamUrl.contains("480p", ignoreCase = true) || resolvedUrl.contains("480p", ignoreCase = true) -> "480p SD (mp4)"
                    rawStreamUrl.contains("360p", ignoreCase = true) || resolvedUrl.contains("360p", ignoreCase = true) -> "360p (mp4)"
                    rawStreamUrl.contains("240p", ignoreCase = true) || resolvedUrl.contains("240p", ignoreCase = true) -> "240p (mp4)"
                    else -> "MP4 Direct Stream #${idx + 1}"
                }

                if (validOptions.none { it.videoUrl == resolvedUrl }) {
                    validOptions.add(
                        PlayableStreamOption(
                            qualityLabel = qualityLabel,
                            format = "mp4",
                            isMuxed = true,
                            videoUrl = resolvedUrl,
                            providerType = ProviderType.DIRECT,
                            headers = defaultHeaders
                        )
                    )
                }
            }

            if (validOptions.isNotEmpty()) {
                val bestOpt = validOptions.first()
                Log.i(TAG, "Eporner fallback resolved ${validOptions.size} valid streams")
                return@withContext StreamData(
                    videoId = videoId,
                    videoUrl = bestOpt.videoUrl ?: "",
                    title = title,
                    channelName = "Eporner",
                    thumbnailUrl = thumb,
                    availableStreamOptions = validOptions,
                    selectedStreamOption = bestOpt,
                    providerId = PROVIDER_ID,
                    providerType = ProviderType.DIRECT,
                    headers = defaultHeaders
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eporner direct fallback failed: ${e.message}", e)
        }

        null
    }

    suspend fun getHome(limit: Int = 25): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder().url("https://www.eporner.com/api/v2/video/search/?order=top-weekly&per_page=$limit&thumbsize=big").build()
            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext emptyList()

            val json = org.json.JSONObject(jsonStr)
            val videosArr = json.optJSONArray("videos") ?: return@withContext emptyList()
            for (i in 0 until videosArr.length()) {
                val video = videosArr.optJSONObject(i) ?: continue
                val id = video.optString("id", "")
                val title = video.optString("title", "Eporner Video")
                val itemVideoId = if (id.isNotBlank()) id else extractVideoId(video.optString("url", ""))
                val url = "https://www.eporner.com/video-$itemVideoId/"
                val thumbObj = video.optJSONObject("default_thumb")
                val thumb = thumbObj?.optString("src", "") ?: video.optString("thumb", "")
                val duration = video.optLong("length_sec", -1L)
                val views = video.optLong("views", -1L)

                items.add(
                    VideoItem(
                        id = url,
                        title = title,
                        uploaderName = "Eporner",
                        durationSeconds = duration,
                        viewCount = views,
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eporner getHome failed: ${e.message}")
        }
        items
    }

    suspend fun search(query: String, limit: Int = 25): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VideoItem>()
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder().url("https://www.eporner.com/api/v2/video/search/?query=$encoded&per_page=$limit&thumbsize=big").build()
            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext emptyList()

            val json = org.json.JSONObject(jsonStr)
            val videosArr = json.optJSONArray("videos") ?: return@withContext emptyList()
            for (i in 0 until videosArr.length()) {
                val video = videosArr.optJSONObject(i) ?: continue
                val id = video.optString("id", "")
                val title = video.optString("title", "Eporner Video")
                val itemVideoId = if (id.isNotBlank()) id else extractVideoId(video.optString("url", ""))
                val url = "https://www.eporner.com/video-$itemVideoId/"
                val thumbObj = video.optJSONObject("default_thumb")
                val thumb = thumbObj?.optString("src", "") ?: video.optString("thumb", "")
                val duration = video.optLong("length_sec", -1L)
                val views = video.optLong("views", -1L)

                items.add(
                    VideoItem(
                        id = url,
                        title = title,
                        uploaderName = "Eporner",
                        durationSeconds = duration,
                        viewCount = views,
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eporner search failed: ${e.message}")
        }
        items
    }
}
