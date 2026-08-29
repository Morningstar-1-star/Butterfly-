package com.example.metadata.providers

import android.util.Log
import com.example.metadata.JavActor
import com.example.metadata.JavIdParser
import com.example.metadata.JavMetadata
import com.example.metadata.MetadataProvider
import com.example.util.AppConfig
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
 * Real Javinizer-Go (v1.5.1+) REST API Metadata Provider.
 * Connects directly to a running Javinizer-Go service instance over HTTP REST API.
 * Supports metadata search, JAV ID lookup, actress biography/artwork resolution, and health diagnostics.
 */
class JavinizerGoMetadataProvider : MetadataProvider {

    companion object {
        private const val TAG = "JavinizerGoProvider"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        data class JavinizerHealthResult(
            val isSuccess: Boolean,
            val latencyMs: Long,
            val serverVersion: String?,
            val message: String
        )
    }

    override val id: String = "javinizer_go"
    override val name: String = "Javinizer-Go (REST Service)"
    override val priority: Int = 200 // Highest priority when enabled

    override val isEnabled: Boolean
        get() = AppConfig.isJavinizerEnabled()

    private fun buildClient(timeoutSec: Int = AppConfig.getJavinizerTimeoutSeconds()): OkHttpClient {
        val validTimeout = if (timeoutSec in 2..120) timeoutSec.toLong() else 15L
        return OkHttpClient.Builder()
            .connectTimeout(validTimeout, TimeUnit.SECONDS)
            .readTimeout(validTimeout, TimeUnit.SECONDS)
            .writeTimeout(validTimeout, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private fun getBaseUrl(): String {
        val url = AppConfig.getJavinizerApiUrl().trim().trimEnd('/')
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "http://$url"
        }
    }

    /**
     * Resolves complete metadata for a JAV ID by querying the Javinizer-Go REST API.
     */
    override suspend fun getMetadata(javCode: String): JavMetadata? = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext null

        val parsedCode = JavIdParser.parse(javCode) ?: javCode.trim()
        if (parsedCode.isBlank()) return@withContext null

        val baseUrl = getBaseUrl()
        val client = buildClient()
        val encodedId = URLEncoder.encode(parsedCode, "UTF-8")

        // Try primary and secondary REST endpoints supported by Javinizer-Go v1.5.1
        val candidateEndpoints = listOf(
            "$baseUrl/api/v1/movie/$encodedId",
            "$baseUrl/api/v1/scrape/movie/$encodedId",
            "$baseUrl/api/v1/scrape/$encodedId",
            "$baseUrl/api/v1/movies/$encodedId",
            "$baseUrl/api/movie/$encodedId"
        )

        for (endpointUrl in candidateEndpoints) {
            try {
                val request = Request.Builder()
                    .url(endpointUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrBlank()) {
                        val parsedMeta = parseJavinizerMovieJson(responseBody, parsedCode, baseUrl)
                        if (parsedMeta != null && parsedMeta.title.isNotBlank()) {
                            Log.i(TAG, "Successfully resolved [$parsedCode] via Javinizer-Go ($endpointUrl)")
                            return@withContext parsedMeta
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Endpoint $endpointUrl failed: ${e.message}")
            }
        }

        // Also try POST scrape endpoint if GET returned nothing
        try {
            val postUrl = "$baseUrl/api/v1/scrape"
            val jsonPayload = JSONObject().apply {
                put("id", parsedCode)
                put("query", parsedCode)
            }.toString()

            val postRequest = Request.Builder()
                .url(postUrl)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val postResponse = client.newCall(postRequest).execute()
            if (postResponse.isSuccessful) {
                val postBody = postResponse.body?.string()
                if (!postBody.isNullOrBlank()) {
                    val parsedMeta = parseJavinizerMovieJson(postBody, parsedCode, baseUrl)
                    if (parsedMeta != null && parsedMeta.title.isNotBlank()) {
                        Log.i(TAG, "Resolved [$parsedCode] via Javinizer-Go POST scrape")
                        return@withContext parsedMeta
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "POST scrape failed for $parsedCode: ${e.message}")
        }

        null
    }

    /**
     * Searches metadata entries across Javinizer-Go scrapers.
     */
    override suspend fun search(query: String): List<JavMetadata> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext emptyList()

        val parsedCode = JavIdParser.parse(query)
        if (parsedCode != null) {
            val direct = getMetadata(parsedCode)
            if (direct != null) return@withContext listOf(direct)
        }

        val baseUrl = getBaseUrl()
        val client = buildClient()
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")

        val searchEndpoints = listOf(
            "$baseUrl/api/v1/search?q=$encodedQuery",
            "$baseUrl/api/v1/scrape/search?q=$encodedQuery",
            "$baseUrl/api/v1/movies?query=$encodedQuery",
            "$baseUrl/api/search?q=$encodedQuery"
        )

        for (searchUrl in searchEndpoints) {
            try {
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Butterfly-Android/1.0 (Javinizer-Go Client)")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val list = parseJavinizerSearchJson(body, baseUrl)
                    if (list.isNotEmpty()) {
                        Log.i(TAG, "Found ${list.size} search results from Javinizer-Go ($searchUrl)")
                        return@withContext list
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Search failed on $searchUrl: ${e.message}")
            }
        }

        emptyList()
    }

    /**
     * Resolves actress metadata and portrait via Javinizer-Go actress scraper endpoints.
     */
    suspend fun getActressMetadata(name: String): JavActor? = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext null
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withContext null

        val baseUrl = getBaseUrl()
        val client = buildClient()
        val encoded = URLEncoder.encode(cleanName, "UTF-8")

        val endpoints = listOf(
            "$baseUrl/api/v1/actress/$encoded",
            "$baseUrl/api/v1/scrape/actress/$encoded",
            "$baseUrl/api/actress/$encoded"
        )

        for (ep in endpoints) {
            try {
                val req = Request.Builder()
                    .url(ep)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: continue
                    val actor = parseActressJson(body, cleanName, baseUrl)
                    if (actor != null) return@withContext actor
                }
            } catch (e: Exception) {
                // Ignore fallback to next
            }
        }
        null
    }

    /**
     * Tests connectivity, health status, version, and latency of the Javinizer-Go instance.
     */
    suspend fun testHealth(
        customBaseUrl: String? = null,
        customTimeoutSec: Int? = null
    ): JavinizerHealthResult = withContext(Dispatchers.IO) {
        val targetBaseUrl = (customBaseUrl ?: getBaseUrl()).trim().trimEnd('/')
        val finalUrl = if (targetBaseUrl.startsWith("http://") || targetBaseUrl.startsWith("https://")) {
            targetBaseUrl
        } else {
            "http://$targetBaseUrl"
        }

        val client = buildClient(customTimeoutSec ?: 8)
        val startTime = System.currentTimeMillis()

        val healthEndpoints = listOf(
            "$finalUrl/api/v1/health",
            "$finalUrl/health",
            "$finalUrl/api/health",
            "$finalUrl/api/v1/status",
            "$finalUrl/version"
        )

        var lastError: Exception? = null
        for (url in healthEndpoints) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Butterfly-Android/1.0 (Health Check)")
                    .get()
                    .build()

                val resp = client.newCall(req).execute()
                val latency = System.currentTimeMillis() - startTime
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    var versionStr: String? = null
                    var statusStr = "Online"

                    if (body.startsWith("{")) {
                        try {
                            val json = JSONObject(body)
                            versionStr = json.optString("version", null)
                                ?: json.optString("app_version", null)
                                ?: json.optJSONObject("data")?.optString("version", null)
                            statusStr = json.optString("status", "Online")
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }

                    return@withContext JavinizerHealthResult(
                        isSuccess = true,
                        latencyMs = latency,
                        serverVersion = versionStr ?: "v1.5.1+",
                        message = "Connected ($statusStr • ${latency}ms)"
                    )
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        val latency = System.currentTimeMillis() - startTime
        val errorMsg = lastError?.localizedMessage ?: "Connection refused or timed out"
        JavinizerHealthResult(
            isSuccess = false,
            latencyMs = latency,
            serverVersion = null,
            message = "Unreachable: $errorMsg"
        )
    }

    /**
     * Normalizes raw JSON response from Javinizer-Go into Butterfly's JavMetadata data model.
     */
    private fun parseJavinizerMovieJson(jsonString: String, fallbackCode: String, baseUrl: String): JavMetadata? {
        try {
            val root = JSONObject(jsonString)
            val movieObj = root.optJSONObject("data")
                ?: root.optJSONObject("result")
                ?: root.optJSONObject("movie")
                ?: root

            val code = movieObj.optString("id", "").ifBlank {
                movieObj.optString("code", "").ifBlank {
                    movieObj.optString("dvd_id", "").ifBlank {
                        movieObj.optString("number", fallbackCode)
                    }
                }
            }

            val title = movieObj.optString("title", "").ifBlank {
                movieObj.optString("name", code)
            }

            val originalTitle = movieObj.optString("original_title", null)?.ifBlank { null }
            val releaseDate = movieObj.optString("release_date", null)?.ifBlank {
                movieObj.optString("premiered", null)?.ifBlank { null }
            }
            val year = releaseDate?.take(4) ?: movieObj.optString("year", null)?.ifBlank { null }

            val durationMinutes = movieObj.optInt("runtime", 0).takeIf { it > 0 }
                ?: movieObj.optInt("duration", 0).takeIf { it > 0 }
                ?: movieObj.optInt("length", 0).takeIf { it > 0 }

            val director = movieObj.optString("director", null)?.ifBlank {
                val dirArr = movieObj.optJSONArray("directors")
                if (dirArr != null && dirArr.length() > 0) {
                    dirArr.optString(0)
                } else null
            }

            val studio = movieObj.optString("studio", null)?.ifBlank {
                movieObj.optString("maker", null)?.ifBlank { null }
            }

            val label = movieObj.optString("label", null)?.ifBlank {
                movieObj.optString("publisher", null)?.ifBlank { null }
            }

            val series = movieObj.optString("series", null)?.ifBlank { null }

            // Genres
            val genresList = mutableListOf<String>()
            val genresArr = movieObj.optJSONArray("genres") ?: movieObj.optJSONArray("tags")
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val g = genresArr.optString(i).trim()
                    if (g.isNotBlank() && !genresList.contains(g)) {
                        genresList.add(g)
                    }
                }
            }

            // Actresses / Cast
            val castList = mutableListOf<JavActor>()
            val actressesArr = movieObj.optJSONArray("actresses")
                ?: movieObj.optJSONArray("actors")
                ?: movieObj.optJSONArray("cast")
                ?: movieObj.optJSONArray("stars")

            if (actressesArr != null) {
                for (i in 0 until actressesArr.length()) {
                    val item = actressesArr.opt(i)
                    if (item is JSONObject) {
                        val name = item.optString("name", "").ifBlank {
                            item.optString("actress_name", "")
                        }
                        if (name.isNotBlank()) {
                            val origName = item.optString("japanese_name", null)?.ifBlank {
                                item.optString("original_name", null)?.ifBlank { null }
                            }
                            val romaji = item.optString("romaji_name", null)?.ifBlank { null }
                            val avatar = item.optString("image_url", null)?.ifBlank {
                                item.optString("avatar_url", null)?.ifBlank {
                                    item.optString("thumb_url", null)?.ifBlank { null }
                                }
                            }
                            val birthday = item.optString("birthday", null)?.ifBlank { null }
                            val height = item.optInt("height_cm", 0).takeIf { it > 0 }
                                ?: item.optInt("height", 0).takeIf { it > 0 }
                            val cup = item.optString("cup_size", null)?.ifBlank {
                                item.optString("cup", null)?.ifBlank { null }
                            }
                            val bust = item.optInt("bust_cm", 0).takeIf { it > 0 }
                                ?: item.optInt("bust", 0).takeIf { it > 0 }
                            val waist = item.optInt("waist_cm", 0).takeIf { it > 0 }
                                ?: item.optInt("waist", 0).takeIf { it > 0 }
                            val hip = item.optInt("hip_cm", 0).takeIf { it > 0 }
                                ?: item.optInt("hip", 0).takeIf { it > 0 }
                            val blood = item.optString("blood_type", null)?.ifBlank { null }
                            val birthplace = item.optString("birthplace", null)?.ifBlank { null }

                            val aliases = mutableListOf<String>()
                            val aliasArr = item.optJSONArray("aliases")
                            if (aliasArr != null) {
                                for (a in 0 until aliasArr.length()) {
                                    val alias = aliasArr.optString(a).trim()
                                    if (alias.isNotBlank()) aliases.add(alias)
                                }
                            }

                            castList.add(
                                JavActor(
                                    name = name,
                                    originalName = origName,
                                    romajiName = romaji,
                                    avatarUrl = normalizeUrl(avatar, baseUrl),
                                    birthday = birthday,
                                    heightCm = height,
                                    cupSize = cup,
                                    bustCm = bust,
                                    waistCm = waist,
                                    hipCm = hip,
                                    bloodType = blood,
                                    birthplace = birthplace,
                                    aliases = aliases
                                )
                            )
                        }
                    } else if (item is String && item.isNotBlank()) {
                        castList.add(JavActor(name = item.trim()))
                    }
                }
            }

            // Cover and Artwork
            val coverUrl = movieObj.optString("cover_url", null)?.ifBlank {
                movieObj.optString("poster_url", null)?.ifBlank {
                    movieObj.optString("thumb_url", null)?.ifBlank {
                        movieObj.optString("fanart_url", null)?.ifBlank { null }
                    }
                }
            }

            val thumbUrl = movieObj.optString("thumb_url", null)?.ifBlank { coverUrl }

            // Preview screenshots gallery
            val screenshotsList = mutableListOf<String>()
            val sampleArr = movieObj.optJSONArray("sample_images")
                ?: movieObj.optJSONArray("screenshots")
                ?: movieObj.optJSONArray("preview_images")
                ?: movieObj.optJSONArray("gallery")

            if (sampleArr != null) {
                for (i in 0 until sampleArr.length()) {
                    val sUrl = sampleArr.optString(i).trim()
                    if (sUrl.isNotBlank()) {
                        screenshotsList.add(normalizeUrl(sUrl, baseUrl) ?: sUrl)
                    }
                }
            }

            // Sample Trailer Video
            val sampleVideo = movieObj.optString("sample_video_url", null)?.ifBlank {
                movieObj.optString("trailer_url", null)?.ifBlank {
                    movieObj.optString("preview_video_url", null)?.ifBlank { null }
                }
            }

            // Rating
            val ratingScore = movieObj.optDouble("score", 0.0).takeIf { it > 0.0 }
                ?: movieObj.optDouble("rating", 0.0).takeIf { it > 0.0 }
                ?: movieObj.optDouble("user_rating", 0.0).takeIf { it > 0.0 }

            val normalizedRating = ratingScore?.let {
                if (it > 5.0) (it / 2.0).toFloat() else it.toFloat()
            }

            val plot = movieObj.optString("description", null)?.ifBlank {
                movieObj.optString("plot", null)?.ifBlank {
                    movieObj.optString("outline", null)?.ifBlank { null }
                }
            }

            val detailUrl = movieObj.optString("url", null)?.ifBlank {
                "$baseUrl/api/v1/movie/$code"
            }

            return JavMetadata(
                id = code,
                code = code,
                title = title,
                originalTitle = originalTitle,
                releaseDate = releaseDate,
                year = year,
                durationMinutes = durationMinutes,
                director = director,
                studio = studio,
                label = label,
                series = series,
                genres = genresList,
                coverUrl = normalizeUrl(coverUrl, baseUrl),
                thumbUrl = normalizeUrl(thumbUrl, baseUrl),
                previewImages = screenshotsList,
                sampleVideoUrl = normalizeUrl(sampleVideo, baseUrl),
                cast = castList,
                rating = normalizedRating,
                providerSource = name,
                detailUrl = detailUrl,
                plotOverview = plot
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing Javinizer movie JSON: ${e.message}")
            return null
        }
    }

    /**
     * Parses search response list from Javinizer-Go.
     */
    private fun parseJavinizerSearchJson(jsonString: String, baseUrl: String): List<JavMetadata> {
        val results = mutableListOf<JavMetadata>()
        try {
            val root = if (jsonString.startsWith("[")) {
                JSONArray(jsonString)
            } else {
                val obj = JSONObject(jsonString)
                obj.optJSONArray("data")
                    ?: obj.optJSONArray("results")
                    ?: obj.optJSONArray("movies")
                    ?: JSONArray()
            }

            for (i in 0 until root.length()) {
                val item = root.optJSONObject(i) ?: continue
                val singleJson = item.toString()
                val parsed = parseJavinizerMovieJson(singleJson, "JAV-$i", baseUrl)
                if (parsed != null && parsed.title.isNotBlank()) {
                    results.add(parsed)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Javinizer search JSON: ${e.message}")
        }
        return results
    }

    /**
     * Parses actress details object from Javinizer-Go actress endpoint.
     */
    private fun parseActressJson(jsonString: String, fallbackName: String, baseUrl: String): JavActor? {
        try {
            val root = JSONObject(jsonString)
            val actressObj = root.optJSONObject("data")
                ?: root.optJSONObject("actress")
                ?: root

            val name = actressObj.optString("name", fallbackName).ifBlank { fallbackName }
            val origName = actressObj.optString("japanese_name", null)?.ifBlank {
                actressObj.optString("original_name", null)?.ifBlank { null }
            }
            val romaji = actressObj.optString("romaji_name", null)?.ifBlank { null }
            val avatar = actressObj.optString("image_url", null)?.ifBlank {
                actressObj.optString("avatar_url", null)?.ifBlank {
                    actressObj.optString("thumb_url", null)?.ifBlank { null }
                }
            }
            val birthday = actressObj.optString("birthday", null)?.ifBlank { null }
            val height = actressObj.optInt("height_cm", 0).takeIf { it > 0 }
                ?: actressObj.optInt("height", 0).takeIf { it > 0 }
            val cup = actressObj.optString("cup_size", null)?.ifBlank {
                actressObj.optString("cup", null)?.ifBlank { null }
            }
            val bust = actressObj.optInt("bust_cm", 0).takeIf { it > 0 }
            val waist = actressObj.optInt("waist_cm", 0).takeIf { it > 0 }
            val hip = actressObj.optInt("hip_cm", 0).takeIf { it > 0 }
            val blood = actressObj.optString("blood_type", null)?.ifBlank { null }
            val birthplace = actressObj.optString("birthplace", null)?.ifBlank { null }

            val aliases = mutableListOf<String>()
            val aliasArr = actressObj.optJSONArray("aliases")
            if (aliasArr != null) {
                for (a in 0 until aliasArr.length()) {
                    val alias = aliasArr.optString(a).trim()
                    if (alias.isNotBlank()) aliases.add(alias)
                }
            }

            return JavActor(
                name = name,
                originalName = origName,
                romajiName = romaji,
                avatarUrl = normalizeUrl(avatar, baseUrl),
                birthday = birthday,
                heightCm = height,
                cupSize = cup,
                bustCm = bust,
                waistCm = waist,
                hipCm = hip,
                bloodType = blood,
                birthplace = birthplace,
                aliases = aliases
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing actress JSON: ${e.message}")
            return null
        }
    }

    private fun normalizeUrl(url: String?, baseUrl: String): String? {
        if (url.isNullOrBlank() || url == "null") return null
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else if (url.startsWith("/")) {
            "$baseUrl$url"
        } else {
            "$baseUrl/$url"
        }
    }
}
