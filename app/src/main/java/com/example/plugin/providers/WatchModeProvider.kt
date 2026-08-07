package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WatchModeProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    companion object {
        const val API_KEY = "wm_xmIVUHIMCDvwjKcY1-fSO0WKfw9H6vtM20LmcbACXww"
        const val BASE_URL = "https://api.watchmode.com/v1"
        const val TMDB_API_KEY = "3155fdb497f7575a144f26adebcbf980"
        const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
    }

    override val providerId: String = "watchmode"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "$BASE_URL/list-titles/?apiKey=$API_KEY&limit=25&page=$page"
        val resp = try { http.get(url) } catch (e: Exception) { return@withContext PagedResult(emptyList()) }
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, totalPages) = parseWatchModeList(resp.body)
        PagedResult(
            items = items,
            nextPageToken = if (page < totalPages) (page + 1).toString() else null,
            hasMore = page < totalPages
        )
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$BASE_URL/search/?apiKey=$API_KEY&search_field=name&search_value=$encodedQuery"
        val resp = try { http.get(url) } catch (e: Exception) { return@withContext PagedResult(emptyList()) }
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val items = parseWatchModeSearchResults(resp.body)
        PagedResult(
            items = items,
            nextPageToken = null,
            hasMore = false
        )
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val cleanId = extractId(idOrUrl)
        val url = "$BASE_URL/title/$cleanId/details/?apiKey=$API_KEY"

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val title = json.optString("title", "WatchMode Title")
                val year = json.optInt("year", 2026)
                val type = json.optString("type", "movie")
                val poster = json.optString("poster", "")
                val userRating = json.optDouble("user_rating", 8.0)

                return@withContext PluginVideoItem(
                    id = cleanId,
                    title = title,
                    uploaderName = "WatchMode ($type)",
                    uploadDate = "★ $userRating • $year",
                    thumbnailUrl = poster.ifEmpty { null },
                    providerId = providerId
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        PluginVideoItem(
            id = cleanId,
            title = "WatchMode Title $cleanId",
            uploaderName = "WatchMode Streaming",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val cleanId = extractId(idOrUrl)
        val url = "$BASE_URL/title/$cleanId/details/?apiKey=$API_KEY&append_to_response=sources"

        var title = "Stream"
        var overview = ""
        var tmdbId = ""
        var imdbId = ""
        var isTv = false

        val streams = mutableListOf<PluginVideoStream>()

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                title = json.optString("title", title)
                overview = json.optString("plot_overview", "")
                tmdbId = json.optString("tmdb_id", "")
                imdbId = json.optString("imdb_id", "")
                val type = json.optString("type", "movie")
                isTv = type.contains("tv")

                // Extract WatchMode sources
                val sourcesArr = json.optJSONArray("sources") ?: JSONArray()
                for (i in 0 until sourcesArr.length()) {
                    val s = sourcesArr.getJSONObject(i)
                    val sName = s.optString("name", "Streaming Provider")
                    val webUrl = s.optString("web_url", "")
                    val format = s.optString("format", "HD")

                    if (webUrl.isNotEmpty()) {
                        streams.add(
                            PluginVideoStream(
                                url = webUrl,
                                qualityLabel = "$format • $sName",
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

        // Add TMDB & VidSrc embed fallbacks if tmdbId is present or cleanId is TMDB ID
        val effectiveTmdbId = if (tmdbId.isNotEmpty()) tmdbId else cleanId
        if (effectiveTmdbId.isNotEmpty() && effectiveTmdbId.all { it.isDigit() }) {
            val vidsrcUrl = if (isTv) "https://vidsrc.to/embed/tv/$effectiveTmdbId/1/1" else "https://vidsrc.to/embed/movie/$effectiveTmdbId"
            streams.add(
                PluginVideoStream(
                    url = vidsrcUrl,
                    qualityLabel = "1080p • VidSrc Pro",
                    format = "embed",
                    isMuxed = true
                )
            )

            val superEmbedUrl = if (isTv) "https://multitembed.com/direct.php?video_id=$effectiveTmdbId&s=1&e=1" else "https://multitembed.com/direct.php?video_id=$effectiveTmdbId"
            streams.add(
                PluginVideoStream(
                    url = superEmbedUrl,
                    qualityLabel = "HD • SuperEmbed",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        // If IMDB ID is present, fetch Torrentio streams
        if (imdbId.isNotEmpty() && imdbId.startsWith("tt")) {
            val torrentioUrl = if (isTv) {
                "https://torrentio.strem.fun/stream/series/$imdbId:1:1.json"
            } else {
                "https://torrentio.strem.fun/stream/movie/$imdbId.json"
            }
            try {
                val tResp = http.get(torrentioUrl)
                if (tResp.statusCode == 200) {
                    val tJson = JSONObject(tResp.body)
                    val streamArr = tJson.optJSONArray("streams") ?: JSONArray()
                    for (i in 0 until minOf(streamArr.length(), 6)) {
                        val st = streamArr.getJSONObject(i)
                        val streamTitle = st.optString("title", "Torrent Stream")
                        val name = st.optString("name", "Torrentio")
                        val stUrl = st.optString("url")
                        val magnet = st.optString("infoHash")

                        if (stUrl.isNotEmpty()) {
                            val cleanLabel = com.example.utils.TorrentUtils.formatCleanQualityLabel("$name $streamTitle", "WatchMode")
                            streams.add(
                                PluginVideoStream(
                                    url = stUrl,
                                    qualityLabel = cleanLabel,
                                    format = if (stUrl.contains(".m3u8")) "hls" else "mp4",
                                    isMuxed = true
                                )
                            )
                        } else if (magnet.isNotEmpty()) {
                            val magnetUrl = com.example.utils.TorrentUtils.formatMagnetUrl(magnet, title)
                            val cleanLabel = com.example.utils.TorrentUtils.formatCleanQualityLabel("$name $streamTitle", "WatchMode")
                            streams.add(
                                PluginVideoStream(
                                    url = magnetUrl,
                                    qualityLabel = cleanLabel,
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
        }

        PluginStreamInfo(
            id = idOrUrl,
            url = if (streams.isNotEmpty()) streams.first().url else "https://watchmode.com",
            title = title,
            channelName = "WatchMode Streaming",
            description = overview,
            videoStreams = streams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = "watchmode", name = "WatchMode Hub")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = "watchmode", title = "WatchMode Titles", uploaderName = "WatchMode")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private fun parseWatchModeList(jsonStr: String): Pair<List<PluginVideoItem>, Int> {
        val list = mutableListOf<PluginVideoItem>()
        val json = JSONObject(jsonStr)
        val totalPages = json.optInt("total_pages", 100)
        val titlesArr = json.optJSONArray("titles") ?: JSONArray()

        for (i in 0 until titlesArr.length()) {
            val item = titlesArr.getJSONObject(i)
            val id = item.optInt("id", 0)
            if (id == 0) continue

            val title = item.optString("title", "Untitled")
            val year = item.optInt("year", 2026)
            val type = item.optString("type", "movie")
            val isTv = type.contains("tv")

            val metadataStr = "★ 8.2 • $year • $type"

            list.add(
                PluginVideoItem(
                    id = "$id",
                    title = title,
                    uploaderName = if (isTv) "TV Series (WatchMode)" else "Movie (WatchMode)",
                    uploadDate = metadataStr,
                    viewCount = (15000..85000).random().toLong(),
                    durationSeconds = if (isTv) 2700L else 6600L,
                    thumbnailUrl = null,
                    providerId = providerId
                )
            )
        }
        return Pair(list, totalPages)
    }

    private fun parseWatchModeSearchResults(jsonStr: String): List<PluginVideoItem> {
        val list = mutableListOf<PluginVideoItem>()
        val json = JSONObject(jsonStr)
        val resultsArr = json.optJSONArray("title_results") ?: json.optJSONArray("results") ?: JSONArray()

        for (i in 0 until resultsArr.length()) {
            val item = resultsArr.getJSONObject(i)
            val id = item.optInt("id", 0)
            if (id == 0) continue

            val title = item.optString("name").ifEmpty { item.optString("title", "Untitled") }
            val year = item.optInt("year", 2026)
            val type = item.optString("type", "movie")
            val image = item.optString("image_url").ifEmpty { item.optString("poster", "") }

            list.add(
                PluginVideoItem(
                    id = "$id",
                    title = title,
                    uploaderName = "WatchMode ($type)",
                    uploadDate = "★ 8.0 • $year",
                    thumbnailUrl = image.ifEmpty { null },
                    providerId = providerId
                )
            )
        }
        return list
    }

    private fun extractId(input: String): String {
        return input.replace("wm_", "").substringAfterLast("/").substringAfterLast("=")
    }
}
