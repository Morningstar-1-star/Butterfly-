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
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Gestdown Subtitle Provider Plugin (Ported from Bazarr Provider Catalog).
 * Official community bridge for Addic7ed TV series subtitles.
 * Provides high-accuracy episode matching, hearing-impaired badges, and corrected translations.
 */
class GestdownPlugin(
    override val info: SubtitlePluginInfo = SubtitlePluginInfo(
        id = "gestdown",
        name = "Gestdown (Bazarr Addic7ed)",
        description = "Addic7ed TV series subtitle engine from Bazarr catalog. Precise episode & season sync.",
        author = "Bazarr / Addic7ed Community",
        version = "1.3.0",
        requiresApiKey = false,
        supportsFps = false,
        supportsHi = true,
        websiteUrl = "https://api.gestdown.info"
    )
) : SubtitlePlugin {

    companion object {
        private const val TAG = "GestdownPlugin"
        private const val BASE_URL = "https://api.gestdown.info"
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleItem>()
        val season = query.season ?: return@withContext emptyList()
        val episode = query.episode ?: return@withContext emptyList()
        val title = query.title.trim()
        if (title.isBlank()) return@withContext emptyList()

        try {
            // Step 1: Search show ID
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val showSearchUrl = "$BASE_URL/shows/search/$encodedTitle"

            val searchReq = Request.Builder()
                .url(showSearchUrl)
                .header("User-Agent", "Butterfly/2.0 Bazarr-Bridge")
                .header("Accept", "application/json")
                .build()

            val searchBody = okHttpClient.newCall(searchReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext emptyList()

            val showJson = JSONObject(searchBody)
            val shows = showJson.optJSONArray("shows") ?: return@withContext emptyList()
            if (shows.length() == 0) return@withContext emptyList()

            // Pick first matching show
            val showObj = shows.optJSONObject(0) ?: return@withContext emptyList()
            val showId = showObj.optString("id")
            val showName = showObj.optString("name", title)

            // Step 2: Query matching subtitles for season & episode
            val targetLang = (query.languageCode ?: "en").lowercase()
            val subUrl = "$BASE_URL/subtitles/get/$showId/$season/$episode/$targetLang"

            val subReq = Request.Builder()
                .url(subUrl)
                .header("User-Agent", "Butterfly/2.0 Bazarr-Bridge")
                .header("Accept", "application/json")
                .build()

            val subBody = okHttpClient.newCall(subReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext emptyList()

            val subJson = JSONObject(subBody)
            val matching = subJson.optJSONArray("matchingSubtitles") ?: return@withContext emptyList()

            for (i in 0 until matching.length()) {
                val sub = matching.optJSONObject(i) ?: continue
                val subId = sub.optString("subtitleId")
                val version = sub.optString("version", "Default")
                val downloadUri = sub.optString("downloadUri")
                val isHi = sub.optBoolean("hearingImpaired", false)
                val isHd = sub.optBoolean("hd", false)
                val langName = sub.optString("language", targetLang)

                val fullDownloadUrl = if (downloadUri.startsWith("http")) downloadUri else "$BASE_URL$downloadUri"

                results.add(
                    SubtitleItem(
                        id = "gestdown_$subId",
                        providerId = id,
                        providerName = name,
                        title = "$showName S%02dE%02d - $version".format(season, episode),
                        languageCode = targetLang,
                        languageName = langName,
                        format = SubtitleFormat.SRT,
                        downloadUrl = fullDownloadUrl,
                        isHearingImpaired = isHi,
                        matchScore = 95,
                        sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                        releaseInfo = version,
                        resolution = if (isHd) "720p" else null,
                        season = season,
                        episode = episode
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gestdown search error: ${e.message}")
        }
        results
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "Butterfly/2.0 Bazarr-Bridge")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gestdown fetch error: ${e.message}")
            null
        }
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url("$BASE_URL/shows/search/Breaking%20Bad")
                .header("User-Agent", "Butterfly/2.0")
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
}
