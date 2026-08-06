package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ApiJavHentaiProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "apijav_hentai"
    private val baseUrl = "https://hentai.apijav.com/wp-json/myvideo/v1"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "$baseUrl/posts?per_page=20&page=$page"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, hasMore) = parsePostsList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = hasMore)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "$baseUrl/posts?search=${java.net.URLEncoder.encode(query, "UTF-8")}&per_page=20&page=$page"
        val resp = http.get(url)
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, hasMore) = parsePostsList(resp.body)
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = hasMore)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        val url = "$baseUrl/posts/$id"
        val resp = http.get(url)
        if (resp.statusCode == 200) {
            val json = JSONObject(resp.body)
            val studio = json.optString("studio").ifEmpty { "APIJAV Hentai" }
            return@withContext PluginVideoItem(
                id = json.optLong("id").toString().ifEmpty { id },
                title = json.optString("title", "APIJAV Hentai Video"),
                uploaderName = studio,
                uploaderAvatarUrl = null,
                viewCount = json.optLong("views", 0),
                durationSeconds = parseDurationSeconds(json.optString("duration")),
                uploadDate = json.optString("date"),
                thumbnailUrl = json.optString("thumbnail"),
                providerId = providerId
            )
        }
        PluginVideoItem(
            id = id,
            title = "APIJAV Hentai #$id",
            uploaderName = "APIJAV Hentai",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val id = extractId(idOrUrl)
        var title = "APIJAV Hentai"
        var studio = "APIJAV Hentai"
        var views = 0L
        var likes = 0L
        var embedUrl = ""
        var iframeHtml = ""

        val postResp = http.get("$baseUrl/posts/$id")
        if (postResp.statusCode == 200) {
            val json = JSONObject(postResp.body)
            title = json.optString("title", title)
            studio = json.optString("studio").ifEmpty { studio }
            views = json.optLong("views", 0)
            likes = json.optLong("likes", 0)
            embedUrl = json.optString("embed_url")
            iframeHtml = json.optString("iframe_html")
        }

        if (embedUrl.isEmpty() || embedUrl.contains("wp-json")) {
            val playerResp = http.get("$baseUrl/player/$id")
            if (playerResp.statusCode == 200) {
                val playerJson = JSONObject(playerResp.body)
                val pEmbed = playerJson.optString("embed_url")
                if (pEmbed.isNotEmpty() && !pEmbed.contains("wp-json")) {
                    embedUrl = pEmbed
                }
                if (iframeHtml.isEmpty()) {
                    iframeHtml = playerJson.optString("iframe_html")
                }
            }
        }

        val iframeSrc = extractIframeSrc(iframeHtml)
        if (!iframeSrc.isNullOrEmpty()) {
            embedUrl = iframeSrc
        }

        embedUrl = sanitizeUrl(embedUrl)

        if (embedUrl.isEmpty()) {
            embedUrl = "https://hentai.apijav.com/?mvembed=1&id=$id"
        }

        PluginStreamInfo(
            id = id,
            url = embedUrl,
            title = title,
            channelName = studio,
            viewCount = views,
            likeCount = likes,
            description = iframeHtml.ifEmpty { "Embedded stream from APIJAV Hentai ($studio)" },
            hlsUrl = null,
            videoStreams = listOf(
                PluginVideoStream(
                    url = embedUrl,
                    qualityLabel = "Web Embed / Auto Play",
                    format = "embed",
                    isMuxed = true
                )
            )
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> {
        return PagedResult(emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> {
        return emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel {
        return PluginChannel(id = channelIdOrUrl, name = "APIJAV Hentai")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist {
        return PluginPlaylist(id = playlistIdOrUrl, title = "APIJAV Hentai Playlist", uploaderName = "APIJAV Hentai")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        val resp = http.get("$baseUrl/posts?per_page=10&orderby=views")
        if (resp.statusCode == 200) parsePostsList(resp.body).first else emptyList()
    }

    private fun parsePostsList(jsonStr: String): Pair<List<PluginVideoItem>, Boolean> {
        val list = mutableListOf<PluginVideoItem>()
        return try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val json = arr.getJSONObject(i)
                val studio = json.optString("studio").ifEmpty { "Hentai" }
                val code = json.optString("code")
                val displayTitle = if (code.isNotEmpty() && !json.optString("title").contains(code)) {
                    "[$code] ${json.optString("title")}"
                } else {
                    json.optString("title")
                }

                list.add(
                    PluginVideoItem(
                        id = json.optLong("id").toString(),
                        title = displayTitle,
                        uploaderName = studio,
                        uploaderAvatarUrl = null,
                        viewCount = json.optLong("views", 0),
                        durationSeconds = parseDurationSeconds(json.optString("duration")),
                        uploadDate = json.optString("date"),
                        thumbnailUrl = json.optString("thumbnail"),
                        providerId = providerId
                    )
                )
            }
            Pair(list, arr.length() >= 20)
        } catch (e: Exception) {
            Pair(emptyList(), false)
        }
    }

    private fun parseDurationSeconds(durationStr: String): Long {
        if (durationStr.isBlank()) return 0
        return try {
            val parts = durationStr.split(":").map { it.trim().toLong() }
            when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun extractIframeSrc(html: String): String? {
        val src = if (html.contains("src=\"")) {
            html.substringAfter("src=\"").substringBefore("\"")
        } else if (html.contains("src='")) {
            html.substringAfter("src='").substringBefore("'")
        } else {
            null
        }
        return if (src != null) sanitizeUrl(src) else null
    }

    private fun sanitizeUrl(url: String): String {
        return url.replace("&#038;", "&")
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "")
            .trim()
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}
