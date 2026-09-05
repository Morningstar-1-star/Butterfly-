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
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object CamModelsProvider {
    private const val TAG = "CamModelsProvider"
    const val PROVIDER_ID = "cammodels"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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
        val gender: String
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

        try {
            val apiUrl = "https://tools.bongacams.com/promo.php?c=777&type=api&api_type=json"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val arr = JSONArray(jsonStr)
                val allModels = mutableListOf<LiveCamInfo>()

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val username = obj.optString("username", "")
                    if (username.isBlank()) continue

                    val displayName = obj.optString("display_name", username)
                    val profileImages = obj.optJSONObject("profile_images")
                    val rawThumb = profileImages?.optString("thumbnail_image_big_live",
                        profileImages.optString("thumbnail_image_medium_live",
                            profileImages.optString("thumbnail_image_big",
                                profileImages.optString("thumbnail_image_medium",
                                    profileImages.optString("profile_image", "")
                                )
                            )
                        )
                    ) ?: ""
                    val thumb = fixUrl(rawThumb)
                    val streamFeedUrl = obj.optString("stream_feed_url", "")
                    val topic = obj.optString("chat_topic", "$displayName's Live Cam").trim()
                    val viewers = obj.optLong("members_count", 920L)
                    val g = obj.optString("gender", "Female")

                    val camInfo = LiveCamInfo(
                        username = username,
                        displayName = displayName,
                        streamUrl = if (streamFeedUrl.isNotBlank() && streamFeedUrl.startsWith("http")) streamFeedUrl else null,
                        thumbnailUrl = thumb,
                        topic = topic.ifBlank { "$displayName's Live Cam Stream" },
                        viewers = viewers,
                        gender = g
                    )

                    // Cache model info
                    liveCamCache[username.lowercase()] = camInfo
                    liveCamCache[displayName.lowercase()] = camInfo
                    allModels.add(camInfo)
                }

                // Filter by gender if requested
                val filtered = if (!gender.isNullOrBlank()) {
                    val gLower = gender.lowercase()
                    allModels.filter {
                        when {
                            gLower.contains("male") && !gLower.contains("fe") -> it.gender.contains("male", ignoreCase = true) && !it.gender.contains("female", ignoreCase = true)
                            gLower.contains("couple") -> it.gender.contains("couple", ignoreCase = true)
                            gLower.contains("trans") -> it.gender.contains("trans", ignoreCase = true)
                            else -> it.gender.contains("female", ignoreCase = true)
                        }
                    }
                } else {
                    allModels
                }

                val paged = filtered.drop(offset).take(limit).ifEmpty { filtered.take(limit) }
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
            }
        } catch (e: Exception) {
            Log.w(TAG, "CamModels Live API fetch error: ${e.message}")
        }

        list
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val q = query.replace(Regex("(?i)^(cammodels:)?"), "").trim()
        val allModels = getHome(limit * 2, page)
        val filtered = allModels.filter {
            q.isBlank() || it.title.contains(q, ignoreCase = true) || it.uploaderName.contains(q, ignoreCase = true)
        }
        if (filtered.isNotEmpty()) filtered.take(limit) else allModels.take(limit)
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

        // 2. If not in cache, refresh live catalog once
        if (cached == null || cached.streamUrl.isNullOrBlank()) {
            try {
                val apiUrl = "https://tools.bongacams.com/promo.php?c=777&type=api&api_type=json"
                val req = Request.Builder().url(apiUrl).build()
                val jsonStr = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
                if (!jsonStr.isNullOrBlank()) {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val uName = obj.optString("username", "")
                        val dName = obj.optString("display_name", uName)
                        val profileImages = obj.optJSONObject("profile_images")
                        val rawThumb = profileImages?.optString("thumbnail_image_big_live",
                            profileImages.optString("thumbnail_image_medium_live",
                                profileImages.optString("thumbnail_image_big", "")
                            )
                        ) ?: ""
                        val streamFeedUrl = obj.optString("stream_feed_url", "")
                        val topic = obj.optString("chat_topic", "$dName's Live Cam")
                        val viewers = obj.optLong("members_count", 950L)
                        val g = obj.optString("gender", "Female")

                        val info = LiveCamInfo(
                            username = uName,
                            displayName = dName,
                            streamUrl = if (streamFeedUrl.isNotBlank() && streamFeedUrl.startsWith("http")) streamFeedUrl else null,
                            thumbnailUrl = fixUrl(rawThumb),
                            topic = topic,
                            viewers = viewers,
                            gender = g
                        )
                        liveCamCache[uName.lowercase()] = info
                        liveCamCache[dName.lowercase()] = info

                        if (uName.equals(username, ignoreCase = true) || dName.equals(username, ignoreCase = true)) {
                            cached = info
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Refresh CamModels cache error: ${e.message}")
            }
        }

        if (cached != null && !cached.streamUrl.isNullOrBlank()) {
            val opt = PlayableStreamOption(
                qualityLabel = "CamModels Live HD (Direct HLS)",
                format = "m3u8",
                isMuxed = true,
                videoUrl = cached.streamUrl!!,
                providerType = ProviderType.DIRECT,
                headers = headers
            )
            return@withContext StreamData(
                videoId = "https://cammodels.com/$username",
                videoUrl = cached.streamUrl!!,
                title = "● LIVE: ${cached.topic}",
                channelName = "${cached.displayName} (CamModels)",
                thumbnailUrl = cached.thumbnailUrl,
                availableStreamOptions = listOf(opt),
                selectedStreamOption = opt,
                hlsUrl = cached.streamUrl,
                providerId = PROVIDER_ID,
                providerType = ProviderType.DIRECT,
                headers = headers
            )
        }

        null
    }
}

