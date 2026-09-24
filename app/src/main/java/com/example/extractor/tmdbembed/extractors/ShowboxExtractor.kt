package com.example.extractor.tmdbembed.extractors

import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ShowboxExtractor {
    private const val TAG = "ShowboxExtractor"
    private const val FEBBOX_BASE = "https://www.febbox.com"
    private const val PSTREAM_API = "https://pstream.org/api"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            // First attempt PStream API with IMDb ID or TMDB ID
            val targetId = request.imdbId ?: request.tmdbId
            val apiUrl = if (request.isTv) {
                "$PSTREAM_API/tv/$targetId/${request.season}/${request.episode}"
            } else {
                "$PSTREAM_API/movie/$targetId"
            }

            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://pstream.org/")
                .build()

            val body = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }

            if (body.isNotBlank()) {
                val json = JSONObject(body)
                val streamsArr = json.optJSONArray("streams")
                if (streamsArr != null) {
                    for (i in 0 until streamsArr.length()) {
                        val item = streamsArr.optJSONObject(i) ?: continue
                        val url = item.optString("url", "")
                        if (url.isNotBlank() && url.startsWith("http")) {
                            val qual = item.optString("quality", "1080p")
                            streams.add(
                                ExtractedStream(
                                    title = "${request.title} [Showbox/FebBox • $qual]",
                                    url = url,
                                    quality = qual,
                                    source = TMDBEmbedSource.SHOWBOX,
                                    isHls = url.contains(".m3u8"),
                                    headers = mapOf(
                                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                        "Referer" to "https://www.febbox.com/"
                                    )
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Showbox extraction failed: ${e.message}", e)
        }
        streams
    }
}
