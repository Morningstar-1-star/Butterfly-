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
 * SubSource & SubtitleCat provider adapter for comprehensive movie, anime, and TV subtitles.
 */
class SubSourceProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SubtitleProvider {

    override val id: String = "subsource"
    override val name: String = "SubSource"

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleItem>()
        try {
            val titleClean = query.title.trim()
            if (titleClean.isBlank()) return@withContext emptyList()

            val encoded = URLEncoder.encode(titleClean, "UTF-8")
            val url = "https://api.subsource.net/api/v1/subtitles/search?query=$encoded"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly Subtitle Client/1.0")
                .build()

            val respBody = okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!respBody.isNullOrBlank() && respBody.startsWith("{")) {
                val json = JSONObject(respBody)
                val subs = json.optJSONArray("subtitles") ?: JSONArray()
                for (i in 0 until subs.length()) {
                    val sub = subs.optJSONObject(i) ?: continue
                    val subId = sub.optString("id", i.toString())
                    val lang = sub.optString("lang", "en")
                    val release = sub.optString("release_name", titleClean)
                    val downloadPath = sub.optString("download_path")

                    if (downloadPath.isNotBlank()) {
                        results.add(
                            SubtitleItem(
                                id = "subsource_$subId",
                                providerId = id,
                                providerName = name,
                                title = release,
                                languageCode = lang,
                                languageName = lang.replaceFirstChar { it.uppercase() },
                                format = SubtitleFormat.SRT,
                                downloadUrl = if (downloadPath.startsWith("http")) downloadPath else "https://api.subsource.net$downloadPath",
                                isHearingImpaired = sub.optBoolean("hi", false),
                                matchScore = 80,
                                sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                                releaseInfo = release
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SubSourceProvider", "SubSource search error: ${e.message}")
        }
        return@withContext results
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(item.downloadUrl).build()
            return@withContext okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w("SubSourceProvider", "Failed to fetch SubSource content: ${e.message}")
            null
        }
    }
}
