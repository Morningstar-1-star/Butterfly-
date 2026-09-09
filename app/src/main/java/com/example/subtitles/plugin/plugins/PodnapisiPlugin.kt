package com.example.subtitles.plugin.plugins

import android.util.Log
import com.example.subtitles.SubtitleFormat
import com.example.subtitles.SubtitleItem
import com.example.subtitles.SubtitleSearchQuery
import com.example.subtitles.SubtitleSourceType
import com.example.subtitles.plugin.SubtitlePlugin
import com.example.subtitles.plugin.SubtitlePluginInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Podnapisi Subtitle Provider Plugin (Ported from Bazarr Provider Catalog).
 * Scrapes community subtitle listings with strict connection timeouts to preserve fast searches.
 */
class PodnapisiPlugin(
    override val info: SubtitlePluginInfo = SubtitlePluginInfo(
        id = "podnapisi",
        name = "Podnapisi",
        description = "Community subtitle catalog from Bazarr. Broad multi-language coverage & HI flags.",
        author = "Bazarr / Podnapisi Community",
        version = "1.1.0",
        requiresApiKey = false,
        supportsFps = true,
        supportsHi = true,
        websiteUrl = "https://www.podnapisi.net"
    )
) : SubtitlePlugin {

    companion object {
        private const val TAG = "PodnapisiPlugin"
        private const val BASE_URL = "https://www.podnapisi.net"
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val title = query.title.ifBlank { query.releaseName ?: "" }.trim()
        if (title.isBlank()) return@withContext emptyList()

        val results = mutableListOf<SubtitleItem>()
        try {
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val seasonParam = query.season?.let { "&seasons=$it" } ?: ""
            val episodeParam = query.episode?.let { "&episodes=$it" } ?: ""
            val yearParam = query.year?.let { "&year=$it" } ?: ""

            val url = "$BASE_URL/subtitles/search/?keywords=$encTitle$yearParam$seasonParam$episodeParam"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Butterfly/2.0")
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val html = resp.body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(html, BASE_URL)

            val rows = doc.select("table.table tbody tr")
            for (row in rows.take(15)) {
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
                        releaseInfo = releaseName,
                        season = query.season,
                        episode = query.episode
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Podnapisi search timed out or failed: ${e.message}")
        }
        results
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Butterfly/2.0")
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val bytes = resp.body?.bytes() ?: return@withContext null

            // Detect and extract zip archive if applicable
            if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".srt", ignoreCase = true) ||
                            entry.name.endsWith(".vtt", ignoreCase = true) ||
                            entry.name.endsWith(".ass", ignoreCase = true)
                        ) {
                            val text = zis.bufferedReader(StandardCharsets.UTF_8).readText()
                            zis.closeEntry()
                            return@withContext text
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } else {
                return@withContext String(bytes, StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Podnapisi fetch error: ${e.message}")
        }
        null
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url(BASE_URL)
                .header("User-Agent", "Mozilla/5.0 Butterfly/2.0")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - start
                if (resp.isSuccessful) {
                    Result.success("Connected (${elapsed}ms)")
                } else {
                    Result.failure(Exception("HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
