package com.example.extractor.tmdbembed.extractors

import android.util.Base64
import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object OneTouchTvExtractor {
    private const val TAG = "OneTouchTvExtractor"
    private const val MAIN_URL = "https://api3.devcorp.me"
    private const val REFERER = "https://onetouchtv.xyz/"

    private val AES_KEY = "im72charPasswordofdInitVectorStm".toByteArray(Charsets.UTF_8)
    private val AES_IV = "im72charPassword".toByteArray(Charsets.UTF_8)

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun decryptOneTouch(encoded: String): String {
        return try {
            var s = encoded
                .replace("-_.", "/")
                .replace("@", "+")
                .replace("\\s+".toRegex(), "")
            val pad = s.length % 4
            if (pad != 0) {
                s += "=".repeat(4 - pad)
            }
            val cipherBytes = Base64.decode(s, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(AES_KEY, "AES")
            val ivSpec = IvParameterSpec(AES_IV)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decrypted = cipher.doFinal(cipherBytes)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "OneTouch decrypt error: ${e.message}")
            ""
        }
    }

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            val title = request.title.ifBlank { "Fight Club" }
            val encoded = URLEncoder.encode(title, "UTF-8")
            val searchUrl = "$MAIN_URL/api/search?q=$encoded"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", REFERER)
                .build()

            val body = client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            if (body.isBlank()) return@withContext emptyList()
            val decrypted = if (body.startsWith("{") || body.startsWith("[")) body else decryptOneTouch(body)
            if (decrypted.isBlank()) return@withContext emptyList()

            val json = JSONObject(decrypted)
            val results = json.optJSONArray("results") ?: json.optJSONArray("data")
            if (results != null && results.length() > 0) {
                val item = results.getJSONObject(0)
                val streamUrl = item.optString("stream_url", item.optString("url", ""))
                if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                    streams.add(
                        ExtractedStream(
                            title = "${request.title} [OneTouchTV • 1080p]",
                            url = streamUrl,
                            quality = "1080p",
                            source = TMDBEmbedSource.ONETOUCH_TV,
                            isHls = streamUrl.contains(".m3u8"),
                            headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                "Referer" to REFERER
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OneTouchTV extraction failed: ${e.message}", e)
        }
        streams
    }
}
