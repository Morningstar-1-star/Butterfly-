package com.example.plugin.providers

import android.util.Log
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Native Provider for Pornhub with real HLS stream extraction and full metadata parsing
 */
class PornhubProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "pornhub"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private val headers = mapOf(
        "User-Agent" to userAgent,
        "Referer" to "https://www.pornhub.com/",
        "Origin" to "https://www.pornhub.com",
        "Cookie" to "age_verified=1"
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://www.pornhub.com/webmasters/search?ordering=mostviewed&page=$page"
        fetchFromPornhubApi(url, page)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = "https://www.pornhub.com/webmasters/search?search=$encoded&page=$page"
        fetchFromPornhubApi(url, page)
    }

    private suspend fun fetchFromPornhubApi(url: String, page: Int): PagedResult<PluginVideoItem> {
        val resp = try { http.get(url, headers) } catch (e: Exception) { return PagedResult(emptyList()) }
        if (resp.statusCode != 200 || resp.body.isBlank()) return PagedResult(emptyList())

        return try {
            val json = JSONObject(resp.body)
            val videosArr = json.optJSONArray("videos") ?: JSONArray()
            val list = mutableListOf<PluginVideoItem>()

            for (i in 0 until videosArr.length()) {
                val item = videosArr.getJSONObject(i)
                val vUrl = item.optString("url")
                val vKey = item.optString("video_id").ifBlank {
                    Pattern.compile("viewkey=([a-zA-Z0-9]+)").matcher(vUrl).let {
                        if (it.find()) it.group(1) else ""
                    }
                }
                if (vKey.isBlank() && vUrl.isBlank()) continue

                val title = item.optString("title", "Pornhub Video")
                val thumb = item.optString("default_thumb").ifBlank { item.optString("thumb") }
                val durationStr = item.optString("duration")
                val durationSecs = parseDuration(durationStr)
                val views = item.optLong("views", 0L)

                val pornstarsArr = item.optJSONArray("pornstars")
                val pornstarsList = mutableListOf<String>()
                if (pornstarsArr != null) {
                    for (p in 0 until pornstarsArr.length()) {
                        val pObj = pornstarsArr.optJSONObject(p)
                        val pName = pObj?.optString("pornstar_name")
                        if (!pName.isNullOrBlank()) pornstarsList.add(pName)
                    }
                }

                val studioMatch = extractStudioFromTitle(title)
                val uploaderName = when {
                    studioMatch.isNotBlank() -> studioMatch
                    pornstarsList.isNotEmpty() -> pornstarsList.joinToString(", ")
                    else -> item.optJSONArray("tags")?.optJSONObject(0)?.optString("tag_name")
                        ?.replaceFirstChar { it.uppercase() } ?: "Pornhub Verified"
                }

                val finalUrl = if (vUrl.isNotBlank()) vUrl else "https://www.pornhub.com/view_video.php?viewkey=$vKey"

                list.add(
                    PluginVideoItem(
                        id = vKey.ifBlank { finalUrl },
                        title = title,
                        uploaderName = uploaderName,
                        viewCount = views,
                        durationSeconds = durationSecs,
                        thumbnailUrl = thumb,
                        providerId = providerId
                    )
                )
            }
            PagedResult(items = list, nextPageToken = (page + 1).toString(), hasMore = list.isNotEmpty())
        } catch (e: Exception) {
            PagedResult(emptyList())
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val vKey = extractViewkey(idOrUrl)
        val metaUrl = "https://www.pornhub.com/webmasters/video_by_id?id=$vKey&thumbsize=large"
        try {
            val resp = http.get(metaUrl, headers)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val root = JSONObject(resp.body)
                val videoObj = root.optJSONObject("video")
                if (videoObj != null) {
                    val title = videoObj.optString("title", "Pornhub Video")
                    val thumb = videoObj.optString("default_thumb").ifBlank { videoObj.optString("thumb") }
                    val views = videoObj.optLong("views", 0L)
                    val dur = parseDuration(videoObj.optString("duration"))

                    val pornstarsArr = videoObj.optJSONArray("pornstars")
                    val pornstarsList = mutableListOf<String>()
                    if (pornstarsArr != null) {
                        for (p in 0 until pornstarsArr.length()) {
                            val pObj = pornstarsArr.optJSONObject(p)
                            val pName = pObj?.optString("pornstar_name")
                            if (!pName.isNullOrBlank()) pornstarsList.add(pName)
                        }
                    }

                    val studio = extractStudioFromTitle(title)
                    val uploader = when {
                        studio.isNotBlank() -> studio
                        pornstarsList.isNotEmpty() -> pornstarsList.joinToString(", ")
                        else -> "Pornhub Verified"
                    }

                    return@withContext PluginVideoItem(
                        id = vKey,
                        title = title,
                        uploaderName = uploader,
                        viewCount = views,
                        durationSeconds = dur,
                        thumbnailUrl = thumb,
                        providerId = providerId
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("PornhubProvider", "getVideo error: ${e.message}")
        }

        PluginVideoItem(
            id = vKey,
            title = "Pornhub Video $vKey",
            uploaderName = "Pornhub",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val vKey = extractViewkey(idOrUrl)
        var title = "Pornhub Video $vKey"
        var channelName = "Pornhub"
        var channelAvatar: String? = null
        var viewCount = 0L
        var likeCount = 0L
        var uploadDate = ""
        var thumbUrl: String? = null
        var description = ""

        // 1. Fetch metadata from official webmasters API
        try {
            val metaUrl = "https://www.pornhub.com/webmasters/video_by_id?id=$vKey&thumbsize=large"
            val resp = http.get(metaUrl, headers)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val root = JSONObject(resp.body)
                val videoObj = root.optJSONObject("video")
                if (videoObj != null) {
                    title = videoObj.optString("title", title)
                    thumbUrl = videoObj.optString("default_thumb").ifBlank { videoObj.optString("thumb") }
                    viewCount = videoObj.optLong("views", 0L)
                    val rating = videoObj.optDouble("rating", 0.0)
                    val ratingsCount = videoObj.optLong("ratings", 0L)
                    if (ratingsCount > 0) {
                        likeCount = ((rating / 100.0) * ratingsCount).toLong()
                    }
                    uploadDate = videoObj.optString("publish_date", "")

                    val pornstarsArr = videoObj.optJSONArray("pornstars")
                    val pornstarsList = mutableListOf<String>()
                    if (pornstarsArr != null) {
                        for (p in 0 until pornstarsArr.length()) {
                            val pObj = pornstarsArr.optJSONObject(p)
                            val pName = pObj?.optString("pornstar_name")
                            if (!pName.isNullOrBlank()) pornstarsList.add(pName)
                        }
                    }

                    val tagsArr = videoObj.optJSONArray("tags")
                    val tagsList = mutableListOf<String>()
                    if (tagsArr != null) {
                        for (t in 0 until tagsArr.length()) {
                            val tObj = tagsArr.optJSONObject(t)
                            val tName = tObj?.optString("tag_name")
                            if (!tName.isNullOrBlank()) tagsList.add(tName)
                        }
                    }

                    val studio = extractStudioFromTitle(title)
                    channelName = when {
                        studio.isNotBlank() -> studio
                        pornstarsList.isNotEmpty() -> pornstarsList.joinToString(", ")
                        else -> "Pornhub Verified"
                    }

                    description = buildString {
                        if (pornstarsList.isNotEmpty()) append("Starring: ${pornstarsList.joinToString(", ")}\n")
                        if (studio.isNotBlank()) append("Studio / Channel: $studio\n")
                        if (tagsList.isNotEmpty()) append("Tags: ${tagsList.take(8).joinToString(" ") { "#${it.replace(" ", "")}" }}\n")
                        append("\nStream delivered via High-Speed Video Pipeline.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("PornhubProvider", "Metadata fetch failed: ${e.message}")
        }

        val streams = mutableListOf<PluginVideoStream>()
        var masterHlsUrl: String? = null

        // 2. Extract mediaDefinitions from embed page HTML
        try {
            val embedUrl = "https://www.pornhub.com/embed/$vKey"
            val embedResp = http.get(embedUrl, headers)
            if (embedResp.statusCode == 200 && embedResp.body.isNotBlank()) {
                val html = embedResp.body
                val mediaDefs = extractMediaDefinitions(html)

                for (md in mediaDefs) {
                    val format = md.optString("format")
                    val quality = md.optString("quality")
                    val videoUrl = md.optString("videoUrl")

                    if (videoUrl.isNotBlank() && !videoUrl.contains("get_media?")) {
                        if (format.equals("hls", ignoreCase = true) || videoUrl.contains(".m3u8")) {
                            if (masterHlsUrl == null) masterHlsUrl = videoUrl
                            val label = if (quality.isNotBlank() && quality != "[]") "${quality}p HLS" else "Auto Adaptive HLS"
                            streams.add(
                                PluginVideoStream(
                                    url = videoUrl,
                                    qualityLabel = label,
                                    format = "hls",
                                    isMuxed = true
                                )
                            )
                        } else if (format.equals("mp4", ignoreCase = true) || videoUrl.contains(".mp4")) {
                            val label = if (quality.isNotBlank() && quality != "[]") "${quality}p MP4" else "Direct MP4"
                            streams.add(
                                PluginVideoStream(
                                    url = videoUrl,
                                    qualityLabel = label,
                                    format = "mp4",
                                    isMuxed = true
                                )
                            )
                        }
                    }
                }

                // If master HLS was extracted, parse sub-qualities from the manifest
                if (masterHlsUrl != null) {
                    try {
                        val mResp = http.get(masterHlsUrl!!, headers)
                        if (mResp.statusCode == 200 && mResp.body.isNotBlank()) {
                            val baseHlsUri = masterHlsUrl!!.substringBeforeLast("/") + "/"
                            val lines = mResp.body.lines()
                            for (i in lines.indices) {
                                val line = lines[i].trim()
                                if (line.startsWith("#EXT-X-STREAM-INF")) {
                                    val resMatch = Pattern.compile("RESOLUTION=([0-9x]+)").matcher(line)
                                    val resLabel = if (resMatch.find()) {
                                        resMatch.group(1).split("x").lastOrNull()?.let { "${it}p" } ?: "HLS"
                                    } else "HLS"
                                    val nextLine = lines.getOrNull(i + 1)?.trim()
                                    if (!nextLine.isNullOrBlank()) {
                                        val fullSubUrl = if (nextLine.startsWith("http")) nextLine else baseHlsUri + nextLine
                                        if (streams.none { it.url == fullSubUrl }) {
                                            streams.add(
                                                PluginVideoStream(
                                                    url = fullSubUrl,
                                                    qualityLabel = "$resLabel Stream",
                                                    format = "hls",
                                                    isMuxed = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("PornhubProvider", "Manifest sub-stream parsing warning: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("PornhubProvider", "Embed scraping failed: ${e.message}")
        }

        // 3. Fallback to YtDlpResolver if streams still empty
        if (streams.isEmpty()) {
            val ctx = com.example.plugin.providers.ArchiveOrgProvider.contextRef
            if (ctx != null) {
                try {
                    val res = com.example.extractor.YtDlpResolver.extractStreamInfo(ctx, "https://www.pornhub.com/view_video.php?viewkey=$vKey")
                    if (res is com.example.extractor.YtDlpResolver.ExtractionResult.Success) {
                        res.playableOptions.forEach { opt ->
                            val vUrl = opt.videoUrl ?: opt.videoStream?.url
                            if (!vUrl.isNullOrBlank()) {
                                streams.add(
                                    PluginVideoStream(
                                        url = vUrl,
                                        qualityLabel = opt.qualityLabel,
                                        format = opt.format ?: "mp4",
                                        isMuxed = opt.isMuxed
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("PornhubProvider", "YtDlp fallback failed: ${e.message}")
                }
            }
        }

        if (streams.isEmpty()) {
            streams.add(
                PluginVideoStream(
                    url = "https://www.pornhub.com/embed/$vKey",
                    qualityLabel = "Pornhub Web Embed",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        val primaryPlayableUrl = streams.firstOrNull()?.url ?: "https://www.pornhub.com/embed/$vKey"

        PluginStreamInfo(
            id = vKey,
            url = primaryPlayableUrl,
            title = title,
            channelName = channelName,
            channelAvatarUrl = channelAvatar,
            viewCount = viewCount,
            likeCount = likeCount,
            uploadDate = uploadDate,
            thumbnailUrl = thumbUrl,
            description = description,
            hlsUrl = masterHlsUrl ?: streams.firstOrNull { it.format == "hls" }?.url,
            videoStreams = streams,
            httpHeaders = headers
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        val vKey = extractViewkey(idOrUrl)
        val comments = mutableListOf<PluginComment>()

        try {
            val viewUrl = "https://www.pornhub.com/view_video.php?viewkey=$vKey"
            val resp = http.get(viewUrl, headers)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val html = resp.body
                val commentBlockPattern = Pattern.compile("<div[^>]*class=[\"'][^\"']*commentBlock[^\"']*[\"'][^>]*>(.*?)</div>\\s*</div>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val matcher = commentBlockPattern.matcher(html)
                var idx = 0
                while (matcher.find() && idx < 25) {
                    val block = matcher.group(1) ?: continue
                    val authorM = Pattern.compile("<a[^>]*class=[\"'][^\"']*usernameLink[^\"']*[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE).matcher(block)
                    val textM = Pattern.compile("<div[^>]*class=[\"'][^\"']*commentMessage[^\"']*[\"'][^>]*><span>(.*?)</span>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(block)
                    val avatarM = Pattern.compile("<img[^>]*src=[\"']([^\"']+)[\"'][^>]*class=[\"'][^\"']*userAvatar[^\"']*[\"']", Pattern.CASE_INSENSITIVE).matcher(block)
                    val upvotesM = Pattern.compile("<span[^>]*class=[\"'][^\"']*voteTotal[^\"']*[\"'][^>]*>([0-9+-]+)</span>", Pattern.CASE_INSENSITIVE).matcher(block)

                    val author = if (authorM.find()) authorM.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: "User" else "User"
                    val text = if (textM.find()) textM.group(1)?.replace(Regex("<[^>]*>"), "")?.trim() ?: "" else ""
                    val avatar = if (avatarM.find()) avatarM.group(1) else null
                    val upvotes = if (upvotesM.find()) upvotesM.group(1)?.replace("+", "")?.toIntOrNull() ?: 0 else 0

                    if (text.isNotBlank()) {
                        comments.add(
                            PluginComment(
                                id = "ph_c_${vKey}_$idx",
                                authorName = author,
                                authorAvatarUrl = avatar,
                                content = text,
                                likeCount = upvotes.toLong(),
                                publishedTime = "Recently"
                            )
                        )
                        idx++
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("PornhubProvider", "Comments scraping failed: ${e.message}")
        }

        PagedResult(items = comments, hasMore = false)
    }

    private fun extractViewkey(idOrUrl: String): String {
        if (!idOrUrl.startsWith("http")) return idOrUrl
        val m = Pattern.compile("viewkey=([a-zA-Z0-9]+)").matcher(idOrUrl)
        if (m.find()) return m.group(1)
        val m2 = Pattern.compile("/embed/([a-zA-Z0-9]+)").matcher(idOrUrl)
        if (m2.find()) return m2.group(1)
        return idOrUrl
    }

    private fun extractStudioFromTitle(title: String): String {
        val t = title.trim()
        val studios = listOf(
            "SisLovesMe", "Sis Loves Me", "FamilyStrokes", "Family Strokes", "Bratty Sis", "BrattySis",
            "Step Siblings Caught", "StepSiblingsCaught", "Brazzers", "Reality Kings", "RealityKings",
            "Blacked", "Vixen", "Tushy", "DDF Network", "Naughty America", "NaughtyAmerica",
            "FakeHub", "Fake Hub", "Fake Taxi", "FakeTaxi", "Bang Bros", "BangBros", "Evil Angel",
            "Mofos", "Twistys", "Team Skeet", "TeamSkeet", "Digital Playground", "Pure Taboo",
            "Sweet Sinner", "PropertySex", "Adult Time", "Czech Hunters", "Nubiles"
        )
        for (studio in studios) {
            if (t.contains(studio, ignoreCase = true)) {
                return studio
            }
        }
        return ""
    }

    private fun extractMediaDefinitions(html: String): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        val tag = "\"mediaDefinitions\":"
        val idx = html.indexOf(tag)
        if (idx == -1) return list
        val start = html.indexOf('[', idx)
        if (start == -1) return list
        var depth = 0
        var inStr = false
        var escape = false
        var end = -1
        for (i in start until html.length) {
            val c = html[i]
            if (inStr) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inStr = false
            } else {
                if (c == '"') inStr = true
                else if (c == '[') depth++
                else if (c == ']') {
                    depth--
                    if (depth == 0) {
                        end = i + 1
                        break
                    }
                }
            }
        }
        if (end != -1) {
            try {
                val jsonArr = JSONArray(html.substring(start, end))
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.optJSONObject(i)
                    if (obj != null) list.add(obj)
                }
            } catch (e: Exception) {
                Log.w("PornhubProvider", "JSON parse mediaDefinitions failed: ${e.message}")
            }
        }
        return list
    }

    private fun parseDuration(durStr: String): Long {
        if (durStr.isBlank()) return 0L
        return try {
            val parts = durStr.split(":").map { it.trim().toLong() }
            when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}

/**
 * Native Provider for Redtube
 */
class RedtubeProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "redtube"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://api.redtube.com/?data=redtube.Videos.searchVideos&output=json&ordering=mostviewed&page=$page"
        fetchFromRedtubeApi(url, page)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = "https://api.redtube.com/?data=redtube.Videos.searchVideos&output=json&search=$encoded&page=$page"
        fetchFromRedtubeApi(url, page)
    }

    private suspend fun fetchFromRedtubeApi(url: String, page: Int): PagedResult<PluginVideoItem> {
        val resp = try { http.get(url) } catch (e: Exception) { return PagedResult(emptyList()) }
        if (resp.statusCode != 200 || resp.body.isBlank()) return PagedResult(emptyList())

        return try {
            val json = JSONObject(resp.body)
            val videosArr = json.optJSONArray("videos") ?: JSONArray()
            val list = mutableListOf<PluginVideoItem>()

            for (i in 0 until videosArr.length()) {
                val videoObj = videosArr.getJSONObject(i).optJSONObject("video") ?: videosArr.getJSONObject(i)
                val vId = videoObj.optString("video_id")
                val vUrl = videoObj.optString("url").ifBlank { "https://www.redtube.com/$vId" }
                val title = videoObj.optString("title", "Redtube Video $vId")
                val thumb = videoObj.optString("default_thumb").ifBlank { videoObj.optString("thumb") }
                val dur = videoObj.optString("duration").toDoubleOrNull()?.toLong() ?: 0L
                val views = videoObj.optLong("views", 0L)

                list.add(
                    PluginVideoItem(
                        id = vUrl,
                        title = title,
                        uploaderName = "Redtube Official",
                        viewCount = views,
                        durationSeconds = dur,
                        thumbnailUrl = thumb,
                        providerId = providerId
                    )
                )
            }
            PagedResult(items = list, nextPageToken = (page + 1).toString(), hasMore = list.isNotEmpty())
        } catch (e: Exception) {
            PagedResult(emptyList())
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val finalUrl = if (idOrUrl.startsWith("http")) idOrUrl else "https://www.redtube.com/$idOrUrl"
        PluginVideoItem(
            id = finalUrl,
            title = "Redtube Video",
            uploaderName = "Redtube",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val finalUrl = if (idOrUrl.startsWith("http")) idOrUrl else "https://www.redtube.com/$idOrUrl"
        val streams = mutableListOf<PluginVideoStream>()

        val ctx = com.example.plugin.providers.ArchiveOrgProvider.contextRef
        if (ctx != null) {
            try {
                val res = com.example.extractor.YtDlpResolver.extractStreamInfo(ctx, finalUrl)
                if (res is com.example.extractor.YtDlpResolver.ExtractionResult.Success) {
                    res.playableOptions.forEach { opt ->
                        val vUrl = opt.videoUrl ?: opt.videoStream?.url
                        if (!vUrl.isNullOrBlank()) {
                            streams.add(
                                PluginVideoStream(
                                    url = vUrl,
                                    qualityLabel = opt.qualityLabel,
                                    format = opt.format ?: "mp4",
                                    isMuxed = opt.isMuxed
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("RedtubeProvider", "Stream resolve failed: ${e.message}")
            }
        }

        if (streams.isEmpty()) {
            streams.add(PluginVideoStream(url = finalUrl, qualityLabel = "Auto", format = "embed"))
        }

        PluginStreamInfo(
            id = finalUrl,
            url = streams.firstOrNull()?.url ?: finalUrl,
            title = "Redtube Video",
            channelName = "Redtube",
            videoStreams = streams
        )
    }
}

/**
 * Native Provider for xHamster
 */
class XhamsterProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "xhamster"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://xhamster.com/$page"
        scrapeXhamsterHtml(url, page)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = "https://xhamster.com/search/$encoded?page=$page"
        scrapeXhamsterHtml(url, page)
    }

    private suspend fun scrapeXhamsterHtml(url: String, page: Int): PagedResult<PluginVideoItem> {
        val resp = try { http.get(url, mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")) } catch (e: Exception) { return PagedResult(emptyList()) }
        if (resp.statusCode != 200 || resp.body.isBlank()) return PagedResult(emptyList())

        return try {
            val html = resp.body
            val list = mutableListOf<PluginVideoItem>()

            val matcher = Pattern.compile("<a[^>]+href=\"(https://[a-z0-9.]*xhamster\\.com/videos/[^\"]+)\"[^>]*>.*?<img[^>]+src=\"([^\"]+)\"[^>]*alt=\"([^\"]+)\"", Pattern.DOTALL).matcher(html)
            while (matcher.find()) {
                val vUrl = matcher.group(1)
                val thumb = matcher.group(2)
                val title = matcher.group(3)

                if (vUrl.isNotBlank() && list.none { it.id == vUrl }) {
                    list.add(
                        PluginVideoItem(
                            id = vUrl,
                            title = title,
                            uploaderName = "xHamster Creator",
                            thumbnailUrl = thumb,
                            providerId = providerId
                        )
                    )
                }
            }

            PagedResult(items = list, nextPageToken = (page + 1).toString(), hasMore = list.isNotEmpty())
        } catch (e: Exception) {
            PagedResult(emptyList())
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val finalUrl = if (idOrUrl.startsWith("http")) idOrUrl else "https://xhamster.com/videos/$idOrUrl"
        PluginVideoItem(
            id = finalUrl,
            title = "xHamster Video",
            uploaderName = "xHamster",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val finalUrl = if (idOrUrl.startsWith("http")) idOrUrl else "https://xhamster.com/videos/$idOrUrl"
        val streams = mutableListOf<PluginVideoStream>()

        val ctx = com.example.plugin.providers.ArchiveOrgProvider.contextRef
        if (ctx != null) {
            try {
                val res = com.example.extractor.YtDlpResolver.extractStreamInfo(ctx, finalUrl)
                if (res is com.example.extractor.YtDlpResolver.ExtractionResult.Success) {
                    res.playableOptions.forEach { opt ->
                        val vUrl = opt.videoUrl ?: opt.videoStream?.url
                        if (!vUrl.isNullOrBlank()) {
                            streams.add(
                                PluginVideoStream(
                                    url = vUrl,
                                    qualityLabel = opt.qualityLabel,
                                    format = opt.format ?: "mp4",
                                    isMuxed = opt.isMuxed
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("XhamsterProvider", "Stream resolve failed: ${e.message}")
            }
        }

        if (streams.isEmpty()) {
            streams.add(PluginVideoStream(url = finalUrl, qualityLabel = "Auto", format = "embed"))
        }

        PluginStreamInfo(
            id = finalUrl,
            url = streams.firstOrNull()?.url ?: finalUrl,
            title = "xHamster Video",
            channelName = "xHamster",
            videoStreams = streams
        )
    }
}

