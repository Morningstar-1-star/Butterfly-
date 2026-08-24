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

data class VegaStreamResult(
    val url: String,
    val quality: String = "Auto",
    val format: String = "mp4",
    val headers: Map<String, String> = emptyMap(),
    val isTorrent: Boolean = false,
    val subtitleUrls: List<String> = emptyList()
)
