package com.example.smartskip

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TheIntroDBProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : SkipSegmentProvider {

    override val source: SkipSource = SkipSource.MOVIES_TV
    override val providerId: String = "theintrodb"
    override val displayName: String = "TheIntroDB"

    override suspend fun fetchSegments(
        context: Context,
        videoId: String,
        durationMs: Long,
        title: String?,
        channelName: String?,
        providerIdParam: String?,
        extraMeta: Map<String, String>
    ): List<SkipSegment> = withContext(Dispatchers.IO) {
        val imdbId = extraMeta["imdbId"] ?: extraMeta["imdb"]
        val tmdbId = extraMeta["tmdbId"] ?: extraMeta["tmdb"]
        val season = extraMeta["season"] ?: extraMeta["seasonNumber"] ?: extractSeason(title ?: "")
        val episode = extraMeta["episode"] ?: extraMeta["episodeNumber"] ?: extractEpisode(title ?: "")

        val queryId = imdbId ?: tmdbId
        if (queryId.isNullOrBlank()) {
            return@withContext emptyList()
        }

        try {
            val url = if (!season.isNullOrBlank() && !episode.isNullOrBlank()) {
                "https://api.theintrodb.org/v1/intro?id=$queryId&season=$season&episode=$episode"
            } else {
                "https://api.theintrodb.org/v1/movies/$queryId"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Butterfly-Android/1.0 (TheIntroDB)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404 || !response.isSuccessful) {
                    return@use emptyList()
                }

                val bodyStr = response.body?.string() ?: return@use emptyList()
                val json = JSONObject(bodyStr)
                val segments = mutableListOf<SkipSegment>()

                // 1. Check intro object
                if (json.has("intro")) {
                    val introObj = json.optJSONObject("intro")
                    if (introObj != null) {
                        val startSec = introObj.optDouble("start", -1.0)
                        val endSec = introObj.optDouble("end", -1.0)
                        if (startSec >= 0 && endSec > startSec) {
                            segments.add(
                                SkipSegment(
                                    category = SkipCategory.INTRO,
                                    startMs = (startSec * 1000).toLong(),
                                    endMs = (endSec * 1000).toLong(),
                                    label = "Show Intro",
                                    providerSource = displayName
                                )
                            )
                        }
                    }
                }

                // 2. Check recap object
                if (json.has("recap")) {
                    val recapObj = json.optJSONObject("recap")
                    if (recapObj != null) {
                        val startSec = recapObj.optDouble("start", -1.0)
                        val endSec = recapObj.optDouble("end", -1.0)
                        if (startSec >= 0 && endSec > startSec) {
                            segments.add(
                                SkipSegment(
                                    category = SkipCategory.PREVIEW,
                                    startMs = (startSec * 1000).toLong(),
                                    endMs = (endSec * 1000).toLong(),
                                    label = "Previously On / Recap",
                                    providerSource = displayName
                                )
                            )
                        }
                    }
                }

                // 3. Check credits / outro object
                if (json.has("credits") || json.has("outro")) {
                    val creditsObj = json.optJSONObject("credits") ?: json.optJSONObject("outro")
                    if (creditsObj != null) {
                        val startSec = creditsObj.optDouble("start", -1.0)
                        val endSec = creditsObj.optDouble("end", (durationMs / 1000.0))
                        if (startSec >= 0 && endSec > startSec) {
                            segments.add(
                                SkipSegment(
                                    category = SkipCategory.OUTRO,
                                    startMs = (startSec * 1000).toLong(),
                                    endMs = (endSec * 1000).toLong(),
                                    label = "End Credits",
                                    providerSource = displayName
                                )
                            )
                        }
                    }
                }

                // 4. Check array format
                val array = json.optJSONArray("segments") ?: json.optJSONArray("intervals")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val type = item.optString("type", "intro")
                        val startSec = item.optDouble("start", -1.0)
                        val endSec = item.optDouble("end", -1.0)
                        if (startSec < 0 || endSec <= startSec) continue

                        val category = when (type.lowercase()) {
                            "intro", "opening" -> SkipCategory.INTRO
                            "outro", "credits", "ending" -> SkipCategory.OUTRO
                            "recap", "preview" -> SkipCategory.PREVIEW
                            else -> SkipCategory.INTRO
                        }

                        segments.add(
                            SkipSegment(
                                category = category,
                                startMs = (startSec * 1000).toLong(),
                                endMs = (endSec * 1000).toLong(),
                                label = category.displayName,
                                providerSource = displayName
                            )
                        )
                    }
                }

                segments.sortedBy { it.startMs }
            }
        } catch (e: Exception) {
            Log.d("TheIntroDB", "TheIntroDB request error: ${e.message}")
            emptyList()
        }
    }

    private fun extractSeason(title: String): String? {
        val regex = Regex("""(?:s|season)\s*(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(title)?.groupValues?.getOrNull(1)
    }

    private fun extractEpisode(title: String): String? {
        val regex = Regex("""(?:e|ep|episode)\s*(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(title)?.groupValues?.getOrNull(1)
    }
}
