package com.example.extractor.tmdbembed

import com.example.model.CaptionOption
import org.json.JSONArray
import org.json.JSONObject

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

fun String?.toJsonObjectOrNull(): JSONObject? {
    if (this == null) return null
    val trimmed = this.trim()
    if (trimmed.isEmpty() || trimmed == "null" || !trimmed.startsWith("{") || trimmed.startsWith("<!")) return null
    return try {
        JSONObject(trimmed)
    } catch (_: Exception) {
        null
    }
}

fun String?.toJsonArrayOrNull(): JSONArray? {
    if (this == null) return null
    val trimmed = this.trim()
    if (trimmed.isEmpty() || trimmed == "null" || !trimmed.startsWith("[") || trimmed.startsWith("<!")) return null
    return try {
        JSONArray(trimmed)
    } catch (_: Exception) {
        null
    }
}

