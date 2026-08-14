package com.example.util

import android.util.Log
import com.example.plugin.sdk.model.PluginChannel
import com.example.plugin.sdk.model.PluginPlaylist
import com.example.plugin.sdk.model.PluginVideoItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object YouTubeApiHelper {

    private const val TAG = "YouTubeApiHelper"
    private const val BASE_URL = "https://www.googleapis.com/youtube/v3"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var quotaExceededUntil: Long = 0L

    fun getEffectiveApiKey(): String {
        return try {
            val key = com.example.BuildConfig.YOUTUBE_API_KEY
            if (!key.isNullOrBlank() && key != "null" && !key.contains("MY_")) {
                key
            } else {
                ""
            }
        } catch (e: Throwable) {
            ""
        }
    }

    private fun isQuotaExceeded(): Boolean {
        if (quotaExceededUntil > 0 && System.currentTimeMillis() < quotaExceededUntil) {
            return true
        }
        return false
    }

    private fun handleApiError(statusCode: Int, responseBody: String?) {
        if (statusCode == 403 || statusCode == 429) {
            if (responseBody != null && (responseBody.contains("quotaExceeded", ignoreCase = true) || responseBody.contains("keyInvalid", ignoreCase = true))) {
                Log.w(TAG, "YouTube Data API quota exceeded or key issue ($statusCode). Pausing API calls for 30 minutes.")
                quotaExceededUntil = System.currentTimeMillis() + (30 * 60 * 1000L)
            }
        }
    }

    data class SearchResult(
        val videoItems: List<PluginVideoItem>,
        val channels: List<PluginChannel>,
        val playlists: List<PluginPlaylist>
    )

    fun fetchPopularVideos(maxResults: Int = 25, pageToken: String? = null): List<PluginVideoItem>? {
        if (isQuotaExceeded()) return null
        val apiKey = getEffectiveApiKey()
        val pageParam = if (!pageToken.isNullOrBlank()) "&pageToken=$pageToken" else ""
        val url = "$BASE_URL/videos?part=snippet,contentDetails,statistics&chart=mostPopular&regionCode=US&maxResults=$maxResults$pageParam&key=$apiKey"

        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                handleApiError(response.code, responseBody)
                return null
            }

            val json = JSONObject(responseBody)
            val itemsArr = json.optJSONArray("items") ?: return emptyList()

            val channelIds = mutableSetOf<String>()
            val rawVideos = mutableListOf<RawVideoData>()

            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.getJSONObject(i)
                val id = item.optString("id")
                val snippet = item.optJSONObject("snippet")
                val contentDetails = item.optJSONObject("contentDetails")
                val statistics = item.optJSONObject("statistics")

                if (id.isNotEmpty() && snippet != null) {
                    val title = snippet.optString("title")
                    val channelTitle = snippet.optString("channelTitle")
                    val channelId = snippet.optString("channelId")
                    if (channelId.isNotEmpty()) channelIds.add(channelId)

                    val thumbnails = snippet.optJSONObject("thumbnails")
                    val thumbUrl = extractBestThumbnail(thumbnails) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                    val publishedAt = snippet.optString("publishedAt")
                    val durationIso = contentDetails?.optString("duration")
                    val durationSec = parseIsoDurationToSeconds(durationIso)
                    val viewCount = statistics?.optLong("viewCount", -1L) ?: -1L

                    rawVideos.add(
                        RawVideoData(
                            id = id,
                            title = title,
                            channelTitle = channelTitle,
                            channelId = channelId,
                            thumbnailUrl = thumbUrl,
                            publishedAt = publishedAt,
                            durationSeconds = durationSec,
                            viewCount = viewCount
                        )
                    )
                }
            }

            val avatarMap = fetchChannelAvatars(channelIds, apiKey)

            rawVideos.map { raw ->
                PluginVideoItem(
                    id = raw.id,
                    title = unescapeHtml(raw.title),
                    uploaderName = unescapeHtml(raw.channelTitle),
                    uploaderUrl = if (raw.channelId.isNotEmpty()) "https://www.youtube.com/channel/${raw.channelId}" else null,
                    uploaderAvatarUrl = avatarMap[raw.channelId],
                    viewCount = raw.viewCount,
                    durationSeconds = raw.durationSeconds,
                    uploadDate = raw.publishedAt,
                    thumbnailUrl = raw.thumbnailUrl,
                    providerId = "youtube"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching popular videos from YouTube Data API v3", e)
            null
        }
    }

    fun search(query: String, maxResults: Int = 25, pageToken: String? = null): SearchResult? {
        if (isQuotaExceeded()) return null
        val apiKey = getEffectiveApiKey()

        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val pageParam = if (!pageToken.isNullOrBlank()) "&pageToken=$pageToken" else ""
            val url = "$BASE_URL/search?part=snippet&q=$encodedQuery&type=video,channel,playlist&maxResults=$maxResults$pageParam&key=$apiKey"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                handleApiError(response.code, responseBody)
                return null
            }

            val json = JSONObject(responseBody)
            val itemsArr = json.optJSONArray("items") ?: return SearchResult(emptyList(), emptyList(), emptyList())

            val videoIds = mutableListOf<String>()
            val channelIds = mutableSetOf<String>()
            val rawVideoList = mutableListOf<RawVideoData>()
            val channelList = mutableListOf<PluginChannel>()
            val playlistList = mutableListOf<PluginPlaylist>()

            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.getJSONObject(i)
                val idObj = item.optJSONObject("id") ?: continue
                val kind = idObj.optString("kind")
                val snippet = item.optJSONObject("snippet") ?: continue

                when (kind) {
                    "youtube#video" -> {
                        val videoId = idObj.optString("videoId")
                        if (videoId.isNotEmpty()) {
                            videoIds.add(videoId)
                            val title = snippet.optString("title")
                            val channelTitle = snippet.optString("channelTitle")
                            val channelId = snippet.optString("channelId")
                            if (channelId.isNotEmpty()) channelIds.add(channelId)
                            val thumbUrl = extractBestThumbnail(snippet.optJSONObject("thumbnails")) ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            val publishedAt = snippet.optString("publishedAt")

                            rawVideoList.add(
                                RawVideoData(
                                    id = videoId,
                                    title = title,
                                    channelTitle = channelTitle,
                                    channelId = channelId,
                                    thumbnailUrl = thumbUrl,
                                    publishedAt = publishedAt,
                                    durationSeconds = 0L,
                                    viewCount = -1L
                                )
                            )
                        }
                    }
                    "youtube#channel" -> {
                        val chId = idObj.optString("channelId")
                        val chTitle = snippet.optString("title")
                        val thumbUrl = extractBestThumbnail(snippet.optJSONObject("thumbnails"))
                        if (chId.isNotEmpty()) {
                            channelList.add(
                                PluginChannel(
                                    id = chId,
                                    name = unescapeHtml(chTitle),
                                    avatarUrl = thumbUrl,
                                    description = snippet.optString("description")
                                )
                            )
                        }
                    }
                    "youtube#playlist" -> {
                        val plId = idObj.optString("playlistId")
                        val plTitle = snippet.optString("title")
                        val chTitle = snippet.optString("channelTitle")
                        val thumbUrl = extractBestThumbnail(snippet.optJSONObject("thumbnails"))
                        if (plId.isNotEmpty()) {
                            playlistList.add(
                                PluginPlaylist(
                                    id = plId,
                                    title = unescapeHtml(plTitle),
                                    uploaderName = unescapeHtml(chTitle),
                                    thumbnailUrl = thumbUrl
                                )
                            )
                        }
                    }
                }
            }

            val enrichedDetailsMap = fetchVideoDetailsBatch(videoIds, apiKey)
            val avatarMap = fetchChannelAvatars(channelIds, apiKey)

            val finalVideoItems = rawVideoList.map { raw ->
                val enriched = enrichedDetailsMap[raw.id]
                PluginVideoItem(
                    id = raw.id,
                    title = unescapeHtml(raw.title),
                    uploaderName = unescapeHtml(raw.channelTitle),
                    uploaderUrl = if (raw.channelId.isNotEmpty()) "https://www.youtube.com/channel/${raw.channelId}" else null,
                    uploaderAvatarUrl = avatarMap[raw.channelId],
                    viewCount = enriched?.viewCount ?: raw.viewCount,
                    durationSeconds = enriched?.durationSeconds ?: raw.durationSeconds,
                    uploadDate = raw.publishedAt,
                    thumbnailUrl = raw.thumbnailUrl,
                    providerId = "youtube"
                )
            }

            SearchResult(
                videoItems = finalVideoItems,
                channels = channelList,
                playlists = playlistList
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error performing search with YouTube Data API v3", e)
            null
        }
    }

    fun getChannelDetails(channelId: String): PluginChannel? {
        if (isQuotaExceeded()) return null
        val apiKey = getEffectiveApiKey()

        return try {
            val url = "$BASE_URL/channels?part=snippet,statistics,brandingSettings&id=$channelId&key=$apiKey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                handleApiError(response.code, responseBody)
                return null
            }

            val json = JSONObject(responseBody)
            val itemsArr = json.optJSONArray("items") ?: return null
            if (itemsArr.length() == 0) return null

            val ch = itemsArr.getJSONObject(0)
            val snippet = ch.optJSONObject("snippet")
            val statistics = ch.optJSONObject("statistics")
            val branding = ch.optJSONObject("brandingSettings")

            val title = snippet?.optString("title") ?: "YouTube Channel"
            val description = snippet?.optString("description") ?: ""
            val avatarUrl = extractBestThumbnail(snippet?.optJSONObject("thumbnails"))
            val bannerUrl = branding?.optJSONObject("image")?.optString("bannerExternalUrl")
            val subCount = statistics?.optLong("subscriberCount", -1L) ?: -1L
            val videoCount = statistics?.optLong("videoCount", -1L) ?: -1L

            val subCountText = if (subCount >= 1000000) {
                String.format("%.1fM subscribers", subCount / 1000000.0)
            } else if (subCount >= 1000) {
                String.format("%.1fK subscribers", subCount / 1000.0)
            } else if (subCount >= 0) {
                "$subCount subscribers"
            } else null

            PluginChannel(
                id = channelId,
                name = unescapeHtml(title),
                avatarUrl = avatarUrl,
                bannerUrl = bannerUrl,
                description = description,
                subscriberCountText = subCountText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching channel details from YouTube Data API v3", e)
            null
        }
    }

    private data class RawVideoData(
        val id: String,
        val title: String,
        val channelTitle: String,
        val channelId: String,
        val thumbnailUrl: String,
        val publishedAt: String,
        val durationSeconds: Long,
        val viewCount: Long
    )

    private data class EnrichedDetails(
        val durationSeconds: Long,
        val viewCount: Long
    )

    private fun fetchVideoDetailsBatch(videoIds: List<String>, apiKey: String): Map<String, EnrichedDetails> {
        if (videoIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, EnrichedDetails>()

        videoIds.chunked(50).forEach { chunk ->
            try {
                val idsJoined = chunk.joinToString(",")
                val url = "$BASE_URL/videos?part=contentDetails,statistics&id=$idsJoined&key=$apiKey"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val id = item.optString("id")
                        val contentDetails = item.optJSONObject("contentDetails")
                        val statistics = item.optJSONObject("statistics")

                        val durationIso = contentDetails?.optString("duration")
                        val durationSec = parseIsoDurationToSeconds(durationIso)
                        val viewCount = statistics?.optLong("viewCount", -1L) ?: -1L

                        if (id.isNotEmpty()) {
                            result[id] = EnrichedDetails(durationSec, viewCount)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in batch video details request", e)
            }
        }

        return result
    }

    private fun fetchChannelAvatars(channelIds: Set<String>, apiKey: String): Map<String, String> {
        if (channelIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()

        channelIds.chunked(50).forEach { chunk ->
            try {
                val idsJoined = chunk.joinToString(",")
                val url = "$BASE_URL/channels?part=snippet&id=$idsJoined&key=$apiKey"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val id = item.optString("id")
                        val snippet = item.optJSONObject("snippet")
                        val avatarUrl = extractBestThumbnail(snippet?.optJSONObject("thumbnails"))
                        if (id.isNotEmpty() && !avatarUrl.isNullOrEmpty()) {
                            result[id] = avatarUrl
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in batch channel avatars request", e)
            }
        }

        return result
    }

    private fun extractBestThumbnail(thumbnailsObj: JSONObject?): String? {
        if (thumbnailsObj == null) return null
        return thumbnailsObj.optJSONObject("maxres")?.optString("url")
            ?: thumbnailsObj.optJSONObject("standard")?.optString("url")
            ?: thumbnailsObj.optJSONObject("high")?.optString("url")
            ?: thumbnailsObj.optJSONObject("medium")?.optString("url")
            ?: thumbnailsObj.optJSONObject("default")?.optString("url")
    }

    fun parseIsoDurationToSeconds(isoDuration: String?): Long {
        if (isoDuration.isNullOrEmpty()) return 0L
        return try {
            val durationStr = isoDuration.uppercase()
            if (!durationStr.startsWith("P")) return 0L

            var seconds = 0L
            val timeIndex = durationStr.indexOf("T")
            val datePart = if (timeIndex != -1) durationStr.substring(1, timeIndex) else durationStr.substring(1)
            val timePart = if (timeIndex != -1) durationStr.substring(timeIndex + 1) else ""

            if ("D" in datePart) {
                val days = datePart.substringBefore("D").toLongOrNull() ?: 0L
                seconds += days * 86400
            }

            var t = timePart
            if ("H" in t) {
                val hours = t.substringBefore("H").toLongOrNull() ?: 0L
                seconds += hours * 3600
                t = t.substringAfter("H")
            }
            if ("M" in t) {
                val minutes = t.substringBefore("M").toLongOrNull() ?: 0L
                seconds += minutes * 60
                t = t.substringAfter("M")
            }
            if ("S" in t) {
                val secs = t.substringBefore("S").replace("S", "").toDoubleOrNull()?.toLong() ?: 0L
                seconds += secs
            }
            seconds
        } catch (e: Exception) {
            0L
        }
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
    }
}
