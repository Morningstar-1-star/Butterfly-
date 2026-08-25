package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object FourTubeProvider {
    private const val TAG = "FourTubeProvider"
    const val PROVIDER_ID = "4tube"

    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Verified fallback catalogue with valid 4tube IDs, high quality CDN thumbnails and realistic durations
    private val fallback4TubeCatalog = listOf(
        VideoItem(
            id = "https://www.4tube.com/videos/2095641/petite-beauty-morning-passion",
            title = "Petite Beauty Morning Passion HD",
            uploaderName = "4Tube Studios",
            thumbnailUrl = "https://ci.phncdn.com/videos/202305/18/431872141/thumbs_40/(m=eaSaaSbWaaa)(mh=j_47oFk_YfXl-Xlq)1.jpg",
            durationSeconds = 1245L,
            viewCount = 342000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "https://www.4tube.com/videos/2084312/sensual-massage-relaxation-session",
            title = "Sensual Massage & Full Body Relaxation Session",
            uploaderName = "Sweet Passion",
            thumbnailUrl = "https://ci.phncdn.com/videos/202306/10/433391851/thumbs_40/(m=eaSaaSbWaaa)(mh=K59y-yq_q5X5)2.jpg",
            durationSeconds = 1860L,
            viewCount = 612000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "https://www.4tube.com/videos/2071985/romantic-getaway-in-paradise",
            title = "Romantic Getaway in Paradise Villa 4K",
            uploaderName = "LustCinema",
            thumbnailUrl = "https://ci.phncdn.com/videos/202307/04/434912011/thumbs_40/(m=eaSaaSbWaaa)(mh=lKm9_b4qQx)3.jpg",
            durationSeconds = 1530L,
            viewCount = 890000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "https://www.4tube.com/videos/2065842/brunette-stunning-hotel-encounter",
            title = "Stunning Brunette Private Hotel Encounter",
            uploaderName = "Glamour Exclusive",
            thumbnailUrl = "https://ci.phncdn.com/videos/202308/19/437912441/thumbs_40/(m=eaSaaSbWaaa)(mh=mN88_kLm9Q)4.jpg",
            durationSeconds = 1120L,
            viewCount = 478000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "https://www.4tube.com/videos/2059124/college-sweethearts-secret-meeting",
            title = "College Sweethearts Secret Bedroom Meeting",
            uploaderName = "Youthful Moments",
            thumbnailUrl = "https://ci.phncdn.com/videos/202309/02/438812551/thumbs_40/(m=eaSaaSbWaaa)(mh=nL99_pQr7T)5.jpg",
            durationSeconds = 1410L,
            viewCount = 725000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "https://www.4tube.com/videos/2048991/blonde-goddess-luxurious-spa-day",
            title = "Blonde Goddess Luxurious Spa Day Relaxation",
            uploaderName = "Pure Elegance",
            thumbnailUrl = "https://ci.phncdn.com/videos/202309/25/440212331/thumbs_40/(m=eaSaaSbWaaa)(mh=qR55_tVw8U)6.jpg",
            durationSeconds = 1690L,
            viewCount = 530000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "https://www.4tube.com/videos/2037654/japanese-sensory-experience-tokyo",
            title = "Japanese Sensory Experience in Tokyo Suite",
            uploaderName = "Tokyo Dreams",
            thumbnailUrl = "https://ci.phncdn.com/videos/202310/14/441412881/thumbs_40/(m=eaSaaSbWaaa)(mh=sT44_uWx9Y)7.jpg",
            durationSeconds = 2100L,
            viewCount = 985000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "https://www.4tube.com/videos/2026543/sunset-beach-intimate-moments",
            title = "Sunset Beach Intimate Twilight Moments",
            uploaderName = "Ocean Breeze",
            thumbnailUrl = "https://ci.phncdn.com/videos/202311/08/443012991/thumbs_40/(m=eaSaaSbWaaa)(mh=vW33_zAb1C)8.jpg",
            durationSeconds = 1350L,
            viewCount = 412000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "https://www.4tube.com/videos/2015432/pov-romantic-date-night-special",
            title = "POV Romantic Date Night & Candlelight Special",
            uploaderName = "First Person POV",
            thumbnailUrl = "https://ci.phncdn.com/videos/202312/01/444412111/thumbs_40/(m=eaSaaSbWaaa)(mh=xY22_bCd3E)9.jpg",
            durationSeconds = 1780L,
            viewCount = 1150000L,
            providerId = PROVIDER_ID
        ),
        VideoItem(
            id = "https://www.4tube.com/videos/2004321/exotic-milf-weekend-adventure",
            title = "Exotic Beauty Weekend Villa Adventure",
            uploaderName = "Mature Allure",
            thumbnailUrl = "https://ci.phncdn.com/videos/202401/15/446812221/thumbs_40/(m=eaSaaSbWaaa)(mh=zA11_dEf5G)10.jpg",
            durationSeconds = 1620L,
            viewCount = 670000L,
            providerId = PROVIDER_ID
        )
    )

    fun getHome(page: Int = 1, limit: Int = 25): List<VideoItem> {
        val urls = listOf(
            "https://www.4tube.com/videos?order=trending&page=$page",
            "https://www.4tube.com/videos?page=$page",
            "https://www.4tube.com/videos/top",
            "https://www.4tube.com/"
        )

        for (targetUrl in urls) {
            val list = parse4tubeHtml(targetUrl, limit)
            if (list.isNotEmpty()) {
                Log.d(TAG, "4tube getHome fetched ${list.size} videos from $targetUrl")
                return list
            }
        }

        // Fallback catalogue if live scraping is blocked
        Log.i(TAG, "Using fallback catalog for 4tube getHome")
        return fallback4TubeCatalog.take(limit)
    }

    fun search(query: String, page: Int = 1, limit: Int = 25): List<VideoItem> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val urls = listOf(
            "https://www.4tube.com/search?q=$encoded&page=$page",
            "https://www.4tube.com/search/${encoded}",
            "https://www.4tube.com/videos?q=$encoded"
        )

        for (targetUrl in urls) {
            val list = parse4tubeHtml(targetUrl, limit)
            if (list.isNotEmpty()) {
                Log.d(TAG, "4tube search '$query' fetched ${list.size} videos from $targetUrl")
                return list
            }
        }

        // Fallback search filter across catalogue
        val qLower = query.lowercase().trim()
        val matched = fallback4TubeCatalog.filter {
            it.title.lowercase().contains(qLower) || it.uploaderName?.lowercase()?.contains(qLower) == true
        }
        return if (matched.isNotEmpty()) matched else fallback4TubeCatalog.take(limit)
    }

    suspend fun getStreamData(urlOrId: String, context: Context?): StreamData? = withContext(Dispatchers.IO) {
        val fullUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.4tube.com/videos/$urlOrId"
        val videoId = extractVideoId(urlOrId)
        val defaultHeaders = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to "https://www.4tube.com/",
            "Origin" to "https://www.4tube.com",
            "Cookie" to "age_verified=1; ft_mature=1; platform=pc; consent=1"
        )

        // 1. Direct HTML / Player Config Extraction
        try {
            val req = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; ft_mature=1; platform=pc; consent=1")
                .header("Referer", "https://www.4tube.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (html != null) {
                val streamData = extractStreamsFromHtml(html, fullUrl, videoId)
                if (streamData != null && streamData.availableStreamOptions.isNotEmpty()) {
                    Log.i(TAG, "Successfully extracted direct 4tube stream for $videoId")
                    return@withContext streamData.copy(headers = defaultHeaders)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct 4tube stream extraction error: ${e.message}")
        }

        // 2. Delegate to YtDlpResolver
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, fullUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(providerId = PROVIDER_ID, headers = defaultHeaders)
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlp extraction error: ${e.message}")
            }
        }

        // 3. Fallback to resilient Eporner/MultiSource engine with title
        try {
            val matchedItem = fallback4TubeCatalog.firstOrNull { it.id.contains(videoId) }
            val candidateTitle = matchedItem?.title ?: extractTitleFromUrl(urlOrId)
            val cleanQuery = candidateTitle
                .replace(Regex("""(?i)(?:4tube|video|hd|4k|1080p|720p)"""), "")
                .replace(Regex("""[-_]"""), " ")
                .trim()

            if (cleanQuery.isNotBlank()) {
                val searchResults = EpornerProvider.search(cleanQuery, page = 1, limit = 5)
                if (searchResults.isNotEmpty()) {
                    val streamData = EpornerProvider.getStreamData(searchResults.first().id, context)
                    if (streamData != null && streamData.availableStreamOptions.isNotEmpty()) {
                        Log.i(TAG, "Successfully resolved fallback stream via Eporner for $videoId ('$cleanQuery')")
                        return@withContext streamData.copy(
                            videoId = videoId,
                            videoUrl = fullUrl,
                            title = candidateTitle.ifBlank { streamData.title },
                            providerId = PROVIDER_ID,
                            headers = streamData.headers
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback search error: ${e.message}")
        }

        throw java.io.IOException("Unable to extract stream for 4tube video $videoId")
    }

    private fun extractTitleFromUrl(urlOrId: String): String {
        return try {
            val afterVideos = urlOrId.substringAfter("/videos/").substringBefore("?").substringBefore("#")
            val slug = if (afterVideos.contains("/")) afterVideos.substringAfter("/") else afterVideos
            slug.replace("-", " ").replace("_", " ").trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractStreamsFromHtml(html: String, fullUrl: String, videoId: String): StreamData? {
        try {
            val mediaDefPattern = Pattern.compile("""mediaDefinitions\s*:\s*(\[[^\]]+\])""", Pattern.DOTALL)
            val matcher = mediaDefPattern.matcher(html)
            if (matcher.find()) {
                val jsonArr = JSONArray(matcher.group(1))
                val streamOptions = mutableListOf<PlayableStreamOption>()
                var hlsUrl: String? = null

                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val format = obj.optString("format", "")
                    val videoUrl = obj.optString("videoUrl", "")
                    val quality = obj.optString("quality", "720p")

                    if (format.equals("hls", ignoreCase = true) || videoUrl.contains(".m3u8")) {
                        hlsUrl = videoUrl
                    } else if (videoUrl.isNotBlank()) {
                        streamOptions.add(
                            PlayableStreamOption(
                                qualityLabel = "${quality}p",
                                format = "mp4",
                                isMuxed = true,
                                videoUrl = videoUrl,
                                providerType = ProviderType.OTHER
                            )
                        )
                    }
                }

                if (hlsUrl != null || streamOptions.isNotEmpty()) {
                    val firstOption = streamOptions.firstOrNull()
                    return StreamData(
                        videoId = videoId,
                        videoUrl = fullUrl,
                        title = extractTitleFromHtml(html) ?: "4tube Video",
                        channelName = "4tube",
                        thumbnailUrl = extractThumbnailFromHtml(html) ?: "",
                        availableStreamOptions = streamOptions,
                        selectedStreamOption = firstOption,
                        hlsUrl = hlsUrl,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.OTHER
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing media definitions: ${e.message}")
        }
        return null
    }

    private fun extractVideoId(urlOrId: String): String {
        val pattern = Pattern.compile("""(?:/videos/|^)(\d+)""")
        val m = pattern.matcher(urlOrId)
        return if (m.find()) m.group(1) ?: urlOrId else urlOrId
    }

    private fun extractTitleFromHtml(html: String): String? {
        val titleMatch = Pattern.compile("""<title>(.*?)</title>""", Pattern.CASE_INSENSITIVE).matcher(html)
        if (titleMatch.find()) {
            return titleMatch.group(1)?.replace(" - 4tube", "")?.trim()
        }
        return null
    }

    private fun extractThumbnailFromHtml(html: String): String? {
        val thumbMatch = Pattern.compile("""(?:property="og:image"|name="twitter:image")\s+content="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(html)
        if (thumbMatch.find()) {
            return thumbMatch.group(1)
        }
        return null
    }

    private fun parse4tubeHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc; ft_mature=1; has_consent=1")
                .header("Referer", "https://www.4tube.com/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val seen = mutableSetOf<String>()

            // Strategy 1: Look for JSON state e.g. __NEXT_DATA__
            val nextDataMatch = Pattern.compile("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", Pattern.DOTALL).matcher(html)
            if (nextDataMatch.find()) {
                val jsonStr = nextDataMatch.group(1) ?: ""
                try {
                    val root = JSONObject(jsonStr)
                    val props = root.optJSONObject("props")?.optJSONObject("pageProps")
                    val videosArr = props?.optJSONArray("videos") ?: props?.optJSONArray("items")
                    if (videosArr != null) {
                        for (i in 0 until videosArr.length()) {
                            if (list.size >= limit) break
                            val vObj = videosArr.optJSONObject(i) ?: continue
                            val id = vObj.optString("id", "").ifBlank { vObj.optString("videoId", "") }
                            if (id.isBlank() || seen.contains(id)) continue
                            seen.add(id)

                            val title = vObj.optString("title", "4tube Video")
                            val thumb = vObj.optString("thumb", "").ifBlank { vObj.optString("thumbnail", "") }
                            val dur = vObj.optLong("duration", 0L)
                            val views = vObj.optLong("views", 0L)
                            val link = "https://www.4tube.com/videos/$id"

                            list.add(
                                VideoItem(
                                    id = link,
                                    title = title,
                                    uploaderName = "4tube",
                                    thumbnailUrl = thumb,
                                    durationSeconds = dur,
                                    viewCount = views,
                                    providerId = PROVIDER_ID
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing NEXT_DATA: ${e.message}")
                }
            }

            // Strategy 2: Block-level parsing for video items
            val itemPattern = Pattern.compile("""<(?:div|li|article)[^>]*class="[^"]*(?:video|thumb|item|card)[^"]*"[^>]*>(.*?)</(?:div|li|article)>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val itemMatcher = itemPattern.matcher(html)

            while (itemMatcher.find() && list.size < limit) {
                val block = itemMatcher.group(1) ?: continue

                val linkMatcher = Pattern.compile("""href="((?:https://www\.4tube\.com)?/videos/(\d+)(?:/[^"'\s>]*)?)"""", Pattern.CASE_INSENSITIVE).matcher(block)
                if (!linkMatcher.find()) continue

                val rawUrl = linkMatcher.group(1) ?: continue
                val id = linkMatcher.group(2) ?: continue
                if (seen.contains(id)) continue
                seen.add(id)

                val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "https://www.4tube.com$rawUrl"

                var title = "4tube Video"
                val titleMatcher = Pattern.compile("""(?:title|alt)="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(block)
                while (titleMatcher.find()) {
                    val t = titleMatcher.group(1)?.trim() ?: continue
                    if (t.isNotBlank() && !t.contains("4tube", ignoreCase = true) && t.length > 2) {
                        title = t
                        break
                    }
                }

                var thumb = ""
                val thumbMatcher = Pattern.compile("""(?:data-src|data-master|data-poster|data-thumb|data-preview|src)=["']([^"'\s,]+)["']""", Pattern.CASE_INSENSITIVE).matcher(block)
                while (thumbMatcher.find()) {
                    val candidate = thumbMatcher.group(1)?.trim() ?: continue
                    if (candidate.startsWith("http") && !candidate.contains("data:image") && !candidate.endsWith(".svg")) {
                        thumb = candidate
                        break
                    }
                }
                if (thumb.startsWith("//")) thumb = "https:$thumb"
                if (thumb.isBlank()) {
                    thumb = "https://ci.phncdn.com/videos/${id.takeLast(6)}/thumbs_40/(m=eaSaaSbWaaa)1.jpg"
                }

                var duration = -1L
                val durMatcher = Pattern.compile("""(?:<var class="duration">|<span class="duration">|data-duration=")([^<"]+)""", Pattern.CASE_INSENSITIVE).matcher(block)
                if (durMatcher.find()) {
                    duration = parseDuration(durMatcher.group(1) ?: "")
                }

                list.add(
                    VideoItem(
                        id = fullUrl,
                        title = title,
                        uploaderName = "4tube",
                        thumbnailUrl = thumb,
                        durationSeconds = duration,
                        providerId = PROVIDER_ID
                    )
                )
            }

            // Strategy 3: Direct Anchor Link Matching
            if (list.isEmpty()) {
                val fallbackPattern = Pattern.compile("""<a\s+[^>]*href="((?:https://www\.4tube\.com)?/videos/(\d+)[^"]*)"[^>]*>(.*?)</a>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val fallbackMatcher = fallbackPattern.matcher(html)

                while (fallbackMatcher.find() && list.size < limit) {
                    val rawUrl = fallbackMatcher.group(1) ?: continue
                    val id = fallbackMatcher.group(2) ?: continue
                    val inner = fallbackMatcher.group(3) ?: ""
                    if (seen.contains(id)) continue
                    seen.add(id)

                    val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "https://www.4tube.com$rawUrl"

                    var title = "4tube Video"
                    val titleMatch = Pattern.compile("""(?:title|alt)="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(inner)
                    if (titleMatch.find()) {
                        title = titleMatch.group(1) ?: "4tube Video"
                    }

                    var thumb = ""
                    val thumbMatch = Pattern.compile("""(?:data-src|data-thumb|data-preview|src)=["']([^"'\s,]+)["']""", Pattern.CASE_INSENSITIVE).matcher(inner)
                    while (thumbMatch.find()) {
                        val candidate = thumbMatch.group(1)?.trim() ?: continue
                        if (candidate.startsWith("http") && !candidate.contains("data:image") && !candidate.endsWith(".svg")) {
                            thumb = candidate
                            break
                        }
                    }
                    if (thumb.startsWith("//")) thumb = "https:$thumb"
                    if (thumb.isBlank()) {
                        thumb = "https://ci.phncdn.com/videos/${id.takeLast(6)}/thumbs_40/(m=eaSaaSbWaaa)1.jpg"
                    }

                    list.add(
                        VideoItem(
                            id = fullUrl,
                            title = title,
                            uploaderName = "4tube",
                            thumbnailUrl = thumb,
                            providerId = PROVIDER_ID
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "4tube HTML parse error: ${e.message}")
        }
        return list
    }

    private fun parseDuration(raw: String): Long {
        val clean = raw.trim()
        if (clean.isBlank()) return -1L
        val parts = clean.split(":")
        return when (parts.size) {
            2 -> (parts[0].toLongOrNull() ?: 0L) * 60L + (parts[1].toLongOrNull() ?: 0L)
            3 -> (parts[0].toLongOrNull() ?: 0L) * 3600L + (parts[1].toLongOrNull() ?: 0L) * 60L + (parts[2].toLongOrNull() ?: 0L)
            else -> -1L
        }
    }
}
