package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class YtsTorrentProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    companion object {
        const val BASE_URL = "https://yts.mx/api/v2"
    }

    override val providerId: String = "yts_torrents"

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "$BASE_URL/list_movies.json?limit=20&page=$page&sort_by=download_count"
        val resp = try { http.get(url) } catch (e: Exception) { return@withContext PagedResult(emptyList()) }
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, totalCount) = parseYtsList(resp.body)
        PagedResult(
            items = items,
            nextPageToken = if (page * 20 < totalCount) (page + 1).toString() else null,
            hasMore = page * 20 < totalCount
        )
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$BASE_URL/list_movies.json?query_term=$encodedQuery&limit=20&page=$page"
        val resp = try { http.get(url) } catch (e: Exception) { return@withContext PagedResult(emptyList()) }
        if (resp.statusCode != 200) return@withContext PagedResult(emptyList())

        val (items, totalCount) = parseYtsList(resp.body)
        PagedResult(
            items = items,
            nextPageToken = if (page * 20 < totalCount) (page + 1).toString() else null,
            hasMore = page * 20 < totalCount
        )
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val movieId = extractId(idOrUrl)
        val url = "$BASE_URL/movie_details.json?movie_id=$movieId"

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val data = json.optJSONObject("data")
                val movie = data?.optJSONObject("movie")
                if (movie != null) {
                    val title = movie.optString("title", "YTS Movie")
                    val year = movie.optInt("year", 0)
                    val cover = movie.optString("large_cover_image").ifEmpty { movie.optString("medium_cover_image") }

                    return@withContext PluginVideoItem(
                        id = movieId,
                        title = "$title ($year)",
                        uploaderName = "YTS YIFY Torrents",
                        uploadDate = year.toString(),
                        thumbnailUrl = cover,
                        providerId = providerId
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        PluginVideoItem(
            id = idOrUrl,
            title = "YTS Movie $movieId",
            uploaderName = "YTS Torrents",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val movieId = extractId(idOrUrl)
        val url = "$BASE_URL/movie_details.json?movie_id=$movieId"

        var title = "YTS Movie"
        var overview = ""
        var imdbCode = ""
        var trailerCode = ""
        val videoStreams = mutableListOf<PluginVideoStream>()

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val data = json.optJSONObject("data")
                val movie = data?.optJSONObject("movie")
                if (movie != null) {
                    title = movie.optString("title", title)
                    overview = movie.optString("description_full", "")
                    imdbCode = movie.optString("imdb_code", "")
                    trailerCode = movie.optString("yt_trailer_code", "")

                    val torrents = movie.optJSONArray("torrents") ?: JSONArray()
                    for (i in 0 until torrents.length()) {
                        val tor = torrents.getJSONObject(i)
                        val quality = tor.optString("quality", "720p")
                        val type = tor.optString("type", "bluray")
                        val size = tor.optString("size", "")
                        val hash = tor.optString("hash", "")
                        val torrentUrl = tor.optString("url", "")

                        val magnetUrl = com.example.utils.TorrentUtils.formatMagnetUrl("magnet:?xt=urn:btih:$hash", title)

                        videoStreams.add(
                            PluginVideoStream(
                                url = magnetUrl,
                                qualityLabel = "YTS $quality $type ($size)",
                                format = "embed",
                                isMuxed = true
                            )
                        )

                        if (torrentUrl.isNotEmpty()) {
                            videoStreams.add(
                                PluginVideoStream(
                                    url = torrentUrl,
                                    qualityLabel = "Download Torrent File: $quality ($size)",
                                    format = "torrent",
                                    isMuxed = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Add YouTube trailer embed option if available
        if (trailerCode.isNotEmpty()) {
            videoStreams.add(
                PluginVideoStream(
                    url = "https://www.youtube.com/embed/$trailerCode",
                    qualityLabel = "Official HD Trailer (YouTube)",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        // Add Torrentio stream if IMDB code exists
        if (imdbCode.isNotEmpty() && imdbCode.startsWith("tt")) {
            try {
                val tResp = http.get("https://torrentio.strem.fun/stream/movie/$imdbCode.json")
                if (tResp.statusCode == 200) {
                    val tJson = JSONObject(tResp.body)
                    val streamArr = tJson.optJSONArray("streams") ?: JSONArray()
                    for (i in 0 until minOf(streamArr.length(), 4)) {
                        val st = streamArr.getJSONObject(i)
                        val streamTitle = st.optString("title", "Torrent Stream ${i + 1}")
                        val stUrl = st.optString("url")
                        if (stUrl.isNotEmpty()) {
                            videoStreams.add(
                                PluginVideoStream(
                                    url = stUrl,
                                    qualityLabel = "Direct Streaming: ${streamTitle.take(35)}",
                                    format = if (stUrl.contains(".m3u8")) "hls" else "mp4",
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
            id = movieId,
            url = videoStreams.firstOrNull()?.url ?: "https://yts.mx/movies/$movieId",
            title = title,
            channelName = "YTS YIFY Torrents",
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
        PluginChannel(id = "yts", name = "YTS YIFY Torrents")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = "yts", title = "YTS Movies", uploaderName = "YTS")
    }

    override suspend fun getRecommendations(idOrUrl: String): List<PluginVideoItem> = withContext(Dispatchers.IO) {
        home().items.take(10)
    }

    private fun parseYtsList(jsonStr: String): Pair<List<PluginVideoItem>, Int> {
        val list = mutableListOf<PluginVideoItem>()
        val json = JSONObject(jsonStr)
        val data = json.optJSONObject("data") ?: JSONObject()
        val movieCount = data.optInt("movie_count", 0)
        val movies = data.optJSONArray("movies") ?: JSONArray()

        for (i in 0 until movies.length()) {
            val m = movies.getJSONObject(i)
            val id = m.optInt("id", 0)
            if (id == 0) continue

            val title = m.optString("title", "YTS Movie")
            val year = m.optInt("year", 0)
            val cover = m.optString("medium_cover_image").ifEmpty { m.optString("small_cover_image") }

            list.add(
                PluginVideoItem(
                    id = "$id",
                    title = if (year > 0) "$title ($year)" else title,
                    uploaderName = "YTS YIFY",
                    uploadDate = if (year > 0) "$year" else null,
                    thumbnailUrl = cover,
                    providerId = providerId
                )
            )
        }
        return Pair(list, movieCount)
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}
