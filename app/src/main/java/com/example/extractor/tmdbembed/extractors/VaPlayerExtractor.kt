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

object VaPlayerExtractor {
    private const val TAG = "VaPlayerExtractor"
    private const val STREAM_API = "https://streamdata.vaplayer.ru/api.php"
    private const val EMBED_BASE = "https://nextgencloudfabric.com"
    private const val TMDB_API_KEY = "844781e64eb5904944883492e8038b34"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private suspend fun resolveImdbId(tmdbId: String, isTv: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val type = if (isTv) "tv" else "movie"
            val url = "https://api.themoviedb.org/3/$type/$tmdbId/external_ids?api_key=$TMDB_API_KEY"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = body.toJsonObjectOrNull()
                val imdb = json?.optString("imdb_id", "") ?: ""
                if (imdb.isNotBlank()) imdb else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            val imdbId = request.imdbId ?: resolveImdbId(request.tmdbId, request.isTv)
            if (imdbId.isNullOrBlank()) {
                Log.w(TAG, "VaPlayer requires IMDb ID, could not resolve for TMDB ${request.tmdbId}")
                return@withContext emptyList()
            }

            val embedReferer = if (request.isTv) {
                "$EMBED_BASE/embed/tv/$imdbId/${request.season}/${request.episode}"
            } else {
                "$EMBED_BASE/embed/movie/$imdbId"
            }

            val apiUrl = if (request.isTv) {
                "$STREAM_API?imdb=$imdbId&type=tv&season=${request.season}&episode=${request.episode}"
            } else {
                "$STREAM_API?imdb=$imdbId&type=movie"
            }

            val apiReq = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", embedReferer)
                .header("Origin", EMBED_BASE)
                .build()

            val body = client.newCall(apiReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            val json = body.toJsonObjectOrNull() ?: return@withContext emptyList()
            val data = json.optJSONObject("data") ?: return@withContext emptyList()

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Referer" to embedReferer,
                "Origin" to EMBED_BASE
            )

            // Parse subtitles
            val captions = mutableListOf<CaptionOption>()
            val subsArray = data.optJSONArray("default_subs")
            if (subsArray != null) {
                for (i in 0 until subsArray.length()) {
                    val subObj = subsArray.optJSONObject(i) ?: continue
                    val label = subObj.optString("language", subObj.optString("label", "Sub $i"))
                    val url = subObj.optString("url", "")
                    if (url.isNotBlank()) {
                        captions.add(CaptionOption(languageName = label, languageCode = "en", format = "vtt", url = url))
                    }
                }
            }

            // Parse stream_urls
            val streamUrls = data.optJSONArray("stream_urls")
            if (streamUrls != null) {
                for (i in 0 until streamUrls.length()) {
                    val sUrl = streamUrls.optString(i, "")
                    if (sUrl.isNotBlank() && sUrl.startsWith("http")) {
                        streams.add(
                            ExtractedStream(
                                title = "${request.title} [VaPlayer • Server ${i + 1}]",
                                url = sUrl,
                                quality = "1080p",
                                source = TMDBEmbedSource.VAPLAYER,
                                isHls = sUrl.contains(".m3u8"),
                                headers = headers,
                                subtitles = captions
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "VaPlayer extraction note: ${e.message}")
        }
        streams
    }
}
