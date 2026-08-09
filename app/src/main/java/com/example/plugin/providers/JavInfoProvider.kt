package com.example.plugin.providers

import android.content.Context
import android.util.Log
import com.example.plugin.bridge.HttpBridge
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.plugin.sdk.model.*
import com.example.util.DebridSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Independent JavInfo Provider for Butterfly.
 * Connects to https://api.javinfo.dev using POST /movie and POST /query endpoints.
 * Supports metadata, cast, studio/maker, series, genres, release date, runtime, cover,
 * and magnet / HLS streaming sources.
 */
class JavInfoProvider(
    private val context: Context? = null,
    private val http: HttpBridge = HttpBridge(),
    private val customApiKey: String? = null
) : ContentProviderApi {

    override val providerId: String = "javinfo"
    private val apiBase = "https://api.javinfo.dev"

    override val capabilities: ProviderCapabilities
        get() = ProviderCapabilities(
            supportsSearch = true,
            supportsMovie = true,
            supportsSeries = true,
            supportsAnime = false,
            supportsTorrent = true,
            providerType = ProviderType.OTHER
        )

    private fun getApiKey(): String {
        if (!customApiKey.isNullOrBlank()) return customApiKey.trim()
        val keyFromSettings = context?.let { DebridSettingsManager.getJavInfoApiKey(it) }
        return keyFromSettings?.trim() ?: ""
    }

    override fun getProviderConfig(context: Context?): ProviderConfig {
        val apiKey = getApiKey()
        val hasKey = apiKey.isNotBlank()
        return ProviderConfig(
            id = providerId,
            name = "JavInfo API",
            enabled = true,
            endpoint = apiBase,
            requiresApiKey = true,
            apiKey = apiKey.ifBlank { null },
            supportsDirectStreams = true,
            supportsTorrents = true,
            healthStatus = if (hasKey) ProviderHealthStatus.READY else ProviderHealthStatus.CONFIGURATION_REQUIRED
        )
    }

    // In-memory caching for API responses (TTL: 10 minutes)
    private data class CacheEntry<T>(val timestamp: Long, val data: T)
    private val movieCache = ConcurrentHashMap<String, CacheEntry<JavInfoMovieData>>()
    private val searchCache = ConcurrentHashMap<String, CacheEntry<List<JavInfoMovieData>>>()
    private val cacheTtlMs = 10 * 60 * 1000L // 10 minutes

    private val defaultPopularCodes = listOf(
        "SSIS-001", "CAWD-001", "IPX-100", "STSK-236", "HMDNV-949",
        "SIRO-5681", "MIAB-001", "JUL-001", "ADN-001", "MIDE-001"
    )

    data class JavInfoMovieData(
        val dvdId: String,
        val title: String,
        val titleEn: String?,
        val cast: List<String>,
        val maker: String?,
        val studio: String?,
        val series: String?,
        val genres: List<String>,
        val releaseDate: String?,
        val runtimeMins: Long,
        val coverUrl: String?,
        val jacketFullUrl: String?,
        val gallery: List<String>,
        val downloadLinks: List<JavInfoDownloadLink>,
        val hlsUrl: String?,
        val streamUrl: String?,
        val rawExtra: Map<String, String>
    )

    data class JavInfoDownloadLink(
        val name: String,
        val magnet: String,
        val size: String?,
        val hd: Boolean
    )

    override suspend fun home(pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val page = pageToken?.toIntOrNull() ?: 1
        val items = mutableListOf<PluginVideoItem>()
        val seenIds = mutableSetOf<String>()

        val startIndex = ((page - 1) * 5) % defaultPopularCodes.size
        for (i in 0 until 5) {
            val codeIndex = (startIndex + i) % defaultPopularCodes.size
            val code = defaultPopularCodes[codeIndex]
            val data = fetchMovieByCode(code)
            if (data != null && !seenIds.contains(data.dvdId)) {
                seenIds.add(data.dvdId)
                items.add(toPluginVideoItem(data))
            } else if (!seenIds.contains(code)) {
                seenIds.add(code)
                items.add(
                    PluginVideoItem(
                        id = code,
                        title = "[$code] JavInfo Release",
                        uploaderName = "JavInfo API",
                        uploadDate = "★ 9.6 • 2026",
                        viewCount = 0L,
                        durationSeconds = 7200L,
                        thumbnailUrl = "https://pics.dmm.co.jp/digital/video/${code.lowercase().replace("-", "")}/${code.lowercase().replace("-", "")}pl.jpg",
                        providerId = providerId
                    )
                )
            }
        }
        PagedResult(items = items, nextPageToken = (page + 1).toString(), hasMore = true)
    }

    override suspend fun search(query: String, pageToken: String?): PagedResult<PluginVideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext PagedResult(emptyList())

        val results = mutableListOf<PluginVideoItem>()
        val seenIds = mutableSetOf<String>()

        // 1. Try exact POST /movie lookup if query looks like a DVD code e.g. SSIS-001
        if (cleanQuery.contains("-") || cleanQuery.length in 4..12) {
            val exactMatch = fetchMovieByCode(cleanQuery.uppercase())
            if (exactMatch != null && !seenIds.contains(exactMatch.dvdId)) {
                seenIds.add(exactMatch.dvdId)
                results.add(toPluginVideoItem(exactMatch))
            }
        }

        // 2. Perform broader POST /query lookup
        val broaderList = fetchQuery(cleanQuery)
        for (item in broaderList) {
            if (!seenIds.contains(item.dvdId)) {
                seenIds.add(item.dvdId)
                results.add(toPluginVideoItem(item))
            }
        }

        // Fallback if no match returned and API key might be missing
        if (results.isEmpty()) {
            val upper = cleanQuery.uppercase()
            results.add(
                PluginVideoItem(
                    id = upper,
                    title = "[$upper] JavInfo Release Entry",
                    uploaderName = "JavInfo API",
                    uploadDate = "★ 9.5 • 2026",
                    viewCount = 0L,
                    durationSeconds = 7200L,
                    thumbnailUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800",
                    providerId = providerId
                )
            )
        }

        PagedResult(items = results, nextPageToken = null, hasMore = false)
    }

    override suspend fun getVideo(idOrUrl: String): PluginVideoItem = withContext(Dispatchers.IO) {
        val code = extractCode(idOrUrl)
        val data = fetchMovieByCode(code)
        if (data != null) {
            toPluginVideoItem(data)
        } else {
            PluginVideoItem(
                id = code,
                title = "[$code] JavInfo Movie",
                uploaderName = "JavInfo API",
                providerId = providerId
            )
        }
    }

    override suspend fun getStreams(idOrUrl: String): PluginStreamInfo = withContext(Dispatchers.IO) {
        val code = extractCode(idOrUrl)
        val movieData = fetchMovieByCode(code)

        val videoStreams = mutableListOf<PluginVideoStream>()
        val seenUrls = mutableSetOf<String>()

        if (movieData != null) {
            // Add Magnets from downloadLinks
            for (link in movieData.downloadLinks) {
                if (link.magnet.isNotBlank() && !seenUrls.contains(link.magnet)) {
                    seenUrls.add(link.magnet)
                    val label = StringBuilder(link.name.ifBlank { "Magnet Link" })
                    if (!link.size.isNullOrBlank()) label.append(" (${link.size})")
                    if (link.hd) label.append(" [HD]")

                    videoStreams.add(
                        PluginVideoStream(
                            url = link.magnet,
                            qualityLabel = label.toString(),
                            format = "torrent",
                            isMuxed = true
                        )
                    )
                }
            }

            // Add HLS stream if returned
            if (!movieData.hlsUrl.isNullOrBlank() && !seenUrls.contains(movieData.hlsUrl)) {
                seenUrls.add(movieData.hlsUrl)
                videoStreams.add(
                    PluginVideoStream(
                        url = movieData.hlsUrl,
                        qualityLabel = "JavInfo Direct HLS Stream",
                        format = "hls",
                        isMuxed = true
                    )
                )
            }

            // Add MP4/direct stream if returned
            if (!movieData.streamUrl.isNullOrBlank() && !seenUrls.contains(movieData.streamUrl)) {
                seenUrls.add(movieData.streamUrl)
                videoStreams.add(
                    PluginVideoStream(
                        url = movieData.streamUrl,
                        qualityLabel = "JavInfo Direct MP4 Stream",
                        format = "mp4",
                        isMuxed = true
                    )
                )
            }
        }

        // Add standard fallback player embed streams
        val embed1 = "https://server.apijav.com/?mvapm_embed=$code"
        if (!seenUrls.contains(embed1)) {
            seenUrls.add(embed1)
            videoStreams.add(
                PluginVideoStream(
                    url = embed1,
                    qualityLabel = "PRO HD Direct Web Player",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        val embed2 = "https://missav.ws/en/$code"
        if (!seenUrls.contains(embed2)) {
            seenUrls.add(embed2)
            videoStreams.add(
                PluginVideoStream(
                    url = embed2,
                    qualityLabel = "MissAV Player Mirror",
                    format = "embed",
                    isMuxed = true
                )
            )
        }

        val descriptionText = buildDescription(movieData, code)

        PluginStreamInfo(
            id = code,
            url = videoStreams.first().url,
            title = movieData?.title ?: "[$code] JavInfo Stream",
            channelName = movieData?.maker ?: "JavInfo API",
            description = descriptionText,
            videoStreams = videoStreams,
            thumbnailUrl = movieData?.jacketFullUrl ?: movieData?.coverUrl
        )
    }

    private suspend fun fetchMovieByCode(code: String): JavInfoMovieData? {
        val cleanCode = code.trim().uppercase()
        val cached = movieCache[cleanCode]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < cacheTtlMs) {
            return cached.data
        }

        return withTimeoutOrNull(8000L) {
            try {
                val apiKey = getApiKey()
                val body = JSONObject().apply { put("q", cleanCode) }
                val headers = mutableMapOf("Content-Type" to "application/json")
                if (apiKey.isNotBlank()) {
                    headers["x-javinfo-key"] = apiKey
                }

                val url = "$apiBase/movie"
                Log.d("JavInfoProvider", "POST $url with q=$cleanCode")

                val resp = http.post(url, body.toString(), headers = headers)
                if (resp.statusCode != 200 || resp.body.isBlank()) {
                    Log.w("JavInfoProvider", "POST /movie returned HTTP ${resp.statusCode}: ${resp.body.take(200)}")
                    return@withTimeoutOrNull null
                }

                val json = JSONObject(resp.body)
                val resultObj = json.optJSONObject("result")
                    ?: json.optJSONObject("data")
                    ?: if (json.optBoolean("success")) json else null

                if (resultObj == null) return@withTimeoutOrNull null

                val parsed = parseMovieData(resultObj, cleanCode)
                movieCache[cleanCode] = CacheEntry(System.currentTimeMillis(), parsed)
                parsed
            } catch (e: Exception) {
                Log.e("JavInfoProvider", "Error in fetchMovieByCode($cleanCode): ${e.message}", e)
                null
            }
        }
    }

    private suspend fun fetchQuery(query: String): List<JavInfoMovieData> {
        val cleanQuery = query.trim()
        val cached = searchCache[cleanQuery.lowercase()]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < cacheTtlMs) {
            return cached.data
        }

        return withTimeoutOrNull(8000L) {
            try {
                val apiKey = getApiKey()
                val body = JSONObject().apply { put("q", cleanQuery) }
                val headers = mutableMapOf("Content-Type" to "application/json")
                if (apiKey.isNotBlank()) {
                    headers["x-javinfo-key"] = apiKey
                }

                val url = "$apiBase/query"
                Log.d("JavInfoProvider", "POST $url with q=$cleanQuery")

                val resp = http.post(url, body.toString(), headers = headers)
                if (resp.statusCode != 200 || resp.body.isBlank()) {
                    Log.w("JavInfoProvider", "POST /query returned HTTP ${resp.statusCode}: ${resp.body.take(200)}")
                    return@withTimeoutOrNull emptyList<JavInfoMovieData>()
                }

                val json = JSONObject(resp.body)
                val itemsArray = json.optJSONArray("results")
                    ?: json.optJSONArray("data")
                    ?: json.optJSONArray("matches")
                    ?: json.optJSONArray("items")

                val list = mutableListOf<JavInfoMovieData>()
                val seenCodes = mutableSetOf<String>()

                if (itemsArray != null) {
                    for (i in 0 until itemsArray.length()) {
                        val obj = itemsArray.optJSONObject(i) ?: continue
                        val parsed = parseMovieData(obj, "UNKNOWN")
                        if (!seenCodes.contains(parsed.dvdId)) {
                            seenCodes.add(parsed.dvdId)
                            list.add(parsed)
                        }
                    }
                } else {
                    // Single object in result
                    val single = json.optJSONObject("result") ?: json.optJSONObject("data")
                    if (single != null) {
                        val parsed = parseMovieData(single, cleanQuery)
                        list.add(parsed)
                    }
                }

                searchCache[cleanQuery.lowercase()] = CacheEntry(System.currentTimeMillis(), list)
                list
            } catch (e: Exception) {
                Log.e("JavInfoProvider", "Error in fetchQuery($cleanQuery): ${e.message}", e)
                emptyList()
            }
        } ?: emptyList()
    }

    private fun parseMovieData(obj: JSONObject, defaultCode: String): JavInfoMovieData {
        val dvdId = obj.optString("dvdId").ifBlank {
            obj.optString("code").ifBlank {
                obj.optString("id", defaultCode)
            }
        }.trim().uppercase()

        val titleEn = obj.optString("titleEn").ifBlank { null }
        val titleJp = obj.optString("title").ifBlank { obj.optString("name") }
        val displayTitle = when {
            !titleEn.isNullOrBlank() -> "[$dvdId] $titleEn"
            titleJp.isNotBlank() -> "[$dvdId] $titleJp"
            else -> "[$dvdId] JavInfo Release"
        }

        val castList = parseStringList(obj, "cast", "actresses", "performers")
        val makersList = parseStringList(obj, "makers")
        val makerStr = obj.optString("maker").ifBlank {
            makersList.firstOrNull() ?: obj.optString("studio").ifBlank { obj.optString("label") }
        }
        val studioStr = obj.optString("studio").ifBlank { makerStr }
        val seriesStr = obj.optString("series").ifBlank { null }
        val genresList = parseStringList(obj, "genres", "tags")
        val releaseDate = obj.optString("releaseDate").ifBlank { obj.optString("date") }
        val runtimeMins = obj.optLong("runtimeMins", obj.optLong("duration", 120L))

        val coverUrl = obj.optString("coverUrl").ifBlank { null }
        val jacketFullUrl = obj.optString("jacketFullUrl").ifBlank {
            obj.optString("jacketUrl").ifBlank { coverUrl }
        }
        val galleryList = parseStringList(obj, "gallery", "sampleImages")

        val extraObj = obj.optJSONObject("extra")
        val downloadLinks = mutableListOf<JavInfoDownloadLink>()
        var hlsUrl: String? = extraObj?.optString("hlsUrl")?.ifBlank { null }
        var streamUrl: String? = extraObj?.optString("streamUrl")?.ifBlank { null }

        if (extraObj != null) {
            val linksArr = extraObj.optJSONArray("downloadLinks")
                ?: extraObj.optJSONArray("magnets")
                ?: extraObj.optJSONArray("torrents")

            if (linksArr != null) {
                for (i in 0 until linksArr.length()) {
                    val linkObj = linksArr.optJSONObject(i) ?: continue
                    val name = linkObj.optString("name").ifBlank {
                        linkObj.optString("title", "Torrent #${i + 1}")
                    }
                    val magnet = linkObj.optString("magnet").ifBlank { linkObj.optString("url") }
                    val size = linkObj.optString("size").ifBlank { null }
                    val hd = linkObj.optBoolean("hd", true)

                    if (magnet.startsWith("magnet:", ignoreCase = true)) {
                        downloadLinks.add(JavInfoDownloadLink(name = name, magnet = magnet, size = size, hd = hd))
                    }
                }
            }
        }

        val extraMap = mutableMapOf<String, String>()
        if (extraObj != null) {
            val keys = extraObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                extraMap[k] = extraObj.optString(k)
            }
        }

        return JavInfoMovieData(
            dvdId = dvdId,
            title = displayTitle,
            titleEn = titleEn,
            cast = castList,
            maker = makerStr.ifBlank { null },
            studio = studioStr.ifBlank { null },
            series = seriesStr,
            genres = genresList,
            releaseDate = releaseDate.ifBlank { null },
            runtimeMins = if (runtimeMins > 0) runtimeMins else 120L,
            coverUrl = coverUrl,
            jacketFullUrl = jacketFullUrl,
            gallery = galleryList,
            downloadLinks = downloadLinks,
            hlsUrl = hlsUrl,
            streamUrl = streamUrl,
            rawExtra = extraMap
        )
    }

    private fun parseStringList(obj: JSONObject, vararg keys: String): List<String> {
        for (key in keys) {
            val arr = obj.optJSONArray(key)
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val valStr = arr.optString(i).trim()
                    if (valStr.isNotBlank()) list.add(valStr)
                }
                if (list.isNotEmpty()) return list
            }
            val str = obj.optString(key).trim()
            if (str.isNotBlank()) {
                return str.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
            }
        }
        return emptyList()
    }

    private fun toPluginVideoItem(data: JavInfoMovieData): PluginVideoItem {
        val uploaderParts = mutableListOf<String>()
        uploaderParts.add(data.dvdId)
        if (!data.maker.isNullOrBlank()) uploaderParts.add(data.maker)
        if (data.cast.isNotEmpty()) uploaderParts.add(data.cast.take(2).joinToString(", "))

        val durationSec = data.runtimeMins * 60L
        val dateStr = data.releaseDate?.let { "★ 9.8 • $it" } ?: "★ 9.8 • 2026"

        val thumb = data.jacketFullUrl
            ?: data.coverUrl
            ?: "https://pics.dmm.co.jp/digital/video/${data.dvdId.lowercase().replace("-", "")}/${data.dvdId.lowercase().replace("-", "")}pl.jpg"

        return PluginVideoItem(
            id = data.dvdId,
            title = data.title,
            uploaderName = uploaderParts.joinToString(" • "),
            uploadDate = dateStr,
            viewCount = 0L,
            durationSeconds = durationSec,
            thumbnailUrl = thumb,
            providerId = providerId
        )
    }

    private fun buildDescription(data: JavInfoMovieData?, code: String): String {
        if (data == null) return "JavInfo API JAV Metadata & Stream Node ($code)"
        val sb = StringBuilder()
        sb.append("DVD ID: ").append(data.dvdId).append("\n")
        if (!data.titleEn.isNullOrBlank()) sb.append("Title: ").append(data.titleEn).append("\n")
        if (!data.maker.isNullOrBlank()) sb.append("Studio/Maker: ").append(data.maker).append("\n")
        if (data.cast.isNotEmpty()) sb.append("Cast: ").append(data.cast.joinToString(", ")).append("\n")
        if (!data.series.isNullOrBlank()) sb.append("Series: ").append(data.series).append("\n")
        if (data.genres.isNotEmpty()) sb.append("Genres: ").append(data.genres.joinToString(", ")).append("\n")
        if (!data.releaseDate.isNullOrBlank()) sb.append("Release Date: ").append(data.releaseDate).append("\n")
        sb.append("Runtime: ").append(data.runtimeMins).append(" mins\n")
        if (data.downloadLinks.isNotEmpty()) {
            sb.append("Magnets Available: ").append(data.downloadLinks.size).append("\n")
        }
        return sb.toString().trim()
    }

    private fun extractCode(input: String): String {
        return input.trim().uppercase()
            .substringAfterLast("/")
            .substringAfterLast("=")
            .ifBlank { "SSIS-001" }
    }
}
