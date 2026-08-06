package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class NyaaAnimeProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    companion object {
        const val BASE_URL = "https://nyaa.si"
    }

    override val providerId: String = "nyaa_si"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        // Fetch Jikan Top Anime to power Nyaa search items with rich posters
        val url = "https://api.jikan.moe/v4/top/anime?page=$page&limit=20"
        val resp = try { http.get(url) } catch (e: Exception) { return@withContext PagedResult(emptyList()) }
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, hasNext) = parseJikanList(resp.body)
        PagedResult(
            items = items,
            nextPageToken = if (hasNext) (page + 1).toString() else null,
            hasMore = hasNext
        )
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api.jikan.moe/v4/anime?q=$encodedQuery&page=$page&limit=20"
        val resp = try { http.get(url) } catch (e: Exception) { return@withContext PagedResult(emptyList()) }
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, hasNext) = parseJikanList(resp.body)
        PagedResult(
            items = items,
            nextPageToken = if (hasNext) (page + 1).toString() else null,
            hasMore = hasNext
        )
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val malId = extractId(idOrUrl)
        PluginVideoItem(
            id = malId,
            title = "Nyaa.si Anime $malId",
            uploaderName = "Nyaa.si Anime Torrents",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val malId = extractId(idOrUrl)
        val videoStreams = mutableListOf<PluginVideoStream>()

        // 1. VidSrc Anime Embed Stream
        val vidsrcAnimeUrl = "https://vidsrc.to/embed/anime/$malId"
        videoStreams.add(
            PluginVideoStream(
                url = vidsrcAnimeUrl,
                qualityLabel = "VidSrc Anime HD Embed",
                format = "embed",
                isMuxed = true
            )
        )

        // 2. Query Torrentio / Nyaa Stremio Addon Engine
        val torrentioAnimeUrl = "https://torrentio.strem.fun/stream/anime/kitsu:$malId.json"
        try {
            val tResp = http.get(torrentioAnimeUrl)
            if (tResp.statusCode == 200) {
                val tJson = JSONObject(tResp.body)
                val streamArr = tJson.optJSONArray("streams") ?: JSONArray()
                for (i in 0 until minOf(streamArr.length(), 6)) {
                    val st = streamArr.getJSONObject(i)
                    val streamTitle = st.optString("title", "Nyaa Stream ${i + 1}")
                    val name = st.optString("name", "Nyaa.si")
                    val stUrl = st.optString("url")
                    val magnet = st.optString("infoHash")

                    if (stUrl.isNotEmpty()) {
                        videoStreams.add(
                            PluginVideoStream(
                                url = stUrl,
                                qualityLabel = "$name: ${streamTitle.take(35)}",
                                format = if (stUrl.contains(".m3u8")) "hls" else "mp4",
                                isMuxed = true
                            )
                        )
                    } else if (magnet.isNotEmpty()) {
                        val magnetUrl = com.example.utils.TorrentUtils.formatMagnetUrl(magnet, name)
                        val cleanTitle = name.replace("\n", " ").replace("\r", " ").take(40)
                        videoStreams.add(
                            PluginVideoStream(
                                url = magnetUrl,
                                qualityLabel = "Nyaa Torrent - $cleanTitle",
                                format = "embed",
                                isMuxed = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        PluginStreamInfo(
            id = malId,
            url = videoStreams.firstOrNull()?.url ?: vidsrcAnimeUrl,
            title = "Nyaa.si Anime Stream",
            channelName = "Nyaa.si Anime Indexer",
            videoStreams = videoStreams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = "nyaa", name = "Nyaa.si Anime")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = "nyaa", title = "Nyaa.si Anime Torrents", uploaderName = "Nyaa")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private fun parseJikanList(jsonStr: String): Pair<List<PluginVideoItem>, Boolean> {
        val list = mutableListOf<PluginVideoItem>()
        val json = JSONObject(jsonStr)
        val pagination = json.optJSONObject("pagination")
        val hasNext = pagination?.optBoolean("has_next_page", false) ?: false
        val data = json.optJSONArray("data") ?: JSONArray()

        for (i in 0 until data.length()) {
            val anime = data.getJSONObject(i)
            val malId = anime.optInt("mal_id", 0)
            if (malId == 0) continue

            val title = anime.optString("title", "Anime")
            val images = anime.optJSONObject("images")?.optJSONObject("jpg")
            val poster = images?.optString("large_image_url") ?: images?.optString("image_url")

            list.add(
                PluginVideoItem(
                    id = "$malId",
                    title = title,
                    uploaderName = "Nyaa.si Anime Release",
                    thumbnailUrl = poster,
                    providerId = providerId
                )
            )
        }
        return Pair(list, hasNext)
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}
