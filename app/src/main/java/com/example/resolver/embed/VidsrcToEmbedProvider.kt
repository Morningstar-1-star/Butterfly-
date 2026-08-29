package com.example.resolver.embed

import com.example.model.MediaType

class VidsrcToEmbedProvider : EmbedProvider {
    override val id: String = "vidsrc_to"
    override val displayName: String = "VidSrc.to"
    override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.MOVIE, MediaType.TV)
    override val isEnabled: Boolean = true
    override val priority: Int = 110

    override var healthStatus: EmbedProviderHealth = EmbedProviderHealth.AVAILABLE

    override fun buildMovieUrl(tmdbId: String?, imdbId: String?, title: String?, year: Int?): String? {
        val idToUse = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } }
            ?: imdbId?.trim()?.takeIf { it.isNotBlank() && it.startsWith("tt") }
            ?: return null
        return "https://vidsrc.to/embed/movie/$idToUse"
    }

    override fun buildEpisodeUrl(tmdbId: String?, imdbId: String?, season: Int, episode: Int, title: String?): String? {
        val idToUse = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } }
            ?: imdbId?.trim()?.takeIf { it.isNotBlank() && it.startsWith("tt") }
            ?: return null
        if (season <= 0 || episode <= 0) return null
        return "https://vidsrc.to/embed/tv/$idToUse/$season/$episode"
    }
}
