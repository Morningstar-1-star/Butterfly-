package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DailymotionProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "dailymotion"

    private val fields = "id,title,description,owner,owner.username,owner.screenname,owner.avatar_360_url,views_total,bookmarks_total,likes_total,created_time,thumbnail_720_url,duration"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://api.dailymotion.com/videos?fields=$fields&limit=20&page=$page&localization=en&languages=en&flags=featured,exportable&sort=visited"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, hasMore) = parseVideoList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = hasMore)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://api.dailymotion.com/videos?search=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=$fields&limit=20&page=$page&localization=en&languages=en"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, hasMore) = parseVideoList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = hasMore)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://api.dailymotion.com/video/$id?fields=$fields"
        val resp = http.get(url)
        val json = if (resp.statusCode == 200) JSONObject(resp.body) else JSONObject()
        val owner = json.optJSONObject("owner")
        val uploaderScreen = owner?.optString("screenname")?.takeIf { it.isNotBlank() }
        val uploaderUser = owner?.optString("username")?.takeIf { it.isNotBlank() }
        val realUploader = uploaderScreen ?: uploaderUser ?: "Dailymotion Creator"
        val realAvatar = owner?.optString("avatar_360_url")

        PluginVideoItem(
            id = json.optString("id", id),
            title = json.optString("title", "Dailymotion Video"),
            uploaderName = realUploader,
            uploaderAvatarUrl = realAvatar,
            viewCount = json.optLong("views_total", 0),
            durationSeconds = json.optLong("duration", 0),
            uploadDate = json.optLong("created_time", 0).toString(),
            thumbnailUrl = json.optString("thumbnail_720_url"),
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        var title = "Dailymotion Video $id"
        var description = "Dailymotion video stream"
        var channelName = "Dailymotion Creator"
        var channelAvatarUrl: String? = null
        var viewCount = 0L
        var likeCount = 0L
        var uploadDate = ""
        var thumbnailUrl: String? = null

        // 1. Fetch metadata from official API
        try {
            val metaUrl = "https://api.dailymotion.com/video/$id?fields=$fields"
            val respMeta = http.get(metaUrl)
            if (respMeta.statusCode == 200) {
                val jsonMeta = JSONObject(respMeta.body)
                title = jsonMeta.optString("title", title)
                description = jsonMeta.optString("description", description)
                val owner = jsonMeta.optJSONObject("owner")
                val uploaderScreen = owner?.optString("screenname")?.takeIf { it.isNotBlank() }
                val uploaderUser = owner?.optString("username")?.takeIf { it.isNotBlank() }
                if (uploaderScreen != null || uploaderUser != null) {
                    channelName = uploaderScreen ?: uploaderUser ?: "Dailymotion Creator"
                }
                channelAvatarUrl = owner?.optString("avatar_360_url")
                viewCount = jsonMeta.optLong("views_total", 0)
                likeCount = jsonMeta.optLong("likes_total", jsonMeta.optLong("bookmarks_total", 0))
                val cTime = jsonMeta.optLong("created_time", 0)
                if (cTime > 0) uploadDate = cTime.toString()
                thumbnailUrl = jsonMeta.optString("thumbnail_720_url").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            // Log/continue
        }

        val streams = mutableListOf<PluginVideoStream>()
        var hlsManifestUrl: String? = null

        // 2. Query player metadata JSON for direct HLS master stream URL
        try {
            val playerMetaUrl = "https://www.dailymotion.com/player/metadata/video/$id"
            val pMetaResp = http.get(
                url = playerMetaUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    "Referer" to "https://www.dailymotion.com/video/$id",
                    "Origin" to "https://www.dailymotion.com"
                )
            )
            if (pMetaResp.statusCode == 200) {
                val pMetaJson = JSONObject(pMetaResp.body)
                val metaTitle = pMetaJson.optString("title")
                if (metaTitle.isNotBlank()) title = metaTitle

                val ownerObj = pMetaJson.optJSONObject("owner")
                if (ownerObj != null) {
                    val sName = ownerObj.optString("screenname").takeIf { it.isNotBlank() }
                    val uName = ownerObj.optString("username").takeIf { it.isNotBlank() }
                    if (sName != null || uName != null) {
                        channelName = sName ?: uName ?: channelName
                    }
                    val avatars = ownerObj.optJSONObject("avatars")
                    val avUrl = avatars?.optString("360")?.takeIf { it.isNotBlank() }
                        ?: avatars?.optString("60")?.takeIf { it.isNotBlank() }
                        ?: ownerObj.optString("avatar_360_url").takeIf { it.isNotBlank() }
                    if (avUrl != null) channelAvatarUrl = avUrl
                }

                val qualitiesObj = pMetaJson.optJSONObject("qualities")
                val autoArr = qualitiesObj?.optJSONArray("auto")
                if (autoArr != null && autoArr.length() > 0) {
                    val m3u8 = autoArr.getJSONObject(0).optString("url")
                    if (m3u8.isNotBlank()) {
                        hlsManifestUrl = m3u8
                        streams.add(
                            PluginVideoStream(
                                url = m3u8,
                                qualityLabel = "Auto (Adaptive HLS)",
                                format = "hls",
                                isMuxed = true
                            )
                        )

                        // Also parse sub-qualities from the manifest for explicit resolution switching
                        try {
                            val manifestResp = http.get(
                                url = m3u8,
                                headers = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                                    "Referer" to "https://www.dailymotion.com/video/$id",
                                    "Origin" to "https://www.dailymotion.com"
                                )
                            )
                            if (manifestResp.statusCode == 200 && manifestResp.body.isNotBlank()) {
                                val lines = manifestResp.body.lines()
                                for (i in lines.indices) {
                                    val line = lines[i].trim()
                                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                                        val nameMatch = java.util.regex.Pattern.compile("NAME=\"([^\"]+)\"").matcher(line)
                                        val resMatch = java.util.regex.Pattern.compile("RESOLUTION=([0-9x]+)").matcher(line)
                                        val label = if (nameMatch.find()) {
                                            "${nameMatch.group(1)}p"
                                        } else if (resMatch.find()) {
                                            resMatch.group(1).split("x").lastOrNull()?.let { "${it}p" } ?: "HLS Stream"
                                        } else {
                                            "HLS Stream"
                                        }
                                        val nextUrl = lines.getOrNull(i + 1)?.trim()
                                        if (!nextUrl.isNullOrBlank() && nextUrl.startsWith("http")) {
                                            streams.add(
                                                PluginVideoStream(
                                                    url = nextUrl,
                                                    qualityLabel = label,
                                                    format = "hls",
                                                    isMuxed = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Sub-quality parsing warning
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Player metadata fetch warning
        }

        // 3. Fallback to YtDlpResolver if HLS is still missing
        if (streams.isEmpty()) {
            val ctx = com.example.plugin.providers.ArchiveOrgProvider.contextRef
            if (ctx != null) {
                try {
                    val fullUrl = "https://www.dailymotion.com/video/$id"
                    when (val ytRes = com.example.extractor.YtDlpResolver.extractStreamInfo(ctx, fullUrl)) {
                        is com.example.extractor.YtDlpResolver.ExtractionResult.Success -> {
                            for (opt in ytRes.playableOptions) {
                                val vUrl = opt.videoUrl ?: continue
                                streams.add(
                                    PluginVideoStream(
                                        url = vUrl,
                                        qualityLabel = opt.qualityLabel,
                                        format = opt.format ?: "hls",
                                        isMuxed = opt.isMuxed
                                    )
                                )
                            }
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    // YtDlp fallback warning
                }
            }
        }

        val primaryPlayableUrl = streams.firstOrNull()?.url ?: hlsManifestUrl ?: "https://www.dailymotion.com/embed/video/$id?autoplay=1"

        PluginStreamInfo(
            id = id,
            url = primaryPlayableUrl,
            title = title,
            channelName = channelName,
            channelAvatarUrl = channelAvatarUrl,
            viewCount = viewCount,
            likeCount = likeCount,
            uploadDate = uploadDate,
            thumbnailUrl = thumbnailUrl,
            description = description,
            hlsUrl = hlsManifestUrl ?: streams.firstOrNull { it.format == "hls" }?.url,
            videoStreams = streams,
            httpHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Referer" to "https://www.dailymotion.com/video/$id",
                "Origin" to "https://www.dailymotion.com"
            )
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://api.dailymotion.com/video/$id/comments?fields=id,message,owner.username,owner.avatar_360_url,created_time&limit=20&page=$page"
        val resp = http.get(url)
        val list = mutableListOf<PluginComment>()
        if (resp.statusCode == 200) {
            val json = JSONObject(resp.body)
            val listArr = json.optJSONArray("list") ?: JSONArray()
            for (i in 0 until listArr.length()) {
                val item = listArr.getJSONObject(i)
                val owner = item.optJSONObject("owner")
                list.add(
                    PluginComment(
                        id = item.optString("id"),
                        authorName = owner?.optString("username") ?: "User",
                        authorAvatarUrl = owner?.optString("avatar_360_url"),
                        content = item.optString("message"),
                        publishedTime = item.optLong("created_time", 0).toString()
                    )
                )
            }
        }
        PagedResult(items = list, nextPageToken = (page + 1).toString(), hasMore = list.isNotEmpty())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://api.dailymotion.com/video/$id/subtitles?fields=id,language,url"
        val resp = http.get(url)
        val list = mutableListOf<PluginSubtitle>()
        if (resp.statusCode == 200) {
            val json = JSONObject(resp.body)
            val listArr = json.optJSONArray("list") ?: JSONArray()
            for (i in 0 until listArr.length()) {
                val item = listArr.getJSONObject(i)
                val lang = item.optString("language", "en")
                list.add(
                    PluginSubtitle(
                        url = item.optString("url"),
                        languageCode = lang,
                        languageName = lang.uppercase(),
                        format = "vtt"
                    )
                )
            }
        }
        list
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        val username = extractId(channelIdOrUrl)
        val url = "https://api.dailymotion.com/user/$username?fields=id,username,avatar_360_url,description"
        val resp = http.get(url)
        val json = JSONObject(resp.body)
        PluginChannel(
            id = json.optString("id", username),
            name = json.optString("username", username),
            avatarUrl = json.optString("avatar_360_url"),
            description = json.optString("description")
        )
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        val id = extractId(playlistIdOrUrl)
        val url = "https://api.dailymotion.com/playlist/$id?fields=id,name,owner.username,videos_total"
        val resp = http.get(url)
        val json = JSONObject(resp.body)
        PluginPlaylist(
            id = json.optString("id", id),
            title = json.optString("name", "Dailymotion Playlist"),
            uploaderName = json.optJSONObject("owner")?.optString("username") ?: "Dailymotion",
            videoCount = json.optInt("videos_total", 0)
        )
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://api.dailymotion.com/video/$id/related?fields=$fields&limit=10"
        val resp = http.get(url)
        if (resp.statusCode == 200) parseVideoList(resp.body).first else emptyList()
    }

    private fun parseVideoList(jsonStr: String): Pair<List<PluginVideoItem>, Boolean> {
        val list = mutableListOf<PluginVideoItem>()
        val json = JSONObject(jsonStr)
        val listArr = json.optJSONArray("list") ?: JSONArray()
        val hasMore = json.optBoolean("has_more", false)

        for (i in 0 until listArr.length()) {
            val v = listArr.getJSONObject(i)
            val owner = v.optJSONObject("owner")
            list.add(
                PluginVideoItem(
                    id = v.optString("id"),
                    title = v.optString("title"),
                    uploaderName = owner?.optString("username") ?: "Dailymotion Creator",
                    uploaderAvatarUrl = owner?.optString("avatar_360_url"),
                    viewCount = v.optLong("views_total", 0),
                    durationSeconds = v.optLong("duration", 0),
                    uploadDate = v.optLong("created_time", 0).toString(),
                    thumbnailUrl = v.optString("thumbnail_720_url"),
                    providerId = providerId
                )
            )
        }
        return Pair(list, hasMore)
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}
