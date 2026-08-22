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
 * Jimaku Japanese anime & drama subtitle provider adapter.
 * Accesses Jimaku / Kitsunekko public anime subtitle feeds.
 */
class JimakuProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SubtitleProvider {

    override val id: String = "jimaku"
    override val name: String = "Jimaku Japanese"

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleItem>()
        try {
            val titleClean = cleanAnimeTitle(query.title)
            if (titleClean.isBlank()) return@withContext emptyList()

            val encoded = URLEncoder.encode(titleClean, "UTF-8")
            val url = "https://jimaku.cc/api/entries/search?query=$encoded"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly Native Subtitles/1.0")
                .header("Accept", "application/json")
                .build()

            val respBody = okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!respBody.isNullOrBlank() && respBody.startsWith("[")) {
                val array = JSONArray(respBody)
                for (i in 0 until array.length().coerceAtMost(10)) {
                    val entry = array.optJSONObject(i) ?: continue
                    val entryId = entry.optString("id")
                    val japaneseTitle = entry.optString("japanese_name", entry.optString("name", titleClean))
                    val files = entry.optJSONArray("files") ?: JSONArray()

                    for (j in 0 until files.length().coerceAtMost(5)) {
                        val file = files.optJSONObject(j) ?: continue
                        val fileUrl = file.optString("url")
                        val fileName = file.optString("name", "$japaneseTitle.ass")
                        val format = if (fileName.endsWith(".ass", ignoreCase = true)) SubtitleFormat.ASS else SubtitleFormat.SRT

                        if (fileUrl.isNotBlank()) {
                            results.add(
                                SubtitleItem(
                                    id = "jimaku_${entryId}_$j",
                                    providerId = id,
                                    providerName = name,
                                    title = fileName,
                                    languageCode = "ja",
                                    languageName = "Japanese (日本語)",
                                    format = format,
                                    downloadUrl = if (fileUrl.startsWith("http")) fileUrl else "https://jimaku.cc$fileUrl",
                                    isHearingImpaired = false,
                                    matchScore = 90,
                                    sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                                    releaseInfo = japaneseTitle
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("JimakuProvider", "Jimaku search error: ${e.message}")
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
            Log.w("JimakuProvider", "Failed to fetch Jimaku content: ${e.message}")
            null
        }
    }

    private fun cleanAnimeTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\[.*?\\]"), "")
            .replace(Regex("(?i)\\(.*?\\)"), "")
            .replace(Regex("(?i)Episode\\s*\\d+"), "")
            .replace(Regex("(?i)Season\\s*\\d+"), "")
            .trim()
    }
}
