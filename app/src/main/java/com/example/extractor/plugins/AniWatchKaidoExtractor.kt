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
 * AniWatch / Kaido Specialized Anime Extractor Plugin.
 * (Adapted from Tons-7/yt-dlp-aniwatchtv-kaido)
 */
class AniWatchKaidoExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : ExtractorPlugin {

    override val id: String = "aniwatch"
    override val name: String = "AniWatch / Kaido Extractor"
    override val version: String = "1.1.4"
    override val isEnabled: Boolean = true

    override fun canHandle(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("aniwatchtv.to") || u.contains("kaido.to") || u.contains("aniwatch.to")
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
            val title = doc.select(".film-name").firstOrNull()?.text() ?: doc.title()
            val poster = doc.select(".film-poster img").firstOrNull()?.attr("src")

            val match = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(html)
            val m3u8Url = match?.value

            if (!m3u8Url.isNullOrBlank()) {
                val headers = mapOf("Referer" to url, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                val option = PlayableStreamOption(
                    qualityLabel = "AniWatch HLS",
                    format = "m3u8",
                    isMuxed = true,
                    videoUrl = m3u8Url,
                    providerType = ProviderType.DIRECT,
                    headers = headers
                )
                return@withContext StreamData(
                    videoId = url,
                    videoUrl = m3u8Url,
                    title = title,
                    channelName = "AniWatch",
                    thumbnailUrl = poster,
                    availableStreamOptions = listOf(option),
                    selectedStreamOption = option,
                    hlsUrl = m3u8Url,
                    providerId = id,
                    headers = headers
                )
            }
        } catch (e: Exception) {
            Log.w("AniWatchKaidoExtractor", "AniWatch extraction failed: ${e.message}")
        }
        null
    }
}
