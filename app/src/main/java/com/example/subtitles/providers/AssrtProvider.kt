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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Assrt (Shooter.cn API) Subtitle Provider.
 * Adapted from Bazarr Provider Catalog (LavX/bazarr-provider-catalog).
 * Provides high quality Chinese and multilingual subtitles.
 */
class AssrtProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : SubtitleProvider {

    companion object {
        private const val TAG = "AssrtProvider"
        private const val SEARCH_URL = "http://api.assrt.net/v1/sub/search"
        private const val DETAIL_URL = "http://api.assrt.net/v1/sub/detail"
        private const val DEFAULT_TOKEN = "eX8KjK42P3M1qLw9"
    }

    override val id: String = "assrt"
    override val name: String = "Assrt / Shooter"

    override suspend fun search(query: SubtitleSearchQuery): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val term = query.releaseName?.ifBlank { null } ?: query.title
        if (term.isBlank()) return@withContext emptyList()

        try {
            val encTerm = URLEncoder.encode(term, StandardCharsets.UTF_8.name())
            val url = "$SEARCH_URL?token=$DEFAULT_TOKEN&q=$encTerm&cnt=15"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly/1.0 BazarrCatalog")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val body = resp.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(body)
            if (root.optInt("status", -1) != 0) return@withContext emptyList()

            val subObj = root.optJSONObject("data")?.optJSONObject("sub") ?: return@withContext emptyList()
            val subs = subObj.optJSONArray("subs") ?: return@withContext emptyList()

            val list = mutableListOf<SubtitleItem>()
            for (i in 0 until subs.length()) {
                val sub = subs.getJSONObject(i)
                val subId = sub.optInt("id", 0).toString()
                val nativeName = sub.optString("native_name", "")
                val release = sub.optString("release_name", nativeName)
                val voteScore = sub.optInt("vote_score", 0)

                val langList = sub.optJSONObject("lang")
                val langDesc = langList?.optJSONArray("desc")?.optString(0, "Chinese") ?: "Chinese"
                val langCode = when {
                    langDesc.contains("繁", ignoreCase = true) -> "zh-TW"
                    langDesc.contains("英", ignoreCase = true) || langDesc.contains("eng", ignoreCase = true) -> "en"
                    langDesc.contains("日", ignoreCase = true) || langDesc.contains("jap", ignoreCase = true) -> "ja"
                    else -> "zh-CN"
                }

                val downloadUrl = "$DETAIL_URL?token=$DEFAULT_TOKEN&id=$subId"

                list.add(
                    SubtitleItem(
                        id = "assrt_$subId",
                        providerId = id,
                        providerName = name,
                        title = nativeName.ifBlank { release },
                        languageCode = langCode,
                        languageName = langDesc,
                        format = SubtitleFormat.SRT,
                        downloadUrl = downloadUrl,
                        isHearingImpaired = false,
                        matchScore = 80 + (voteScore.coerceIn(0, 20)),
                        sourceType = SubtitleSourceType.EXTERNAL_PROVIDER,
                        releaseInfo = release
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Assrt search failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun fetchContent(item: SubtitleItem): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "Butterfly/1.0 BazarrCatalog")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val body = resp.body?.string() ?: return@withContext null
            val root = JSONObject(body)
            val subDetail = root.optJSONObject("data")?.optJSONObject("sub") ?: return@withContext null
            val fileList = subDetail.optJSONArray("filelist")

            if (fileList != null && fileList.length() > 0) {
                val firstFile = fileList.getJSONObject(0)
                val fileUrl = firstFile.optString("url", "")
                if (fileUrl.isNotBlank()) {
                    val fileReq = Request.Builder().url(fileUrl).build()
                    val fileResp = client.newCall(fileReq).execute()
                    if (fileResp.isSuccessful) {
                        return@withContext fileResp.body?.string()
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Assrt fetchContent error: ${e.message}")
            null
        }
    }
}
