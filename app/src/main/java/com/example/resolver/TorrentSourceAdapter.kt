package com.example.resolver

import android.util.Log
import com.example.model.MediaIdentity
import com.example.torrent.model.TorrentRelease
import com.example.torrent.provider.TorrentProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Adapter integrating BitTorrent swarm indexers (Torrentio, Nyaa, YTS, EZTV)
 * into the UnifiedSourceResolver pipeline.
 */
class TorrentSourceAdapter(
    override val id: String = "torrent_adapter",
    override val displayName: String = "P2P Torrent Swarms",
    override val isEnabled: Boolean = true,
    override val priority: Int = 80
) : SourceProvider {

    companion object {
        private const val TAG = "TorrentSourceAdapter"
    }

    private val torrentManager = TorrentProviderManager.getInstance()

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val query = identity.title.trim()
        if (query.isBlank()) {
            emit(emptyList<SourceCandidate>())
            return@flow
        }

        val torrentIdentity = com.example.torrent.provider.MediaIdentity(
            title = identity.title,
            year = identity.year,
            mediaType = identity.mediaType.name.lowercase(),
            imdbId = identity.imdbId,
            tmdbId = identity.tmdbId,
            season = identity.season,
            episode = identity.episode
        )

        try {
            val releases: List<TorrentRelease> = torrentManager.searchReleases(query, torrentIdentity)
            val candidates: List<SourceCandidate> = releases.mapIndexed { index, rel ->
                val health = when {
                    rel.seeders >= 50 -> 100
                    rel.seeders >= 20 -> 90
                    rel.seeders >= 5 -> 70
                    rel.seeders > 0 -> 40
                    else -> 10
                }

                SourceCandidate(
                    id = "torrent_${rel.provider}_${rel.infoHash.take(8)}_${index}",
                    providerId = rel.provider.lowercase(),
                    providerName = rel.provider,
                    serverName = "${rel.provider} (🧲 ${rel.seeders} seeds)",
                    type = SourceStreamType.TORRENT,
                    title = rel.title,
                    urlOrMagnet = rel.magnetUrl,
                    quality = if (rel.quality.isNotBlank()) rel.quality else "1080p",
                    qualityScore = rel.qualityScore,
                    format = "mkv",
                    sizeBytes = rel.sizeBytes,
                    formattedSize = rel.formattedSize,
                    seeders = rel.seeders,
                    leechers = rel.leechers,
                    healthScore = health,
                    isPlayable = rel.seeders > 0 || rel.magnetUrl.isNotBlank(),
                    extraData = mapOf(
                        "infoHash" to rel.infoHash,
                        "codec" to rel.codec,
                        "audio" to rel.audioChannels,
                        "hdr" to rel.hdr
                    )
                )
            }
            emit(candidates)
        } catch (e: Exception) {
            Log.e(TAG, "Torrent search error: ${e.message}")
            emit(emptyList<SourceCandidate>())
        }
    }.flowOn(Dispatchers.IO)
}
