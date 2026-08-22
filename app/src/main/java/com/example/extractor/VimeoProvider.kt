package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VimeoProvider {
    private const val TAG = "VimeoProvider"
    const val PROVIDER_ID = "vimeo"

    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getHome(limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val url = "https://vimeo.com/api/v2/channel/staffpicks/videos.json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "https://vimeo.com/")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until minOf(jsonArray.length(), limit)) {
                    val item = jsonArray.optJSONObject(i) ?: continue
                    val id = item.optLong("id", 0L)
                    if (id == 0L) continue
                    val videoUrl = item.optString("url", "https://vimeo.com/$id")
                    val title = item.optString("title", "Vimeo Video")
                    val uploader = item.optString("user_name", "Vimeo")
                    val thumb = item.optString("thumbnail_large", item.optString("thumbnail_medium", ""))
                    val duration = item.optLong("duration", -1L)
                    val views = item.optLong("stats_number_of_plays", -1L)

                    list.add(
                        VideoItem(
                            id = videoUrl,
                            title = title,
                            uploaderName = uploader,
                            durationSeconds = duration,
                            viewCount = views,
                            thumbnailUrl = thumb,
                            providerId = PROVIDER_ID
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vimeo getHome error: ${e.message}")
        }
        return list
    }

    private fun parseVimeoConfig(jsonStr: String, videoId: String): StreamData? {
        try {
            val json = JSONObject(jsonStr)
            val videoObj = json.optJSONObject("video")
            val title = videoObj?.optString("title", "Vimeo Video") ?: "Vimeo Video"
            val uploader = videoObj?.optJSONObject("owner")?.optString("name", "Vimeo") ?: "Vimeo"
            val thumbsObj = videoObj?.optJSONObject("thumbs")
            val thumb = thumbsObj?.optString("640", thumbsObj?.optString("base", "") ?: "") ?: ""

            val requestObj = json.optJSONObject("request")
            val filesObj = requestObj?.optJSONObject("files") ?: return null

            val options = mutableListOf<PlayableStreamOption>()
            val vimeoHeaders = mapOf(
                "User-Agent" to DEFAULT_USER_AGENT,
                "Referer" to "https://vimeo.com/",
                "Origin" to "https://vimeo.com"
            )

            // 1. Adaptive HLS streams
            val hlsObj = filesObj.optJSONObject("hls")
            if (hlsObj != null) {
                val cdnsObj = hlsObj.optJSONObject("cdns")
                val defaultCdnKey = hlsObj.optString("default_cdn", "")
                var hlsUrl = ""
                if (cdnsObj != null) {
                    if (defaultCdnKey.isNotBlank() && cdnsObj.has(defaultCdnKey)) {
                        hlsUrl = cdnsObj.optJSONObject(defaultCdnKey)?.optString("url", "") ?: ""
                    }
                    if (hlsUrl.isBlank()) {
                        val keys = cdnsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val cdnItem = cdnsObj.optJSONObject(key)
                            val u = cdnItem?.optString("url", "") ?: ""
                            if (u.isNotBlank()) {
                                hlsUrl = u
                                break
                            }
                        }
                    }
                }
                if (hlsUrl.isBlank()) {
                    hlsUrl = hlsObj.optString("url", "")
                }
                if (hlsUrl.isNotBlank()) {
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "Adaptive HLS (m3u8)",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = hlsUrl,
                            providerType = ProviderType.DIRECT,
                            headers = vimeoHeaders
                        )
                    )
                }
            }

            // 2. Progressive MP4 streams
            val progressiveArray = filesObj.optJSONArray("progressive")
            if (progressiveArray != null) {
                for (i in 0 until progressiveArray.length()) {
                    val streamObj = progressiveArray.optJSONObject(i) ?: continue
                    val streamUrl = streamObj.optString("url", "")
                    val quality = streamObj.optString("quality", "720p")
                    val mime = streamObj.optString("mime", "video/mp4")
                    if (streamUrl.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "$quality MP4",
                                format = if (mime.contains("mp4")) "mp4" else "video",
                                isMuxed = true,
                                videoUrl = streamUrl,
                                providerType = ProviderType.DIRECT,
                                headers = vimeoHeaders
                            )
                        )
                    }
                }
            }

            // 3. DASH streams
            val dashObj = filesObj.optJSONObject("dash")
            if (dashObj != null) {
                val cdnsObj = dashObj.optJSONObject("cdns")
                val defaultCdnKey = dashObj.optString("default_cdn", "")
                var dashUrl = ""
                if (cdnsObj != null) {
                    if (defaultCdnKey.isNotBlank() && cdnsObj.has(defaultCdnKey)) {
                        dashUrl = cdnsObj.optJSONObject(defaultCdnKey)?.optString("url", "") ?: ""
                    }
                    if (dashUrl.isBlank()) {
                        val keys = cdnsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val cdnItem = cdnsObj.optJSONObject(key)
                            val u = cdnItem?.optString("url", "") ?: ""
                            if (u.isNotBlank()) {
                                dashUrl = u
                                break
                            }
                        }
                    }
                }
                if (dashUrl.isBlank()) {
                    dashUrl = dashObj.optString("url", "")
                }
                if (dashUrl.isNotBlank()) {
                    options.add(
                        PlayableStreamOption(
                            qualityLabel = "Adaptive DASH (mpd)",
                            format = "mpd",
                            isMuxed = true,
                            videoUrl = dashUrl,
                            providerType = ProviderType.DIRECT,
                            headers = vimeoHeaders
                        )
                    )
                }
            }

            if (options.isNotEmpty()) {
                val bestOption = options.first()
                return StreamData(
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
                    headers = vimeoHeaders
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Vimeo config JSON: ${e.message}")
        }
        return null
    }

    private fun extractJsonObjectByMarker(html: String, marker: String): JSONObject? {
        val idx = html.indexOf(marker)
        if (idx == -1) return null
        val braceIndex = html.indexOf('{', idx)
        if (braceIndex == -1) return null

        var openBraces = 0
        var inString = false
        var escape = false

        for (i in braceIndex until html.length) {
            val c = html[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (c == '\\') {
                    escape = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> openBraces++
                    '}' -> {
                        openBraces--
                        if (openBraces == 0) {
                            val jsonSub = html.substring(braceIndex, i + 1)
                            return try {
                                JSONObject(jsonSub)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val videoId = Regex("""\d+""").find(cleanInput)?.value ?: ""
        if (videoId.isBlank()) return@withContext null

        val vimeoHeaders = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to "https://vimeo.com/",
            "Origin" to "https://vimeo.com"
        )

        // Strategy 1: Direct Player Config API with embed referer
        try {
            val configUrl = "https://player.vimeo.com/video/$videoId/config"
            val req = Request.Builder()
                .url(configUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "https://player.vimeo.com/video/$videoId")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val parsedData = parseVimeoConfig(jsonStr, videoId)
                if (parsedData != null) {
                    Log.i(TAG, "Successfully extracted Vimeo stream via Player Config API for ID $videoId")
                    return@withContext parsedData
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vimeo player config endpoint failed: ${e.message}")
        }

        // Strategy 2: Player Embed Webpage HTML extraction
        try {
            val embedUrl = "https://player.vimeo.com/video/$videoId"
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "https://vimeo.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val markers = listOf("var config = ", "window.playerConfig = ", "playerConfig = ", "\"request\":")
                for (marker in markers) {
                    val jsonObj = extractJsonObjectByMarker(html, marker)
                    if (jsonObj != null) {
                        val parsedData = parseVimeoConfig(jsonObj.toString(), videoId)
                        if (parsedData != null) {
                            Log.i(TAG, "Successfully extracted Vimeo stream via Embed Page HTML for ID $videoId")
                            return@withContext parsedData
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vimeo embed webpage extraction failed: ${e.message}")
        }

        // Strategy 3: Video Webpage HTML extraction
        try {
            val pageUrl = "https://vimeo.com/$videoId"
            val req = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "https://vimeo.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val markers = listOf("window.vimeo.clip_page_config = ", "window.playerConfig = ", "var config = ", "\"request\":")
                for (marker in markers) {
                    val jsonObj = extractJsonObjectByMarker(html, marker)
                    if (jsonObj != null) {
                        val parsedData = parseVimeoConfig(jsonObj.toString(), videoId)
                        if (parsedData != null) {
                            Log.i(TAG, "Successfully extracted Vimeo stream via Main Video Page HTML for ID $videoId")
                            return@withContext parsedData
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vimeo main webpage extraction failed: ${e.message}")
        }

        // Strategy 4: Fallback to YtDlpResolver
        if (context != null) {
            try {
                Log.i(TAG, "Falling back to YtDlpResolver for Vimeo video ID: $videoId")
                val targetUrl = if (cleanInput.startsWith("http")) cleanInput else "https://vimeo.com/$videoId"
                val ytDlpRes = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytDlpRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytDlpRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = vimeoHeaders
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "YtDlpResolver Vimeo fallback failed: ${e.message}")
            }
        }

        null
    }
}

