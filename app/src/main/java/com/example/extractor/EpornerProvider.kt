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

    private fun extractVideoId(raw: String): String {
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
            val noRedirectClient = httpClient.newBuilder().followRedirects(false).build()
            val req = Request.Builder()
                .url(dwnUrl)
                .headers(okhttp3.Headers.Builder().apply { headersMap.forEach { (k, v) -> add(k, v) } }.build())
                .build()
            noRedirectClient.newCall(req).execute().use { resp ->
                val loc = resp.header("Location")
                if (!loc.isNullOrBlank()) {
                    if (loc.startsWith("/")) "https://www.eporner.com$loc" else loc
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getStreamData(urlOrId: String): StreamData? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrId)
        if (videoId.isBlank()) {
            Log.e(TAG, "Failed to extract video ID from $urlOrId")
            return@withContext null
        }

        val targetUrl = "https://www.eporner.com/video-$videoId/"
        val embedUrl = "https://www.eporner.com/embed/$videoId/"

        try {
            val epornerHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Referer" to "https://www.eporner.com/",
                "Origin" to "https://www.eporner.com"
            )

            var title = "Eporner Video"
            var thumb = ""
            val options = mutableListOf<PlayableStreamOption>()
            val seenUrls = mutableSetOf<String>()

            // 1. Check API v2 for direct metadata
            try {
                val apiReq = Request.Builder()
                    .url("https://www.eporner.com/api/v2/video/id/?id=$videoId")
                    .headers(okhttp3.Headers.Builder().apply { epornerHeaders.forEach { (k, v) -> add(k, v) } }.build())
                    .build()
                val apiResp = httpClient.newCall(apiReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
                if (!apiResp.isNullOrBlank()) {
                    val apiJson = org.json.JSONObject(apiResp)
                    val apiTitle = apiJson.optString("title", "")
                    if (apiTitle.isNotBlank()) title = apiTitle
                    val defaultThumb = apiJson.optJSONObject("default_thumb")?.optString("src", "") ?: apiJson.optString("thumb", "")
                    if (defaultThumb.isNotBlank()) thumb = defaultThumb
                }
            } catch (e: Exception) {
                Log.w(TAG, "API v2 lookup failed: ${e.message}")
            }

            // 2. Query XHR player config for direct MP4 sources
            try {
                val xhrReq = Request.Builder()
                    .url("https://www.eporner.com/xhr/video/$videoId")
                    .headers(okhttp3.Headers.Builder().apply { epornerHeaders.forEach { (k, v) -> add(k, v) } }.build())
                    .build()
                val xhrResp = httpClient.newCall(xhrReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
                if (!xhrResp.isNullOrBlank()) {
                    val json = org.json.JSONObject(xhrResp)
                    val sources = json.optJSONObject("sources") ?: json.optJSONObject("mp4")
                    if (sources != null) {
                        val qualities = listOf("1080p", "720p", "480p", "360p", "240p")
                        for (q in qualities) {
                            val qObj = sources.optJSONObject(q)
                            val src = qObj?.optString("src", "") ?: sources.optString(q, "")
                            if (src.isNotBlank() && !seenUrls.contains(src)) {
                                seenUrls.add(src)
                                options.add(
                                    PlayableStreamOption(
                                        qualityLabel = "$q Direct MP4",
                                        format = "mp4",
                                        isMuxed = true,
                                        videoUrl = src,
                                        providerType = ProviderType.DIRECT,
                                        headers = epornerHeaders
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "XHR player config lookup failed: ${e.message}")
            }

            // 3. Resolve direct CDN Location headers from /dwn/ endpoints without following redirects
            val dwnEndpoints = listOf(
                "https://www.eporner.com/dwn/$videoId/1080p" to "1080p Direct MP4",
                "https://www.eporner.com/dwn/$videoId/720p" to "720p Direct MP4",
                "https://www.eporner.com/dwn/$videoId/480p" to "480p Direct MP4",
                "https://www.eporner.com/dwn/$videoId" to "SD Direct MP4"
            )

            for ((dwnUrl, label) in dwnEndpoints) {
                val directCdnMp4 = resolveCdnDirectMp4Url(dwnUrl, epornerHeaders)
                val finalUrl = directCdnMp4 ?: dwnUrl
                if (!seenUrls.contains(finalUrl)) {
                    seenUrls.add(finalUrl)
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = label,
                            format = "mp4",
                            isMuxed = true,
                            videoUrl = finalUrl,
                            providerType = ProviderType.DIRECT,
                            headers = epornerHeaders
                        )
                    )
                }
            }

            // 4. HTML Scrape fallback if needed
            if (options.isEmpty()) {
                val pagesToScrape = listOf(targetUrl, embedUrl)
                for (pUrl in pagesToScrape) {
                    try {
                        val req = Request.Builder()
                            .url(pUrl)
                            .headers(okhttp3.Headers.Builder().apply { epornerHeaders.forEach { (k, v) -> add(k, v) } }.build())
                            .build()

                        val html = httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) resp.body?.string() else null
                        }

                        if (!html.isNullOrBlank()) {
                            val mp4Pattern = Pattern.compile("(https?://[^\"]+?\\.mp4[^\"]*)", Pattern.CASE_INSENSITIVE)
                            val matcher = mp4Pattern.matcher(html)

                            while (matcher.find()) {
                                var streamUrl = matcher.group(1)?.replace("&amp;", "&")?.replace("\\/", "/") ?: continue
                                if (seenUrls.contains(streamUrl)) continue
                                seenUrls.add(streamUrl)

                                val qualityLabel = when {
                                    streamUrl.contains("1080p", ignoreCase = true) -> "1080p MP4"
                                    streamUrl.contains("720p", ignoreCase = true) -> "720p MP4"
                                    streamUrl.contains("480p", ignoreCase = true) -> "480p MP4"
                                    else -> "HD MP4"
                                }

                                options.add(
                                    PlayableStreamOption(
                                        qualityLabel = qualityLabel,
                                        format = "mp4",
                                        isMuxed = true,
                                        videoUrl = streamUrl,
                                        providerType = ProviderType.DIRECT,
                                        headers = epornerHeaders
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Scrape failed for $pUrl: ${e.message}")
                    }
                }
            }

            val sortedOptions = options.sortedByDescending { 
                when {
                    it.qualityLabel.contains("1080p") -> 1080
                    it.qualityLabel.contains("720p") -> 720
                    it.qualityLabel.contains("480p") -> 480
                    else -> 360
                }
            }

            val bestOption = sortedOptions.first()

            StreamData(
                videoId = videoId,
                videoUrl = bestOption.videoUrl ?: "",
                title = title,
                channelName = "Eporner",
                description = title,
                thumbnailUrl = thumb,
                availableStreamOptions = sortedOptions,
                selectedStreamOption = bestOption,
                providerId = PROVIDER_ID,
                providerType = ProviderType.DIRECT,
                headers = epornerHeaders
            )
        } catch (e: Exception) {
            Log.e(TAG, "Eporner extraction failed: ${e.message}", e)
            null
        }
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
