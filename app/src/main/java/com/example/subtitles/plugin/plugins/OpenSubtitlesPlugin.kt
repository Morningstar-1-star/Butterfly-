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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * OpenSubtitles.com Subtitle Provider Plugin.
 * High-accuracy multi-language subtitle catalog with support for FPS, Hearing Impaired,
 * movie hashes, and episode matching.
 */
class OpenSubtitlesPlugin(
    private val customApiKeyProvider: () -> String? = { null },
    override val info: SubtitlePluginInfo = SubtitlePluginInfo(
        id = "opensubtitles",
        name = "OpenSubtitles.com",
        description = "World's largest multi-language subtitle database. Full support for FPS, HI, and moviehash.",
        author = "OpenSubtitles Org",
        version = "2.1.0",
        requiresApiKey = false,
        apiKeyHelpUrl = "https://www.opensubtitles.com",
        supportsFps = true,
        supportsHi = true,
        websiteUrl = "https://www.opensubtitles.com"
    )
) : SubtitlePlugin {

    companion object {
        private const val TAG = "OpenSubtitlesPlugin"
        private const val BASE_URL = "https://api.opensubtitles.com/api/v1/subtitles"
        private const val DEFAULT_API_KEY = "p1Q8N8Z6eB0s6Z6A5t8Y4U1I3O9P2L5K"
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun resolveApiKey(): String {
        val custom = customApiKeyProvider()
        return if (!custom.isNullOrBlank()) custom.trim() else com.example.util.AppConfig.getOpenSubtitlesApiKey()
    }

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleItem>()
        try {
            val urlBuilder = StringBuilder("$BASE_URL?")
            var hasParam = false

            if (!query.imdbId.isNullOrBlank()) {
                val imdbClean = query.imdbId.replace("tt", "").trim()
                urlBuilder.append("imdb_id=").append(imdbClean)
                hasParam = true
            } else if (!query.tmdbId.isNullOrBlank()) {
                urlBuilder.append("tmdb_id=").append(query.tmdbId.trim())
                hasParam = true
            } else if (!query.movieHash.isNullOrBlank()) {
                urlBuilder.append("moviehash=").append(query.movieHash.trim())
                hasParam = true
            } else if (query.title.isNotBlank()) {
                urlBuilder.append("query=").append(URLEncoder.encode(query.title.trim(), "UTF-8"))
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

            val key = resolveApiKey()
            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "Butterfly/2.0")
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
                    val isHd = attributes.optBoolean("hd", false)
                    val fps = attributes.optDouble("fps", 0.0).toFloat().takeIf { it > 0f }
                    val foreignPartsOnly = attributes.optBoolean("foreign_parts_only", false)

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
                                isForced = foreignPartsOnly,
                                matchScore = 90,
                                sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                                releaseInfo = release.ifBlank { fileName },
                                fps = fps,
                                resolution = if (isHd) "1080p" else null,
                                downloadsCount = downloadCount,
                                season = query.season,
                                episode = query.episode
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenSubtitles search failed: ${e.message}")
        }
        results
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            val key = resolveApiKey()
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "Butterfly/2.0")
                .header("Api-Key", key)
                .build()

            val body = okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!body.isNullOrBlank()) {
                // OpenSubtitles download endpoint might return JSON containing temporary download link
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
            Log.w(TAG, "OpenSubtitles fetch failed: ${e.message}")
        }
        null
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val key = resolveApiKey()
            val req = Request.Builder()
                .url("$BASE_URL?query=Inception")
                .header("User-Agent", "Butterfly/2.0")
                .header("Api-Key", key)
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - start
                if (resp.isSuccessful) {
                    Result.success("Connected (${elapsed}ms)")
                } else {
                    Result.failure(Exception("HTTP ${resp.code}: ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getLanguageDisplayName(code: String): String {
        return when (code.lowercase().trim()) {
            "en", "eng" -> "English"
            "es", "spa" -> "Spanish"
            "fr", "fre", "fra" -> "French"
            "de", "ger", "deu" -> "German"
            "it", "ita" -> "Italian"
            "pt", "por" -> "Portuguese"
            "ru", "rus" -> "Russian"
            "ja", "jpn" -> "Japanese"
            "ko", "kor" -> "Korean"
            "zh", "zho", "chi" -> "Chinese"
            "ar", "ara" -> "Arabic"
            "hi", "hin" -> "Hindi"
            "id", "ind" -> "Indonesian"
            else -> code.uppercase()
        }
    }
}
