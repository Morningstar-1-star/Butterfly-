package com.example.resolver.providers

import android.content.Context
import com.example.bunkr.model.BunkrFile
import com.example.bunkr.model.BunkrUrlType
import com.example.bunkr.repository.BunkrRepository
import com.example.bunkr.resolver.BunkrFileResolver
import com.example.bunkr.resolver.BunkrUrlUtils
import com.example.model.MediaIdentity
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import com.example.resolver.health.ProviderHealthManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

class BunkrSourceProvider(private val context: Context? = null) : SourceProvider {

    override val id: String = "bunkr"
    override val displayName: String = "Bunkr Albums & Direct CDN"
    override val isEnabled: Boolean = true
    override val priority: Int = 95

    private val fileResolver by lazy { BunkrFileResolver() }

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        val query = identity.rawQueryOrUrl.ifBlank { identity.title }
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        // 1. Direct Bunkr URL handling
        if (BunkrUrlUtils.isBunkrUrl(query)) {
            val parsed = BunkrUrlUtils.parseUrl(query)
            if (parsed != null) {
                if (parsed.type == BunkrUrlType.FILE) {
                    try {
                        val resolved = fileResolver.resolveFile(parsed.canonicalUrl)
                        val candidate = SourceCandidate(
                            id = "bunkr_${resolved.fileId}",
                            providerId = id,
                            providerName = "Bunkr",
                            serverName = "Bunkr Direct CDN",
                            type = if (resolved.mimeType.contains("mpegURL")) SourceStreamType.HLS else SourceStreamType.DIRECT,
                            title = resolved.title,
                            urlOrMagnet = resolved.streamUrl,
                            quality = resolved.resolution.ifBlank { "HD" },
                            headers = resolved.headers,
                            healthScore = ProviderHealthManager.getHealthScore(id),
                            capabilities = PlaybackCapabilities(supportsSeeking = true, supportsTrackSelection = true)
                        )
                        emit(listOf(candidate))
                        return@flow
                    } catch (e: Exception) {
                        // Fallback
                    }
                }
            }
        }

        // 2. Search locally saved Bunkr Repository if Context is available
        if (context != null) {
            try {
                val repository = BunkrRepository.getInstance(context)
                val allFiles = repository.allFiles.firstOrNull() ?: emptyList()
                val matched = allFiles.filter { file ->
                    file.title.contains(query, ignoreCase = true) ||
                            file.fileId.equals(query, ignoreCase = true) ||
                            file.sourceUrl.contains(query, ignoreCase = true)
                }.take(10)

                val candidates = mutableListOf<SourceCandidate>()
                for (file in matched) {
                    try {
                        val stream = repository.resolveStreamForFile(file)
                        candidates.add(
                            SourceCandidate(
                                id = "bunkr_${file.fileId}",
                                providerId = id,
                                providerName = "Bunkr",
                                serverName = "Bunkr (${file.fileSize.ifBlank { "CDN" }})",
                                type = if (stream.mimeType.contains("mpegURL")) SourceStreamType.HLS else SourceStreamType.DIRECT,
                                title = file.title,
                                urlOrMagnet = stream.streamUrl,
                                quality = file.resolution.ifBlank { "HD" },
                                headers = stream.headers,
                                healthScore = ProviderHealthManager.getHealthScore(id),
                                capabilities = PlaybackCapabilities(supportsSeeking = true, supportsTrackSelection = true)
                            )
                        )
                    } catch (e: Exception) {
                        // Ignore individual resolution failures in batch search
                    }
                }
                if (candidates.isNotEmpty()) {
                    emit(candidates)
                    return@flow
                }
            } catch (e: Exception) {
                // Ignore repo exceptions
            }
        }

        emit(emptyList())
    }
}
