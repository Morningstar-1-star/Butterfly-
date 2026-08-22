package com.example.smartskip

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AniSkipProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : SkipSegmentProvider {

    override val source: SkipSource = SkipSource.ANIME
    override val providerId: String = "aniskip"
    override val displayName: String = "AniSkip"

    override suspend fun fetchSegments(
        context: Context,
        videoId: String,
        durationMs: Long,
        title: String?,
        channelName: String?,
        providerIdParam: String?,
        extraMeta: Map<String, String>
    ): List<SkipSegment> = withContext(Dispatchers.IO) {
        val malIdStr = extraMeta["malId"] ?: extraMeta["animeId"]
        val epStr = extraMeta["episodeNumber"] ?: extraMeta["episode"] ?: extractEpisodeNumber(title ?: "")

        val malId = malIdStr?.toLongOrNull() ?: resolveMalId(title, extraMeta)
        val episodeNumber = epStr?.toIntOrNull() ?: 1
        val episodeLengthSec = if (durationMs > 0) (durationMs / 1000).toInt() else 0

        if (malId == null || malId <= 0) {
            return@withContext emptyList()
        }

        try {
            val urlBuilder = StringBuilder("https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber")
            urlBuilder.append("?types[]=op&types[]=ed&types[]=mixed-op&types[]=mixed-ed&types[]=recap")
            if (episodeLengthSec > 0) {
                urlBuilder.append("&episodeLength=$episodeLengthSec")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "Butterfly-Android/1.0 (AniSkip)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404 || !response.isSuccessful) {
                    return@use emptyList()
                }

                val bodyStr = response.body?.string() ?: return@use emptyList()
                val json = JSONObject(bodyStr)
                val found = json.optBoolean("found", false)
                if (!found) return@use emptyList()

                val resultsArr = json.optJSONArray("results") ?: return@use emptyList()
                val segments = mutableListOf<SkipSegment>()

                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.optJSONObject(i) ?: continue
                    val skipType = item.optString("skipType", "").lowercase()
                    val interval = item.optJSONObject("interval") ?: continue
                    val startSec = interval.optDouble("startTime", -1.0)
                    val endSec = interval.optDouble("endTime", -1.0)

                    if (startSec < 0 || endSec <= startSec) continue

                    val startMs = (startSec * 1000).toLong()
                    val endMs = (endSec * 1000).toLong()
                    val skipId = item.optString("skipId", "")

                    val category = when (skipType) {
                        "op", "mixed-op" -> SkipCategory.INTRO
                        "ed", "mixed-ed" -> SkipCategory.OUTRO
                        "recap" -> SkipCategory.PREVIEW
                        else -> SkipCategory.INTRO
                    }

                    val label = when (skipType) {
                        "op" -> "Anime Opening (OP)"
                        "ed" -> "Anime Ending (ED)"
                        "mixed-op" -> "Mixed Opening"
                        "mixed-ed" -> "Mixed Ending"
                        "recap" -> "Episode Recap"
                        else -> category.displayName
                    }

                    segments.add(
                        SkipSegment(
                            category = category,
                            startMs = startMs,
                            endMs = endMs,
                            label = label,
                            providerSource = displayName,
                            uuid = skipId
                        )
                    )
                }

                segments.sortedBy { it.startMs }
            }
        } catch (e: Exception) {
            Log.d("AniSkip", "AniSkip request error: ${e.message}")
            emptyList()
        }
    }

    private fun extractEpisodeNumber(title: String): String? {
        val regex = Regex("""(?:ep|episode|e|#)\s*(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(title)
        return match?.groupValues?.getOrNull(1)
    }

    private fun resolveMalId(title: String?, extraMeta: Map<String, String>): Long? {
        // If anilist or myanimelist id is in extra metadata
        extraMeta["anilistId"]?.toLongOrNull()?.let { return it }
        extraMeta["jikanId"]?.toLongOrNull()?.let { return it }
        return null
    }
}
