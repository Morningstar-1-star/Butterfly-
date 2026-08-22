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
 * SubDL subtitle provider adapter.
 * Accesses SubDL API with TMDB, IMDb, and title-based subtitle discovery.
 */
class SubDlProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SubtitleProvider {

    override val id: String = "subdl"
    override val name: String = "SubDL"

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleItem>()
        try {
            val urlBuilder = StringBuilder("https://api.subdl.com/api/v1/subtitles?")
            var hasParam = false

            if (!query.imdbId.isNullOrBlank()) {
                urlBuilder.append("imdb_id=").append(query.imdbId)
                hasParam = true
            } else if (!query.tmdbId.isNullOrBlank()) {
                urlBuilder.append("tmdb_id=").append(query.tmdbId)
                hasParam = true
            } else if (query.title.isNotBlank()) {
                urlBuilder.append("film_name=").append(URLEncoder.encode(query.title, "UTF-8"))
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
                .header("User-Agent", "Butterfly Subtitle Client/1.0")
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
                    val release = sub.optString("release_name", sub.optString("name", "Subtitle"))
                    val hi = sub.optBoolean("hi", false)

                    val fullDownloadUrl = if (subUrl.startsWith("http")) subUrl else "https://dl.subdl.com$subUrl"

                    results.add(
                        SubtitleItem(
                            id = "subdl_${sub.optString("id", i.toString())}",
                            providerId = id,
                            providerName = name,
                            title = release,
                            languageCode = normalizeLangCode(lang),
                            languageName = lang.replaceFirstChar { it.uppercase() },
                            format = SubtitleFormat.SRT,
                            downloadUrl = fullDownloadUrl,
                            isHearingImpaired = hi,
                            matchScore = 85,
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
                .header("User-Agent", "Mozilla/5.0")
                .build()

            return@withContext okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
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
            else -> lang.take(2).lowercase()
        }
    }
}
