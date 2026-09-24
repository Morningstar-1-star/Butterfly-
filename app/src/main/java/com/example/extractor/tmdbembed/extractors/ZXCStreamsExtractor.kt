package com.example.extractor.tmdbembed.extractors

import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object ZXCStreamsExtractor {
    private const val TAG = "ZXCStreamsExtractor"
    private const val INITIAL_BASE = "https://r1.zxcstream.xyz"
    private const val SALT = "3435443433"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun sha512Hex(data: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-512")
            val digest = md.digest(data.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            val servers = listOf("icarus", "berkas", "orion", "athena")
            val rt = System.currentTimeMillis()
            val token = sha512Hex("$rt:$SALT:${request.tmdbId}").take(64)

            for (srv in servers) {
                val streamUrl = if (request.isTv) {
                    "$INITIAL_BASE/stream/$srv/tv/${request.tmdbId}/${request.season}/${request.episode}?token=$token"
                } else {
                    "$INITIAL_BASE/stream/$srv/movie/${request.tmdbId}?token=$token"
                }

                streams.add(
                    ExtractedStream(
                        title = "${request.title} [ZXCStreams • ${srv.replaceFirstChar { it.uppercase() }}]",
                        url = streamUrl,
                        quality = "1080p",
                        source = TMDBEmbedSource.ZXCSTREAMS,
                        isHls = true,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                            "Referer" to "$INITIAL_BASE/"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "ZXCStreams extraction failed: ${e.message}", e)
        }
        streams
    }
}
