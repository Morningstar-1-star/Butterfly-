package com.example.resolver.embed

import com.example.model.MediaType

class TwoEmbedProvider : EmbedProvider {
    override val id: String = "2embed"
    override val displayName: String = "2Embed"
    override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.MOVIE, MediaType.TV)
    override val isEnabled: Boolean = true
    override val priority: Int = 105

    override var healthStatus: EmbedProviderHealth = EmbedProviderHealth.AVAILABLE

    override fun buildMovieUrl(tmdbId: String?, imdbId: String?, title: String?, year: Int?): String? {
        val cleanTmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } }
            ?: imdbId?.trim()?.takeIf { it.isNotBlank() && it.startsWith("tt") }
            ?: return null
        return "https://www.2embed.cc/embed/$cleanTmdb"
    }

    override fun buildEpisodeUrl(tmdbId: String?, imdbId: String?, season: Int, episode: Int, title: String?): String? {
        val cleanTmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } }
            ?: imdbId?.trim()?.takeIf { it.isNotBlank() && it.startsWith("tt") }
            ?: return null
        if (season <= 0 || episode <= 0) return null
        return "https://www.2embed.cc/embedtv/$cleanTmdb&s=$season&e=$episode"
    }
}
