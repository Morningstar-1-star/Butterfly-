package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TorrentioAggregatorProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    companion object {
        const val BASE_URL = "https://torrentio.strem.fun"
        const val TMDB_API_KEY = "3155fdb497f7575a144f26adebcbf980"
    }

    override val providerId: String = "torrentio_aggregator"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        // Use TMDB popular movies to drive Torrentio multi-indexer streams
        val url = "https://api.themoviedb.org/3/movie/popular?api_key=$TMDB_API_KEY&page=$page"
        val resp = try { http.get(url) } catch (e: Exception) { return@withContext PagedResult(emptyList()) }
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, totalPages) = parseTmdbList(resp.body)
        PagedResult(
            items = items,
            nextPageToken = if (page < totalPages) (page + 1).toString() else null,
            hasMore = page < totalPages
        )
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&query=$encodedQuery&page=$page"
        val resp = try { http.get(url) } catch (e: Exception) { return@withContext PagedResult(emptyList()) }
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, totalPages) = parseTmdbList(resp.body)
        PagedResult(
            items = items,
            nextPageToken = if (page < totalPages) (page + 1).toString() else null,
            hasMore = page < totalPages
        )
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val cleanId = extractId(idOrUrl)
        PluginVideoItem(
            id = cleanId,
            title = "Torrentio Stream $cleanId",
            uploaderName = "Torrentio Multi-Indexer",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val cleanId = extractId(idOrUrl)
        val isTv = idOrUrl.contains("tv_")
        val type = if (isTv) "tv" else "movie"

        // Fetch IMDB ID from TMDB
        val detailsUrl = "https://api.themoviedb.org/3/$type/$cleanId?api_key=$TMDB_API_KEY&append_to_response=external_ids"
        var imdbId = ""
        var title = "Torrent Stream"
        var overview = ""

        try {
            val resp = http.get(detailsUrl)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                title = json.optString("title").ifEmpty { json.optString("name", title) }
                overview = json.optString("overview", "")
                val extIds = json.optJSONObject("external_ids")
                imdbId = extIds?.optString("imdb_id", "") ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val videoStreams = mutableListOf<PluginVideoStream>()

        if (imdbId.isNotEmpty() && imdbId.startsWith("tt")) {
            val streamUrl = if (isTv) {
                "$BASE_URL/stream/series/$imdbId:1:1.json"
            } else {
                "$BASE_URL/stream/movie/$imdbId.json"
            }

            try {
                val tResp = http.get(streamUrl)
                if (tResp.statusCode == 200) {
                    val tJson = JSONObject(tResp.body)
                    val streamArr = tJson.optJSONArray("streams") ?: JSONArray()
                    for (i in 0 until streamArr.length()) {
                        val st = streamArr.getJSONObject(i)
                        val streamTitle = st.optString("title", "Torrent Stream ${i + 1}")
                        val name = st.optString("name", "Torrentio")
                        val url = st.optString("url")
                        val infoHash = st.optString("infoHash")

                        if (url.isNotEmpty()) {
                            videoStreams.add(
                                PluginVideoStream(
                                    url = url,
                                    qualityLabel = "$name: ${streamTitle.take(40)}",
                                    format = if (url.contains(".m3u8")) "hls" else "mp4",
                                    isMuxed = true
                                )
                            )
                        } else if (infoHash.isNotEmpty()) {
                            val magnetUrl = com.example.utils.TorrentUtils.formatMagnetUrl(infoHash, title)
                            val cleanTitle = streamTitle.replace("\n", " ").replace("\r", " ").take(45)
                            videoStreams.add(
                                PluginVideoStream(
                                    url = magnetUrl,
                                    qualityLabel = "$name - $cleanTitle",
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

        // Fallback VidSrc Stream
        if (videoStreams.isEmpty()) {
            val fallbackUrl = if (isTv) "https://vidsrc.to/embed/tv/$cleanId/1/1" else "https://vidsrc.to/embed/movie/$cleanId"
            videoStreams.add(
                PluginVideoStream(
                    url = fallbackUrl,
                    qualityLabel = "VidSrc Fallback Stream",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        PluginStreamInfo(
            id = cleanId,
            url = videoStreams.firstOrNull()?.url ?: "https://torrentio.strem.fun",
            title = title,
            channelName = "Torrentio Multi-Indexer",
            description = overview,
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
        PluginChannel(id = "torrentio", name = "Torrentio Multi-Indexer")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = "torrentio", title = "Torrent Streams", uploaderName = "Torrentio")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private fun parseTmdbList(jsonStr: String): Pair<List<PluginVideoItem>, Int> {
        val list = mutableListOf<PluginVideoItem>()
        val json = JSONObject(jsonStr)
        val totalPages = json.optInt("total_pages", 1)
        val results = json.optJSONArray("results") ?: JSONArray()

        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val mediaType = item.optString("media_type", "movie")
            if (mediaType == "person") continue

            val id = item.optInt("id", 0)
            if (id == 0) continue

            val title = item.optString("title").ifEmpty { item.optString("name", "Untitled") }
            val poster = item.optString("poster_path")
            val isTv = mediaType == "tv" || item.has("first_air_date")

            val imgPath = if (poster.isNotEmpty()) "https://image.tmdb.org/t/p/w500$poster" else null

            list.add(
                PluginVideoItem(
                    id = if (isTv) "tv_$id" else "$id",
                    title = title,
                    uploaderName = if (isTv) "Torrentio TV" else "Torrentio Movie",
                    thumbnailUrl = imgPath,
                    providerId = providerId
                )
            )
        }
        return Pair(list, totalPages)
    }

    private fun extractId(input: String): String {
        return input.replace("tv_", "").substringAfterLast("/").substringAfterLast("=")
    }
}
