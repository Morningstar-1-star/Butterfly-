package com.example.extractor.tmdbembed

import com.example.model.CaptionOption

data class TMDBMediaRequest(
    val tmdbId: String,
    val mediaType: String = "movie", // "movie" or "tv"
    val season: Int = 1,
    val episode: Int = 1,
    val title: String = "",
    val year: String = "",
    val imdbId: String? = null
) {
    val isTv: Boolean get() = mediaType.equals("tv", ignoreCase = true) || season > 0 && episode > 0
}

data class ExtractedStream(
    val title: String,
    val url: String,
    val quality: String = "1080p",
    val source: TMDBEmbedSource,
    val isHls: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<CaptionOption> = emptyList(),
    val sizeText: String = "",
    val seeders: Int = -1,
    val extraData: Map<String, String> = emptyMap()
)
