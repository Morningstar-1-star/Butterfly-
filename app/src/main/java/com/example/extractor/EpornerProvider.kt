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
                val code = resp.code
                val loc = resp.header("Location")
                if (!loc.isNullOrBlank()) {
                    val fullUrl = if (loc.startsWith("/")) "https://www.eporner.com$loc" else loc
                    if (!fullUrl.contains("/dwn/")) fullUrl else null
                } else if ((code == 200 || code == 206) && !dwnUrl.contains("/dwn/")) {
                    dwnUrl
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun validateAndResolveMediaUrl(rawUrl: String, headersMap: Map<String, String>): String? {
        if (rawUrl.isBlank()) return null
        val targetUrl = if (rawUrl.contains("/dwn/")) {
            resolveCdnDirectMp4Url(rawUrl, headersMap) ?: return null
        } else rawUrl

        if (targetUrl.contains("/dwn/")) return null

        return try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("Range", "bytes=0-1024")
                .headers(okhttp3.Headers.Builder().apply { headersMap.forEach { (k, v) -> add(k, v) } }.build())
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val code = resp.code
                val mime = resp.header("Content-Type") ?: ""
                val host = try { Uri.parse(targetUrl).host ?: "unknown" } catch (_: Exception) { "unknown" }
                if ((resp.isSuccessful || code == 206 || code == 302) && (mime.contains("video", ignoreCase = true) || mime.contains("octet-stream", ignoreCase = true) || targetUrl.contains(".mp4"))) {
                    Log.i(TAG, "[EPORNER_VALIDATION_SUCCESS] host=$host, status=$code, mime=$mime")
                    targetUrl
                } else {
                    Log.w(TAG, "[EPORNER_VALIDATION_REJECTED] host=$host, status=$code, mime=$mime")
                    null
                }
            }
        } catch (e: Exception) {
            if (targetUrl.contains(".mp4") && !targetUrl.contains("/dwn/")) {
                val host = try { Uri.parse(targetUrl).host ?: "unknown" } catch (_: Exception) { "unknown" }
                Log.i(TAG, "[EPORNER_VALIDATION_FALLBACK] host=$host, stream URL accepted by format check")
                targetUrl
            } else null
        }
    }

    suspend fun getStreamData(urlOrId: String, context: android.content.Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrId)
        val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.eporner.com/video-$videoId/"
        
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
