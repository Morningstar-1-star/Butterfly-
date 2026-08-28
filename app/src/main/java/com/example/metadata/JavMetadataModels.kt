package com.example.metadata

import com.example.model.CastMember
import com.example.model.MediaDetailInfo
import com.example.model.VideoTrailerClip

/**
 * Normalized Actor / Cast Information.
 */
data class JavActor(
    val name: String,
    val originalName: String? = null,
    val romajiName: String? = null,
    val avatarUrl: String? = null,
    val birthday: String? = null,
    val heightCm: Int? = null,
    val cupSize: String? = null,
    val bustCm: Int? = null,
    val waistCm: Int? = null,
    val hipCm: Int? = null,
    val bloodType: String? = null,
    val birthplace: String? = null,
    val aliases: List<String> = emptyList()
) {
    fun toCastMember(): CastMember {
        val roleDesc = if (!cupSize.isNullOrBlank()) "Cup: $cupSize" else "Cast"
        return CastMember(
            name = name,
            role = roleDesc,
            avatarUrl = avatarUrl,
            personId = null
        )
    }
}

/**
 * Normalized JAV Metadata representation across all metadata scrapers and providers.
 */
data class JavMetadata(
    val id: String,                         // Normalized Code e.g. "SSIS-001"
    val code: String = id,
    val title: String,
    val originalTitle: String? = null,
    val releaseDate: String? = null,        // "YYYY-MM-DD"
    val year: String? = null,
    val durationMinutes: Int? = null,
    val director: String? = null,
    val studio: String? = null,             // Maker / Production Studio
    val label: String? = null,              // Publisher / Label
    val series: String? = null,
    val genres: List<String> = emptyList(),
    val coverUrl: String? = null,           // High-res main poster
    val thumbUrl: String? = null,
    val previewImages: List<String> = emptyList(), // Sample screenshots
    val sampleVideoUrl: String? = null,     // Official sample trailer HLS / MP4
    val cast: List<JavActor> = emptyList(),
    val rating: Float? = null,              // 0..10 or 0..5
    val providerSource: String = "",
    val detailUrl: String? = null,
    val plotOverview: String? = null
) {
    fun toMediaDetailInfo(): MediaDetailInfo {
        val dateText = releaseDate ?: year ?: "Unknown"
        val ratingStr = if (rating != null && rating > 0f) "★ ${String.format("%.1f", rating)} / 5.0" else "★ 4.8 / 5.0"
        val clips = mutableListOf<VideoTrailerClip>()
        if (!sampleVideoUrl.isNullOrBlank()) {
            clips.add(
                VideoTrailerClip(
                    title = "$code Official Sample Preview",
                    embedUrl = sampleVideoUrl,
                    thumbnailUrl = coverUrl ?: thumbUrl,
                    durationText = "2:00",
                    clipType = "Sample Trailer"
                )
            )
        }

        return MediaDetailInfo(
            title = title,
            plotOverview = plotOverview ?: "[$code] $title",
            releaseDateFormatted = dateText,
            ratingText = ratingStr,
            director = director ?: "Unknown Director",
            writer = label ?: studio ?: "Unknown Studio",
            studioOrCollection = studio ?: label ?: "Production Studio",
            genres = genres.ifEmpty { listOf("JAV", "Drama") },
            cast = cast.map { it.toCastMember() },
            screenshots = previewImages,
            clipsAndTrailers = clips,
            status = "Released"
        )
    }
}
