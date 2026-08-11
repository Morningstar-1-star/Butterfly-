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

    override val capabilities: ProviderCapabilities = ProviderCapabilities(supportsTorrent = true)

    override fun getProviderConfig(context: android.content.Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "YTS Torrents Engine",
            enabled = true,
            endpoint = BASE_URL,
            supportsTorrents = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

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
        val identity = com.example.util.MediaIdResolver.resolve(idOrUrl)
        val queryTerm = identity.imdbId ?: identity.tmdbId ?: extractId(idOrUrl)
        val url = "$BASE_URL/list_movies.json?query_term=$queryTerm"

        var title = "YTS Movie"
        var overview = ""
        var imdbCode = identity.imdbId ?: ""
        var trailerCode = ""
        val videoStreams = mutableListOf<PluginVideoStream>()

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val data = json.optJSONObject("data")
                val movies = data?.optJSONArray("movies")
                if (movies != null && movies.length() > 0) {
                    val movie = movies.getJSONObject(0)
                    title = movie.optString("title", title)
                    overview = movie.optString("summary", "")
                    if (imdbCode.isEmpty()) imdbCode = movie.optString("imdb_code", "")
                    trailerCode = movie.optString("yt_trailer_code", "")

                    val torrents = movie.optJSONArray("torrents") ?: JSONArray()
                    for (i in 0 until torrents.length()) {
                        val tor = torrents.getJSONObject(i)
                        val quality = tor.optString("quality", "720p")
                        val type = tor.optString("type", "bluray")
                        val size = tor.optString("size", "")
                        val hash = tor.optString("hash", "")

                        val magnetUrl = com.example.utils.TorrentUtils.formatMagnetUrl("magnet:?xt=urn:btih:$hash", title)
                        val cleanLabel = com.example.utils.TorrentUtils.formatCleanQualityLabel("$quality $type YTS", "YTS")
                        val finalLabel = if (size.isNotEmpty()) "$cleanLabel ($size)" else cleanLabel

                        videoStreams.add(
                            PluginVideoStream(
                                url = magnetUrl,
                                qualityLabel = finalLabel,
                                format = "torrent",
                                isMuxed = true
                            )
                        )
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
                    qualityLabel = "HD Trailer • YouTube",
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
                            val cleanLabel = com.example.utils.TorrentUtils.formatCleanQualityLabel(streamTitle, "Torrentio")
                            videoStreams.add(
                                PluginVideoStream(
                                    url = stUrl,
                                    qualityLabel = cleanLabel,
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
            id = idOrUrl,
            url = videoStreams.firstOrNull()?.url ?: "https://yts.mx",
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
            val year = m.optInt("year", 2026)
            val rating = m.optDouble("rating", 7.8)
            val cover = m.optString("medium_cover_image").ifEmpty { m.optString("small_cover_image") }

            val studioName = getStudioName(title)
            val scoreVal = if (rating > 0) String.format("%.1f", rating) else "7.9"
            val metadataStr = "★ $scoreVal • $year"

            val downloads = m.optLong("download_count", 0L)
            val runtimeMins = m.optLong("runtime", 0L)

            list.add(
                PluginVideoItem(
                    id = "$id",
                    title = title,
                    uploaderName = studioName,
                    uploadDate = metadataStr,
                    viewCount = downloads,
                    durationSeconds = runtimeMins * 60L,
                    thumbnailUrl = cover,
                    providerId = providerId
                )
            )
        }
        return Pair(list, movieCount)
    }

    private fun getStudioName(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("spider") || lower.contains("marvel") || lower.contains("avengers") -> "Marvel Studios"
            lower.contains("batman") || lower.contains("superman") || lower.contains("dc") -> "DC Studios"
            lower.contains("fast") || lower.contains("jurassic") || lower.contains("oppenheimer") -> "Universal Pictures"
            lower.contains("dune") || lower.contains("rings") || lower.contains("warner") -> "Warner Bros. Pictures"
            lower.contains("sonic") || lower.contains("top gun") || lower.contains("mission") -> "Paramount Pictures"
            else -> "YTS Movie"
        }
    }

    private fun extractId(input: String): String {
        return input.substringAfterLast("/").substringAfterLast("=")
    }
}
