package com.example.extractor

import android.content.Context
import android.util.Log
import com.example.extractor.supjav.SupJavNetwork
import com.example.extractor.supjav.SupJavParser
import com.example.extractor.supjav.SupJavResolver
import com.example.extractor.supjav.SupJavSource
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.util.StreamCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Top-level provider for SupJav catalog browsing, search, and native ExoPlayer stream resolution.
 */
object SupJavProvider {

    private const val TAG = "SupJavProvider"
    const val PROVIDER_ID = "supjav"
    const val DEFAULT_BASE_URL = "https://supjav.com"

    fun registerPageUrl(id: String, url: String) {
        SupJavResolver.registerPageUrl(id, url)
    }

    /**
     * Fetches SupJav trending / home catalog.
     */
    suspend fun getHome(limit: Int = 20, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            SupJavResolver.fetchCatalog(limit, page, context)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching SupJav home catalog: ${e.message}")
            emptyList()
        }
    }

    /**
     * Searches SupJav video catalog.
     */
    suspend fun search(query: String, limit: Int = 20, page: Int = 1, context: Context? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            SupJavResolver.searchCatalog(query, limit, page, context)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching SupJav for $query: ${e.message}")
            emptyList()
        }
    }

    /**
     * Resolves all available media stream options for a SupJav video.
     */
    suspend fun resolveSource(urlOrId: String, context: Context? = null): List<SupJavSource> = withContext(Dispatchers.IO) {
        try {
            SupJavResolver.resolveStreams(urlOrId, context)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Error resolving SupJav streams for $urlOrId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Resolves full standardized Butterfly StreamData for ExoPlayer/Media3 playback.
     */
    suspend fun getStreamData(urlOrId: String, context: Context? = null): StreamData? = withContext(Dispatchers.IO) {
        Log.i(TAG, "SupJav getStreamData for $urlOrId")
        try {
            val sources = resolveSource(urlOrId, context)
            if (sources.isEmpty()) {
                Log.w(TAG, "No playable streams found for SupJav: $urlOrId")
                return@withContext null
            }

            val cleanId = urlOrId.trim().removePrefix("supjav_").removePrefix("supjav:").removeSuffix(".html").substringAfterLast("/")
            val javCode = SupJavParser.extractJavCode(cleanId).ifBlank { cleanId.uppercase() }

            val cachedMeta = SupJavResolver.getCachedMetadata(urlOrId)
            val displayTitle = cachedMeta?.first?.ifBlank { null }
                ?: if (javCode.isNotBlank()) "[$javCode] JAV Video" else "SupJav Video ($cleanId)"
            val displayThumb = cachedMeta?.second

            val streamOptions = sources.map { src ->
                val isHls = src.isHls || src.url.contains(".m3u8")
                PlayableStreamOption(
                    qualityLabel = src.quality,
                    format = if (isHls) "m3u8" else "mp4",
                    isMuxed = true,
                    videoUrl = src.url,
                    audioUrl = null,
                    providerType = ProviderType.DIRECT,
                    headers = src.headers,
                    sourceName = src.sourceName,
                    qualityCategory = StreamCategorizer.detectQualityFromText(src.quality, false, false)
                )
            }

            val primaryOpt = streamOptions.firstOrNull { it.qualityLabel?.contains("1080") == true } ?: streamOptions.first()
            val primarySource = sources.first()

            StreamData(
                videoId = "supjav_$cleanId",
                videoUrl = if (urlOrId.startsWith("http")) urlOrId else "$DEFAULT_BASE_URL/$cleanId.html",
                title = displayTitle,
                channelName = "SupJav",
                channelAvatarUrl = null,
                uploadDate = null,
                description = "SupJav Direct Video Stream • Code: $javCode",
                availableStreamOptions = streamOptions,
                selectedStreamOption = primaryOpt,
                hlsUrl = if (primarySource.isHls) primarySource.url else sources.firstOrNull { it.isHls }?.url,
                providerId = PROVIDER_ID,
                thumbnailUrl = displayThumb,
                providerType = ProviderType.DIRECT,
                headers = primarySource.headers,
                tags = listOf("JAV", "SupJav", javCode)
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Exception creating StreamData for SupJav ($urlOrId): ${e.message}", e)
            null
        }
    }
}
