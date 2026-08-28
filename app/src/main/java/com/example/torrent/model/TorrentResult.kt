package com.example.torrent.model

import com.example.torrent.protocol.MagnetParser
import java.util.Locale

/**
 * Normalized Torrent Result schema shared across all torrent providers and indexers.
 * Guarantees a consistent data structure: title, magnet, infoHash, size, seeders, leechers, source, category.
 */
data class TorrentResult(
    val title: String,
    val magnet: String,
    val infoHash: String,
    val size: Long = 0L,
    val formattedSize: String = "",
    val seeders: Int = 0,
    val leechers: Int = 0,
    val source: String,
    val category: String = "Other", // "Movies", "TV", "Anime", "JAV/Adult", "Documentary", "Other"
    val quality: String = "1080p",
    val codec: String = "",
    val hdr: String = "",
    val audioChannels: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val uploadDate: String? = null,
    val isVerified: Boolean = true,
    val trackerUrls: List<String> = emptyList()
) {
    val qualityScore: Int
        get() {
            var score = when {
                quality.contains("2160p", ignoreCase = true) || quality.contains("4K", ignoreCase = true) -> 400
                quality.contains("1080p", ignoreCase = true) -> 300
                quality.contains("720p", ignoreCase = true) -> 200
                quality.contains("480p", ignoreCase = true) -> 100
                else -> 150
            }
            if (codec.contains("265", ignoreCase = true) || codec.contains("hevc", ignoreCase = true) || codec.contains("av1", ignoreCase = true)) {
                score += 50
            }
            if (hdr.isNotBlank()) score += 30
            if (audioChannels.contains("5.1") || audioChannels.contains("7.1") || audioChannels.contains("Atmos")) score += 20
            score += minOf(seeders, 150)
            if (isVerified) score += 25
            return score
        }

    fun toTorrentRelease(): TorrentRelease {
        val effectiveFormattedSize = if (formattedSize.isNotBlank()) {
            formattedSize
        } else {
            formatBytes(size)
        }

        val effectiveMagnet = if (magnet.isNotBlank()) {
            magnet
        } else if (infoHash.isNotBlank()) {
            MagnetParser.buildMagnetUrl(infoHash, title, trackerUrls.ifEmpty { MagnetParser.DEFAULT_TRACKERS })
        } else {
            ""
        }

        return TorrentRelease(
            title = title,
            infoHash = infoHash.lowercase().trim(),
            magnetUrl = effectiveMagnet,
            provider = source,
            seeders = seeders,
            leechers = leechers,
            sizeBytes = size,
            formattedSize = effectiveFormattedSize,
            quality = quality,
            codec = codec,
            hdr = hdr,
            audioChannels = audioChannels,
            season = season,
            episode = episode,
            uploadDate = uploadDate,
            trackerUrls = trackerUrls,
            isVerified = isVerified
        )
    }

    companion object {
        fun fromRelease(release: TorrentRelease, category: String = "Other"): TorrentResult {
            return TorrentResult(
                title = release.title,
                magnet = release.magnetUrl,
                infoHash = release.infoHash.lowercase().trim(),
                size = release.sizeBytes,
                formattedSize = release.formattedSize,
                seeders = release.seeders,
                leechers = release.leechers,
                source = release.provider,
                category = category,
                quality = release.quality,
                codec = release.codec,
                hdr = release.hdr,
                audioChannels = release.audioChannels,
                season = release.season,
                episode = release.episode,
                uploadDate = release.uploadDate,
                isVerified = release.isVerified,
                trackerUrls = release.trackerUrls
            )
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return ""
            val gb = bytes / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.2f GB", gb)
            } else {
                val mb = bytes / (1024.0 * 1024.0)
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
    }
}
