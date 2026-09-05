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
        val javCode = JavIdParser.parse(rawCode) ?: JavIdParser.parse(identity.title) ?: rawCode.trim()

        if (javCode.isBlank() || javCode.length < 3) {
            emit(emptyList())
            return@flow
        }

        try {
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

                // Look for links matching /video/12345/slug
                val videoLinks = doc.select("a[href*=\"/video/\"]")
                val targetHref = videoLinks.firstOrNull { elem ->
                    val href = elem.attr("href")
                    val text = elem.text()
                    href.contains(javCode, ignoreCase = true) || text.contains(javCode, ignoreCase = true)
                }?.attr("href") ?: videoLinks.firstOrNull()?.attr("href")

                if (!targetHref.isNullOrBlank()) {
                    // Extract ID e.g. /video/113237/heyzo-3931 -> 113237
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
                            val fastStreamUrl = Regex("""(https://fast-stream\.jav\.si/p/[a-zA-Z0-9-]+)""")
                                .find(embedHtml)?.groupValues?.get(1)

                            if (!fastStreamUrl.isNullOrBlank()) {
                                candidates.add(
                                    SourceCandidate(
                                        id = "javtiful_$vidId",
                                        providerId = id,
                                        providerName = "Javtiful",
                                        serverName = "Javtiful Fast Stream (720p)",
                                        type = SourceStreamType.DIRECT,
                                        title = "[$javCode] Javtiful Stream",
                                        urlOrMagnet = fastStreamUrl,
                                        quality = "720p HD",
                                        qualityScore = 720,
                                        format = "mp4",
                                        headers = mapOf(
                                            "Referer" to embedUrl,
                                            "Origin" to BASE_URL,
                                            "User-Agent" to USER_AGENT
                                        ),
                                        healthScore = 93,
                                        capabilities = PlaybackCapabilities(
                                            supportsSeeking = true,
                                            supportsTrackSelection = false
                                        )
                                    )
                                )
                                emit(ArrayList(candidates))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Javtiful extraction failed for $javCode: ${e.message}")
        }

        emit(candidates)
    }.flowOn(Dispatchers.IO)
}
