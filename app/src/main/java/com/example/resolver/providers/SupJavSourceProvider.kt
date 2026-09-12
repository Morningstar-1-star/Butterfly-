package com.example.resolver.providers

import android.util.Log
import com.example.extractor.SupJavProvider
import com.example.extractor.supjav.SupJavParser
import com.example.extractor.supjav.SupJavSource
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
 * Universal SourceProvider adapter for SupJav.
 * Connects SupJavProvider to Butterfly's UnifiedSourceResolver and SourceRankingEngine.
 */
class SupJavSourceProvider : SourceProvider {

    companion object {
        private const val TAG = "SupJavSourceProvider"
    }

    override val id: String = SupJavProvider.PROVIDER_ID
    override val displayName: String = "SupJav"
    override val isEnabled: Boolean = true
    override val priority: Int = 90

    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.SEARCH,
        ProviderCapability.STREAM,
        ProviderCapability.HLS,
        ProviderCapability.DIRECT_HTTP
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
            if (query.startsWith("http://") || query.startsWith("https://")) {
                val sources = SupJavProvider.resolveSource(query)
                for (src in sources) {
                    candidates.add(mapToCandidate(src, identity.title.ifBlank { "SupJav Video" }, query))
                }
            } else {
                val searchResults = SupJavProvider.search(query, limit = 10)
                for (item in searchResults.take(10)) {
                    val pageUrl = item.uploaderUrl ?: "${SupJavProvider.DEFAULT_BASE_URL}/${item.id}.html"
                    val sources = SupJavProvider.resolveSource(pageUrl)
                    for (src in sources) {
                        candidates.add(mapToCandidate(src, item.title, pageUrl))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SupJav source provider error resolving $query: ${e.message}")
        }

        emit(candidates)
    }.flowOn(Dispatchers.IO)

    private fun mapToCandidate(src: SupJavSource, title: String, pageUrl: String): SourceCandidate {
        val isHls = src.isHls || src.url.contains(".m3u8")
        val qualityScore = when {
            src.quality.contains("1080", ignoreCase = true) -> 1080
            src.quality.contains("720", ignoreCase = true) -> 720
            src.quality.contains("480", ignoreCase = true) -> 480
            src.quality.contains("4k", ignoreCase = true) -> 2160
            else -> 1080
        }

        return SourceCandidate(
            id = "supjav_${src.url.hashCode()}",
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
            subtitleUrls = emptyList(),
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
