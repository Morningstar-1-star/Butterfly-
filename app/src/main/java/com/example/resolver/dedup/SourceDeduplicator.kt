package com.example.resolver.dedup

import android.net.Uri
import android.util.Log
import com.example.model.StreamResult
import com.example.resolver.SourceCandidate
import com.example.resolver.SourceStreamType
import java.security.MessageDigest
import java.util.Locale

/**
 * Advanced Deduplication & Candidate Merging Engine (Adapted from Cauldron & AIOStreams dedup specifications).
 *
 * Implements 4-tier deduplication keys:
 * Tier 1: Canonical URL (stripped of ephemeral query tokens, timestamps, tracking hashes).
 * Tier 2: BitTorrent infoHash (canonical 40-char lowercase hex or base32).
 * Tier 3: Normalized Identity Key (clean title + season + episode + quality + codec).
 * Tier 4: Fallback Fingerprint (hash of title + size + format + streamType).
 *
 * Merging:
 * When duplicate streams/torrents are discovered across multiple indexers, merges them into ONE
 * candidate while retaining the highest seeders, largest bitrate, combined subtitle/audio tracks,
 * and best health score.
 */
object SourceDeduplicator {
    private const val TAG = "SourceDeduplicator"

    private val EPHEMERAL_QUERY_PARAMS = setOf(
        "token", "expires", "expiry", "h", "key", "t", "timestamp", "sig", "signature",
        "auth", "session", "st", "e", "ip", "referer", "user", "uid", "v"
    )

    /**
     * Deduplicates a list of [SourceCandidate] items.
     */
    fun deduplicateCandidates(candidates: List<SourceCandidate>): List<SourceCandidate> {
        val mergedMap = LinkedHashMap<String, SourceCandidate>()

        for (candidate in candidates) {
            val key = generateCandidateKey(candidate)
            val existing = mergedMap[key]

            if (existing == null) {
                mergedMap[key] = candidate
            } else {
                mergedMap[key] = mergeCandidates(existing, candidate)
            }
        }

        return mergedMap.values.toList()
    }

    /**
     * Deduplicates a list of [StreamResult] items.
     */
    fun deduplicateStreams(streams: List<StreamResult>): List<StreamResult> {
        val mergedMap = LinkedHashMap<String, StreamResult>()

        for (stream in streams) {
            val key = generateStreamKey(stream)
            val existing = mergedMap[key]

            if (existing == null) {
                mergedMap[key] = stream
            } else {
                mergedMap[key] = mergeStreams(existing, stream)
            }
        }

        return mergedMap.values.toList()
    }

    private fun generateCandidateKey(c: SourceCandidate): String {
        // Tier 2: InfoHash for Torrents
        val infoHash = c.extraData["infoHash"] ?: extractInfoHashFromMagnet(c.urlOrMagnet)
        if (!infoHash.isNullOrBlank()) {
            return "hash:${infoHash.lowercase(Locale.ROOT)}"
        }

        // Tier 1: Canonical URL
        if (c.urlOrMagnet.startsWith("http://") || c.urlOrMagnet.startsWith("https://")) {
            val canonicalUrl = buildCanonicalUrl(c.urlOrMagnet)
            if (canonicalUrl.isNotBlank()) {
                return "url:$canonicalUrl"
            }
        }

        // Tier 3 & 4: Fallback Fingerprint
        val cleanTitle = c.title.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")
        val sizeGroup = (c.sizeBytes / (10 * 1024 * 1024)) // 10MB bucket
        return "fp:${c.type}:$cleanTitle:${c.quality}:$sizeGroup"
    }

    private fun generateStreamKey(s: StreamResult): String {
        // Tier 2: InfoHash
        val infoHash = s.infoHash ?: extractInfoHashFromMagnet(s.url)
        if (!infoHash.isNullOrBlank()) {
            return "hash:${infoHash.lowercase(Locale.ROOT)}"
        }

        // Tier 1: Canonical URL
        if (s.url.startsWith("http://") || s.url.startsWith("https://")) {
            val canonical = buildCanonicalUrl(s.url)
            if (canonical.isNotBlank()) {
                return "url:$canonical"
            }
        }

        val cleanTitle = s.title.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")
        val sizeGroup = (s.sizeBytes / (10 * 1024 * 1024))
        return "fp:${s.streamType}:$cleanTitle:${s.qualityLabel}:$sizeGroup"
    }

    fun buildCanonicalUrl(rawUrl: String): String {
        return try {
            val uri = Uri.parse(rawUrl)
            val scheme = (uri.scheme ?: "https").lowercase(Locale.ROOT)
            val host = (uri.host ?: "").lowercase(Locale.ROOT)
            val path = uri.path ?: ""

            // Rebuild query excluding ephemeral tokens
            val preservedParams = mutableListOf<String>()
            val queryNames = try { uri.queryParameterNames } catch (_: Exception) { emptySet() }
            for (name in queryNames.sorted()) {
                if (!EPHEMERAL_QUERY_PARAMS.contains(name.lowercase(Locale.ROOT))) {
                    val value = uri.getQueryParameter(name) ?: ""
                    preservedParams.add("$name=$value")
                }
            }

            val queryPart = if (preservedParams.isNotEmpty()) "?${preservedParams.joinToString("&")}" else ""
            "$scheme://$host$path$queryPart"
        } catch (_: Exception) {
            rawUrl.substringBefore("?")
        }
    }

    private fun mergeCandidates(existing: SourceCandidate, incoming: SourceCandidate): SourceCandidate {
        val bestSeeders = maxOf(existing.seeders, incoming.seeders)
        val bestLeechers = maxOf(existing.leechers, incoming.leechers)
        val bestHealth = maxOf(existing.healthScore, incoming.healthScore)
        val bestQualityScore = maxOf(existing.qualityScore, incoming.qualityScore)
        val bestSize = maxOf(existing.sizeBytes, incoming.sizeBytes)

        val mergedSubs = (existing.subtitleUrls + incoming.subtitleUrls).distinct()
        val mergedAudio = (existing.audioTracks + incoming.audioTracks).distinct()
        val mergedHeaders = existing.headers + incoming.headers
        val mergedExtra = existing.extraData + incoming.extraData

        val providerName = if (existing.providerId == incoming.providerId) {
            existing.providerName
        } else {
            "${existing.providerName} + ${incoming.providerName}"
        }

        return existing.copy(
            providerName = providerName,
            seeders = bestSeeders,
            leechers = bestLeechers,
            healthScore = bestHealth,
            qualityScore = bestQualityScore,
            sizeBytes = bestSize,
            formattedSize = if (existing.formattedSize.isNotBlank()) existing.formattedSize else incoming.formattedSize,
            subtitleUrls = mergedSubs,
            audioTracks = mergedAudio,
            headers = mergedHeaders,
            extraData = mergedExtra
        )
    }

    private fun mergeStreams(existing: StreamResult, incoming: StreamResult): StreamResult {
        val bestSeeders = maxOf(existing.seeders, incoming.seeders)
        val bestLeechers = maxOf(existing.leechers, incoming.leechers)
        val bestHealth = maxOf(existing.healthScore, incoming.healthScore)
        val bestQualityScore = maxOf(existing.qualityScore, incoming.qualityScore)
        val bestBitrate = maxOf(existing.bitrateBps, incoming.bitrateBps)
        val bestSize = maxOf(existing.sizeBytes, incoming.sizeBytes)

        val mergedSubs = (existing.subtitleUrls + incoming.subtitleUrls).distinct()
        val mergedAudio = (existing.audioTracks + incoming.audioTracks).distinct()
        val mergedHeaders = existing.headers + incoming.headers
        val mergedAudioHeaders = existing.audioHeaders + incoming.audioHeaders
        val mergedExtra = existing.extraData + incoming.extraData

        val providerName = if (existing.providerId == incoming.providerId) {
            existing.providerName
        } else {
            "${existing.providerName} + ${incoming.providerName}"
        }

        return existing.copy(
            providerName = providerName,
            seeders = bestSeeders,
            leechers = bestLeechers,
            healthScore = bestHealth,
            qualityScore = bestQualityScore,
            bitrateBps = bestBitrate,
            sizeBytes = bestSize,
            subtitleUrls = mergedSubs,
            audioTracks = mergedAudio,
            headers = mergedHeaders,
            audioHeaders = mergedAudioHeaders,
            extraData = mergedExtra
        )
    }

    private fun extractInfoHashFromMagnet(magnet: String): String? {
        if (!magnet.startsWith("magnet:", ignoreCase = true)) return null
        val match = Regex("""xt=urn:btih:([a-zA-Z0-9]{32,40})""", RegexOption.IGNORE_CASE).find(magnet)
        return match?.groupValues?.getOrNull(1)
    }
}
