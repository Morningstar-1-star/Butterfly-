package com.example.subtitles.providers

import android.util.Log
import com.example.subtitles.SubtitleFormat
import com.example.subtitles.SubtitleItem
import com.example.subtitles.SubtitleProvider
import com.example.subtitles.SubtitleSearchQuery
import com.example.subtitles.SubtitleSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Subscene Subtitle Provider.
 * Adapted from Bazarr Provider Catalog (LavX/bazarr-provider-catalog).
 * Parses community-uploaded subtitles across 50+ languages with hearing-impaired badges.
 */
class SubsceneProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : SubtitleProvider {

    companion object {
        private const val TAG = "SubsceneProvider"
        private const val BASE_URL = "https://subscene.best"
    }

    override val id: String = "subscene"
    override val name: String = "Subscene"

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val title = query.title.ifBlank { query.releaseName ?: "" }
        if (title.isBlank()) return@withContext emptyList()

        try {
            val searchUrl = "$BASE_URL/subtitles/searchbytitle"
            val formBody = FormBody.Builder()
                .add("query", title)
                .build()

            val req = Request.Builder()
                .url(searchUrl)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val html = resp.body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(html, BASE_URL)

            // Find exact or popular search results
            val exactMatches = doc.select(".search-result ul li a, .title a")
            if (exactMatches.isEmpty()) return@withContext emptyList()

            // Select the most promising title page
            val titleHref = exactMatches.first()?.attr("href") ?: return@withContext emptyList()
            val detailUrl = if (titleHref.startsWith("http")) titleHref else "$BASE_URL$titleHref"

            val detailReq = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val detailResp = client.newCall(detailReq).execute()
            if (!detailResp.isSuccessful) return@withContext emptyList()

            val detailHtml = detailResp.body?.string() ?: return@withContext emptyList()
            val detailDoc = Jsoup.parse(detailHtml, BASE_URL)

            val rows = detailDoc.select("table tbody tr")
            val results = mutableListOf<SubtitleItem>()

            for (row in rows) {
                val linkEl = row.select("td.a1 a").first() ?: continue
                val href = linkEl.attr("href")
                val langSpan = linkEl.select("span").firstOrNull()?.text()?.trim() ?: "English"
                val nameSpan = linkEl.select("span").getOrNull(1)?.text()?.trim() ?: linkEl.text().trim()
                val isHi = row.select("td.a40").isNotEmpty() || row.select(".hi").isNotEmpty()

                val langCode = mapLanguageToCode(langSpan)

                val fullDownloadPage = if (href.startsWith("http")) href else "$BASE_URL$href"
                val subId = href.substringAfterLast("/").ifBlank { nameSpan.hashCode().toString() }

                results.add(
                    SubtitleItem(
                        id = "subscene_$subId",
                        providerId = id,
                        providerName = name,
                        title = nameSpan,
                        languageCode = langCode,
                        languageName = langSpan,
                        format = SubtitleFormat.SRT,
                        downloadUrl = fullDownloadPage,
                        isHearingImpaired = isHi,
                        matchScore = 85,
                        sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                        releaseInfo = nameSpan
                    )
                )

                if (results.size >= 25) break
            }
            results
        } catch (e: Exception) {
            Log.w(TAG, "Subscene search failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            // First visit page to get direct download button
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val html = resp.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html, BASE_URL)
            val downloadBtn = doc.select("a#downloadButton, .download a").first() ?: return@withContext null
            val directHref = downloadBtn.attr("href")
            val fileUrl = if (directHref.startsWith("http")) directHref else "$BASE_URL$directHref"

            val fileReq = Request.Builder()
                .url(fileUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", item.downloadUrl)
                .build()

            val fileResp = client.newCall(fileReq).execute()
            if (!fileResp.isSuccessful) return@withContext null

            val bodyBytes = fileResp.body?.bytes() ?: return@withContext null

            // Check if zip archive
            if (bodyBytes.size > 4 && bodyBytes[0] == 0x50.toByte() && bodyBytes[1] == 0x4B.toByte()) {
                ZipInputStream(bodyBytes.inputStream()).use { zis ->
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
                return@withContext String(bodyBytes, Charsets.UTF_8)
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Subscene download error: ${e.message}")
            null
        }
    }

    private fun mapLanguageToCode(name: String): String {
        return when (name.lowercase()) {
            "english" -> "en"
            "spanish" -> "es"
            "french" -> "fr"
            "german" -> "de"
            "italian" -> "it"
            "japanese" -> "ja"
            "korean" -> "ko"
            "chinese", "chinese bg code", "mandarin" -> "zh-CN"
            "portuguese", "brazilian portuguese" -> "pt"
            "russian" -> "ru"
            "arabic" -> "ar"
            "indonesian" -> "id"
            "vietnamese" -> "vi"
            else -> "en"
        }
    }
}
