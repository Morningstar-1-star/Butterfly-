package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class SponsorSegment(
    val category: String,
    val startMs: Long,
    val endMs: Long
)

object SponsorBlockHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val segmentsCache = ConcurrentHashMap<String, List<SponsorSegment>>()

    suspend fun fetchSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext emptyList()
        segmentsCache[videoId]?.let { return@withContext it }

        try {
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"sponsor\",\"intro\",\"outro\",\"selfpromo\"]"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "ButterflyMedia/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val array = JSONArray(bodyStr)
                val list = mutableListOf<SponsorSegment>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val category = obj.optString("category", "sponsor")
                    val segArray = obj.getJSONArray("segment")
                    val startSec = segArray.getDouble(0)
                    val endSec = segArray.getDouble(1)
                    list.add(
                        SponsorSegment(
                            category = category,
                            startMs = (startSec * 1000).toLong(),
                            endMs = (endSec * 1000).toLong()
                        )
                    )
                }
                segmentsCache[videoId] = list
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSkipTargetMs(videoId: String, currentPositionMs: Long): SponsorSegment? {
        val segments = segmentsCache[videoId] ?: return null
        for (segment in segments) {
            // Check if position is within 500ms threshold of start and before end
            if (currentPositionMs >= (segment.startMs - 300) && currentPositionMs < (segment.endMs - 1000)) {
                return segment
            }
        }
        return null
    }
}
