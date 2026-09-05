package com.example.resolver

import android.content.Context
import android.util.Log
import com.example.model.MediaIdentity
import com.example.resolver.dedup.SourceDeduplicator
import com.example.resolver.health.ProviderIsolationController
import com.example.resolver.providers.CometSourceProvider
import com.example.resolver.providers.Hanime1SourceProvider
import com.example.resolver.providers.HQPornerSourceProvider
import com.example.resolver.providers.JableMissAvSourceProvider
import com.example.resolver.providers.JavPySourceProvider
import com.example.resolver.providers.MagnetioSourceProvider
import com.example.resolver.providers.MediaFusionSourceProvider
import com.example.resolver.providers.NuvioDirectSourceProvider
import com.example.resolver.providers.SpankBangSourceProvider
import com.example.resolver.providers.YarrSourceProvider
import com.example.resolver.embed.TwoEmbedProvider
import com.example.resolver.embed.VidlinkEmbedProvider
import com.example.resolver.embed.VidrockEmbedProvider
import com.example.resolver.embed.VidsrcMeEmbedProvider
import com.example.resolver.embed.VidsrcSbsEmbedProvider
import com.example.resolver.embed.VidsrcToEmbedProvider
import com.example.resolver.providers.EmbedSourceProviderAdapter
import com.example.resolver.validation.MediaIdentityValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Unified Source Resolver 2.0 (Inspired by Cauldron & Nuvio aggregation architectures).
 *
 * Core Orchestration:
 * - Concurrent independent provider execution with strict fault isolation via [ProviderIsolationController].
 * - Dynamic capability and media-type routing.
 * - Strict media identity validation rejecting false positives via [MediaIdentityValidator].
 * - 4-Tier canonical deduplication & candidate merging via [SourceDeduplicator].
 * - Composite multi-factor scoring and progressive flow emission via [SourceRankingEngine].
 */
class UnifiedSourceResolver(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedSourceResolver"

        @Volatile
        private var instance: UnifiedSourceResolver? = null

        fun getInstance(context: Context): UnifiedSourceResolver {
            return instance ?: synchronized(this) {
                instance ?: UnifiedSourceResolver(context.applicationContext).also { instance = it }
            }
        }
    }

    private val vegaAdapter = VegaSourceAdapter(context)
    private val torrentAdapter = TorrentSourceAdapter()
    private val nuvioDirectProvider = NuvioDirectSourceProvider()
    private val jableMissAvProvider = JableMissAvSourceProvider()
    private val spankBangProvider = SpankBangSourceProvider()
    private val hanime1Provider = Hanime1SourceProvider()
    private val hqPornerProvider = HQPornerSourceProvider()
    private val javPyProvider = JavPySourceProvider()
    private val mediaFusionProvider = MediaFusionSourceProvider()
    private val cometProvider = CometSourceProvider()
    private val yarrProvider = YarrSourceProvider()
    private val magnetioProvider = MagnetioSourceProvider()
    private val bunkrProvider = com.example.resolver.providers.BunkrSourceProvider(context)
    private val telegramProvider = com.example.resolver.providers.TelegramSourceProvider(context)
    private val megaProvider = com.example.resolver.providers.MegaSourceProvider(context)

    // Embed Providers
    private val vidlinkProvider = EmbedSourceProviderAdapter(VidlinkEmbedProvider())
    private val vidsrcSbsProvider = EmbedSourceProviderAdapter(VidsrcSbsEmbedProvider())
    private val vidrockProvider = EmbedSourceProviderAdapter(VidrockEmbedProvider())
    private val vidsrcToProvider = EmbedSourceProviderAdapter(VidsrcToEmbedProvider())
    private val twoEmbedProvider = EmbedSourceProviderAdapter(TwoEmbedProvider())
    private val vidsrcMeProvider = EmbedSourceProviderAdapter(VidsrcMeEmbedProvider())

    val activeProviders: List<SourceProvider>
        get() = listOf(
            vidsrcSbsProvider,
            vidrockProvider,
            vidlinkProvider,
            vidsrcToProvider,
            twoEmbedProvider,
            vidsrcMeProvider,
            nuvioDirectProvider,
            mediaFusionProvider,
            cometProvider,
            yarrProvider,
            magnetioProvider,
            bunkrProvider,
            telegramProvider,
            megaProvider,
            jableMissAvProvider,
            spankBangProvider,
            hanime1Provider,
            hqPornerProvider,
            javPyProvider,
            vegaAdapter,
            torrentAdapter
        ).filter { it.isEnabled }.sortedByDescending { it.priority }

    /**
     * Resolves sources across all compatible providers concurrently with full fault isolation.
     * Emits progressively ranked candidate lists.
     */
    fun resolveSources(
        identity: MediaIdentity,
        requiredCapabilities: Set<ProviderCapability> = emptySet()
    ): Flow<List<SourceCandidate>> = channelFlow {
        val collectedRaw = mutableListOf<SourceCandidate>()

        // Filter providers matching media type and required capabilities
        val eligibleProviders = activeProviders.filter { provider ->
            val matchesMedia = provider.supportedMediaTypes.contains(identity.mediaType)
            val matchesCaps = requiredCapabilities.isEmpty() || provider.capabilities.containsAll(requiredCapabilities)
            matchesMedia && matchesCaps
        }

        if (eligibleProviders.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }

        supervisorScope {
            // Launch parallel isolated scraper coroutines
            eligibleProviders.forEach { provider ->
                launch {
                    try {
                        ProviderIsolationController.executeIsolated(provider, identity)
                            .collect { candidates ->
                                if (candidates.isNotEmpty()) {
                                    // 1. Strict Media Identity Validation
                                    val validCandidates = candidates.filter { candidate ->
                                        val outcome = MediaIdentityValidator.validateCandidate(candidate, identity)
                                        if (!outcome.isValid) {
                                            Log.d(TAG, "Rejected candidate '${candidate.title}' from ${candidate.providerName}: ${outcome.reason}")
                                        }
                                        outcome.isValid
                                    }

                                    if (validCandidates.isNotEmpty()) {
                                        synchronized(collectedRaw) {
                                            collectedRaw.addAll(validCandidates)
                                        }

                                        // 2. Stronger Multi-Tier Deduplication & Candidate Merging
                                        val deduplicated = SourceDeduplicator.deduplicateCandidates(collectedRaw)

                                        // 3. Multi-Factor Composite Ranking
                                        val ranked = SourceRankingEngine.rank(deduplicated)

                                        send(ranked)
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        Log.w(TAG, "Isolated provider execution note for ${provider.displayName}: ${e.message}")
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
