package com.example.resolver.embed

import com.example.model.MediaType

class VidsrcMeEmbedProvider : EmbedProvider {
    override val id: String = "vidsrc_me"
    override val displayName: String = "VidSrc.me"
    override val supportedMediaTypes: Set<MediaType> = setOf(MediaType.MOVIE, MediaType.TV)
    override val isEnabled: Boolean = true
    override val priority: Int = 100

    override var healthStatus: EmbedProviderHealth = EmbedProviderHealth.AVAILABLE

    override fun buildMovieUrl(tmdbId: String?, imdbId: String?, title: String?, year: Int?): String? {
        val cleanTmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } }
        if (cleanTmdb != null) {
            return "https://vidsrc.me/embed/movie?tmdb=$cleanTmdb"
        }
        val cleanImdb = imdbId?.trim()?.takeIf { it.isNotBlank() && it.startsWith("tt") }
        if (cleanImdb != null) {
            return "https://vidsrc.me/embed/movie?imdb=$cleanImdb"
        }
        return null
    }

    override fun buildEpisodeUrl(tmdbId: String?, imdbId: String?, season: Int, episode: Int, title: String?): String? {
        if (season <= 0 || episode <= 0) return null
        val cleanTmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() && it.all { char -> char.isDigit() } }
        if (cleanTmdb != null) {
            return "https://vidsrc.me/embed/tv?tmdb=$cleanTmdb&season=$season&episode=$episode"
        }
        val cleanImdb = imdbId?.trim()?.takeIf { it.isNotBlank() && it.startsWith("tt") }
        if (cleanImdb != null) {
            return "https://vidsrc.me/embed/tv?imdb=$cleanImdb&season=$season&episode=$episode"
        }
        return null
    }
}
