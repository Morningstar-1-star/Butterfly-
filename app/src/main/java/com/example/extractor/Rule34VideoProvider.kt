package com.example.extractor

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object Rule34VideoProvider {
    private const val TAG = "Rule34VideoProvider"
    const val PROVIDER_ID = "rule34video"

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .dns(com.example.util.SecureDnsManager.appDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            var req = chain.request()
            val builder = req.newBuilder()
            if (req.header("User-Agent") == null) {
                builder.header("User-Agent", DEFAULT_USER_AGENT)
            }
            if (req.header("Referer") == null) {
                builder.header("Referer", "https://rule34video.com/")
            }
            chain.proceed(builder.build())
        }
        .build()

    fun extractVideoId(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        val videoPattern = Pattern.compile("/video/(\\d+)", Pattern.CASE_INSENSITIVE)
        val matcher = videoPattern.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1) ?: ""
        }
        val digitsPattern = Pattern.compile("^(\\d{4,10})$")
        val dMatch = digitsPattern.matcher(trimmed)
        if (dMatch.find()) {
            return dMatch.group(1) ?: ""
        }
        return trimmed.substringAfterLast("/")
    }

    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        val id = extractVideoId(urlOrId)
        val targetUrl = if (urlOrId.startsWith("http")) urlOrId else "https://rule34video.com/video/$id/"

        val defaultHeaders = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to "https://rule34video.com/"
        )

        // 1. First attempt: yt-dlp resolver
        if (context != null) {
            try {
                Log.i(TAG, "[Rule34Video] Step 1: Attempting YtDlpResolver for $targetUrl")
                val res = YtDlpResolver.extractStreamInfo(context, targetUrl)
                if (res is YouTubeExtractorHelper.ExtractionResult.Success) {
                    Log.i(TAG, "[Rule34Video] YtDlpResolver extraction successful for $targetUrl")
                    return@withContext res.streamData.copy(providerId = PROVIDER_ID)
                }
            } catch (e: Exception) {
                Log.w(TAG, "[Rule34Video] YtDlpResolver failed: ${e.message}")
            }
        }

        // 2. Direct HTML / KVS Player Fallback if yt-dlp fails
        try {
            Log.i(TAG, "[Rule34Video] Step 2: Attempting direct HTML extraction for $targetUrl")
            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Referer", "https://rule34video.com/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext null

            // Extract title
            var title = "Rule34Video #$id"
            val titleMatch = Pattern.compile("<h1[^>]*>([^<]+)</h1>", Pattern.CASE_INSENSITIVE).matcher(html)
            if (titleMatch.find()) {
                title = titleMatch.group(1)?.trim() ?: title
            }

            // Extract direct video source URLs (MP4 / HLS)
            val mp4Urls = mutableListOf<String>()
            
            // Pattern A: <source src="https://...mp4" ...>
            val sourceMatcher = Pattern.compile("<source[^>]+src=\"([^\"]+\\.mp4[^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(html)
            while (sourceMatcher.find()) {
                val u = sourceMatcher.group(1) ?: continue
                if (!mp4Urls.contains(u)) mp4Urls.add(u)
            }

            // Pattern B: video_url: 'https://...' or video_url='...'
            val jsMatcher = Pattern.compile("video_url\\s*:\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE).matcher(html)
            while (jsMatcher.find()) {
                val u = jsMatcher.group(1) ?: continue
                if (!mp4Urls.contains(u)) mp4Urls.add(u)
            }

            // Pattern C: get_file / download links
            val downloadMatcher = Pattern.compile("href=\"([^\"]*get_file[^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(html)
            while (downloadMatcher.find()) {
                val u = downloadMatcher.group(1) ?: continue
                val fullUrl = if (u.startsWith("/")) "https://rule34video.com$u" else u
                if (!mp4Urls.contains(fullUrl)) mp4Urls.add(fullUrl)
            }

            if (mp4Urls.isEmpty()) {
                Log.w(TAG, "[Rule34Video] No video streams discovered in HTML for $targetUrl")
                return@withContext null
            }

            val options = mp4Urls.mapIndexed { index, streamUrl ->
                val label = if (streamUrl.contains("720p")) "720p HD (mp4)"
                else if (streamUrl.contains("1080p")) "1080p Full HD (mp4)"
                else if (streamUrl.contains("480p")) "480p SD (mp4)"
                else "Direct MP4 #${index + 1}"

                PlayableStreamOption(
                    qualityLabel = label,
                    format = "mp4",
                    isMuxed = true,
                    videoUrl = streamUrl,
                    providerType = ProviderType.DIRECT,
                    headers = defaultHeaders
                )
            }

            val bestOption = options.first()
            val thumb = "https://rule34video.com/contents/videos_screenshots/${(id.toIntOrNull() ?: 0) / 1000 * 1000}/$id/preview.jpg"

            Log.i(TAG, "[Rule34Video] Direct extraction successful with ${options.size} stream options")
            StreamData(
                videoId = id,
                videoUrl = bestOption.videoUrl ?: "",
                title = title,
                channelName = "Rule34Video",
                thumbnailUrl = thumb,
                availableStreamOptions = options,
                selectedStreamOption = bestOption,
                providerId = PROVIDER_ID,
                providerType = ProviderType.DIRECT,
                headers = defaultHeaders
            )
        } catch (e: Exception) {
            Log.e(TAG, "[Rule34Video] Direct extraction failed: ${e.message}", e)
            null
        }
    }

    suspend fun getHome(context: Context, limit: Int = 25): List<VideoItem> = withContext(Dispatchers.IO) {
        MultiSourceProvider.getHome(context, "rule34video", limit)
    }

    suspend fun search(context: Context, query: String, limit: Int = 25): List<VideoItem> = withContext(Dispatchers.IO) {
        MultiSourceProvider.search(context, "rule34video", query, limit)
    }
}
