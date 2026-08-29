package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentResult
import com.example.torrent.protocol.MagnetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Torrentio Stremio Addon Provider.
 * Queries Torrentio stream endpoint with Real-Debrid / AllDebrid / P2P Swarm resolution.
 */
class TorrentioProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : TorrentProvider {

    override val id: String = "torrentio"
    override val name: String = "Torrentio"
    override val isEnabled: Boolean = true

    companion object {
        private const val TAG = "TorrentioProvider"
    }

    private val baseUrl: String get() = com.example.util.PlaybackPreferences.torrentioBaseUrl

    override suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult> = withContext(Dispatchers.IO) {
        val imdbId = identity.imdbId?.trim()
        if (imdbId.isNullOrBlank() || !imdbId.startsWith("tt")) {
            return@withContext emptyList()
        }

        var rootUrl = baseUrl.trim().trimEnd('/')
        if (rootUrl.endsWith("/manifest.json")) {
            rootUrl = rootUrl.removeSuffix("/manifest.json")
        }
        val debridKey = com.example.util.AppConfig.getDebridApiKey().trim()
        if (debridKey.isNotBlank() && !rootUrl.contains("realdebrid=") && !rootUrl.contains("alldebrid=") && !rootUrl.contains("premiumize=")) {
            rootUrl = "$rootUrl/realdebrid=$debridKey"
        }

        val url = if (identity.mediaType.equals("tv", ignoreCase = true) && identity.season != null && identity.episode != null) {
            "$rootUrl/stream/series/$imdbId:${identity.season}:${identity.episode}.json"
        } else {
            "$rootUrl/stream/movie/$imdbId.json"
        }

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly/1.0")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val body = resp.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val streams = json.optJSONArray("streams") ?: return@withContext emptyList()

            val results = mutableListOf<TorrentResult>()

            for (i in 0 until streams.length()) {
                val stream = streams.getJSONObject(i)
                val directUrl = stream.optString("url", "").trim()
                var infoHash = stream.optString("infoHash", "").trim()
                if (infoHash.isBlank() && directUrl.isBlank()) continue
                if (infoHash.isBlank()) {
                    infoHash = "debrid_" + Math.abs(directUrl.hashCode()).toString(16)
                }

                val streamName = stream.optString("name", "") // e.g. "[RD+] Torrentio\n1080p"
                val streamTitle = stream.optString("title", "") // e.g. "Spider-Man.No.Way.Home.2021.1080p.WEBRip.x264\n💾 2.4 GB 👤 142"

                val titleLines = streamTitle.split("\n")
                val releaseName = titleLines.firstOrNull()?.trim() ?: identity.title

                var seeders = if (directUrl.isNotBlank() || streamName.contains("RD") || streamName.contains("AD") || streamName.contains("PM")) 999 else 0
                var sizeBytes = 0L
                var formattedSize = ""

                for (line in titleLines) {
                    if (line.contains("👤") || line.contains("seed", ignoreCase = true)) {
                        val seederMatch = Regex("👤\\s*(\\d+)").find(line)
                        if (seederMatch != null) {
                            seeders = seederMatch.groupValues[1].toIntOrNull() ?: seeders
                        }
                    }
                    if (line.contains("💾") || line.contains("GB", ignoreCase = true) || line.contains("MB", ignoreCase = true)) {
                        val sizeMatch = Regex("💾\\s*([0-9.]+\\s*[GM]B)").find(line)
                        if (sizeMatch != null) {
                            formattedSize = sizeMatch.groupValues[1]
                            sizeBytes = TorrentResult.parseBytes(formattedSize)
                        }
                    }
                }

                if (sizeBytes <= 0L && formattedSize.isNotBlank()) {
                    sizeBytes = TorrentResult.parseBytes(formattedSize)
                }

                val quality = parseQuality(streamName + " " + streamTitle)
                val codec = parseCodec(streamTitle)
                val hdr = parseHdr(streamTitle)
                val audio = parseAudio(streamTitle)

                val magnetUrl = if (directUrl.isNotBlank()) directUrl else MagnetParser.buildMagnetUrl(infoHash, releaseName)

                val providerLabel = if (streamName.contains("[RD+]") || streamName.contains("RD")) {
                    "Torrentio (Real-Debrid)"
                } else if (streamName.contains("[AD+]") || streamName.contains("AD")) {
                    "Torrentio (AllDebrid)"
                } else if (streamName.contains("[PM+]") || streamName.contains("PM")) {
                    "Torrentio (Premiumize)"
                } else {
                    "Torrentio"
                }

                results.add(
                    TorrentResult(
                        title = releaseName,
                        magnet = magnetUrl,
                        infoHash = infoHash.lowercase(),
                        size = sizeBytes,
                        formattedSize = formattedSize,
                        seeders = seeders,
                        leechers = 0,
                        source = providerLabel,
                        category = if (identity.mediaType.equals("tv", ignoreCase = true)) "TV" else "Movies",
                        quality = quality,
                        codec = codec,
                        hdr = hdr,
                        audioChannels = audio,
                        season = identity.season,
                        episode = identity.episode
                    )
                )
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from Torrentio: ${e.message}")
            emptyList()
        }
    }

    private fun parseQuality(text: String): String {
        val upper = text.uppercase()
        return when {
            upper.contains("2160P") || upper.contains("4K") || upper.contains("UHD") -> "4K UHD"
            upper.contains("1080P") || upper.contains("FHD") -> "1080p"
            upper.contains("720P") || upper.contains("HD") -> "720p"
            upper.contains("480P") || upper.contains("SD") -> "480p"
            else -> "1080p"
        }
    }

    private fun parseCodec(text: String): String {
        val upper = text.uppercase()
        return when {
            upper.contains("HEVC") || upper.contains("X265") || upper.contains("H.265") || upper.contains("H265") -> "x265 HEVC"
            upper.contains("AV1") -> "AV1"
            upper.contains("X264") || upper.contains("H.264") || upper.contains("H264") || upper.contains("AVC") -> "x264"
            else -> ""
        }
    }

    private fun parseHdr(text: String): String {
        val upper = text.uppercase()
        return when {
            upper.contains("DV") && upper.contains("HDR") -> "Dolby Vision + HDR10"
            upper.contains("DOLBY VISION") || upper.contains("DV") -> "Dolby Vision"
            upper.contains("HDR10+") || upper.contains("HDR10PLUS") -> "HDR10+"
            upper.contains("HDR") -> "HDR"
            else -> ""
        }
    }

    private fun parseAudio(text: String): String {
        val upper = text.uppercase()
        return when {
            upper.contains("ATMOS") -> "Dolby Atmos"
            upper.contains("TRUEHD") -> "TrueHD 7.1"
            upper.contains("DTS-HD") || upper.contains("DTS") -> "DTS-HD"
            upper.contains("7.1") -> "7.1 Surround"
            upper.contains("5.1") || upper.contains("DD5.1") || upper.contains("EAC3") || upper.contains("AC3") -> "5.1 Surround"
            upper.contains("AAC") -> "AAC Stereo"
            else -> ""
        }
    }
}
