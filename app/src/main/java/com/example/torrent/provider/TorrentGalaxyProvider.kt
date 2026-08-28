package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentResult
import com.example.torrent.protocol.MagnetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * TorrentGalaxy (TGx) Torrent Indexer Provider (Adapted from Magnetio multi-indexer logic).
 * Directly scrapes high-speed TGx search tables with embedded magnet links, seeders, and categories.
 */
class TorrentGalaxyProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : TorrentProvider {

    override val id: String = "torrentgalaxy"
    override val name: String = "TorrentGalaxy"
    override val isEnabled: Boolean = true

    companion object {
        private const val TAG = "TorrentGalaxyProvider"
        private val MIRRORS = listOf(
            "https://torrentgalaxy.to",
            "https://torrentgalaxy.mx",
            "https://torrentgalaxy.su",
            "https://tgx.rs"
        )
    }

    override suspend fun search(query: String, identity: MediaIdentity): List<TorrentResult> = withContext(Dispatchers.IO) {
        val searchKeyword = when {
            identity.title.isNotBlank() -> {
                var q = identity.title
                if (identity.season != null && identity.episode != null) {
                    q = "$q S${String.format(Locale.US, "%02d", identity.season)}E${String.format(Locale.US, "%02d", identity.episode)}"
                } else if (identity.year != null && identity.year.isNotBlank() && !q.contains(identity.year)) {
                    q = "$q ${identity.year}"
                }
                q
            }
            query.isNotBlank() -> query
            else -> return@withContext emptyList()
        }

        val results = mutableListOf<TorrentResult>()

        for (baseMirror in MIRRORS) {
            try {
                val encodedQuery = URLEncoder.encode(searchKeyword.trim(), StandardCharsets.UTF_8.name())
                val searchUrl = "$baseMirror/torrents.php?search=$encodedQuery&sort=seeders&order=desc"

                val req = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue

                val html = resp.body?.string() ?: continue
                val doc = Jsoup.parse(html, baseMirror)
                val rows = doc.select("div.tgxtablerow")
                if (rows.isEmpty()) continue

                for (row in rows) {
                    // Extract title
                    val titleEl = row.select("div.tgxtablecell a.txlight").firstOrNull()
                        ?: row.select("div.tgxtablecell a[title]").firstOrNull()
                    val title = titleEl?.attr("title")?.ifBlank { titleEl.text() }?.trim() ?: continue

                    // Extract direct magnet link
                    val magnetEl = row.select("a[href^=\"magnet:?xt=\"]").firstOrNull()
                    val magnetUrl = magnetEl?.attr("href")?.trim() ?: ""
                    if (magnetUrl.isBlank()) continue

                    val parsedMagnet = MagnetParser.parse(magnetUrl)
                    val infoHash = parsedMagnet?.infoHashHex ?: ""
                    if (infoHash.isBlank()) continue

                    // Extract seeders & leechers
                    val seedersEl = row.select("span[title=\"Seeders/Leechers\"] font b").firstOrNull()
                        ?: row.select("span[title*=\"Seeders\"] font b").firstOrNull()
                        ?: row.select("span font[color=\"green\"] b").firstOrNull()
                    val seeders = seedersEl?.text()?.trim()?.toIntOrNull() ?: 0

                    val leechersEl = row.select("span[title=\"Seeders/Leechers\"] font b").getOrNull(1)
                        ?: row.select("span[title*=\"Leechers\"] font b").firstOrNull()
                        ?: row.select("span font[color*=\"red\"] b").firstOrNull()
                    val leechers = leechersEl?.text()?.trim()?.toIntOrNull() ?: 0

                    // Extract size
                    val sizeEl = row.select("span.badge-secondary").firstOrNull()
                    val sizeText = sizeEl?.text()?.trim() ?: ""
                    val sizeBytes = parseSizeToBytes(sizeText)

                    // Extract category
                    val catEl = row.select("a[href*=\"/torrents.php?parent_cat=\"]").firstOrNull()
                    val catText = catEl?.text()?.trim() ?: ""
                    val category = when {
                        catText.contains("Movie", ignoreCase = true) || identity.mediaType.equals("movie", true) -> "Movies"
                        catText.contains("TV", ignoreCase = true) || identity.mediaType.equals("tv", true) -> "TV"
                        catText.contains("Anime", ignoreCase = true) || identity.mediaType.equals("anime", true) -> "Anime"
                        catText.contains("XXX", ignoreCase = true) || identity.mediaType.equals("jav", true) -> "JAV/Adult"
                        catText.contains("Music", ignoreCase = true) -> "Music"
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

                    val hdr = when {
                        title.contains("DV", ignoreCase = true) && title.contains("HDR", ignoreCase = true) -> "Dolby Vision + HDR"
                        title.contains("Dolby Vision", ignoreCase = true) || title.contains("DV", ignoreCase = true) -> "Dolby Vision"
                        title.contains("HDR10+", ignoreCase = true) -> "HDR10+"
                        title.contains("HDR", ignoreCase = true) -> "HDR"
                        else -> ""
                    }

                    val audio = when {
                        title.contains("Atmos", ignoreCase = true) -> "Dolby Atmos"
                        title.contains("TrueHD", ignoreCase = true) -> "TrueHD"
                        title.contains("DTS", ignoreCase = true) -> "DTS-HD"
                        title.contains("5.1", ignoreCase = true) || title.contains("DD5.1", ignoreCase = true) -> "5.1 Surround"
                        else -> ""
                    }

                    results.add(
                        TorrentResult(
                            title = title,
                            magnet = magnetUrl,
                            infoHash = infoHash.lowercase(),
                            size = sizeBytes,
                            formattedSize = sizeText,
                            seeders = seeders,
                            leechers = leechers,
                            source = "TorrentGalaxy",
                            category = category,
                            quality = quality,
                            codec = codec,
                            hdr = hdr,
                            audioChannels = audio,
                            season = identity.season,
                            episode = identity.episode
                        )
                    )
                }

                if (results.isNotEmpty()) {
                    Log.i(TAG, "TorrentGalaxy returned ${results.size} entries from $baseMirror")
                    return@withContext results
                }
            } catch (e: Exception) {
                Log.w(TAG, "TorrentGalaxy mirror $baseMirror failed: ${e.message}")
            }
        }

        emptyList()
    }

    private fun parseSizeToBytes(sizeStr: String): Long {
        try {
            val trimmed = sizeStr.trim()
            val numStr = trimmed.replace(Regex("[^0-9.]"), "")
            val num = numStr.toDoubleOrNull() ?: return 0L
            return when {
                trimmed.contains("GB", ignoreCase = true) -> (num * 1024 * 1024 * 1024).toLong()
                trimmed.contains("MB", ignoreCase = true) -> (num * 1024 * 1024).toLong()
                trimmed.contains("KB", ignoreCase = true) -> (num * 1024).toLong()
                trimmed.contains("TB", ignoreCase = true) -> (num * 1024 * 1024 * 1024 * 1024).toLong()
                else -> 0L
            }
        } catch (_: Exception) {
            return 0L
        }
    }
}
