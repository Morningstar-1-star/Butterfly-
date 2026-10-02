package com.example.metadata.javinizer

import android.util.Log
import com.example.metadata.JavActor
import com.example.metadata.JavIdParser
import com.example.metadata.JavMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Dedicated REST API Client for Javinizer-Go (https://github.com/javinizer/javinizer-go).
 *
 * Implements the documented Javinizer-Go OpenAPI / Swagger contract:
 * - Health check: GET /api/v1/health (or GET /api/v1/status)
 * - Movie metadata: GET /api/v1/movie/{id} (or POST /api/v1/scrape)
 * - Search: GET /api/v1/search?query={query}
 * - Actress lookup: GET /api/v1/actress/{name}
 */
class JavinizerGoClient(
    private val defaultTimeoutSec: Int = 15
) {
    companion object {
        private const val TAG = "JavinizerGoClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        data class HealthResult(
            val isSuccess: Boolean,
            val latencyMs: Long,
            val serverVersion: String?,
            val statusMessage: String
        )
    }

    private fun getHttpClient(timeoutSec: Int, baseUrl: String = ""): OkHttpClient {
        val isLocal = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1") || baseUrl.isBlank()
        val connectTimeoutMs = if (isLocal) 1200L else 2500L
        val readTimeoutMs = if (isLocal) 2000L else timeoutSec.coerceIn(2, 20).toLong() * 1000L
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(false)
            .build()
    }

    private fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    /**
     * Checks health and server version of the Javinizer-Go service instance.
     */
    suspend fun checkHealth(baseUrl: String, timeoutSec: Int = defaultTimeoutSec): HealthResult = withContext(Dispatchers.IO) {
        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        val client = getHttpClient(timeoutSec, cleanBaseUrl)
        val startTime = System.currentTimeMillis()

        val endpoints = listOf(
            "$cleanBaseUrl/api/v1/health",
            "$cleanBaseUrl/api/v1/status"
        )

        var lastErrorMsg = "Unreachable"

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    var version: String? = null
                    var status = "Online"

                    if (body.startsWith("{")) {
                        try {
                            val json = JSONObject(body)
                            version = json.optString("version", null)
                                ?: json.optString("app_version", null)
                                ?: json.optJSONObject("data")?.optString("version", null)
                            status = json.optString("status", "Online")
                        } catch (e: Exception) {
                            Log.d(TAG, "Health JSON parse notice: ${e.message}")
                        }
                    }

                    return@withContext HealthResult(
                        isSuccess = true,
                        latencyMs = latency,
                        serverVersion = version ?: "v1.5.1+",
                        statusMessage = "Connected ($status • ${latency}ms)"
                    )
                } else {
                    lastErrorMsg = "HTTP ${response.code} on $endpoint"
                }
            } catch (e: Exception) {
                lastErrorMsg = e.message ?: "Connection failed"
            }
        }

        val latency = System.currentTimeMillis() - startTime
        HealthResult(
            isSuccess = false,
            latencyMs = latency,
            serverVersion = null,
            statusMessage = "Unreachable: $lastErrorMsg"
        )
    }

    /**
     * Fetches metadata for a given JAV ID using the documented Javinizer-Go API.
     */
    suspend fun getMovieMetadata(
        javId: String,
        baseUrl: String,
        timeoutSec: Int = defaultTimeoutSec
    ): JavMetadata? = withContext(Dispatchers.IO) {
        val parsedCode = JavIdParser.parse(javId) ?: javId.trim()
        if (parsedCode.isBlank()) return@withContext null

        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        val client = getHttpClient(timeoutSec, cleanBaseUrl)
        val encodedId = URLEncoder.encode(parsedCode, "UTF-8")

        // 1. Primary Documented Endpoint: GET /api/v1/movie/{id}
        val getEndpoint = "$cleanBaseUrl/api/v1/movie/$encodedId"
        try {
            val request = Request.Builder()
                .url(getEndpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val metadata = parseMovieJson(body, parsedCode, cleanBaseUrl)
                    if (metadata != null && metadata.title.isNotBlank()) {
                        Log.i(TAG, "Resolved [$parsedCode] via Javinizer-Go GET /api/v1/movie/$encodedId")
                        return@withContext metadata
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "GET movie request failed for $parsedCode: ${e.message}")
        }

        // 2. Secondary Documented Endpoint: POST /api/v1/scrape (Multi-source aggregation)
        try {
            val postEndpoint = "$cleanBaseUrl/api/v1/scrape"
            val scrapersArray = JSONArray().apply {
                put("r18dev")
                put("dmm")
                put("fanza")
                put("javdb")
                put("javlibrary")
                put("libredmm")
            }
            val payload = JSONObject().apply {
                put("id", parsedCode)
                put("query", parsedCode)
                put("scrapers", scrapersArray)
                put("aggregate", true)
                put("download_images", false)
            }.toString()

            val postRequest = Request.Builder()
                .url(postEndpoint)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val postResponse = client.newCall(postRequest).execute()
            if (postResponse.isSuccessful) {
                val postBody = postResponse.body?.string()
                if (!postBody.isNullOrBlank()) {
                    val metadata = parseMovieJson(postBody, parsedCode, cleanBaseUrl)
                    if (metadata != null && metadata.title.isNotBlank()) {
                        Log.i(TAG, "Resolved [$parsedCode] via Javinizer-Go POST /api/v1/scrape (aggregated)")
                        return@withContext metadata
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "POST scrape failed for $parsedCode: ${e.message}")
        }

        null
    }

    /**
     * Fetches latest JAV releases from Javinizer-Go.
     */
    suspend fun getLatestReleases(
        baseUrl: String,
        page: Int = 1,
        limit: Int = 20,
        timeoutSec: Int = defaultTimeoutSec
    ): List<JavMetadata> = withContext(Dispatchers.IO) {
        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        val client = getHttpClient(timeoutSec, cleanBaseUrl)
        val endpoints = listOf(
            "$cleanBaseUrl/api/v1/latest?page=$page&limit=$limit",
            "$cleanBaseUrl/api/v1/movies/latest?page=$page&limit=$limit",
            "$cleanBaseUrl/api/v1/feed/latest?page=$page&limit=$limit",
            "$cleanBaseUrl/api/v1/releases?page=$page&limit=$limit"
        )

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val list = parseSearchJson(body, cleanBaseUrl)
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "getLatestReleases error on $endpoint: ${e.message}")
            }
        }
        emptyList()
    }

    /**
     * Fetches popular/trending JAV titles from Javinizer-Go.
     */
    suspend fun getPopularMovies(
        baseUrl: String,
        page: Int = 1,
        limit: Int = 20,
        timeoutSec: Int = defaultTimeoutSec
    ): List<JavMetadata> = withContext(Dispatchers.IO) {
        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        val client = getHttpClient(timeoutSec, cleanBaseUrl)
        val endpoints = listOf(
            "$cleanBaseUrl/api/v1/popular?page=$page&limit=$limit",
            "$cleanBaseUrl/api/v1/movies/popular?page=$page&limit=$limit",
            "$cleanBaseUrl/api/v1/feed/popular?page=$page&limit=$limit",
            "$cleanBaseUrl/api/v1/ranking?page=$page&limit=$limit"
        )

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val list = parseSearchJson(body, cleanBaseUrl)
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "getPopularMovies error on $endpoint: ${e.message}")
            }
        }
        emptyList()
    }

    /**
     * Fetches list of actresses with avatars and bios from Javinizer-Go.
     */
    suspend fun getActresses(
        baseUrl: String,
        page: Int = 1,
        limit: Int = 30,
        query: String = "",
        sort: String = "popular",
        timeoutSec: Int = defaultTimeoutSec
    ): List<JavActor> = withContext(Dispatchers.IO) {
        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        val client = getHttpClient(timeoutSec, cleanBaseUrl)
        val encodedQ = if (query.isNotBlank()) "&query=${URLEncoder.encode(query, "UTF-8")}" else ""
        val endpoints = listOf(
            "$cleanBaseUrl/api/v1/actresses?page=$page&limit=$limit&sort=$sort$encodedQ",
            "$cleanBaseUrl/api/v1/actors?page=$page&limit=$limit$encodedQ",
            "$cleanBaseUrl/api/v1/cast?page=$page&limit=$limit$encodedQ"
        )

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val list = parseActressesListJson(body, cleanBaseUrl)
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "getActresses error on $endpoint: ${e.message}")
            }
        }
        emptyList()
    }

    /**
     * Fetches studio/maker list from Javinizer-Go.
     */
    suspend fun getStudios(
        baseUrl: String,
        timeoutSec: Int = defaultTimeoutSec
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        val client = getHttpClient(timeoutSec, cleanBaseUrl)
        val endpoints = listOf(
            "$cleanBaseUrl/api/v1/studios",
            "$cleanBaseUrl/api/v1/makers"
        )
        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val list = parseStringListJson(body)
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    /**
     * Fetches genres and tags list from Javinizer-Go.
     */
    suspend fun getGenres(
        baseUrl: String,
        timeoutSec: Int = defaultTimeoutSec
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        val client = getHttpClient(timeoutSec, cleanBaseUrl)
        val endpoints = listOf(
            "$cleanBaseUrl/api/v1/genres",
            "$cleanBaseUrl/api/v1/tags"
        )
        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val list = parseStringListJson(body)
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    /**
     * Searches movies by genre/tag/studio/actress/series via Javinizer-Go.
     */
    suspend fun searchByCategory(
        type: String,
        value: String,
        baseUrl: String,
        page: Int = 1,
        limit: Int = 20,
        timeoutSec: Int = defaultTimeoutSec
    ): List<JavMetadata> = withContext(Dispatchers.IO) {
        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        val client = getHttpClient(timeoutSec, cleanBaseUrl)
        val encodedVal = URLEncoder.encode(value.trim(), "UTF-8")
        val endpoints = listOf(
            "$cleanBaseUrl/api/v1/browse?type=$type&value=$encodedVal&page=$page&limit=$limit",
            "$cleanBaseUrl/api/v1/$type/$encodedVal?page=$page&limit=$limit",
            "$cleanBaseUrl/api/v1/search?query=$encodedVal&page=$page&limit=$limit"
        )

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val list = parseSearchJson(body, cleanBaseUrl)
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    /**
     * Searches for movies matching a text query via Javinizer-Go.
     */
    suspend fun search(
        query: String,
        baseUrl: String,
        timeoutSec: Int = defaultTimeoutSec
    ): List<JavMetadata> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val parsedCode = JavIdParser.parse(cleanQuery)
        if (parsedCode != null) {
            val direct = getMovieMetadata(parsedCode, baseUrl, timeoutSec)
            if (direct != null) return@withContext listOf(direct)
        }

        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        val client = getHttpClient(timeoutSec, cleanBaseUrl)
        val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")

        val searchUrl = "$cleanBaseUrl/api/v1/search?query=$encodedQuery"
        try {
            val request = Request.Builder()
                .url(searchUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                return@withContext parseSearchJson(body, cleanBaseUrl)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Javinizer-Go search failed for $cleanQuery: ${e.message}")
        }

        emptyList()
    }

    /**
     * Resolves actress biography and avatar via Javinizer-Go actress endpoint.
     */
    suspend fun getActress(
        name: String,
        baseUrl: String,
        timeoutSec: Int = defaultTimeoutSec
    ): JavActor? = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withContext null

        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        val client = getHttpClient(timeoutSec, cleanBaseUrl)
        val encoded = URLEncoder.encode(cleanName, "UTF-8")

        val endpoint = "$cleanBaseUrl/api/v1/actress/$encoded"
        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                return@withContext parseActressJson(body, cleanName, cleanBaseUrl)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Javinizer-Go actress lookup failed for $cleanName: ${e.message}")
        }

        null
    }

    private fun parseMovieJson(jsonStr: String, fallbackCode: String, baseUrl: String): JavMetadata? {
        try {
            val root = JSONObject(jsonStr)
            val movie = root.optJSONObject("data")
                ?: root.optJSONObject("result")
                ?: root.optJSONObject("movie")
                ?: root

            val code = movie.optString("id", "").ifBlank {
                movie.optString("code", "").ifBlank {
                    movie.optString("dvd_id", "").ifBlank {
                        movie.optString("number", fallbackCode)
                    }
                }
            }

            val title = movie.optString("title", "").ifBlank {
                movie.optString("name", code)
            }

            if (title.isBlank() && code.isBlank()) return null

            val originalTitle = movie.optString("original_title", null)?.ifBlank { null }
            val releaseDate = movie.optString("release_date", null)?.ifBlank {
                movie.optString("premiered", null)?.ifBlank { null }
            }
            val year = releaseDate?.take(4) ?: movie.optString("year", null)?.ifBlank { null }

            val durationMinutes = movie.optInt("runtime", 0).takeIf { it > 0 }
                ?: movie.optInt("duration", 0).takeIf { it > 0 }
                ?: movie.optInt("length", 0).takeIf { it > 0 }

            val director = movie.optString("director", null)?.ifBlank {
                val arr = movie.optJSONArray("directors")
                if (arr != null && arr.length() > 0) arr.optString(0) else null
            }

            val studio = movie.optString("studio", null)?.ifBlank {
                movie.optString("maker", null)?.ifBlank { null }
            }

            val label = movie.optString("label", null)?.ifBlank {
                movie.optString("publisher", null)?.ifBlank { null }
            }

            val series = movie.optString("series", null)?.ifBlank { null }

            // Genres & Tags
            val genresList = mutableListOf<String>()
            val genresArr = movie.optJSONArray("genres") ?: movie.optJSONArray("tags")
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val g = genresArr.optString(i).trim()
                    if (g.isNotBlank() && !genresList.contains(g)) {
                        genresList.add(g)
                    }
                }
            }

            // Cast / Actresses
            val castList = mutableListOf<JavActor>()
            val actressesArr = movie.optJSONArray("actresses")
                ?: movie.optJSONArray("actors")
                ?: movie.optJSONArray("cast")
                ?: movie.optJSONArray("stars")

            if (actressesArr != null) {
                for (i in 0 until actressesArr.length()) {
                    val item = actressesArr.opt(i)
                    if (item is JSONObject) {
                        val name = item.optString("name", "").ifBlank {
                            item.optString("actress_name", "")
                        }
                        if (name.isNotBlank()) {
                            val orig = item.optString("japanese_name", null)?.ifBlank {
                                item.optString("original_name", null)?.ifBlank { null }
                            }
                            val avatar = item.optString("image_url", null)?.ifBlank {
                                item.optString("avatar_url", null)?.ifBlank {
                                    item.optString("thumb_url", null)?.ifBlank { null }
                                }
                            }
                            castList.add(
                                JavActor(
                                    name = name,
                                    originalName = orig,
                                    romajiName = item.optString("romaji_name", null)?.ifBlank { null },
                                    avatarUrl = normalizeUrl(avatar, baseUrl),
                                    birthday = item.optString("birthday", null)?.ifBlank { null },
                                    heightCm = item.optInt("height_cm", 0).takeIf { it > 0 },
                                    cupSize = item.optString("cup_size", null)?.ifBlank { null }
                                )
                            )
                        }
                    } else if (item is String && item.isNotBlank()) {
                        castList.add(JavActor(name = item.trim()))
                    }
                }
            }

            // Covers and Images
            val coverUrl = movie.optString("cover_url", null)?.ifBlank {
                movie.optString("poster_url", null)?.ifBlank {
                    movie.optString("thumb_url", null)?.ifBlank {
                        movie.optString("fanart_url", null)?.ifBlank { null }
                    }
                }
            }

            val posterUrl = movie.optString("poster_url", null)?.ifBlank { coverUrl }

            val screenshots = mutableListOf<String>()
            val ssArr = movie.optJSONArray("screenshots")
                ?: movie.optJSONArray("sample_images")
                ?: movie.optJSONArray("preview_images")
                ?: movie.optJSONArray("samples")

            if (ssArr != null) {
                for (i in 0 until ssArr.length()) {
                    val img = ssArr.optString(i).trim()
                    if (img.isNotBlank()) {
                        val normalizedImg = normalizeUrl(img, baseUrl)
                        if (normalizedImg != null && !screenshots.contains(normalizedImg)) {
                            screenshots.add(normalizedImg)
                        }
                    }
                }
            }

            val trailerUrl = movie.optString("trailer_url", null)?.ifBlank {
                movie.optString("sample_video_url", null)?.ifBlank {
                    movie.optString("preview_video_url", null)?.ifBlank { null }
                }
            }

            val rating = movie.optDouble("rating", 0.0).takeIf { it > 0.0 }?.toFloat()
                ?: movie.optDouble("score", 0.0).takeIf { it > 0.0 }?.toFloat()

            val plot = movie.optString("plot", null)?.ifBlank {
                movie.optString("description", null)?.ifBlank {
                    movie.optString("outline", null)?.ifBlank { null }
                }
            }

            val finalCover = normalizeUrl(coverUrl ?: posterUrl, baseUrl)
            val finalThumb = normalizeUrl(posterUrl ?: coverUrl, baseUrl)

            return JavMetadata(
                id = code,
                code = code,
                title = title,
                originalTitle = originalTitle,
                plotOverview = plot,
                releaseDate = releaseDate,
                year = year,
                durationMinutes = durationMinutes,
                director = director,
                studio = studio,
                label = label,
                series = series,
                genres = genresList,
                coverUrl = finalCover,
                thumbUrl = finalThumb,
                previewImages = screenshots,
                cast = castList,
                rating = rating,
                sampleVideoUrl = trailerUrl,
                providerSource = "Javinizer-Go",
                detailUrl = "$baseUrl/movie/$code"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing movie JSON: ${e.message}")
            return null
        }
    }

    private fun parseSearchJson(jsonStr: String, baseUrl: String): List<JavMetadata> {
        val list = mutableListOf<JavMetadata>()
        try {
            val root = JSONObject(jsonStr)
            val itemsArr = root.optJSONArray("data")
                ?: root.optJSONArray("results")
                ?: root.optJSONArray("movies")
                ?: root.optJSONArray("items")

            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val obj = itemsArr.optJSONObject(i) ?: continue
                    val meta = parseMovieJson(obj.toString(), "", baseUrl)
                    if (meta != null && meta.title.isNotBlank()) {
                        list.add(meta)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error parsing search JSON: ${e.message}")
        }
        return list
    }

    private fun parseActressJson(jsonStr: String, defaultName: String, baseUrl: String): JavActor? {
        try {
            val root = JSONObject(jsonStr)
            val obj = root.optJSONObject("data")
                ?: root.optJSONObject("actress")
                ?: root

            val name = obj.optString("name", defaultName)
            val orig = obj.optString("japanese_name", null)?.ifBlank {
                obj.optString("original_name", null)?.ifBlank { null }
            }
            val avatar = obj.optString("image_url", null)?.ifBlank {
                obj.optString("avatar_url", null)?.ifBlank {
                    obj.optString("thumb_url", null)?.ifBlank { null }
                }
            }

            return JavActor(
                name = name,
                originalName = orig,
                romajiName = obj.optString("romaji_name", null)?.ifBlank { null },
                avatarUrl = normalizeUrl(avatar, baseUrl),
                birthday = obj.optString("birthday", null)?.ifBlank { null },
                heightCm = obj.optInt("height_cm", 0).takeIf { it > 0 },
                cupSize = obj.optString("cup_size", null)?.ifBlank { null }
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseActressesListJson(jsonStr: String, baseUrl: String): List<JavActor> {
        val list = mutableListOf<JavActor>()
        try {
            val root = JSONObject(jsonStr)
            val array = root.optJSONArray("data")
                ?: root.optJSONArray("results")
                ?: root.optJSONArray("actresses")
                ?: root.optJSONArray("actors")
                ?: root.optJSONArray("items")

            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i)
                    if (obj != null) {
                        val actor = parseActressJson(obj.toString(), "", baseUrl)
                        if (actor != null && actor.name.isNotBlank()) {
                            list.add(actor)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error parsing actresses list JSON: ${e.message}")
        }
        return list
    }

    private fun parseStringListJson(jsonStr: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val root = JSONObject(jsonStr)
            val array = root.optJSONArray("data")
                ?: root.optJSONArray("results")
                ?: root.optJSONArray("items")
                ?: root.optJSONArray("list")

            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    if (item is String && item.isNotBlank()) {
                        list.add(item.trim())
                    } else if (item is JSONObject) {
                        val name = item.optString("name", "").ifBlank { item.optString("title", "") }
                        if (name.isNotBlank()) list.add(name.trim())
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun normalizeUrl(url: String?, baseUrl: String): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "$baseUrl$trimmed"
            else -> "$baseUrl/$trimmed"
        }
    }
}
