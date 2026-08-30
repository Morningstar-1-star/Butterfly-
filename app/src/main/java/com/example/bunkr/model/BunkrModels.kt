package com.example.bunkr.model

import com.example.model.VideoItem

enum class BunkrUrlType {
    ALBUM,
    FILE,
    UNKNOWN
}

data class BunkrUrlInfo(
    val rawUrl: String,
    val canonicalUrl: String,
    val type: BunkrUrlType,
    val id: String,
    val domain: String
)

data class BunkrAlbum(
    val albumId: String,
    val title: String,
    val sourceUrl: String,
    val isEnabled: Boolean = true,
    val lastScanTime: Long = 0L,
    val itemCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class BunkrFile(
    val fileId: String,
    val albumId: String,
    val title: String,
    val sourceUrl: String,
    val thumbnailUrl: String? = null,
    val mediaType: String = "video",
    val duration: String = "",
    val resolution: String = "",
    val fileSize: String = "",
    val streamUrl: String? = null,
    val streamUrlExpiry: Long = 0L,
    val isAvailable: Boolean = true,
    val orderIndex: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toVideoItem(albumTitle: String = "Bunkr Album"): VideoItem {
        val studio = if (albumTitle.isNotBlank()) "Bunkr • $albumTitle" else "Bunkr"
        return VideoItem(
            id = sourceUrl,
            title = title.ifBlank { "Bunkr Video $fileId" },
            uploaderName = studio,
            uploaderAvatarUrl = thumbnailUrl,
            thumbnailUrl = thumbnailUrl,
            providerId = "bunkr",
            description = "Bunkr Media Item • Album: $albumTitle • Size: $fileSize"
        )
    }
}

data class BunkrStreamResult(
    val source: String = "Bunkr",
    val fileId: String,
    val albumId: String,
    val title: String,
    val streamUrl: String,
    val thumbnailUrl: String? = null,
    val mimeType: String = "video/mp4",
    val headers: Map<String, String> = emptyMap(),
    val durationMs: Long = 0L,
    val resolution: String = ""
)

data class BunkrScanReport(
    val totalAlbumsProcessed: Int = 0,
    val totalItemsDiscovered: Int = 0,
    val playableCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val errors: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

sealed class BunkrException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidUrlException(url: String) : BunkrException("Invalid Bunkr URL: $url")
    class AlbumUnavailableException(albumId: String, reason: String = "") : BunkrException("Album unavailable ($albumId): $reason")
    class FileUnavailableException(fileId: String, reason: String = "") : BunkrException("File unavailable ($fileId): $reason")
    class ResolutionFailedException(fileId: String, reason: String = "") : BunkrException("Media URL resolution failed ($fileId): $reason")
    class NetworkTimeoutException(url: String) : BunkrException("Network timeout fetching Bunkr URL: $url")
    class RateLimitedException(domain: String) : BunkrException("Rate limited by Bunkr server ($domain)")
}
