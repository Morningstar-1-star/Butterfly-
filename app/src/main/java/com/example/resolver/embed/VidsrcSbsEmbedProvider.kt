package com.example.resolver.embed

import com.example.model.MediaType

/**
 * Embed provider for VidSrc.sbs (https://vidsrc.sbs)
 * Supports movies and TV series by TMDB or IMDB ID.
 */
class VidsrcSbsEmbedProvider : EmbedProvider {
    override val id: String = "vidsrc_sbs"
    override val displayName: String = "VidSrc"
    override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.MOVIE, MediaType.TV)
    override val isEnabled: Boolean = true
    override val priority: Int = 120

    override var healthStatus: EmbedProviderHealth = EmbedProviderHealth.AVAILABLE

    override fun buildMovieUrl(tmdbId: String?, imdbId: String?, title: String?, year: Int?): String? {
        val cleanTmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } }
        if (cleanTmdb != null) {
            return "https://vidsrc.sbs/embed/movie/$cleanTmdb"
        }
        val cleanImdb = imdbId?.trim()?.takeIf { it.isNotBlank() && it.startsWith("tt") }
        if (cleanImdb != null) {
            return "https://vidsrc.sbs/embed/movie/$cleanImdb"
        }
        return null
    }

    override fun buildEpisodeUrl(tmdbId: String?, imdbId: String?, season: Int, episode: Int, title: String?): String? {
        val s = if (season <= 0) 1 else season
        val ep = if (episode <= 0) 1 else episode
        val cleanTmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } }
        if (cleanTmdb != null) {
            return "https://vidsrc.sbs/embed/tv/$cleanTmdb/$s/$ep"
        }
        val cleanImdb = imdbId?.trim()?.takeIf { it.isNotBlank() && it.startsWith("tt") }
        if (cleanImdb != null) {
            return "https://vidsrc.sbs/embed/tv/$cleanImdb/$s/$ep"
        }
        return null
    }
}
