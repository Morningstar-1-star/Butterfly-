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
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * HiAnime Specialized Stream Extractor Plugin.
 * (Adapted from pratikpatel8982/yt-dlp-hianime)
 */
class HiAnimeExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : ExtractorPlugin {

    override val id: String = "hianime"
    override val name: String = "HiAnime Extractor"
    override val version: String = "1.2.0"
    override val isEnabled: Boolean = true

    override fun canHandle(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("hianime.to") || u.contains("hianime.nz") || u.contains("hianime.sx")
    }

    override suspend fun extract(context: Context, url: String): StreamData? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val doc = Jsoup.parse(resp.body?.string() ?: "", url)
            val title = doc.select(".film-name, h2.film-name, meta[property='og:title']").firstOrNull()?.text()
                ?: doc.title().replace("Watch ", "").replace(" English Sub/Dub online Free on HiAnime.to", "")

            val poster = doc.select(".film-poster img, meta[property='og:image']").firstOrNull()?.attr("src")

            // Look for embed or player sources
            val match = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(doc.html())
            val m3u8Url = match?.value

            if (!m3u8Url.isNullOrBlank()) {
                val headers = mapOf("Referer" to url, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                val option = PlayableStreamOption(
                    qualityLabel = "HiAnime HLS (Multi-Audio)",
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
                    channelName = "HiAnime",
                    thumbnailUrl = poster,
                    availableStreamOptions = listOf(option),
                    selectedStreamOption = option,
                    hlsUrl = m3u8Url,
                    providerId = id,
                    headers = headers
                )
            }
        } catch (e: Exception) {
            Log.w("HiAnimeExtractor", "HiAnime extraction failed: ${e.message}")
        }
        null
    }
}
