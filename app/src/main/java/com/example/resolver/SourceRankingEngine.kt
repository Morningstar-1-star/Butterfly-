package com.example.resolver

/**
 * Intelligent sorting & ranking engine for all unified media sources.
 * Balances instant playback availability, resolution, bitrate, audio fidelity, and swarm health.
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

    private fun calculateCompositeScore(candidate: SourceCandidate): Int {
        var score = 0

        // 1. Availability / Stream Type weight
        when (candidate.type) {
            SourceStreamType.DIRECT, SourceStreamType.HLS -> score += 600
            SourceStreamType.DASH -> score += 550
            SourceStreamType.TORRENT -> {
                score += when {
                    candidate.seeders >= 50 -> 580
                    candidate.seeders >= 20 -> 520
                    candidate.seeders >= 5 -> 400
                    candidate.seeders > 0 -> 250
                    else -> 50 // 0 seeders
                }
            }
            SourceStreamType.LOCAL -> score += 1000
        }

        // 2. Resolution / Quality Score
        score += candidate.qualityScore

        // 3. Health & Codec bonus
        score += (candidate.healthScore * 2)

        if (candidate.audioTracks.isNotEmpty() || candidate.extraData["isDualAudio"] == "true") {
            score += 40
        }

        return score
    }
}
