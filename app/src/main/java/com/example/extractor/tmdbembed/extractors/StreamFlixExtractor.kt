package com.example.extractor.tmdbembed.extractors

import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import com.example.extractor.tmdbembed.toJsonObjectOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object StreamFlixExtractor {
    private const val TAG = "StreamFlixExtractor"
    private const val API_BASE = "https://api.streamflix.app"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            val req = Request.Builder()
                .url("$API_BASE/config/config-streamflixapp.json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, */*")
                .build()

            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            val config = body.toJsonObjectOrNull() ?: return@withContext emptyList()
            val streamBase = config.optString("stream_url", config.optString("server", ""))

            if (streamBase.isNotBlank() && streamBase.startsWith("http")) {
                val playUrl = if (request.isTv) {
                    "$streamBase/tv/${request.tmdbId}/${request.season}/${request.episode}/index.m3u8"
                } else {
                    "$streamBase/movie/${request.tmdbId}/index.m3u8"
                }

                streams.add(
                    ExtractedStream(
                        title = "${request.title} [StreamFlix • 1080p HLS]",
                        url = playUrl,
                        quality = "1080p",
                        source = TMDBEmbedSource.STREAMFLIX,
                        isHls = true,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                            "Referer" to "$API_BASE/"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "StreamFlix extraction note: ${e.message}")
        }
        streams
    }
}

