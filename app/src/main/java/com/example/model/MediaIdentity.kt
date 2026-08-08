package com.example.model

enum class MediaType {
    MOVIE,
    TV,
    ANIME,
    VIDEO,
    UNKNOWN
}

data class MediaIdentity(
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val season: Int? = null,
    val episode: Int? = null,
    val rawQueryOrUrl: String = ""
) {
    /**
     * True if a valid IMDb ID (tt...) is present.
     */
    val hasValidImdbId: Boolean
        get() = !imdbId.isNullOrBlank() && imdbId.startsWith("tt") && imdbId.length >= 7 && !imdbId.contains("0000000")

    /**
     * Formats IMDb ID for Stremio/Torrentio/Comet/MediaFusion requests:
     * - Movies: "tt1234567"
     * - TV: "tt1234567:1:1"
     * Returns null if no valid IMDb ID exists (never invents or fabricates fake IDs).
     */
    fun toStremioImdbId(): String? {
        if (!hasValidImdbId) return null
        val baseImdb = imdbId!!
        return if (mediaType == MediaType.TV && season != null && episode != null) {
            "$baseImdb:$season:$episode"
        } else {
            baseImdb
        }
    }

    /**
     * Formats TMDB ID for TMDB-native providers:
     * - Movie: "550"
     * - TV: "1399" or "tv_1399_1_2"
     */
    fun toTmdbId(): String? {
        if (tmdbId.isNullOrBlank()) return null
        return tmdbId
    }

    /**
     * Formats TV TMDB string e.g. "tv_1399_1_2"
     */
    fun toTmdbTvString(): String? {
        if (tmdbId.isNullOrBlank()) return null
        return if (mediaType == MediaType.TV && season != null && episode != null) {
            "tv_${tmdbId}_${season}_${episode}"
        } else if (mediaType == MediaType.TV) {
            "tv_${tmdbId}"
        } else {
            tmdbId
        }
    }
}
