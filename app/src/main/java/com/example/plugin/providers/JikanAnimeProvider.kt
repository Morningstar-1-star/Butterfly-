package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class JikanAnimeProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    companion object {
        const val BASE_URL = "https://api.jikan.moe/v4"
    }

    override val providerId: String = "jikan_anime"

    override fun getProviderConfig(context: android.content.Context?): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = "Jikan MyAnimeList",
            enabled = true,
            endpoint = BASE_URL,
            supportsDirectStreams = true,
            supportsTorrents = true,
            healthStatus = ProviderHealthStatus.READY
        )
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "$BASE_URL/top/anime?page=$page&limit=20"
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
        val url = "$BASE_URL/anime?q=$encodedQuery&page=$page&limit=20"
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
        val url = "$BASE_URL/anime/$malId/full"

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val data = json.optJSONObject("data")
                if (data != null) {
                    val title = data.optString("title", "Anime $malId")
                    val images = data.optJSONObject("images")?.optJSONObject("jpg")
                    val poster = images?.optString("large_image_url") ?: images?.optString("image_url")
                    val score = data.optDouble("score", 0.0)

                    return@withContext PluginVideoItem(
                        id = malId,
                        title = title,
                        uploaderName = if (score > 0) "MyAnimeList (Score: $score)" else "MyAnimeList Anime",
                        thumbnailUrl = poster,
                        providerId = providerId
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        PluginVideoItem(
            id = idOrUrl,
            title = "Anime $malId",
            uploaderName = "Jikan Anime",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val malId = extractId(idOrUrl)
        val url = "$BASE_URL/anime/$malId/full"

        var title = "Anime Stream"
        var synopsis = ""
        var ytEmbedUrl = ""
        val videoStreams = mutableListOf<PluginVideoStream>()

        try {
            val resp = http.get(url)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val data = json.optJSONObject("data")
                if (data != null) {
                    title = data.optString("title", title)
                    synopsis = data.optString("synopsis", "")
                    val trailer = data.optJSONObject("trailer")
                    ytEmbedUrl = trailer?.optString("embed_url", "") ?: ""
                    val ytId = trailer?.optString("youtube_id", "") ?: ""

                    if (ytEmbedUrl.isEmpty() && ytId.isNotEmpty()) {
                        ytEmbedUrl = "https://www.youtube.com/embed/$ytId"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

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

        // 2. Official YouTube Trailer / PV stream if available
        if (ytEmbedUrl.isNotEmpty()) {
            videoStreams.add(
                PluginVideoStream(
                    url = ytEmbedUrl,
                    qualityLabel = "Official Trailer / PV (YouTube)",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        // 3. Torrentio Anime Torrent Streams (Kitsu / MAL ID)
        val torrentioAnimeUrl = "https://torrentio.strem.fun/stream/anime/kitsu:$malId.json"
        try {
            val tResp = http.get(torrentioAnimeUrl)
            if (tResp.statusCode == 200) {
                val tJson = JSONObject(tResp.body)
                val streamArr = tJson.optJSONArray("streams") ?: JSONArray()
                for (i in 0 until minOf(streamArr.length(), 5)) {
                    val st = streamArr.getJSONObject(i)
                    val streamTitle = st.optString("title", "Anime Torrent ${i + 1}")
                    val name = st.optString("name", "Nyaa / Torrentio")
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
                        val magnetUrl = com.example.utils.TorrentUtils.formatMagnetUrl(magnet, title)
                        val cleanTitle = streamTitle.replace("\n", " ").replace("\r", " ").take(40)
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

        PluginStreamInfo(
            id = malId,
            url = videoStreams.firstOrNull()?.url ?: vidsrcAnimeUrl,
            title = title,
            channelName = "Jikan MyAnimeList",
            description = synopsis,
            videoStreams = videoStreams
        )
    }

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        val malId = extractId(idOrUrl)
        val res = com.example.util.AnimeReviewFetcher.fetchUnifiedAnimeReviews(
            title = "Anime $malId",
            videoId = malId,
            providerId = providerId
        )
        val pluginComments = res.reviews.map { vc ->
            PluginComment(
                id = vc.id,
                authorName = vc.authorName,
                authorAvatarUrl = vc.authorAvatarUrl,
                content = vc.commentText,
                publishedTime = vc.timeAgo,
                likeCount = vc.likeCount.toLong(),
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
        PluginChannel(id = "jikan", name = "MyAnimeList Jikan API")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = "jikan", title = "Top Anime", uploaderName = "Jikan")
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
            val score = anime.optDouble("score", 0.0)
            val episodes = anime.optInt("episodes", 0)
            val images = anime.optJSONObject("images")?.optJSONObject("jpg")
            val poster = images?.optString("large_image_url") ?: images?.optString("image_url")

            val epText = if (episodes > 0) "S1 • $episodes ep" else "S1 • 24 ep"
            val scoreVal = if (score > 0) String.format("%.1f", score) else "8.4"
            val metadataStr = "★ $scoreVal • 2026 • $epText"

            val studiosArr = anime.optJSONArray("studios")
            val studioName = if (studiosArr != null && studiosArr.length() > 0) {
                studiosArr.getJSONObject(0).optString("name", "Anime Studio")
            } else {
                "Anime Studio"
            }

            val members = anime.optLong("members", 0L)

            list.add(
                PluginVideoItem(
                    id = "$malId",
                    title = title,
                    uploaderName = studioName,
                    uploadDate = if (score > 0) "★ ${String.format("%.1f", score)}" else null,
                    viewCount = members,
                    durationSeconds = 0L,
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
