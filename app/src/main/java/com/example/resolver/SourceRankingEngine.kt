package com.example.resolver

import com.example.model.StreamResult
import com.example.resolver.health.ProviderHealthManager

/**
 * Intelligent sorting & ranking engine for all unified media sources.
 * Balances instant playback availability, resolution, bitrate, provider health scores,
 * failure risk penalties, audio fidelity, HDR, and swarm health.
 *
 * Core Principle:
 * "A healthy 720p/1080p source with 99% success rate > 4K source with 90% failure rate".
 */
object SourceRankingEngine {

    fun rank(candidates: List<SourceCandidate>): List<SourceCandidate> {
        return candidates
            .filter { it.isPlayable }
            .sortedWith(
                compareByDescending<SourceCandidate> { calculateCompositeScore(it) }
                    .thenByDescending { it.qualityScore }
                    .thenByDescending { it.seeders }
            )
    }

    fun rankStreams(streams: List<StreamResult>): List<StreamResult> {
        return streams
            .filter { it.isPlayable }
            .map { stream ->
                val score = calculateStreamScore(stream)
                stream.copy(rankingScore = score)
            }
            .sortedWith(
                compareByDescending<StreamResult> { it.rankingScore }
                    .thenByDescending { it.qualityScore }
                    .thenByDescending { it.seeders }
            )
    }

    fun calculateCompositeScore(candidate: SourceCandidate): Int {
        var score = 0

        // 1. Availability / Stream Type weight
        when (candidate.type) {
            SourceStreamType.DIRECT -> score += 650 // Instant playback
            SourceStreamType.HLS -> score += 620   // Adaptive bitrate streaming
            SourceStreamType.DASH -> score += 580
            SourceStreamType.TORRENT -> {
                score += when {
                    candidate.seeders >= 100 -> 600
                    candidate.seeders >= 50 -> 550
                    candidate.seeders >= 20 -> 480
                    candidate.seeders >= 5 -> 380
                    candidate.seeders > 0 -> 220
                    else -> 40 // 0 seeders
                }
            }
            SourceStreamType.LOCAL -> score += 2000 // Cached locally
        }

        // 2. Base Quality / Resolution Score
        score += candidate.qualityScore

        // 3. Bitrate estimation / size scoring
        if (candidate.sizeBytes > 0) {
            val sizeMb = candidate.sizeBytes / (1024 * 1024)
            when {
                sizeMb in 1500..8000 -> score += 100 // Sweet spot for high quality 1080p/4K
                sizeMb > 8000 -> score += 60         // Very large remux
                sizeMb in 500..1499 -> score += 80   // Standard webrip/720p
                sizeMb < 500 && sizeMb > 100 -> score += 30
            }
        }

        // 4. Live Provider Health Score (0-100) & Latency
        val liveHealth = ProviderHealthManager.getHealthScore(candidate.providerId)
        val stats = ProviderHealthManager.getStats(candidate.providerId)
        val effectiveHealth = if (candidate.healthScore > 0) {
            (liveHealth * 2 + candidate.healthScore) / 3
        } else {
            liveHealth
        }

        // Health weight multiplier: High health yields up to +1500 points
        score += (effectiveHealth * 15)

        // Latency bonus / penalty
        if (stats.averageLatencyMs > 0) {
            when {
                stats.averageLatencyMs < 400 -> score += 120
                stats.averageLatencyMs < 1000 -> score += 60
                stats.averageLatencyMs > 3000 -> score -= 200
                stats.averageLatencyMs > 2000 -> score -= 100
            }
        }

        // Severe penalty for quarantined or failing providers
        val isQuarantined = ProviderHealthManager.isQuarantined(candidate.providerId)
        if (isQuarantined) {
            score -= 3000
        } else if (effectiveHealth < 40) {
            score -= 1400
        } else if (effectiveHealth < 60) {
            score -= 600
        }

        // 5. Codec Bonus
        val titleUpper = candidate.title.uppercase()
        when {
            titleUpper.contains("AV1") -> score += 120
            titleUpper.contains("HEVC") || titleUpper.contains("X265") || titleUpper.contains("H265") || titleUpper.contains("H.265") -> score += 90
            titleUpper.contains("X264") || titleUpper.contains("H264") || titleUpper.contains("H.264") || candidate.format.equals("mp4", ignoreCase = true) -> score += 50
        }

        // 6. HDR / High Dynamic Range & Audio Fidelity
        when {
            titleUpper.contains("DV") || titleUpper.contains("DOLBY VISION") -> score += 90
            titleUpper.contains("HDR10+") -> score += 85
            titleUpper.contains("HDR") -> score += 70
        }

        if (titleUpper.contains("ATMOS") || titleUpper.contains("DDP5.1") || titleUpper.contains("5.1") || titleUpper.contains("7.1")) {
            score += 60
        }

        // 7. Subtitles & Audio Multi-Track
        if (candidate.subtitleUrls.isNotEmpty() || titleUpper.contains("MULTI-SUB") || titleUpper.contains("SUBBED")) {
            score += 80
        }

        if (candidate.audioTracks.isNotEmpty() || titleUpper.contains("DUAL AUDIO") || titleUpper.contains("MULTI AUDIO")) {
            score += 50
        }

        return score
    }

    fun calculateStreamScore(stream: StreamResult): Int {
        var score = 0

        // 1. Availability / Stream Type weight
        when (stream.streamType) {
            SourceStreamType.DIRECT -> score += 650
            SourceStreamType.HLS -> score += 620
            SourceStreamType.DASH -> score += 580
            SourceStreamType.TORRENT -> {
                score += when {
                    stream.seeders >= 100 -> 600
                    stream.seeders >= 50 -> 550
                    stream.seeders >= 20 -> 480
                    stream.seeders >= 5 -> 380
                    stream.seeders > 0 -> 220
                    else -> 40
                }
            }
            SourceStreamType.LOCAL -> score += 2000
        }

        // 2. Base Quality Score
        score += stream.qualityScore

        // 3. Bitrate
        if (stream.bitrateBps > 0) {
            val mbps = stream.bitrateBps / (1000 * 1000)
            score += (mbps * 10).coerceAtMost(200).toInt()
        }

        // 4. Live Provider Health
        val liveHealth = ProviderHealthManager.getHealthScore(stream.providerId)
        val stats = ProviderHealthManager.getStats(stream.providerId)
        val effectiveHealth = (liveHealth * 2 + stream.healthScore) / 3
        score += (effectiveHealth * 15)

        if (stats.averageLatencyMs > 0) {
            when {
                stats.averageLatencyMs < 400 -> score += 120
                stats.averageLatencyMs < 1000 -> score += 60
                stats.averageLatencyMs > 3000 -> score -= 200
            }
        }

        if (ProviderHealthManager.isQuarantined(stream.providerId)) {
            score -= 3000
        } else if (effectiveHealth < 40) {
            score -= 1400
        } else if (effectiveHealth < 60) {
            score -= 600
        }

        // 5. Codec & Format
        val titleUpper = stream.title.uppercase()
        when {
            titleUpper.contains("AV1") -> score += 120
            titleUpper.contains("HEVC") || titleUpper.contains("X265") || titleUpper.contains("H.265") -> score += 90
            stream.format.equals("mp4", ignoreCase = true) || stream.format.equals("m3u8", ignoreCase = true) -> score += 50
        }

        // 6. HDR & Subtitles
        if (titleUpper.contains("HDR") || titleUpper.contains("DV")) score += 75
        if (stream.subtitleUrls.isNotEmpty()) score += 80
        if (stream.audioTracks.isNotEmpty() || titleUpper.contains("DUAL")) score += 50

        return score
    }
}
