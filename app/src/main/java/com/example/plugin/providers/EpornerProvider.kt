package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class EpornerProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "eporner"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://www.eporner.com/api/v2/video/search/?per_page=20&page=$page&thumbsize=big&order=latest&format=json"
        fetchVideos(url, page)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val encodedQuery = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val url = "https://www.eporner.com/api/v2/video/search/?query=$encodedQuery&per_page=20&page=$page&thumbsize=big&order=top-monthly&format=json"
        fetchVideos(url, page)
    }

    private suspend fun fetchVideos(url: String, currentPage: Int): PagedResult<PluginVideoItem> {
        val resp = http.get(url)
        if (resp.statusCode != 200) return PagedResult(emptyList())

        return try {
            val json = JSONObject(resp.body)
            val videosArr = json.optJSONArray("videos") ?: JSONArray()
            val list = mutableListOf<PluginVideoItem>()

            for (i in 0 until videosArr.length()) {
                val item = videosArr.getJSONObject(i)
                val id = item.optString("id")
                val title = item.optString("title", "Video $id")
                val thumbObj = item.optJSONObject("default_thumb")
                val thumbUrl = thumbObj?.optString("src") ?: item.optString("default_thumb")
                val duration = item.optLong("length_sec", 0L)
                val views = item.optLong("views", 0L)

                if (id.isNotBlank()) {
                    list.add(
                        PluginVideoItem(
                            id = id,
                            title = title,
                            uploaderName = "Eporner",
                            viewCount = views,
                            durationSeconds = duration,
                            thumbnailUrl = thumbUrl,
                            providerId = providerId
                        )
                    )
                }
            }
            val hasMore = list.isNotEmpty() && currentPage < json.optInt("total_pages", 100)
            PagedResult(items = list, nextPageToken = (currentPage + 1).toString(), hasMore = hasMore)
        } catch (e: Exception) {
            PagedResult(emptyList())
        }
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "https://www.eporner.com/api/v2/video/id/?id=$id&format=json"
        val resp = http.get(url)
        if (resp.statusCode == 200) {
            try {
                val json = JSONObject(resp.body)
                val thumbObj = json.optJSONObject("default_thumb")
                val thumbUrl = thumbObj?.optString("src") ?: json.optString("default_thumb")
                return@withContext PluginVideoItem(
                    id = id,
                    title = json.optString("title", "Eporner Video"),
                    uploaderName = "Eporner",
                    viewCount = json.optLong("views", 0L),
                    durationSeconds = json.optLong("length_sec", 0L),
                    thumbnailUrl = thumbUrl,
                    providerId = providerId
                )
            } catch (e: Exception) {
                // Fallback
            }
        }
        PluginVideoItem(
            id = id,
            title = "Eporner Video $id",
            uploaderName = "Eporner",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val embedUrl = "https://www.eporner.com/embed/$id/"

        // Attempt API lookup to see if direct streams exist
        val apiUrl = "https://www.eporner.com/api/v2/video/id/?id=$id&format=json"
        val resp = http.get(apiUrl)
        val videoStreams = mutableListOf<PluginVideoStream>()

        if (resp.statusCode == 200) {
            try {
                val json = JSONObject(resp.body)
                val embed = json.optString("embed", embedUrl)
                videoStreams.add(
                    PluginVideoStream(
                        url = if (embed.isNotBlank()) embed else embedUrl,
                        qualityLabel = "Embed Player (Auto)",
                        format = "embed",
                        isMuxed = true
                    )
                )
            } catch (e: Exception) {
                videoStreams.add(
                    PluginVideoStream(
                        url = embedUrl,
                        qualityLabel = "Embed Stream",
                        format = "embed",
                        isMuxed = true
                    )
                )
            }
        } else {
            videoStreams.add(
                PluginVideoStream(
                    url = embedUrl,
                    qualityLabel = "Embed Stream",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        PluginStreamInfo(
            id = id,
            url = embedUrl,
            title = "Eporner Stream $id",
            channelName = "Eporner",
            description = "Eporner Video Player",
            videoStreams = videoStreams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> =
        PagedResult(emptyList())

    private fun extractId(input: String): String {
        val clean = input.trim()
        if (clean.contains("eporner.com/video-")) {
            val after = clean.substringAfter("eporner.com/video-")
            return after.substringBefore("/")
        }
        if (clean.contains("eporner.com/embed/")) {
            val after = clean.substringAfter("eporner.com/embed/")
            return after.substringBefore("/")
        }
        return clean
    }
}
