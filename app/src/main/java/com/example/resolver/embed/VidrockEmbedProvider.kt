package com.example.resolver.embed

import com.example.model.MediaType

/**
 * Embed provider for VidRock (https://vidrock.net)
 * Supports movies and TV series by TMDB or IMDB ID.
 */
class VidrockEmbedProvider : EmbedProvider {
    override val id: String = "vidrock"
    override val displayName: String = "VidRock"
    override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.MOVIE, MediaType.TV)
    override val isEnabled: Boolean = true
    override val priority: Int = 115

    override var healthStatus: EmbedProviderHealth = EmbedProviderHealth.AVAILABLE

    override fun buildMovieUrl(tmdbId: String?, imdbId: String?, title: String?, year: Int?): String? {
        val cleanTmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } }
        if (cleanTmdb != null) {
            return "https://vidrock.net/movie/$cleanTmdb"
        }
        val cleanImdb = imdbId?.trim()?.takeIf { it.isNotBlank() && it.startsWith("tt") }
        if (cleanImdb != null) {
            return "https://vidrock.net/movie/$cleanImdb"
        }
        return null
    }

    override fun buildEpisodeUrl(tmdbId: String?, imdbId: String?, season: Int, episode: Int, title: String?): String? {
        val s = if (season <= 0) 1 else season
        val ep = if (episode <= 0) 1 else episode
        val cleanTmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } }
        if (cleanTmdb != null) {
            return "https://vidrock.net/tv/$cleanTmdb/$s/$ep"
        }
        val cleanImdb = imdbId?.trim()?.takeIf { it.isNotBlank() && it.startsWith("tt") }
        if (cleanImdb != null) {
            return "https://vidrock.net/tv/$cleanImdb/$s/$ep"
        }
        return null
    }
}
