package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object EpornerProvider {
    private const val TAG = "EpornerProvider"
    const val PROVIDER_ID = "eporner"

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            if (request.header("User-Agent") == null) {
                builder.header("User-Agent", DEFAULT_USER_AGENT)
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
        if (trimmed.contains("/embed/")) {
            val part = trimmed.substringAfter("/embed/").substringBefore("/")
            val idMatch = Regex("^([a-zA-Z0-9]{4,15})").find(part)
            if (idMatch != null) return idMatch.groupValues[1]
            return part
        }
        if (trimmed.contains("/dwn/") || trimmed.contains("/dload/")) {
            val part = trimmed.substringAfter("/dwn/").substringAfter("/dload/").substringBefore("/")
            val idMatch = Regex("^([a-zA-Z0-9]{4,15})").find(part)
            if (idMatch != null) return idMatch.groupValues[1]
            return part
        }
        if (trimmed.contains("gvideo.eporner.com/")) {
            val part = trimmed.substringAfter("gvideo.eporner.com/").substringBefore("/")
            val idMatch = Regex("^([a-zA-Z0-9]{4,15})").find(part)
            if (idMatch != null) return idMatch.groupValues[1]
            return part
        }
        val lastSegment = trimmed.substringAfterLast("/")
        val idMatch = Regex("([a-zA-Z0-9]{4,15})").find(lastSegment)
        if (idMatch != null) return idMatch.groupValues[1]
        return lastSegment
    }

    /**
     * Converts a 32-character hexadecimal string into base-36 chunks
     * matching the Eporner JS player / yt-dlp calc_hash algorithm.
     */
    fun calculateEpornerHash(hexHash: String): String {
        val clean = hexHash.trim().lowercase()
        if (clean.length < 32) return clean
        val sb = StringBuilder()
        for (i in 0 until 32 step 8) {
            val chunk = clean.substring(i, (i + 8).coerceAtMost(clean.length))
            try {
                val num = chunk.toLong(16)
                sb.append(java.lang.Long.toString(num, 36))
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing hex chunk $chunk: ${e.message}")
            }
        }
        return sb.toString()
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrId)
        val defaultHeaders = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to "https://www.eporner.com/embed/$videoId/",
            "Origin" to "https://www.eporner.com"
        )

        // STEP 1: Ultra-Fast Native Direct Extraction (~200ms - 400ms)
        try {
            Log.i(TAG, "Starting fast native Eporner extraction for video ID: $videoId")
            val embedUrl = "https://www.eporner.com/embed/$videoId/"
            val embedReq = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "https://www.eporner.com/")
                .build()

            var pageHtml = ""
            var title = "Eporner Video #$videoId"
            var posterUrl = ""

            try {
                httpClient.newCall(embedReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        pageHtml = resp.body?.string() ?: ""
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Embed fetch failed: ${e.message}")
            }

            // If embed failed or returned short content, try main video page
            if (pageHtml.length < 200) {
                val pageUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.eporner.com/video-$videoId/"
                val pageReq = Request.Builder()
                    .url(pageUrl)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Referer", "https://www.eporner.com/")
                    .build()
                try {
                    httpClient.newCall(pageReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            pageHtml = resp.body?.string() ?: ""
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Video page fetch failed: ${e.message}")
                }
            }

            // Extract title and poster from page HTML
            val titleMatch = Regex("""<title>(.*?)(?: - EPORNER)?</title>""", RegexOption.IGNORE_CASE).find(pageHtml)
            if (titleMatch != null) {
                title = titleMatch.groupValues[1].trim()
            }
            val posterMatch = Regex("""(?:poster|src)\s*[:=]\s*['"](https?://[^'"]*static[^'"]*thumbs[^'"]+)['"]""", RegexOption.IGNORE_CASE).find(pageHtml)
            if (posterMatch != null) {
                posterUrl = posterMatch.groupValues[1]
            }

            // Extract 32-char hex hash
            val hashRegex = Regex("""(?:hash|EP\.video\.player\.hash)\s*[:=]\s*['"]([0-9a-fA-F]{32})['"]""", RegexOption.IGNORE_CASE)
            val hashMatch = hashRegex.find(pageHtml)
            val rawHash = hashMatch?.groupValues?.get(1)

            if (!rawHash.isNullOrBlank()) {
                val calcHash = calculateEpornerHash(rawHash)
                Log.i(TAG, "Calculated Eporner hash for $videoId: $calcHash (raw: $rawHash)")

                val xhrUrl = "https://www.eporner.com/xhr/video/$videoId?hash=$calcHash"
                val xhrReq = Request.Builder()
                    .url(xhrUrl)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Referer", embedUrl)
                    .header("Origin", "https://www.eporner.com")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .build()

                val xhrResponseStr = httpClient.newCall(xhrReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!xhrResponseStr.isNullOrBlank()) {
                    val xhrJson = JSONObject(xhrResponseStr)
                    val isAvailable = xhrJson.optBoolean("available", true)
                    val sourcesObj = xhrJson.optJSONObject("sources")
                    val mp4Obj = sourcesObj?.optJSONObject("mp4")

                    if (mp4Obj != null && isAvailable) {
                        val parsedOptions = mutableListOf<Pair<Int, PlayableStreamOption>>()

                        val keys = mp4Obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val streamObj = mp4Obj.optJSONObject(key) ?: continue
                            val src = streamObj.optString("src", "").trim()
                            if (src.isBlank() || src.contains("na.mp4") || src.contains("404.mp4")) continue

                            val labelShort = streamObj.optString("labelShort", key)
                            val (height, label) = when {
                                key.contains("1080", ignoreCase = true) || labelShort.contains("1080", ignoreCase = true) -> 1080 to "1080p Full HD (mp4)"
                                key.contains("720", ignoreCase = true) || labelShort.contains("720", ignoreCase = true) -> 720 to "720p HD (mp4)"
                                key.contains("480", ignoreCase = true) || labelShort.contains("480", ignoreCase = true) -> 480 to "480p SD (mp4)"
                                key.contains("360", ignoreCase = true) || labelShort.contains("360", ignoreCase = true) -> 360 to "360p (mp4)"
                                key.contains("240", ignoreCase = true) || labelShort.contains("240", ignoreCase = true) -> 240 to "240p (mp4)"
                                else -> 480 to "MP4 Stream ($labelShort)"
                            }

                            parsedOptions.add(
                                height to PlayableStreamOption(
                                    qualityLabel = label,
                                    format = "mp4",
                                    isMuxed = true,
                                    videoUrl = src,
                                    providerType = ProviderType.DIRECT,
                                    headers = defaultHeaders
                                )
                            )
                        }

                        if (parsedOptions.isNotEmpty()) {
                            // Sort highest resolution first
                            val sortedOptions = parsedOptions
                                .sortedByDescending { it.first }
                                .map { it.second }
                                .distinctBy { it.videoUrl }

                            val bestOption = sortedOptions.first()
                            Log.i(TAG, "Fast Eporner extraction SUCCESS: ${sortedOptions.size} streams resolved in direct XHR mode")

                            return@withContext StreamData(
                                videoId = videoId,
                                videoUrl = bestOption.videoUrl ?: "",
                                title = title,
                                channelName = "Eporner",
                                thumbnailUrl = if (posterUrl.isNotBlank()) posterUrl else "https://static-sg-cdn.eporner.com/thumbs/static4/1/17/178/17873827/14_360.jpg",
                                availableStreamOptions = sortedOptions,
                                selectedStreamOption = bestOption,
                                providerId = PROVIDER_ID,
                                providerType = ProviderType.DIRECT,
                                headers = defaultHeaders
                            )
                        }
                    }
                }
            }

            // STEP 2: Backup fast parser for direct contentUrl or embed gvideo
            val gvideoMatch = Regex("""["']contentUrl["']\s*:\s*["']([^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE).find(pageHtml)
            val gvideoUrl = gvideoMatch?.groupValues?.get(1)
            if (!gvideoUrl.isNullOrBlank()) {
                val directOption = PlayableStreamOption(
                    qualityLabel = "720p HD (Direct)",
                    format = "mp4",
                    isMuxed = true,
                    videoUrl = gvideoUrl,
                    providerType = ProviderType.DIRECT,
                    headers = defaultHeaders
                )
                Log.i(TAG, "Eporner resolved via contentUrl direct: $gvideoUrl")
                return@withContext StreamData(
                    videoId = videoId,
                    videoUrl = gvideoUrl,
                    title = title,
                    channelName = "Eporner",
                    thumbnailUrl = posterUrl,
                    availableStreamOptions = listOf(directOption),
                    selectedStreamOption = directOption,
                    providerId = PROVIDER_ID,
                    providerType = ProviderType.DIRECT,
                    headers = defaultHeaders
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fast native Eporner extraction failed: ${e.message}", e)
        }

        // STEP 3: Fallback to YtDlp only as a last resort if context is available
        if (context != null) {
            try {
                val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.eporner.com/video-$videoId/"
                Log.i(TAG, "Attempting YtDlp fallback for Eporner: $targetUrl")
                val res = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (res is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext res.streamData.copy(
                        providerId = PROVIDER_ID
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlp fallback failed: ${e.message}")
            }
        }

        null
    }

    suspend fun getHome(limit: Int = 25, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url("https://www.eporner.com/api/v2/video/search/?order=top-weekly&per_page=$limit&page=$page&thumbsize=big")
                .header("User-Agent", DEFAULT_USER_AGENT)
                .build()
            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext emptyList()

            val json = JSONObject(jsonStr)
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

                val previewThumbsList = mutableListOf<String>()
                val thumbsArr = video.optJSONArray("thumbs")
                if (thumbsArr != null) {
                    for (t in 0 until thumbsArr.length()) {
                        val tObj = thumbsArr.optJSONObject(t)
                        val tSrc = tObj?.optString("src", "")
                        if (!tSrc.isNullOrBlank()) {
                            previewThumbsList.add(tSrc)
                        }
                    }
                }
                if (previewThumbsList.isEmpty() && thumb.isNotBlank()) {
                    previewThumbsList.addAll(com.example.util.PreviewFrameResolver.resolvePreviewFrames(
                        VideoItem(id = url, title = title, uploaderName = "Eporner", thumbnailUrl = thumb, providerId = PROVIDER_ID)
                    ))
                }

                items.add(
                    VideoItem(
                        id = url,
                        title = title,
                        uploaderName = "Eporner",
                        uploaderUrl = "https://www.eporner.com",
                        uploaderAvatarUrl = "https://static-sg-cdn.eporner.com/thumbs/static4/1/17/178/17873827/14_360.jpg",
                        durationSeconds = duration,
                        viewCount = views,
                        uploadDate = video.optString("added", "HD"),
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        previewThumbnails = previewThumbsList
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eporner getHome failed: ${e.message}")
        }
        items
    }

    suspend fun search(query: String, limit: Int = 25, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VideoItem>()
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://www.eporner.com/api/v2/video/search/?query=$encoded&per_page=$limit&page=$page&thumbsize=big")
                .header("User-Agent", DEFAULT_USER_AGENT)
                .build()
            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext emptyList()

            val json = JSONObject(jsonStr)
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

                val previewThumbsList = mutableListOf<String>()
                val thumbsArr = video.optJSONArray("thumbs")
                if (thumbsArr != null) {
                    for (t in 0 until thumbsArr.length()) {
                        val tObj = thumbsArr.optJSONObject(t)
                        val tSrc = tObj?.optString("src", "")
                        if (!tSrc.isNullOrBlank()) {
                            previewThumbsList.add(tSrc)
                        }
                    }
                }
                if (previewThumbsList.isEmpty() && thumb.isNotBlank()) {
                    previewThumbsList.addAll(com.example.util.PreviewFrameResolver.resolvePreviewFrames(
                        VideoItem(id = url, title = title, uploaderName = "Eporner", thumbnailUrl = thumb, providerId = PROVIDER_ID)
                    ))
                }

                items.add(
                    VideoItem(
                        id = url,
                        title = title,
                        uploaderName = "Eporner",
                        uploaderUrl = "https://www.eporner.com",
                        uploaderAvatarUrl = "https://static-sg-cdn.eporner.com/thumbs/static4/1/17/178/17873827/14_360.jpg",
                        durationSeconds = duration,
                        viewCount = views,
                        uploadDate = video.optString("added", "HD"),
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        previewThumbnails = previewThumbsList
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eporner search failed: ${e.message}")
        }
        items
    }
}

