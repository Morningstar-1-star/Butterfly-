package com.example.resolver

import com.example.model.MediaIdentity
import com.example.model.MediaType
import kotlinx.coroutines.flow.Flow

/**
 * Universal interface for all content sources (Vega scrapers, Direct HTTP, Torrent swarms, etc.).
 */
interface SourceProvider {
    val id: String
    val displayName: String
    val isEnabled: Boolean
    val priority: Int // Higher = prioritized

    val capabilities: Set<ProviderCapability>
        get() = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM)

    val supportedMediaTypes: Set<MediaType>
        get() = setOf(MediaType.MOVIE, MediaType.TV, MediaType.ANIME, MediaType.JAV, MediaType.VIDEO, MediaType.UNKNOWN)

    val timeoutMs: Long
        get() = 12000L

    val maxConcurrentRequests: Int
        get() = 3

    /**
     * Resolves stream candidates matching the provided media identity.
     * Emits progressively as candidate servers are extracted.
     */
    fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>>
}

