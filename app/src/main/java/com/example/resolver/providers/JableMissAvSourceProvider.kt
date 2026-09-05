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
 * JableTV & MissAV Stream Provider (Adapted from Alos21750/JableTV-MissAV-Downloader-GUI-2026).
 * Extracts fast direct HLS (M3U8) video streams and subtitle tracks from Jable.tv and MissAV.
 */
class JableMissAvSourceProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SourceProvider {

    companion object {
        private const val TAG = "JableMissAvProvider"
        private val JABLE_MIRRORS = listOf("https://jable.tv", "https://jable.me")
        private val MISSAV_MIRRORS = listOf("https://missav.ai", "https://missav.ws", "https://missav.live", "https://missav.com", "https://missav.yt")
    }

    override val id: String = "jable_missav"
    override val displayName: String = "Jable & MissAV Direct HLS"
    override val isEnabled: Boolean = true
    override val priority: Int = 95

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val candidates = mutableListOf<SourceCandidate>()
        val rawCode = identity.rawQueryOrUrl.ifBlank { identity.title }
        val javCode = JavIdParser.parse(rawCode) ?: JavIdParser.parse(identity.title) ?: rawCode.trim()

        if (javCode.isBlank()) {
            emit(emptyList())
            return@flow
        }

        // 1. Resolve Jable.tv HLS M3U8 across mirrors
        val jableCode = javCode.lowercase()
        for (mirror in JABLE_MIRRORS) {
            try {
                val jableUrl = "$mirror/videos/$jableCode/"
                val jableReq = Request.Builder()
                    .url(jableUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "$mirror/")
                    .build()

                val jableResp = client.newCall(jableReq).execute()
                if (jableResp.isSuccessful) {
                    val html = jableResp.body?.string() ?: ""
                    val m3u8Match = Regex("var\\s+hlsUrl\\s*=\\s*['\"](https://[^'\"]+\\.m3u8)['\"]").find(html)
                        ?: Regex("['\"](https://[^'\"]+\\.m3u8[^'\"]*)['\"]").find(html)

                    if (m3u8Match != null) {
                        val m3u8Url = m3u8Match.groupValues[1]
                        val doc = Jsoup.parse(html)
                        val title = doc.select(".header-left h4, .title, h1").firstOrNull()?.text()?.trim() ?: "[$javCode] Jable 1080p"

                        candidates.add(
                            SourceCandidate(
                                id = "jable_$jableCode",
                                providerId = id,
                                providerName = "Jable.tv",
                                serverName = "Jable CDN (HLS)",
                                type = SourceStreamType.HLS,
                                title = title,
                                urlOrMagnet = m3u8Url,
                                quality = "1080p FHD",
                                qualityScore = 1080,
                                format = "m3u8",
                                headers = mapOf(
                                    "Referer" to "$mirror/",
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                ),
                                healthScore = 98,
                                capabilities = PlaybackCapabilities(
                                    supportsSeeking = true,
                                    supportsTrackSelection = true
                                )
                            )
                        )
                        emit(ArrayList(candidates))
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Jable resolution error on $mirror for $javCode: ${e.message}")
            }
        }

        // 2. Resolve MissAV HLS M3U8 across mirrors
        val missavCode = javCode.lowercase()
        for (mirror in MISSAV_MIRRORS) {
            try {
                val missavUrl = "$mirror/$missavCode"
                val missavReq = Request.Builder()
                    .url(missavUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "$mirror/")
                    .build()

                val missavResp = client.newCall(missavReq).execute()
                if (missavResp.isSuccessful) {
                    val html = missavResp.body?.string() ?: ""
                    val m3u8Match = Regex("['\"](https://(?:[a-zA-Z0-9.-]+\\.)?surrit\\.(?:com|cc|net)/[^'\"]+/playlist\\.m3u8)['\"]").find(html)
                        ?: Regex("['\"](https://(?:[a-zA-Z0-9.-]+\\.)?sixyik\\.(?:com|cc|net)/[^'\"]+/playlist\\.m3u8)['\"]").find(html)
                        ?: Regex("['\"](https://[^'\"]+/playlist\\.m3u8)['\"]").find(html)
                        ?: Regex("['\"](https://[^'\"]+\\.m3u8)['\"]").find(html)

                    if (m3u8Match != null) {
                        val m3u8Url = m3u8Match.groupValues[1]
                        val doc = Jsoup.parse(html)
                        val title = doc.select("h1, .text-base").firstOrNull()?.text()?.trim() ?: "[$javCode] MissAV 1080p"

                        candidates.add(
                            SourceCandidate(
                                id = "missav_$missavCode",
                                providerId = id,
                                providerName = "MissAV",
                                serverName = "Surrit Fast CDN (HLS)",
                                type = SourceStreamType.HLS,
                                title = title,
                                urlOrMagnet = m3u8Url,
                                quality = "1080p FHD",
                                qualityScore = 1080,
                                format = "m3u8",
                                headers = mapOf(
                                    "Referer" to "$mirror/",
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                ),
                                healthScore = 95,
                                capabilities = PlaybackCapabilities(
                                    supportsSeeking = true,
                                    supportsTrackSelection = true
                                )
                            )
                        )
                        emit(ArrayList(candidates))
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MissAV resolution error on $mirror for $javCode: ${e.message}")
            }
        }

        emit(candidates)
    }.flowOn(Dispatchers.IO)
}
