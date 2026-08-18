package com.example.extractor

import android.net.Uri
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object EpornerProvider {
    private const val TAG = "EpornerProvider"
    const val PROVIDER_ID = "eporner"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            var request = chain.request()
            val builder = request.newBuilder()
            if (request.header("User-Agent") == null) {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            }
            if (request.header("Referer") == null) {
                builder.header("Referer", "https://www.eporner.com/")
            }
            chain.proceed(builder.build())
        }
        .build()

    suspend fun getStreamData(urlOrId: String): StreamData? = withContext(Dispatchers.IO) {
        val targetUrl = when {
            urlOrId.startsWith("http") -> urlOrId
            else -> "https://www.eporner.com/video-$urlOrId/"
        }

        try {
            val req = Request.Builder().url(targetUrl).build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext null

            var title = "Eporner Video"
            val titleMatcher = Pattern.compile("<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
            if (titleMatcher.find()) {
                title = titleMatcher.group(1) ?: title
            } else {
                val tMatch = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
                if (tMatch.find()) {
                    title = tMatch.group(1)?.substringBefore("- EPORNER")?.trim() ?: title
                }
            }

            var thumb = ""
            val thumbMatcher = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
            if (thumbMatcher.find()) {
                thumb = thumbMatcher.group(1) ?: ""
            }

            val options = mutableListOf<PlayableStreamOption>()
            val epornerHeaders = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Referer" to "https://www.eporner.com/"
            )

            val mp4Pattern = Pattern.compile("(https?://[^\"]+?\\.mp4[^\"]*)", Pattern.CASE_INSENSITIVE)
            val matcher = mp4Pattern.matcher(html)
            val seenUrls = mutableSetOf<String>()

            while (matcher.find()) {
                val streamUrl = matcher.group(1)?.replace("&amp;", "&") ?: continue
                if (seenUrls.contains(streamUrl)) continue
                seenUrls.add(streamUrl)

                val qualityLabel = when {
                    streamUrl.contains("1080p", ignoreCase = true) -> "1080p MP4"
                    streamUrl.contains("720p", ignoreCase = true) -> "720p MP4"
                    streamUrl.contains("480p", ignoreCase = true) -> "480p MP4"
                    streamUrl.contains("360p", ignoreCase = true) -> "360p MP4"
                    else -> "HD MP4"
                }

                options.add(
                    PlayableStreamOption(
                        qualityLabel = qualityLabel,
                        format = "mp4",
                        isMuxed = true,
                        videoUrl = streamUrl,
                        providerType = ProviderType.DIRECT,
                        headers = epornerHeaders
                    )
                )
            }

            if (options.isEmpty()) {
                return@withContext null
            }

            val sortedOptions = options.sortedByDescending { 
                when {
                    it.qualityLabel.contains("1080p") -> 1080
                    it.qualityLabel.contains("720p") -> 720
                    it.qualityLabel.contains("480p") -> 480
                    else -> 360
                }
            }

            val bestOption = sortedOptions.first()

            StreamData(
                videoId = targetUrl.substringAfter("video-").substringBefore("/"),
                videoUrl = bestOption.videoUrl ?: "",
                title = title,
                channelName = "Eporner",
                description = title,
                thumbnailUrl = thumb,
                availableStreamOptions = sortedOptions,
                selectedStreamOption = bestOption,
                providerId = PROVIDER_ID,
                providerType = ProviderType.DIRECT,
                headers = epornerHeaders
            )
        } catch (e: Exception) {
            Log.e(TAG, "Eporner extraction failed: ${e.message}", e)
            null
        }
    }
}
