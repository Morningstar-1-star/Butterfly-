package com.example.resolver.providers

import android.util.Log
import com.example.model.MediaIdentity
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import com.example.torrent.provider.MagnetioProvider
import com.example.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Magnetio Multi-Indexer Source Provider (Adapted from peterdsp/Magnetio).
 * Integrates multi-indexer torrent search and deduplication directly into Butterfly's stream resolver.
 */
class MagnetioSourceProvider : SourceProvider {

    companion object {
        private const val TAG = "MagnetioSourceProvider"
    }

    override val id: String = "magnetio"
    override val displayName: String = "Magnetio Multi-Indexer"
    override val isEnabled: Boolean
        get() = AppConfig.isMagnetioEnabled()
    override val priority: Int = 85

    private val magnetioTorrentProvider = MagnetioProvider()

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val query = identity.title.ifBlank { identity.rawQueryOrUrl }.trim()
        if (query.isBlank()) {
            emit(emptyList<SourceCandidate>())
            return@flow
        }

        try {
            val results = magnetioTorrentProvider.search(query)
            val candidates = results.map { item ->
                SourceCandidate(
                    id = "magnetio_${item.infoHash.ifBlank { item.title.hashCode().toString() }}",
                    providerId = id,
                    providerName = "Magnetio [${item.source.ifBlank { "Multi" }}]",
                    serverName = item.source.ifBlank { "Magnetio Torrent Swarm" },
                    type = SourceStreamType.TORRENT,
                    title = item.title,
                    urlOrMagnet = item.magnet,
                    quality = item.quality,
                    qualityScore = item.qualityScore,
                    format = "mkv",
                    sizeBytes = item.size,
                    formattedSize = item.formattedSize,
                    seeders = item.seeders,
                    leechers = item.leechers,
                    healthScore = if (item.seeders > 20) 95 else if (item.seeders > 5) 80 else 50,
                    extraData = mapOf("infoHash" to item.infoHash)
                )
            }
            emit(candidates)
        } catch (e: Exception) {
            Log.w(TAG, "Magnetio search error: ${e.message}")
            emit(emptyList<SourceCandidate>())
        }
    }.flowOn(Dispatchers.IO)
}
