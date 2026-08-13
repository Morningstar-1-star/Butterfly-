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
 * Native Provider for Pornhub
 */
class PornhubProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "pornhub"

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
        val resp = try { http.get(url) } catch (e: Exception) { return PagedResult(emptyList()) }
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
                val thumb = item.optString("default_thumb")
                val durationStr = item.optString("duration")
                val durationSecs = parseDuration(durationStr)
                val views = item.optLong("views", 0L)
                val uploader = item.optJSONArray("tags")?.optString(0) ?: "Pornhub Verified"

                val finalUrl = if (vUrl.isNotBlank()) vUrl else "https://www.pornhub.com/view_video.php?viewkey=$vKey"

                list.add(
                    PluginVideoItem(
                        id = finalUrl,
                        title = title,
                        uploaderName = "Pornhub • $uploader",
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
        val finalUrl = if (idOrUrl.startsWith("http")) idOrUrl else "https://www.pornhub.com/view_video.php?viewkey=$idOrUrl"
        PluginVideoItem(
            id = finalUrl,
            title = "Pornhub Video",
            uploaderName = "Pornhub",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val finalUrl = if (idOrUrl.startsWith("http")) idOrUrl else "https://www.pornhub.com/view_video.php?viewkey=$idOrUrl"
        PluginStreamInfo(
            id = finalUrl,
            url = finalUrl,
            title = "Pornhub Video",
            channelName = "Pornhub",
            videoStreams = listOf(PluginVideoStream(url = finalUrl, qualityLabel = "Auto", format = "mp4"))
        )
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
                        uploaderName = "Redtube",
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
        PluginStreamInfo(
            id = finalUrl,
            url = finalUrl,
            title = "Redtube Video",
            channelName = "Redtube",
            videoStreams = listOf(PluginVideoStream(url = finalUrl, qualityLabel = "Auto", format = "mp4"))
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
        val resp = try { http.get(url) } catch (e: Exception) { return PagedResult(emptyList()) }
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
                            uploaderName = "xHamster",
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
        PluginStreamInfo(
            id = finalUrl,
            url = finalUrl,
            title = "xHamster Video",
            channelName = "xHamster",
            videoStreams = listOf(PluginVideoStream(url = finalUrl, qualityLabel = "Auto", format = "mp4"))
        )
    }
}
