package com.example.extractor.tmdbembed.extractors

import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import com.example.extractor.tmdbembed.toJsonObjectOrNull
import com.example.model.CaptionOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NetMirrorExtractor {
    private const val TAG = "NetMirrorExtractor"
    private const val BASE_URL = "https://net27.cc"
    private const val REFERER = "https://videodownloader.site/"
    private const val ORIGIN = "https://videodownloader.site"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            val apiUrl = if (request.isTv) {
                "$BASE_URL/api/embed-tmdb/${request.tmdbId}?type=tv&se=${request.season}&ep=${request.episode}"
            } else {
                "$BASE_URL/api/embed-tmdb/${request.tmdbId}"
            }

            val apiReq = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", REFERER)
                .header("Origin", ORIGIN)
                .build()

            val body = client.newCall(apiReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            val json = body.toJsonObjectOrNull() ?: return@withContext emptyList()

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Referer" to REFERER,
                "Origin" to ORIGIN
            )

            // Extract captions
            val captionsList = mutableListOf<CaptionOption>()
            val captionsArray = json.optJSONArray("captions")
            if (captionsArray != null) {
                for (i in 0 until captionsArray.length()) {
                    val capObj = captionsArray.optJSONObject(i) ?: continue
                    val label = capObj.optString("label", "Sub $i")
                    val file = capObj.optString("file", "")
                    if (file.isNotBlank()) {
                        captionsList.add(CaptionOption(languageName = label, languageCode = "en", format = "vtt", url = file))
                    }
                }
            }

            // Extract streams object
            val streamsObj = json.optJSONObject("streams")
            if (streamsObj != null) {
                val qualities = listOf("1080", "720", "480", "360")
                for (q in qualities) {
                    val streamUrl = streamsObj.optString(q, "")
                    if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                        val isHls = streamUrl.contains(".m3u8")
                        streams.add(
                            ExtractedStream(
                                title = "${request.title} [NetMirror • ${q}p]",
                                url = streamUrl,
                                quality = "${q}p",
                                source = TMDBEmbedSource.NETMIRROR,
                                isHls = isHls,
                                headers = headers,
                                subtitles = captionsList
                            )
                        )
                    }
                }
            }

            // Fallback HLS
            val fallbackHls = json.optString("fallbackHls", "")
            if (fallbackHls.isNotBlank() && fallbackHls.startsWith("http")) {
                streams.add(
                    ExtractedStream(
                        title = "${request.title} [NetMirror • Master HLS]",
                        url = fallbackHls,
                        quality = "1080p",
                        source = TMDBEmbedSource.NETMIRROR,
                        isHls = true,
                        headers = headers,
                        subtitles = captionsList
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "NetMirror extraction note: ${e.message}")
        }
        streams
    }
}
