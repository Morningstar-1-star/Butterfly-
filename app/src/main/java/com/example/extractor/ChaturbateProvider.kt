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
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object ChaturbateProvider {
    private const val TAG = "ChaturbateProvider"
    const val PROVIDER_ID = "chaturbate"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://chaturbate.com/")
                .header("Origin", "https://chaturbate.com")
                .build()
            chain.proceed(req)
        }
        .build()

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "https://chaturbate.com/",
        "Origin" to "https://chaturbate.com"
    )

    suspend fun getHome(limit: Int = 24, page: Int = 1, gender: String? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()

        val gParam = when (gender?.lowercase()) {
            "female", "f" -> "f"
            "male", "m" -> "m"
            "couple", "c" -> "c"
            "trans", "t", "transgender" -> "t"
            else -> ""
        }

        try {
            val offset = (page - 1) * limit
            val apiUrl = "https://chaturbate.com/api/ts/roomlist/room-list/?offset=$offset&limit=$limit&gender=$gParam"
            val req = Request.Builder()
                .url(apiUrl)
                .headers(okhttp3.Headers.Builder().apply { headers.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val roomsArr = root.optJSONArray("rooms")
                if (roomsArr != null) {
                    for (i in 0 until roomsArr.length()) {
                        val room = roomsArr.optJSONObject(i) ?: continue
                        val username = room.optString("username", "")
                        if (username.isBlank()) continue

                        val roomTitle = room.optString("room_subject", room.optString("subject", room.optString("room_title", "$username's Live Cam"))).trim()
                        val numUsers = room.optLong("num_users", room.optLong("viewers", 0L))
                        val thumb = room.optString("img", room.optString("image_url", "https://thumb.live.mmcdn.com/riw/$username.jpg"))
                        val broadcasterGender = room.optString("broadcaster_gender", room.optString("gender", "f"))
                        val isHd = room.optBoolean("is_hd", true)

                        val genderLabel = when (broadcasterGender.lowercase()) {
                            "f" -> "Female"
                            "m" -> "Male"
                            "c" -> "Couple"
                            "t", "s" -> "Trans"
                            else -> "Live"
                        }

                        list.add(
                            VideoItem(
                                id = "https://chaturbate.com/$username",
                                title = "● LIVE: $roomTitle",
                                uploaderName = "$username ($genderLabel${if (isHd) " • HD" else ""})",
                                uploaderUrl = "https://chaturbate.com/$username",
                                thumbnailUrl = thumb,
                                providerId = PROVIDER_ID,
                                durationSeconds = 0L,
                                viewCount = numUsers,
                                uploadDate = "🔴 LIVE NOW",
                                description = "Live cam room of $username on Chaturbate. $numUsers viewers watching."
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Chaturbate API getHome failed: ${e.message}")
        }

        if (list.isEmpty()) {
            list.addAll(getFallbackRooms(limit, page))
        }

        list
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val q = query.replace(Regex("(?i)^(chaturbate:)?"), "").trim()
        val gParam = when (q.lowercase()) {
            "female", "girls", "women" -> "f"
            "male", "guys", "men" -> "m"
            "couple", "couples" -> "c"
            "trans", "transgender" -> "t"
            else -> null
        }

        if (gParam != null) {
            return@withContext getHome(limit, page, gParam)
        }

        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val offset = (page - 1) * limit
            val apiUrl = "https://chaturbate.com/api/ts/roomlist/room-list/?offset=$offset&limit=$limit&keywords=$encoded"
            val req = Request.Builder()
                .url(apiUrl)
                .headers(okhttp3.Headers.Builder().apply { headers.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val list = mutableListOf<VideoItem>()
                val root = JSONObject(jsonStr)
                val roomsArr = root.optJSONArray("rooms")
                if (roomsArr != null) {
                    for (i in 0 until roomsArr.length()) {
                        val room = roomsArr.optJSONObject(i) ?: continue
                        val username = room.optString("username", "")
                        if (username.isBlank()) continue

                        val roomTitle = room.optString("room_title", "$username's Live Room")
                        val numUsers = room.optLong("num_users", 0L)
                        val thumb = room.optString("image_url", "https://roomimg.stream.highwebmedia.com/ri/$username.jpg")

                        list.add(
                            VideoItem(
                                id = "https://chaturbate.com/$username",
                                title = "● LIVE: $roomTitle",
                                uploaderName = "$username (Chaturbate)",
                                uploaderUrl = "https://chaturbate.com/$username",
                                thumbnailUrl = thumb,
                                providerId = PROVIDER_ID,
                                durationSeconds = 0L,
                                viewCount = numUsers,
                                uploadDate = "🔴 LIVE NOW",
                                description = "Live cam room of $username on Chaturbate."
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Chaturbate search error: ${e.message}")
        }

        val allRooms = getHome(limit * 2, 1)
        allRooms.filter {
            it.title.contains(q, ignoreCase = true) || it.uploaderName.contains(q, ignoreCase = true)
        }.take(limit).ifEmpty { allRooms.take(limit) }
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val username = when {
            cleanInput.contains("chaturbate.com/") -> cleanInput.substringAfter("chaturbate.com/").substringBefore("/").substringBefore("?")
            cleanInput.startsWith("chaturbate:") -> cleanInput.substringAfter("chaturbate:")
            cleanInput.startsWith("http") -> cleanInput.substringAfterLast("/").substringBefore("?")
            else -> cleanInput
        }

        if (username.isBlank()) return@withContext null

        try {
            // 0. Try direct AJAX HLS endpoint first (instant and high-speed LLHLS)
            val ajaxUrl = "https://chaturbate.com/get_edge_hls_url_ajax/"
            val formBody = okhttp3.FormBody.Builder()
                .add("room_slug", username)
                .add("bandwidth", "high")
                .build()
            val ajaxReq = Request.Builder()
                .url(ajaxUrl)
                .post(formBody)
                .headers(okhttp3.Headers.Builder().apply {
                    headers.forEach { (k, v) -> add(k, v) }
                    add("X-Requested-With", "XMLHttpRequest")
                }.build())
                .build()

            val ajaxJson = httpClient.newCall(ajaxReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
            if (!ajaxJson.isNullOrBlank()) {
                val jsonObj = JSONObject(ajaxJson)
                val streamUrl = jsonObj.optString("url", "")
                if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                    val option = PlayableStreamOption(
                        qualityLabel = "Chaturbate Live HD (LLHLS)",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = streamUrl,
                        providerType = ProviderType.DIRECT,
                        headers = headers
                    )
                    return@withContext StreamData(
                        videoId = "https://chaturbate.com/$username",
                        videoUrl = streamUrl,
                        title = "● LIVE: $username on Chaturbate",
                        channelName = "$username (Chaturbate)",
                        thumbnailUrl = "https://thumb.live.mmcdn.com/riw/$username.jpg",
                        availableStreamOptions = listOf(option),
                        selectedStreamOption = option,
                        hlsUrl = streamUrl,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        headers = headers
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Chaturbate AJAX stream error: ${e.message}")
        }

        try {
            // 1. Fetch room page HTML and extract initialRoomDossier / LLHLS stream
            val roomUrl = "https://chaturbate.com/$username/"
            val req = Request.Builder()
                .url(roomUrl)
                .headers(okhttp3.Headers.Builder().apply { headers.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val m3u8Matcher = Pattern.compile("""(https?://[^"'\s\\]+?\.m3u8(?:\?[^"'\s\\]*)?)""").matcher(html)
                var resolvedStreamUrl: String? = null
                while (m3u8Matcher.find()) {
                    val candidate = m3u8Matcher.group(1).replace("\\u0026", "&").replace("\\/", "/")
                    if (!candidate.contains("preview") || resolvedStreamUrl == null) {
                        resolvedStreamUrl = candidate
                        if (candidate.contains("playlist.m3u8") || candidate.contains("llhls.m3u8") || candidate.contains("master.m3u8")) {
                            break
                        }
                    }
                }

                if (!resolvedStreamUrl.isNullOrBlank()) {
                    val option = PlayableStreamOption(
                        qualityLabel = "Chaturbate Live Stream (HLS)",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = resolvedStreamUrl,
                        providerType = ProviderType.DIRECT,
                        headers = headers
                    )
                    return@withContext StreamData(
                        videoId = "https://chaturbate.com/$username",
                        videoUrl = resolvedStreamUrl,
                        title = "● LIVE: $username on Chaturbate",
                        channelName = "$username (Chaturbate)",
                        thumbnailUrl = "https://roomimg.stream.highwebmedia.com/ri/$username.jpg",
                        availableStreamOptions = listOf(option),
                        selectedStreamOption = option,
                        hlsUrl = resolvedStreamUrl,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        headers = headers
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Chaturbate HTML stream resolution error: ${e.message}")
        }

        // 2. Try yt-dlp resolver if available
        if (context != null) {
            try {
                val ytRes = YtDlpResolver.extractStreamInfo(context, "https://chaturbate.com/$username")
                if (ytRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = headers
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp Chaturbate stream extract error: ${e.message}")
            }
        }

        // 3. Fallback direct stream endpoint
        val fallbackHls = "https://edge-hls.doppiocdn.com/hls/live/mystream/$username/master.m3u8"
        val option = PlayableStreamOption(
            qualityLabel = "Chaturbate Live Adaptive (Auto)",
            format = "m3u8",
            isMuxed = true,
            videoUrl = fallbackHls,
            providerType = ProviderType.DIRECT,
            headers = headers
        )

        StreamData(
            videoId = "https://chaturbate.com/$username",
            videoUrl = fallbackHls,
            title = "● LIVE: $username on Chaturbate",
            channelName = "$username (Chaturbate)",
            thumbnailUrl = "https://roomimg.stream.highwebmedia.com/ri/$username.jpg",
            availableStreamOptions = listOf(option),
            selectedStreamOption = option,
            hlsUrl = fallbackHls,
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT,
            headers = headers
        )
    }

    private fun getFallbackRooms(limit: Int, page: Int): List<VideoItem> {
        return emptyList()
    }
}
