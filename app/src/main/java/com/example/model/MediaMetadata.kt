package com.example.model

/**
 * Standard unified media metadata representation across Butterfly media engine.
 * Normalizes Movies, TV Shows, Anime, JAV, Web Videos, and Live Streams into
 * a single structured model.
 */
data class MediaMetadata(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val overview: String? = null,
    val releaseDate: String? = null,
    val year: String? = null,
    val durationMinutes: Int? = null,
    val durationSeconds: Long? = null,
    val rating: Float? = null,
    val ratingText: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val thumbnailUrls: List<String> = emptyList(),
    val previewThumbnails: List<String> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val director: String? = null,
    val writer: String? = null,
    val studio: String? = null,
    val label: String? = null,
    val series: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val trailers: List<VideoTrailerClip> = emptyList(),
    val sampleVideoUrl: String? = null,
    val providerSource: String = "",
    val detailUrl: String? = null,
    val externalIds: Map<String, String> = emptyMap(),
    val extraData: Map<String, String> = emptyMap()
) {
    val formattedYear: String
        get() = year ?: releaseDate?.take(4) ?: ""

    val primaryImageUrl: String?
        get() = posterUrl ?: thumbnailUrls.firstOrNull() ?: backdropUrl

    fun toMediaIdentity(): MediaIdentity {
        return MediaIdentity(
            title = title,
            year = year ?: releaseDate?.take(4),
            tmdbId = externalIds["tmdb"],
            imdbId = externalIds["imdb"],
            anilistId = externalIds["anilist"],
            mediaType = mediaType,
            season = seasonNumber,
            episode = episodeNumber,
            rawQueryOrUrl = detailUrl ?: id
        )
    }

    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = id,
            title = title,
            uploaderName = studio ?: director ?: providerSource.ifBlank { "Butterfly Media" },
            uploaderUrl = detailUrl,
            uploaderAvatarUrl = cast.firstOrNull()?.avatarUrl,
            durationSeconds = durationSeconds ?: ((durationMinutes ?: 0) * 60L),
            uploadDate = releaseDate ?: year,
            thumbnailUrl = primaryImageUrl,
            providerId = providerSource.lowercase(),
            tags = tags.ifEmpty { genres },
            description = overview,
            previewThumbnails = previewThumbnails,
            previewClipUrl = sampleVideoUrl
        )
    }
}
