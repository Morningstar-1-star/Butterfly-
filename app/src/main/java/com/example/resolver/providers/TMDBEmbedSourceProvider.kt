package com.example.resolver.providers

import android.content.Context
import android.util.Log
import com.example.extractor.tmdbembed.TMDBEmbedConfig
import com.example.extractor.tmdbembed.TMDBEmbedExtractorEngine
import com.example.extractor.tmdbembed.TMDBMediaRequest
import com.example.model.MediaIdentity
import com.example.model.MediaType
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.ProviderCapability
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class TMDBEmbedSourceProvider(private val context: Context) : SourceProvider {

    companion object {
        private const val TAG = "TMDBEmbedSourceProvider"
    }

    override val id: String = "tmdb_embed"
    override val displayName: String = "TMDB Embed"
    override val isEnabled: Boolean
        get() = TMDBEmbedConfig.isMasterEnabled(context)
    override val priority: Int = 99

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
        val targetId = tmdbId ?: imdbId ?: ""

        if (targetId.isBlank() && identity.title.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val isTv = identity.mediaType == MediaType.TV
        val season = identity.season ?: 1
        val episode = identity.episode ?: 1

        val request = TMDBMediaRequest(
            tmdbId = tmdbId ?: targetId,
            mediaType = if (isTv) "tv" else "movie",
            season = season,
            episode = episode,
            title = identity.title,
            year = identity.year?.toString() ?: "",
            imdbId = imdbId
        )

        try {
            val options = TMDBEmbedExtractorEngine.resolveStreamOptions(context, request)
            if (options.isEmpty()) {
                emit(emptyList())
                return@flow
            }

            val candidates = options.mapIndexed { index, opt ->
                val streamUrl = opt.videoUrl ?: ""
                val isHls = opt.format.contains("hls", ignoreCase = true) || streamUrl.contains(".m3u8")

                val qualityNum = when {
                    opt.qualityLabel.contains("2160") || opt.qualityLabel.contains("4k", ignoreCase = true) -> 2160
                    opt.qualityLabel.contains("1080") -> 1080
                    opt.qualityLabel.contains("720") -> 720
                    opt.qualityLabel.contains("480") -> 480
                    else -> 1080
                }

                SourceCandidate(
                    id = "tmdb_embed_${index}_${opt.sourceName.hashCode()}_${targetId.hashCode()}",
                    providerId = id,
                    providerName = "TMDB Embed",
                    serverName = opt.sourceName,
                    type = if (isHls) SourceStreamType.HLS else SourceStreamType.DIRECT,
                    title = "${identity.title} [${opt.sourceName} • ${opt.qualityLabel}]",
                    urlOrMagnet = streamUrl,
                    quality = opt.qualityCategory.ifBlank { "1080p" },
                    qualityScore = qualityNum,
                    format = opt.format.lowercase(),
                    headers = opt.headers,
                    healthScore = 98,
                    subtitleUrls = opt.subtitles.map { it.url },
                    extraData = mapOf(
                        "source_name" to opt.sourceName,
                        "raw_url" to streamUrl
                    ),
                    capabilities = PlaybackCapabilities(
                        supportsSeeking = true,
                        supportsTrackSelection = true
                    )
                )
            }

            emit(candidates)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving sources in TMDBEmbedSourceProvider: ${e.message}", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
}
