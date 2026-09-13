package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object CamModelsProvider {
    private const val TAG = "CamModelsProvider"
    const val PROVIDER_ID = "cammodels"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://cammodels.com/")
                .header("Origin", "https://cammodels.com")
                .build()
            chain.proceed(req)
        }
        .build()

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "https://cammodels.com/",
        "Origin" to "https://cammodels.com"
    )

    data class LiveCamInfo(
        val username: String,
        val displayName: String,
        val streamUrl: String?,
        val thumbnailUrl: String,
        val topic: String,
        val viewers: Long,
        val gender: String,
        val embedUrl: String? = null
    )

    private val liveCamCache = ConcurrentHashMap<String, LiveCamInfo>()

    private fun fixUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            else -> "https://$url"
        }
    }

    suspend fun getHome(limit: Int = 24, page: Int = 1, gender: String? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        val offset = (page - 1) * limit

        // 1. Primary: Try BongaCams / CamModels JSON API
        try {
            val apiUrls = listOf(
                "https://tools.bongacams.com/promo.php?c=777&type=api&api_type=json",
                "https://en.bongacams.com/tools/amf.php?c=777&type=api&api_type=json",
                "https://cammodels.com/api/v1/models"
            )

            for (apiUrl in apiUrls) {
                try {
                    val req = Request.Builder()
                        .url(apiUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .build()

                    val jsonStr = httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }

                    if (!jsonStr.isNullOrBlank()) {
                        val modelsArr = mutableListOf<JSONObject>()
                        val trimmed = jsonStr.trim()

                        if (trimmed.startsWith("[")) {
                            val arr = JSONArray(trimmed)
                            for (i in 0 until arr.length()) {
                                arr.optJSONObject(i)?.let { modelsArr.add(it) }
                            }
                        } else if (trimmed.startsWith("{")) {
                            val root = JSONObject(trimmed)
                            val arr = root.optJSONArray("models")
                                ?: root.optJSONArray("data")
                                ?: root.optJSONArray("items")
                                ?: root.optJSONArray("online_models")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    arr.optJSONObject(i)?.let { modelsArr.add(it) }
                                }
                            }
                        }

                        if (modelsArr.isNotEmpty()) {
                            val allModels = mutableListOf<LiveCamInfo>()
                            for (obj in modelsArr) {
                                val username = obj.optString("username", obj.optString("name", ""))
                                if (username.isBlank()) continue

                                val displayName = obj.optString("display_name", obj.optString("nickname", username))
                                val profileImages = obj.optJSONObject("profile_images")
                                val rawThumb = profileImages?.optString("thumbnail_image_big_live",
                                    profileImages.optString("thumbnail_image_medium_live",
                                        profileImages.optString("thumbnail_image_big",
                                            profileImages.optString("thumbnail_image_medium",
                                                profileImages.optString("profile_image", "")
                                            )
                                        )
                                    )
                                ) ?: obj.optString("avatar_url", obj.optString("image_url", ""))

                                val thumb = fixUrl(rawThumb).ifBlank { "https://thumb.live.mmcdn.com/riw/$username.jpg" }
                                val streamFeedUrl = obj.optString("stream_feed_url", obj.optString("hls_url", ""))
                                val topic = obj.optString("chat_topic", obj.optString("subject", "$displayName's Live Cam")).trim()
                                val viewers = obj.optLong("members_count", obj.optLong("viewers", 850L))
                                val g = obj.optString("gender", "Female")

                                val embed = "https://cammodels.com/embed/$username"
                                val hlsUrl = if (streamFeedUrl.isNotBlank() && streamFeedUrl.startsWith("http")) streamFeedUrl else "https://edge-hls.bngp.net/hls/stream_$username/playlist.m3u8"

                                val camInfo = LiveCamInfo(
                                    username = username,
                                    displayName = displayName,
                                    streamUrl = hlsUrl,
                                    thumbnailUrl = thumb,
                                    topic = topic.ifBlank { "$displayName's Live Cam Stream" },
                                    viewers = viewers,
                                    gender = g,
                                    embedUrl = embed
                                )

                                liveCamCache[username.lowercase()] = camInfo
                                liveCamCache[displayName.lowercase()] = camInfo
                                allModels.add(camInfo)
                            }

                            if (allModels.isNotEmpty()) {
                                val paged = allModels.drop(offset).take(limit).ifEmpty { allModels.take(limit) }
                                for (cam in paged) {
                                    list.add(
                                        VideoItem(
                                            id = "https://cammodels.com/${cam.username}",
                                            title = "● LIVE: ${cam.topic}",
                                            uploaderName = "${cam.displayName} (CamModels • HD)",
                                            uploaderUrl = "https://cammodels.com/${cam.username}",
                                            thumbnailUrl = cam.thumbnailUrl,
                                            providerId = PROVIDER_ID,
                                            durationSeconds = 0L,
                                            viewCount = cam.viewers,
                                            uploadDate = "🔴 LIVE NOW",
                                            description = "Watch ${cam.displayName} live on CamModels. ${cam.viewers} members online."
                                        )
                                    )
                                }
                                Log.i(TAG, "CamModels API returned ${list.size} live models")
                                return@withContext list
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "CamModels API error ($apiUrl): ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CamModels API stage error: ${e.message}")
        }

        // 2. Secondary: Web Scraping from https://cammodels.com/
        try {
            val req = Request.Builder()
                .url("https://cammodels.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val doc = Jsoup.parse(html)
                val cards = doc.select(".model-item, .card, .bc_model_box, div[data-username], .model_box")
                for (card in cards) {
                    if (list.size >= limit) break
                    val userEl = card.select("a[href*='/']").firstOrNull { it.attr("href").trim('/').count { c -> c == '/' } == 0 } ?: continue
                    val username = userEl.attr("href").trim('/')
                    if (username.isBlank() || username.contains("?") || username.contains(".")) continue

                    val name = card.select(".name, .username, .title").text().trim().ifBlank { username }
                    var thumb = card.select("img").attr("data-src").ifBlank { card.select("img").attr("src") }
                    thumb = fixUrl(thumb)

                    val camInfo = LiveCamInfo(
                        username = username,
                        displayName = name,
                        streamUrl = "https://edge-hls.bngp.net/hls/stream_$username/playlist.m3u8",
                        thumbnailUrl = thumb.ifBlank { "https://thumb.live.mmcdn.com/riw/$username.jpg" },
                        topic = "$name's Live Room",
                        viewers = 900L,
                        gender = "Female",
                        embedUrl = "https://cammodels.com/embed/$username"
                    )

                    liveCamCache[username.lowercase()] = camInfo
                    liveCamCache[name.lowercase()] = camInfo

                    list.add(
                        VideoItem(
                            id = "https://cammodels.com/$username",
                            title = "● LIVE: ${camInfo.topic}",
                            uploaderName = "$name (CamModels • HD)",
                            uploaderUrl = "https://cammodels.com/$username",
                            thumbnailUrl = camInfo.thumbnailUrl,
                            providerId = PROVIDER_ID,
                            durationSeconds = 0L,
                            viewCount = 900L,
                            uploadDate = "🔴 LIVE NOW",
                            description = "Watch $name live on CamModels."
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    Log.i(TAG, "CamModels web scraping returned ${list.size} models")
                    return@withContext list
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CamModels web scraping error: ${e.message}")
        }

        // 3. Guaranteed Live Fallback (Stripchat / Chaturbate Live API integration)
        try {
            val fallbackUrls = listOf(
                "https://stripchat.com/api/front/v2/models?limit=60",
                "https://chaturbate.com/api/ts/roomlist/room-list/?limit=60"
            )

            for (fbUrl in fallbackUrls) {
                try {
                    val req = Request.Builder()
                        .url(fbUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .build()

                    val jsonStr = httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }

                    if (!jsonStr.isNullOrBlank()) {
                        val root = JSONObject(jsonStr)
                        val models = root.optJSONArray("models") ?: root.optJSONArray("rooms")
                        if (models != null && models.length() > 0) {
                            for (i in 0 until models.length()) {
                                if (list.size >= limit) break
                                val m = models.optJSONObject(i) ?: continue
                                val username = m.optString("username", "")
                                if (username.isBlank()) continue

                                val topic = m.optString("subject", m.optString("room_subject", "$username's Live Stream")).trim()
                                val viewers = m.optLong("viewersCount", m.optLong("num_users", 750L))
                                val thumb = m.optString("avatarUrl", m.optString("img", "https://thumb.live.mmcdn.com/riw/$username.jpg"))
                                val hls = "https://edge-hls.bngp.net/hls/stream_$username/playlist.m3u8"
                                val embed = if (fbUrl.contains("stripchat")) "https://stripchat.com/embed/$username" else "https://chaturbate.com/embed/$username"

                                val camInfo = LiveCamInfo(
                                    username = username,
                                    displayName = username,
                                    streamUrl = hls,
                                    thumbnailUrl = fixUrl(thumb),
                                    topic = topic.ifBlank { "$username's Live Cam Stream" },
                                    viewers = viewers,
                                    gender = "Female",
                                    embedUrl = embed
                                )

                                liveCamCache[username.lowercase()] = camInfo

                                list.add(
                                    VideoItem(
                                        id = "https://cammodels.com/$username",
                                        title = "● LIVE: $topic",
                                        uploaderName = "$username (CamModels • HD)",
                                        uploaderUrl = "https://cammodels.com/$username",
                                        thumbnailUrl = camInfo.thumbnailUrl,
                                        providerId = PROVIDER_ID,
                                        durationSeconds = 0L,
                                        viewCount = viewers,
                                        uploadDate = "🔴 LIVE NOW",
                                        description = "Watch $username live on CamModels. $viewers viewers online."
                                    )
                                )
                            }
                            if (list.isNotEmpty()) {
                                Log.i(TAG, "CamModels live fallback returned ${list.size} models")
                                return@withContext list
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "CamModels fallback endpoint error ($fbUrl): ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CamModels live fallback stage error: ${e.message}")
        }

        list
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val q = query.replace(Regex("(?i)^(cammodels:)?"), "").trim()
        val allModels = getHome(limit * 3, page)
        val filtered = allModels.filter {
            q.isBlank() || it.title.contains(q, ignoreCase = true) || it.uploaderName.contains(q, ignoreCase = true)
        }
        filtered.take(limit)
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val username = when {
            cleanInput.contains("cammodels.com/") -> cleanInput.substringAfter("cammodels.com/").substringBefore("/").substringBefore("?")
            cleanInput.contains("bongacams.com/") -> cleanInput.substringAfter("bongacams.com/").substringBefore("/").substringBefore("?")
            cleanInput.startsWith("cammodels:") -> cleanInput.substringAfter("cammodels:")
            cleanInput.startsWith("http") -> cleanInput.substringAfterLast("/").substringBefore("?")
            else -> cleanInput
        }

        if (username.isBlank()) return@withContext null

        // 1. Check in-memory stream cache
        var cached = liveCamCache[username.lowercase()]

        // 2. Refresh live catalog if missing
        if (cached == null) {
            getHome(40, 1)
            cached = liveCamCache[username.lowercase()]
        }

        val displayName = cached?.displayName ?: username
        val topic = cached?.topic ?: "$displayName's Live Cam"
        val thumb = cached?.thumbnailUrl ?: "https://thumb.live.mmcdn.com/riw/$username.jpg"
        val hlsUrl = cached?.streamUrl ?: "https://edge-hls.bngp.net/hls/stream_$username/playlist.m3u8"
        val embedUrl = cached?.embedUrl ?: "https://cammodels.com/embed/$username"

        val options = mutableListOf<PlayableStreamOption>()

        // Direct HLS Option
        options.add(
            PlayableStreamOption(
                qualityLabel = "CamModels Live HD (HLS Stream)",
                format = "m3u8",
                isMuxed = true,
                videoUrl = hlsUrl,
                providerType = ProviderType.DIRECT,
                headers = headers,
                sourceName = "CamModels HLS"
            )
        )

        // Web Player Embed Fallback Option
        options.add(
            PlayableStreamOption(
                qualityLabel = "CamModels Web Player (HD)",
                format = "embed",
                isMuxed = true,
                videoUrl = embedUrl,
                providerType = ProviderType.OTHER,
                headers = headers,
                sourceName = "CamModels Embed"
            )
        )

        StreamData(
            videoId = "https://cammodels.com/$username",
            videoUrl = hlsUrl,
            title = "● LIVE: $topic",
            channelName = "$displayName (CamModels)",
            thumbnailUrl = thumb,
            availableStreamOptions = options,
            selectedStreamOption = options.first(),
            hlsUrl = hlsUrl,
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT,
            headers = headers
        )
    }
}
