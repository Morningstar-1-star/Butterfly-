package com.example.plugin.providers

import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-source Torrent Indexer Provider covering 1337x, TorrentGalaxy, ThePirateBay, BitSearch, Nyaa, MagnetDL, etc.
 */
class TorrentApiMultiProvider(
    private val http: HttpBridge = HttpBridge()
) : ContentProviderApi {

    companion object {
        const val TMDB_API_KEY = "3155fdb497f7575a144f26adebcbf980"
        const val TORRENT_API_BASE = "https://torrent-api-py-v2.vercel.app/api/v1"
    }

    override val providerId: String = "torrent_api_multi"

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
        
        // Try searching Torrent API directly
        val torrentApiUrl = "$TORRENT_API_BASE/all/search?query=$encodedQuery"
        try {
            val resp = http.get(torrentApiUrl)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val data = json.optJSONArray("data") ?: JSONArray()
                val items = mutableListOf<PluginVideoItem>()
                for (i in 0 until minOf(data.length(), 25)) {
                    val item = data.getJSONObject(i)
                    val name = item.optString("name").ifEmpty { item.optString("title", "Torrent Item") }
                    val magnet = item.optString("magnet").ifEmpty { item.optString("hash") }
                    val site = item.optString("site", "Torrent")
                    val poster = item.optString("poster", "")

                    if (name.isNotEmpty()) {
                        items.add(
                            PluginVideoItem(
                                id = magnet.ifEmpty { name },
                                title = name,
                                uploaderName = "Source: $site",
                                thumbnailUrl = poster.ifEmpty { null },
                                providerId = providerId
                            )
                        )
                    }
                }
                if (items.isNotEmpty()) {
                    return@withContext PagedResult(items, null, false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to TMDB Search
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
            title = "Torrent Item $cleanId",
            uploaderName = "1337x / TGx / PirateBay Multi Indexer",
            providerId = providerId
        )
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val cleanId = extractId(idOrUrl)
        val videoStreams = mutableListOf<PluginVideoStream>()

        // Direct Magnet Check
        if (cleanId.startsWith("magnet:") || cleanId.length > 25) {
            val rawMagnet = if (cleanId.startsWith("magnet:")) cleanId else "magnet:?xt=urn:btih:$cleanId"
            val magnetUrl = com.example.utils.TorrentUtils.formatMagnetUrl(rawMagnet)
            videoStreams.add(
                PluginVideoStream(
                    url = magnetUrl,
                    qualityLabel = "Magnet Torrent Direct Stream",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        val isTv = idOrUrl.contains("tv_")
        val type = if (isTv) "tv" else "movie"

        // Fetch TMDB & IMDB details
        val detailsUrl = "https://api.themoviedb.org/3/$type/$cleanId?api_key=$TMDB_API_KEY&append_to_response=external_ids"
        var imdbId = ""
        var title = "Torrent Movie/TV Stream"
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

        // Query Stremio multi-indexers (Torrentio + MediaFusion + Orion)
        if (imdbId.isNotEmpty() && imdbId.startsWith("tt")) {
            val queryPath = if (isTv) "series/$imdbId:1:1.json" else "movie/$imdbId.json"
            
            // 1. Torrentio
            fetchAddonStreams("https://torrentio.strem.fun/stream/$queryPath", "Torrentio Multi", videoStreams)
            
            // 2. MediaFusion
            fetchAddonStreams("https://mediafusion.elfhosted.com/stream/$queryPath", "MediaFusion", videoStreams)
            
            // 3. KnightCrawler
            fetchAddonStreams("https://knightcrawler.elfhosted.com/stream/$queryPath", "KnightCrawler", videoStreams)
        }

        // Add 1337x / TGx / VidSrc fallback players
        val embedUrl = if (isTv) "https://vidsrc.to/embed/tv/$cleanId/1/1" else "https://vidsrc.to/embed/movie/$cleanId"
        videoStreams.add(
            PluginVideoStream(
                url = embedUrl,
                qualityLabel = "VidSrc Ultra HD",
                format = "embed",
                isMuxed = true
            )
        )

        PluginStreamInfo(
            id = cleanId,
            url = videoStreams.firstOrNull()?.url ?: embedUrl,
            title = title,
            channelName = "1337x / TGx / PirateBay Multi-Indexer",
            description = overview,
            videoStreams = videoStreams
        )
    }

    private suspend fun fetchAddonStreams(addonUrl: String, sourceName: String, list: MutableList<PluginVideoStream>) {
        try {
            val resp = http.get(addonUrl)
            if (resp.statusCode == 200) {
                val json = JSONObject(resp.body)
                val streams = json.optJSONArray("streams") ?: JSONArray()
                for (i in 0 until minOf(streams.length(), 5)) {
                    val st = streams.getJSONObject(i)
                    val title = st.optString("title", "Torrent Stream")
                    val name = st.optString("name", sourceName)
                    val url = st.optString("url")
                    val hash = st.optString("infoHash")

                    if (url.isNotEmpty()) {
                        list.add(
                            PluginVideoStream(
                                url = url,
                                qualityLabel = "$name: ${title.take(35)}",
                                format = if (url.contains(".m3u8")) "hls" else "mp4",
                                isMuxed = true
                            )
                        )
                    } else if (hash.isNotEmpty()) {
                        val magnet = com.example.utils.TorrentUtils.formatMagnetUrl(hash, title)
                        val cleanTitle = title.replace("\n", " ").replace("\r", " ").take(40)
                        list.add(
                            PluginVideoStream(
                                url = magnet,
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

    override suspend fun getComments(idOrUrl: String, pageToken: String?): PagedResult<PluginComment> = withContext(Dispatchers.IO) {
        PagedResult(emptyList())
    }

    override suspend fun getSubtitles(idOrUrl: String): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getChannel(channelIdOrUrl: String): PluginChannel = withContext(Dispatchers.IO) {
        PluginChannel(id = "torrent_api", name = "1337x / PirateBay / TGx Multi")
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): PluginPlaylist = withContext(Dispatchers.IO) {
        PluginPlaylist(id = "torrent_api", title = "Torrent Indexer", uploaderName = "Multi")
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
                    uploaderName = if (isTv) "1337x / TGx TV" else "1337x / TGx Movie",
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
