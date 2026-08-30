package com.example.resolver.providers

import android.content.Context
import com.example.cloudsocial.mega.MegaSourceResolver
import com.example.cloudsocial.repository.CloudSocialRepository
import com.example.model.MediaIdentity
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import com.example.resolver.health.ProviderHealthManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

class MegaSourceProvider(private val context: Context? = null) : SourceProvider {

    override val id: String = "mega"
    override val displayName: String = "MEGA Cloud Files & Folders"
    override val isEnabled: Boolean = true
    override val priority: Int = 94

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val query = identity.rawQueryOrUrl.ifBlank { identity.title }
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        // 1. Direct MEGA URL parsing
        val megaInfo = MegaSourceResolver.parseUrl(query)
        if (megaInfo != null) {
            val candidate = SourceCandidate(
                id = "mega_${megaInfo.id}",
                providerId = id,
                providerName = "MEGA",
                serverName = if (megaInfo.isFolder) "MEGA Folder" else "MEGA Direct File",
                type = SourceStreamType.DIRECT,
                title = identity.title.ifBlank { "MEGA ${megaInfo.id}" },
                urlOrMagnet = query,
                quality = "HD",
                headers = mapOf("User-Agent" to "Mozilla/5.0"),
                healthScore = ProviderHealthManager.getHealthScore(id),
                capabilities = PlaybackCapabilities(supportsSeeking = true, supportsTrackSelection = false)
            )
            emit(listOf(candidate))
            return@flow
        }

        // 2. Local DB query if Context is available
        if (context != null) {
            try {
                val repository = CloudSocialRepository.getInstance(context)
                val allMedia = repository.allMedia.firstOrNull() ?: emptyList()
                val matched = allMedia.filter { item ->
                    item.type == "MEGA" && (
                        item.title.contains(query, ignoreCase = true) ||
                        item.caption?.contains(query, ignoreCase = true) == true ||
                        item.sourceUrl.contains(query, ignoreCase = true)
                    )
                }.take(10)

                val candidates = matched.map { media ->
                    SourceCandidate(
                        id = media.id,
                        providerId = id,
                        providerName = "MEGA",
                        serverName = "MEGA (${media.formattedSize})",
                        type = SourceStreamType.DIRECT,
                        title = media.title,
                        urlOrMagnet = media.sourceUrl,
                        quality = media.resolution,
                        headers = mapOf("User-Agent" to "Mozilla/5.0"),
                        healthScore = ProviderHealthManager.getHealthScore(id),
                        capabilities = PlaybackCapabilities(supportsSeeking = true, supportsTrackSelection = false)
                    )
                }

                if (candidates.isNotEmpty()) {
                    emit(candidates)
                    return@flow
                }
            } catch (e: Exception) {
                // Ignore DB search exceptions
            }
        }

        emit(emptyList())
    }
}
