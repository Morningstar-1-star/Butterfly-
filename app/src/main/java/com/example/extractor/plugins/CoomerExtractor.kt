package com.example.extractor.plugins

import android.content.Context
import android.util.Log
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Coomer / Kemono Media Extractor Plugin.
 * (Adapted from schmoaaaaah/yt-dlp-coomer)
 */
class CoomerExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : ExtractorPlugin {

    override val id: String = "coomer"
    override val name: String = "Coomer / Kemono Extractor"
    override val version: String = "1.0.4"
    override val isEnabled: Boolean = true

    override fun canHandle(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("coomer.su") || u.contains("coomer.party") || u.contains("kemono.su") || u.contains("kemono.party")
    }

    override suspend fun extract(context: Context, url: String): StreamData? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val html = resp.body?.string() ?: ""
            val doc = Jsoup.parse(html, url)
            val title = doc.select(".post__title, h1.post__title").firstOrNull()?.text() ?: "Coomer Media"
            val author = doc.select(".post__user-name").firstOrNull()?.text() ?: "Creator"

            val videoSrc = doc.select("video source, video").firstOrNull()?.attr("src")
                ?: doc.select("a.post__attachment-link[href$='.mp4']").firstOrNull()?.attr("href")

            if (!videoSrc.isNullOrBlank()) {
                val fullUrl = if (videoSrc.startsWith("http")) videoSrc else "https://coomer.su$videoSrc"
                val option = PlayableStreamOption(
                    qualityLabel = "Original MP4",
                    format = "mp4",
                    isMuxed = true,
                    videoUrl = fullUrl,
                    providerType = ProviderType.DIRECT
                )
                return@withContext StreamData(
                    videoId = url,
                    videoUrl = fullUrl,
                    title = title,
                    channelName = author,
                    availableStreamOptions = listOf(option),
                    selectedStreamOption = option,
                    providerId = id
                )
            }
        } catch (e: Exception) {
            Log.w("CoomerExtractor", "Coomer extraction failed: ${e.message}")
        }
        null
    }
}

/**
 * PMVHaven Media Extractor Plugin.
 * (Adapted from Earthworm-Banana/yt-dlp-PMVHaven_com-plugin)
 */
class PMVHavenExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : ExtractorPlugin {

    override val id: String = "pmvhaven"
    override val name: String = "PMVHaven Extractor"
    override val version: String = "1.0.2"
    override val isEnabled: Boolean = true

    override fun canHandle(url: String): Boolean {
        return url.lowercase().contains("pmvhaven.com")
    }

    override suspend fun extract(context: Context, url: String): StreamData? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val html = resp.body?.string() ?: ""
            val doc = Jsoup.parse(html, url)
            val title = doc.select("h1, .video-title").firstOrNull()?.text() ?: "PMV Video"
            val videoEl = doc.select("video source, video").firstOrNull()
            val videoUrl = videoEl?.attr("src") ?: doc.select("iframe").firstOrNull()?.attr("src")

            if (!videoUrl.isNullOrBlank()) {
                val fullUrl = if (videoUrl.startsWith("http")) videoUrl else "https://pmvhaven.com$videoUrl"
                val option = PlayableStreamOption(
                    qualityLabel = "1080p MP4",
                    format = "mp4",
                    isMuxed = true,
                    videoUrl = fullUrl,
                    providerType = ProviderType.DIRECT
                )
                return@withContext StreamData(
                    videoId = url,
                    videoUrl = fullUrl,
                    title = title,
                    channelName = "PMVHaven",
                    availableStreamOptions = listOf(option),
                    selectedStreamOption = option,
                    providerId = id
                )
            }
        } catch (e: Exception) {
            Log.w("PMVHavenExtractor", "PMVHaven extraction error: ${e.message}")
        }
        null
    }
}
