package com.example.resolver.providers

import android.content.Context
import android.util.Log
import com.example.extractor.nuvio.NuvioProviderEngine
import com.example.extractor.nuvio.NuvioProviderRepository
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

/**
 * High-Performance Nuvio Provider Extension Manager for Butterfly.
 *
 * Direct integration with Nuvio's provider network (UHDMovies, MoviesMod, MoviesDrive, 4KHDHub, HDHub4u, etc.):
 * - Resolves Movies and TV Shows via TMDB ID, Season, and Episode
 * - Fetches multi-quality streams (4K UHD, 1080p FHD, 720p HD, MKV, MP4, HLS m3u8)
 * - Retains full request headers, subtitles, and server origin
 * - Plays natively via Media3 ExoPlayer
 */
class NuvioDirectSourceProvider(
    private val context: Context? = null
) : SourceProvider {

    override val timeoutMs: Long get() = 120_000L

    companion object {
        private const val TAG = "NuvioDirectSourceProvider"
    }

    override val id: String = "nuvio_direct"
    override val displayName: String = "Nuvio Providers (29+ Scrapers)"
    override val isEnabled: Boolean = true
    override val priority: Int = 96

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
        val candidates = mutableListOf<SourceCandidate>()
        val tmdbId = identity.tmdbId ?: identity.imdbId?.substringBefore(":")
        val title = identity.title.ifBlank { "Movie" }

        if (tmdbId.isNullOrBlank() && title.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val isTv = identity.mediaType == MediaType.TV
        val season = identity.season ?: 1
        val episode = identity.episode ?: 1

        val request = TMDBMediaRequest(
            title = title,
            year = identity.year?.toString() ?: "",
            tmdbId = tmdbId ?: "",
            mediaType = if (isTv) "tv" else "movie",
            season = season,
            episode = episode
        )

        try {
            val appContext = context ?: com.example.MainApplication.appContext
            val nuvioCandidates = NuvioProviderEngine.resolveSourceCandidates(appContext, request)
            candidates.addAll(nuvioCandidates)
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving Nuvio source candidates: ${e.message}")
        }

        emit(candidates)
    }.flowOn(Dispatchers.IO)
}
