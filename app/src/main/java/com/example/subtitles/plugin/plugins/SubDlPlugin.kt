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
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * SubDL Subtitle Provider Plugin.
 * High-speed subtitle database with full multi-language coverage, ZIP archive streaming,
 * and precise TMDB/IMDb matching.
 */
class SubDlPlugin(
    private val customApiKeyProvider: () -> String? = { null },
    override val info: SubtitlePluginInfo = SubtitlePluginInfo(
        id = "subdl",
        name = "SubDL",
        description = "Official SubDL REST API v1. Rapid subtitle indexer with automated ZIP unpacking.",
        author = "SubDL Team",
        version = "1.4.5",
        requiresApiKey = false,
        apiKeyHelpUrl = "https://subdl.com",
        supportsFps = true,
        supportsHi = true,
        websiteUrl = "https://subdl.com"
    )
) : SubtitlePlugin {

    companion object {
        private const val TAG = "SubDlPlugin"
        private const val BASE_URL = "https://api.subdl.com/api/v1/subtitles"
    }

    // Lazy initialization of HTTP client to preserve startup time and memory
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun resolveApiKey(): String {
        val custom = customApiKeyProvider()
        return if (!custom.isNullOrBlank()) custom.trim() else com.example.util.AppConfig.getSubdlApiKey()
    }

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleItem>()
        try {
            val key = resolveApiKey()
            val urlBuilder = StringBuilder("$BASE_URL?api_key=").append(key)
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
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Butterfly/2.0")
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
                    val season = sub.optInt("season", query.season ?: 0)
                    val episode = sub.optInt("episode", query.episode ?: 0)

                    val fullDownloadUrl = when {
                        subUrl.startsWith("http://") || subUrl.startsWith("https://") -> subUrl
                        subUrl.startsWith("/") -> "https://dl.subdl.com$subUrl"
                        else -> "https://dl.subdl.com/$subUrl"
                    }

                    // Detect resolution and fps from release string if present
                    val resolution = extractResolution(release)
                    val fps = extractFps(release)

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
                            matchScore = 95,
                            sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                            releaseInfo = release,
                            resolution = resolution,
                            fps = fps,
                            season = if (season > 0) season else null,
                            episode = if (episode > 0) episode else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SubDL search error: ${e.message}")
        }
        results
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Butterfly/2.0")
                .build()

            val responseBytes = okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            } ?: return@withContext null

            // Inspect if payload is ZIP archive (PK\x03\x04 header)
            if (responseBytes.size >= 4 && responseBytes[0] == 0x50.toByte() && responseBytes[1] == 0x4B.toByte()) {
                ZipInputStream(ByteArrayInputStream(responseBytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        if (name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".ass")) {
                            val text = zis.bufferedReader(StandardCharsets.UTF_8).readText()
                            zis.closeEntry()
                            return@withContext text
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // Direct plain text subtitle
            return@withContext String(responseBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "SubDL fetchContent failed: ${e.message}")
            null
        }
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val key = resolveApiKey()
            val req = Request.Builder()
                .url("$BASE_URL?api_key=$key&film_name=Inception")
                .header("User-Agent", "Mozilla/5.0 Butterfly/2.0")
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

    private fun normalizeLangCode(lang: String): String {
        return when (lang.lowercase().trim()) {
            "english", "en", "eng" -> "en"
            "spanish", "es", "spa" -> "es"
            "french", "fr", "fra", "fre" -> "fr"
            "german", "de", "deu", "ger" -> "de"
            "italian", "it", "ita" -> "it"
            "portuguese", "pt", "por", "brazilian" -> "pt"
            "russian", "ru", "rus" -> "ru"
            "japanese", "ja", "jpn" -> "ja"
            "korean", "ko", "kor" -> "ko"
            "chinese", "zh", "zho", "chi" -> "zh"
            "arabic", "ar", "ara" -> "ar"
            "hindi", "hi", "hin" -> "hi"
            "indonesian", "id", "ind" -> "id"
            "turkish", "tr", "tur" -> "tr"
            "vietnamese", "vi", "vie" -> "vi"
            else -> lang.take(2).lowercase()
        }
    }

    private fun extractResolution(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("2160p") || lower.contains("4k") || lower.contains("uhd") -> "2160p"
            lower.contains("1080p") || lower.contains("fhd") -> "1080p"
            lower.contains("720p") || lower.contains("hd") -> "720p"
            lower.contains("480p") || lower.contains("sd") -> "480p"
            else -> null
        }
    }

    private fun extractFps(text: String): Float? {
        val match = Regex("""\b(23\.976|24\.000|24|25\.000|25|29\.970|29\.97|30\.000|30|60\.000|60)\s*(?:fps)?""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }
}
