package com.example.metadata

import android.util.Log
import com.example.metadata.person.GFriendsPersonProvider
import com.example.metadata.person.PersonProvider
import com.example.metadata.providers.*
import com.example.metadata.trailer.JavPreviewTrailerProvider
import com.example.metadata.trailer.TrailerProvider
import com.example.model.MediaDetailInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Master JAV Metadata & Enrichment Resolver.
 * Connects input parsing, provider cascades, actress enrichment, and preview discovery.
 */
object JavMetadataResolver {

    private const val TAG = "JavMetadataResolver"

    private val metadataProviders = listOf<MetadataProvider>(
        JavinizerMetadataProvider(),
        JavdexMetadataProvider(),
        AvmMetadataProvider(),
        OpenAverMetadataProvider(),
        MdcxMetadataProvider(),
        FssMetadataProvider()
    ).sortedByDescending { it.priority }

    private val personProvider: PersonProvider = GFriendsPersonProvider()
    private val trailerProvider: TrailerProvider = JavPreviewTrailerProvider()

    // Cache parsed metadata
    private val metadataCache = ConcurrentHashMap<String, JavMetadata>()

    /**
     * Resolves complete, rich metadata and cast enrichment for any JAV ID or search query.
     */
    suspend fun resolve(queryOrId: String): JavMetadata? = withContext(Dispatchers.IO) {
        val parsedCode = JavIdParser.parse(queryOrId) ?: queryOrId.trim()
        if (parsedCode.isBlank()) return@withContext null

        metadataCache[parsedCode]?.let { return@withContext it }

        var resolvedMetadata: JavMetadata? = null

        // Cascade through providers in priority order
        for (provider in metadataProviders) {
            if (!provider.isEnabled) continue
            try {
                val meta = provider.getMetadata(parsedCode)
                if (meta != null && meta.title.isNotBlank()) {
                    Log.i(TAG, "Resolved metadata for [$parsedCode] using provider: ${provider.name}")
                    resolvedMetadata = meta
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Provider ${provider.name} failed for $parsedCode: ${e.message}")
            }
        }

        if (resolvedMetadata == null) {
            return@withContext null
        }

        // Enrich Actresses via GFriends
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
     * Searches metadata across providers for discovery.
     */
    suspend fun search(query: String): List<JavMetadata> = withContext(Dispatchers.IO) {
        val parsed = JavIdParser.parse(query)
        if (parsed != null) {
            val direct = resolve(parsed)
            if (direct != null) return@withContext listOf(direct)
        }

        for (provider in metadataProviders) {
            try {
                val results = provider.search(query)
                if (results.isNotEmpty()) {
                    return@withContext results
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search provider ${provider.name} error: ${e.message}")
            }
        }
        emptyList()
    }
}
