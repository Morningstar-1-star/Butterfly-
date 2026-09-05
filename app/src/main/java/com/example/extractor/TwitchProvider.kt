package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.model.parseDurationToSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Twitch Clips & Live Streams Provider.
 * Extracts high-definition direct MP4 video clips and Ushur HLS live streams
 * using Twitch's official GraphQL endpoint with token-authenticated playback.
 */
object TwitchProvider {
    private const val TAG = "TwitchProvider"
    private const val PROVIDER_ID = "twitch"
    private const val GQL_ENDPOINT = "https://gql.twitch.tv/gql"
    private const val CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun extractClipSlug(raw: String): String {
        val trimmed = raw.trim()
        val regex = Regex("""twitch\.tv/(?:[^/]+/clip/|clips\.twitch\.tv/)([a-zA-Z0-9_-]+)""")
        val match = regex.find(trimmed)
        if (match != null) return match.groupValues[1]

        val clipParam = Regex("""[?&]clip=([a-zA-Z0-9_-]+)""").find(trimmed)
        if (clipParam != null) return clipParam.groupValues[1]

        return trimmed.substringAfterLast("/")
    }

    private fun parseQualityScore(quality: String): Int {
        val q = quality.lowercase()
        return when {
            q.contains("1080") -> 80
            q.contains("720") -> 60
            q.contains("480") -> 40
            q.contains("360") -> 30
            else -> 20
        }
    }

    suspend fun getStreamData(urlOrSlug: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val slug = extractClipSlug(urlOrSlug)

        // 1. Try resolving as Twitch Clip
        val clipData = resolveClipStream(slug)
        if (clipData != null) return@withContext clipData

        // 2. If clip resolution returns null, try resolving as Twitch Live Channel
        val liveData = resolveLiveStream(slug)
        if (liveData != null) return@withContext liveData

        null
    }

    private suspend fun resolveClipStream(slug: String): StreamData? = withContext(Dispatchers.IO) {
        try {
            val queryJson = JSONObject().apply {
                put("query", """
                    query {
                        clip(slug: "$slug") {
                            id
                            title
                            durationSeconds
                            viewCount
                            createdAt
                            broadcaster {
                                displayName
                                profileImageURL(width: 300)
                            }
                            playbackAccessToken(params: { platform: "web", playerType: "site" }) {
                                signature
                                value
                            }
                            videoQualities {
                                frameRate
                                quality
                                sourceURL
                            }
                        }
                    }
                """.trimIndent())
            }

            val req = Request.Builder()
                .url(GQL_ENDPOINT)
                .header("Client-Id", CLIENT_ID)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .post(queryJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val json = JSONObject(body)
                val clip = json.optJSONObject("data")?.optJSONObject("clip") ?: return@use null

                val title = clip.optString("title", "Twitch Clip $slug")
                val durationSec = clip.optLong("durationSeconds", 30L)
                val views = clip.optLong("viewCount", 1000L)
                val broadcaster = clip.optJSONObject("broadcaster")
                val channelName = broadcaster?.optString("displayName", "Twitch Streamer") ?: "Twitch Streamer"
                val avatar = broadcaster?.optString("profileImageURL")

                val tokenObj = clip.optJSONObject("playbackAccessToken")
                val sig = tokenObj?.optString("signature", "") ?: ""
                val token = tokenObj?.optString("value", "") ?: ""

                val qualities = clip.optJSONArray("videoQualities") ?: JSONArray()
                val options = mutableListOf<PlayableStreamOption>()

                for (i in 0 until qualities.length()) {
                    val q = qualities.optJSONObject(i) ?: continue
                    val sourceUrl = q.optString("sourceURL")
                    val qualityStr = q.optString("quality", "1080")
                    if (sourceUrl.isNotBlank()) {
                        val fullUrl = if (sig.isNotBlank() && token.isNotBlank()) {
                            "$sourceUrl?sig=$sig&token=${URLEncoder.encode(token, "UTF-8")}"
                        } else sourceUrl

                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "${qualityStr}p",
                                format = "mp4",
                                isMuxed = true,
                                videoUrl = fullUrl,
                                headers = mapOf("Referer" to "https://www.twitch.tv/")
                            )
                        )
                    }
                }

                if (options.isNotEmpty()) {
                    val selected = options.maxByOrNull { parseQualityScore(it.qualityLabel) } ?: options.first()
                    return@withContext StreamData(
                        videoId = slug,
                        title = title,
                        channelName = channelName,
                        channelAvatarUrl = avatar,
                        subscriberCountText = "Twitch Partner",
                        viewCount = views,
                        uploadDate = "Twitch Clip",
                        description = "Twitch Clip: $title by $channelName",
                        availableStreamOptions = options,
                        selectedStreamOption = selected,
                        providerId = PROVIDER_ID
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Twitch clip extraction note: ${e.message}")
        }
        null
    }

    private suspend fun resolveLiveStream(channelLogin: String): StreamData? = withContext(Dispatchers.IO) {
        try {
            val queryJson = JSONObject().apply {
                put("query", """
                    query {
                        user(login: "$channelLogin") {
                            id
                            login
                            displayName
                            profileImageURL(width: 300)
                            stream {
                                id
                                title
                                viewersCount
                                type
                                game {
                                    name
                                }
                                playbackAccessToken(params: { platform: "web", playerType: "site" }) {
                                    signature
                                    value
                                }
                            }
                        }
                    }
                """.trimIndent())
            }

            val req = Request.Builder()
                .url(GQL_ENDPOINT)
                .header("Client-Id", CLIENT_ID)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .post(queryJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val json = JSONObject(body)
                val user = json.optJSONObject("data")?.optJSONObject("user") ?: return@use null
                val stream = user.optJSONObject("stream") ?: return@use null

                val title = stream.optString("title", "${user.optString("displayName")} Live Stream")
                val viewers = stream.optLong("viewersCount", 1000L)
                val channelName = user.optString("displayName", channelLogin)
                val avatar = user.optString("profileImageURL")
                val gameName = stream.optJSONObject("game")?.optString("name", "Just Chatting") ?: "Live"

                val tokenObj = stream.optJSONObject("playbackAccessToken")
                val sig = tokenObj?.optString("signature", "") ?: ""
                val token = tokenObj?.optString("value", "") ?: ""

                if (sig.isNotBlank() && token.isNotBlank()) {
                    val encodedToken = URLEncoder.encode(token, "UTF-8")
                    val usherUrl = "https://usher.ttvnw.net/api/channel/hls/$channelLogin.m3u8?client_id=$CLIENT_ID&token=$encodedToken&sig=$sig&allow_source=true&allow_audio_only=true"

                    val option = PlayableStreamOption(
                        qualityLabel = "Source (Live 1080p60)",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = usherUrl,
                        headers = mapOf("Referer" to "https://www.twitch.tv/")
                    )

                    return@withContext StreamData(
                        videoId = channelLogin,
                        title = "🔴 $title",
                        channelName = channelName,
                        channelAvatarUrl = avatar,
                        subscriberCountText = "$gameName Stream",
                        viewCount = viewers,
                        uploadDate = "Live Now",
                        description = "Twitch Live: $title\nPlaying $gameName for $viewers viewers.",
                        availableStreamOptions = listOf(option),
                        selectedStreamOption = option,
                        providerId = PROVIDER_ID
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Twitch live stream resolution note: ${e.message}")
        }
        null
    }

    suspend fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VideoItem>()
        val seenIds = mutableSetOf<String>()

        // 1. Fetch Top Live Streams
        try {
            val streamsQuery = JSONObject().apply {
                put("query", """
                    query {
                        streams(first: ${limit.coerceIn(5, 30)}) {
                            edges {
                                node {
                                    id
                                    title
                                    viewersCount
                                    previewImageURL(width: 640, height: 360)
                                    broadcaster {
                                        id
                                        login
                                        displayName
                                        profileImageURL(width: 150)
                                    }
                                    game {
                                        name
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent())
            }

            val req = Request.Builder()
                .url(GQL_ENDPOINT)
                .header("Client-Id", CLIENT_ID)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .post(streamsQuery.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val edges = json.optJSONObject("data")?.optJSONObject("streams")?.optJSONArray("edges") ?: JSONArray()

                    for (i in 0 until edges.length()) {
                        val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                        val broadcaster = node.optJSONObject("broadcaster") ?: continue
                        val login = broadcaster.optString("login")
                        if (login.isBlank() || seenIds.contains(login)) continue
                        seenIds.add(login)

                        val title = node.optString("title", "Live: $login")
                        val thumb = node.optString("previewImageURL")
                        val viewers = node.optLong("viewersCount", 1500L)
                        val author = broadcaster.optString("displayName", login)
                        val avatar = broadcaster.optString("profileImageURL")
                        val game = node.optJSONObject("game")?.optString("name", "Twitch") ?: "Twitch"

                        items.add(
                            VideoItem(
                                id = login,
                                title = title,
                                uploaderName = "$author ($game)",
                                uploaderAvatarUrl = avatar,
                                thumbnailUrl = thumb,
                                durationSeconds = -1L,
                                viewCount = viewers,
                                uploadDate = "Live",
                                providerId = PROVIDER_ID
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Twitch live streams fetch error: ${e.message}")
        }

        // 2. Fetch Top Clips
        try {
            val clipsQuery = JSONObject().apply {
                put("query", """
                    query {
                        clips(first: ${limit.coerceIn(5, 30)}, criteria: { period: ALL_TIME }) {
                            edges {
                                node {
                                    id
                                    slug
                                    title
                                    thumbnailURL
                                    durationSeconds
                                    viewCount
                                    broadcaster {
                                        displayName
                                        profileImageURL(width: 150)
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent())
            }

            val req = Request.Builder()
                .url(GQL_ENDPOINT)
                .header("Client-Id", CLIENT_ID)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .post(clipsQuery.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val edges = json.optJSONObject("data")?.optJSONObject("clips")?.optJSONArray("edges") ?: JSONArray()

                    for (i in 0 until edges.length()) {
                        val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                        val slug = node.optString("slug")
                        if (slug.isBlank() || seenIds.contains(slug)) continue
                        seenIds.add(slug)

                        val title = node.optString("title", "Twitch Clip")
                        val thumb = node.optString("thumbnailURL")
                        val durationSec = node.optLong("durationSeconds", 30L)
                        val views = node.optLong("viewCount", 1000L)
                        val broadcaster = node.optJSONObject("broadcaster")
                        val author = broadcaster?.optString("displayName", "Twitch Streamer") ?: "Twitch Streamer"
                        val avatar = broadcaster?.optString("profileImageURL")

                        items.add(
                            VideoItem(
                                id = slug,
                                title = title,
                                uploaderName = author,
                                uploaderAvatarUrl = avatar,
                                thumbnailUrl = thumb,
                                durationSeconds = durationSec,
                                viewCount = views,
                                uploadDate = "Clip",
                                providerId = PROVIDER_ID
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Twitch clips fetch error: ${e.message}")
        }

        items
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VideoItem>()
        val seenIds = mutableSetOf<String>()
        try {
            val safeQuery = query.replace("\"", "\\\"")
            val queryJson = JSONObject().apply {
                put("query", """
                    query {
                        searchFor(searchQuery: "$safeQuery", first: ${limit.coerceIn(5, 30)}, types: [CLIPS, CHANNELS, GAMES]) {
                            clips {
                                edges {
                                    node {
                                        id
                                        slug
                                        title
                                        thumbnailURL
                                        durationSeconds
                                        viewCount
                                        broadcaster {
                                            displayName
                                            profileImageURL(width: 150)
                                        }
                                    }
                                }
                            }
                            channels {
                                edges {
                                    node {
                                        id
                                        login
                                        displayName
                                        profileImageURL(width: 150)
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent())
            }

            val req = Request.Builder()
                .url(GQL_ENDPOINT)
                .header("Client-Id", CLIENT_ID)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .post(queryJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string() ?: return@use
                val json = JSONObject(body)
                val searchFor = json.optJSONObject("data")?.optJSONObject("searchFor") ?: return@use

                // 1. Clips
                val clipEdges = searchFor.optJSONObject("clips")?.optJSONArray("edges") ?: JSONArray()
                for (i in 0 until clipEdges.length()) {
                    val node = clipEdges.optJSONObject(i)?.optJSONObject("node") ?: continue
                    val slug = node.optString("slug")
                    if (slug.isBlank() || seenIds.contains(slug)) continue
                    seenIds.add(slug)

                    val title = node.optString("title", "Twitch Clip")
                    val thumb = node.optString("thumbnailURL")
                    val durationSec = node.optLong("durationSeconds", 30L)
                    val views = node.optLong("viewCount", 500L)
                    val broadcaster = node.optJSONObject("broadcaster")
                    val author = broadcaster?.optString("displayName", "Twitch Streamer") ?: "Twitch Streamer"
                    val avatar = broadcaster?.optString("profileImageURL")

                    items.add(
                        VideoItem(
                            id = slug,
                            title = title,
                            uploaderName = author,
                            uploaderAvatarUrl = avatar,
                            thumbnailUrl = thumb,
                            durationSeconds = durationSec,
                            viewCount = views,
                            uploadDate = "Twitch",
                            providerId = PROVIDER_ID
                        )
                    )
                }

                // 2. Channels
                val channelEdges = searchFor.optJSONObject("channels")?.optJSONArray("edges") ?: JSONArray()
                for (i in 0 until channelEdges.length()) {
                    val node = channelEdges.optJSONObject(i)?.optJSONObject("node") ?: continue
                    val login = node.optString("login")
                    if (login.isBlank() || seenIds.contains(login)) continue
                    seenIds.add(login)

                    val author = node.optString("displayName", login)
                    val avatar = node.optString("profileImageURL")

                    items.add(
                        VideoItem(
                            id = login,
                            title = "$author Channel",
                            uploaderName = author,
                            uploaderAvatarUrl = avatar,
                            thumbnailUrl = avatar,
                            durationSeconds = -1L,
                            viewCount = 1000L,
                            uploadDate = "Twitch Channel",
                            providerId = PROVIDER_ID
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Twitch search failed: ${e.message}")
        }
        items
    }
}

