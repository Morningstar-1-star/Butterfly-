package com.example.resolver.providers

import android.util.Log
import com.example.extractor.SextbProvider
import com.example.extractor.sextb.SextbParser
import com.example.extractor.sextb.SextbResolver
import com.example.model.MediaIdentity
import com.example.model.MediaType
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.ProviderCapability
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import com.example.resolver.StreamHealthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Universal SourceProvider adapter for SEXТB.
 * Connects SextbProvider to Butterfly's UnifiedSourceResolver and SourceRankingEngine.
 */
class SextbSourceProvider : SourceProvider {

    companion object {
        private const val TAG = "SextbSourceProvider"
    }

    override val id: String = SextbProvider.PROVIDER_ID
    override val displayName: String = SextbProvider.DISPLAY_NAME
    override val isEnabled: Boolean = true
    override val priority: Int = 85

    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.SEARCH,
        ProviderCapability.STREAM,
        ProviderCapability.HLS,
        ProviderCapability.SUBTITLE
    )

    override val supportedMediaTypes: Set<MediaType> = setOf(
        MediaType.JAV,
        MediaType.VIDEO,
        MediaType.MOVIE,
        MediaType.UNKNOWN
    )

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val candidates = mutableListOf<SourceCandidate>()
        val query = identity.rawQueryOrUrl.ifBlank { identity.title }

        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        try {
            // 1. If query is a direct URL, resolve directly
            if (query.startsWith("http://") || query.startsWith("https://")) {
                val sources = SextbProvider.resolveSource(query)
                for (src in sources) {
                    candidates.add(mapToCandidate(src, identity.title.ifBlank { "SEXТB Video" }, query))
                }
            } else {
                // 2. Otherwise search for matching videos
                val searchResults = SextbProvider.search(query, limit = 5)
                for (item in searchResults.take(3)) {
                    val pageUrl = item.uploaderUrl ?: SextbProvider.normalizePageUrl(item.id)
                    val sources = SextbProvider.resolveSource(pageUrl)
                    for (src in sources) {
                        candidates.add(mapToCandidate(src, item.title, pageUrl))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SEXТB source provider error resolving $query: ${e.message}")
        }

        emit(candidates)
    }.flowOn(Dispatchers.IO)

    private fun mapToCandidate(src: com.example.extractor.sextb.VideoSource, title: String, pageUrl: String): SourceCandidate {
        val isHls = src.isHls
        val qualityScore = when (src.quality.lowercase()) {
            "4k" -> 2160
            "1080p" -> 1080
            "720p" -> 720
            "480p" -> 480
            "360p" -> 360
            else -> 1080
        }

        return SourceCandidate(
            id = "sextb_${src.url.hashCode()}",
            providerId = id,
            providerName = displayName,
            serverName = src.sourceName,
            type = if (isHls) SourceStreamType.HLS else SourceStreamType.DIRECT,
            title = title,
            urlOrMagnet = src.url,
            quality = src.quality,
            qualityScore = qualityScore,
            format = if (isHls) "hls" else "mp4",
            headers = src.headers,
            subtitleUrls = src.subtitles.map { it.url },
            healthStatus = StreamHealthStatus.RESOLVED,
            isPlayable = true,
            capabilities = PlaybackCapabilities(
                supportsSeeking = true,
                supportsRangeSeeking = true,
                supportsTrackSelection = true
            ),
            extraData = mapOf("pageUrl" to pageUrl)
        )
    }
}
