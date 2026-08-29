package com.example.resolver.embed

import com.example.model.MediaType

class VidlinkEmbedProvider : EmbedProvider {
    override val id: String = "vidlink_pro"
    override val displayName: String = "Vidlink Pro"
    override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.MOVIE, MediaType.TV)
    override val isEnabled: Boolean = true
    override val priority: Int = 120

    override var healthStatus: EmbedProviderHealth = EmbedProviderHealth.AVAILABLE

    override fun buildMovieUrl(tmdbId: String?, imdbId: String?, title: String?, year: Int?): String? {
        val cleanTmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } } ?: return null
        return "https://vidlink.pro/movie/$cleanTmdb"
    }

    override fun buildEpisodeUrl(tmdbId: String?, imdbId: String?, season: Int, episode: Int, title: String?): String? {
        val cleanTmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } } ?: return null
        if (season <= 0 || episode <= 0) return null
        return "https://vidlink.pro/tv/$cleanTmdb/$season/$episode"
    }
}
