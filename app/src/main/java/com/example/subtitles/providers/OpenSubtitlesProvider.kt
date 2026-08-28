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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * OpenSubtitles provider adapter (REST API v1 / REST API).
 * High-quality movie and TV subtitles with multi-language coverage.
 */
class OpenSubtitlesProvider(
    private val customApiKey: String? = null,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SubtitleProvider {

    override val id: String = "opensubtitles"
    override val name: String = "OpenSubtitles"

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleItem>()
        try {
            // Build query URL with IMDB ID or Title query
            val urlBuilder = StringBuilder("https://api.opensubtitles.com/api/v1/subtitles?")
            var hasParam = false

            if (!query.imdbId.isNullOrBlank()) {
                val imdbNum = query.imdbId.replace("tt", "").trim()
                urlBuilder.append("imdb_id=").append(imdbNum)
                hasParam = true
            } else if (!query.tmdbId.isNullOrBlank()) {
                urlBuilder.append("tmdb_id=").append(query.tmdbId)
                hasParam = true
            } else if (query.title.isNotBlank()) {
                urlBuilder.append("query=").append(URLEncoder.encode(query.title, "UTF-8"))
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
            if (!query.languageCode.isNullOrBlank() && query.languageCode != "auto") {
                urlBuilder.append("&languages=").append(query.languageCode)
            }

            val key = customApiKey ?: com.example.util.AppConfig.getOpenSubtitlesApiKey()
            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "Butterfly v1.0")
                .header("Api-Key", key)
                .build()

            val responseBody = okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!responseBody.isNullOrBlank()) {
                val json = JSONObject(responseBody)
                val data = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val attributes = item.optJSONObject("attributes") ?: continue
                    val files = attributes.optJSONArray("files")
                    val firstFile = files?.optJSONObject(0)
                    val fileId = firstFile?.optInt("file_id") ?: 0
                    val fileName = firstFile?.optString("file_name") ?: "subtitle.srt"

                    val lang = attributes.optString("language", "en")
                    val release = attributes.optString("release", "")
                    val downloadCount = attributes.optInt("download_count", 0)
                    val isHearingImpaired = attributes.optBoolean("hearing_impaired", false)

                    val downloadUrl = if (fileId > 0) {
                        "https://api.opensubtitles.com/api/v1/download?file_id=$fileId"
                    } else {
                        attributes.optString("url", "")
                    }

                    if (downloadUrl.isNotBlank()) {
                        results.add(
                            SubtitleItem(
                                id = "os_${item.optString("id", i.toString())}",
                                providerId = id,
                                providerName = name,
                                title = fileName,
                                languageCode = lang,
                                languageName = getLanguageDisplayName(lang),
                                format = SubtitleFormat.SRT,
                                downloadUrl = downloadUrl,
                                isHearingImpaired = isHearingImpaired,
                                matchScore = calculateMatchScore(query, attributes.optString("feature_details")),
                                sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                                releaseInfo = release.ifBlank { null }
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("OpenSubtitlesProvider", "OpenSubtitles search error: ${e.message}")
        }
        return@withContext results
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "Butterfly v1.0")
                .header("Api-Key", "p1Q8N8Z6eB0s6Z6A5t8Y4U1I3O9P2L5K")
                .build()

            val body = okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!body.isNullOrBlank()) {
                // If it returns JSON with a link object e.g. { "link": "https://..." }
                if (body.startsWith("{") && body.contains("\"link\"")) {
                    val link = JSONObject(body).optString("link")
                    if (link.isNotBlank()) {
                        val subReq = Request.Builder().url(link).build()
                        return@withContext okHttpClient.newCall(subReq).execute().use { it.body?.string() }
                    }
                }
                return@withContext body
            }
        } catch (e: Exception) {
            Log.w("OpenSubtitlesProvider", "Failed to fetch subtitle content: ${e.message}")
        }
        return@withContext null
    }

    private fun calculateMatchScore(query: SubtitleSearchQuery, details: String): Int {
        var score = 80
        if (!query.imdbId.isNullOrBlank()) score += 15
        if (query.season != null && query.season > 0) score += 5
        return score.coerceIn(0, 100)
    }

    private fun getLanguageDisplayName(code: String): String {
        return when (code.lowercase()) {
            "en", "eng" -> "English"
            "hi", "hin" -> "Hindi"
            "ja", "jpn" -> "Japanese"
            "zh", "zho", "chi" -> "Chinese"
            "ko", "kor" -> "Korean"
            "es", "spa" -> "Spanish"
            "fr", "fre", "fra" -> "French"
            "de", "ger", "deu" -> "German"
            "ru", "rus" -> "Russian"
            "pt", "por" -> "Portuguese"
            "ar", "ara" -> "Arabic"
            "id", "ind" -> "Indonesian"
            else -> code.uppercase()
        }
    }
}
