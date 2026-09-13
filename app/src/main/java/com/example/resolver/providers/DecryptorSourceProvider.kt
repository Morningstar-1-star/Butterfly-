package com.example.resolver.providers

import android.util.Log
import com.example.decryptor.DecryptorProviderClient
import com.example.model.MediaIdentity
import com.example.model.MediaType
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.ProviderCapability
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import com.example.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Decryptor Source Provider Adapter for Butterfly's UnifiedSourceResolver.
 *
 * Connects to the Decryptor multi-server extractor backend (POST /api/extract)
 * and resolves TMDB Movies and TV episodes into individual playable stream candidates:
 * - Independent provider alongside Torrentio and Vega
 * - Multi-server output (Vidhide, Turbo, Nxsha Fast, etc.)
 * - Strict Referer/Origin/User-Agent header pass-through
 * - Seamless Media3 ExoPlayer integration
 */
class DecryptorSourceProvider : SourceProvider {

    companion object {
        private const val TAG = "DecryptorSourceProvider"
    }

    override val id: String = "decryptor"
    override val displayName: String = "Decryptor (Nxsha Multi-Server)"
    override val isEnabled: Boolean
        get() = AppConfig.isDecryptorEnabled()
    override val priority: Int = 98

    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.SEARCH,
        ProviderCapability.STREAM,
        ProviderCapability.DIRECT_HTTP,
        ProviderCapability.HLS,
        ProviderCapability.SUBTITLE,
        ProviderCapability.CAPABILITY_4K
    )

    override val supportedMediaTypes: Set<MediaType> = setOf(
        MediaType.MOVIE,
        MediaType.TV,
        MediaType.ANIME
    )

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        if (!isEnabled) {
            emit(emptyList())
            return@flow
        }

        val tmdbId = identity.tmdbId?.takeIf { it.isNotBlank() }
        val imdbId = identity.imdbId ?: identity.toStremioImdbId()?.substringBefore(":")
        val targetId = tmdbId ?: imdbId ?: identity.title

        if (targetId.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val isTv = identity.mediaType == MediaType.TV
        val season = identity.season ?: 1
        val episode = identity.episode ?: 1

        try {
            val extractResult = DecryptorProviderClient.extract(
                tmdbIdOrUrl = targetId,
                mediaType = if (isTv) "tv" else "movie",
                season = season,
                episode = episode,
                title = identity.title
            )

            if (!extractResult.success || extractResult.servers.isEmpty()) {
                Log.d(TAG, "No Decryptor servers: ${extractResult.errorMessage}")
                emit(emptyList())
                return@flow
            }

            val candidates = extractResult.servers.mapIndexed { index, server ->
                val streamUrl = server.effectivePlayableUrl
                val isHls = server.type.contains("m3u8", ignoreCase = true) ||
                        streamUrl.contains(".m3u8", ignoreCase = true)

                val qualityNum = when {
                    server.quality.contains("2160") || server.quality.contains("4k", ignoreCase = true) -> 2160
                    server.quality.contains("1080") -> 1080
                    server.quality.contains("720") -> 720
                    server.quality.contains("480") -> 480
                    else -> 1080
                }

                SourceCandidate(
                    id = "decryptor_${server.name.lowercase().replace(" ", "_")}_${index}_${targetId.hashCode()}",
                    providerId = id,
                    providerName = "Decryptor",
                    serverName = server.name,
                    type = if (isHls) SourceStreamType.HLS else SourceStreamType.DIRECT,
                    title = "${identity.title} [${server.name} • ${server.quality} • ${server.type.uppercase()}]",
                    urlOrMagnet = streamUrl,
                    quality = server.quality,
                    qualityScore = qualityNum,
                    format = server.type.lowercase(),
                    headers = server.headers,
                    healthScore = if (server.status.equals("Online", ignoreCase = true)) 98 else 85,
                    subtitleUrls = server.subtitles.map { it.url },
                    extraData = mapOf(
                        "server_status" to server.status,
                        "server_name" to server.name,
                        "proxy_url" to (server.proxyUrl ?: ""),
                        "raw_url" to (server.url ?: "")
                    ),
                    capabilities = PlaybackCapabilities(
                        supportsSeeking = true,
                        supportsTrackSelection = true
                    )
                )
            }

            emit(candidates)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving sources in DecryptorSourceProvider: ${e.message}", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
}
