package com.example.model

enum class ExploreMediaType(val label: String) {
    ALL("All"),
    MOVIE("Movie"),
    TV("TV Series"),
    ANIME("Anime")
}

enum class ExploreSource(val label: String) {
    TMDB("TMDB"),
    IMDB("IMDb"),
    ANILIST("AniList"),
    JIKAN("MyAnimeList")
}

data class MediaClipItem(
    val id: String,
    val name: String,
    val type: String = "Trailer", // Trailer, Teaser, Clip, Featurette, Opening, Ending
    val site: String = "YouTube",
    val key: String,
    val thumbnailUrl: String? = null
)

data class MediaReviewItem(
    val id: String,
    val author: String,
    val authorAvatarUrl: String? = null,
    val rating: Double? = null,
    val content: String,
    val createdAt: String? = null,
    val source: String = "IMDb / TMDB"
)

data class ExploreMediaItem(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val mediaType: ExploreMediaType = ExploreMediaType.MOVIE,
    val source: ExploreSource = ExploreSource.TMDB,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Double = 0.0,
    val ratingSource: String = "TMDB",
    val releaseYear: String = "",
    val genres: List<String> = emptyList(),
    val overview: String = "",
    val episodesCount: Int? = null,
    val status: String? = null,
    val studio: String? = null,
    val trailerYoutubeId: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val isSaved: Boolean = false,
    val cast: List<CastMember> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val clipsAndTrailers: List<MediaClipItem> = emptyList(),
    val reviews: List<MediaReviewItem> = emptyList(),
    val relatedContent: List<ExploreMediaItem> = emptyList(),
    val runtimeText: String? = null,
    val director: String? = null,
    val tagline: String? = null
) {
    val displayRating: String
        get() = if (rating > 0.0) String.format("%.1f", rating) else "N/A"

    val typeBadge: String
        get() = when (mediaType) {
            ExploreMediaType.MOVIE -> "Movie"
            ExploreMediaType.TV -> if (episodesCount != null && episodesCount > 0) "$episodesCount Eps" else "TV Series"
            ExploreMediaType.ANIME -> if (episodesCount != null && episodesCount > 0) "Anime • $episodesCount Eps" else "Anime"
            else -> "Media"
        }
}

data class ExploreSection(
    val title: String,
    val subtitle: String? = null,
    val iconName: String = "trending",
    val items: List<ExploreMediaItem> = emptyList()
)
