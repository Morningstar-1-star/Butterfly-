package com.example.plugin.jav

enum class ProviderStatusState {
    SUCCESS,
    TIMEOUT,
    BLOCKED,
    INVALID,
    NO_RESULT,
    AUTH_REQUIRED,
    ERROR
}

enum class ProviderCapabilityType {
    METADATA,
    STREAM,
    TRAILER,
    SUBTITLE
}

data class ProviderDiagnosticResult(
    val providerId: String,
    val providerName: String,
    val capability: ProviderCapabilityType,
    val status: ProviderStatusState,
    val responseTimeMs: Long,
    val detailMessage: String = "",
    val itemFoundCount: Int = 0
)

data class FieldWithConfidence<T>(
    val value: T,
    val providerId: String,
    val confidenceScore: Int // 0 to 100
)

data class JavMetadata(
    val javId: String, // e.g. "IPX-123"
    val title: String = "",
    val originalTitle: String = "",
    val releaseDate: String = "",
    val durationMins: Int = 0,
    val studio: String = "",
    val label: String = "",
    val director: String = "",
    val series: String = "",
    val maker: String = "",
    val coverUrl: String = "",
    val previewImages: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val description: String = "",
    val overallConfidenceScore: Int = 0,
    val providerScores: Map<String, Int> = emptyMap()
)

data class JavStream(
    val id: String,
    val javId: String,
    val url: String,
    val title: String,
    val qualityLabel: String = "1080p",
    val codec: String = "H.264",
    val mimeType: String = "video/mp4",
    val headers: Map<String, String> = emptyMap(),
    val durationSeconds: Long = 0L,
    val providerId: String,
    val providerName: String,
    val subtitles: List<JavSubtitle> = emptyList(),
    val isVerifiedPlayable: Boolean = true
)

data class JavTrailer(
    val id: String,
    val javId: String,
    val title: String,
    val videoUrl: String,
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0L,
    val providerId: String,
    val providerName: String
)

data class JavSubtitle(
    val id: String,
    val javId: String = "",
    val language: String,
    val languageCode: String = "en",
    val url: String,
    val format: String = "srt", // srt, vtt, ass
    val isHearingImpaired: Boolean = false,
    val providerId: String,
    val matchScore: Int = 100
)

data class JavUnifiedResult(
    val javId: String,
    val metadata: JavMetadata?,
    val streams: List<JavStream> = emptyList(),
    val trailers: List<JavTrailer> = emptyList(),
    val subtitles: List<JavSubtitle> = emptyList(),
    val diagnostics: List<ProviderDiagnosticResult> = emptyList()
)
