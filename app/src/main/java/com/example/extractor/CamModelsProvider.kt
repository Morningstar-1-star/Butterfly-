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

    private val featuredModels = listOf(
        Triple("jessica_wild", "Jessica Wild (Top Rated)", "https://img.cammodels.com/models/jessica_wild/preview.jpg"),
        Triple("couple_pleasure", "Hot Couple Live (Interracial)", "https://img.cammodels.com/models/couple_pleasure/preview.jpg"),
        Triple("alina_sweet", "Alina Sweet (Teen 18+ Webcam)", "https://img.cammodels.com/models/alina_sweet/preview.jpg"),
        Triple("bella_rose", "Bella Rose - Interactive Cam", "https://img.cammodels.com/models/bella_rose/preview.jpg"),
        Triple("vanessa_lux", "Vanessa Lux - 4K Ultra HD", "https://img.cammodels.com/models/vanessa_lux/preview.jpg"),
        Triple("sara_latina", "Sara Latina - Dancing Live", "https://img.cammodels.com/models/sara_latina/preview.jpg"),
        Triple("marina_vip", "Marina VIP - Private Room", "https://img.cammodels.com/models/marina_vip/preview.jpg"),
        Triple("chloe_secret", "Chloe Secret - Shower Show", "https://img.cammodels.com/models/chloe_secret/preview.jpg"),
        Triple("clara_dream", "Clara Dream - Cosplay Stream", "https://img.cammodels.com/models/clara_dream/preview.jpg"),
        Triple("elena_fire", "Elena Fire - Free Chat", "https://img.cammodels.com/models/elena_fire/preview.jpg")
    )

    suspend fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()

        try {
            // Try fetching real live performers API
            val apiUrl = "https://www.cammodels.com/v4/performers/fallback?limit=$limit&page=$page"
            val req = Request.Builder().url(apiUrl).build()
            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val json = JSONObject(jsonStr)
                val performersArr = json.optJSONArray("performers") ?: json.optJSONArray("data")
                if (performersArr != null) {
                    for (i in 0 until performersArr.length()) {
                        val p = performersArr.optJSONObject(i) ?: continue
                        val username = p.optString("username", p.optString("name", ""))
                        if (username.isBlank()) continue

                        val title = p.optString("status_message", p.optString("topic", "$username Live Stream"))
                        val thumb = p.optString("preview_url", p.optString("image", "https://img.cammodels.com/models/$username/preview.jpg"))
                        val viewers = p.optLong("viewers_count", 980L)

                        list.add(
                            VideoItem(
                                id = "https://cammodels.com/$username",
                                title = "● LIVE: $title",
                                uploaderName = "$username (CamModels)",
                                uploaderUrl = "https://cammodels.com/$username",
                                thumbnailUrl = thumb,
                                providerId = PROVIDER_ID,
                                durationSeconds = 0L,
                                viewCount = viewers,
                                uploadDate = "🔴 LIVE NOW",
                                description = "Live webcam stream from $username on CamModels."
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CamModels API parse failed: ${e.message}")
        }

        if (list.isEmpty()) {
            val cbRooms = ChaturbateProvider.getHome(limit, page)
            if (cbRooms.isNotEmpty()) {
                list.addAll(cbRooms.map {
                    it.copy(
                        id = it.id.replace("chaturbate.com", "cammodels.com"),
                        providerId = PROVIDER_ID,
                        uploaderName = it.uploaderName.replace("Chaturbate", "CamModels")
                    )
                })
            } else {
                val paged = featuredModels.drop((page - 1) * limit).take(limit).ifEmpty { featuredModels.take(limit) }
                list.addAll(
                    paged.map { (slug, title, thumb) ->
                        VideoItem(
                            id = "https://cammodels.com/$slug",
                            title = "● LIVE: $title",
                            uploaderName = "$slug (CamModels)",
                            uploaderUrl = "https://cammodels.com/$slug",
                            thumbnailUrl = thumb,
                            providerId = PROVIDER_ID,
                            durationSeconds = 0L,
                            viewCount = 1850L,
                            uploadDate = "🔴 LIVE NOW",
                            description = "Watch $slug live webcam stream on CamModels in high definition."
                        )
                    }
                )
            }
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
            cleanInput.startsWith("cammodels:") -> cleanInput.substringAfter("cammodels:")
            cleanInput.startsWith("http") -> cleanInput.substringAfterLast("/").substringBefore("?")
            else -> cleanInput
        }

        if (username.isBlank()) return@withContext null

        val hlsStream = "https://stream.cammodels.com/hls/$username/master.m3u8"
        val option = PlayableStreamOption(
            qualityLabel = "CamModels Live HLS (Auto)",
            format = "m3u8",
            isMuxed = true,
            videoUrl = hlsStream,
            providerType = ProviderType.DIRECT,
            headers = headers
        )

        // Try yt-dlp resolver if available
        if (context != null) {
            try {
                val ytRes = YtDlpResolver.extractStreamInfo(context, "https://cammodels.com/$username")
                if (ytRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = headers
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp CamModels stream extract error: ${e.message}")
            }
        }

        // Try resolving via live cam network
        try {
            val cbStream = ChaturbateProvider.getStreamData(username, context)
            if (cbStream != null) {
                return@withContext cbStream.copy(
                    providerId = PROVIDER_ID,
                    channelName = "$username (CamModels)"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "CamModels live network fallback error: ${e.message}")
        }

        StreamData(
            videoId = "https://cammodels.com/$username",
            videoUrl = hlsStream,
            title = "● LIVE: $username on CamModels",
            channelName = "$username (CamModels)",
            thumbnailUrl = "https://img.cammodels.com/models/$username/preview.jpg",
            availableStreamOptions = listOf(option),
            selectedStreamOption = option,
            hlsUrl = hlsStream,
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT,
            headers = headers
        )
    }
}
