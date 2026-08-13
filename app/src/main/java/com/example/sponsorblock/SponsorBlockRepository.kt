package com.example.sponsorblock

import android.content.Context
import android.util.Log
import com.example.sponsorblock.model.SponsorBlockAction
import com.example.sponsorblock.model.SponsorBlockCategory
import com.example.sponsorblock.model.SponsorSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SponsorBlockRepository {

    private const val TAG = "SponsorBlockRepository"
    private val cache = mutableMapOf<String, List<SponsorSegment>>()

    suspend fun fetchSegments(
        context: Context,
        videoId: String
    ): List<SponsorSegment> = withContext(Dispatchers.IO) {
        val prefs = SponsorBlockPreferences.getInstance(context)
        if (!prefs.isEnabled.value) return@withContext emptyList()

        val cleanVideoId = extractYouTubeVideoId(videoId) ?: return@withContext emptyList()

        cache[cleanVideoId]?.let { return@withContext it }

        val categoriesJson = "[\"sponsor\",\"selfpromo\",\"interaction\",\"poi_highlight\",\"intro\",\"outro\",\"preview\",\"filler\",\"tangent\"]"
        val encodedCat = URLEncoder.encode(categoriesJson, "UTF-8")
        val urlStr = "${prefs.apiUrl.value}/api/skipSegments?videoID=$cleanVideoId&categories=$encodedCat"

        val segments = mutableListOf<SponsorSegment>()
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ButterflyPlayer)")

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonStr)

                val minDuration = prefs.minimumSegmentDurationSeconds.value

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val catKey = obj.optString("category", "sponsor")
                    val segArray = obj.getJSONArray("segment")
                    val start = segArray.getDouble(0)
                    val end = segArray.getDouble(1)
                    val uuid = obj.optString("UUID", "")
                    val locked = obj.optInt("locked", 0)

                    val category = SponsorBlockCategory.fromKey(catKey)
                    val segment = SponsorSegment(category, start, end, uuid, locked)

                    if (segment.duration >= minDuration) {
                        val action = prefs.getCategoryAction(category)
                        if (action != SponsorBlockAction.DISABLE) {
                            segments.add(segment)
                        }
                    }
                }
                segments.sortBy { it.startTime }
                cache[cleanVideoId] = segments
                Log.d(TAG, "Successfully loaded ${segments.size} SponsorBlock segments for $cleanVideoId")
            } else if (conn.responseCode == 404) {
                Log.d(TAG, "No SponsorBlock segments found for $cleanVideoId")
                cache[cleanVideoId] = emptyList()
            } else {
                Log.w(TAG, "SponsorBlock API returned HTTP ${conn.responseCode}")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error fetching SponsorBlock segments: ${e.message}")
        } finally {
            conn?.disconnect()
        }

        return@withContext segments
    }

    suspend fun sendSkipViewedTime(context: Context, uuid: String) = withContext(Dispatchers.IO) {
        val prefs = SponsorBlockPreferences.getInstance(context)
        if (!prefs.enableSkipCountTracking.value || uuid.isBlank()) return@withContext

        var conn: HttpURLConnection? = null
        try {
            val urlStr = "${prefs.apiUrl.value}/api/viewedVideoSponsorTime?UUID=$uuid"
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", "ButterflyPlayer/1.0")
            val code = conn.responseCode
            Log.d(TAG, "ViewedVideoSponsorTime POST response: $code")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to send skip stats: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun voteSegment(context: Context, uuid: String, type: Int): Boolean = withContext(Dispatchers.IO) {
        val prefs = SponsorBlockPreferences.getInstance(context)
        if (uuid.isBlank()) return@withContext false

        var conn: HttpURLConnection? = null
        try {
            val userId = prefs.privateUserId.value
            val urlStr = "${prefs.apiUrl.value}/api/voteOnSponsorTime?UUID=$uuid&userID=$userId&type=$type"
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val code = conn.responseCode
            return@withContext (code == 200)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to vote on segment: ${e.message}")
            return@withContext false
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun fetchAllMediaSegments(
        context: Context,
        videoId: String?,
        streamTitle: String? = null
    ): List<SponsorSegment> = withContext(Dispatchers.IO) {
        val prefs = SponsorBlockPreferences.getInstance(context)
        if (!prefs.isEnabled.value) return@withContext emptyList()

        val allSegments = mutableListOf<SponsorSegment>()

        // 1. YouTube SponsorBlock Check
        if (!videoId.isNullOrBlank()) {
            val ytId = extractYouTubeVideoId(videoId)
            if (ytId != null) {
                val ytSegments = fetchSegments(context, ytId)
                allSegments.addAll(ytSegments)
            }
        }

        // 2. Anime AniSkip Check
        val title = streamTitle ?: videoId ?: ""
        val animeParams = extractAnimeParams(title)
        if (animeParams != null) {
            val (malId, epNum) = animeParams
            val aniSegments = fetchAniSkipSegments(context, malId, epNum)
            allSegments.addAll(aniSegments)
        }

        // 3. TV Show IntroDB Check
        val tvParams = extractTvShowParams(title)
        if (tvParams != null) {
            val (showId, season, episode) = tvParams
            val tvSegments = fetchIntroDbSegments(context, showId, season, episode)
            allSegments.addAll(tvSegments)
        }

        return@withContext allSegments.distinctBy { "${it.category}_${it.startTime}" }.sortedBy { it.startTime }
    }

    suspend fun fetchAniSkipSegments(
        context: Context,
        malId: Int,
        episodeNumber: Int
    ): List<SponsorSegment> = withContext(Dispatchers.IO) {
        val prefs = SponsorBlockPreferences.getInstance(context)
        if (!prefs.isEnabled.value || malId <= 0 || episodeNumber <= 0) return@withContext emptyList()

        val cacheKey = "aniskip_${malId}_$episodeNumber"
        cache[cacheKey]?.let { return@withContext it }

        val urlStr = "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types=op&types=ed&types=recap&types=mixed-op&types=mixed-ed"
        val segments = mutableListOf<SponsorSegment>()
        var conn: HttpURLConnection? = null

        try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "ButterflyPlayer/1.0")

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = org.json.JSONObject(jsonStr)
                if (obj.optBoolean("found", false)) {
                    val results = obj.optJSONArray("results") ?: org.json.JSONArray()
                    for (i in 0 until results.length()) {
                        val res = results.getJSONObject(i)
                        val skipType = res.optString("skipType", "")
                        val interval = res.getJSONObject("interval")
                        val start = interval.getDouble("startTime")
                        val end = interval.getDouble("endTime")
                        val skipId = res.optString("skipId", "aniskip_$i")

                        val category = SponsorBlockCategory.fromKey(skipType)
                        val segment = SponsorSegment(category, start, end, skipId)

                        val action = prefs.getCategoryAction(category)
                        if (action != SponsorBlockAction.DISABLE) {
                            segments.add(segment)
                        }
                    }
                }
                cache[cacheKey] = segments
                Log.d(TAG, "Loaded ${segments.size} AniSkip segments for MAL:$malId Ep:$episodeNumber")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "AniSkip API request failed: ${e.message}")
        } finally {
            conn?.disconnect()
        }

        return@withContext segments
    }

    suspend fun fetchIntroDbSegments(
        context: Context,
        showId: String,
        season: Int,
        episode: Int
    ): List<SponsorSegment> = withContext(Dispatchers.IO) {
        val prefs = SponsorBlockPreferences.getInstance(context)
        if (!prefs.isEnabled.value || showId.isBlank()) return@withContext emptyList()

        val cacheKey = "introdb_${showId}_${season}_$episode"
        cache[cacheKey]?.let { return@withContext it }

        val urlStr = "https://api.theintrodb.app/v1/shows/$showId/episodes/$season/$episode"
        val segments = mutableListOf<SponsorSegment>()
        var conn: HttpURLConnection? = null

        try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "ButterflyPlayer/1.0")

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = org.json.JSONObject(jsonStr)

                if (obj.has("intro")) {
                    val introObj = obj.getJSONObject("intro")
                    val start = introObj.getDouble("start")
                    val end = introObj.getDouble("end")
                    val cat = SponsorBlockCategory.TV_INTRO
                    if (prefs.getCategoryAction(cat) != SponsorBlockAction.DISABLE) {
                        segments.add(SponsorSegment(cat, start, end, "introdb_intro"))
                    }
                }

                if (obj.has("outro")) {
                    val outroObj = obj.getJSONObject("outro")
                    val start = outroObj.getDouble("start")
                    val end = outroObj.getDouble("end")
                    val cat = SponsorBlockCategory.TV_CREDITS
                    if (prefs.getCategoryAction(cat) != SponsorBlockAction.DISABLE) {
                        segments.add(SponsorSegment(cat, start, end, "introdb_outro"))
                    }
                }

                cache[cacheKey] = segments
                Log.d(TAG, "Loaded ${segments.size} TheIntroDB segments for show $showId S${season}E${episode}")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "TheIntroDB API request failed: ${e.message}")
        } finally {
            conn?.disconnect()
        }

        return@withContext segments
    }

    private fun extractAnimeParams(title: String): Pair<Int, Int>? {
        if (title.isBlank()) return null
        val epRegex = Regex("(?:Episode|Ep|EP|ep|#)\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val match = epRegex.find(title) ?: return null
        val epNum = match.groupValues[1].toIntOrNull() ?: return null

        val malId = kotlin.math.abs(title.takeWhile { it != 'E' && it != 'e' && it != '#' }.hashCode() % 10000) + 1
        return Pair(malId, epNum)
    }

    private fun extractTvShowParams(title: String): Triple<String, Int, Int>? {
        if (title.isBlank()) return null
        val tvRegex = Regex("S(\\d{1,2})E(\\d{1,2})", RegexOption.IGNORE_CASE)
        val match = tvRegex.find(title) ?: return null
        val season = match.groupValues[1].toIntOrNull() ?: 1
        val episode = match.groupValues[2].toIntOrNull() ?: 1
        val showName = title.substringBefore(match.value).trim().lowercase().replace(" ", "-")
        val showId = if (showName.isNotBlank()) showName else "show"
        return Triple(showId, season, episode)
    }

    fun extractYouTubeVideoId(input: String): String? {
        if (input.isBlank()) return null

        val clean = input.trim()
        if (clean.length == 11 && !clean.contains("/") && !clean.contains("?") && !clean.contains("&")) {
            return clean
        }

        val patterns = listOf(
            Regex("(?:v=|/v/|embed/|youtu\\.be/|shorts/|/e/)([a-zA-Z0-9_-]{11})"),
            Regex("^[a-zA-Z0-9_-]{11}$")
        )

        for (pattern in patterns) {
            val match = pattern.find(clean)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }

        return if (clean.length == 11) clean else null
    }

    fun clearCache() {
        cache.clear()
    }
}
