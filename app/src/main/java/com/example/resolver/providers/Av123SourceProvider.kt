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
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * 123AV & JAVPlayer HLS Stream Provider.
 * Extracts high-speed m3u8 HLS streams from 123AV via javplayer.cc stream resolution.
 */
class Av123SourceProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : SourceProvider {

    companion object {
        private const val TAG = "Av123SourceProvider"
        private const val BASE_URL = "https://123av.com"
        private const val JAVPLAYER_BASE = "https://javplayer.cc"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    override val id: String = "123av"
    override val displayName: String = "123AV & JAVPlayer CDN"
    override val isEnabled: Boolean = true
    override val priority: Int = 94

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val candidates = mutableListOf<SourceCandidate>()
        val rawCode = identity.rawQueryOrUrl.ifBlank { identity.title }
        val javCode = JavIdParser.parse(rawCode) ?: JavIdParser.parse(identity.title) ?: rawCode.trim()

        if (javCode.isBlank() || javCode.length < 3) {
            emit(emptyList())
            return@flow
        }

        try {
            val searchUrl = "$BASE_URL/en/search?keyword=$javCode"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/en")
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val doc = Jsoup.parse(html, BASE_URL)

                // Match first or most relevant video card link
                val videoLinks = doc.select("a.card__cover, a.card__link, a[href^=\"/en/v/\"]")
                val targetHref = videoLinks.firstOrNull { elem ->
                    val href = elem.attr("href")
                    val text = elem.text()
                    href.contains(javCode, ignoreCase = true) || text.contains(javCode, ignoreCase = true)
                }?.attr("abs:href") ?: videoLinks.firstOrNull()?.attr("abs:href")

                if (!targetHref.isNullOrBlank()) {
                    val detailReq = Request.Builder()
                        .url(targetHref)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", searchUrl)
                        .build()

                    val detailResp = client.newCall(detailReq).execute()
                    if (detailResp.isSuccessful) {
                        val detailHtml = detailResp.body?.string() ?: ""
                        val pageDoc = Jsoup.parse(detailHtml, BASE_URL)
                        val pageTitle = pageDoc.select("h1, .vdetail__title, title").firstOrNull()?.text() ?: "[$javCode] 123AV Stream"

                        // Extract JAVPlayer embed ID: https://javplayer.cc/e/{id} or iframe
                        val embedId = Regex("""https?://javplayer\.cc/e/([a-zA-Z0-9_-]+)""")
                            .find(detailHtml)?.groupValues?.get(1)

                        if (!embedId.isNullOrBlank()) {
                            val streamApiUrl = "$JAVPLAYER_BASE/stream?id=$embedId"
                            val streamReq = Request.Builder()
                                .url(streamApiUrl)
                                .header("User-Agent", USER_AGENT)
                                .header("Referer", "$JAVPLAYER_BASE/e/$embedId")
                                .build()

                            val streamResp = client.newCall(streamReq).execute()
                            if (streamResp.isSuccessful) {
                                val streamJson = JSONObject(streamResp.body?.string() ?: "")
                                if (streamJson.optString("status") == "ok") {
                                    val mediaObj = streamJson.optJSONObject("media")
                                    val m3u8Url = mediaObj?.optString("stream")
                                    val vttUrl = mediaObj?.optString("vtt")?.takeIf { it.isNotBlank() }

                                    if (!m3u8Url.isNullOrBlank()) {
                                        candidates.add(
                                            SourceCandidate(
                                                id = "123av_$embedId",
                                                providerId = id,
                                                providerName = "123AV",
                                                serverName = "JAVPlayer Fast CDN (1080p)",
                                                type = SourceStreamType.HLS,
                                                title = pageTitle,
                                                urlOrMagnet = m3u8Url,
                                                quality = "1080p FHD",
                                                qualityScore = 1080,
                                                format = "m3u8",
                                                headers = mapOf(
                                                    "Referer" to "$JAVPLAYER_BASE/",
                                                    "User-Agent" to USER_AGENT
                                                ),
                                                healthScore = 96,
                                                capabilities = PlaybackCapabilities(
                                                    supportsSeeking = true,
                                                    supportsTrackSelection = true
                                                )
                                            )
                                        )
                                        emit(ArrayList(candidates))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "123AV extraction failed for $javCode: ${e.message}")
        }

        emit(candidates)
    }.flowOn(Dispatchers.IO)
}
