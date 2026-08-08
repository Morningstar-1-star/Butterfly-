package com.example.plugin.manager

import android.util.Log
import com.example.model.*
import com.example.plugin.sdk.model.PluginVideoStream
import com.example.plugin.sdk.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PipelineValidationResult(
    val playableStreams: List<PlayableStreamOption>,
    val failedLogs: List<FailedSourceLog>
)

class SourcePipelineEngine(
    private val streamValidator: StreamValidator = StreamValidator(),
    private val torrentResolver: TorrentResolver = TorrentResolver()
) {

    fun extractInfoHash(url: String): String? {
        if (!url.contains("xt=urn:btih:", ignoreCase = true)) return null
        val regex = Regex("xt=urn:btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})", RegexOption.IGNORE_CASE)
        val match = regex.find(url)
        return match?.groupValues?.get(1)?.lowercase()
    }

    fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (clean.startsWith("magnet:", ignoreCase = true)) {
            val hash = extractInfoHash(clean)
            return if (hash != null) "magnet:?xt=urn:btih:$hash" else clean
        }
        // Remove tracking params
        clean = clean.replace(Regex("([?&])(utm_[^&]+|_t=[^&]+|ref=[^&]+)"), "")
            .replace(Regex("[?&]$"), "")
        return clean
    }

    suspend fun processAndValidateStreams(
        rawStreams: List<PluginVideoStream>,
        providerId: String
    ): PipelineValidationResult = withContext(Dispatchers.IO) {
        val playable = mutableListOf<PlayableStreamOption>()
        val failed = mutableListOf<FailedSourceLog>()

        // 1. DEDUPLICATION BY INFOHASH / NORMALIZED URL
        val seenInfoHashes = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()
        val deduplicatedStreams = mutableListOf<PluginVideoStream>()

        for (stream in rawStreams) {
            val rawUrl = stream.url ?: continue
            val infoHash = extractInfoHash(rawUrl)
            if (infoHash != null) {
                if (seenInfoHashes.contains(infoHash)) {
                    Log.d("SourcePipeline", "[Deduplication] Dropped duplicate torrent infoHash: $infoHash from $providerId")
                    continue
                }
                seenInfoHashes.add(infoHash)
                deduplicatedStreams.add(stream)
            } else {
                val normUrl = normalizeUrl(rawUrl)
                if (seenUrls.contains(normUrl)) {
                    Log.d("SourcePipeline", "[Deduplication] Dropped duplicate stream URL: $normUrl from $providerId")
                    continue
                }
                seenUrls.add(normUrl)
                deduplicatedStreams.add(stream)
            }
        }

        // 2. LIFECYCLE PIPELINE (DISCOVERED -> PARSED -> VALIDATED -> RESOLVING -> PLAYABLE/FAILED)
        for (stream in deduplicatedStreams) {
            val rawUrl = stream.url ?: continue
            val title = stream.qualityLabel.ifBlank { "Stream" }
            val infoHash = extractInfoHash(rawUrl)
            
            val urlType = when {
                infoHash != null -> "MAGNET"
                rawUrl.contains(".m3u8") || rawUrl.contains("/hls/") -> "DIRECT_HLS"
                rawUrl.contains(".mp4") || rawUrl.contains(".mkv") -> "DIRECT_MP4"
                rawUrl.contains("embed") || rawUrl.contains("vidsrc") -> "EMBED"
                else -> "UNKNOWN"
            }

            // STAGE: PARSED -> VALIDATED
            val validation = streamValidator.validateStream(rawUrl)
            if (!validation.isValid) {
                failed.add(
                    FailedSourceLog(
                        providerId = providerId,
                        sourceTitle = title,
                        rawUrl = rawUrl,
                        errorType = validation.failureReason?.name ?: "VALIDATION_FAILED",
                        httpStatus = if (validation.httpCode != 0) validation.httpCode else null,
                        urlType = urlType,
                        stage = SourceLifecycleStage.VALIDATED,
                        failureReason = validation.failureReason?.description ?: "Failed initial URL or magnet hash validation"
                    )
                )
                continue
            }

            // STAGE: RESOLVING (for torrent magnets or direct streams requiring probe)
            if (infoHash != null) {
                val apiKey = System.getenv("TORBOX_API_KEY") ?: ""
                var resolvedUrl = rawUrl
                var isPlayableDirect = false

                if (apiKey.isNotBlank()) {
                    val torboxRes = torrentResolver.resolveTorrent(rawUrl)
                    if (torboxRes != null) {
                        resolvedUrl = torboxRes.playableUrl
                        isPlayableDirect = true
                    }
                }

                playable.add(
                    PlayableStreamOption(
                        qualityLabel = title,
                        format = if (isPlayableDirect) "hls" else "torrent",
                        isMuxed = stream.isMuxed,
                        videoUrl = resolvedUrl,
                        audioUrl = null,
                        providerType = ProviderType.TORRENT
                    )
                )
            } else {
                playable.add(
                    PlayableStreamOption(
                        qualityLabel = title,
                        format = stream.format,
                        isMuxed = stream.isMuxed,
                        videoUrl = rawUrl,
                        audioUrl = null,
                        providerType = ProviderType.OTHER
                    )
                )
            }
        }

        PipelineValidationResult(playableStreams = playable, failedLogs = failed)
    }
}
