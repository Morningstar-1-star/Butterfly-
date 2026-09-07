package com.example.vega

data class InstalledVegaProvider(
    val id: String,
    val name: String,
    val isEnabled: Boolean = true,
    val installedAtMs: Long = System.currentTimeMillis()
)

data class VegaSearchResult(
    val id: String,
    val title: String,
    val link: String,
    val imageUrl: String? = null,
    val providerId: String,
    val extraInfo: String? = null
)

data class VegaDirectLink(
    val title: String,
    val link: String,
    val type: String = "movie", // "movie", "episode", "series"
    val description: String? = null,
    val image: String? = null
)

data class VegaEpisode(
    val title: String,
    val link: String,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val description: String? = null,
    val image: String? = null
)

data class VegaLinkList(
    val title: String,
    val quality: String = "Auto",
    val directLinks: List<VegaDirectLink> = emptyList(),
    val episodesLink: String? = null
)

data class VegaMetaResult(
    val title: String,
    val synopsis: String? = null,
    val image: String? = null,
    val poster: String? = null,
    val type: String = "movie", // "movie" or "series"
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val rating: String? = null,
    val tags: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val linkList: List<VegaLinkList> = emptyList(),
    val webUrl: String? = null,
    val requiresWebView: Boolean = false
)

data class VegaStreamResult(
    val server: String = "Direct",
    val url: String,
    val quality: String = "Auto",
    val format: String = "mp4",
    val headers: Map<String, String> = emptyMap(),
    val isTorrent: Boolean = false,
    val subtitleUrls: List<String> = emptyList(),
    val supportsRange: Boolean = true,
    val requiresWebView: Boolean = false
)

data class VegaDiagnosticResult(
    val providerId: String,
    val providerName: String,
    val searchStatus: String = "NOT_TESTED", // PASS, FAIL, TIMEOUT
    val metaStatus: String = "NOT_TESTED",
    val episodesStatus: String = "SKIPPED",
    val streamStatus: String = "NOT_TESTED",
    val rangeStatus: String = "NOT_TESTED",
    val overallStatus: String = "UNKNOWN", // WORKING, PARTIAL, BROKEN, WEBVIEW_REQUIRED, ANIME_ONLY
    val failureStage: String? = null,
    val errorMessage: String? = null,
    val httpCode: Int = 0,
    val durationMs: Long = 0L,
    val testedItemTitle: String? = null,
    val resolvedStreamUrl: String? = null
)


