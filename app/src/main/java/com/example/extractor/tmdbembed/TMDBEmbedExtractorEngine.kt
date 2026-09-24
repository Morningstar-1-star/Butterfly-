package com.example.extractor.tmdbembed

import android.content.Context
import android.util.Log
import com.example.extractor.tmdbembed.extractors.CastleTvExtractor
import com.example.extractor.tmdbembed.extractors.DahmerMoviesExtractor
import com.example.extractor.tmdbembed.extractors.FourKHDHubExtractor
import com.example.extractor.tmdbembed.extractors.HDGharTvExtractor
import com.example.extractor.tmdbembed.extractors.NetMirrorExtractor
import com.example.extractor.tmdbembed.extractors.OneTouchTvExtractor
import com.example.extractor.tmdbembed.extractors.ShowboxExtractor
import com.example.extractor.tmdbembed.extractors.StreamFlixExtractor
import com.example.extractor.tmdbembed.extractors.VaPlayerExtractor
import com.example.extractor.tmdbembed.extractors.VideasyExtractor
import com.example.extractor.tmdbembed.extractors.VidlinkExtractor
import com.example.extractor.tmdbembed.extractors.VixSrcExtractor
import com.example.extractor.tmdbembed.extractors.ZXCStreamsExtractor
import com.example.model.PlayableStreamOption
import com.example.model.ProviderType
import com.example.util.StreamCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TMDBEmbedExtractorEngine {
    private const val TAG = "TMDBEmbedExtractorEngine"

    suspend fun resolveStreamOptions(
        context: Context,
        request: TMDBMediaRequest
    ): List<PlayableStreamOption> = withContext(Dispatchers.IO) {
        val prioritizedSources = TMDBEmbedConfig.getPrioritizedSources(context)
        val fallbackAllowed = TMDBEmbedConfig.isFallbackEnabled(context)
        val discoveredStreams = mutableListOf<ExtractedStream>()

        Log.d(TAG, "Starting TMDB Embed extraction for ${request.title} (${request.tmdbId}). Prioritized sources: ${prioritizedSources.map { it.id }}")

        for (source in prioritizedSources) {
            try {
                val streams = when (source) {
                    TMDBEmbedSource.VIXSRC -> VixSrcExtractor.extract(request)
                    TMDBEmbedSource.NETMIRROR -> NetMirrorExtractor.extract(request)
                    TMDBEmbedSource.VIDLINK -> VidlinkExtractor.extract(request)
                    TMDBEmbedSource.VAPLAYER -> VaPlayerExtractor.extract(request)
                    TMDBEmbedSource.VIDEASY -> VideasyExtractor.extract(request)
                    TMDBEmbedSource.CASTLE_TV -> CastleTvExtractor.extract(request)
                    TMDBEmbedSource.ONETOUCH_TV -> OneTouchTvExtractor.extract(request)
                    TMDBEmbedSource.HDGHAR_TV -> HDGharTvExtractor.extract(request)
                    TMDBEmbedSource.DAHMER_MOVIES -> DahmerMoviesExtractor.extract(request)
                    TMDBEmbedSource.STREAMFLIX -> StreamFlixExtractor.extract(request)
                    TMDBEmbedSource.ZXCSTREAMS -> ZXCStreamsExtractor.extract(request)
                    TMDBEmbedSource.FOUR_K_HD_HUB -> FourKHDHubExtractor.extract(request)
                    TMDBEmbedSource.SHOWBOX -> ShowboxExtractor.extract(request)
                }

                if (streams.isNotEmpty()) {
                    TMDBEmbedConfig.markSuccess(source)
                    discoveredStreams.addAll(streams)
                    Log.i(TAG, "Source ${source.displayName} succeeded with ${streams.size} stream(s)")

                    // If we found streams, and user got results, we can continue or stop depending on fallback policy
                    // To ensure fast, responsive playback we can break once the primary source returns streams
                    if (discoveredStreams.isNotEmpty() && !fallbackAllowed) {
                        break
                    }
                    if (discoveredStreams.size >= 3) {
                        break
                    }
                } else {
                    TMDBEmbedConfig.markFailure(source, "No streams returned")
                    Log.w(TAG, "Source ${source.displayName} returned 0 streams")
                    if (!fallbackAllowed) {
                        Log.d(TAG, "Fallback disabled; stopping after default source attempt")
                        break
                    }
                }
            } catch (e: Exception) {
                TMDBEmbedConfig.markFailure(source, e.message ?: "Extraction error")
                Log.e(TAG, "Error extracting from ${source.displayName}: ${e.message}", e)
                if (!fallbackAllowed) {
                    break
                }
            }
        }

        // Map ExtractedStream to PlayableStreamOption
        discoveredStreams.map { stream ->
            PlayableStreamOption(
                qualityLabel = "${stream.source.displayName} • ${stream.quality}",
                format = if (stream.isHls) "hls" else "mp4",
                isMuxed = true,
                videoUrl = stream.url,
                audioUrl = null,
                providerType = ProviderType.TMDB_EMBED,
                headers = stream.headers,
                sourceName = "TMDB Embed (${stream.source.displayName})",
                qualityCategory = StreamCategorizer.detectQualityFromText(stream.quality),
                sizeText = stream.sizeText,
                seeders = stream.seeders,
                releaseTitle = stream.title,
                subtitles = stream.subtitles,
                serverStatus = "Online"
            )
        }
    }
}
