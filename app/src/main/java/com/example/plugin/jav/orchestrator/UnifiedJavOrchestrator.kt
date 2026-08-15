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
        OpenSubtitlesRestProvider()
    )

    private val metadataCache = ConcurrentHashMap<String, JavMetadata>()
    private val streamCache = ConcurrentHashMap<String, List<JavStream>>()

    /**
     * Enables or disables a registered provider by ID.
     */
    fun setProviderEnabled(providerId: String, enabled: Boolean): Boolean {
        val provider = (
            metadataProviders +
            streamProviders +
            trailerProviders +
            subtitleProviders
        ).firstOrNull { it.id == providerId }

        if (provider == null) return false

        provider.isEnabled = enabled

        // Clear cached results so the next resolution respects the new state.
        metadataCache.clear()
        streamCache.clear()

        return true
    }

    /**
     * Executes unified resolution pipeline for a JAV ID
     */
    suspend fun resolveJav(
        javId: String,
        title: String = ""
    ): JavUnifiedResult = withContext(Dispatchers.IO) {
        val cleanJavId = javId.trim().uppercase()

        val metadataDeferred = async {
            aggregateMetadata(cleanJavId)
        }

        val streamsDeferred = async {
            resolveStreams(cleanJavId, title)
        }

        val trailersDeferred = async {
            resolveTrailers(cleanJavId)
        }

        val subtitlesDeferred = async {
            resolveSubtitles(cleanJavId, title)
        }

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
     * Aggregates metadata across all enabled metadata providers.
     */
    suspend fun aggregateMetadata(
        javId: String
    ): JavMetadata? = withContext(Dispatchers.IO) {

        if (metadataCache.containsKey(javId)) {
            return@withContext metadataCache[javId]
        }

        val activeProviders = metadataProviders.filter { it.isEnabled }

        if (activeProviders.isEmpty()) {
            return@withContext null
        }

        val jobs = activeProviders.map { provider ->
            async {
                try {
                    val meta = withTimeoutOrNull(provider.timeoutMs) {
                        provider.fetchMetadata(javId)
                    }

                    if (meta != null) {
                        provider.id to meta
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

        val providerResults = jobs.awaitAll().filterNotNull()

        if (providerResults.isEmpty()) {
            return@withContext null
        }

        var bestTitle = javId
        var maxTitleScore = 0

        var bestCoverUrl = ""
        var maxCoverScore = 0

        var bestStudio = ""
        var maxStudioScore = 0

        var overallMaxConfidence = 0

        val providerScores = mutableMapOf<String, Int>()
        val allActors = mutableSetOf<String>()

        for ((providerId, result) in providerResults) {
            val score = result.overallConfidenceScore

            providerScores[providerId] = score

            if (score > overallMaxConfidence) {
                overallMaxConfidence = score
            }

            if (
                result.title.isNotBlank() &&
                result.title != javId &&
                score > maxTitleScore
            ) {
                bestTitle = result.title
                maxTitleScore = score
            }

            if (
                result.coverUrl.isNotBlank() &&
                score > maxCoverScore
            ) {
                bestCoverUrl = result.coverUrl
                maxCoverScore = score
            }

            if (
                result.studio.isNotBlank() &&
                score > maxStudioScore
            ) {
                bestStudio = result.studio
                maxStudioScore = score
            }

            allActors.addAll(result.actors)
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
     * Resolves playable streams across stream providers.
     */
    suspend fun resolveStreams(
        javId: String,
        title: String
    ): List<JavStream> = withContext(Dispatchers.IO) {

        val activeProviders = streamProviders.filter { it.isEnabled }

        if (activeProviders.isEmpty()) {
            return@withContext emptyList()
        }

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

        val validatedStreams = rawStreams.filter { stream ->
            stream.url.isNotBlank() &&
                (
                    stream.url.startsWith("http://") ||
                    stream.url.startsWith("https://")
                )
        }

        validatedStreams.distinctBy { it.url }
    }

    /**
     * Resolves preview trailers.
     */
    suspend fun resolveTrailers(
        javId: String
    ): List<JavTrailer> = withContext(Dispatchers.IO) {

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

        jobs.awaitAll()
            .flatten()
            .distinctBy { it.videoUrl }
    }

    /**
     * Resolves subtitles.
     */
    suspend fun resolveSubtitles(
        javId: String,
        title: String
    ): List<JavSubtitle> = withContext(Dispatchers.IO) {

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

        jobs.awaitAll()
            .flatten()
            .distinctBy { it.url }
    }

    /**
     * Diagnostic testing engine for all providers.
     */
    suspend fun runDiagnostics(
        testJavId: String = "IPX-123"
    ): List<ProviderDiagnosticResult> = withContext(Dispatchers.IO) {

        val reports = mutableListOf<ProviderDiagnosticResult>()

        // 1. Test Metadata Providers
        for (provider in metadataProviders) {

            var state = ProviderStatusState.NO_RESULT
            var count = 0
            var message = ""

            val time = measureTimeMillis {

                try {
                    val result = withTimeoutOrNull(provider.timeoutMs) {
                        provider.fetchMetadata(testJavId)
                    }

                    if (
                        result != null &&
                        result.title.isNotBlank() &&
                        result.coverUrl.isNotBlank()
                    ) {
                        state = ProviderStatusState.SUCCESS
                        count = 1
                        message = "Title: ${result.title.take(30)}"
                    } else {
                        state = ProviderStatusState.NO_RESULT
                        message = "No valid metadata/cover"
                    }

                } catch (e: TimeoutCancellationException) {
                    state = ProviderStatusState.TIMEOUT
                    message = "Timeout exceeded ${provider.timeoutMs}ms"

                } catch (e: Exception) {
                    state = ProviderStatusState.ERROR
                    message = e.message ?: "Error"
                }
            }

            reports.add(
                ProviderDiagnosticResult(
                    providerId = provider.id,
                    providerName = provider.name,
                    capability = ProviderCapabilityType.METADATA,
                    status = state,
                    responseTimeMs = time,
                    detailMessage = message,
                    itemFoundCount = count
                )
            )
        }

        // 2. Test Stream Providers
        for (provider in streamProviders) {

            var state = ProviderStatusState.NO_RESULT
            var count = 0
            var message = ""

            val time = measureTimeMillis {

                try {
                    val streams = withTimeoutOrNull(provider.timeoutMs) {
                        provider.resolveStreams(testJavId, testJavId)
                    } ?: emptyList()

                    if (streams.isNotEmpty()) {
                        state = ProviderStatusState.SUCCESS
                        count = streams.size
                        message = "Found ${streams.size} streams"
                    } else {
                        state = ProviderStatusState.NO_RESULT
                        message = "No streams resolved"
                    }

                } catch (e: TimeoutCancellationException) {
                    state = ProviderStatusState.TIMEOUT
                    message = "Timeout exceeded"

                } catch (e: Exception) {
                    state = ProviderStatusState.ERROR
                    message = e.message ?: "Error"
                }
            }

            reports.add(
                ProviderDiagnosticResult(
                    providerId = provider.id,
                    providerName = provider.name,
                    capability = ProviderCapabilityType.STREAM,
                    status = state,
                    responseTimeMs = time,
                    detailMessage = message,
                    itemFoundCount = count
                )
            )
        }

        // 3. Test Trailer Providers
        for (provider in trailerProviders) {

            var state = ProviderStatusState.NO_RESULT
            var count = 0
            var message = ""

            val time = measureTimeMillis {

                try {
                    val trailers = withTimeoutOrNull(provider.timeoutMs) {
                        provider.fetchTrailers(testJavId)
                    } ?: emptyList()

                    if (trailers.isNotEmpty()) {
                        state = ProviderStatusState.SUCCESS
                        count = trailers.size
                        message = "Found ${trailers.size} trailers"
                    } else {
                        state = ProviderStatusState.NO_RESULT
                        message = "No trailers found"
                    }

                } catch (e: Exception) {
                    state = ProviderStatusState.ERROR
                    message = e.message ?: "Error"
                }
            }

            reports.add(
                ProviderDiagnosticResult(
                    providerId = provider.id,
                    providerName = provider.name,
                    capability = ProviderCapabilityType.TRAILER,
                    status = state,
                    responseTimeMs = time,
                    detailMessage = message,
                    itemFoundCount = count
                )
            )
        }

        // 4. Test Subtitle Providers
        for (provider in subtitleProviders) {

            var state = ProviderStatusState.NO_RESULT
            var count = 0
            var message = ""

            val time = measureTimeMillis {

                try {
                    val subtitles = withTimeoutOrNull(provider.timeoutMs) {
                        provider.searchSubtitles(testJavId, testJavId)
                    } ?: emptyList()

                    if (subtitles.isNotEmpty()) {
                        state = ProviderStatusState.SUCCESS
                        count = subtitles.size
                        message = "Found ${subtitles.size} subtitles"
                    } else {
                        state = ProviderStatusState.NO_RESULT
                        message = "No subtitles found"
                    }

                } catch (e: Exception) {
                    state = ProviderStatusState.ERROR
                    message = e.message ?: "Error"
                }
            }

            reports.add(
                ProviderDiagnosticResult(
                    providerId = provider.id,
                    providerName = provider.name,
                    capability = ProviderCapabilityType.SUBTITLE,
                    status = state,
                    responseTimeMs = time,
                    detailMessage = message,
                    itemFoundCount = count
                )
            )
        }

        reports
    }
}
