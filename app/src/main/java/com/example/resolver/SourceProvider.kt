package com.example.resolver

import com.example.model.MediaIdentity
import kotlinx.coroutines.flow.Flow

/**
 * Universal interface for all content sources (Vega scrapers, Torrent swarms, etc.).
 */
interface SourceProvider {
    val id: String
    val displayName: String
    val isEnabled: Boolean
    val priority: Int // Higher = prioritized

    /**
     * Resolves stream candidates matching the provided media identity.
     * Emits progressively as candidate servers are extracted.
     */
    fun searchSources(identity: MediaIdentity): Flow<List<SourceCandidate>>
}
