package com.example.resolver.embed

import com.example.model.MediaType

enum class EmbedProviderHealth {
    AVAILABLE,
    UNAVAILABLE,
    TIMEOUT,
    ERROR,
    DISABLED
}

/**
 * Isolated abstraction for authorized external embed providers.
 * Generates valid embed URLs for Movies and TV Episodes without mixing with direct ExoPlayer streams.
 */
interface EmbedProvider {
    val id: String
    val displayName: String
    val supportedMediaTypes: Set<MediaType>
        get() = setOf(MediaType.MOVIE, MediaType.TV)
    val isEnabled: Boolean
        get() = true
    val priority: Int
        get() = 100

    var healthStatus: EmbedProviderHealth

    fun buildMovieUrl(tmdbId: String?, imdbId: String?, title: String?, year: Int?): String?
    fun buildEpisodeUrl(tmdbId: String?, imdbId: String?, season: Int, episode: Int, title: String?): String?
}
