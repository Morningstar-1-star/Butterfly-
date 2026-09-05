package com.example.resolver.registry

import android.util.Log
import com.example.model.MediaType
import com.example.resolver.ProviderCapability
import com.example.resolver.SourceProvider
import com.example.resolver.health.ProviderHealthManager
import com.example.resolver.mirror.MirrorManager
import java.util.concurrent.ConcurrentHashMap

enum class ProviderCategory {
    MAINSTREAM_VIDEO, // YouTube, Vimeo, Dailymotion, Bilibili, Twitch
    ANIME,            // Hanime1, HiAnime, AniWatch, Nyaa
    ADULT,            // Pornhub, XVideos, XHamster, Eporner, Jable, MissAV, SpankBang, HQPorner, etc.
    TORRENT_DEBRID,   // MediaFusion, Comet, Yarr, Magnetio, Torrentio
    DIRECT_STREAM,    // Nuvio, Vidsrc, AutoEmbed, SuperStream
    ARCHIVE           // Archive.org
}

data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val category: ProviderCategory,
    val baseDomain: String,
    val mirrors: List<String> = emptyList(),
    val capabilities: Set<ProviderCapability> = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM),
    val supportedMediaTypes: Set<MediaType> = setOf(MediaType.MOVIE, MediaType.TV, MediaType.ANIME, MediaType.JAV, MediaType.VIDEO, MediaType.UNKNOWN),
    val supportsDirect: Boolean = true,
    val supportsHls: Boolean = false,
    val supportsDash: Boolean = false,
    val supportsSubtitles: Boolean = false,
    val supportsStoryboards: Boolean = false,
    val supportsSearch: Boolean = true,
    val supportsHomeFeed: Boolean = true,
    val supportedQualities: List<String> = listOf("1080p", "720p", "480p", "360p"),
    val priority: Int = 50,
    val isEnabled: Boolean = true,
    val fallbackProviderIds: List<String> = emptyList(),
    val sourceProvider: SourceProvider? = null
)

/**
 * Universal Provider Registry 2.0 (Inspired by Cauldron & Nuvio provider abstraction).
 *
 * Central configuration holding all providers, capabilities, mirrors, priorities,
 * health states, and fallback chains. Adding future sources is purely a registry
 * adapter registration without modifying the core resolver pipeline.
 */
object ProviderRegistry {
    private const val TAG = "ProviderRegistry"

    private val providers = ConcurrentHashMap<String, ProviderDescriptor>()

    init {
        registerDefaultProviders()
    }

    private fun registerDefaultProviders() {
        // Mainstream Providers
        register(
            ProviderDescriptor(
                id = "youtube",
                displayName = "YouTube",
                category = ProviderCategory.MAINSTREAM_VIDEO,
                baseDomain = "https://www.youtube.com",
                capabilities = setOf(
                    ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.HLS,
                    ProviderCapability.DASH, ProviderCapability.SUBTITLE, ProviderCapability.CAPABILITY_4K,
                    ProviderCapability.LIVE, ProviderCapability.DOWNLOAD
                ),
                supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.ANIME, MediaType.MOVIE),
                supportsDirect = true,
                supportsHls = true,
                supportsDash = true,
                supportsSubtitles = true,
                supportsStoryboards = true,
                supportedQualities = listOf("4K", "1440p", "1080p", "720p", "480p", "360p"),
                priority = 100
            )
        )
        register(
            ProviderDescriptor(
                id = "twitch",
                displayName = "Twitch",
                category = ProviderCategory.MAINSTREAM_VIDEO,
                baseDomain = "https://www.twitch.tv",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.HLS, ProviderCapability.LIVE),
                supportedMediaTypes = setOf(MediaType.VIDEO),
                supportsHls = true,
                supportedQualities = listOf("1080p60", "720p60", "480p", "360p", "160p"),
                priority = 92
            )
        )
        register(
            ProviderDescriptor(
                id = "bigo",
                displayName = "Bigo Live",
                category = ProviderCategory.MAINSTREAM_VIDEO,
                baseDomain = "https://www.bigo.tv",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.HLS, ProviderCapability.LIVE),
                supportedMediaTypes = setOf(MediaType.VIDEO),
                supportsHls = true,
                supportedQualities = listOf("1080p", "720p", "480p", "360p"),
                priority = 91
            )
        )
        register(
            ProviderDescriptor(
                id = "bilibili",
                displayName = "Bilibili",
                category = ProviderCategory.MAINSTREAM_VIDEO,
                baseDomain = "https://www.bilibili.com",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DASH, ProviderCapability.SUBTITLE),
                supportedMediaTypes = setOf(MediaType.ANIME, MediaType.VIDEO),
                supportsDirect = true,
                supportsDash = true,
                supportsSubtitles = true,
                priority = 90
            )
        )
        register(
            ProviderDescriptor(
                id = "dailymotion",
                displayName = "Dailymotion",
                category = ProviderCategory.MAINSTREAM_VIDEO,
                baseDomain = "https://www.dailymotion.com",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.HLS),
                supportedMediaTypes = setOf(MediaType.VIDEO),
                supportsHls = true,
                priority = 85
            )
        )
        register(
            ProviderDescriptor(
                id = "vimeo",
                displayName = "Vimeo",
                category = ProviderCategory.MAINSTREAM_VIDEO,
                baseDomain = "https://vimeo.com",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.HLS, ProviderCapability.CAPABILITY_4K),
                supportedMediaTypes = setOf(MediaType.VIDEO),
                supportsHls = true,
                priority = 80
            )
        )

        // Direct HTTP Streaming (Nuvio / Scrapers)
        register(
            ProviderDescriptor(
                id = "bunkr",
                displayName = "Bunkr Albums & Direct CDN",
                category = ProviderCategory.DIRECT_STREAM,
                baseDomain = "https://bunkr.cr",
                mirrors = listOf(
                    "https://bunkr.cr", "https://bunkr.is", "https://bunkr.la", "https://bunkr.fi",
                    "https://bunkr.ph", "https://bunkr.site", "https://bunkrr.org", "https://bunkr.ru"
                ),
                capabilities = setOf(
                    ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP,
                    ProviderCapability.HLS, ProviderCapability.DOWNLOAD
                ),
                supportedMediaTypes = setOf(MediaType.VIDEO, MediaType.MOVIE, MediaType.ANIME, MediaType.JAV, MediaType.UNKNOWN),
                supportsDirect = true,
                supportsHls = true,
                priority = 95
            )
        )
        register(
            ProviderDescriptor(
                id = "nuvio_direct",
                displayName = "Nuvio Direct HTTP/HLS",
                category = ProviderCategory.DIRECT_STREAM,
                baseDomain = "https://vidsrc.to",
                capabilities = setOf(
                    ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP,
                    ProviderCapability.HLS, ProviderCapability.SUBTITLE, ProviderCapability.CAPABILITY_4K
                ),
                supportedMediaTypes = setOf(MediaType.MOVIE, MediaType.TV, MediaType.ANIME),
                supportsDirect = true,
                supportsHls = true,
                supportsSubtitles = true,
                priority = 92,
                fallbackProviderIds = listOf("mediafusion", "comet")
            )
        )

        // Torrent & Debrid Aggregators (Cauldron / YARR / Comet / MediaFusion)
        register(
            ProviderDescriptor(
                id = "mediafusion",
                displayName = "MediaFusion Multi-Index",
                category = ProviderCategory.TORRENT_DEBRID,
                baseDomain = "https://mediafusion.elfhosted.com",
                capabilities = setOf(
                    ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.TORRENT,
                    ProviderCapability.DIRECT_HTTP, ProviderCapability.HLS, ProviderCapability.CAPABILITY_4K,
                    ProviderCapability.HDR, ProviderCapability.MULTI_AUDIO
                ),
                supportedMediaTypes = setOf(MediaType.MOVIE, MediaType.TV, MediaType.ANIME),
                priority = 88,
                fallbackProviderIds = listOf("comet", "yarr", "magnetio")
            )
        )
        register(
            ProviderDescriptor(
                id = "comet",
                displayName = "Comet Torrent / Debrid Indexer",
                category = ProviderCategory.TORRENT_DEBRID,
                baseDomain = "https://comet.elfhosted.com",
                capabilities = setOf(
                    ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.TORRENT,
                    ProviderCapability.DIRECT_HTTP, ProviderCapability.CAPABILITY_4K, ProviderCapability.HDR
                ),
                supportedMediaTypes = setOf(MediaType.MOVIE, MediaType.TV, MediaType.ANIME),
                priority = 86,
                fallbackProviderIds = listOf("mediafusion", "yarr", "magnetio")
            )
        )
        register(
            ProviderDescriptor(
                id = "magnetio",
                displayName = "Magnetio Multi-Indexer",
                category = ProviderCategory.TORRENT_DEBRID,
                baseDomain = "magnet:",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.TORRENT, ProviderCapability.CAPABILITY_4K),
                supportedMediaTypes = setOf(MediaType.MOVIE, MediaType.TV, MediaType.ANIME),
                priority = 85,
                fallbackProviderIds = listOf("yarr", "comet")
            )
        )
        register(
            ProviderDescriptor(
                id = "yarr",
                displayName = "YARR Torrent Aggregator",
                category = ProviderCategory.TORRENT_DEBRID,
                baseDomain = "https://yarr.fly.dev",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.TORRENT, ProviderCapability.CAPABILITY_4K),
                supportedMediaTypes = setOf(MediaType.MOVIE, MediaType.TV, MediaType.ANIME),
                priority = 84,
                fallbackProviderIds = listOf("magnetio", "comet")
            )
        )

        // Adult & JAV Providers
        register(
            ProviderDescriptor(
                id = "jable",
                displayName = "JableTV (HLS 1080p)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://jable.tv",
                mirrors = listOf("https://jable.tv", "https://jable.to", "https://jable.net"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.HLS, ProviderCapability.CAPABILITY_4K),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsHls = true,
                supportsStoryboards = true,
                priority = 96,
                fallbackProviderIds = listOf("missav", "spankbang", "eporner")
            )
        )
        register(
            ProviderDescriptor(
                id = "missav",
                displayName = "MissAV (Surrit Fast CDN)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://missav.ai",
                mirrors = listOf("https://missav.ai", "https://missav.ws", "https://missav.com"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.HLS),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsHls = true,
                priority = 95,
                fallbackProviderIds = listOf("jable", "spankbang", "eporner")
            )
        )
        register(
            ProviderDescriptor(
                id = "hanime1",
                displayName = "Hanime1 (Anime HLS)",
                category = ProviderCategory.ANIME,
                baseDomain = "https://hanime1.me",
                mirrors = listOf("https://hanime1.me", "https://hanime1.com"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.HLS, ProviderCapability.SUBTITLE),
                supportedMediaTypes = setOf(MediaType.ANIME, MediaType.JAV, MediaType.VIDEO),
                supportsHls = true,
                supportsSubtitles = true,
                priority = 94,
                fallbackProviderIds = listOf("hianime", "aniwatch")
            )
        )
        register(
            ProviderDescriptor(
                id = "spankbang",
                displayName = "SpankBang (4K/1080p Direct)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://spankbang.com",
                mirrors = listOf("https://spankbang.com", "https://spankbang.party", "https://spankbang.porn"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP, ProviderCapability.HLS, ProviderCapability.CAPABILITY_4K),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsDirect = true,
                supportsHls = true,
                supportedQualities = listOf("4K", "1080p", "720p", "480p", "360p", "240p"),
                priority = 93,
                fallbackProviderIds = listOf("hqporner", "eporner", "xvideos")
            )
        )
        register(
            ProviderDescriptor(
                id = "hqporner",
                displayName = "HQPorner (Ultra HD)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://hqporner.com",
                mirrors = listOf("https://hqporner.com", "https://hqporner.tv"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP, ProviderCapability.CAPABILITY_4K),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsDirect = true,
                supportedQualities = listOf("4K", "1080p", "720p"),
                priority = 92,
                fallbackProviderIds = listOf("spankbang", "eporner")
            )
        )
        register(
            ProviderDescriptor(
                id = "eporner",
                displayName = "Eporner (4K/HD Direct)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://www.eporner.com",
                mirrors = listOf("https://www.eporner.com"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP, ProviderCapability.CAPABILITY_4K),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsDirect = true,
                supportedQualities = listOf("4K", "1080p", "720p", "480p", "360p"),
                priority = 91,
                fallbackProviderIds = listOf("pornhub", "xvideos")
            )
        )
        register(
            ProviderDescriptor(
                id = "pornhub",
                displayName = "Pornhub (HLS Streams)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://www.pornhub.com",
                mirrors = listOf("https://www.pornhub.com", "https://www.pornhubpremium.com"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.HLS),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsHls = true,
                priority = 90,
                fallbackProviderIds = listOf("eporner", "xhamster")
            )
        )
        register(
            ProviderDescriptor(
                id = "xhamster",
                displayName = "xHamster (HLS Streams)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://xhamster.com",
                mirrors = listOf("https://xhamster.com", "https://xhamster.desi", "https://xhamster2.com"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.HLS),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsHls = true,
                priority = 89,
                fallbackProviderIds = listOf("eporner", "xvideos")
            )
        )
        register(
            ProviderDescriptor(
                id = "xvideos",
                displayName = "XVideos (HLS/Direct)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://www.xvideos.com",
                mirrors = listOf("https://www.xvideos.com", "https://www.xvideos2.com"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP, ProviderCapability.HLS),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsHls = true,
                supportsDirect = true,
                priority = 88,
                fallbackProviderIds = listOf("eporner", "youporn")
            )
        )
        register(
            ProviderDescriptor(
                id = "youporn",
                displayName = "YouPorn (HLS/Direct)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://www.youporn.com",
                mirrors = listOf("https://www.youporn.com"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP, ProviderCapability.HLS),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsHls = true,
                priority = 87,
                fallbackProviderIds = listOf("eporner", "redtube")
            )
        )
        register(
            ProviderDescriptor(
                id = "redtube",
                displayName = "RedTube (HLS/Direct)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://www.redtube.com",
                mirrors = listOf("https://www.redtube.com"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP, ProviderCapability.HLS),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsHls = true,
                priority = 86,
                fallbackProviderIds = listOf("eporner", "4tube")
            )
        )
        register(
            ProviderDescriptor(
                id = "4tube",
                displayName = "4Tube (HLS/Direct)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://www.4tube.com",
                mirrors = listOf("https://www.4tube.com"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP, ProviderCapability.HLS),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsHls = true,
                priority = 85,
                fallbackProviderIds = listOf("eporner", "pornhub")
            )
        )
        register(
            ProviderDescriptor(
                id = "beeg",
                displayName = "Beeg (Direct HD)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://beeg.com",
                mirrors = listOf("https://beeg.com"),
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsDirect = true,
                priority = 84,
                fallbackProviderIds = listOf("eporner")
            )
        )
        register(
            ProviderDescriptor(
                id = "rule34video",
                displayName = "Rule34Video (Animation)",
                category = ProviderCategory.ADULT,
                baseDomain = "https://rule34video.com",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.STREAM, ProviderCapability.DIRECT_HTTP),
                supportedMediaTypes = setOf(MediaType.JAV, MediaType.VIDEO),
                supportsDirect = true,
                priority = 83
            )
        )
    }

    fun register(descriptor: ProviderDescriptor) {
        providers[descriptor.id.lowercase()] = descriptor
        if (descriptor.mirrors.isNotEmpty()) {
            MirrorManager.registerMirror(
                com.example.resolver.mirror.MirrorConfig(
                    providerId = descriptor.id,
                    primaryDomain = descriptor.baseDomain,
                    mirrors = descriptor.mirrors
                )
            )
        }
        Log.d(TAG, "Registered provider: ${descriptor.displayName} (${descriptor.id}) with capabilities ${descriptor.capabilities}")
    }

    fun get(providerId: String): ProviderDescriptor? {
        return providers[providerId.lowercase()]
    }

    fun getAll(): List<ProviderDescriptor> = providers.values.toList()

    fun getActiveProviders(): List<ProviderDescriptor> {
        return providers.values
            .filter { it.isEnabled }
            .sortedWith { p1, p2 ->
                val health1 = ProviderHealthManager.getHealthScore(p1.id)
                val health2 = ProviderHealthManager.getHealthScore(p2.id)

                val effectiveScore1 = (p1.priority * 10) + (health1 * 5)
                val effectiveScore2 = (p2.priority * 10) + (health2 * 5)

                effectiveScore2.compareTo(effectiveScore1)
            }
    }

    fun getProvidersForMedia(
        mediaType: MediaType,
        requiredCapabilities: Set<ProviderCapability> = emptySet()
    ): List<ProviderDescriptor> {
        return getActiveProviders().filter { descriptor ->
            val matchesMedia = descriptor.supportedMediaTypes.contains(mediaType)
            val matchesCaps = requiredCapabilities.isEmpty() || descriptor.capabilities.containsAll(requiredCapabilities)
            matchesMedia && matchesCaps
        }
    }

    fun getFallbackProviders(providerId: String): List<ProviderDescriptor> {
        val descriptor = get(providerId) ?: return emptyList()
        return descriptor.fallbackProviderIds.mapNotNull { get(it) }
    }
}
