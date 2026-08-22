package com.example.smartskip

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class BilibiliSponsorBlockProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : SkipSegmentProvider {

    override val source: SkipSource = SkipSource.BILIBILI
    override val providerId: String = "bilibili_sponsorblock"
    override val displayName: String = "Bilibili SponsorBlock"

    private val categoriesParam = "[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"preview\",\"music_offtopic\",\"filler\",\"poi_highlight\",\"hook\"]"

    override suspend fun fetchSegments(
        context: Context,
        videoId: String,
        durationMs: Long,
        title: String?,
        channelName: String?,
        providerIdParam: String?,
        extraMeta: Map<String, String>
    ): List<SkipSegment> = withContext(Dispatchers.IO) {
        val isBilibili = providerIdParam?.equals("bilibili", ignoreCase = true) == true ||
                videoId.startsWith("BV", ignoreCase = true) ||
                videoId.startsWith("av", ignoreCase = true) ||
                extraMeta["source"]?.contains("bilibili", ignoreCase = true) == true

        if (!isBilibili) return@withContext emptyList()

        // Extract bvid or cid
        val bvid = when {
            videoId.startsWith("BV", ignoreCase = true) -> videoId
            videoId.contains("BV") -> "BV" + videoId.substringAfter("BV").substringBefore("?").substringBefore("/")
            else -> videoId
        }

        val endpoints = listOf(
            "https://bsb.sola.love/api/skipSegments?videoID=$bvid&categories=$categoriesParam",
            "https://bilibili.sponsor.ajay.app/api/skipSegments?videoID=$bvid&categories=$categoriesParam"
        )

        for (url in endpoints) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Butterfly-Android/1.0 (SmartSkip)")
                    .header("Referer", "https://www.bilibili.com/")
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.code == 404) return@use
                    if (!resp.isSuccessful) return@use

                    val body = resp.body?.string() ?: return@use
                    val arr = JSONArray(body)
                    val result = parseSegmentsArray(arr)
                    if (result.isNotEmpty()) {
                        return@withContext result
                    }
                }
            } catch (e: Exception) {
                Log.d("BilibiliSponsorBlock", "Failed endpoint $url: ${e.message}")
            }
        }

        emptyList()
    }

    private fun parseSegmentsArray(jsonArray: JSONArray): List<SkipSegment> {
        val result = mutableListOf<SkipSegment>()
        for (i in 0 until jsonArray.length()) {
            val segObj = jsonArray.optJSONObject(i) ?: continue
            val categoryStr = segObj.optString("category", "sponsor")
            val segmentArr = segObj.optJSONArray("segment") ?: continue
            if (segmentArr.length() < 2) continue

            val startSec = segmentArr.optDouble(0, -1.0)
            val endSec = segmentArr.optDouble(1, -1.0)
            if (startSec < 0 || endSec <= startSec) continue

            val startMs = (startSec * 1000).toLong()
            val endMs = (endSec * 1000).toLong()
            val uuid = segObj.optString("UUID", "")
            val category = SkipCategory.fromId(categoryStr)

            result.add(
                SkipSegment(
                    category = category,
                    startMs = startMs,
                    endMs = endMs,
                    label = category.displayName,
                    providerSource = displayName,
                    uuid = uuid
                )
            )
        }
        return result.sortedBy { it.startMs }
    }
}
