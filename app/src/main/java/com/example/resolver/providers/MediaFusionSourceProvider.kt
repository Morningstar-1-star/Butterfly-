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
 * MediaFusion Stream Provider (Adapted from mhdzumair/MediaFusion).
 * Universal multi-scraper indexing torrents, direct debrid links, and HLS live streams.
 */
class MediaFusionSourceProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SourceProvider {

    companion object {
        private const val TAG = "MediaFusionProvider"
        const val DEFAULT_BASE_URL = "https://mediafusion.elfhosted.com"
    }

    override val id: String = "mediafusion"
    override val displayName: String = "MediaFusion Multi-Index"
    override val isEnabled: Boolean = true
    override val priority: Int = 88

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val candidates = mutableListOf<SourceCandidate>()
        val stremioId = identity.toStremioImdbId() ?: identity.rawQueryOrUrl.ifBlank { null }
        if (stremioId.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }

        val type = if (stremioId.contains(":") || identity.mediaType == com.example.model.MediaType.TV) "series" else "movie"
        val debridKey = AppConfig.getDebridApiKey().trim()
        val baseUrl = DEFAULT_BASE_URL

        val streamUrl = if (debridKey.isNotBlank()) {
            "$baseUrl/realdebrid=$debridKey/stream/$type/$stremioId.json"
        } else {
            "$baseUrl/stream/$type/$stremioId.json"
        }

        try {
            val req = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Butterfly/1.0 Stremio")
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val jsonStr = resp.body?.string() ?: ""
                val root = JSONObject(jsonStr)
                val streams = root.optJSONArray("streams")
                if (streams != null) {
                    for (i in 0 until streams.length()) {
                        val s = streams.optJSONObject(i) ?: continue
                        val name = s.optString("name", "MediaFusion")
                        val title = s.optString("title", "Stream #$i")
                        val infoHash = s.optString("infoHash")
                        val directUrl = s.optString("url")

                        val isDebridDirect = directUrl.isNotBlank() && (directUrl.startsWith("http://") || directUrl.startsWith("https://"))
                        val targetUrlOrMagnet = if (isDebridDirect) {
                            directUrl
                        } else if (infoHash.isNotBlank()) {
                            "magnet:?xt=urn:btih:$infoHash&dn=${java.net.URLEncoder.encode(title.substringBefore("\n"), "UTF-8")}"
                        } else {
                            continue
                        }

                        val streamType = if (isDebridDirect) {
                            if (directUrl.contains(".m3u8")) SourceStreamType.HLS else SourceStreamType.DIRECT
                        } else {
                            SourceStreamType.TORRENT
                        }

                        val seeders = Regex("👤\\s*(\\d+)").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        val sizeStr = Regex("💾\\s*([0-9.]+\\s*[GMBkmb]+)").find(title)?.groupValues?.getOrNull(1) ?: ""

                        candidates.add(
                            SourceCandidate(
                                id = "mf_${infoHash.ifBlank { i.toString() }}",
                                providerId = id,
                                providerName = if (isDebridDirect) "MediaFusion [RD+]" else "MediaFusion Swarm",
                                serverName = name.replace("\n", " "),
                                type = streamType,
                                title = title.replace("\n", " • "),
                                urlOrMagnet = targetUrlOrMagnet,
                                formattedSize = sizeStr,
                                seeders = seeders,
                                healthScore = if (isDebridDirect) 100 else (seeders * 3).coerceIn(10, 100),
                                capabilities = PlaybackCapabilities(
                                    supportsSeeking = true,
                                    supportsTrackSelection = true
                                ),
                                extraData = if (infoHash.isNotBlank()) mapOf("infoHash" to infoHash) else emptyMap()
                            )
                        )
                    }
                    emit(ArrayList(candidates))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaFusion search error: ${e.message}")
        }

        emit(candidates)
    }.flowOn(Dispatchers.IO)
}
