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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * SubDL subtitle provider adapter.
 * Uses the official SubDL v1 API contract with support for TMDB, IMDb, and title queries.
 */
class SubDlProvider(
    private val apiKey: String = "subdl_Mp42hcrZJOddEWEGyUjzp1q2A1NsdWxkAd2pDD8PCwg",
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SubtitleProvider {

    override val id: String = "subdl"
    override val name: String = "SubDL"

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleItem>()
        try {
            val urlBuilder = StringBuilder("https://api.subdl.com/api/v1/subtitles?api_key=").append(apiKey)
            var hasParam = false

            if (!query.imdbId.isNullOrBlank()) {
                urlBuilder.append("&imdb_id=").append(query.imdbId.trim())
                hasParam = true
            } else if (!query.tmdbId.isNullOrBlank()) {
                urlBuilder.append("&tmdb_id=").append(query.tmdbId.trim())
                hasParam = true
            } else if (query.title.isNotBlank()) {
                urlBuilder.append("&film_name=").append(URLEncoder.encode(query.title.trim(), "UTF-8"))
                hasParam = true
            }

            if (!hasParam) return@withContext emptyList()

            if (query.season != null && query.season > 0) {
                urlBuilder.append("&season_number=").append(query.season)
            }
            if (query.episode != null && query.episode > 0) {
                urlBuilder.append("&episode_number=").append(query.episode)
            }
            if (query.year != null && query.year > 1900) {
                urlBuilder.append("&year=").append(query.year)
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Butterfly/1.0")
                .header("Accept", "application/json")
                .build()

            val respString = okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!respString.isNullOrBlank()) {
                val json = JSONObject(respString)
                val subtitlesArr = json.optJSONArray("subtitles") ?: JSONArray()
                for (i in 0 until subtitlesArr.length()) {
                    val sub = subtitlesArr.optJSONObject(i) ?: continue
                    val subUrl = sub.optString("url", "")
                    val lang = sub.optString("lang", "en")
                    val release = sub.optString("release_name", sub.optString("name", "Subtitle $i"))
                    val hi = sub.optBoolean("hi", false)

                    val fullDownloadUrl = when {
                        subUrl.startsWith("http://") || subUrl.startsWith("https://") -> subUrl
                        subUrl.startsWith("/") -> "https://dl.subdl.com$subUrl"
                        else -> "https://dl.subdl.com/$subUrl"
                    }

                    results.add(
                        SubtitleItem(
                            id = "subdl_${sub.optString("id", i.toString())}",
                            providerId = id,
                            providerName = name,
                            title = release,
                            languageCode = normalizeLangCode(lang),
                            languageName = lang.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                            format = if (release.endsWith(".vtt", ignoreCase = true)) SubtitleFormat.VTT else SubtitleFormat.SRT,
                            downloadUrl = fullDownloadUrl,
                            isHearingImpaired = hi,
                            matchScore = 90,
                            sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                            releaseInfo = release
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("SubDlProvider", "SubDL search error: ${e.message}")
        }
        return@withContext results
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val responseBytes = okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            } ?: return@withContext null

            // Check if response is a ZIP archive (starts with PK\x03\x04)
            if (responseBytes.size >= 4 && responseBytes[0] == 0x50.toByte() && responseBytes[1] == 0x4B.toByte()) {
                val zis = ZipInputStream(ByteArrayInputStream(responseBytes))
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".ass")) {
                        val text = zis.bufferedReader(StandardCharsets.UTF_8).readText()
                        zis.closeEntry()
                        zis.close()
                        return@withContext text
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zis.close()
            }

            // Direct subtitle text
            return@withContext String(responseBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w("SubDlProvider", "Failed to fetch SubDL content: ${e.message}")
            null
        }
    }

    private fun normalizeLangCode(lang: String): String {
        return when (lang.lowercase()) {
            "english", "en", "eng" -> "en"
            "hindi", "hi", "hin" -> "hi"
            "japanese", "ja", "jpn" -> "ja"
            "chinese", "zh", "zho" -> "zh"
            "spanish", "es", "spa" -> "es"
            "french", "fr", "fra" -> "fr"
            "german", "de", "deu" -> "de"
            "russian", "ru", "rus" -> "ru"
            "korean", "ko", "kor" -> "ko"
            "arabic", "ar", "ara" -> "ar"
            "portuguese", "pt", "por" -> "pt"
            "italian", "it", "ita" -> "it"
            else -> lang.take(2).lowercase()
        }
    }
}
