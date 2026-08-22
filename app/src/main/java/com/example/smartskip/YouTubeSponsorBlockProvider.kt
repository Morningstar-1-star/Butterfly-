package com.example.smartskip

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class YouTubeSponsorBlockProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : SkipSegmentProvider {

    override val source: SkipSource = SkipSource.YOUTUBE
    override val providerId: String = "sponsorblock"
    override val displayName: String = "YouTube SponsorBlock"

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
        if (videoId.isBlank()) return@withContext emptyList()

        // Clean video ID (ensure YouTube 11 chars or alphanumeric)
        val cleanVideoId = videoId.trim()

        // 1. Try K-Anonymity Hash Prefix lookup first for privacy
        val segmentsFromHash = fetchViaKAnonymity(cleanVideoId)
        if (segmentsFromHash.isNotEmpty()) {
            return@withContext segmentsFromHash
        }

        // 2. Direct lookup fallback
        val segmentsDirect = fetchDirect(cleanVideoId)
        return@withContext segmentsDirect
    }

    private fun fetchViaKAnonymity(videoId: String): List<SkipSegment> {
        return try {
            val hash = sha256(videoId)
            val hashPrefix = hash.take(4) // 4 characters k-anonymity
            val url = "https://sponsor.ajay.app/api/skipSegments/$hashPrefix?categories=$categoriesParam"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly-Android/1.0 (SmartSkip)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return emptyList()
                }
                if (!response.isSuccessful) {
                    return emptyList()
                }

                val bodyStr = response.body?.string() ?: return emptyList()
                val jsonArray = JSONArray(bodyStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val objVideoId = obj.optString("videoID", "")
                    if (objVideoId == videoId) {
                        val segmentsArray = obj.optJSONArray("segments") ?: continue
                        return parseSegmentsArray(segmentsArray)
                    }
                }
                emptyList()
            }
        } catch (e: Exception) {
            Log.d("SponsorBlock", "K-anonymity lookup exception: ${e.message}")
            emptyList()
        }
    }

    private fun fetchDirect(videoId: String): List<SkipSegment> {
        return try {
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$categoriesParam"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly-Android/1.0 (SmartSkip)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return emptyList()
                }
                if (!response.isSuccessful) {
                    return emptyList()
                }

                val bodyStr = response.body?.string() ?: return emptyList()
                val jsonArray = JSONArray(bodyStr)
                parseSegmentsArray(jsonArray)
            }
        } catch (e: Exception) {
            Log.d("SponsorBlock", "Direct SponsorBlock lookup exception: ${e.message}")
            emptyList()
        }
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

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
