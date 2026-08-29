package com.example.metadata.providers

import android.util.Log
import com.example.metadata.JavActor
import com.example.metadata.JavIdParser
import com.example.metadata.JavMetadata
import com.example.metadata.MetadataProvider
import com.example.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * JAVapi Metadata Provider (Adapted from ANDonekey/javapi).
 * Multi-source JAV lookup and JavDB / DMM aggregation engine with built-in caching.
 * Normalizes movie codes, multi-language titles, actresses, high-res jackets, and sample previews.
 */
class JavapiMetadataProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : MetadataProvider {

    companion object {
        private const val TAG = "JavapiMetadataProvider"
        private const val DEFAULT_PUBLIC_ENDPOINT = "https://javapi.vercel.app"
    }

    override val id: String = "javapi"
    override val name: String = "JAVapi Engine"
    override val priority: Int = 180

    override val isEnabled: Boolean
        get() = AppConfig.isJavapiEnabled()

    private val localCache = ConcurrentHashMap<String, JavMetadata>()

    override suspend fun getMetadata(javCode: String): JavMetadata? = withContext(Dispatchers.IO) {
        val parsedCode = JavIdParser.parse(javCode) ?: javCode.trim()
        if (parsedCode.isBlank()) return@withContext null

        localCache[parsedCode]?.let { return@withContext it }

        val baseUrl = AppConfig.getJavapiServerUrl().ifBlank { DEFAULT_PUBLIC_ENDPOINT }.trimEnd('/')
        val encodedCode = URLEncoder.encode(parsedCode, StandardCharsets.UTF_8.name())

        // 1. Query JAVapi REST service
        val endpoint = "$baseUrl/api/v1/movie/$encodedCode"
        try {
            val req = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Butterfly/1.0 JavapiClient")
                .header("Accept", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val dataObj = if (json.has("data")) json.optJSONObject("data") ?: json else json

                val code = dataObj.optString("id", dataObj.optString("code", parsedCode))
                val title = dataObj.optString("title", parsedCode)
                val originalTitle = dataObj.optString("original_title", dataObj.optString("title_ja", title))
                val releaseDate = dataObj.optString("release_date", dataObj.optString("date", null))
                val year = releaseDate?.take(4) ?: dataObj.optString("year", null)
                val duration = dataObj.optInt("duration", dataObj.optInt("runtime", 0)).takeIf { it > 0 }
                val director = dataObj.optString("director", null)?.takeIf { it.isNotBlank() }
                val studio = dataObj.optString("maker", dataObj.optString("studio", null))?.takeIf { it.isNotBlank() }
                val label = dataObj.optString("label", null)?.takeIf { it.isNotBlank() }
                val series = dataObj.optString("series", null)?.takeIf { it.isNotBlank() }
                val coverUrl = dataObj.optString("cover", dataObj.optString("poster", null))?.takeIf { it.isNotBlank() }
                val sampleVideoUrl = dataObj.optString("preview_video", dataObj.optString("trailer", null))?.takeIf { it.isNotBlank() }
                val rating = dataObj.optDouble("score", dataObj.optDouble("rating", 0.0)).toFloat().takeIf { it > 0f }
                val plotOverview = dataObj.optString("description", dataObj.optString("plot", null))?.takeIf { it.isNotBlank() }

                // Parse genres
                val genresList = mutableListOf<String>()
                val genresArr = dataObj.optJSONArray("genres") ?: dataObj.optJSONArray("tags")
                if (genresArr != null) {
                    for (i in 0 until genresArr.length()) {
                        val g = genresArr.optString(i)
                        if (g.isNotBlank()) genresList.add(g)
                    }
                }

                // Parse preview screenshots
                val previewList = mutableListOf<String>()
                val previewsArr = dataObj.optJSONArray("preview_images") ?: dataObj.optJSONArray("samples")
                if (previewsArr != null) {
                    for (i in 0 until previewsArr.length()) {
                        val p = previewsArr.optString(i)
                        if (p.isNotBlank()) previewList.add(p)
                    }
                }

                // Parse actors
                val castList = mutableListOf<JavActor>()
                val actorsArr = dataObj.optJSONArray("actors") ?: dataObj.optJSONArray("actresses")
                if (actorsArr != null) {
                    for (i in 0 until actorsArr.length()) {
                        val actorObj = actorsArr.optJSONObject(i)
                        if (actorObj != null) {
                            val aName = actorObj.optString("name", "")
                            if (aName.isNotBlank()) {
                                castList.add(
                                    JavActor(
                                        name = aName,
                                        originalName = actorObj.optString("name_ja", null),
                                        romajiName = actorObj.optString("romaji", null),
                                        avatarUrl = actorObj.optString("avatar", actorObj.optString("photo", null)),
                                        birthday = actorObj.optString("birthday", null),
                                        cupSize = actorObj.optString("cup", null)
                                    )
                                )
                            }
                        } else {
                            val aName = actorsArr.optString(i)
                            if (aName.isNotBlank()) {
                                castList.add(JavActor(name = aName))
                            }
                        }
                    }
                }

                val metadata = JavMetadata(
                    id = code,
                    code = code,
                    title = title,
                    originalTitle = originalTitle,
                    releaseDate = releaseDate,
                    year = year,
                    durationMinutes = duration,
                    director = director,
                    studio = studio,
                    label = label,
                    series = series,
                    genres = genresList,
                    coverUrl = coverUrl,
                    thumbUrl = coverUrl,
                    previewImages = previewList,
                    sampleVideoUrl = sampleVideoUrl,
                    cast = castList,
                    rating = rating,
                    providerSource = name,
                    detailUrl = endpoint,
                    plotOverview = plotOverview
                )

                localCache[parsedCode] = metadata
                return@withContext metadata
            }
        } catch (e: Exception) {
            Log.w(TAG, "JAVapi request failed for $parsedCode: ${e.message}")
        }

        null
    }

    override suspend fun search(query: String): List<JavMetadata> = withContext(Dispatchers.IO) {
        val parsed = JavIdParser.parse(query)
        if (parsed != null) {
            val direct = getMetadata(parsed)
            if (direct != null) return@withContext listOf(direct)
        }

        val baseUrl = AppConfig.getJavapiServerUrl().ifBlank { DEFAULT_PUBLIC_ENDPOINT }.trimEnd('/')
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val endpoint = "$baseUrl/api/v1/search?q=$encodedQuery"

        try {
            val req = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Butterfly/1.0 JavapiClient")
                .header("Accept", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(bodyStr)
                val itemsArr = json.optJSONArray("results") ?: json.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<JavMetadata>()

                for (i in 0 until itemsArr.length()) {
                    val item = itemsArr.optJSONObject(i) ?: continue
                    val code = item.optString("id", item.optString("code", ""))
                    val title = item.optString("title", code)
                    val cover = item.optString("cover", item.optString("poster", null))
                    val date = item.optString("date", null)
                    if (code.isNotBlank()) {
                        list.add(
                            JavMetadata(
                                id = code,
                                code = code,
                                title = title,
                                releaseDate = date,
                                year = date?.take(4),
                                coverUrl = cover,
                                thumbUrl = cover,
                                providerSource = name
                            )
                        )
                    }
                }
                return@withContext list
            }
        } catch (e: Exception) {
            Log.w(TAG, "JAVapi search failed: ${e.message}")
        }

        emptyList()
    }
}
