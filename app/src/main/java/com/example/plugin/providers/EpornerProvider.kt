package com.example.plugin.providers

import android.content.Context
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
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = false,
        supportsAnime = false,
        supportsTorrent = false,
        supportsSubtitles = false
    )

    override fun getProviderConfig(context: Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "Eporner HD",
            enabled = true,
            endpoint = "https://www.eporner.com",
            requiresApiKey = false,
            supportsDirectStreams = true,
            supportsWebView = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://www.eporner.com/api/v2/video/search/?query=all&per_page=30&page=$page&thumbsize=big&order=top-rated"
        fetchApiList(url, page)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val cleanQuery = query.trim().ifBlank { "all" }
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val url = "https://www.eporner.com/api/v2/video/search/?query=$encoded&per_page=30&page=$page&thumbsize=big&order=top-rated"
        fetchApiList(url, page)
    }

    private suspend fun fetchApiList(apiUrl: String, currentPage: Int): PagedResult<PluginVideoItem> {
        try {
            val resp = http.get(apiUrl)
            if (resp.statusCode != 200 || resp.body.isBlank()) return PagedResult(emptyList())

            val json = JSONObject(resp.body)
            val videosArr = json.optJSONArray("videos") ?: JSONArray()
            val totalPages = json.optInt("total_pages", 1)
            val items = mutableListOf<PluginVideoItem>()

            for (i in 0 until videosArr.length()) {
                val item = videosArr.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                if (id.isBlank()) continue

                val title = item.optString("title", "Eporner Video $id")
                val uploader = item.optString("uploader").ifBlank { item.optString("user").ifBlank { "Eporner Creator" } }
                val views = item.optLong("views", 0L)
                val lengthSec = item.optLong("length_sec", 0L)
                val added = item.optString("added")
                val thumbObj = item.optJSONObject("default_thumb")
                val thumbUrl = thumbObj?.optString("src") ?: item.optString("thumbnail", "")

                items.add(
                    PluginVideoItem(
                        id = id,
                        title = title,
                        uploaderName = uploader,
                        viewCount = views,
                        durationSeconds = lengthSec,
                        uploadDate = added,
                        thumbnailUrl = thumbUrl,
                        providerId = providerId
                    )
                )
            }

            return PagedResult(
                items = items,
                nextPageToken = (currentPage + 1).toString(),
                hasMore = currentPage < totalPages
            )
        } catch (e: Exception) {
            return PagedResult(emptyList())
        }
    }

    private fun extractVideoId(input: String): String {
        val trimmed = input.trim()
        val regexVideo = Regex("""video-([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
        val regexEmbed = Regex("""embed\/([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
        val regexId = Regex("""id=([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)

        regexVideo.find(trimmed)?.groupValues?.get(1)?.let { return it }
        regexEmbed.find(trimmed)?.groupValues?.get(1)?.let { return it }
        regexId.find(trimmed)?.groupValues?.get(1)?.let { return it }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val parts = trimmed.split("/").filter { it.isNotBlank() }
            for (part in parts.reversed()) {
                if (part.startsWith("video-")) return part.substringAfter("video-")
                if (part.all { it.isLetterOrDigit() } && part.length in 4..15) return part
            }
        }
        return trimmed
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(idOrUrl)
        val apiUrl = "https://www.eporner.com/api/v2/video/id/?id=$videoId&thumbsize=big"
        try {
            val resp = http.get(apiUrl)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val item = JSONObject(resp.body)
                val id = item.optString("id", videoId)
                val title = item.optString("title", "Eporner Video $id")
                val uploader = item.optString("uploader").ifBlank { item.optString("user").ifBlank { "Eporner Creator" } }
                val views = item.optLong("views", 0L)
                val lengthSec = item.optLong("length_sec", 0L)
                val added = item.optString("added")
                val thumbObj = item.optJSONObject("default_thumb")
                val thumbUrl = thumbObj?.optString("src") ?: item.optString("thumbnail", "")

                return@withContext PluginVideoItem(
                    id = id,
                    title = title,
                    uploaderName = uploader,
                    viewCount = views,
                    durationSeconds = lengthSec,
                    uploadDate = added,
                    thumbnailUrl = thumbUrl,
                    providerId = providerId
                )
            }
        } catch (_: Exception) {}

        PluginVideoItem(
            id = videoId,
            title = "Eporner Video $videoId",
            uploaderName = "Eporner Creator",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(idOrUrl)
        val videoStreams = mutableListOf<PluginVideoStream>()

        var title = "Eporner Video $videoId"
        var uploader = "Eporner Creator"
        var thumbUrl = "https://static-web.eporner.com/thumbs/static/$videoId.jpg"
        var description = "Eporner Direct H.264 MP4 Stream"

        // 1. Fetch metadata via API v2
        val apiUrl = "https://www.eporner.com/api/v2/video/id/?id=$videoId&thumbsize=big"
        try {
            val resp = http.get(apiUrl)
            if (resp.statusCode == 200 && resp.body.isNotBlank()) {
                val item = JSONObject(resp.body)
                title = item.optString("title", title)
                uploader = item.optString("uploader").ifBlank { item.optString("user").ifBlank { uploader } }
                val thumbObj = item.optJSONObject("default_thumb")
                thumbUrl = thumbObj?.optString("src") ?: item.optString("thumbnail", thumbUrl)
                description = "Uploaded by $uploader • ${item.optString("keywords", "HD Video")}"

                val embedUrl = item.optString("embed")
                if (embedUrl.isNotBlank()) {
                    videoStreams.add(
                        PluginVideoStream(
                            url = embedUrl,
                            qualityLabel = "Web Player Embed",
                            format = "embed",
                            isMuxed = true
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // 2. Extract direct MP4 stream links from Eporner page / download endpoints
        try {
            val pageUrl = "https://www.eporner.com/video-$videoId/"
            val pageResp = http.get(pageUrl)
            if (pageResp.statusCode == 200 && pageResp.body.isNotBlank()) {
                val body = pageResp.body

                // Find MP4 download links or video source tags
                val mp4Regex = Regex("""href=["']([^"']+dload[^"']+\.mp4)["']""", RegexOption.IGNORE_CASE)
                val matches = mp4Regex.findAll(body)
                for (match in matches) {
                    var streamUrl = match.groupValues[1]
                    if (streamUrl.startsWith("//")) streamUrl = "https:$streamUrl"
                    else if (streamUrl.startsWith("/")) streamUrl = "https://www.eporner.com$streamUrl"

                    val qualityLabel = when {
                        streamUrl.contains("1080p", ignoreCase = true) -> "H.264 MP4 (1080p)"
                        streamUrl.contains("720p", ignoreCase = true) -> "H.264 MP4 (720p)"
                        streamUrl.contains("480p", ignoreCase = true) -> "H.264 MP4 (480p)"
                        else -> "H.264 Direct MP4"
                    }

                    if (videoStreams.none { it.url == streamUrl }) {
                        videoStreams.add(
                            0,
                            PluginVideoStream(
                                url = streamUrl,
                                qualityLabel = qualityLabel,
                                format = "mp4",
                                isMuxed = true
                            )
                        )
                    }
                }

                // Also check direct <source src="..." or <video src="..."
                val srcRegex = Regex("""<(?:source|video)[^>]+src=["']([^"']+\.mp4(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE)
                srcRegex.findAll(body).forEach { sMatch ->
                    var sUrl = sMatch.groupValues[1]
                    if (sUrl.startsWith("//")) sUrl = "https:$sUrl"
                    else if (sUrl.startsWith("/")) sUrl = "https://www.eporner.com$sUrl"

                    if (videoStreams.none { it.url == sUrl }) {
                        videoStreams.add(
                            0,
                            PluginVideoStream(
                                url = sUrl,
                                qualityLabel = "Direct MP4 Stream (HD)",
                                format = "mp4",
                                isMuxed = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback direct embed URL if no streams added yet
        if (videoStreams.isEmpty()) {
            videoStreams.add(
                PluginVideoStream(
                    url = "https://www.eporner.com/embed/$videoId/",
                    qualityLabel = "Web Player Embed",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        val epornerHeaders = mapOf(
            "Referer" to "https://www.eporner.com/",
            "Origin" to "https://www.eporner.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        )

        PluginStreamInfo(
            id = videoId,
            url = "https://www.eporner.com/video-$videoId/",
            title = title,
            channelName = uploader,
            description = description,
            thumbnailUrl = thumbUrl,
            httpHeaders = epornerHeaders,
            videoStreams = videoStreams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> {
        return PagedResult(emptyList())
    }
}
