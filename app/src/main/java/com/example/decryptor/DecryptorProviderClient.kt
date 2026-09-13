package com.example.decryptor

import android.content.Context
import android.util.Log
import com.example.model.CaptionOption
import com.example.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DecryptorSubtitle(
    val lang: String = "English",
    val url: String
)

data class DecryptorServer(
    val name: String,
    val type: String = "m3u8",
    val quality: String = "1080p",
    val proxyUrl: String? = null,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<DecryptorSubtitle> = emptyList(),
    val status: String = "Online"
) {
    val effectivePlayableUrl: String
        get() = (if (!proxyUrl.isNullOrBlank()) proxyUrl else url).orEmpty()
}

data class DecryptorExtractResult(
    val success: Boolean,
    val servers: List<DecryptorServer> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Enterprise client for the Decryptor multi-server extractor and HLS proxy.
 *
 * Capabilities:
 * - Direct resolution from TMDB ID or TMDB URL (Movie & TV with Season/Episode)
 * - Nxsha multi-server extraction (Vidhide, Turbo, Lulustream, Vidara, Fast CDN, etc.)
 * - Strict HTTP header preservation (Referer, Origin, User-Agent)
 * - Safe brief in-memory TTL caching (5 minutes, never permanent)
 * - Resilient server failover tracking
 * - Complete isolation from other providers (never throws unhandled exceptions)
 */
object DecryptorProviderClient {

    private const val TAG = "DecryptorClient"
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes brief metadata cache

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private data class CachedResult(
        val timestamp: Long,
        val result: DecryptorExtractResult
    )

    private val memoryCache = ConcurrentHashMap<String, CachedResult>()
    private val failedServerUrls = ConcurrentHashMap.newKeySet<String>()

    /**
     * Mark a specific server URL as failed so subsequent attempts prefer other servers.
     */
    fun markServerFailed(url: String?) {
        if (!url.isNullOrBlank()) {
            failedServerUrls.add(url)
            Log.d(TAG, "Marked server URL as failed: $url")
        }
    }

    fun isServerFailed(url: String?): Boolean {
        return url != null && failedServerUrls.contains(url)
    }

    fun clearFailedServers() {
        failedServerUrls.clear()
    }

    /**
     * Extract playable multi-server streams for a TMDB movie or TV episode.
     */
    suspend fun extract(
        context: Context? = null,
        tmdbIdOrUrl: String,
        mediaType: String = "movie",
        season: Int? = null,
        episode: Int? = null,
        title: String = ""
    ): DecryptorExtractResult = withContext(Dispatchers.IO) {
        val cleanInput = tmdbIdOrUrl.trim()
        if (cleanInput.isBlank()) {
            return@withContext DecryptorExtractResult(
                success = false,
                errorMessage = "Invalid TMDB ID or URL provided."
            )
        }

        val isTv = mediaType.equals("tv", ignoreCase = true) || mediaType.equals("series", ignoreCase = true)
        val s = season ?: 1
        val ep = episode ?: 1

        // Check if Decryptor provider is enabled
        val isEnabled = AppConfig.isDecryptorEnabled()
        if (!isEnabled) {
            return@withContext DecryptorExtractResult(
                success = false,
                errorMessage = "Decryptor provider is currently disabled in Settings."
            )
        }

        // Standardize cache key
        val cacheKey = if (isTv) "tv_${cleanInput}_s${s}e${ep}" else "movie_${cleanInput}"
        val cached = memoryCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.timestamp) < CACHE_TTL_MS) {
            val validServers = cached.result.servers.filterNot { isServerFailed(it.effectivePlayableUrl) }
            if (validServers.isNotEmpty()) {
                Log.d(TAG, "Returning ${validServers.size} cached Decryptor servers for $cacheKey")
                return@withContext cached.result.copy(servers = validServers)
            }
        }

        val baseUrl = AppConfig.getDecryptorBaseUrl().trimEnd('/')
        val endpoint = "$baseUrl/api/extract"

        // Format TMDB target URL
        val targetUrl = when {
            cleanInput.startsWith("http://") || cleanInput.startsWith("https://") -> cleanInput
            isTv -> "https://www.themoviedb.org/tv/$cleanInput/season/$s/episode/$ep"
            else -> "https://www.themoviedb.org/movie/$cleanInput"
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("url", targetUrl)
                if (!cleanInput.startsWith("http")) {
                    put("tmdbId", cleanInput)
                }
                put("type", if (isTv) "tv" else "movie")
                if (isTv) {
                    put("season", s)
                    put("episode", ep)
                }
                if (title.isNotBlank()) {
                    put("title", title)
                }
            }

            Log.i(TAG, "Calling Decryptor extraction on $endpoint for $targetUrl")

            val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "Butterfly/1.0 (Android; Decryptor-Client)")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = "Decryptor server returned HTTP ${response.code}: ${response.message}"
                Log.w(TAG, "$errorMsg - Body: $responseBody")
                return@withContext DecryptorExtractResult(
                    success = false,
                    errorMessage = errorMsg
                )
            }

            val parsedServers = parseServersResponse(responseBody, baseUrl)
            if (parsedServers.isEmpty()) {
                Log.w(TAG, "No playable servers extracted from Decryptor response")
                return@withContext DecryptorExtractResult(
                    success = false,
                    errorMessage = "No playable stream servers were found by Decryptor for this title."
                )
            }

            val result = DecryptorExtractResult(
                success = true,
                servers = parsedServers
            )

            // Cache safe metadata briefly
            memoryCache[cacheKey] = CachedResult(timestamp = now, result = result)
            Log.i(TAG, "Successfully extracted ${parsedServers.size} Decryptor servers")
            return@withContext result

        } catch (e: Exception) {
            val err = "Decryptor extraction failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
            Log.e(TAG, err, e)
            return@withContext DecryptorExtractResult(
                success = false,
                errorMessage = err
            )
        }
    }

    /**
     * Parses the Decryptor JSON response payload into strongly-typed servers.
     */
    private fun parseServersResponse(jsonString: String, baseUrl: String): List<DecryptorServer> {
        val servers = mutableListOf<DecryptorServer>()
        if (jsonString.isBlank()) return servers

        try {
            val root = JSONObject(jsonString)

            // The JSON might contain "servers" or "data.servers" or "sources"
            val serversArray: JSONArray? = when {
                root.has("servers") -> root.optJSONArray("servers")
                root.has("data") && root.optJSONObject("data")?.has("servers") == true -> root.optJSONObject("data")?.optJSONArray("servers")
                root.has("sources") -> root.optJSONArray("sources")
                else -> null
            }

            if (serversArray != null) {
                for (i in 0 until serversArray.length()) {
                    val serverObj = serversArray.optJSONObject(i) ?: continue

                    val name = serverObj.optString("name").ifBlank {
                        serverObj.optString("server").ifBlank { "Server ${i + 1}" }
                    }
                    val type = serverObj.optString("type").ifBlank { "m3u8" }
                    val quality = serverObj.optString("quality").ifBlank { "1080p" }
                    val status = serverObj.optString("status").ifBlank { "Online" }

                    // Resolve relative proxyUrl if present
                    var proxyUrl = serverObj.optString("proxyUrl").ifBlank { null }
                    if (proxyUrl != null && !proxyUrl.startsWith("http://") && !proxyUrl.startsWith("https://")) {
                        proxyUrl = "$baseUrl/${proxyUrl.trimStart('/')}"
                    }

                    var rawUrl = serverObj.optString("url").ifBlank {
                        serverObj.optString("file").ifBlank { serverObj.optString("streamUrl").ifBlank { null } }
                    }
                    if (rawUrl != null && !rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                        rawUrl = "$baseUrl/${rawUrl.trimStart('/')}"
                    }

                    // Parse custom headers (Referer, Origin, User-Agent, etc.)
                    val headers = mutableMapOf<String, String>()
                    val headersObj = serverObj.optJSONObject("headers")
                    if (headersObj != null) {
                        val keys = headersObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = headersObj.optString(key)
                            if (value.isNotBlank()) {
                                headers[key] = value
                            }
                        }
                    }

                    // Parse subtitles
                    val subtitles = mutableListOf<DecryptorSubtitle>()
                    val subArray = serverObj.optJSONArray("subtitles")
                    if (subArray != null) {
                        for (s in 0 until subArray.length()) {
                            val subItem = subArray.opt(s)
                            if (subItem is JSONObject) {
                                val lang = subItem.optString("lang").ifBlank {
                                    subItem.optString("language").ifBlank { "English" }
                                }
                                var sUrl = subItem.optString("url").ifBlank { subItem.optString("file") }
                                if (sUrl.isNotBlank()) {
                                    if (!sUrl.startsWith("http://") && !sUrl.startsWith("https://")) {
                                        sUrl = "$baseUrl/${sUrl.trimStart('/')}"
                                    }
                                    subtitles.add(DecryptorSubtitle(lang = lang, url = sUrl))
                                }
                            } else if (subItem is String && subItem.isNotBlank()) {
                                var sUrl = subItem
                                if (!sUrl.startsWith("http://") && !sUrl.startsWith("https://")) {
                                    sUrl = "$baseUrl/${sUrl.trimStart('/')}"
                                }
                                subtitles.add(DecryptorSubtitle(lang = "Track ${s + 1}", url = sUrl))
                            }
                        }
                    }

                    // If neither proxyUrl nor rawUrl is provided, skip
                    if (proxyUrl.isNullOrBlank() && rawUrl.isNullOrBlank()) {
                        continue
                    }

                    servers.add(
                        DecryptorServer(
                            name = name,
                            type = type,
                            quality = quality,
                            proxyUrl = proxyUrl,
                            url = rawUrl,
                            headers = headers,
                            subtitles = subtitles,
                            status = status
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Decryptor response JSON: ${e.message}", e)
        }

        return servers
    }

    /**
     * Converts Decryptor subtitles to app's CaptionOption models.
     */
    fun convertSubtitlesToCaptions(subtitles: List<DecryptorSubtitle>): List<CaptionOption> {
        return subtitles.mapIndexed { idx, sub ->
            val langCode = when {
                sub.lang.contains("eng", ignoreCase = true) -> "en"
                sub.lang.contains("spa", ignoreCase = true) -> "es"
                sub.lang.contains("fre", ignoreCase = true) -> "fr"
                sub.lang.contains("ger", ignoreCase = true) -> "de"
                sub.lang.contains("ita", ignoreCase = true) -> "it"
                sub.lang.contains("por", ignoreCase = true) -> "pt"
                sub.lang.contains("rus", ignoreCase = true) -> "ru"
                sub.lang.contains("hin", ignoreCase = true) -> "hi"
                sub.lang.contains("ara", ignoreCase = true) -> "ar"
                sub.lang.contains("chi", ignoreCase = true) -> "zh"
                sub.lang.contains("jpn", ignoreCase = true) -> "ja"
                sub.lang.contains("kor", ignoreCase = true) -> "ko"
                else -> "en_${idx + 1}"
            }
            CaptionOption(
                languageName = sub.lang,
                languageCode = langCode,
                format = if (sub.url.contains(".vtt", ignoreCase = true)) "vtt" else "srt",
                url = sub.url
            )
        }
    }
}
