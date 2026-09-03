package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object DailymotionProvider {
    private const val TAG = "DailymotionProvider"
    const val PROVIDER_ID = "dailymotion"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://www.dailymotion.com/")
                .header("Origin", "https://www.dailymotion.com")
                .build()
            chain.proceed(req)
        }
        .build()

    private val dmHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "https://www.dailymotion.com/",
        "Origin" to "https://www.dailymotion.com"
    )

    suspend fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val url = "https://api.dailymotion.com/videos?fields=id,title,owner.username,thumbnail_720_url,duration,views_total,created_time&flags=featured&limit=$limit&page=$page"
        parseDailymotionApi(url)
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()

        // 1. Check for specialized prefixes: dailymotion:playlist:, dailymotion:search:, dailymotion:user:
        if (cleanQuery.startsWith("dailymotion:playlist:", ignoreCase = true)) {
            val playlistId = cleanQuery.substringAfter("dailymotion:playlist:").trim()
            return@withContext getPlaylistVideos(playlistId, limit, page)
        }

        if (cleanQuery.startsWith("dailymotion:user:", ignoreCase = true)) {
            val username = cleanQuery.substringAfter("dailymotion:user:").trim()
            return@withContext getUserVideos(username, limit, page)
        }

        val rawQuery = if (cleanQuery.startsWith("dailymotion:search:", ignoreCase = true)) {
            cleanQuery.substringAfter("dailymotion:search:").trim()
        } else if (cleanQuery.startsWith("dailymotion:", ignoreCase = true)) {
            cleanQuery.substringAfter("dailymotion:").trim()
        } else {
            cleanQuery
        }

        val encoded = URLEncoder.encode(rawQuery, "UTF-8")
        val url = "https://api.dailymotion.com/videos?fields=id,title,owner.username,thumbnail_720_url,duration,views_total,created_time&search=$encoded&limit=$limit&page=$page"
        parseDailymotionApi(url)
    }

    suspend fun getPlaylistVideos(playlistId: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.substringAfterLast("/").substringBefore("?")
        val url = "https://api.dailymotion.com/playlist/$cleanId/videos?fields=id,title,owner.username,thumbnail_720_url,duration,views_total&limit=$limit&page=$page"
        parseDailymotionApi(url)
    }

    suspend fun getUserVideos(username: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanUser = username.substringAfterLast("/").substringBefore("?")
        val url = "https://api.dailymotion.com/user/$cleanUser/videos?fields=id,title,owner.username,thumbnail_720_url,duration,views_total&limit=$limit&page=$page"
        parseDailymotionApi(url)
    }

    private fun parseDailymotionApi(apiUrl: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(apiUrl)
                .headers(okhttp3.Headers.Builder().apply { dmHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val json = JSONObject(jsonStr)
            val listArr = json.optJSONArray("list") ?: return list
            for (i in 0 until listArr.length()) {
                val item = listArr.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                if (id.isBlank()) continue
                val title = item.optString("title", "Dailymotion Video")
                val ownerObj = item.optJSONObject("owner")
                val uploader = ownerObj?.optString("username", "Dailymotion") ?: "Dailymotion"
                val thumb = item.optString("thumbnail_720_url", "https://www.dailymotion.com/thumbnail/video/$id")
                val duration = item.optLong("duration", -1L)
                val views = item.optLong("views_total", -1L)

                list.add(
                    VideoItem(
                        id = "https://www.dailymotion.com/video/$id",
                        title = title,
                        uploaderName = uploader,
                        uploaderUrl = "https://www.dailymotion.com/$uploader",
                        durationSeconds = duration,
                        viewCount = views,
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        description = "Watch $title on Dailymotion."
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion API parse error: ${e.message}")
        }
        return list
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val videoId = when {
            cleanInput.startsWith("dailymotion:playlist:") || cleanInput.startsWith("dailymotion:user:") || cleanInput.startsWith("dailymotion:search:") -> {
                // If it's a playlist or user query, resolve the first video
                val items = search(cleanInput, limit = 1, page = 1)
                if (items.isNotEmpty()) {
                    items.first().id.substringAfter("/video/").substringBefore("?")
                } else ""
            }
            cleanInput.startsWith("dailymotion:") -> cleanInput.substringAfter("dailymotion:")
            cleanInput.contains("/video/") -> cleanInput.substringAfter("/video/").substringBefore("?").substringBefore("_")
            cleanInput.contains("dai.ly/") -> cleanInput.substringAfter("dai.ly/").substringBefore("?")
            cleanInput.startsWith("http") -> cleanInput.substringAfterLast("/").substringBefore("?").substringBefore("_")
            else -> cleanInput
        }

        if (videoId.isBlank()) return@withContext null

        try {
            // 1. Dailymotion Direct Player Metadata API
            val metadataUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
            val req = Request.Builder()
                .url(metadataUrl)
                .headers(okhttp3.Headers.Builder().apply { dmHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val json = JSONObject(jsonStr)
                val title = json.optString("title", "Dailymotion Video")
                val ownerObj = json.optJSONObject("owner")
                val uploader = ownerObj?.optString("username", "Dailymotion") ?: json.optString("owner.username", "Dailymotion")
                val postersObj = json.optJSONObject("posters")
                val thumb = postersObj?.optString("60", postersObj.optString("480", "")) ?: ""

                val qualitiesObj = json.optJSONObject("qualities")
                val options = mutableListOf<PlayableStreamOption>()

                if (qualitiesObj != null) {
                    val qualityKeys = listOf("1080", "720", "480", "360", "240")
                    for (qKey in qualityKeys) {
                        val arr = qualitiesObj.optJSONArray(qKey) ?: continue
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val streamUrl = item.optString("url", "")
                            val type = item.optString("type", "")
                            if (streamUrl.isNotBlank()) {
                                options.add(
                                    PlayableStreamOption(
                                        qualityLabel = "${qKey}p MP4 Direct",
                                        format = if (type.contains("mp4")) "mp4" else "m3u8",
                                        isMuxed = true,
                                        videoUrl = streamUrl,
                                        providerType = ProviderType.DIRECT,
                                        headers = dmHeaders
                                    )
                                )
                            }
                        }
                    }

                    // HLS Master Playlist option ("auto")
                    val autoArr = qualitiesObj.optJSONArray("auto")
                    if (autoArr != null && autoArr.length() > 0) {
                        val hlsUrl = autoArr.optJSONObject(0)?.optString("url", "")
                        if (!hlsUrl.isNullOrBlank()) {
                            options.add(
                                PlayableStreamOption(
                                    qualityLabel = "Adaptive HLS (Auto)",
                                    format = "m3u8",
                                    isMuxed = true,
                                    videoUrl = hlsUrl,
                                    providerType = ProviderType.DIRECT,
                                    headers = dmHeaders
                                )
                            )
                        }
                    }
                }

                if (options.isNotEmpty()) {
                    val bestOption = options.first()
                    return@withContext StreamData(
                        videoId = videoId,
                        videoUrl = bestOption.videoUrl ?: "",
                        title = title,
                        channelName = uploader,
                        description = title,
                        thumbnailUrl = thumb,
                        availableStreamOptions = options,
                        selectedStreamOption = bestOption,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        headers = dmHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion direct metadata extraction failed: ${e.message}")
        }

        // 2. Fall back to YtDlpResolver
        if (context != null) {
            try {
                Log.i(TAG, "Falling back to YtDlpResolver for Dailymotion video ID: $videoId")
                val targetUrl = if (cleanInput.startsWith("http")) cleanInput else "https://www.dailymotion.com/video/$videoId"
                val ytDlpRes = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytDlpRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytDlpRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = dmHeaders
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "YtDlpResolver Dailymotion fallback failed: ${e.message}")
            }
        }

        null
    }
}
