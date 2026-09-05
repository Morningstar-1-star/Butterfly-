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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

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

    data class CachedCam4Stream(
        val username: String,
        val title: String,
        val streamUrl: String?,
        val posterUrl: String,
        val viewers: Long,
        val gender: String
    )

    private val liveStreamCache = ConcurrentHashMap<String, CachedCam4Stream>()

    suspend fun getHome(limit: Int = 24, page: Int = 1, gender: String? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()

        val genderFilter = when (gender?.lowercase()) {
            "female", "f", "girls" -> "female"
            "male", "m", "men" -> "male"
            "couple", "c", "couples" -> "couple"
            "trans", "t", "transgender", "shemale" -> "transgender"
            else -> null
        }

        val offset = (page - 1) * limit

        // 1. Try CAM4 GraphQL API with working schema
        try {
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
                          sexualOrientation
                          profileImageURL
                          preview {
                            sourceType
                            src
                            poster
                            orientation
                          }
                          viewers
                          broadcastType
                          showType
                        }
                      }
                    }
                """.trimIndent())
                val variables = JSONObject().apply {
                    val input = JSONObject().apply {
                        if (!genderFilter.isNullOrBlank()) {
                            put("gender", genderFilter)
                        }
                        val cursor = JSONObject().apply {
                            put("first", limit)
                            put("offset", offset)
                        }
                        put("cursor", cursor)
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

                if (itemsArr != null && itemsArr.length() > 0) {
                    for (i in 0 until itemsArr.length()) {
                        val item = itemsArr.optJSONObject(i) ?: continue
                        val username = item.optString("username", "")
                        if (username.isBlank()) continue

                        val preview = item.optJSONObject("preview")
                        val src = preview?.optString("src", "")
                        val poster = preview?.optString("poster", "") ?: ""
                        val profileImg = item.optString("profileImageURL", "") ?: ""
                        val viewers = item.optLong("viewers", 950L)
                        val g = item.optString("gender", "Live Cam") ?: "Live Cam"

                        val finalPoster = when {
                            poster.isNotBlank() -> poster
                            profileImg.isNotBlank() -> profileImg
                            else -> "https://snapshots.xcdnpro.com/thumbnails/$username"
                        }

                        // Cache live stream info for instant playback
                        liveStreamCache[username.lowercase()] = CachedCam4Stream(
                            username = username,
                            title = "$username's CAM4 Live Show",
                            streamUrl = if (!src.isNullOrBlank() && src.startsWith("http")) src else null,
                            posterUrl = finalPoster,
                            viewers = viewers,
                            gender = g
                        )

                        list.add(
                            VideoItem(
                                id = "https://www.cam4.com/$username",
                                title = "● LIVE: $username's Live Cam",
                                uploaderName = "$username (${g.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }})",
                                uploaderUrl = "https://www.cam4.com/$username",
                                thumbnailUrl = finalPoster,
                                providerId = PROVIDER_ID,
                                durationSeconds = 0L,
                                viewCount = viewers,
                                uploadDate = "🔴 LIVE NOW",
                                description = "Live webcam stream from $username on CAM4."
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CAM4 GraphQL home fetch failed: ${e.message}")
        }

        list
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val q = query.replace(Regex("(?i)^(cam4:)?"), "").trim()
        val genderFilter = when (q.lowercase()) {
            "female", "girls", "women" -> "female"
            "male", "guys", "men" -> "male"
            "couple", "couples" -> "couple"
            "trans", "transgender", "shemale" -> "transgender"
            else -> null
        }

        if (genderFilter != null) {
            return@withContext getHome(limit, page, genderFilter)
        }

        val allCams = getHome(limit * 2, page)
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

        // 1. Check in-memory stream cache
        val cached = liveStreamCache[username.lowercase()]
        if (cached != null && !cached.streamUrl.isNullOrBlank()) {
            val opt = PlayableStreamOption(
                qualityLabel = "CAM4 Live HD (Direct HLS)",
                format = "m3u8",
                isMuxed = true,
                videoUrl = cached.streamUrl,
                providerType = ProviderType.DIRECT,
                headers = headers
            )
            return@withContext StreamData(
                videoId = "https://www.cam4.com/$username",
                videoUrl = cached.streamUrl,
                title = "● LIVE: ${cached.title}",
                channelName = "$username (CAM4)",
                thumbnailUrl = cached.posterUrl,
                availableStreamOptions = listOf(opt),
                selectedStreamOption = opt,
                hlsUrl = cached.streamUrl,
                providerId = PROVIDER_ID,
                providerType = ProviderType.DIRECT,
                headers = headers
            )
        }

        // 2. Query GraphQL broadcasts with users: [username]
        try {
            val queryJson = JSONObject().apply {
                put("query", """
                    query getPerformerData(${'$'}input: BroadcastsInput) {
                      broadcasts(input: ${'$'}input) {
                        items {
                          id
                          username
                          gender
                          profileImageURL
                          preview {
                            src
                            poster
                          }
                          viewers
                        }
                      }
                    }
                """.trimIndent())
                val variables = JSONObject().apply {
                    val input = JSONObject().apply {
                        val usersArr = JSONArray().apply { put(username) }
                        put("users", usersArr)
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
                val itemsArr = root.optJSONObject("data")?.optJSONObject("broadcasts")?.optJSONArray("items")
                if (itemsArr != null && itemsArr.length() > 0) {
                    val bc = itemsArr.optJSONObject(0)
                    val preview = bc?.optJSONObject("preview")
                    val streamUrl = preview?.optString("src", "")
                    val poster = preview?.optString("poster", "") ?: ""
                    val profileImg = bc?.optString("profileImageURL", "") ?: ""
                    val finalPoster = when {
                        !poster.isNullOrBlank() -> poster
                        !profileImg.isNullOrBlank() -> profileImg
                        else -> "https://snapshots.xcdnpro.com/thumbnails/$username"
                    }

                    if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                        val opt = PlayableStreamOption(
                            qualityLabel = "CAM4 Live HD (HLS)",
                            format = "m3u8",
                            isMuxed = true,
                            videoUrl = streamUrl,
                            providerType = ProviderType.DIRECT,
                            headers = headers
                        )
                        return@withContext StreamData(
                            videoId = "https://www.cam4.com/$username",
                            videoUrl = streamUrl,
                            title = "● LIVE: $username on CAM4",
                            channelName = "$username (CAM4)",
                            thumbnailUrl = finalPoster,
                            availableStreamOptions = listOf(opt),
                            selectedStreamOption = opt,
                            hlsUrl = streamUrl,
                            providerId = PROVIDER_ID,
                            providerType = ProviderType.DIRECT,
                            headers = headers
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct CAM4 GraphQL stream resolve error: ${e.message}")
        }

        // 3. Fallback: try refreshing home feed to find the model or return best known preview
        null
    }
}

