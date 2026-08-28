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
 * JavPy Stream Provider (Adapted from TheodoreKrypton/JavPy).
 * Multi-host streamer resolving sources across JavMost, Netflav, and high-speed mirrors.
 */
class JavPySourceProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SourceProvider {

    companion object {
        private const val TAG = "JavPyProvider"
        private const val NETFLAV_API_URL = "https://netflav.com/api/video/get"
    }

    override val id: String = "javpy"
    override val displayName: String = "JavPy Multi-Source"
    override val isEnabled: Boolean = true
    override val priority: Int = 90

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val candidates = mutableListOf<SourceCandidate>()
        val rawCode = identity.rawQueryOrUrl.ifBlank { identity.title }
        val javCode = JavIdParser.parse(rawCode) ?: JavIdParser.parse(identity.title) ?: rawCode.trim()

        if (javCode.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            // Netflav search
            val searchUrl = "https://netflav.com/search?type=title&keyword=$javCode"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val doc = Jsoup.parse(html, "https://netflav.com")
                val firstLink = doc.select("a[href*=\"/video?\"]").firstOrNull()?.attr("abs:href")

                if (!firstLink.isNullOrBlank()) {
                    val vidId = firstLink.substringAfter("id=").substringBefore("&")
                    if (vidId.isNotBlank()) {
                        val apiReq = Request.Builder()
                            .url("$NETFLAV_API_URL?id=$vidId")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .header("Referer", firstLink)
                            .build()

                        val apiResp = client.newCall(apiReq).execute()
                        if (apiResp.isSuccessful) {
                            val json = org.json.JSONObject(apiResp.body?.string() ?: "")
                            val result = json.optJSONObject("result")
                            val srcUrl = result?.optString("src") ?: result?.optString("video_url")
                            val title = result?.optString("title") ?: "[$javCode] Netflav Stream"

                            if (!srcUrl.isNullOrBlank() && (srcUrl.startsWith("http://") || srcUrl.startsWith("https://"))) {
                                val isHls = srcUrl.contains(".m3u8")
                                candidates.add(
                                    SourceCandidate(
                                        id = "netflav_$vidId",
                                        providerId = id,
                                        providerName = "JavPy / Netflav",
                                        serverName = "Netflav Global Stream",
                                        type = if (isHls) SourceStreamType.HLS else SourceStreamType.DIRECT,
                                        title = title,
                                        urlOrMagnet = srcUrl,
                                        quality = "1080p FHD",
                                        qualityScore = 1080,
                                        format = if (isHls) "m3u8" else "mp4",
                                        headers = mapOf(
                                            "Referer" to "https://netflav.com/",
                                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                                        ),
                                        healthScore = 92,
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
        } catch (e: Exception) {
            Log.w(TAG, "JavPy lookup failed for $javCode: ${e.message}")
        }

        emit(candidates)
    }.flowOn(Dispatchers.IO)
}
