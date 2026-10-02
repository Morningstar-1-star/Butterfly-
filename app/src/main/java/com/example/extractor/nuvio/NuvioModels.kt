package com.example.extractor.nuvio

import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.resolver.PlaybackCapabilities
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceStreamType
import com.example.resolver.StreamHealthStatus

data class NuvioSubtitle(
    val url: String,
    val language: String,
    val name: String,
    val headers: Map<String, String> = emptyMap()
)

data class NuvioStreamResult(
    val name: String,
    val title: String,
    val url: String,
    val quality: String = "1080p",
    val size: Long = 0L,
    val formattedSize: String = "",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<NuvioSubtitle> = emptyList(),
    val isHls: Boolean = false,
    val seeders: Int = -1,
    val providerId: String = ""
) {
    fun toPlayableStreamOption(providerType: ProviderType = ProviderType.NUVIO): PlayableStreamOption {
        val cleanQuality = if (quality.isNotBlank()) quality else "1080p"
        val cleanFormat = if (isHls || url.contains(".m3u8", ignoreCase = true)) "m3u8" else if (url.contains(".mkv", ignoreCase = true)) "mkv" else "mp4"
        val captionOptions = subtitles.map {
            CaptionOption(
                languageName = it.name.ifBlank { "English" },
                languageCode = it.language.ifBlank { "en" },
                format = "vtt",
                url = it.url
            )
        }
        return PlayableStreamOption(
            qualityLabel = if (formattedSize.isNotBlank()) "$cleanQuality ($formattedSize)" else cleanQuality,
            format = cleanFormat,
            isMuxed = true,
            videoUrl = url,
            audioUrl = null,
            providerType = providerType,
            headers = headers,
            audioHeaders = emptyMap(),
            sourceName = if (name.isNotBlank()) name else "Nuvio",
            qualityCategory = cleanQuality,
            sizeText = formattedSize,
            seeders = seeders,
            subtitles = captionOptions,
            serverStatus = "Online"
        )
    }

    fun toSourceCandidate(): SourceCandidate {
        val streamType = when {
            isHls || url.contains(".m3u8", ignoreCase = true) -> SourceStreamType.HLS
            url.startsWith("magnet:", ignoreCase = true) -> SourceStreamType.TORRENT
            else -> SourceStreamType.DIRECT
        }
        return SourceCandidate(
            id = "nuvio_${providerId}_${System.currentTimeMillis()}_${(0..9999).random()}",
            providerId = if (providerId.isNotBlank()) providerId else "nuvio",
            providerName = if (name.isNotBlank()) name else "Nuvio",
            serverName = name,
            type = streamType,
            title = title,
            urlOrMagnet = url,
            quality = quality,
            qualityScore = when {
                quality.contains("4k", ignoreCase = true) || quality.contains("2160", ignoreCase = true) -> 2160
                quality.contains("1080", ignoreCase = true) -> 1080
                quality.contains("720", ignoreCase = true) -> 720
                else -> 480
            },
            format = if (isHls) "m3u8" else "mp4",
            sizeBytes = size,
            formattedSize = formattedSize,
            seeders = seeders.coerceAtLeast(0),
            headers = headers,
            subtitleUrls = subtitles.map { it.url },
            healthScore = 95,
            healthStatus = StreamHealthStatus.RESOLVED,
            isPlayable = true,
            capabilities = PlaybackCapabilities(
                supportsSeeking = true,
                supportsRangeSeeking = true,
                supportsTrackSelection = true
            )
        )
    }
}

data class NuvioScraperManifestItem(
    val id: String,
    val name: String,
    val description: String,
    val version: String = "1.0.0",
    val author: String = "Nuvio Team",
    val supportedTypes: List<String> = listOf("movie", "tv"),
    val filename: String = "",
    val enabled: Boolean = true,
    val formats: List<String> = listOf("mp4", "m3u8"),
    val logo: String = "",
    val contentLanguage: List<String> = listOf("en"),
    val hasSettings: Boolean = false,
    val notes: String? = null,
    val priority: Int = 50
)

data class InstalledNuvioProvider(
    val id: String,
    val name: String,
    val description: String,
    val version: String = "1.0.0",
    val author: String = "Nuvio Team",
    val isEnabled: Boolean = true,
    val isInstalled: Boolean = true,
    val supportedTypes: List<String> = listOf("movie", "tv"),
    val formats: List<String> = listOf("mp4", "m3u8"),
    val contentLanguage: List<String> = listOf("en"),
    val logo: String = "",
    val priority: Int = 50,
    val status: String = "Active",
    val lastError: String? = null,
    val installedAtMs: Long = System.currentTimeMillis()
)
