package com.example.resolver

import android.content.Context
import android.util.Log
import com.example.model.MediaIdentity
import com.example.model.MediaType
import com.example.vega.VegaProviderClient
import com.example.vega.VegaProviderRepository
import com.example.vega.VegaSearchResult
import com.example.vega.VegaStreamResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Adapter integrating Vega scraping providers into the UnifiedSourceResolver pipeline.
 */
class VegaSourceAdapter(
    private val context: Context,
    override val id: String = "vega_adapter",
    override val displayName: String = "Vega Direct Streams",
    override val isEnabled: Boolean = true,
    override val priority: Int = 100
) : SourceProvider {

    companion object {
        private const val TAG = "VegaSourceAdapter"
        private const val PROVIDER_TIMEOUT_MS = 6000L
    }

    private val repository = VegaProviderRepository(context)

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val query = identity.title.trim()
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val installed = repository.getInstalledProviders().filter { it.isEnabled }
        if (installed.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val collectedCandidates = mutableListOf<SourceCandidate>()

        supervisorScope {
            // Search each active Vega provider concurrently
            val searchJobs = installed.map { provider ->
                async {
                    try {
                        withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                            val results = VegaProviderClient.search(provider.id, query)
                            if (results.isEmpty()) return@withTimeoutOrNull emptyList<SourceCandidate>()

                            val matchingResult = selectBestMatch(results, identity)
                                ?: results.firstOrNull()
                                ?: return@withTimeoutOrNull emptyList<SourceCandidate>()

                            // Fetch metadata / linkList
                            val meta = VegaProviderClient.getMeta(provider.id, matchingResult.link)
                                ?: return@withTimeoutOrNull emptyList<SourceCandidate>()

                            val directLinks = extractDirectLinksForIdentity(meta, identity)
                            val resolvedCandidates = mutableListOf<SourceCandidate>()

                            // Fetch stream URLs
                            for ((index, dLink) in directLinks.take(4).withIndex()) {
                                try {
                                    val streams = VegaProviderClient.getStream(provider.id, dLink.link)
                                    for ((sIdx, stream) in streams.withIndex()) {
                                        val candidate = mapStreamToCandidate(
                                            providerId = provider.id,
                                            providerName = provider.name,
                                            serverName = stream.server.ifBlank { "Server ${index + 1}.${sIdx + 1}" },
                                            stream = stream,
                                            title = "${meta.title} - ${dLink.title}",
                                            index = resolvedCandidates.size
                                        )
                                        if (candidate != null) {
                                            resolvedCandidates.add(candidate)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            resolvedCandidates
                        } ?: emptyList()
                    } catch (e: Exception) {
                        Log.w(TAG, "Vega provider ${provider.name} failed: ${e.message}")
                        emptyList()
                    }
                }
            }

            // Await each provider response and progressively emit candidates
            for (deferred in searchJobs) {
                val newCandidates = deferred.await()
                if (newCandidates.isNotEmpty()) {
                    synchronized(collectedCandidates) {
                        collectedCandidates.addAll(newCandidates)
                    }
                    emit(collectedCandidates.toList())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun selectBestMatch(results: List<VegaSearchResult>, identity: MediaIdentity): VegaSearchResult? {
        val queryLower = identity.title.lowercase().trim()
        val year = identity.year

        // 1. Exact match with year
        if (!year.isNullOrBlank()) {
            val withYear = results.firstOrNull { 
                val t = it.title.lowercase()
                t.contains(queryLower) && t.contains(year) 
            }
            if (withYear != null) return withYear
        }

        // 2. Direct title match
        return results.firstOrNull { it.title.lowercase().contains(queryLower) }
    }

    private fun extractDirectLinksForIdentity(
        meta: com.example.vega.VegaMetaResult,
        identity: MediaIdentity
    ): List<com.example.vega.VegaDirectLink> {
        val allDirectLinks = mutableListOf<com.example.vega.VegaDirectLink>()

        if (identity.mediaType == MediaType.TV && identity.episode != null) {
            val targetEp = identity.episode
            val targetSeason = identity.season ?: 1

            for (linkList in meta.linkList) {
                val matchingLinks = linkList.directLinks.filter { link ->
                    val lower = link.title.lowercase()
                    val matchesEp = lower.contains("e$targetEp") || 
                                    lower.contains("ep $targetEp") || 
                                    lower.contains("episode $targetEp") ||
                                    lower.contains("e${String.format("%02d", targetEp)}")
                    val matchesSeason = lower.contains("s$targetSeason") || 
                                        lower.contains("season $targetSeason") ||
                                        !lower.contains("season")
                    matchesEp && matchesSeason
                }
                allDirectLinks.addAll(matchingLinks)
            }
        }

        if (allDirectLinks.isEmpty()) {
            // Take all available links (e.g. for Movies or general releases)
            meta.linkList.forEach { allDirectLinks.addAll(it.directLinks) }
        }

        return allDirectLinks
    }

    private fun mapStreamToCandidate(
        providerId: String,
        providerName: String,
        serverName: String,
        stream: VegaStreamResult,
        title: String,
        index: Int
    ): SourceCandidate? {
        if (stream.url.isBlank()) return null

        val isMagnet = stream.url.startsWith("magnet:", ignoreCase = true)
        val streamType = when {
            isMagnet || stream.isTorrent -> SourceStreamType.TORRENT
            stream.format.equals("hls", ignoreCase = true) || stream.url.contains(".m3u8", ignoreCase = true) -> SourceStreamType.HLS
            stream.format.equals("dash", ignoreCase = true) || stream.url.contains(".mpd", ignoreCase = true) -> SourceStreamType.DASH
            else -> SourceStreamType.DIRECT
        }

        val qualityScore = when {
            stream.quality.contains("4k", ignoreCase = true) || stream.quality.contains("2160", ignoreCase = true) -> 2160
            stream.quality.contains("1080", ignoreCase = true) -> 1080
            stream.quality.contains("720", ignoreCase = true) -> 720
            stream.quality.contains("480", ignoreCase = true) -> 480
            else -> 1080
        }

        return SourceCandidate(
            id = "vega_${providerId}_${index}_${System.currentTimeMillis() % 10000}",
            providerId = providerId,
            providerName = providerName,
            serverName = serverName,
            type = streamType,
            title = title,
            urlOrMagnet = stream.url,
            quality = stream.quality.ifBlank { "1080p" },
            qualityScore = qualityScore,
            format = stream.format,
            headers = stream.headers,
            subtitleUrls = stream.subtitleUrls,
            healthScore = 95,
            isPlayable = true
        )
    }
}
