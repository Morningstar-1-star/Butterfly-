package com.example.resolver

import android.content.Context
import android.util.Log
import com.example.model.MediaIdentity
import com.example.resolver.providers.CometSourceProvider
import com.example.resolver.providers.JableMissAvSourceProvider
import com.example.resolver.providers.JavPySourceProvider
import com.example.resolver.providers.MediaFusionSourceProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Unified Source Resolver: Central orchestration engine merging Vega direct streams,
 * BitTorrent swarm indexers, Jable/MissAV direct HLS, JavPy multi-source, MediaFusion,
 * and Comet debrid/torrent scrapers into a unified, progressively resolved source stream.
 */
class UnifiedSourceResolver(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedSourceResolver"

        @Volatile
        private var instance: UnifiedSourceResolver? = null

        fun getInstance(context: Context): UnifiedSourceResolver {
            return instance ?: synchronized(this) {
                instance ?: UnifiedSourceResolver(context.applicationContext).also { instance = it }
            }
        }
    }

    private val vegaAdapter = VegaSourceAdapter(context)
    private val torrentAdapter = TorrentSourceAdapter()
    private val jableMissAvProvider = JableMissAvSourceProvider()
    private val javPyProvider = JavPySourceProvider()
    private val mediaFusionProvider = MediaFusionSourceProvider()
    private val cometProvider = CometSourceProvider()
    private val yarrProvider = com.example.resolver.providers.YarrSourceProvider()
    private val magnetioProvider = com.example.resolver.providers.MagnetioSourceProvider()

    val activeProviders: List<SourceProvider>
        get() = listOf(
            jableMissAvProvider,
            javPyProvider,
            mediaFusionProvider,
            cometProvider,
            yarrProvider,
            magnetioProvider,
            vegaAdapter,
            torrentAdapter
        ).filter { it.isEnabled }.sortedByDescending { it.priority }

    /**
     * Searches all active stream providers in parallel with complete isolation.
     * Emits progressively ranked candidate lists as providers return results.
     */
    fun resolveSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = channelFlow {
        val collectedSources = mutableListOf<SourceCandidate>()

        supervisorScope {
            // Launch parallel independent collectors for each provider
            activeProviders.forEach { provider ->
                launch {
                    try {
                        provider.searchSources(identity).collect { newCandidates ->
                            if (newCandidates.isNotEmpty()) {
                                synchronized(collectedSources) {
                                    // Deduplicate by URL or InfoHash
                                    for (c in newCandidates) {
                                        val existingIndex = collectedSources.indexOfFirst {
                                            it.urlOrMagnet == c.urlOrMagnet ||
                                            (it.isTorrent && c.isTorrent && it.extraData["infoHash"] == c.extraData["infoHash"])
                                        }
                                        if (existingIndex >= 0) {
                                            // Keep higher quality / seeder version
                                            if (c.seeders > collectedSources[existingIndex].seeders) {
                                                collectedSources[existingIndex] = c
                                            }
                                        } else {
                                            collectedSources.add(c)
                                        }
                                    }
                                }

                                // Rank and progressively emit updated candidate list
                                val ranked = SourceRankingEngine.rank(collectedSources)
                                send(ranked)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Source provider ${provider.displayName} resolution error: ${e.message}")
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
