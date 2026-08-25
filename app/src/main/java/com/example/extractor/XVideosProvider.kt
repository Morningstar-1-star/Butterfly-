package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object XVideosProvider {
    private const val TAG = "XVideosProvider"
    const val PROVIDER_ID = "xvideos"

    private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getHome(limit: Int = 20, page: Int = 1): List<VideoItem> {
        val target = if (page <= 1) "https://www.xvideos.com/new/1" else "https://www.xvideos.com/new/$page"
        return parseXVideosHtml(target, limit)
    }

    fun search(query: String, limit: Int = 20, page: Int = 1): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val target = if (page <= 1) "https://www.xvideos.com/?k=$encoded" else "https://www.xvideos.com/?k=$encoded&p=$page"
        return parseXVideosHtml(target, limit)
    }

    private fun parseXVideosHtml(targetUrl: String, limit: Int): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1")
                .header("Referer", "https://www.xvideos.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return list

            val pattern = Pattern.compile("""<a\s+href="(/video(?:\.?\d+|[^"'\s]+)/[^"]*)"[^>]*title="([^"]+)"""", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            val seenIds = mutableSetOf<String>()

            val thumbPattern = Pattern.compile("""data-src="(https://[^"]+?\.jpg[^"]*)"""", Pattern.CASE_INSENSITIVE)
            val thumbMatcher = thumbPattern.matcher(html)
            val thumbs = mutableListOf<String>()
            while (thumbMatcher.find()) {
                thumbs.add(thumbMatcher.group(1) ?: "")
            }

            var thumbIdx = 0
            while (matcher.find() && list.size < limit) {
                val path = matcher.group(1) ?: continue
                val title = matcher.group(2) ?: "XVideos"

                if (seenIds.contains(path)) continue
                seenIds.add(path)

                val thumb = if (thumbIdx < thumbs.size) thumbs[thumbIdx++] else ""
                val previewList = if (thumb.isNotBlank()) {
                    com.example.util.PreviewFrameResolver.resolvePreviewFrames(
                        VideoItem(id = "https://www.xvideos.com$path", title = title, uploaderName = "XVideos", thumbnailUrl = thumb, providerId = PROVIDER_ID)
                    )
                } else emptyList()

                list.add(
                    VideoItem(
                        id = "https://www.xvideos.com$path",
                        title = title,
                        uploaderName = "XVideos",
                        thumbnailUrl = thumb,
                        providerId = PROVIDER_ID,
                        previewThumbnails = previewList
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "XVideos parse error: ${e.message}")
        }
        return list
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val cleanInput = urlOrId.trim()
        val targetUrl = when {
            cleanInput.startsWith("http") -> cleanInput
            cleanInput.startsWith("/") -> "https://www.xvideos.com$cleanInput"
            else -> "https://www.xvideos.com/video$cleanInput/"
        }

        val xvHeaders = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Cookie" to "age_verified=1",
            "Referer" to "https://www.xvideos.com/"
        )

        try {
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Cookie", "age_verified=1")
                .header("Referer", "https://www.xvideos.com/")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            if (!html.isNullOrBlank()) {
                val titlePattern = Pattern.compile("""<meta\s+property="og:title"\s+content="([^"]+)"""", Pattern.CASE_INSENSITIVE)
                val titleMatcher = titlePattern.matcher(html)
                val title = if (titleMatcher.find()) titleMatcher.group(1) ?: "XVideos" else "XVideos"

                val thumbPattern = Pattern.compile("""<meta\s+property="og:image"\s+content="([^"]+)"""", Pattern.CASE_INSENSITIVE)
                val thumbMatcher = thumbPattern.matcher(html)
                val thumb = if (thumbMatcher.find()) thumbMatcher.group(1) ?: "" else ""

                val options = mutableListOf<PlayableStreamOption>()

                // HLS
                val hlsPattern = Pattern.compile("""html5player\.setVideoHLS\s*\(\s*['"]([^'"]+)['"]\s*\)""", Pattern.CASE_INSENSITIVE)
                val hlsMatcher = hlsPattern.matcher(html)
                if (hlsMatcher.find()) {
                    val hlsUrl = hlsMatcher.group(1) ?: ""
                    if (hlsUrl.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "Adaptive HLS (m3u8)",
                                format = "m3u8",
                                isMuxed = true,
                                videoUrl = hlsUrl,
                                providerType = ProviderType.DIRECT,
                                headers = xvHeaders
                            )
                        )
                    }
                }

                // High MP4
                val highPattern = Pattern.compile("""html5player\.setVideoUrlHigh\s*\(\s*['"]([^'"]+)['"]\s*\)""", Pattern.CASE_INSENSITIVE)
                val highMatcher = highPattern.matcher(html)
                if (highMatcher.find()) {
                    val highUrl = highMatcher.group(1) ?: ""
                    if (highUrl.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "High Quality MP4",
                                format = "mp4",
                                isMuxed = true,
                                videoUrl = highUrl,
                                providerType = ProviderType.DIRECT,
                                headers = xvHeaders
                            )
                        )
                    }
                }

                // Low MP4
                val lowPattern = Pattern.compile("""html5player\.setVideoUrlLow\s*\(\s*['"]([^'"]+)['"]\s*\)""", Pattern.CASE_INSENSITIVE)
                val lowMatcher = lowPattern.matcher(html)
                if (lowMatcher.find()) {
                    val lowUrl = lowMatcher.group(1) ?: ""
                    if (lowUrl.isNotBlank()) {
                        options.add(
                            PlayableStreamOption(
                                qualityLabel = "Low Quality MP4",
                                format = "mp4",
                                isMuxed = true,
                                videoUrl = lowUrl,
                                providerType = ProviderType.DIRECT,
                                headers = xvHeaders
                            )
                        )
                    }
                }

                if (options.isNotEmpty()) {
                    val bestOption = options.first()
                    return@withContext StreamData(
                        videoId = targetUrl,
                        videoUrl = bestOption.videoUrl ?: "",
                        title = title,
                        channelName = "XVideos",
                        description = title,
                        thumbnailUrl = thumb,
                        availableStreamOptions = options,
                        selectedStreamOption = bestOption,
                        providerId = PROVIDER_ID,
                        providerType = ProviderType.DIRECT,
                        headers = xvHeaders
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "XVideos direct HTML extraction failed: ${e.message}")
        }

        // Fallback to YtDlpResolver
        if (context != null) {
            try {
                Log.i(TAG, "Falling back to YtDlpResolver for XVideos URL: $targetUrl")
                val ytDlpRes = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (ytDlpRes is YouTubeExtractorHelper.ExtractionResult.Success) {
                    return@withContext ytDlpRes.streamData.copy(
                        providerId = PROVIDER_ID,
                        headers = xvHeaders
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "YtDlpResolver XVideos fallback failed: ${e.message}")
            }
        }

        null
    }
}
