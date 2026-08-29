package com.example.metadata

import android.util.Log
import com.example.metadata.person.GFriendsPersonProvider
import com.example.metadata.person.PersonProvider
import com.example.metadata.providers.*
import com.example.metadata.trailer.JavPreviewTrailerProvider
import com.example.metadata.trailer.TrailerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Master JAV Metadata & Enrichment Resolver.
 *
 * Coordinates:
 * - Javinizer-Go (Primary REST Service)
 * - JAVapi / JavBus / Javdex / AVM / OpenAver / MDCx / FSS (Fallback Scrapers)
 * - GFriends Actress Avatar & Biography Enrichment
 * - JAV-Preview Sample Video & Trailer Discovery
 */
object JavMetadataResolver {

    private const val TAG = "JavMetadataResolver"

    private val javinizerGoProvider = JavinizerGoMetadataProvider()

    private val metadataProviders = listOf<MetadataProvider>(
        javinizerGoProvider,
        JavapiMetadataProvider(),
        JavBusMetadataProvider(),
        JavdexMetadataProvider(),
        AvmMetadataProvider(),
        OpenAverMetadataProvider(),
        MdcxMetadataProvider(),
        FssMetadataProvider()
    ).sortedByDescending { it.priority }

    private val personProvider: PersonProvider = GFriendsPersonProvider()
    private val trailerProvider: TrailerProvider = JavPreviewTrailerProvider()

    // Thread-safe in-memory metadata cache
    private val metadataCache = ConcurrentHashMap<String, JavMetadata>()

    /**
     * Resolves complete, rich metadata and cast enrichment for any JAV ID or search query.
     * Returns null if metadata cannot be resolved. Never returns fake placeholder metadata.
     */
    suspend fun resolve(queryOrId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val parsedCode = JavIdParser.parse(queryOrId) ?: return@withContext null
        if (parsedCode.isBlank()) return@withContext null

        metadataCache[parsedCode]?.let { return@withContext it }

        var resolvedMetadata: JavMetadata? = null
        val fallbackEnabled = com.example.util.AppConfig.isJavinizerFallbackEnabled()

        // Cascade through providers in priority order (Javinizer-Go first)
        for (provider in metadataProviders) {
            if (!provider.isEnabled) continue
            try {
                val meta = provider.getMetadata(parsedCode)
                if (meta != null && meta.title.isNotBlank()) {
                    Log.i(TAG, "Resolved metadata for [$parsedCode] via ${provider.name} (${provider.classification.displayName})")
                    resolvedMetadata = meta
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Provider ${provider.name} failed for $parsedCode: ${e.message}")
            }

            // If Javinizer-Go was executed and failed, and user disabled secondary fallback scrapers, stop cascade
            if (provider is JavinizerGoMetadataProvider && !fallbackEnabled && resolvedMetadata == null) {
                Log.d(TAG, "Javinizer-Go had no result and fallback is disabled.")
                break
            }
        }

        if (resolvedMetadata == null) {
            return@withContext null
        }

        // Enrich Actresses via GFriends (if cast names exist)
        val enrichedCast = if (resolvedMetadata.cast.isNotEmpty()) {
            val castDeferred = resolvedMetadata.cast.map { actor ->
                async {
                    try {
                        personProvider.enrichActor(actor)
                    } catch (e: Exception) {
                        actor
                    }
                }
            }
            castDeferred.awaitAll()
        } else {
            emptyList()
        }

        // Discover Previews & Trailers via JAV-Preview
        val trailers = try {
            trailerProvider.resolveTrailers(parsedCode, resolvedMetadata)
        } catch (e: Exception) {
            emptyList()
        }

        val primaryTrailerUrl = trailers.firstOrNull()?.embedUrl ?: resolvedMetadata.sampleVideoUrl

        val finalMetadata = resolvedMetadata.copy(
            cast = enrichedCast,
            sampleVideoUrl = primaryTrailerUrl
        )

        metadataCache[parsedCode] = finalMetadata
        finalMetadata
    }

    /**
     * Searches metadata across providers for discovery with deduplication.
     */
    suspend fun search(query: String): List<JavMetadata> = withContext(Dispatchers.IO) {
        val parsed = JavIdParser.parse(query)
        if (parsed != null) {
            val direct = resolve(parsed)
            if (direct != null) return@withContext listOf(direct)
        }

        val aggregatedResults = mutableListOf<JavMetadata>()
        for (provider in metadataProviders) {
            if (!provider.isEnabled) continue
            try {
                val results = provider.search(query)
                if (results.isNotEmpty()) {
                    aggregatedResults.addAll(results)
                    // If high-priority provider returns rich results, avoid querying all 7 providers
                    if (aggregatedResults.size >= 10) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search provider ${provider.name} error: ${e.message}")
            }
        }

        // Deduplicate results by JAV code
        aggregatedResults.distinctBy { it.code.uppercase() }
    }
}
