package com.example.plugin.providers

import android.content.Context
import android.util.Log
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.util.DebridSettingsManager
import com.example.util.MediaIdResolver
import com.example.utils.TorrentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Comet Stremio / Torrent Indexer Provider Plugin
 * Capability: Movie, Series, Torrent, Search
 */
class CometProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    override val providerId: String = "comet"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsSearch = true,
        supportsMovie = true,
        supportsSeries = true,
        supportsTorrent = true
    )

    private fun getBaseUrl(context: Context?): String {
        return if (context != null) {
            DebridSettingsManager.getCometEndpoint(context)
        } else {
            "https://comet.elfhosted.com"
        }
    }

    override fun getProviderConfig(context: Context?): ProviderConfig {
        val endpoint = getBaseUrl(context)
        return ProviderConfig(
            id = providerId,
            name = "Comet Stremio Indexer",
            enabled = true,
            endpoint = endpoint,
            requiresApiKey = false,
            supportedMediaTypes = listOf("movie", "series"),
            supportsDirectStreams = true,
            supportsTorrents = true,
            healthStatus = if (endpoint.isNotBlank()) ProviderHealthStatus.READY else ProviderHealthStatus.CONFIGURATION_REQUIRED
        )
    }

    companion object {
        private const val TMDB_API_KEY = "3155fdb497f7575a144f26adebcbf980"
    }

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val url = "https://api.themoviedb.org/3/trending/all/day?api_key=$TMDB_API_KEY&page=$page"
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
                uploaderName = details.studioOrCollection.ifBlank { "Comet Indexer" },
                durationSeconds = 0L,
                thumbnailUrl = details.screenshots.firstOrNull(),
                providerId = providerId,
                uploadDate = details.releaseDateFormatted
            )
        } else {
            val cleanTitle = com.example.util.TMDBHelper.cleanTitleForSearch(idOrUrl)
            PluginVideoItem(
                id = idOrUrl,
                title = cleanTitle.ifBlank { idOrUrl },
                uploaderName = "Comet Indexer",
                providerId = providerId
            )
        }
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
            val year = if (dateStr.length >= 4) dateStr.substring(0, 4) else ""
            val isTv = mediaType == "tv" || item.has("first_air_date")

            val voteAvg = item.optDouble("vote_average", 0.0)
            val formattedScore = if (voteAvg > 0) String.format(java.util.Locale.US, "%.1f", voteAvg) else ""
            val voteCount = item.optLong("vote_count", 0L)

            val metadataStr = buildString {
                if (formattedScore.isNotEmpty()) append("★ $formattedScore")
                if (year.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append(year)
                }
                if (isTv) {
                    if (isNotEmpty()) append(" • ")
                    append("TV")
                }
            }

            val imgPath = if (backdrop.isNotEmpty()) "https://image.tmdb.org/t/p/w780$backdrop" else if (poster.isNotEmpty()) "https://image.tmdb.org/t/p/w500$poster" else null

            list.add(
                PluginVideoItem(
                    id = if (isTv) "tv_$id" else "$id",
                    title = title,
                    uploaderName = com.example.util.StudioDetector.detectStudio(title, isTv),
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

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val identity = MediaIdResolver.resolve(idOrUrl)
        val stremioId = identity.toStremioImdbId()
        val videoStreams = mutableListOf<PluginVideoStream>()

        if (stremioId != null) {
            val isTv = identity.mediaType == com.example.model.MediaType.TV
            val baseUrl = getBaseUrl(null)
            val streamUrl = if (isTv) "$baseUrl/stream/series/$stremioId.json" else "$baseUrl/stream/movie/$stremioId.json"
            Log.d("CometProvider", "Requesting Comet streams from real network endpoint: $streamUrl")

            try {
                val response = http.get(streamUrl)
                if (response.statusCode == 200 && response.body.isNotBlank()) {
                    val json = JSONObject(response.body)
                    val streamArr = json.optJSONArray("streams") ?: JSONArray()
                    for (i in 0 until streamArr.length()) {
                        val st = streamArr.getJSONObject(i)
                        val title = st.optString("title", "Comet Stream ${i + 1}")
                        val name = st.optString("name", "Comet")
                        val url = st.optString("url")
                        val infoHash = st.optString("infoHash")

                        val cleanLabel = TorrentUtils.formatCleanQualityLabel("$name $title", "Comet")
                        if (url.isNotEmpty()) {
                            videoStreams.add(
                                PluginVideoStream(
                                    url = url,
                                    qualityLabel = cleanLabel,
                                    format = if (url.contains(".m3u8")) "hls" else "mp4",
                                    isMuxed = true
                                )
                            )
                        } else if (infoHash.isNotEmpty()) {
                            val magnet = TorrentUtils.formatMagnetUrl(infoHash, title)
                            videoStreams.add(
                                PluginVideoStream(
                                    url = magnet,
                                    qualityLabel = cleanLabel,
                                    format = "torrent",
                                    isMuxed = true
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CometProvider", "Network request failed for Comet endpoint: ${e.message}")
            }
        }

        PluginStreamInfo(
            id = idOrUrl,
            url = videoStreams.firstOrNull()?.url ?: "",
            title = "Comet Stream",
            channelName = "Comet Fast Indexer",
            videoStreams = videoStreams
        )
    }
}
