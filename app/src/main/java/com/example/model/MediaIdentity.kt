package com.example.model

enum class MediaType {
    MOVIE,
    TV,
    ANIME,
    VIDEO,
    UNKNOWN
}

data class MediaIdentity(
    val title: String = "",
    val year: String? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val anilistId: String? = null,
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
     * Formats ID for Stremio/Torrentio/Comet/MediaFusion requests:
     * - Movies: "tt1234567" or "tmdb:969681"
     * - TV: "tt1234567:1:1" or "tmdb:969681:1:1"
     */
    fun toStremioImdbId(): String? {
        if (hasValidImdbId) {
            val baseImdb = imdbId!!
            return if (mediaType == MediaType.TV && season != null && episode != null) {
                "$baseImdb:$season:$episode"
            } else if (mediaType == MediaType.TV) {
                "$baseImdb:1:1"
            } else {
                baseImdb
            }
        }
        if (!tmdbId.isNullOrBlank()) {
            val s = season ?: 1
            val e = episode ?: 1
            return if (mediaType == MediaType.TV) {
                "tmdb:$tmdbId:$s:$e"
            } else {
                "tmdb:$tmdbId"
            }
        }
        return null
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
