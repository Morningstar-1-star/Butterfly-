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

data class VegaLinkList(
    val title: String,
    val quality: String = "Auto",
    val directLinks: List<VegaDirectLink> = emptyList()
)

data class VegaMetaResult(
    val title: String,
    val synopsis: String? = null,
    val image: String? = null,
    val poster: String? = null,
    val type: String = "movie",
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val rating: String? = null,
    val tags: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val linkList: List<VegaLinkList> = emptyList(),
    val webUrl: String? = null
)

data class VegaStreamResult(
    val server: String = "Direct",
    val url: String,
    val quality: String = "Auto",
    val format: String = "mp4",
    val headers: Map<String, String> = emptyMap(),
    val isTorrent: Boolean = false,
    val subtitleUrls: List<String> = emptyList()
)

