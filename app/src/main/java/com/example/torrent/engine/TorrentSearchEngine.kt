package com.example.torrent.engine

import android.util.Log
import com.example.torrent.model.TorrentRelease
import com.example.torrent.model.TorrentResult
import com.example.torrent.protocol.MagnetParser
import com.example.torrent.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * High-performance, multi-provider Torrent Search & Aggregation Engine.
 *
 * Coordinates concurrent queries across multiple public and private indexers:
 * - 1337x (Scraper adapted from Magnetio)
 * - TorrentGalaxy (TGx scraper adapted from Magnetio)
 * - YTS (YIFY official API)
 * - EZTV (TV show mirror API)
 * - Nyaa / AnimeTosho (Anime search API)
 * - Torrentio (Stremio multi-source / Debrid indexer)
 * - Torznab (Generic Jackett / Prowlarr client)
 *
 * Guarantees:
 * 1. Normalized output schema (TorrentResult / TorrentRelease)
 * 2. Fault isolation: one failing or timed-out indexer will never break the search
 * 3. InfoHash deduplication with highest-health metadata retention
 * 4. Malformed magnet filtering and hash validation
 * 5. Media classification & intelligent multi-factor ranking
 */
class TorrentSearchEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    companion object {
        private const val TAG = "TorrentSearchEngine"
        private const val PROVIDER_TIMEOUT_MS = 12_000L

        @Volatile
        private var instance: TorrentSearchEngine? = null

        fun getInstance(): TorrentSearchEngine {
            return instance ?: synchronized(this) {
                instance ?: TorrentSearchEngine().also { instance = it }
            }
        }
    }

    private val defaultProviders: List<TorrentProvider> = listOf(
        TorrentioProvider(client),
        YtsProvider(client),
        EztvProvider(client),
        NyaaProvider(client),
        X1337Provider(client),
        TorrentGalaxyProvider(client),
        TorznabProvider(client)
    )

    private val customProviders = mutableListOf<TorrentProvider>()

    fun registerProvider(provider: TorrentProvider) {
        synchronized(customProviders) {
            if (customProviders.none { it.id.equals(provider.id, ignoreCase = true) }) {
                customProviders.add(provider)
            }
        }
    }

    fun getAllProviders(): List<TorrentProvider> {
        return synchronized(customProviders) {
            defaultProviders + customProviders
        }
    }

    /**
     * Executes parallel search across all active indexers with isolated error handling,
     * deduplication, magnet validation, and score ranking.
     */
    suspend fun search(
        query: String,
        identity: MediaIdentity
    ): List<TorrentResult> = withContext(Dispatchers.IO) {
        val providers = getAllProviders().filter { it.isEnabled }
        if (providers.isEmpty()) return@withContext emptyList()

        val rawResults = supervisorScope {
            providers.map { provider ->
                async {
                    try {
                        withTimeout(PROVIDER_TIMEOUT_MS) {
                            val results = provider.search(query, identity)
                            Log.d(TAG, "Provider ${provider.name} returned ${results.size} items")
                            results
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.w(TAG, "Provider ${provider.name} timed out after ${PROVIDER_TIMEOUT_MS}ms")
                        emptyList()
                    } catch (e: Exception) {
                        Log.w(TAG, "Provider ${provider.name} search failed: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        // Validate, normalize and deduplicate
        val sanitized = processAndDeduplicate(rawResults)
        Log.i(TAG, "Aggregated ${sanitized.size} distinct valid torrents for \"${identity.title.ifBlank { query }}\"")
        sanitized
    }

    /**
     * Progressive streaming search flow emitting accumulated results as providers finish.
     */
    fun searchFlow(
        query: String,
        identity: MediaIdentity
    ): Flow<List<TorrentResult>> = flow {
        val providers = getAllProviders().filter { it.isEnabled }
        if (providers.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val accumulated = ConcurrentHashMap<String, TorrentResult>()

        coroutineScope {
            providers.map { provider ->
                launch {
                    try {
                        withTimeout(PROVIDER_TIMEOUT_MS) {
                            val results = provider.search(query, identity)
                            for (item in results) {
                                val valid = validateAndNormalize(item) ?: continue
                                val key = valid.infoHash.lowercase()
                                val existing = accumulated[key]
                                if (existing == null || valid.seeders > existing.seeders) {
                                    accumulated[key] = valid
                                }
                            }
                            // Sort and emit current progressive state
                            val sorted = rankAndSort(accumulated.values.toList())
                            emit(sorted)
                        }
                    } catch (_: Exception) {
                        // Isolated failure
                    }
                }
            }.joinAll()
        }

        val finalSorted = rankAndSort(accumulated.values.toList())
        emit(finalSorted)
    }

    /**
     * Backward-compatible search method returning List<TorrentRelease>.
     */
    suspend fun searchReleases(
        query: String,
        identity: MediaIdentity
    ): List<TorrentRelease> {
        val results = search(query, identity)
        return results.map { it.toTorrentRelease() }
    }

    /**
     * Validates magnet/infoHash, filters out corrupt entries, and merges duplicates.
     */
    fun processAndDeduplicate(rawList: List<TorrentResult>): List<TorrentResult> {
        val distinctMap = LinkedHashMap<String, TorrentResult>()

        for (item in rawList) {
            val normalized = validateAndNormalize(item) ?: continue
            val key = normalized.infoHash.lowercase()

            val existing = distinctMap[key]
            if (existing == null) {
                distinctMap[key] = normalized
            } else {
                // Merge: keep higher seeders and non-empty metadata
                val merged = mergeResults(existing, normalized)
                distinctMap[key] = merged
            }
        }

        return rankAndSort(distinctMap.values.toList())
    }

    /**
     * Validates magnet URI and infoHash, ensuring valid 40-char hex hash.
     */
    private fun validateAndNormalize(item: TorrentResult): TorrentResult? {
        if (item.title.isBlank()) return null

        var hexHash = item.infoHash.trim().lowercase()

        if (hexHash.isBlank() || hexHash.length != 40 || !hexHash.matches(Regex("^[0-9a-f]{40}$"))) {
            // Try extracting from magnet URL
            if (item.magnet.isNotBlank()) {
                val parsed = MagnetParser.parse(item.magnet)
                if (parsed != null) {
                    hexHash = parsed.infoHashHex.lowercase()
                }
            }
        }

        // Must have a valid 40-character hex hash or valid debrid stream hash
        if (hexHash.isBlank() || (!hexHash.startsWith("debrid_") && !hexHash.matches(Regex("^[0-9a-f]{40}$")))) {
            return null
        }

        val effectiveMagnet = if (item.magnet.isNotBlank() && item.magnet.startsWith("magnet:?")) {
            item.magnet
        } else if (!hexHash.startsWith("debrid_")) {
            MagnetParser.buildMagnetUrl(hexHash, item.title, item.trackerUrls.ifEmpty { MagnetParser.DEFAULT_TRACKERS })
        } else {
            item.magnet
        }

        val enriched = enrichMetadata(item.copy(infoHash = hexHash, magnet = effectiveMagnet))
        return enriched
    }

    private fun mergeResults(a: TorrentResult, b: TorrentResult): TorrentResult {
        val bestSeeders = maxOf(a.seeders, b.seeders)
        val bestLeechers = maxOf(a.leechers, b.leechers)
        val bestSize = maxOf(a.size, b.size)
        val bestFormattedSize = if (a.formattedSize.isNotBlank()) a.formattedSize else b.formattedSize
        val bestQuality = if (a.qualityScore >= b.qualityScore) a.quality else b.quality
        val bestCodec = a.codec.ifBlank { b.codec }
        val bestHdr = a.hdr.ifBlank { b.hdr }
        val bestAudio = a.audioChannels.ifBlank { b.audioChannels }
        val combinedSource = if (a.source.contains(b.source, ignoreCase = true)) a.source else "${a.source}, ${b.source}"

        return a.copy(
            seeders = bestSeeders,
            leechers = bestLeechers,
            size = bestSize,
            formattedSize = bestFormattedSize,
            quality = bestQuality,
            codec = bestCodec,
            hdr = bestHdr,
            audioChannels = bestAudio,
            source = combinedSource
        )
    }

    private fun enrichMetadata(item: TorrentResult): TorrentResult {
        val text = item.title + " " + item.quality + " " + item.codec

        var quality = item.quality
        if (quality.isBlank() || quality == "1080p") {
            quality = when {
                text.contains("2160p", ignoreCase = true) || text.contains("4K", ignoreCase = true) || text.contains("UHD", ignoreCase = true) -> "4K UHD"
                text.contains("1080p", ignoreCase = true) || text.contains("FHD", ignoreCase = true) -> "1080p"
                text.contains("720p", ignoreCase = true) || text.contains("HD", ignoreCase = true) -> "720p"
                text.contains("480p", ignoreCase = true) -> "480p"
                else -> "1080p"
            }
        }

        var codec = item.codec
        if (codec.isBlank()) {
            codec = when {
                text.contains("x265", ignoreCase = true) || text.contains("HEVC", ignoreCase = true) || text.contains("H.265", ignoreCase = true) -> "x265 HEVC"
                text.contains("AV1", ignoreCase = true) -> "AV1"
                text.contains("x264", ignoreCase = true) || text.contains("H.264", ignoreCase = true) || text.contains("AVC", ignoreCase = true) -> "x264"
                else -> ""
            }
        }

        var hdr = item.hdr
        if (hdr.isBlank()) {
            hdr = when {
                text.contains("DV", ignoreCase = true) && text.contains("HDR", ignoreCase = true) -> "Dolby Vision + HDR"
                text.contains("Dolby Vision", ignoreCase = true) || text.contains("DV", ignoreCase = true) -> "Dolby Vision"
                text.contains("HDR10+", ignoreCase = true) -> "HDR10+"
                text.contains("HDR", ignoreCase = true) -> "HDR"
                else -> ""
            }
        }

        var audio = item.audioChannels
        if (audio.isBlank()) {
            audio = when {
                text.contains("Atmos", ignoreCase = true) -> "Dolby Atmos"
                text.contains("TrueHD", ignoreCase = true) -> "TrueHD"
                text.contains("DTS-HD", ignoreCase = true) || text.contains("DTS", ignoreCase = true) -> "DTS-HD"
                text.contains("7.1", ignoreCase = true) -> "7.1"
                text.contains("5.1", ignoreCase = true) || text.contains("DD5.1", ignoreCase = true) || text.contains("AC3", ignoreCase = true) || text.contains("EAC3", ignoreCase = true) -> "5.1 Surround"
                text.contains("AAC", ignoreCase = true) -> "AAC"
                else -> ""
            }
        }

        val effectiveSize = if (item.size > 0L) {
            item.size
        } else if (item.formattedSize.isNotBlank()) {
            TorrentResult.parseBytes(item.formattedSize)
        } else {
            0L
        }

        val formattedSize = if (item.formattedSize.isNotBlank()) {
            item.formattedSize
        } else {
            TorrentResult.formatBytes(effectiveSize)
        }

        return item.copy(
            size = effectiveSize,
            quality = quality,
            codec = codec,
            hdr = hdr,
            audioChannels = audio,
            formattedSize = formattedSize
        )
    }

    private fun rankAndSort(list: List<TorrentResult>): List<TorrentResult> {
        return list.sortedWith(
            compareByDescending<TorrentResult> { it.seeders > 0 }
                .thenByDescending { it.qualityScore }
                .thenByDescending { it.seeders }
                .thenByDescending { it.size }
        )
    }
}
