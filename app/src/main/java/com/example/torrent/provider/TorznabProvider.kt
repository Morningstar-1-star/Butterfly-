package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentResult
import com.example.torrent.protocol.MagnetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Generic Torznab / Newznab Torrent Indexer Provider (Adapted from Jackett & Prowlarr specifications).
 * Connects to any standard self-hosted or remote Torznab endpoint (e.g. Jackett / Prowlarr)
 * using standard Torznab RSS/XML parameters (t=search, t=movie, t=tvsearch, cat, imdbid).
 */
class TorznabProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val customEndpoint: String? = null,
    private val customApiKey: String? = null,
    override val id: String = "torznab",
    override val name: String = "Torznab Indexer"
) : TorrentProvider {

    companion object {
        private const val TAG = "TorznabProvider"
    }

    override val isEnabled: Boolean
        get() = getEffectiveEndpoint().isNotBlank()

    private fun getEffectiveEndpoint(): String {
        return (customEndpoint ?: com.example.util.PlaybackPreferences.torznabBaseUrl).trim()
    }

    private fun getEffectiveApiKey(): String {
        return (customApiKey ?: com.example.util.PlaybackPreferences.torznabApiKey).trim()
    }

    override suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult> = withContext(Dispatchers.IO) {
        val endpoint = getEffectiveEndpoint().trimEnd('/')
        if (endpoint.isBlank()) return@withContext emptyList()

        val apiKey = getEffectiveApiKey()
        val imdbId = identity.imdbId?.trim()

        val searchType = when {
            identity.mediaType.equals("tv", ignoreCase = true) && identity.season != null -> "tvsearch"
            identity.mediaType.equals("movie", ignoreCase = true) && !imdbId.isNullOrBlank() -> "movie"
            else -> "search"
        }

        val urlBuilder = StringBuilder("$endpoint/api?apikey=$apiKey&t=$searchType")

        if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt")) {
            urlBuilder.append("&imdbid=").append(imdbId.removePrefix("tt"))
        }

        if (identity.season != null) {
            urlBuilder.append("&season=").append(identity.season)
        }
        if (identity.episode != null) {
            urlBuilder.append("&ep=").append(identity.episode)
        }

        val searchTerm = when {
            identity.title.isNotBlank() -> identity.title
            query.isNotBlank() -> query
            else -> ""
        }

        if (searchTerm.isNotBlank()) {
            val enc = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8.name())
            urlBuilder.append("&q=").append(enc)
        }

        val finalUrl = urlBuilder.toString()

        try {
            val req = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "Butterfly/1.0 TorznabClient")
                .header("Accept", "application/xml,text/xml")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val xmlBody = resp.body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(xmlBody, "", Parser.xmlParser())
            val items = doc.select("item")
            if (items.isEmpty()) return@withContext emptyList()

            val results = mutableListOf<TorrentResult>()

            for (item in items) {
                val title = item.select("title").text().trim()
                if (title.isBlank()) continue

                var magnetUrl = ""
                var infoHash = ""
                var seeders = 0
                var leechers = 0
                var sizeBytes = 0L
                var categoryId = ""

                // Extract torznab:attr elements
                val attrs = item.select("torznab|attr")
                for (attr in attrs) {
                    val attrName = attr.attr("name").lowercase()
                    val attrValue = attr.attr("value")
                    when (attrName) {
                        "seeders", "seeds" -> seeders = attrValue.toIntOrNull() ?: seeders
                        "peers", "leechers", "leeches" -> leechers = attrValue.toIntOrNull() ?: leechers
                        "infohash" -> infoHash = attrValue.trim()
                        "magneturl" -> magnetUrl = attrValue.trim()
                        "size" -> sizeBytes = attrValue.toLongOrNull() ?: sizeBytes
                        "category", "cat" -> categoryId = attrValue
                    }
                }

                // If magnet/size not in attributes, check enclosure or link
                if (sizeBytes == 0L) {
                    val encLength = item.select("enclosure").attr("length").toLongOrNull()
                    if (encLength != null && encLength > 0) sizeBytes = encLength
                    if (sizeBytes == 0L) {
                        sizeBytes = item.select("size").text().trim().toLongOrNull() ?: 0L
                    }
                }

                if (magnetUrl.isBlank()) {
                    val linkText = item.select("link").text().trim()
                    if (linkText.startsWith("magnet:?")) {
                        magnetUrl = linkText
                    }
                }

                if (infoHash.isBlank() && magnetUrl.isNotBlank()) {
                    val parsed = MagnetParser.parse(magnetUrl)
                    if (parsed != null) infoHash = parsed.infoHashHex
                }

                if (infoHash.isBlank()) continue

                val finalMagnet = if (magnetUrl.isNotBlank()) magnetUrl else MagnetParser.buildMagnetUrl(infoHash, title)

                val category = when {
                    categoryId.startsWith("2") || identity.mediaType.equals("movie", true) -> "Movies"
                    categoryId.startsWith("5") || identity.mediaType.equals("tv", true) -> "TV"
                    categoryId == "5070" || identity.mediaType.equals("anime", true) -> "Anime"
                    categoryId.startsWith("6") || identity.mediaType.equals("jav", true) -> "JAV/Adult"
                    else -> "Other"
                }

                val quality = when {
                    title.contains("2160p", ignoreCase = true) || title.contains("4K", ignoreCase = true) -> "4K UHD"
                    title.contains("1080p", ignoreCase = true) -> "1080p"
                    title.contains("720p", ignoreCase = true) -> "720p"
                    title.contains("480p", ignoreCase = true) -> "480p"
                    else -> "1080p"
                }

                val codec = when {
                    title.contains("x265", ignoreCase = true) || title.contains("HEVC", ignoreCase = true) -> "x265 HEVC"
                    title.contains("AV1", ignoreCase = true) -> "AV1"
                    title.contains("x264", ignoreCase = true) || title.contains("H.264", ignoreCase = true) -> "x264"
                    else -> ""
                }

                val pubDate = item.select("pubDate").text().trim()

                results.add(
                    TorrentResult(
                        title = title,
                        magnet = finalMagnet,
                        infoHash = infoHash.lowercase(),
                        size = sizeBytes,
                        formattedSize = TorrentResult.formatBytes(sizeBytes),
                        seeders = seeders,
                        leechers = leechers,
                        source = name,
                        category = category,
                        quality = quality,
                        codec = codec,
                        season = identity.season,
                        episode = identity.episode,
                        uploadDate = pubDate
                    )
                )
            }

            Log.i(TAG, "Torznab returned ${results.size} items for $searchTerm")
            results
        } catch (e: Exception) {
            Log.w(TAG, "Torznab query failed: ${e.message}")
            emptyList()
        }
    }
}
