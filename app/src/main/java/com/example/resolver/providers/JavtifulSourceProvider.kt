package com.example.resolver.providers

import android.util.Log
import com.example.metadata.JavIdParser
import com.example.model.MediaIdentity
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Javtiful Stream Provider.
 * Discovers and streams direct MP4 fast-stream media from Javtiful.
 */
class JavtifulSourceProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : SourceProvider {

    companion object {
        private const val TAG = "JavtifulSourceProvider"
        private const val BASE_URL = "https://javtiful.com"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    override val id: String = "javtiful"
    override val displayName: String = "Javtiful Direct Stream"
    override val isEnabled: Boolean = true
    override val priority: Int = 92

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val candidates = mutableListOf<SourceCandidate>()
        val rawCode = identity.rawQueryOrUrl.ifBlank { identity.title }
        val slug = rawCode.removePrefix("javtiful_").trim()
        val numId = Regex("""\b(\d+)\b""").find(slug)?.groupValues?.get(1)
        val javCode = JavIdParser.parse(slug) ?: JavIdParser.parse(identity.title) ?: slug

        if (slug.isBlank() && javCode.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            var extractedStreamUrl: String? = null
            var extractedFormat = "mp4"
            var pageTitle = "[$javCode] Javtiful Stream"

            // 1. Try direct video and embed URLs if slug or numeric ID is available
            val directUrls = mutableListOf<String>()
            if (numId != null) {
                val formattedSlug = slug.replace('_', '/')
                directUrls.add("$BASE_URL/video/$formattedSlug")
                directUrls.add("$BASE_URL/video/$numId")
                directUrls.add("$BASE_URL/embed/$numId")
            } else if (slug.isNotBlank() && !slug.contains(" ")) {
                directUrls.add("$BASE_URL/video/$slug")
            }

            for (directUrl in directUrls) {
                try {
                    val req = Request.Builder()
                        .url(directUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "$BASE_URL/")
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val doc = Jsoup.parse(html, BASE_URL)
                        val titleFound = doc.select("h1, .video-title, .title, title").firstOrNull()?.text()
                        if (!titleFound.isNullOrBlank()) {
                            pageTitle = titleFound
                        }

                        val fastStream = Regex("""(https://fast-stream\.jav\.si/p/[a-zA-Z0-9_-]+)""").find(html)?.groupValues?.get(1)
                            ?: Regex("""(https://[^"'\s<>]+\.mp4[^"'\s<>]*)""").find(html)?.groupValues?.get(1)
                            ?: Regex("""(https://[^"'\s<>]+\.m3u8[^"'\s<>]*)""").find(html)?.groupValues?.get(1)

                        if (!fastStream.isNullOrBlank()) {
                            extractedStreamUrl = fastStream
                            if (fastStream.contains(".m3u8")) extractedFormat = "m3u8"
                            break
                        }

                        // Check if video page has embed link
                        val embedId = Regex("""/embed/(\d+)""").find(html)?.groupValues?.get(1)
                        if (!embedId.isNullOrBlank() && !directUrl.contains("/embed/")) {
                            val embedReq = Request.Builder()
                                .url("$BASE_URL/embed/$embedId")
                                .header("User-Agent", USER_AGENT)
                                .header("Referer", directUrl)
                                .build()
                            val embedResp = client.newCall(embedReq).execute()
                            if (embedResp.isSuccessful) {
                                val embedHtml = embedResp.body?.string() ?: ""
                                val embedStream = Regex("""(https://fast-stream\.jav\.si/p/[a-zA-Z0-9_-]+)""").find(embedHtml)?.groupValues?.get(1)
                                    ?: Regex("""(https://[^"'\s<>]+\.mp4[^"'\s<>]*)""").find(embedHtml)?.groupValues?.get(1)
                                if (!embedStream.isNullOrBlank()) {
                                    extractedStreamUrl = embedStream
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Direct lookup error for $directUrl: ${e.message}")
                }
            }

            // 2. Search fallback if not found directly
            if (extractedStreamUrl == null && javCode.isNotBlank()) {
                val searchUrl = "$BASE_URL/search?q=$javCode"
                val req = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "$BASE_URL/")
                    .build()

                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val doc = Jsoup.parse(html, BASE_URL)

                    val videoLinks = doc.select("a[href*=\"/video/\"]")
                    val targetHref = videoLinks.firstOrNull { elem ->
                        val href = elem.attr("href")
                        val text = elem.text()
                        href.contains(javCode, ignoreCase = true) || text.contains(javCode, ignoreCase = true)
                    }?.attr("href") ?: videoLinks.firstOrNull()?.attr("href")

                    if (!targetHref.isNullOrBlank()) {
                        val vidId = Regex("""/video/(\d+)""").find(targetHref)?.groupValues?.get(1)
                        if (!vidId.isNullOrBlank()) {
                            val embedUrl = "$BASE_URL/embed/$vidId"
                            val embedReq = Request.Builder()
                                .url(embedUrl)
                                .header("User-Agent", USER_AGENT)
                                .header("Referer", "$BASE_URL$targetHref")
                                .build()

                            val embedResp = client.newCall(embedReq).execute()
                            if (embedResp.isSuccessful) {
                                val embedHtml = embedResp.body?.string() ?: ""
                                val fastStreamUrl = Regex("""(https://fast-stream\.jav\.si/p/[a-zA-Z0-9_-]+)""")
                                    .find(embedHtml)?.groupValues?.get(1)

                                if (!fastStreamUrl.isNullOrBlank()) {
                                    extractedStreamUrl = fastStreamUrl
                                }
                            }
                        }
                    }
                }
            }

            if (!extractedStreamUrl.isNullOrBlank()) {
                val isHls = extractedFormat == "m3u8" || extractedStreamUrl.contains(".m3u8")
                candidates.add(
                    SourceCandidate(
                        id = "javtiful_${numId ?: slug}",
                        providerId = id,
                        providerName = "Javtiful",
                        serverName = "Javtiful Fast Stream (720p)",
                        type = if (isHls) SourceStreamType.HLS else SourceStreamType.DIRECT,
                        title = pageTitle,
                        urlOrMagnet = extractedStreamUrl,
                        quality = "720p HD",
                        qualityScore = 720,
                        format = if (isHls) "m3u8" else "mp4",
                        headers = mapOf(
                            "Referer" to "$BASE_URL/",
                            "Origin" to BASE_URL,
                            "User-Agent" to USER_AGENT
                        ),
                        healthScore = 95,
                        capabilities = PlaybackCapabilities(
                            supportsSeeking = true,
                            supportsTrackSelection = isHls
                        )
                    )
                )
                emit(ArrayList(candidates))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Javtiful extraction failed for $javCode: ${e.message}")
        }

        emit(candidates)
    }.flowOn(Dispatchers.IO)
}
