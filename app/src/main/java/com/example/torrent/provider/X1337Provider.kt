package com.example.torrent.provider

import android.util.Log
import com.example.torrent.model.TorrentResult
import com.example.torrent.protocol.MagnetParser
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 1337x Torrent Indexer Provider (Adapted from Magnetio multi-indexer logic).
 * Scrapes 1337x mirrors with categorization (Movies, TV, Anime, Adult/XXX)
 * and resolves magnet links asynchronously for top torrent entries.
 */
class X1337Provider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : TorrentProvider {

    override val id: String = "1337x"
    override val name: String = "1337x"
    override val isEnabled: Boolean = true

    companion object {
        private const val TAG = "X1337Provider"
        private val MIRRORS = listOf(
            "https://1337x.to",
            "https://1337x.st",
            "https://1337x.so",
            "https://1337x.ws"
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

        val categoryPath = when (identity.mediaType?.lowercase()) {
            "movie", "movies" -> "Movies"
            "tv", "series" -> "TV"
            "anime" -> "Anime"
            "jav", "xxx", "adult" -> "XXX"
            else -> null
        }

        for (baseMirror in MIRRORS) {
            try {
                val encodedQuery = URLEncoder.encode(searchKeyword.trim(), StandardCharsets.UTF_8.name())
                val searchUrl = if (categoryPath != null) {
                    "$baseMirror/category-search/$encodedQuery/$categoryPath/1/"
                } else {
                    "$baseMirror/search/$encodedQuery/1/"
                }

                val req = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue

                val html = resp.body?.string() ?: continue
                val doc = Jsoup.parse(html, baseMirror)
                val rows = doc.select("table.table-list tbody tr")
                if (rows.isEmpty()) continue

                val rawEntries = mutableListOf<Raw1337Entry>()

                for (row in rows) {
                    val nameLink = row.select("td.name a[href*=\"/torrent/\"]").firstOrNull() ?: continue
                    val title = nameLink.text().trim()
                    val detailHref = nameLink.attr("abs:href")
                    val seeders = row.select("td.seeds").text().trim().toIntOrNull() ?: 0
                    val leechers = row.select("td.leeches").text().trim().toIntOrNull() ?: 0
                    val sizeText = row.select("td.size").text().trim()
                    val dateText = row.select("td.coll-date").text().trim()

                    if (title.isBlank() || detailHref.isBlank()) continue

                    rawEntries.add(
                        Raw1337Entry(
                            title = title,
                            detailUrl = detailHref,
                            seeders = seeders,
                            leechers = leechers,
                            sizeText = sizeText,
                            uploadDate = dateText
                        )
                    )
                }

                if (rawEntries.isEmpty()) continue

                // Concurrently fetch magnet links for the top 10 results
                val topEntries = rawEntries.take(10)
                val results = supervisorScope {
                    topEntries.map { entry ->
                        async {
                            resolveMagnetFromDetail(baseMirror, entry, identity)
                        }
                    }.awaitAll().filterNotNull()
                }

                if (results.isNotEmpty()) {
                    Log.i(TAG, "1337x search resolved ${results.size} magnet entries from $baseMirror")
                    return@withContext results
                }
            } catch (e: Exception) {
                Log.w(TAG, "1337x mirror $baseMirror search error: ${e.message}")
            }
        }

        emptyList()
    }

    private suspend fun resolveMagnetFromDetail(
        baseMirror: String,
        entry: Raw1337Entry,
        identity: MediaIdentity
    ): TorrentResult? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(entry.detailUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", baseMirror)
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val html = resp.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html, baseMirror)
            val magnetLink = doc.select("a[href^=\"magnet:?xt=\"]").firstOrNull()?.attr("href")

            val parsedMagnet = if (!magnetLink.isNullOrBlank()) {
                MagnetParser.parse(magnetLink)
            } else {
                null
            }

            val infoHash = parsedMagnet?.infoHashHex ?: ""
            if (infoHash.isBlank()) return@withContext null

            val finalMagnet = magnetLink ?: MagnetParser.buildMagnetUrl(infoHash, entry.title)
            val sizeBytes = parseSizeToBytes(entry.sizeText)

            val quality = when {
                entry.title.contains("2160p", ignoreCase = true) || entry.title.contains("4K", ignoreCase = true) -> "4K UHD"
                entry.title.contains("1080p", ignoreCase = true) -> "1080p"
                entry.title.contains("720p", ignoreCase = true) -> "720p"
                entry.title.contains("480p", ignoreCase = true) -> "480p"
                else -> "1080p"
            }

            val codec = when {
                entry.title.contains("x265", ignoreCase = true) || entry.title.contains("HEVC", ignoreCase = true) -> "x265 HEVC"
                entry.title.contains("AV1", ignoreCase = true) -> "AV1"
                entry.title.contains("x264", ignoreCase = true) || entry.title.contains("H.264", ignoreCase = true) -> "x264"
                else -> ""
            }

            val hdr = when {
                entry.title.contains("DV", ignoreCase = true) && entry.title.contains("HDR", ignoreCase = true) -> "Dolby Vision + HDR"
                entry.title.contains("Dolby Vision", ignoreCase = true) || entry.title.contains("DV", ignoreCase = true) -> "Dolby Vision"
                entry.title.contains("HDR10+", ignoreCase = true) -> "HDR10+"
                entry.title.contains("HDR", ignoreCase = true) -> "HDR"
                else -> ""
            }

            val category = when {
                entry.detailUrl.contains("/Movies/", ignoreCase = true) || identity.mediaType.equals("movie", true) -> "Movies"
                entry.detailUrl.contains("/TV/", ignoreCase = true) || identity.mediaType.equals("tv", true) -> "TV"
                entry.detailUrl.contains("/Anime/", ignoreCase = true) || identity.mediaType.equals("anime", true) -> "Anime"
                entry.detailUrl.contains("/XXX/", ignoreCase = true) || identity.mediaType.equals("jav", true) -> "JAV/Adult"
                else -> "Other"
            }

            TorrentResult(
                title = entry.title,
                magnet = finalMagnet,
                infoHash = infoHash.lowercase(),
                size = sizeBytes,
                formattedSize = entry.sizeText,
                seeders = entry.seeders,
                leechers = entry.leechers,
                source = "1337x",
                category = category,
                quality = quality,
                codec = codec,
                hdr = hdr,
                season = identity.season,
                episode = identity.episode,
                uploadDate = entry.uploadDate
            )
        } catch (e: Exception) {
            null
        }
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

    private data class Raw1337Entry(
        val title: String,
        val detailUrl: String,
        val seeders: Int,
        val leechers: Int,
        val sizeText: String,
        val uploadDate: String
    )
}
