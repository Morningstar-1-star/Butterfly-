package com.example.plugin.providers

import android.util.Log
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
        val details = com.example.util.TMDBHelper.fetchMediaDetails(idOrUrl, idOrUrl)
        if (details.title.isNotBlank()) {
            PluginVideoItem(
                id = idOrUrl,
                title = details.title,
                uploaderName = details.studioOrCollection.ifBlank { "Torrentio Multi-Indexer" },
                durationSeconds = 0L,
                thumbnailUrl = details.screenshots.firstOrNull(),
                providerId = providerId,
                uploadDate = details.releaseDateFormatted
            )
        } else {
            val cleanId = extractId(idOrUrl)
            PluginVideoItem(
                id = cleanId,
                title = cleanId,
                uploaderName = "Torrentio Multi-Indexer",
                providerId = providerId
            )
        }
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val identity = com.example.util.MediaIdResolver.resolve(idOrUrl)
        val stremioId = identity.toStremioImdbId()
        Log.d("TorrentioProvider", "Requesting Torrentio streams for ID: $stremioId (raw: $idOrUrl)")

        val videoStreams = mutableListOf<PluginVideoStream>()

        // 1. Torrentio Streams (Prioritized)
        if (stremioId != null) {
            val isTv = identity.mediaType == com.example.model.MediaType.TV
            val streamUrl = if (isTv) {
                "$BASE_URL/stream/series/$stremioId.json"
            } else {
                "$BASE_URL/stream/movie/$stremioId.json"
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
                            val cleanLabel = com.example.utils.TorrentUtils.formatCleanQualityLabel("$name $streamTitle", "Torrentio")
                            videoStreams.add(
                                PluginVideoStream(
                                    url = url,
                                    qualityLabel = cleanLabel,
                                    format = if (url.contains(".m3u8")) "hls" else if (url.contains(".mp4") || url.contains(".mkv")) "mp4" else "torrent",
                                    isMuxed = true
                                )
                            )
                        } else if (infoHash.isNotEmpty()) {
                            val magnetUrl = com.example.utils.TorrentUtils.formatMagnetUrl(infoHash, streamTitle)
                            val cleanLabel = com.example.utils.TorrentUtils.formatCleanQualityLabel("$name $streamTitle", "Torrentio")
                            videoStreams.add(
                                PluginVideoStream(
                                    url = magnetUrl,
                                    qualityLabel = cleanLabel,
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
        }

        val isTv = identity.mediaType == com.example.model.MediaType.TV
        val resolvedMediaTitle = identity.rawQueryOrUrl.takeIf { it.isNotBlank() && it != "Unknown" } ?: idOrUrl
        val resolvedStudioName = getStudioName(resolvedMediaTitle, isTv)

        PluginStreamInfo(
            id = idOrUrl,
            url = videoStreams.firstOrNull()?.url ?: "",
            title = resolvedMediaTitle,
            channelName = resolvedStudioName,
            description = "",
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
            val backdrop = item.optString("backdrop_path")
            val dateStr = item.optString("release_date").ifEmpty { item.optString("first_air_date") }
            val year = if (dateStr.length >= 4) dateStr.substring(0, 4) else "2026"
            val isTv = mediaType == "tv" || item.has("first_air_date")

            val voteAvg = item.optDouble("vote_average", 7.6)
            val formattedScore = if (voteAvg > 0) String.format("%.1f", voteAvg) else "7.9"

            val voteCount = item.optLong("vote_count", 0L)
            val metadataStr = if (isTv) {
                "★ $formattedScore • $year • TV"
            } else {
                "★ $formattedScore • $year"
            }

            val imgPath = if (backdrop.isNotEmpty()) "https://image.tmdb.org/t/p/w780$backdrop" else if (poster.isNotEmpty()) "https://image.tmdb.org/t/p/w500$poster" else null

            list.add(
                PluginVideoItem(
                    id = if (isTv) "tv_$id" else "$id",
                    title = title,
                    uploaderName = getStudioName(title, isTv),
                    uploadDate = metadataStr,
                    viewCount = voteCount,
                    durationSeconds = 0L,
                    thumbnailUrl = imgPath,
                    providerId = providerId
                )
            )
        }
        return Pair(list, totalPages)
    }

    private fun getStudioName(title: String, isTv: Boolean): String {
        return com.example.util.StudioDetector.detectStudio(title, isTv)
    }

    private fun extractId(input: String): String {
        return input.replace("tv_", "").substringAfterLast("/").substringAfterLast("=")
    }
}
