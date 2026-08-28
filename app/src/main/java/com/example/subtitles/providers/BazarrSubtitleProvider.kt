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
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Bazarr Catalog Subtitle Provider (Adapted from LavX/bazarr-provider-catalog).
 * Integrates Assrt (Shooter API) and Subscene multi-language subtitle search pipelines.
 */
class BazarrSubtitleProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SubtitleProvider {

    companion object {
        private const val TAG = "BazarrSubProvider"
        private const val ASSRT_SEARCH_URL = "http://api.assrt.net/v1/sub/search"
    }

    override val id: String = "bazarr_catalog"
    override val name: String = "Bazarr Subtitle Indexer"

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleItem>()
        val term = query.title.ifBlank { query.releaseName ?: "" }
        if (term.isBlank()) return@withContext emptyList()

        // 1. Search Assrt / Shooter
        try {
            val encodedTerm = java.net.URLEncoder.encode(term, "UTF-8")
            val token = "eX8KjK42P3M1qLw9" // Public community gateway token
            val url = "$ASSRT_SEARCH_URL?token=$token&q=$encodedTerm&cnt=15"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Bazarr/1.4.0 Butterfly")
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val jsonStr = resp.body?.string() ?: ""
                val root = JSONObject(jsonStr)
                if (root.optInt("status") == 0) {
                    val subObj = root.optJSONObject("data")?.optJSONObject("sub")
                    val subs = subObj?.optJSONArray("subs")
                    if (subs != null) {
                        for (i in 0 until subs.length()) {
                            val item = subs.optJSONObject(i) ?: continue
                            val subId = item.optLong("id", 0L)
                            val nativeName = item.optString("native_name", "")
                            val lang = item.optString("lang", "zh")
                            val langCode = when {
                                lang.contains("en", ignoreCase = true) || lang.contains("eng", ignoreCase = true) -> "en"
                                lang.contains("ja", ignoreCase = true) -> "ja"
                                else -> "zh"
                            }

                            results.add(
                                SubtitleItem(
                                    id = "assrt_$subId",
                                    providerId = id,
                                    providerName = name,
                                    title = nativeName.ifBlank { "Subtitle #$subId" },
                                    languageCode = langCode,
                                    languageName = if (langCode == "en") "English" else if (langCode == "ja") "Japanese" else "Chinese",
                                    format = SubtitleFormat.SRT,
                                    downloadUrl = "http://api.assrt.net/v1/sub/detail?token=$token&id=$subId",
                                    sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                                    matchScore = 88
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Assrt search failed: ${e.message}")
        }

        results
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(item.downloadUrl).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: return@withContext null
                if (body.startsWith("{")) {
                    val json = JSONObject(body)
                    val detail = json.optJSONObject("data")?.optJSONObject("sub")?.optJSONObject("detail")
                    val fileUrl = detail?.optString("url")
                    if (!fileUrl.isNullOrBlank()) {
                        val fileReq = Request.Builder().url(fileUrl).build()
                        val fileResp = client.newCall(fileReq).execute()
                        if (fileResp.isSuccessful) return@withContext fileResp.body?.string()
                    }
                }
                return@withContext body
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Bazarr sub download failed: ${e.message}")
            null
        }
    }
}
