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
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object FourTubeProvider {
    private const val TAG = "FourTubeProvider"
    const val PROVIDER_ID = "4tube"

    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getHome(page: Int = 1, limit: Int = 30): List<VideoItem> {
        val urls = listOf(
            if (page == 1) "https://www.4tube.com/popular" else "https://www.4tube.com/popular?page=$page",
            if (page == 1) "https://www.4tube.com/new" else "https://www.4tube.com/new?page=$page",
            if (page == 1) "https://www.4tube.com/rating" else "https://www.4tube.com/rating?page=$page",
            if (page == 1) "https://www.4tube.com/" else "https://www.4tube.com/?page=$page"
        )

        for (targetUrl in urls) {
            val list = parse4tubeHtml(targetUrl, limit)
            if (list.isNotEmpty()) {
                Log.d(TAG, "4tube getHome page $page fetched ${list.size} videos from $targetUrl")
                return list
            }
        }

        return emptyList()
    }

    fun search(query: String, page: Int = 1, limit: Int = 30): List<VideoItem> {
        val cleanQuery = query.trim()
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val urls = listOf(
            if (page == 1) "https://www.4tube.com/search?q=$encoded" else "https://www.4tube.com/search?q=$encoded&page=$page",
            "https://www.4tube.com/search/$encoded?page=$page"
        )

        for (targetUrl in urls) {
            val list = parse4tubeHtml(targetUrl, limit)
            if (list.isNotEmpty()) {
                Log.d(TAG, "4tube search '$query' page $page fetched ${list.size} videos from $targetUrl")
                return list
            }
        }

        return emptyList()
    }

    fun getCreatorVideos(slugOrName: String, page: Int = 1, limit: Int = 30): List<VideoItem> {
        val clean = slugOrName.trim().lowercase().replace(" ", "-")
        val urls = listOf(
            "https://www.4tube.com/source/$clean?page=$page",
            "https://www.4tube.com/pornstar/$clean?page=$page"
        )

        for (u in urls) {
            val list = parse4tubeHtml(u, limit)
            if (list.isNotEmpty()) return list
        }
        return search(slugOrName, page, limit)
    }

    suspend fun getStreamData(urlOrId: String, context: Context?): StreamData? = withContext(Dispatchers.IO) {
        val publicId = extractPublicId(urlOrId)
        val fullUrl = if (urlOrId.startsWith("http")) urlOrId else "https://www.4tube.com/item/$publicId"
        val defaultHeaders = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to "https://www.4tube.com/",
            "Origin" to "https://www.4tube.com",
            "Cookie" to "age_verified=1; ft_mature=1; platform=pc; consent=1"
        )

        // 1. Direct Page / Player Extraction
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

            if (!html.isNullOrBlank()) {
                val streamOptions = mutableListOf<PlayableStreamOption>()
                var title = "4tube Video"
                var thumb = "https://c2.ttcache.com/thumbnail/$publicId/288x162/1.jpg"

                val tMatch = Pattern.compile("""<meta\s+property="og:title"\s+content="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (tMatch.find()) title = tMatch.group(1)?.replace(" - 4Tube", "")?.trim() ?: title

                val iMatch = Pattern.compile("""<meta\s+property="og:image"\s+content="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (iMatch.find()) thumb = iMatch.group(1)?.trim() ?: thumb

                // Check for embedded mp4/m3u8 URLs
                val vUrlMatch = Pattern.compile("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""", Pattern.CASE_INSENSITIVE).matcher(html)
                while (vUrlMatch.find()) {
                    val sUrl = vUrlMatch.group(0) ?: continue
                    if (!sUrl.contains("preview") && !sUrl.contains("trailer") && !sUrl.contains("banner")) {
                        val isHls = sUrl.contains(".m3u8")
                        streamOptions.add(
                            PlayableStreamOption(
                                qualityLabel = if (isHls) "Auto HLS" else "1080p",
                                format = if (isHls) "m3u8" else "mp4",
                                isMuxed = true,
                                videoUrl = sUrl,
                                providerType = ProviderType.OTHER,
                                headers = defaultHeaders
                            )
                        )
                    }
                }

                if (streamOptions.isNotEmpty()) {
                    val primaryUrl = streamOptions.first().videoUrl ?: ""
                    return@withContext StreamData(
                        videoId = publicId,
                        videoUrl = primaryUrl,
                        title = title,
                        channelName = "4Tube",
                        thumbnailUrl = thumb,
                        availableStreamOptions = streamOptions,
                        selectedStreamOption = streamOptions.first(),
                        providerId = PROVIDER_ID,
                        headers = defaultHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct 4tube stream extraction error: ${e.message}")
        }

        // 2. YtDlp fallback
        if (context != null) {
            try {
                val ytdlResult = YtDlpResolver.extractStreamInfo(context, fullUrl)
                if (ytdlResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytdlResult.streamData.copy(providerId = PROVIDER_ID, headers = defaultHeaders)
                }
            } catch (e: Exception) {
                Log.w(TAG, "YtDlp error for 4tube: ${e.message}")
            }
        }

        // 3. Resilient Multi-Source Matcher via title
        try {
            val candidateTitle = extractTitleFromUrl(urlOrId)
            val cleanQuery = candidateTitle
                .replace(Regex("""(?i)(?:4tube|video|hd|4k|1080p|720p|\d{6,})"""), "")
                .replace(Regex("""[-_]"""), " ")
                .trim()

            if (cleanQuery.isNotBlank()) {
                // First try RedTube/Eporner for matching stream
                val redtubeResults = RedTubeProvider.search(cleanQuery, page = 1, limit = 3)
                if (redtubeResults.isNotEmpty()) {
                    val streamData = RedTubeProvider.getStreamData(redtubeResults.first().id, context)
                    if (streamData != null && streamData.availableStreamOptions.isNotEmpty()) {
                        Log.i(TAG, "Successfully resolved 4tube stream via RedTube for '$cleanQuery'")
                        return@withContext streamData.copy(
                            videoId = publicId,
                            videoUrl = fullUrl,
                            title = candidateTitle.ifBlank { streamData.title },
                            providerId = PROVIDER_ID,
                            headers = streamData.headers
                        )
                    }
                }

                val epornerResults = EpornerProvider.search(cleanQuery, page = 1, limit = 3)
                if (epornerResults.isNotEmpty()) {
                    val streamData = EpornerProvider.getStreamData(epornerResults.first().id, context)
                    if (streamData != null && streamData.availableStreamOptions.isNotEmpty()) {
                        Log.i(TAG, "Successfully resolved 4tube stream via Eporner for '$cleanQuery'")
                        return@withContext streamData.copy(
                            videoId = publicId,
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

        throw java.io.IOException("Unable to extract stream for 4tube video $publicId")
    }

    private fun extractPublicId(urlOrId: String): String {
        val clean = urlOrId.trim()
        val m = Pattern.compile("""(?:item/|videos/|public_id=)([a-zA-Z0-9_-]+)""").matcher(clean)
        if (m.find()) return m.group(1) ?: clean
        return clean.substringAfterLast("/").substringBefore("?").substringBefore("&")
    }

    private fun extractTitleFromUrl(urlOrId: String): String {
        val clean = urlOrId.substringAfterLast("/").substringBefore("?")
        return clean.replace(Regex("""[-_]"""), " ")
    }

    private fun parse4tubeHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1; platform=pc; ft_mature=1; has_consent=1")
                .header("Referer", "https://www.4tube.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val seen = mutableSetOf<String>()

            // Pattern 1: Modern 4tube card with data-public-id
            val cardPattern = Pattern.compile("""<div[^>]+class="[^"]*card[^"]*"[^>]*data-public-id="([^"]+)"[^>]*>(.*?)</div>\s*</div>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val cardMatcher = cardPattern.matcher(html)

            while (cardMatcher.find() && list.size < limit) {
                val publicId = cardMatcher.group(1) ?: continue
                val body = cardMatcher.group(2) ?: continue
                if (seen.contains(publicId)) continue
                seen.add(publicId)

                // Title
                var title = "4tube Video"
                val titleM = Pattern.compile("""title="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(body)
                if (titleM.find()) {
                    title = titleM.group(1)?.trim() ?: title
                }

                // Thumbnail
                var thumb = ""
                val imgM = Pattern.compile("""<img[^>]+src="([^"]+)"""", Pattern.CASE_INSENSITIVE).matcher(body)
                if (imgM.find()) {
                    thumb = imgM.group(1)?.trim() ?: ""
                }
                if (thumb.isBlank() || thumb.contains("data:image")) {
                    thumb = "https://c2.ttcache.com/thumbnail/$publicId/288x162/1.jpg"
                }

                // Storyboard Scrubbing Frames (1..16 thumbs)
                val previewThumbnails = mutableListOf<String>()
                val ttHostMatch = Pattern.compile("""(https://c\d+\.ttcache\.com/thumbnail/[^/]+/288x162/)""", Pattern.CASE_INSENSITIVE).matcher(thumb)
                if (ttHostMatch.find()) {
                    val prefix = ttHostMatch.group(1)
                    for (i in 1..16) {
                        previewThumbnails.add("${prefix}${i}.jpg")
                    }
                } else {
                    for (i in 1..16) {
                        previewThumbnails.add("https://c2.ttcache.com/thumbnail/$publicId/288x162/${i}.jpg")
                    }
                }

                // Duration
                var duration = -1L
                val durM = Pattern.compile("""(\d+:\d+(?::\d+)?)""").matcher(body)
                if (durM.find()) {
                    duration = parseDuration(durM.group(1) ?: "")
                }

                // Creator / Source
                var creator = "4tube"
                var creatorUrl: String? = null
                val creatorM = Pattern.compile("""<a[^>]+class="[^"]*item-source[^"]*"[^>]*href="([^"]+)"[^>]*>(?:<i[^>]*></i>)?([^<]+)</a>""", Pattern.CASE_INSENSITIVE).matcher(body)
                if (creatorM.find()) {
                    creatorUrl = "https://www.4tube.com" + (creatorM.group(1) ?: "")
                    creator = creatorM.group(2)?.trim() ?: creator
                }

                val fullUrl = "https://www.4tube.com/item/$publicId"

                list.add(
                    VideoItem(
                        id = fullUrl,
                        title = title,
                        uploaderName = creator,
                        uploaderUrl = creatorUrl,
                        thumbnailUrl = thumb,
                        durationSeconds = duration,
                        previewThumbnails = previewThumbnails,
                        providerId = PROVIDER_ID
                    )
                )
            }

            // Pattern 2: Legacy 4tube link fallback
            if (list.isEmpty()) {
                val legacyPattern = Pattern.compile("""href="(/videos/(\d+)[^"]*)".*?(?:title|alt)="([^"]+)".*?src="([^"]+)"""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val legacyMatcher = legacyPattern.matcher(html)
                while (legacyMatcher.find() && list.size < limit) {
                    val path = legacyMatcher.group(1) ?: continue
                    val id = legacyMatcher.group(2) ?: continue
                    val title = legacyMatcher.group(3) ?: "4tube Video"
                    val thumb = legacyMatcher.group(4) ?: ""
                    if (seen.contains(id)) continue
                    seen.add(id)

                    list.add(
                        VideoItem(
                            id = "https://www.4tube.com$path",
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
