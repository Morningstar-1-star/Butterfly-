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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * SubtitleCat Subtitle Provider Plugin.
 * Scrapes and streams multi-language subtitles from SubtitleCat.com without requiring any API keys.
 * Supports direct download of original and translated SRT files.
 */
class SubtitleCatPlugin(
    override val info: SubtitlePluginInfo = SubtitlePluginInfo(
        id = "subtitlecat",
        name = "SubtitleCat",
        description = "Public multi-language subtitle library. Zero-config, supports 100+ languages without API keys.",
        author = "SubtitleCat Community",
        version = "1.2.0",
        requiresApiKey = false,
        supportsFps = false,
        supportsHi = false,
        websiteUrl = "https://www.subtitlecat.com"
    )
) : SubtitlePlugin {

    companion object {
        private const val TAG = "SubtitleCatPlugin"
        private const val BASE_URL = "https://www.subtitlecat.com"
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleItem>()
        val searchTerm = query.title.ifBlank { query.releaseName ?: "" }.trim()
        if (searchTerm.isBlank()) return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(searchTerm, "UTF-8")
            val searchUrl = "$BASE_URL/index.php?search=$encoded"

            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Butterfly/2.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml")
                .build()

            val html = okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext emptyList()

            val doc = Jsoup.parse(html, BASE_URL)
            val rows = doc.select("table.sub-table tbody tr")

            val targetLang = (query.languageCode ?: "en").lowercase()

            for (row in rows.take(15)) {
                val link = row.select("td:first-child a").first() ?: continue
                val href = link.attr("href")
                val releaseName = link.text().trim()
                if (releaseName.isBlank()) continue

                // Check size / downloads from table
                val sizeText = row.select(".sub-table__metric-value").first()?.text()?.trim()

                val pageUrl = if (href.startsWith("http")) href else "$BASE_URL/$href"

                // Construct direct download URL candidates based on SubtitleCat URL conventions:
                // Detail page: subs/1655/Inception.2010.1080p.BrRip.x264.html
                // Direct file: subs/1655/Inception.2010.1080p.BrRip.x264-orig.srt
                val subId = href.substringAfter("subs/").substringBefore(".html").replace("/", "_")

                results.add(
                    SubtitleItem(
                        id = "cat_$subId",
                        providerId = id,
                        providerName = name,
                        title = releaseName,
                        languageCode = targetLang,
                        languageName = targetLang.uppercase(),
                        format = SubtitleFormat.SRT,
                        downloadUrl = pageUrl,
                        matchScore = 82,
                        sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                        releaseInfo = releaseName,
                        resolution = extractResolution(releaseName),
                        season = query.season,
                        episode = query.episode
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "SubtitleCat search failed: ${e.message}")
        }
        results
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            // If downloadUrl points to HTML page, visit it to grab the direct .srt link
            val pageUrl = item.downloadUrl
            if (pageUrl.endsWith(".srt", ignoreCase = true)) {
                return@withContext downloadDirectText(pageUrl)
            }

            val req = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Butterfly/2.0")
                .build()

            val html = okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext null

            val doc = Jsoup.parse(html, BASE_URL)

            // Look for target language download button (e.g. id="download_en" or id="download_<lang>")
            val langCode = item.languageCode.lowercase()
            val specificDl = doc.select("a#download_$langCode, a[id*='download_$langCode']").first()
            val englishDl = doc.select("a#download_en").first()
            val anyDl = doc.select("a[href$='.srt'], a[id^='download_']").first()

            val chosenHref = specificDl?.attr("href")
                ?: englishDl?.attr("href")
                ?: anyDl?.attr("href")

            if (!chosenHref.isNullOrBlank()) {
                val fullUrl = if (chosenHref.startsWith("http")) chosenHref else "$BASE_URL$chosenHref"
                return@withContext downloadDirectText(fullUrl)
            }

            // Look for orig srt fallback in buttons
            val origBtn = doc.select("button[onclick*='-orig.srt']").first()
            if (origBtn != null) {
                val onclick = origBtn.attr("onclick")
                val filenameMatch = Regex("'(.*?-orig\\.srt)'").find(onclick)?.groupValues?.getOrNull(1)
                val folderMatch = Regex("'/subs/(\\d+)/'").find(onclick)?.groupValues?.getOrNull(1)
                if (filenameMatch != null && folderMatch != null) {
                    val fallbackUrl = "$BASE_URL/subs/$folderMatch/$filenameMatch"
                    return@withContext downloadDirectText(fallbackUrl)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SubtitleCat fetch failed: ${e.message}")
        }
        null
    }

    private fun downloadDirectText(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Butterfly/2.0")
            .header("Referer", BASE_URL)
            .build()

        return okHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
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

    private fun extractResolution(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("2160p") || lower.contains("4k") -> "2160p"
            lower.contains("1080p") -> "1080p"
            lower.contains("720p") -> "720p"
            lower.contains("480p") -> "480p"
            else -> null
        }
    }
}
