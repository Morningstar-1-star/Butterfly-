package com.example.resolver.providers

import com.example.extractor.Hanime1Provider
import com.example.extractor.SpankBangProvider
import com.example.model.MediaIdentity
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import com.example.resolver.health.ProviderHealthManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class Hanime1SourceProvider : SourceProvider {
    override val id: String = "hanime1"
    override val displayName: String = "Hanime1 Anime HLS"
    override val isEnabled: Boolean = true
    override val priority: Int = 94

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val query = identity.rawQueryOrUrl.ifBlank { identity.title }
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val streamData = Hanime1Provider.getStreamData(query)
        if (streamData != null) {
            val health = ProviderHealthManager.getHealthScore(id)
            val candidates = streamData.availableStreamOptions.mapIndexed { idx, opt ->
                val vUrl = opt.videoUrl ?: ""
                val isHls = opt.format.contains("m3u8", ignoreCase = true) || vUrl.contains(".m3u8")
                SourceCandidate(
                    id = "hanime1_${streamData.videoId}_$idx",
                    providerId = id,
                    providerName = "Hanime1",
                    serverName = "Hanime1 CDN (${opt.qualityLabel})",
                    type = if (isHls) SourceStreamType.HLS else SourceStreamType.DIRECT,
                    title = streamData.title,
                    urlOrMagnet = vUrl,
                    quality = opt.qualityLabel,
                    qualityScore = SpankBangProvider.parseQualityScore(opt.qualityLabel),
                    format = opt.format,
                    headers = opt.headers,
                    healthScore = health,
                    capabilities = PlaybackCapabilities(supportsSeeking = true, supportsTrackSelection = true)
                )
            }
            emit(candidates)
        } else {
            val searchResults = Hanime1Provider.search(query, limit = 5)
            val candidates = mutableListOf<SourceCandidate>()
            for (res in searchResults) {
                val data = Hanime1Provider.getStreamData(res.id)
                if (data != null) {
                    val health = ProviderHealthManager.getHealthScore(id)
                    val opts = data.availableStreamOptions.mapIndexed { idx, opt ->
                        val vUrl = opt.videoUrl ?: ""
                        SourceCandidate(
                            id = "hanime1_${res.id}_$idx",
                            providerId = id,
                            providerName = "Hanime1",
                            serverName = "Hanime1 (${opt.qualityLabel})",
                            type = if (opt.format.contains("m3u8")) SourceStreamType.HLS else SourceStreamType.DIRECT,
                            title = data.title,
                            urlOrMagnet = vUrl,
                            quality = opt.qualityLabel,
                            qualityScore = SpankBangProvider.parseQualityScore(opt.qualityLabel),
                            format = opt.format,
                            headers = opt.headers,
                            healthScore = health,
                            capabilities = PlaybackCapabilities(supportsSeeking = true)
                        )
                    }
                    candidates.addAll(opts)
                    emit(ArrayList(candidates))
                }
            }
        }
    }
}
