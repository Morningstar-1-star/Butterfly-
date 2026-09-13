package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object DailymotionProvider {
    private const val TAG = "DailymotionProvider"
    const val PROVIDER_ID = "dailymotion"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val REFERER = "https://www.dailymotion.com/"

    private const val API_FIELDS =
        "id,title,owner.username,owner.screenname,owner.avatar_120_url,owner.avatar_240_url,owner.avatar_720_url,owner.url,thumbnail_720_url,thumbnail_480_url,duration,views_total,created_time"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Accept", "*/*")
                .build()
            chain.proceed(req)
        }
        .build()

    val dmHeaders = mapOf(
        "User-Agent" to USER_AGENT,
        "Referer" to REFERER,
        "Accept" to "*/*"
    )

    suspend fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val sorts = listOf("visited-today", "recent", "visited-this-week", "visited-month")
        val sort = sorts[((page - 1).coerceAtLeast(0)) % sorts.size]
        val pageNum = (((page - 1).coerceAtLeast(0)) / sorts.size) + 1
        val url = "https://api.dailymotion.com/videos?fields=$API_FIELDS&sort=$sort&limit=$limit&page=$pageNum"
        val items = parseDailymotionApi(url)
        if (items.isEmpty()) {
            val fallbackUrl = "https://api.dailymotion.com/videos?fields=$API_FIELDS&sort=recent&limit=$limit&page=1"
            parseDailymotionApi(fallbackUrl)
        } else {
            items
        }
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()

        // Specialized prefixes
        if (cleanQuery.startsWith("dailymotion:playlist:", ignoreCase = true)) {
            val playlistId = cleanQuery.substringAfter("dailymotion:playlist:").trim()
            return@withContext getPlaylistVideos(playlistId, limit, page)
        }

        if (cleanQuery.startsWith("dailymotion:user:", ignoreCase = true)) {
            val username = cleanQuery.substringAfter("dailymotion:user:").trim()
            return@withContext getUserVideos(username, limit, page)
        }

        val rawQuery = if (cleanQuery.startsWith("dailymotion:search:", ignoreCase = true)) {
            cleanQuery.substringAfter("dailymotion:search:").trim()
        } else if (cleanQuery.startsWith("dailymotion:", ignoreCase = true)) {
            cleanQuery.substringAfter("dailymotion:").trim()
        } else {
            cleanQuery
        }

        val encoded = URLEncoder.encode(rawQuery, "UTF-8")
        val url = "https://api.dailymotion.com/videos?fields=$API_FIELDS&search=$encoded&limit=$limit&page=$page"
        parseDailymotionApi(url)
    }

    suspend fun getPlaylistVideos(playlistId: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.substringAfterLast("/").substringBefore("?")
        val url = "https://api.dailymotion.com/playlist/$cleanId/videos?fields=$API_FIELDS&limit=$limit&page=$page"
        parseDailymotionApi(url)
    }

    suspend fun getUserVideos(username: String, limit: Int = 20, page: Int = 1): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanUser = username.substringAfterLast("/").substringBefore("?").removePrefix("@")
        val url = "https://api.dailymotion.com/user/$cleanUser/videos?fields=$API_FIELDS&limit=$limit&page=$page"
        parseDailymotionApi(url)
    }

    private fun parseDailymotionApi(apiUrl: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(apiUrl)
                .headers(okhttp3.Headers.Builder().apply { dmHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val jsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val json = JSONObject(jsonStr)
            val listArr = json.optJSONArray("list") ?: return list
            for (i in 0 until listArr.length()) {
                val item = listArr.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                if (id.isBlank()) continue
                val title = item.optString("title", "Dailymotion Video")

                // Extract real channel display name and username
                val ownerScreenName = item.optString("owner.screenname").takeIf { it.isNotBlank() }
                    ?: item.optJSONObject("owner")?.optString("screenname")?.takeIf { it.isNotBlank() }
                    ?: item.optString("owner.username").takeIf { it.isNotBlank() }
                    ?: item.optJSONObject("owner")?.optString("username")?.takeIf { it.isNotBlank() }
                    ?: "Dailymotion Creator"

                val ownerUsername = item.optString("owner.username").takeIf { it.isNotBlank() }
                    ?: item.optJSONObject("owner")?.optString("username")?.takeIf { it.isNotBlank() }
                    ?: ownerScreenName.replace(" ", "").lowercase()

                // Extract real high-resolution channel logo
                val ownerAvatar = item.optString("owner.avatar_240_url").takeIf { it.isNotBlank() }
                    ?: item.optString("owner.avatar_120_url").takeIf { it.isNotBlank() }
                    ?: item.optString("owner.avatar_720_url").takeIf { it.isNotBlank() }
                    ?: item.optJSONObject("owner")?.optString("avatar_240_url")?.takeIf { it.isNotBlank() }
                    ?: item.optJSONObject("owner")?.optString("avatar_120_url")?.takeIf { it.isNotBlank() }
                    ?: item.optJSONObject("owner")?.optJSONObject("avatars")?.optString("120")?.takeIf { it.isNotBlank() }

                val thumb = item.optString("thumbnail_720_url").takeIf { it.isNotBlank() }
                    ?: item.optString("thumbnail_480_url").takeIf { it.isNotBlank() }
                    ?: "https://www.dailymotion.com/thumbnail/video/$id"

                val duration = item.optLong("duration", -1L)
                val views = item.optLong("views_total", -1L)

                list.add(
                    VideoItem(
                        id = "https://www.dailymotion.com/video/$id",
                        title = title,
                        uploaderName = ownerScreenName,
                        uploaderUrl = "https://www.dailymotion.com/$ownerUsername",
                        uploaderAvatarUrl = ownerAvatar,
                        durationSeconds = duration,
                        viewCount = views,
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        description = "Watch $title by $ownerScreenName on Dailymotion."
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion API parse error: ${e.message}")
        }
        return list
    }

    /**
     * Resolves complete stream information and all resolution options from Dailymotion.
     */
    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val videoId = when {
            cleanInput.startsWith("dailymotion:playlist:") || cleanInput.startsWith("dailymotion:user:") || cleanInput.startsWith("dailymotion:search:") -> {
                val items = search(cleanInput, limit = 1, page = 1)
                if (items.isNotEmpty()) {
                    items.first().id.substringAfter("/video/").substringBefore("?").substringBefore("_")
                } else ""
            }
            cleanInput.startsWith("dailymotion:") -> cleanInput.substringAfter("dailymotion:").substringBefore("_")
            cleanInput.contains("/video/") -> cleanInput.substringAfter("/video/").substringBefore("?").substringBefore("_")
            cleanInput.contains("dai.ly/") -> cleanInput.substringAfter("dai.ly/").substringBefore("?").substringBefore("_")
            cleanInput.startsWith("http") -> cleanInput.substringAfterLast("/").substringBefore("?").substringBefore("_")
            else -> cleanInput
        }

        if (videoId.isBlank()) return@withContext null

        // 1. Primary: Direct player metadata API
        try {
            val metadataUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
            val metaReq = Request.Builder()
                .url(metadataUrl)
                .headers(okhttp3.Headers.Builder().apply { dmHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val metaJsonStr = httpClient.newCall(metaReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!metaJsonStr.isNullOrBlank()) {
                val metaJson = JSONObject(metaJsonStr)
                val title = metaJson.optString("title", "Dailymotion Video")

                // Extract real owner / channel name and logo
                val ownerObj = metaJson.optJSONObject("owner")
                val channelName = ownerObj?.optString("screenname")?.takeIf { it.isNotBlank() }
                    ?: ownerObj?.optString("username")?.takeIf { it.isNotBlank() }
                    ?: "Dailymotion Channel"
                val channelHandle = ownerObj?.optString("username") ?: channelName

                val avatarsObj = ownerObj?.optJSONObject("avatars")
                val channelAvatar = avatarsObj?.optString("240")?.takeIf { it.isNotBlank() }
                    ?: avatarsObj?.optString("120")?.takeIf { it.isNotBlank() }
                    ?: avatarsObj?.optString("60")?.takeIf { it.isNotBlank() }
                    ?: ownerObj?.optString("avatar_240_url")?.takeIf { it.isNotBlank() }
                    ?: ownerObj?.optString("avatar_120_url")?.takeIf { it.isNotBlank() }

                val thumbsObj = metaJson.optJSONObject("thumbnails")
                val thumb = thumbsObj?.optString("720")?.takeIf { it.isNotBlank() }
                    ?: thumbsObj?.optString("480")?.takeIf { it.isNotBlank() }
                    ?: thumbsObj?.optString("360")?.takeIf { it.isNotBlank() }
                    ?: metaJson.optString("filmstrip_url")
                    ?: "https://www.dailymotion.com/thumbnail/video/$videoId"

                val duration = metaJson.optLong("duration", -1L)
                val isExplicit = metaJson.optBoolean("explicit", false)
                val tagsArr = metaJson.optJSONArray("tags")
                val tagsList = mutableListOf<String>()
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        tagsList.add(tagsArr.optString(t))
                    }
                }

                val options = mutableListOf<PlayableStreamOption>()
                val captions = mutableListOf<CaptionOption>()
                val qualitiesObj = metaJson.optJSONObject("qualities")

                var masterM3u8Url: String? = null
                if (qualitiesObj != null) {
                    val autoArr = qualitiesObj.optJSONArray("auto")
                    if (autoArr != null && autoArr.length() > 0) {
                        masterM3u8Url = autoArr.optJSONObject(0)?.optString("url", "")
                    }
                }

                // If master m3u8 is available, fetch and parse resolution variants & subtitles
                if (!masterM3u8Url.isNullOrBlank()) {
                    try {
                        val m3u8Req = Request.Builder()
                            .url(masterM3u8Url)
                            .headers(okhttp3.Headers.Builder().apply { dmHeaders.forEach { (k, v) -> add(k, v) } }.build())
                            .build()

                        val playlistText = httpClient.newCall(m3u8Req).execute().use { resp ->
                            if (resp.isSuccessful) resp.body?.string() else null
                        }

                        if (!playlistText.isNullOrBlank()) {
                            val lines = playlistText.lines()
                            for (i in lines.indices) {
                                val line = lines[i].trim()
                                if (line.contains("EXT-X-STREAM-INF")) {
                                    val nameMatch = Regex("NAME=\"([^\"]+)\"").find(line)
                                    val resMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(line)
                                    val qLabel = nameMatch?.groupValues?.get(1)?.let { "${it}p" }
                                        ?: resMatch?.groupValues?.get(1)?.substringAfter("x")?.let { "${it}p" }
                                        ?: "HD Stream"

                                    var streamUrl = lines.getOrNull(i + 1)?.trim()
                                    if (!streamUrl.isNullOrBlank()) {
                                        if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
                                            streamUrl = try {
                                                java.net.URI(masterM3u8Url).resolve(streamUrl).toString()
                                            } catch (_: Exception) {
                                                streamUrl
                                            }
                                        }
                                        // Use masterM3u8Url as primary source so ExoPlayer demuxes audio + video
                                        options.add(
                                            PlayableStreamOption(
                                                qualityLabel = qLabel,
                                                format = "m3u8",
                                                isMuxed = true,
                                                videoUrl = masterM3u8Url,
                                                providerType = ProviderType.DIRECT,
                                                headers = dmHeaders
                                            )
                                        )
                                    }
                                } else if (line.contains("EXT-X-MEDIA:TYPE=SUBTITLES")) {
                                    val nameMatch = Regex("NAME=\"([^\"]+)\"").find(line)
                                    val uriMatch = Regex("URI=\"([^\"]+)\"").find(line)
                                    val langMatch = Regex("(?:ASSOC-LANGUAGE|LANGUAGE)=\"([^\"]+)\"").find(line)
                                    val subName = nameMatch?.groupValues?.get(1) ?: "Subtitles"
                                    var subUri = uriMatch?.groupValues?.get(1)
                                    val subLang = langMatch?.groupValues?.get(1) ?: "en"

                                    if (!subUri.isNullOrBlank()) {
                                        if (!subUri.startsWith("http://") && !subUri.startsWith("https://")) {
                                            subUri = try {
                                                java.net.URI(masterM3u8Url).resolve(subUri).toString()
                                            } catch (_: Exception) { subUri }
                                        }
                                        captions.add(
                                            CaptionOption(
                                                languageName = subName,
                                                languageCode = subLang,
                                                format = "vtt",
                                                url = subUri
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Note: parsing Dailymotion child m3u8 playlists: ${e.message}")
                    }

                    // Always add Adaptive HLS (Auto) at top
                    val autoOption = PlayableStreamOption(
                        qualityLabel = "Adaptive HLS (Auto)",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = masterM3u8Url,
                        providerType = ProviderType.DIRECT,
                        headers = dmHeaders
                    )
                    options.add(0, autoOption)
                }

                // Fetch real related videos from Dailymotion
                val relatedVideos = mutableListOf<VideoItem>()
                try {
                    val relatedUrl = "https://api.dailymotion.com/video/$videoId/related?fields=$API_FIELDS&limit=12"
                    relatedVideos.addAll(parseDailymotionApi(relatedUrl))
                } catch (e: Exception) {
                    Log.w(TAG, "Note: fetching related videos: ${e.message}")
                }

                if (options.isNotEmpty()) {
                    val bestOption = options.first()
                    return@withContext StreamData(
                        videoId = videoId,
                        videoUrl = bestOption.videoUrl ?: "",
                        hlsUrl = masterM3u8Url,
                        title = title,
                        channelName = channelName,
                        channelAvatarUrl = channelAvatar,
                        subscriberCountText = "Verified Channel",
                        description = "Watch $title on Dailymotion.",
                        thumbnailUrl = thumb,
                        captionOptions = captions,
                        availableStreamOptions = options,
                        selectedStreamOption = bestOption,
                        relatedVideos = relatedVideos,
                        tags = tagsList,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        headers = dmHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion player metadata direct extraction failed: ${e.message}")
        }

        // 2. Secondary fallback: Embed page scraper
        try {
            val embedUrl = "https://www.dailymotion.com/embed/video/$videoId"
            val embedReq = Request.Builder()
                .url(embedUrl)
                .headers(okhttp3.Headers.Builder().apply { dmHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val embedHtml = httpClient.newCall(embedReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!embedHtml.isNullOrBlank()) {
                val m3u8Match = Regex("https://[^\"'\\s]+\\.m3u8[^\"'\\s]*").find(embedHtml)
                if (m3u8Match != null) {
                    val extractedM3u8 = m3u8Match.value.replace("\\/", "/")
                    val option = PlayableStreamOption(
                        qualityLabel = "Adaptive HLS (Auto)",
                        format = "m3u8",
                        isMuxed = true,
                        videoUrl = extractedM3u8,
                        providerType = ProviderType.DIRECT,
                        headers = dmHeaders
                    )
                    return@withContext StreamData(
                        videoId = videoId,
                        videoUrl = extractedM3u8,
                        hlsUrl = extractedM3u8,
                        title = "Dailymotion Video",
                        channelName = "Dailymotion Creator",
                        channelAvatarUrl = null,
                        thumbnailUrl = "https://www.dailymotion.com/thumbnail/video/$videoId",
                        availableStreamOptions = listOf(option),
                        selectedStreamOption = option,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        headers = dmHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion embed page fallback failed: ${e.message}")
        }

        // 3. Fall back to YtDlpResolver
        if (context != null) {
            try {
                Log.i(TAG, "Falling back to YtDlpResolver for Dailymotion video ID: $videoId")
                val targetUrl = if (cleanInput.startsWith("http")) cleanInput else "https://www.dailymotion.com/video/$videoId"
                val ytDlpRes = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytDlpRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytDlpRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = dmHeaders
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "YtDlpResolver Dailymotion fallback failed: ${e.message}")
            }
        }

        null
    }

    /**
     * Fetches detailed channel profile with real logo, banner, stats, and uploaded videos.
     */
    suspend fun fetchChannelDetails(channelNameOrUrl: String, fallbackAvatar: String? = null): ChannelDetails = withContext(Dispatchers.IO) {
        val trimmed = channelNameOrUrl.trim()
        val username = when {
            trimmed.contains("dailymotion.com/") -> trimmed.substringAfter("dailymotion.com/").substringBefore("/").substringBefore("?").removePrefix("@")
            trimmed.contains("dai.ly/") -> trimmed.substringAfter("dai.ly/").substringBefore("/").substringBefore("?").removePrefix("@")
            trimmed.startsWith("@") -> trimmed.removePrefix("@")
            else -> trimmed
        }

        var channelName = trimmed
        var channelHandle = "@$username"
        var avatarUrl = fallbackAvatar
        var bannerUrl: String? = null
        var subCountText = "Verified Channel"
        var videoCountText = "Videos"
        var description = "Official Dailymotion Channel"
        val videosList = mutableListOf<VideoItem>()

        try {
            // 1. Fetch User Profile
            val userApiUrl = "https://api.dailymotion.com/user/$username?fields=id,screenname,username,avatar_240_url,avatar_720_url,cover_url,description,videos_total,views_total,followers_total"
            val req = Request.Builder()
                .url(userApiUrl)
                .headers(okhttp3.Headers.Builder().apply { dmHeaders.forEach { (k, v) -> add(k, v) } }.build())
                .build()

            val userJsonStr = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!userJsonStr.isNullOrBlank()) {
                val userJson = JSONObject(userJsonStr)
                channelName = userJson.optString("screenname").takeIf { it.isNotBlank() } ?: userJson.optString("username", channelName)
                channelHandle = "@${userJson.optString("username", username)}"
                avatarUrl = userJson.optString("avatar_240_url").takeIf { it.isNotBlank() }
                    ?: userJson.optString("avatar_720_url").takeIf { it.isNotBlank() }
                    ?: avatarUrl
                bannerUrl = userJson.optString("cover_url").takeIf { it.isNotBlank() }

                val followers = userJson.optLong("followers_total", 0L)
                val views = userJson.optLong("views_total", 0L)
                val totalVideos = userJson.optLong("videos_total", 0L)

                subCountText = when {
                    followers >= 1_000_000 -> String.format("%.1fM followers", followers / 1_000_000.0)
                    followers >= 1_000 -> String.format("%.1fK followers", followers / 1_000.0)
                    followers > 0 -> "$followers followers"
                    else -> "Verified Creator"
                }

                if (views > 0) {
                    val viewsFormatted = when {
                        views >= 1_000_000 -> String.format("%.1fM views", views / 1_000_000.0)
                        views >= 1_000 -> String.format("%.1fK views", views / 1_000.0)
                        else -> "$views views"
                    }
                    subCountText = "$subCountText • $viewsFormatted"
                }

                videoCountText = "$totalVideos Videos"
                description = userJson.optString("description").replace("<br />", "\n").replace("<br/>", "\n").ifBlank {
                    "Official Dailymotion channel for $channelName."
                }
            }

            // 2. Fetch User Videos
            videosList.addAll(getUserVideos(username, limit = 30, page = 1))
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Dailymotion channel details for $username: ${e.message}")
        }

        ChannelDetails(
            channelId = username,
            name = channelName,
            handle = channelHandle,
            avatarUrl = avatarUrl,
            bannerUrl = bannerUrl,
            subscriberCount = subCountText,
            videoCount = videoCountText,
            description = description,
            isSubscribed = false,
            videos = videosList
        )
    }
}
