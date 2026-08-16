package com.example.model

data class CastMember(
    val name: String,
    val role: String? = null,
    val avatarUrl: String? = null,
    val personId: Int? = null
)

data class CastFilmographyItem(
    val id: String,
    val title: String,
    val mediaType: String, // "movie" or "tv"
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseYear: String = "2024",
    val character: String = "",
    val voteAverage: Double = 8.0
)

data class EpisodeItem(
    val id: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val durationText: String = "",
    val thumbnailUrl: String? = null,
    val providerId: String? = null,
    val viewsText: String = ""
)

data class SeriesSeason(
    val seasonNumber: Int,
    val seasonName: String,
    val episodes: List<EpisodeItem>
)

data class VideoComment(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val commentText: String,
    val timeAgo: String = "2 hours ago",
    val likeCount: Int = 0,
    val dislikeCount: Int = 0,
    val isLikedByMe: Boolean = false,
    val isDislikedByMe: Boolean = false,
    val rating: Float? = null,
    val ratingText: String? = null,
    val reviewTitle: String? = null,
    val summary: String? = null,
    val isSpoiler: Boolean = false,
    val totalReviewsCountText: String? = null,
    val sourceBadge: String? = null,
    val reviewUrl: String? = null
)

data class VideoTrailerClip(
    val title: String,
    val youtubeKey: String? = null,
    val embedUrl: String? = null,
    val thumbnailUrl: String? = null,
    val durationText: String = "",
    val clipType: String = "Trailer"
)

data class MediaDetailInfo(
    val title: String,
    val plotOverview: String,
    val releaseDateFormatted: String,
    val ratingText: String = "★ 8.8 / 10 TMDB",
    val director: String = "Feature Film Director",
    val writer: String = "Screenwriter",
    val studioOrCollection: String = "Sunrise / Bandai Namco",
    val genres: List<String> = listOf("Action", "Adventure", "Comedy", "Drama"),
    val cast: List<CastMember> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val clipsAndTrailers: List<VideoTrailerClip> = emptyList(),
    val budget: String? = null,
    val revenue: String? = null,
    val boxOffice: String? = null,
    val status: String? = null
)
