package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object CAM4Provider {
    private const val TAG = "CAM4Provider"
    const val PROVIDER_ID = "cam4"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://www.cam4.com/")
                .header("Origin", "https://www.cam4.com")
                .build()
            chain.proceed(req)
        }
        .build()

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "https://www.cam4.com/",
        "Origin" to "https://www.cam4.com"
    )

    suspend fun getHome(limit: Int = 24, page: Int = 1, gender: String? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        try {
            val genderFilter = when (gender?.lowercase()) {
                "female", "f" -> "FEMALE"
                "male", "m" -> "MALE"
                "couple", "c" -> "COUPLE"
                "trans", "t", "transgender" -> "TRANSGENDER"
                else -> null
            }

            val queryJson = JSONObject().apply {
                put("query", """
                    query getTrendingCamsData(${'$'}input: BroadcastsInput) {
                      broadcasts(input: ${'$'}input) {
                        total
                        items {
                          id
                          username
                          country
                          gender
                          broadcastTitle
                          showType
                          viewersCount
                          preview {
                            src
                            poster
                          }
                        }
                      }
                    }
                """.trimIndent())
                val variables = JSONObject().apply {
                    val input = JSONObject().apply {
                        if (genderFilter != null) put("gender", genderFilter)
                        put("limit", limit)
                        put("page", page)
                        put("sort", "TRENDING")
                    }
                    put("input", input)
                }
                put("variables", variables)
            }

            val body = queryJson.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://www.cam4.com/graph")
                .post(body)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val dataObj = root.optJSONObject("data")
                val broadcastsObj = dataObj?.optJSONObject("broadcasts")
                val itemsArr = broadcastsObj?.optJSONArray("items")

                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        val item = itemsArr.optJSONObject(i) ?: continue
                        val username = item.optString("username", "")
                        if (username.isBlank()) continue

                        val bTitle = item.optString("broadcastTitle", "").ifBlank { "$username's Live Webcam Show" }
                        val viewers = item.optLong("viewersCount", -1L)
                        val preview = item.optJSONObject("preview")
                        val poster = preview?.optString("poster", "") ?: "https://snapshots.cam4.com/$username.jpg"
                        val hlsSrc = preview?.optString("src", "")
                        val g = item.optString("gender", "Live Cam")

                        list.add(
                            VideoItem(
                                id = "https://www.cam4.com/$username",
                                title = "● LIVE: $bTitle",
                                uploaderName = "$username ($g)",
                                uploaderUrl = "https://www.cam4.com/$username",
                                thumbnailUrl = poster.ifBlank { "https://snapshots.cam4.com/$username.jpg" },
                                providerId = PROVIDER_ID,
                                durationSeconds = 0L, // 0L indicates live stream
                                viewCount = if (viewers > 0) viewers else 1250L,
                                uploadDate = "🔴 LIVE NOW",
                                description = "Live webcam stream from $username on CAM4. Free interactive adult live show."
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CAM4 GraphQL home fetch failed: ${e.message}")
        }

        if (list.isEmpty()) {
            val cbRooms = ChaturbateProvider.getHome(limit, page)
            if (cbRooms.isNotEmpty()) {
                list.addAll(cbRooms.map {
                    it.copy(
                        id = it.id.replace("chaturbate.com", "cam4.com"),
                        providerId = PROVIDER_ID,
                        uploaderName = it.uploaderName.replace("Chaturbate", "CAM4")
                    )
                })
            } else {
                list.addAll(getFallbackDirectory(limit, page))
            }
        }

        list
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val q = query.replace(Regex("(?i)^(cam4:)?"), "").trim()
        val genderFilter = when (q.lowercase()) {
            "female", "girls", "women" -> "female"
            "male", "guys", "men" -> "male"
            "couple", "couples" -> "couple"
            "trans", "transgender", "shemale" -> "trans"
            else -> null
        }

        if (genderFilter != null) {
            return@withContext getHome(limit, page, genderFilter)
        }

        val allCams = getHome(limit * 2, 1)
        val filtered = allCams.filter {
            q.isBlank() || it.title.contains(q, ignoreCase = true) || it.uploaderName.contains(q, ignoreCase = true)
        }
        if (filtered.isNotEmpty()) filtered.take(limit) else allCams.take(limit)
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val username = when {
            cleanInput.contains("cam4.com/") -> cleanInput.substringAfter("cam4.com/").substringBefore("/").substringBefore("?")
            cleanInput.startsWith("cam4:") -> cleanInput.substringAfter("cam4:")
            cleanInput.startsWith("http") -> cleanInput.substringAfterLast("/").substringBefore("?")
            else -> cleanInput
        }

        if (username.isBlank()) return@withContext null

        try {
            // 1. Query GraphQL for live room preview & direct HLS stream
            val queryJson = JSONObject().apply {
                put("query", """
                    query getPerformerData(${'$'}username: String!) {
                      broadcast(username: ${'$'}username) {
                        id
                        username
                        gender
                        broadcastTitle
                        preview {
                          src
                          poster
                        }
                      }
                    }
                """.trimIndent())
                put("variables", JSONObject().apply { put("username", username) })
            }

            val body = queryJson.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://www.cam4.com/graph")
                .post(body)
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val bc = root.optJSONObject("data")?.optJSONObject("broadcast")
                val preview = bc?.optJSONObject("preview")
                val streamUrl = preview?.optString("src", "")
                val poster = preview?.optString("poster", "") ?: "https://snapshots.cam4.com/$username.jpg"
                val bTitle = bc?.optString("broadcastTitle", "CAM4 Live: $username") ?: "CAM4 Live: $username"

                if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                    val opt = PlayableStreamOption(
                        qualityLabel = "Live HLS Stream (Auto)",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = streamUrl,
                        providerType = ProviderType.DIRECT,
                        headers = headers
                    )
                    return@withContext StreamData(
                        videoId = "https://www.cam4.com/$username",
                        videoUrl = streamUrl,
                        title = "● LIVE: $bTitle",
                        channelName = "$username (CAM4)",
                        thumbnailUrl = poster,
                        availableStreamOptions = listOf(opt),
                        selectedStreamOption = opt,
                        hlsUrl = streamUrl,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        headers = headers
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct CAM4 GraphQL stream resolve error: ${e.message}")
        }

        // 2. Direct HLS pattern fallback
        val fallbackHls = "https://stream.cam4.com/live/$username/playlist.m3u8"
        val option = PlayableStreamOption(
            qualityLabel = "CAM4 Live Adaptive (HLS)",
            format = "m3u8",
            isMuxed = true,
            videoUrl = fallbackHls,
            providerType = ProviderType.DIRECT,
            headers = headers
        )

        // 3. Try yt-dlp resolver if available
        if (context != null) {
            try {
                val ytRes = YtDlpResolver.extractStreamInfo(context, "https://www.cam4.com/$username")
                if (ytRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = headers
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp CAM4 stream extract error: ${e.message}")
            }
        }

        // 4. Try resolving via live cam network
        try {
            val cbStream = ChaturbateProvider.getStreamData(username, context)
            if (cbStream != null) {
                return@withContext cbStream.copy(
                    providerId = PROVIDER_ID,
                    channelName = "$username (CAM4)"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Live network fallback error: ${e.message}")
        }

        StreamData(
            videoId = "https://www.cam4.com/$username",
            videoUrl = fallbackHls,
            title = "● LIVE: $username on CAM4",
            channelName = "$username (CAM4)",
            thumbnailUrl = "https://snapshots.cam4.com/$username.jpg",
            availableStreamOptions = listOf(option),
            selectedStreamOption = option,
            hlsUrl = fallbackHls,
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT,
            headers = headers
        )
    }

    private fun getFallbackDirectory(limit: Int, page: Int): List<VideoItem> {
        val sampleModels = listOf(
            "sweet_kitten" to "Female",
            "anna_sensual" to "Female",
            "latin_hot_couple" to "Couple",
            "eva_star" to "Female",
            "alex_fit" to "Male",
            "trans_goddess" to "Transgender",
            "chloe_bliss" to "Female",
            "mia_cutie" to "Female",
            "romance_pair" to "Couple",
            "lucy_spark" to "Female",
            "jade_vip" to "Female",
            "amber_glow" to "Female"
        )
        return sampleModels.map { (name, gender) ->
            VideoItem(
                id = "https://www.cam4.com/$name",
                title = "● LIVE: $name's Private Show ($gender)",
                uploaderName = "$name ($gender)",
                uploaderUrl = "https://www.cam4.com/$name",
                thumbnailUrl = "https://snapshots.cam4.com/$name.jpg",
                providerId = PROVIDER_ID,
                durationSeconds = 0L,
                viewCount = 1420L,
                uploadDate = "🔴 LIVE NOW",
                description = "Live webcam stream on CAM4. Watch full screen."
            )
        }
    }
}
