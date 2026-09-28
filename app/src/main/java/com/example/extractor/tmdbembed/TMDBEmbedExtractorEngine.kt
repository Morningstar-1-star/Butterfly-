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
        request: TMDBMediaRequest,
        specificSource: TMDBEmbedSource? = null
    ): List<PlayableStreamOption> = withContext(Dispatchers.IO) {
        val basePrioritized = TMDBEmbedConfig.getPrioritizedSources(context)
        val prioritizedSources = if (specificSource != null) {
            val list = mutableListOf(specificSource)
            for (s in basePrioritized) {
                if (s != specificSource && !list.contains(s)) {
                    list.add(s)
                }
            }
            list
        } else {
            basePrioritized
        }

        val fallbackAllowed = TMDBEmbedConfig.isFallbackEnabled(context)
        val discoveredStreams = mutableListOf<ExtractedStream>()

        // Ensure VIDLINK is always in the attempt list if not already present
        val sourceOrder = prioritizedSources.toMutableList()
        if (!sourceOrder.contains(TMDBEmbedSource.VIDLINK)) {
            sourceOrder.add(0, TMDBEmbedSource.VIDLINK)
        }

        Log.d(TAG, "Starting TMDB Embed extraction for ${request.title} (${request.tmdbId}). Target: ${specificSource?.displayName ?: "Auto"}. Prioritized sources: ${sourceOrder.map { it.id }}")

        for (source in sourceOrder) {
            try {
                val streams = when (source) {
                    TMDBEmbedSource.VIDLINK -> VidlinkExtractor.extract(request)
                    TMDBEmbedSource.SHOWBOX -> ShowboxExtractor.extract(request)
                    TMDBEmbedSource.VIDEASY -> VideasyExtractor.extract(request)
                    TMDBEmbedSource.NETMIRROR -> NetMirrorExtractor.extract(request)
                    TMDBEmbedSource.FOUR_K_HD_HUB -> FourKHDHubExtractor.extract(request)
                    TMDBEmbedSource.VIXSRC -> VixSrcExtractor.extract(request)
                    TMDBEmbedSource.STREAMFLIX -> StreamFlixExtractor.extract(request)
                    TMDBEmbedSource.CASTLE_TV -> CastleTvExtractor.extract(request)
                    TMDBEmbedSource.HDGHAR_TV -> HDGharTvExtractor.extract(request)
                    TMDBEmbedSource.ONETOUCH_TV -> OneTouchTvExtractor.extract(request)
                    TMDBEmbedSource.VAPLAYER -> VaPlayerExtractor.extract(request)
                    TMDBEmbedSource.DAHMER_MOVIES -> DahmerMoviesExtractor.extract(request)
                    TMDBEmbedSource.ZXCSTREAMS -> ZXCStreamsExtractor.extract(request)
                }

                if (streams.isNotEmpty()) {
                    TMDBEmbedConfig.markSuccess(source)
                    discoveredStreams.addAll(streams)
                    Log.i(TAG, "Source ${source.displayName} succeeded with ${streams.size} stream(s)")

                    // If user requested a specific source and it succeeded, we can stop immediately
                    if (specificSource != null && source == specificSource) {
                        break
                    }
                    if (discoveredStreams.size >= 4) {
                        break
                    }
                } else {
                    TMDBEmbedConfig.markFailure(source, "No streams returned")
                    Log.w(TAG, "Source ${source.displayName} returned 0 streams; continuing fallback")
                }
            } catch (e: Exception) {
                TMDBEmbedConfig.markFailure(source, e.message ?: "Extraction error")
                Log.w(TAG, "Note extracting from ${source.displayName}: ${e.message}")
            }
        }

        if (discoveredStreams.isEmpty() && (request.tmdbId.isNotBlank() || request.title.isNotBlank())) {
            val reqTitle = request.title.ifBlank { "Cinema Stream" }
            try {
                val mediaIdentity = com.example.torrent.provider.MediaIdentity(
                    title = reqTitle,
                    mediaType = if (request.isTv) "tv" else "movie",
                    season = request.season,
                    episode = request.episode,
                    tmdbId = request.tmdbId.takeIf { it.all { c -> c.isDigit() } }
                )
                val torrentReleases = com.example.torrent.provider.TorrentProviderManager.getInstance()
                    .searchReleases(reqTitle, mediaIdentity)
                for (rel in torrentReleases.take(8)) {
                    discoveredStreams.add(
                        ExtractedStream(
                            title = rel.title,
                            url = rel.magnetUrl,
                            source = TMDBEmbedSource.SHOWBOX,
                            quality = rel.quality,
                            sizeText = rel.formattedSize,
                            seeders = rel.seeders,
                            isHls = rel.magnetUrl.contains(".m3u8")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct fallback error for TMDB: ${e.message}")
            }
        }

        // Map ExtractedStream to real direct PlayableStreamOption (no webview embeds)
        discoveredStreams.map { stream ->
            val isDirectMedia = stream.url.contains(".m3u8", ignoreCase = true) || stream.url.contains(".mp4", ignoreCase = true)
            val isTorrent = stream.url.startsWith("magnet:") || stream.url.contains("/stream")
            val qualitySuffix = if (stream.seeders > 0) " [${stream.seeders} seeds]" else ""
            PlayableStreamOption(
                qualityLabel = "${stream.source.displayName} • ${stream.quality}$qualitySuffix",
                format = if (isDirectMedia) (if (stream.isHls) "hls" else "mp4") else if (isTorrent) "mkv" else "mp4",
                isMuxed = true,
                videoUrl = stream.url,
                audioUrl = null,
                providerType = if (isTorrent) ProviderType.TORRENT else ProviderType.DIRECT,
                headers = stream.headers.ifEmpty { mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36") },
                sourceName = stream.source.displayName,
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
