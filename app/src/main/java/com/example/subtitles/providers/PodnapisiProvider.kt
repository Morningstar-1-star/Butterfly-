package com.example.subtitles.providers

import android.util.Log
import com.example.subtitles.SubtitleFormat
import com.example.subtitles.SubtitleItem
import com.example.subtitles.SubtitleProvider
import com.example.subtitles.SubtitleSearchQuery
import com.example.subtitles.SubtitleSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Podnapisi Subtitle Provider.
 * Adapted from Bazarr Provider Catalog (LavX/bazarr-provider-catalog).
 * Parses multilingual community subtitles, hearing impaired badges, and season/episode matches.
 */
class PodnapisiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : SubtitleProvider {

    companion object {
        private const val TAG = "PodnapisiProvider"
        private const val BASE_URL = "https://www.podnapisi.net"
    }

    override val id: String = "podnapisi"
    override val name: String = "Podnapisi"

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val title = query.title.ifBlank { query.releaseName ?: "" }
        if (title.isBlank()) return@withContext emptyList()

        try {
            val encTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
            val seasonParam = query.season?.let { "&seasons=$it" } ?: ""
            val episodeParam = query.episode?.let { "&episodes=$it" } ?: ""
            val yearParam = query.year?.let { "&year=$it" } ?: ""

            val url = "$BASE_URL/subtitles/search/?keywords=$encTitle$yearParam$seasonParam$episodeParam"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val html = resp.body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(html, BASE_URL)

            val rows = doc.select("table.table tbody tr")
            val results = mutableListOf<SubtitleItem>()

            for (row in rows) {
                val titleEl = row.select("td:nth-child(1) a").first() ?: continue
                val href = titleEl.attr("href")
                val releaseName = titleEl.text().trim()

                val langEl = row.select("td:nth-child(2) span.flag").first()
                val langCode = langEl?.attr("class")?.substringAfter("flag-")?.trim() ?: "en"
                val isHi = row.select("i.fa-hearing-impaired, i.fa-deaf").isNotEmpty()

                val dlEl = row.select("td.text-center a.btn-download, td a[href*=download]").first()
                val dlHref = dlEl?.attr("href") ?: "$href/download"
                val fullDlUrl = if (dlHref.startsWith("http")) dlHref else "$BASE_URL$dlHref"

                val subId = href.substringAfterLast("/").ifBlank { releaseName.hashCode().toString() }

                results.add(
                    SubtitleItem(
                        id = "podnapisi_$subId",
                        providerId = id,
                        providerName = name,
                        title = releaseName,
                        languageCode = langCode,
                        languageName = langCode.uppercase(),
                        format = SubtitleFormat.SRT,
                        downloadUrl = fullDlUrl,
                        isHearingImpaired = isHi,
                        matchScore = 80,
                        sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                        releaseInfo = releaseName
                    )
                )

                if (results.size >= 25) break
            }

            results
        } catch (e: Exception) {
            Log.w(TAG, "Podnapisi search failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val bytes = resp.body?.bytes() ?: return@withContext null

            // Check if zip archive
            if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                ZipInputStream(bytes.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".srt", ignoreCase = true) ||
                            entry.name.endsWith(".vtt", ignoreCase = true) ||
                            entry.name.endsWith(".ass", ignoreCase = true)) {
                            return@withContext zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry = zis.nextEntry
                    }
                }
            } else {
                return@withContext String(bytes, Charsets.UTF_8)
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Podnapisi fetch error: ${e.message}")
            null
        }
    }
}
