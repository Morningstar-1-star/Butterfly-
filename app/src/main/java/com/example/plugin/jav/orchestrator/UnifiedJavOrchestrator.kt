package com.example.plugin.jav.orchestrator

import com.example.plugin.jav.*
import com.example.plugin.jav.providers.*
import com.example.plugin.manager.StreamValidator
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

object UnifiedJavOrchestrator {
    // Registered Providers
    val metadataProviders = mutableListOf<MetadataProvider>(
        JavinizerGoProvider(),
        AvmProvider(),
        JavdexProvider(),
        OpenAverProvider(),
        GFriendsProvider(),
        MdcxProvider(),
        FssProvider()
    )

    val streamProviders = mutableListOf<StreamProvider>(
        JableMissAvResolver(),
        JavPyResolver(),
        YtDlpStreamResolver(),
        MediaFusionJavResolver(),
        CometJavResolver()
    )

    val trailerProviders = mutableListOf<TrailerProvider>(
        JavPreviewProvider()
    )

    val subtitleProviders = mutableListOf<SubtitleProvider>(
        BazarrCatalogSubtitleProvider()
    )

    private val metadataCache = ConcurrentHashMap<String, JavMetadata>()
    private val streamCache = ConcurrentHashMap<String, List<JavStream>>()

    /**
     * Executes unified resolution pipeline for a JAV ID
     */
    suspend fun resolveJav(javId: String, title: String = ""): JavUnifiedResult = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()

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
                    withTimeoutOrNull(provider.timeoutMs) {
                        provider.fetchMetadata(javId)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

        val results = jobs.awaitAll().filterNotNull()
        if (results.isEmpty()) return@withContext null

        // Field-level merging using confidence scoring
        var bestTitle = javId
        var bestCoverUrl = ""
        var bestStudio = ""
        var maxConfidence = 0
        val providerScores = mutableMapOf<String, Int>()

        for (res in results) {
            if (res.title.isNotBlank() && res.title != javId) {
                bestTitle = res.title
            }
            if (res.coverUrl.isNotBlank() && bestCoverUrl.isBlank()) {
                bestCoverUrl = res.coverUrl
            }
            if (res.studio.isNotBlank() && bestStudio.isBlank()) {
                bestStudio = res.studio
            }
            if (res.overallConfidenceScore > maxConfidence) {
                maxConfidence = res.overallConfidenceScore
            }
            providerScores[res.javId] = res.overallConfidenceScore
        }

        val canonical = JavMetadata(
            javId = javId,
            title = bestTitle,
            coverUrl = bestCoverUrl,
            studio = bestStudio,
            overallConfidenceScore = maxConfidence,
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
        
        // Filter & Validate Streams
        val validatedStreams = rawStreams.filter { stream ->
            if (stream.url.isBlank()) false
            else true
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
}
