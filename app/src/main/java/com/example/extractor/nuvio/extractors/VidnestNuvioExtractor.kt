package com.example.extractor.nuvio.extractors

import android.util.Log
import com.example.extractor.nuvio.NuvioDomainManager
import com.example.extractor.nuvio.NuvioStreamResult
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VidnestNuvioExtractor {
    private const val TAG = "VidnestNuvio"
    private const val PROVIDER_ID = "vidnest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun extract(request: TMDBMediaRequest): List<NuvioStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NuvioStreamResult>()
        val domain = NuvioDomainManager.getDomain(PROVIDER_ID)

        try {
            val url = if (request.isTv) {
                "$domain/api/source/tv/${request.tmdbId}/${request.season ?: 1}/${request.episode ?: 1}"
            } else {
                "$domain/api/source/movie/${request.tmdbId}"
            }

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", domain)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val streamUrl = json.optString("url", "").ifBlank { json.optString("file", "") }
                    if (streamUrl.isNotBlank()) {
                        results.add(
                            NuvioStreamResult(
                                name = "Vidnest",
                                title = "${request.title} (Vidnest Encrypted)",
                                url = streamUrl,
                                quality = "1080p",
                                isHls = streamUrl.contains(".m3u8"),
                                providerId = PROVIDER_ID,
                                headers = mapOf("Referer" to domain, "User-Agent" to "Mozilla/5.0")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vidnest Nuvio note: ${e.message}")
        }
        return@withContext results
    }
}
