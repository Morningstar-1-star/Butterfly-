package com.example.resolver.providers

import android.util.Log
import com.example.model.MediaIdentity
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import com.example.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * YARR Stream Provider (Adapted from spookyhost1/yarr-stremio).
 * High-performance torrent aggregation service designed for Stremio/Butterfly integrations.
 */
class YarrSourceProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : SourceProvider {

    companion object {
        private const val TAG = "YarrProvider"
        const val DEFAULT_BASE_URL = "https://yarr.fly.dev"
    }

    override val id: String = "yarr"
    override val displayName: String = "YARR Torrent Aggregator"
    override val isEnabled: Boolean
        get() = AppConfig.isYarrEnabled()
    override val priority: Int = 84

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val stremioId = identity.toStremioImdbId() ?: identity.rawQueryOrUrl.ifBlank { null }
        if (stremioId.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }

        val type = if (stremioId.contains(":") || identity.mediaType == com.example.model.MediaType.TV) "series" else "movie"
        val baseUrl = AppConfig.getYarrServerUrl().ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
        val endpoint = "$baseUrl/stream/$type/$stremioId.json"

        val candidates = mutableListOf<SourceCandidate>()
        try {
            val req = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Butterfly/1.0 YarrClient")
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val jsonStr = resp.body?.string() ?: "{}"
                val root = JSONObject(jsonStr)
                val streams = root.optJSONArray("streams")
                if (streams != null) {
                    for (i in 0 until streams.length()) {
                        val s = streams.optJSONObject(i) ?: continue
                        val name = s.optString("name", "YARR")
                        val title = s.optString("title", "Stream #$i")
                        val infoHash = s.optString("infoHash", "")
                        val directUrl = s.optString("url", "")

                        val isDirect = directUrl.isNotBlank() && directUrl.startsWith("http")
                        val targetUrlOrMagnet = if (isDirect) {
                            directUrl
                        } else if (infoHash.isNotBlank()) {
                            com.example.torrent.protocol.MagnetParser.buildMagnetUrl(infoHash, title.substringBefore("\n"))
                        } else {
                            continue
                        }

                        val streamType = if (isDirect) {
                            if (directUrl.contains(".m3u8")) SourceStreamType.HLS else SourceStreamType.DIRECT
                        } else {
                            SourceStreamType.TORRENT
                        }

                        val seeders = Regex("👤\\s*(\\d+)").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        val sizeStr = Regex("💾\\s*([0-9.]+\\s*[GMBkmb]+)").find(title)?.groupValues?.getOrNull(1) ?: ""

                        val quality = when {
                            title.contains("2160p", ignoreCase = true) || title.contains("4K", ignoreCase = true) -> "4K"
                            title.contains("1080p", ignoreCase = true) -> "1080p"
                            title.contains("720p", ignoreCase = true) -> "720p"
                            title.contains("480p", ignoreCase = true) -> "480p"
                            else -> "1080p"
                        }
                        val qualityScore = when (quality) {
                            "4K" -> 2160
                            "1080p" -> 1080
                            "720p" -> 720
                            "480p" -> 480
                            else -> 1080
                        }

                        candidates.add(
                            SourceCandidate(
                                id = "yarr_${infoHash.ifBlank { i.toString() }}",
                                providerId = id,
                                providerName = if (isDirect) "YARR [Direct]" else "YARR Torrent",
                                serverName = name.replace("\n", " "),
                                type = streamType,
                                title = title.replace("\n", " ").trim(),
                                urlOrMagnet = targetUrlOrMagnet,
                                quality = quality,
                                qualityScore = qualityScore,
                                format = if (isDirect) "mp4" else "mkv",
                                formattedSize = sizeStr,
                                seeders = seeders,
                                leechers = 0,
                                healthScore = if (seeders > 50) 100 else if (seeders > 10) 85 else 60,
                                extraData = if (infoHash.isNotBlank()) mapOf("infoHash" to infoHash) else emptyMap()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "YARR stream resolution note: ${e.message}")
        }

        emit(candidates)
    }.flowOn(Dispatchers.IO)
}
