package com.example.resolver.providers

import com.example.model.MediaIdentity
import com.example.model.MediaType
import com.example.resolver.ProviderCapability
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceProvider
import com.example.resolver.SourceStreamType
import com.example.resolver.embed.EmbedProvider
import com.example.resolver.embed.EmbedProviderHealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Universal adapter integrating isolated [EmbedProvider] instances into Butterfly's
 * provider registration and aggregation pipelines.
 */
class EmbedSourceProviderAdapter(
    val embedProvider: EmbedProvider
) : SourceProvider {

    override val id: String = embedProvider.id
    override val displayName: String = embedProvider.displayName
    override val isEnabled: Boolean
        get() = embedProvider.isEnabled && embedProvider.healthStatus != EmbedProviderHealth.DISABLED
    override val priority: Int = embedProvider.priority

    override val capabilities: Set<ProviderCapability> = setOf(ProviderCapability.STREAM)
    override val supportedMediaTypes: Set<MediaType> = embedProvider.supportedMediaTypes

    override fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>> = flow {
        if (!isEnabled || embedProvider.healthStatus == EmbedProviderHealth.UNAVAILABLE || embedProvider.healthStatus == EmbedProviderHealth.ERROR) {
            emit(emptyList())
            return@flow
        }

        val embedUrl = if (identity.mediaType == MediaType.TV || (identity.season != null && identity.season > 0)) {
            embedProvider.buildEpisodeUrl(
                tmdbId = identity.tmdbId,
                imdbId = identity.imdbId,
                season = identity.season ?: 1,
                episode = identity.episode ?: 1,
                title = identity.title
            )
        } else {
            embedProvider.buildMovieUrl(
                tmdbId = identity.tmdbId,
                imdbId = identity.imdbId,
                title = identity.title,
                year = identity.year?.toIntOrNull()
            )
        }

        if (embedUrl.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }

        val candidate = SourceCandidate(
            id = "${id}_${identity.tmdbId ?: identity.imdbId ?: identity.title.hashCode()}",
            providerId = id,
            providerName = displayName,
            serverName = "$displayName (Embed Player)",
            type = SourceStreamType.EMBED_WEBVIEW,
            title = identity.title,
            urlOrMagnet = embedUrl,
            quality = "Auto HD",
            qualityScore = 1080,
            format = "embed",
            sizeBytes = 0L,
            formattedSize = "Web Embed",
            healthScore = 100,
            isPlayable = true,
            extraData = mapOf(
                "embedProvider" to id,
                "allowPopups" to "false"
            )
        )

        emit(listOf(candidate))
    }
}
