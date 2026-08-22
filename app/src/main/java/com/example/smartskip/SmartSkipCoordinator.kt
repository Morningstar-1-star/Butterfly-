package com.example.smartskip

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmartSkipCoordinator {

    private val providers = listOf(
        YouTubeSponsorBlockProvider(),
        BilibiliSponsorBlockProvider(),
        AniSkipProvider(),
        TheIntroDBProvider()
    )

    // Memory cache: videoId -> list of skip segments
    private val segmentCache = mutableMapOf<String, List<SkipSegment>>()

    suspend fun resolveSegments(
        context: Context,
        videoId: String,
        durationMs: Long,
        title: String? = null,
        channelName: String? = null,
        providerId: String? = null,
        extraMeta: Map<String, String> = emptyMap()
    ): List<SkipSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()

        val cacheKey = "${providerId ?: "default"}_${videoId}"
        segmentCache[cacheKey]?.let { cached ->
            return@withContext cached
        }

        val prefs = SmartSkipPreferences.getInstance(context)
        if (!prefs.isSmartSkipEnabled.value) {
            return@withContext emptyList()
        }

        val allResolvedSegments = mutableListOf<SkipSegment>()

        for (provider in providers) {
            // Check if this provider source is enabled in preferences
            if (!prefs.isSourceEnabled(provider.source)) {
                continue
            }

            try {
                val segments = provider.fetchSegments(
                    context = context,
                    videoId = videoId,
                    durationMs = durationMs,
                    title = title,
                    channelName = channelName,
                    providerIdParam = providerId,
                    extraMeta = extraMeta
                )

                if (segments.isNotEmpty()) {
                    Log.i("SmartSkipCoordinator", "Resolved ${segments.size} segments from ${provider.displayName} for $videoId")
                    allResolvedSegments.addAll(segments)
                    // If we found segments for YouTube or Bilibili, we can proceed or merge
                    if (provider.source == SkipSource.YOUTUBE || provider.source == SkipSource.BILIBILI) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w("SmartSkipCoordinator", "Provider ${provider.displayName} failed: ${e.message}")
            }
        }

        // Deduplicate overlapping segments and sort by startMs
        val sorted = allResolvedSegments.distinctBy { "${it.category.id}_${it.startMs}_${it.endMs}" }.sortedBy { it.startMs }
        segmentCache[cacheKey] = sorted
        sorted
    }

    fun clearCache() {
        segmentCache.clear()
    }
}
