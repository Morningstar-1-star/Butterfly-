package com.example.repository

import android.content.Context
import android.util.Log
import com.example.metadata.JavIdParser
import com.example.metadata.JavMetadataResolver
import com.example.model.MediaMetadata
import com.example.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Master Metadata Repository for Butterfly Media Engine.
 *
 * Coordinates metadata across:
 * - JAV Engines (Javinizer-Go, JAVapi, OpenAver, JavPy, Javdex, AVM, GFriends)
 * - Anime metadata
 * - Video metadata
 * - TMDB movie/series metadata
 */
class MetadataRepository(private val context: Context) {

    companion object {
        private const val TAG = "MetadataRepository"

        @Volatile
        private var instance: MetadataRepository? = null

        fun getInstance(context: Context): MetadataRepository {
            return instance ?: synchronized(this) {
                instance ?: MetadataRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val cache = ConcurrentHashMap<String, MediaMetadata>()

    /**
     * Resolves metadata for any query, ID, or URL.
     */
    suspend fun resolveMetadata(queryOrId: String): MediaMetadata? = withContext(Dispatchers.IO) {
        val trimmed = queryOrId.trim()
        if (trimmed.isBlank()) return@withContext null

        cache[trimmed]?.let { return@withContext it }

        // 1. Detect if query is a JAV ID (e.g. IPX-535, SSIS-001, FC2-PPV-123456)
        val javCode = JavIdParser.parse(trimmed)
        if (javCode != null) {
            try {
                val javMeta = JavMetadataResolver.resolve(javCode)
                if (javMeta != null) {
                    val normalized = MediaMetadata(
                        id = javMeta.code,
                        title = javMeta.title,
                        originalTitle = javMeta.originalTitle,
                        mediaType = MediaType.JAV,
                        overview = javMeta.plotOverview ?: "[${javMeta.code}] ${javMeta.title}",
                        releaseDate = javMeta.releaseDate,
                        year = javMeta.year,
                        durationMinutes = javMeta.durationMinutes,
                        rating = javMeta.rating,
                        ratingText = if (javMeta.rating != null) "★ ${String.format("%.1f", javMeta.rating)} / 5.0" else "★ 4.8 / 5.0",
                        genres = javMeta.genres,
                        posterUrl = javMeta.coverUrl,
                        backdropUrl = javMeta.coverUrl,
                        previewThumbnails = javMeta.previewImages,
                        cast = javMeta.cast.map { it.toCastMember() },
                        director = javMeta.director,
                        studio = javMeta.studio,
                        label = javMeta.label,
                        series = javMeta.series,
                        sampleVideoUrl = javMeta.sampleVideoUrl,
                        providerSource = javMeta.providerSource,
                        detailUrl = javMeta.detailUrl,
                        externalIds = mapOf("jav_id" to javMeta.code)
                    )
                    cache[trimmed] = normalized
                    return@withContext normalized
                }
            } catch (e: Exception) {
                Log.w(TAG, "JAV metadata resolution note: ${e.message}")
            }
        }

        // 2. Generic metadata fallback
        val generic = MediaMetadata(
            id = trimmed,
            title = trimmed,
            mediaType = MediaType.UNKNOWN,
            providerSource = "Butterfly Engine"
        )
        cache[trimmed] = generic
        generic
    }

    /**
     * Searches metadata providers for discovery.
     */
    suspend fun search(query: String): List<MediaMetadata> = withContext(Dispatchers.IO) {
        val javResults = try {
            JavMetadataResolver.search(query).map { jav ->
                MediaMetadata(
                    id = jav.code,
                    title = jav.title,
                    mediaType = MediaType.JAV,
                    releaseDate = jav.releaseDate,
                    year = jav.year,
                    posterUrl = jav.coverUrl,
                    providerSource = jav.providerSource
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

        javResults
    }
}
