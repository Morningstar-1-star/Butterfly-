package com.example.resolver.providers

import com.example.extractor.HQPornerProvider
import com.example.model.MediaIdentity
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import com.example.resolver.health.ProviderHealthManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class HQPornerSourceProvider : SourceProvider {
    override val id: String = "hqporner"
    override val displayName: String = "HQPorner Ultra HD"
    override val isEnabled: Boolean = true
    override val priority: Int = 92

    private fun parseQualityScore(quality: String): Int {
        val q = quality.lowercase()
        return when {
            q.contains("4k") || q.contains("2160") -> 100
            q.contains("1440") || q.contains("2k") -> 90
            q.contains("1080") -> 80
            q.contains("720") -> 60
            q.contains("480") -> 40
            q.contains("360") -> 30
            else -> 20
        }
    }

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val query = identity.rawQueryOrUrl.ifBlank { identity.title }
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val streamData = HQPornerProvider.getStreamData(query)
        if (streamData != null) {
            val health = ProviderHealthManager.getHealthScore(id)
            val candidates = streamData.availableStreamOptions.mapIndexed { idx, opt ->
                val vUrl = opt.videoUrl ?: ""
                SourceCandidate(
                    id = "hqporner_${streamData.videoId}_$idx",
                    providerId = id,
                    providerName = "HQPorner",
                    serverName = "HQPorner (${opt.qualityLabel})",
                    type = SourceStreamType.DIRECT,
                    title = streamData.title,
                    urlOrMagnet = vUrl,
                    quality = opt.qualityLabel,
                    qualityScore = parseQualityScore(opt.qualityLabel),
                    format = opt.format,
                    headers = opt.headers,
                    healthScore = health,
                    capabilities = PlaybackCapabilities(supportsSeeking = true)
                )
            }
            emit(candidates)
        } else {
            val searchResults = HQPornerProvider.search(query, limit = 5)
            val candidates = mutableListOf<SourceCandidate>()
            for (res in searchResults) {
                val data = HQPornerProvider.getStreamData(res.id)
                if (data != null) {
                    val health = ProviderHealthManager.getHealthScore(id)
                    val opts = data.availableStreamOptions.mapIndexed { idx, opt ->
                        val vUrl = opt.videoUrl ?: ""
                        SourceCandidate(
                            id = "hqporner_${res.id}_$idx",
                            providerId = id,
                            providerName = "HQPorner",
                            serverName = "HQPorner (${opt.qualityLabel})",
                            type = SourceStreamType.DIRECT,
                            title = data.title,
                            urlOrMagnet = vUrl,
                            quality = opt.qualityLabel,
                            qualityScore = parseQualityScore(opt.qualityLabel),
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
