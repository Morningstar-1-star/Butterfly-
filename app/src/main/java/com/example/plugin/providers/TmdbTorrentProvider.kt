package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TmdbTorrentProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    companion object {
        const val API_KEY = "3155fdb497f7575a144f26adebcbf980"
        const val BASE_URL = "https://api.themoviedb.org/3"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
    }

    override val providerId: String = "tmdb_movies"

    override fun getProviderConfig(context: android.content.Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "TMDB Movies & Series",
            enabled = true,
            endpoint = BASE_URL,
            supportsDirectStreams = true,
            supportsTorrents = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "$BASE_URL/trending/all/day?api_key=$API_KEY&page=$page"
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
        val url = "$BASE_URL/search/multi?api_key=$API_KEY&query=$encodedQuery&page=$page"
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
        val isTv = idOrUrl.contains("tv_") || idOrUrl.contains("/tv/")
        val type = if (isTv) "tv" else "movie"
        val url = "$BASE_URL/$type/$cleanId?api_key=$API_KEY"

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val title = json.optString("title").ifEmpty { json.optString("name", "TMDB Title") }
                val poster = json.optString("poster_path")
                val backdrop = json.optString("backdrop_path")
                val date = json.optString("release_date").ifEmpty { json.optString("first_air_date") }
                
                return@withContext PluginVideoItem(
                    id = if (isTv) "tv_$cleanId" else cleanId,
                    title = title,
                    uploaderName = if (isTv) "TV Series (TMDB)" else "Movie (TMDB)",
                    uploadDate = date,
                    thumbnailUrl = if (poster.isNotEmpty()) "$IMAGE_BASE_URL$poster" else if (backdrop.isNotEmpty()) "$IMAGE_BASE_URL$backdrop" else null,
                    providerId = providerId
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        PluginVideoItem(
            id = idOrUrl,
            title = "TMDB Content $cleanId",
            uploaderName = "TMDB Cinema",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val cleanId = extractId(idOrUrl)
        val isTv = idOrUrl.contains("tv_")
        val type = if (isTv) "tv" else "movie"
        
        // Get details & external IMDB ID
        val detailsUrl = "$BASE_URL/$type/$cleanId?api_key=$API_KEY&append_to_response=external_ids"
        var imdbId = ""
        var title = "Movie/TV Stream"
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

        val streams = mutableListOf<PluginVideoStream>()

        // 1. VidSrc.to Embed Stream
        val vidsrcUrl = if (isTv) "https://vidsrc.to/embed/tv/$cleanId/1/1" else "https://vidsrc.to/embed/movie/$cleanId"
        streams.add(
            PluginVideoStream(
                url = vidsrcUrl,
                qualityLabel = "VidSrc HD Embed",
                format = "embed",
                isMuxed = true
            )
        )

        // 5. Torrentio Stremio Torrent Stream (If IMDB ID is present)
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
                        val streamTitle = st.optString("title", "Torrent Stream ${i + 1}")
                        val name = st.optString("name", "Torrentio")
                        val url = st.optString("url")
                        val magnet = st.optString("infoHash")

                        if (url.isNotEmpty()) {
                            val cleanLabel = com.example.utils.TorrentUtils.formatCleanQualityLabel("$name $streamTitle", "TMDB")
                            streams.add(
                                PluginVideoStream(
                                    url = url,
                                    qualityLabel = cleanLabel,
                                    format = if (url.contains(".m3u8")) "hls" else "mp4",
                                    isMuxed = true
                                )
                            )
                        } else if (magnet.isNotEmpty()) {
                            val magnetUrl = com.example.utils.TorrentUtils.formatMagnetUrl(magnet, title)
                            val cleanLabel = com.example.utils.TorrentUtils.formatCleanQualityLabel("$name $streamTitle", "TMDB")
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
            url = vidsrcUrl,
            title = title,
            channelName = if (isTv) "TMDB TV Series" else "TMDB Cinema",
            description = overview,
            videoStreams = streams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        val result = com.example.util.TorrentReviewFetcher.fetchReviewsForTorrent(
            title = idOrUrl,
            videoId = idOrUrl,
            providerId = providerId
        )
        val pluginComments = result.reviews.map { vc ->
            PluginComment(
                id = vc.id,
                authorName = vc.authorName,
                authorAvatarUrl = vc.authorAvatarUrl,
                content = vc.commentText,
                publishedTime = vc.timeAgo,
                likeCount = vc.likeCount.toLong(),
                dislikeCount = vc.dislikeCount.toLong(),
                rating = vc.rating,
                reviewTitle = vc.reviewTitle,
                isSpoiler = vc.isSpoiler
            )
        }
        PagedResult(pluginComments)
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = "tmdb", name = "TMDB Movies & Series")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = "tmdb", title = "TMDB Collection", uploaderName = "TMDB")
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

            val voteAvg = item.optDouble("vote_average", 7.5)
            val formattedScore = if (voteAvg > 0) String.format("%.1f", voteAvg) else "7.8"

            val voteCount = item.optLong("vote_count", 0L)
            val metadataStr = if (isTv) {
                "★ $formattedScore • $year • TV"
            } else {
                "★ $formattedScore • $year"
            }

            val imgPath = if (backdrop.isNotEmpty()) "https://image.tmdb.org/t/p/w780$backdrop" else if (poster.isNotEmpty()) "$IMAGE_BASE_URL$poster" else null

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
        val lower = title.lowercase()
        return when {
            lower.contains("spider") || lower.contains("avengers") || lower.contains("marvel") || lower.contains("iron man") || lower.contains("thor") -> "Marvel Studios"
            lower.contains("batman") || lower.contains("superman") || lower.contains("joker") || lower.contains("dc") -> "DC Studios"
            lower.contains("star wars") || lower.contains("avatar") -> "20th Century Studios"
            lower.contains("paramount") || lower.contains("sonic") || lower.contains("top gun") -> "Paramount Pictures"
            isTv -> "TV Network"
            else -> "Film Studio"
        }
    }

    private fun extractId(input: String): String {
        return input.replace("tv_", "").substringAfterLast("/").substringAfterLast("=")
    }
}
