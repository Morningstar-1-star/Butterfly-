package com.example.extractor.tmdbembed.extractors

import android.util.Base64
import android.util.Log
import com.example.extractor.tmdbembed.ExtractedStream
import com.example.extractor.tmdbembed.TMDBEmbedSource
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CastleTvExtractor {
    private const val TAG = "CastleTvExtractor"
    private const val BASE_URL = "https://api.hlowb.com"
    private const val CHANNEL = "IndiaA"
    private const val CLIENT = "1"
    private const val LANG = "en-US"
    private const val PKG = "com.external.castle"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun deriveKey(securityKeyB64: String): ByteArray {
        val keyBytes = Base64.decode(securityKeyB64, Base64.DEFAULT)
        val suffix = "T!BgJB".toByteArray(Charsets.UTF_8)
        val combined = ByteArray(16)
        val total = keyBytes.size + suffix.size
        val source = ByteArray(total)
        System.arraycopy(keyBytes, 0, source, 0, keyBytes.size)
        System.arraycopy(suffix, 0, source, keyBytes.size, suffix.size)

        for (i in 0 until 16) {
            combined[i] = if (i < total) source[i] else 0
        }
        return combined
    }

    private fun decryptCastle(cipherText: String, key: ByteArray): String {
        return try {
            val trimmed = cipherText.trim().replace("\n", "").replace("\r", "")
            val cipherBytes = Base64.decode(trimmed, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(key)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decrypted = cipher.doFinal(cipherBytes)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Castle decrypt failed: ${e.message}")
            ""
        }
    }

    suspend fun extract(request: TMDBMediaRequest): List<ExtractedStream> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<ExtractedStream>()
        try {
            // Step 1: Security Key
            val secKeyUrl = "$BASE_URL/v0.1/system/getSecurityKey/1?channel=$CHANNEL&clientType=$CLIENT&lang=$LANG"
            val secKeyReq = Request.Builder()
                .url(secKeyUrl)
                .header("User-Agent", "okhttp/4.9.3")
                .header("Accept", "application/json")
                .header("Referer", BASE_URL)
                .build()

            val secKeyResp = client.newCall(secKeyReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            val secKeyJson = JSONObject(secKeyResp)
            val secKey = secKeyJson.optString("data", "")
            if (secKey.isBlank()) return@withContext emptyList()

            val aesKey = deriveKey(secKey)

            // Step 2: Search by title
            val cleanTitle = request.title.ifBlank { "Fight Club" }
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val searchUrl = "$BASE_URL/film-api/v1.1.0/movie/searchByKeyword?channel=$CHANNEL&clientType=$CLIENT&keyword=$encodedTitle&lang=$LANG&mode=1&packageName=$PKG&page=1&size=5"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "okhttp/4.9.3")
                .header("Accept", "application/json")
                .header("Referer", BASE_URL)
                .build()

            val searchBody = client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            var searchCipher = searchBody.trim()
            try {
                val parsed = JSONObject(searchCipher)
                if (parsed.has("data") && parsed.get("data") is String) {
                    searchCipher = parsed.getString("data")
                }
            } catch (e: Exception) {}

            val searchPlain = decryptCastle(searchCipher, aesKey)
            if (searchPlain.isBlank()) return@withContext emptyList()

            val searchJson = JSONObject(searchPlain)
            val searchData = searchJson.optJSONObject("data") ?: return@withContext emptyList()
            val rows = searchData.optJSONArray("rows") ?: return@withContext emptyList()
            if (rows.length() == 0) return@withContext emptyList()

            val firstMovie = rows.getJSONObject(0)
            val movieId = firstMovie.optLong("id", 0L)
            if (movieId == 0L) return@withContext emptyList()

            // Step 3: Fetch movie details
            val detailUrl = "$BASE_URL/film-api/v1.9.9/movie?channel=$CHANNEL&clientType=$CLIENT&lang=$LANG&movieId=$movieId&packageName=$PKG"
            val detailReq = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", "okhttp/4.9.3")
                .header("Accept", "application/json")
                .header("Referer", BASE_URL)
                .build()

            val detailBody = client.newCall(detailReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            var detailCipher = detailBody.trim()
            try {
                val parsed = JSONObject(detailCipher)
                if (parsed.has("data") && parsed.get("data") is String) {
                    detailCipher = parsed.getString("data")
                }
            } catch (e: Exception) {}

            val detailPlain = decryptCastle(detailCipher, aesKey)
            if (detailPlain.isNotBlank()) {
                val detailJson = JSONObject(detailPlain)
                val detailData = detailJson.optJSONObject("data") ?: detailJson
                val resolutions = detailData.optJSONArray("resolutions")
                if (resolutions != null) {
                    for (i in 0 until resolutions.length()) {
                        val resObj = resolutions.optJSONObject(i) ?: continue
                        val playUrl = resObj.optString("playUrl", resObj.optString("url", ""))
                        if (playUrl.isNotBlank() && playUrl.startsWith("http")) {
                            val qual = resObj.optString("resolution", "1080p")
                            streams.add(
                                ExtractedStream(
                                    title = "${request.title} [CastleTV • $qual]",
                                    url = playUrl,
                                    quality = qual,
                                    source = TMDBEmbedSource.CASTLE_TV,
                                    isHls = playUrl.contains(".m3u8"),
                                    headers = mapOf(
                                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                                        "Referer" to BASE_URL
                                    )
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CastleTv extraction failed: ${e.message}", e)
        }
        streams
    }
}
