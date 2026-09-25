package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * High-performance Stripchat Live Cam Provider & Direct HLS Stream Extractor.
 * Connects directly to Stripchat front API endpoints and DoppioCDN streaming edge clusters
 * using System DNS and canonical headers for zero-buffer live playback.
 */
object StripchatProvider {
    private const val TAG = "StripchatProvider"
    const val PROVIDER_ID = "stripchat"
    private const val BASE_URL = "https://stripchat.com"

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(Dns.SYSTEM)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    private val feedCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<VideoItem>>>()
    private const val CACHE_TTL = 30_000L // 30 seconds for live feeds

    suspend fun getHome(limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        val offset = (page - 1) * limit
        val cacheKey = "home_${page}_$limit"

        feedCache[cacheKey]?.let { (ts, items) ->
            if (System.currentTimeMillis() - ts < CACHE_TTL && items.isNotEmpty()) {
                return@withContext items.take(limit)
            }
        }

        try {
            // Stripchat JSON API for live models
            val apiUrls = listOf(
                "$BASE_URL/api/front/models?limit=$limit&offset=$offset&primaryTag=girls",
                "$BASE_URL/api/front/v2/models?limit=$limit&offset=$offset",
                "$BASE_URL/api/front/models?limit=$limit&offset=$offset"
            )

            for (apiUrl in apiUrls) {
                if (list.isNotEmpty()) break
                try {
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
                        val modelsArr = root.optJSONArray("models")
                            ?: root.optJSONArray("items")
                            ?: root.optJSONArray("data")

                        if (modelsArr != null) {
                            for (i in 0 until modelsArr.length()) {
                                val m = modelsArr.optJSONObject(i) ?: continue
                                val username = m.optString("username", "")
                                    .ifBlank { m.optJSONObject("user")?.optString("username", "") ?: "" }
                                if (username.isBlank()) continue

                                val status = m.optString("status", "public")
                                if (status == "off" || status == "offline") continue

                                val topic = m.optString("topic", m.optString("statusText", "$username's Live Cam")).trim()
                                val previewObj = m.optJSONObject("previewUrlThumbBig") ?: m.optJSONObject("snapshotUrl")
                                val thumb = m.optString("snapshotUrl", "").ifBlank {
                                    m.optString("avatarUrl", "").ifBlank {
                                        "https://img.strpst.com/thumbs/$username.jpg"
                                    }
                                }
                                val viewers = m.optLong("viewersCount", m.optLong("usersCount", 1200L))
                                val isHd = m.optBoolean("isCamAvailable", true) || m.optBoolean("isHd", true)

                                list.add(
                                    VideoItem(
                                        id = "$BASE_URL/$username",
                                        title = "● LIVE: ${if (topic.isNotBlank()) topic else "$username Live Room"}",
                                        uploaderName = "$username (Live${if (isHd) " • HD" else ""})",
                                        uploaderUrl = "$BASE_URL/$username",
                                        thumbnailUrl = thumb,
                                        providerId = PROVIDER_ID,
                                        durationSeconds = 0L,
                                        viewCount = viewers,
                                        uploadDate = "🔴 LIVE NOW",
                                        description = "Live interactive Stripchat HD broadcast for $username"
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Stripchat API endpoint $apiUrl note: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stripchat feed API fetch note: ${e.message}")
        }

        // Fallback: parse homepage HTML if API response structure is modified
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
                    val userMatches = Regex("""(?:data-model-name=['"]([a-zA-Z0-9_-]+)['"]|href=['"]/(?:[a-zA-Z0-9_-]+)['"][^>]*class=['"][^'"]*model[^'"]*['"]|class=['"][^'"]*model-item[^'"]*['"][^>]*data-username=['"]([a-zA-Z0-9_-]+)['"])""").findAll(html)
                    for (match in userMatches) {
                        val user = (match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
                            ?: match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }) ?: continue
                        if (user.lowercase() in listOf("login", "signup", "tags", "categories", "vr", "app", "terms", "privacy", "help")) continue
                        list.add(
                            VideoItem(
                                id = "$BASE_URL/$user",
                                title = "● LIVE: $user Show",
                                uploaderName = "$user (Stripchat Live)",
                                uploaderUrl = "$BASE_URL/$user",
                                thumbnailUrl = "https://img.strpst.com/thumbs/$user.jpg",
                                providerId = PROVIDER_ID,
                                durationSeconds = 0L,
                                viewCount = 1500L,
                                uploadDate = "🔴 LIVE NOW",
                                description = "Stripchat Live Model HD Show"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stripchat HTML fallback error: ${e.message}")
            }
        }

        val res = list.distinctBy { it.id }.take(limit)
        if (res.isNotEmpty()) {
            feedCache[cacheKey] = Pair(System.currentTimeMillis(), res)
        }
        res
    }

    suspend fun search(query: String, limit: Int = 24, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("(?i)stripchat:"), "").trim()
        if (clean.isBlank()) return@withContext getHome(limit, page)
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val offset = (page - 1) * limit
        val list = mutableListOf<VideoItem>()

        try {
            val searchApiUrl = "$BASE_URL/api/front/models?query=$encoded&limit=$limit&offset=$offset"
            val req = Request.Builder()
                .url(searchApiUrl)
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

                        val topic = m.optString("topic", m.optString("statusText", "$username's Live Cam")).trim()
                        val thumb = m.optString("snapshotUrl", "").ifBlank {
                            m.optString("avatarUrl", "").ifBlank {
                                "https://img.strpst.com/thumbs/$username.jpg"
                            }
                        }
                        val viewers = m.optLong("viewersCount", m.optLong("usersCount", 900L))

                        list.add(
                            VideoItem(
                                id = "$BASE_URL/$username",
                                title = "● LIVE: ${if (topic.isNotBlank()) topic else "$username Live Room"}",
                                uploaderName = "$username (Stripchat Live)",
                                uploaderUrl = "$BASE_URL/$username",
                                thumbnailUrl = thumb,
                                providerId = PROVIDER_ID,
                                durationSeconds = 0L,
                                viewCount = viewers,
                                uploadDate = "🔴 LIVE NOW",
                                description = "Stripchat live search result for $clean"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stripchat search API note: ${e.message}")
        }

        if (list.isNotEmpty()) {
            return@withContext list.distinctBy { it.id }.take(limit)
        }

        // Fallback: search within home feed
        val homeItems = getHome(60, 1)
        val filtered = homeItems.filter {
            it.title.contains(clean, ignoreCase = true) || it.uploaderName.contains(clean, ignoreCase = true)
        }
        if (filtered.isNotEmpty()) filtered.take(limit) else homeItems.take(limit)
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val username = when {
            urlOrId.startsWith("http://") || urlOrId.startsWith("https://") -> {
                urlOrId.substringAfterLast("/").substringBefore("?").substringBefore("#").trim()
            }
            urlOrId.startsWith("stripchat:", ignoreCase = true) -> {
                urlOrId.substringAfter(":").substringBefore("?").substringBefore("#").trim('/')
            }
            else -> urlOrId.substringBefore("?").substringBefore("#").trim()
        }

        if (username.isBlank()) return@withContext null
        val targetPageUrl = if (urlOrId.startsWith("http")) urlOrId else "$BASE_URL/$username"

        val defaultStreamHeaders = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to "$BASE_URL/",
            "Origin" to BASE_URL,
            "Accept" to "*/*"
        )

        var streamNameFound: String? = null
        val viewServersFound = mutableListOf<String>()
        var directHlsUrl: String? = null
        var modelTitle: String = "$username Live Cam"
        var modelAvatar: String = "https://img.strpst.com/thumbs/$username.jpg"
        var snapshotThumb: String = "https://img.strpst.com/thumbs/$username.jpg"

        // STEP 1: Query Stripchat front cam API endpoints
        val candidateApiUrls = listOf(
            "$BASE_URL/api/front/v2/models/username/$username/cam",
            "$BASE_URL/api/front/models/username/$username/cam",
            "$BASE_URL/api/front/models/username/$username"
        )

        for (apiUrl in candidateApiUrls) {
            if (directHlsUrl != null || streamNameFound != null) break
            try {
                val req = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Referer", targetPageUrl)
                    .header("Origin", BASE_URL)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()

                val jsonStr = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!jsonStr.isNullOrBlank()) {
                    val root = JSONObject(jsonStr)

                    // Check cam object
                    val camObj = root.optJSONObject("cam")
                        ?: root.optJSONObject("model")?.optJSONObject("cam")
                        ?: root.optJSONObject("stream")

                    if (camObj != null) {
                        val sName = camObj.optString("streamName", "")
                        if (sName.isNotBlank()) streamNameFound = sName

                        val hls = camObj.optString("hlsStreamUrl", "").ifBlank {
                            camObj.optString("streamUrl", "").ifBlank {
                                camObj.optString("url", "")
                            }
                        }
                        if (hls.isNotBlank()) directHlsUrl = hls

                        // Extract viewServers
                        val vsObj = camObj.optJSONObject("viewServers")
                        if (vsObj != null) {
                            val keys = vsObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val v = vsObj.optString(k, "")
                                if (v.isNotBlank()) {
                                    if (v.startsWith("http://") || v.startsWith("https://")) {
                                        if (v.contains(".m3u8")) directHlsUrl = v
                                        val host = v.substringAfter("://").substringBefore("/")
                                        if (host.isNotBlank() && !viewServersFound.contains(host)) viewServersFound.add(host)
                                    } else if (!viewServersFound.contains(v)) {
                                        viewServersFound.add(v)
                                    }
                                }
                            }
                        }

                        val vsArr = camObj.optJSONArray("viewServers") ?: camObj.optJSONArray("servers")
                        if (vsArr != null) {
                            for (i in 0 until vsArr.length()) {
                                val s = vsArr.optString(i, "")
                                if (s.isNotBlank() && !viewServersFound.contains(s)) viewServersFound.add(s)
                            }
                        }
                    }

                    // Extract user / model metadata
                    val userObj = root.optJSONObject("user")?.optJSONObject("user")
                        ?: root.optJSONObject("user")
                        ?: root.optJSONObject("model")
                    if (userObj != null) {
                        val statusText = userObj.optString("statusText", "").ifBlank {
                            userObj.optString("topic", "")
                        }
                        if (statusText.isNotBlank()) modelTitle = statusText
                        val snap = userObj.optString("snapshotUrl", "").ifBlank {
                            userObj.optString("avatarUrl", "")
                        }
                        if (snap.isNotBlank()) {
                            snapshotThumb = snap
                            modelAvatar = snap
                        }
                    }

                    if (directHlsUrl.isNullOrBlank()) {
                        val rootHls = root.optString("hlsStreamUrl", "").ifBlank { root.optString("streamUrl", "") }
                        if (rootHls.isNotBlank()) directHlsUrl = rootHls
                    }
                    if (streamNameFound.isNullOrBlank()) {
                        val rootSName = root.optString("streamName", "")
                        if (rootSName.isNotBlank()) streamNameFound = rootSName
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Stripchat API $apiUrl note: ${e.message}")
            }
        }

        // STEP 2: HTML Scrape Fallback if API changed format
        if (directHlsUrl.isNullOrBlank() && streamNameFound.isNullOrBlank()) {
            try {
                val req = Request.Builder()
                    .url(targetPageUrl)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()

                val html = httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }

                if (!html.isNullOrBlank()) {
                    val sNameMatch = Regex("""["']streamName["']\s*:\s*["']([^"']+)["']""").find(html)
                    if (sNameMatch != null) streamNameFound = sNameMatch.groupValues[1]

                    val hlsMatch = Regex("""["']hlsStreamUrl["']\s*:\s*["']([^"']+)["']""").find(html)
                    if (hlsMatch != null) directHlsUrl = hlsMatch.groupValues[1].replace("\\/", "/")

                    val snapMatch = Regex("""["']snapshotUrl["']\s*:\s*["']([^"']+)["']""").find(html)
                    if (snapMatch != null) snapshotThumb = snapMatch.groupValues[1].replace("\\/", "/")

                    val vsMatches = Regex("""["'](?:b-hls-\d+|edge-hls)\.doppiocdn\.(?:com|live)["']""").findAll(html)
                    for (m in vsMatches) {
                        val s = m.value.trim('"', '\'')
                        if (!viewServersFound.contains(s)) viewServersFound.add(s)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stripchat HTML scrape note: ${e.message}")
            }
        }

        // STEP 3: Fallback streamName if needed
        val effectiveStreamName = streamNameFound ?: username
        if (viewServersFound.isEmpty()) {
            viewServersFound.addAll(
                listOf(
                    "edge-hls.doppiocdn.com",
                    "b-hls-17.doppiocdn.com",
                    "b-hls-01.doppiocdn.com",
                    "b-hls-02.doppiocdn.com",
                    "b-hls-05.doppiocdn.com",
                    "edge-hls.doppiocdn.live"
                )
            )
        }

        // STEP 4: Build high-performance PlayableStreamOption list with fallback clusters
        val streamOptions = mutableListOf<PlayableStreamOption>()

        if (!directHlsUrl.isNullOrBlank()) {
            val cleanDirect = directHlsUrl.replace("\\/", "/")
            streamOptions.add(
                PlayableStreamOption(
                    qualityLabel = "Auto 1080p (Primary Live)",
                    format = "m3u8",
                    isMuxed = true,
                    videoUrl = cleanDirect,
                    providerType = ProviderType.DIRECT,
                    headers = defaultStreamHeaders
                )
            )
        }

        // Construct Canonical DoppelCDN Master Playlists
        for (server in viewServersFound) {
            val cleanServer = server.removePrefix("https://").removePrefix("http://").trimEnd('/')
            val directStreamUrl = "https://$cleanServer/hls/$effectiveStreamName/$effectiveStreamName.m3u8"
            val autoUrl = "https://$cleanServer/hls/$effectiveStreamName/${effectiveStreamName}_auto.m3u8"
            val direct1080Url = "https://$cleanServer/hls/$effectiveStreamName/${effectiveStreamName}_1080p.m3u8"
            val direct720Url = "https://$cleanServer/hls/$effectiveStreamName/${effectiveStreamName}_720p.m3u8"
            val direct480Url = "https://$cleanServer/hls/$effectiveStreamName/${effectiveStreamName}_480p.m3u8"

            if (streamOptions.none { it.videoUrl == directStreamUrl }) {
                streamOptions.add(
                    PlayableStreamOption(
                        qualityLabel = "Auto 1080p (Primary Live)",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = directStreamUrl,
                        providerType = ProviderType.DIRECT,
                        headers = defaultStreamHeaders
                    )
                )
            }
            if (streamOptions.none { it.videoUrl == autoUrl }) {
                streamOptions.add(
                    PlayableStreamOption(
                        qualityLabel = "1080p 60fps (Doppio CDN)",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = autoUrl,
                        providerType = ProviderType.DIRECT,
                        headers = defaultStreamHeaders
                    )
                )
            }
            if (streamOptions.none { it.videoUrl == direct1080Url }) {
                streamOptions.add(
                    PlayableStreamOption(
                        qualityLabel = "1080p Full HD",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = direct1080Url,
                        providerType = ProviderType.DIRECT,
                        headers = defaultStreamHeaders
                    )
                )
            }
            if (streamOptions.none { it.videoUrl == direct720Url }) {
                streamOptions.add(
                    PlayableStreamOption(
                        qualityLabel = "720p HD",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = direct720Url,
                        providerType = ProviderType.DIRECT,
                        headers = defaultStreamHeaders
                    )
                )
            }
            if (streamOptions.none { it.videoUrl == direct480Url }) {
                streamOptions.add(
                    PlayableStreamOption(
                        qualityLabel = "480p SD (Fast Buffer)",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = direct480Url,
                        providerType = ProviderType.DIRECT,
                        headers = defaultStreamHeaders
                    )
                )
            }
        }

        // Additional edge cluster fallback
        val edgeAutoUrl = "https://edge-hls.doppiocdn.com/hls/$effectiveStreamName/$effectiveStreamName.m3u8"
        if (streamOptions.none { it.videoUrl == edgeAutoUrl }) {
            streamOptions.add(
                PlayableStreamOption(
                    qualityLabel = "Adaptive Live (Global Edge)",
                    format = "m3u8",
                    isMuxed = true,
                    videoUrl = edgeAutoUrl,
                    providerType = ProviderType.DIRECT,
                    headers = defaultStreamHeaders
                )
            )
        }

        if (streamOptions.isEmpty()) {
            // STEP 5: yt-dlp fallback if direct CDN assembly was insufficient
            if (context != null) {
                try {
                    val result = YtDlpResolver.extractStreamInfo(context, targetPageUrl)
                    if (result is YouTubeExtractorHelper.ExtractionResult.Success) {
                        return@withContext result.streamData.copy(
                            providerId = PROVIDER_ID,
                            headers = defaultStreamHeaders
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "YtDlpResolver Stripchat extraction note: ${e.message}")
                }
            }
            return@withContext null
        }

        val bestOption = streamOptions.first()
        val primaryPlayableUrl = bestOption.videoUrl ?: ""

        Log.i(TAG, "Stripchat stream successfully extracted for $username -> $primaryPlayableUrl with ${streamOptions.size} options")

        return@withContext StreamData(
            videoId = targetPageUrl,
            videoUrl = primaryPlayableUrl,
            title = "● LIVE: ${if (modelTitle.isNotBlank()) modelTitle else "$username Show"}",
            channelName = username,
            channelAvatarUrl = modelAvatar,
            description = "Live High Speed Interactive Broadcast on Stripchat ($username)",
            thumbnailUrl = snapshotThumb,
            providerId = PROVIDER_ID,
            providerType = ProviderType.DIRECT,
            headers = defaultStreamHeaders,
            availableStreamOptions = streamOptions,
            selectedStreamOption = bestOption,
            hlsUrl = primaryPlayableUrl
        )
    }
}
