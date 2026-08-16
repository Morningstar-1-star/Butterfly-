package com.example.plugin.jav.orchestrator

import com.example.plugin.jav.*
import com.example.plugin.jav.providers.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

object UnifiedJavOrchestrator {
    // Registered Providers (Repository & Source Integrations)
    val metadataProviders = mutableListOf<MetadataProvider>(
        JavinizerGoMetadataProvider(),
        AvmMetadataProvider(),
        JavdexMetadataProvider(),
        OpenAverMetadataProvider(),
        MdcxMetadataProvider(),
        FssMetadataProvider(),
        JavLibraryBusMetadataProvider(),
        Jav321MetadataProvider(),
        JavDbMetadataProvider(),
        JavMenuMetadataProvider(),
        GFriendsAvatarProvider(),
        AirAvBarcodeMetadataProvider(),
        ArzonCatalogMetadataProvider()
    )

    val streamProviders = mutableListOf<StreamProvider>(
        JavPyStreamResolver(),
        MissAvSurritStreamResolver(),
        JableTvStreamResolver(),
        AvgleApiStreamResolver(),
        YtDlpExtractorResolver(),
        MediaFusionStremioResolver(),
        CometStremioResolver()
    )

    val trailerProviders = mutableListOf<TrailerProvider>(
        DmmFreePvTrailerProvider()
    )

    val subtitleProviders = mutableListOf<SubtitleProvider>(
        SubtitleCatProvider(),
        OpenSubtitlesRestProvider(),
        BazarrSubHdProvider()
    )

    private val metadataCache = ConcurrentHashMap<String, JavMetadata>()
    private val streamCache = ConcurrentHashMap<String, List<JavStream>>()

    /**
     * Normalizes a raw JAV ID search string into a consistent format using AVM Engine ID normalization logic.
     * e.g., "abp 123" -> "ABP-123", "040110_123" -> "040110-123"
     */
    private fun normalizeJavId(rawId: String): String {
        var id = rawId.trim().uppercase()
        // Replace underscores or multiple spaces with a single dash
        id = id.replace("_", "-").replace(Regex("\\s+"), "-")
        // Insert dash between letters and numbers if missing (e.g., SSNI001 -> SSNI-001)
        if (!id.contains("-") && Regex("^[A-Z]+[0-9]+$").matches(id)) {
            id = id.replace(Regex("^([A-Z]+)([0-9]+)$"), "$1-$2")
        }
        return id
    }

    /**
     * Executes unified resolution pipeline for a JAV ID
     */
    suspend fun resolveJav(javId: String, title: String = ""): JavUnifiedResult = withContext(Dispatchers.IO) {
        val cleanJavId = normalizeJavId(javId)

        // 1. Metadata Aggregation
        val metadataDeferred = async { aggregateMetadata(cleanJavId) }

        // 2. Stream Resolution
        val streamsDeferred = async { resolveStreams(cleanJavId, title) }

        // 3. Trailer Resolution
        val trailersDeferred = async { resolveTrailers(cleanJavId) }

        // 4. Subtitle Resolution
        val subtitlesDeferred = async { resolveSubtitles(cleanJavId, title) }

        JavUnifiedResult(
            javId = cleanJavId,
            metadata = metadataDeferred.await(),
            streams = streamsDeferred.await(),
            trailers = trailersDeferred.await(),
            subtitles = subtitlesDeferred.await(),
            diagnostics = emptyList()
        )
    }

    /**
     * Aggregates metadata across all enabled metadata providers with confidence scoring
     */
    suspend fun aggregateMetadata(javId: String): JavMetadata? = withContext(Dispatchers.IO) {
        if (metadataCache.containsKey(javId)) {
            return@withContext metadataCache[javId]
        }

        val activeProviders = metadataProviders.filter { it.isEnabled }
        if (activeProviders.isEmpty()) return@withContext null

        val jobs = activeProviders.map { provider ->
            async {
                try {
                    val meta = withTimeoutOrNull(provider.timeoutMs) {
                        provider.fetchMetadata(javId)
                    }
                    if (meta != null) provider.id to meta else null
                } catch (e: Exception) {
                    null
                }
            }
        }

        val providerResults = jobs.awaitAll().filterNotNull()
        if (providerResults.isEmpty()) return@withContext null

        var bestTitle = javId
        var maxTitleScore = 0

        var bestCoverUrl = ""
        var maxCoverScore = 0

        var bestStudio = ""
        var maxStudioScore = 0

        var overallMaxConfidence = 0
        val providerScores = mutableMapOf<String, Int>()
        val allActors = mutableSetOf<String>()

        for ((pId, res) in providerResults) {
            val score = res.overallConfidenceScore
            providerScores[pId] = score
            if (score > overallMaxConfidence) {
                overallMaxConfidence = score
            }

            if (res.title.isNotBlank() && res.title != javId && score > maxTitleScore) {
                bestTitle = res.title
                maxTitleScore = score
            }
            if (res.coverUrl.isNotBlank() && score > maxCoverScore) {
                bestCoverUrl = res.coverUrl
                maxCoverScore = score
            }
            if (res.studio.isNotBlank() && score > maxStudioScore) {
                bestStudio = res.studio
                maxStudioScore = score
            }
            allActors.addAll(res.actors)
        }

        val canonical = JavMetadata(
            javId = javId,
            title = bestTitle,
            coverUrl = bestCoverUrl,
            studio = bestStudio,
            actors = allActors.toList(),
            overallConfidenceScore = overallMaxConfidence,
            providerScores = providerScores
        )

        metadataCache[javId] = canonical
        canonical
    }

    /**
     * Resolves playable streams across stream providers
     */
    suspend fun resolveStreams(javId: String, title: String): List<JavStream> = withContext(Dispatchers.IO) {
        val activeProviders = streamProviders.filter { it.isEnabled }
        if (activeProviders.isEmpty()) return@withContext emptyList()

        val jobs = activeProviders.map { provider ->
            async {
                try {
                    withTimeoutOrNull(provider.timeoutMs) {
                        provider.resolveStreams(javId, title)
                    } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        val rawStreams = jobs.awaitAll().flatten()
        
        // Filter & Validate Streams (Only valid http/https URLs)
        val validatedStreams = rawStreams.filter { stream ->
            stream.url.isNotBlank() && (stream.url.startsWith("http://") || stream.url.startsWith("https://"))
        }

        validatedStreams.distinctBy { it.url }
    }

    /**
     * Resolves preview trailers
     */
    suspend fun resolveTrailers(javId: String): List<JavTrailer> = withContext(Dispatchers.IO) {
        val activeProviders = trailerProviders.filter { it.isEnabled }
        val jobs = activeProviders.map { provider ->
            async {
                try {
                    withTimeoutOrNull(provider.timeoutMs) {
                        provider.fetchTrailers(javId)
                    } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        jobs.awaitAll().flatten().distinctBy { it.videoUrl }
    }

    /**
     * Resolves subtitles
     */
    suspend fun resolveSubtitles(javId: String, title: String): List<JavSubtitle> = withContext(Dispatchers.IO) {
        val activeProviders = subtitleProviders.filter { it.isEnabled }
        val jobs = activeProviders.map { provider ->
            async {
                try {
                    withTimeoutOrNull(provider.timeoutMs) {
                        provider.searchSubtitles(javId, title)
                    } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        jobs.awaitAll().flatten().distinctBy { it.url }
    }

    /**
     * Diagnostic testing engine for all providers
     */
    suspend fun runDiagnostics(testJavId: String = "IPX-123"): List<ProviderDiagnosticResult> = withContext(Dispatchers.IO) {
        val reports = mutableListOf<ProviderDiagnosticResult>()

        // 1. Test Metadata Providers
        for (provider in metadataProviders) {
            var state = ProviderStatusState.NO_RESULT
            var count = 0
            var msg = ""
            val time = measureTimeMillis {
                try {
                    val result = withTimeoutOrNull(provider.timeoutMs) {
                        provider.fetchMetadata(testJavId)
                    }
                    if (result != null && result.title.isNotBlank() && result.coverUrl.isNotBlank()) {
                        state = ProviderStatusState.SUCCESS
                        count = 1
                        msg = "Title: ${result.title.take(30)}"
                    } else {
                        state = ProviderStatusState.NO_RESULT
                        msg = "No valid metadata/cover"
                    }
                } catch (e: TimeoutCancellationException) {
                    state = ProviderStatusState.TIMEOUT
                    msg = "Timeout exceeded ${provider.timeoutMs}ms"
                } catch (e: Exception) {
                    state = ProviderStatusState.ERROR
                    msg = e.message ?: "Error"
                }
            }
            reports.add(
                ProviderDiagnosticResult(
                    providerId = provider.id,
                    providerName = provider.name,
                    capability = ProviderCapabilityType.METADATA,
                    status = state,
                    responseTimeMs = time,
                    detailMessage = msg,
                    itemFoundCount = count
                )
            )
        }

        // 2. Test Stream Providers
        for (provider in streamProviders) {
            var state = ProviderStatusState.NO_RESULT
            var count = 0
            var msg = ""
            val time = measureTimeMillis {
                try {
                    val streams = withTimeoutOrNull(provider.timeoutMs) {
                        provider.resolveStreams(testJavId, testJavId)
                    } ?: emptyList()
                    if (streams.isNotEmpty()) {
                        state = ProviderStatusState.SUCCESS
                        count = streams.size
                        msg = "Found ${streams.size} streams"
                    } else {
                        state = ProviderStatusState.NO_RESULT
                        msg = "No streams resolved"
                    }
                } catch (e: TimeoutCancellationException) {
                    state = ProviderStatusState.TIMEOUT
                    msg = "Timeout exceeded"
                } catch (e: Exception) {
                    state = ProviderStatusState.ERROR
                    msg = e.message ?: "Error"
                }
            }
            reports.add(
                ProviderDiagnosticResult(
                    providerId = provider.id,
                    providerName = provider.name,
                    capability = ProviderCapabilityType.STREAM,
                    status = state,
                    responseTimeMs = time,
                    detailMessage = msg,
                    itemFoundCount = count
                )
            )
        }

        // 3. Test Trailer Providers
        for (provider in trailerProviders) {
            var state = ProviderStatusState.NO_RESULT
            var count = 0
            var msg = ""
            val time = measureTimeMillis {
                try {
                    val trailers = withTimeoutOrNull(provider.timeoutMs) {
                        provider.fetchTrailers(testJavId)
                    } ?: emptyList()
                    if (trailers.isNotEmpty()) {
                        state = ProviderStatusState.SUCCESS
                        count = trailers.size
                        msg = "Found ${trailers.size} trailers"
                    } else {
                        state = ProviderStatusState.NO_RESULT
                        msg = "No trailers found"
                    }
                } catch (e: Exception) {
                    state = ProviderStatusState.ERROR
                    msg = e.message ?: "Error"
                }
            }
            reports.add(
                ProviderDiagnosticResult(
                    providerId = provider.id,
                    providerName = provider.name,
                    capability = ProviderCapabilityType.TRAILER,
                    status = state,
                    responseTimeMs = time,
                    detailMessage = msg,
                    itemFoundCount = count
                )
            )
        }

        // 4. Test Subtitle Providers
        for (provider in subtitleProviders) {
            var state = ProviderStatusState.NO_RESULT
            var count = 0
            var msg = ""
            val time = measureTimeMillis {
                try {
                    val subs = withTimeoutOrNull(provider.timeoutMs) {
                        provider.searchSubtitles(testJavId, testJavId)
                    } ?: emptyList()
                    if (subs.isNotEmpty()) {
                        state = ProviderStatusState.SUCCESS
                        count = subs.size
                        msg = "Found ${subs.size} subtitles"
                    } else {
                        state = ProviderStatusState.NO_RESULT
                        msg = "No subtitles found"
                    }
                } catch (e: Exception) {
                    state = ProviderStatusState.ERROR
                    msg = e.message ?: "Error"
                }
            }
            reports.add(
                ProviderDiagnosticResult(
                    providerId = provider.id,
                    providerName = provider.name,
                    capability = ProviderCapabilityType.SUBTITLE,
                    status = state,
                    responseTimeMs = time,
                    detailMessage = msg,
                    itemFoundCount = count
                )
            )
        }

        reports
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        metadataProviders.find { it.id == providerId }?.isEnabled = enabled
        streamProviders.find { it.id == providerId }?.isEnabled = enabled
        trailerProviders.find { it.id == providerId }?.isEnabled = enabled
        subtitleProviders.find { it.id == providerId }?.isEnabled = enabled
    }
}
