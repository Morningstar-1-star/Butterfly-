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
import java.util.regex.Pattern

/**
 * Stripchat Provider & Live Cam Extractor.
 * Supported by yt-dlp 'Stripchat' extractor.
 */
object StripchatProvider {
    private const val TAG = "StripchatProvider"
    const val PROVIDER_ID = "stripchat"
    private const val BASE_URL = "https://stripchat.com"

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .build()
            chain.proceed(req)
        }
        .build()

    suspend fun getHome(limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        val offset = (page - 1) * limit

        try {
            // Stripchat JSON API for front models
            val apiUrl = "$BASE_URL/api/front/models?limit=$limit&offset=$offset&primaryTag=girls"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val modelsArr = root.optJSONArray("models") ?: root.optJSONArray("items")
                if (modelsArr != null) {
                    for (i in 0 until modelsArr.length()) {
                        val m = modelsArr.optJSONObject(i) ?: continue
                        val username = m.optString("username", "")
                        if (username.isBlank()) continue

                        val status = m.optString("status", "public")
                        if (status == "off") continue

                        val topic = m.optString("topic", m.optString("statusText", "$username's Live Show")).trim()
                        val previewObj = m.optJSONObject("previewUrlThumbBig") ?: m.optJSONObject("snapshotUrl")
                        val thumb = m.optString("snapshotUrl", m.optString("avatarUrl", "https://img.strpst.com/thumbs/$username.jpg"))
                        val viewers = m.optLong("viewersCount", m.optLong("usersCount", 0L))
                        val isHd = m.optBoolean("isCamAvailable", true)

                        list.add(
                            VideoItem(
                                id = "$BASE_URL/$username",
                                title = "● LIVE: ${if (topic.isNotBlank()) topic else "$username Live Cam"}",
                                uploaderName = "$username (Live${if (isHd) " • HD" else ""})",
                                uploaderUrl = "$BASE_URL/$username",
                                thumbnailUrl = thumb,
                                providerId = PROVIDER_ID,
                                durationSeconds = 0L,
                                viewCount = viewers,
                                uploadDate = "🔴 LIVE NOW",
                                description = "Live Interactive Stripchat Webcam Show for $username"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stripchat API fetch note: ${e.message}")
        }

        // Fallback: parse homepage if API format changed
        if (list.isEmpty()) {
            try {
                val req = Request.Builder()
                    .url("$BASE_URL/")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()

                val html = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!html.isNullOrBlank()) {
                    val userMatches = Regex("""(?:href=['"]/(?:[a-zA-Z0-9_-]+)['"][^>]*class=['"][^'"]*model[^'"]*['"]|data-model-name=['"]([a-zA-Z0-9_-]+)['"])""").findAll(html)
                    for (match in userMatches) {
                        val user = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: continue
                        if (user.lowercase() in listOf("login", "signup", "tags", "categories", "vr", "app", "terms")) continue
                        list.add(
                            VideoItem(
                                id = "$BASE_URL/$user",
                                title = "● LIVE: $user Show",
                                uploaderName = "$user (Stripchat Live)",
                                uploaderUrl = "$BASE_URL/$user",
                                thumbnailUrl = "https://img.strpst.com/thumbs/$user.jpg",
                                providerId = PROVIDER_ID,
                                durationSeconds = 0L,
                                viewCount = 1200L,
                                uploadDate = "🔴 LIVE NOW",
                                description = "Stripchat Live Model Show"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stripchat HTML fallback error: ${e.message}")
            }
        }

        list.distinctBy { it.id }.take(limit)
    }

    suspend fun search(query: String, limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        val all = getHome(limit = 60, page = page)
        val filtered = all.filter {
            it.title.lowercase().contains(q) || it.uploaderName.lowercase().contains(q)
        }
        if (filtered.isNotEmpty()) filtered.take(limit) else all.take(limit)
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val username = when {
            urlOrId.startsWith("http://") || urlOrId.startsWith("https://") -> urlOrId.substringAfterLast("/").substringBefore("?")
            urlOrId.startsWith("stripchat:", ignoreCase = true) -> urlOrId.substringAfter(":").trim('/')
            else -> urlOrId.trim()
        }

        val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "$BASE_URL/$username"

        // 1. Direct model API lookup for live HLS stream
        try {
            val apiUrl = "$BASE_URL/api/front/v2/models/username/$username/cam"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", targetUrl)
                .header("Origin", BASE_URL)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val streamObj = root.optJSONObject("stream")
                val hlsUrl = streamObj?.optString("url") ?: root.optString("hlsStreamUrl")

                if (!hlsUrl.isNullOrBlank()) {
                    val streamOptions = listOf(
                        PlayableStreamOption(
                            qualityLabel = "1080p 60fps (Live HLS)",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = hlsUrl,
                            providerType = ProviderType.DIRECT,
                            headers = mapOf(
                                "User-Agent" to DEFAULT_USER_AGENT,
                                "Referer" to "$BASE_URL/"
                            )
                        )
                    )
                    val best = streamOptions.first()

                    return@withContext StreamData(
                        videoId = targetUrl,
                        videoUrl = hlsUrl,
                        title = "● LIVE: $username Show",
                        channelName = username,
                        channelAvatarUrl = "https://img.strpst.com/thumbs/$username.jpg",
                        description = "Stripchat Live High Speed Broadcast",
                        thumbnailUrl = "https://img.strpst.com/thumbs/$username.jpg",
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        availableStreamOptions = streamOptions,
                        selectedStreamOption = best
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct Stripchat cam API note: ${e.message}")
        }

        // 2. yt-dlp Stripchat extractor fallback
        if (context != null) {
            try {
                val result = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext result.streamData.copy(providerId = PROVIDER_ID)
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlpResolver Stripchat extraction failed: ${e.message}")
            }
        }

        return@withContext null
    }
}
