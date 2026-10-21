package com.example.vega

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class VegaRegisteredProvider(
    val id: String,
    val displayName: String,
    val version: String = "1.0",
    val category: String = "Movies/TV",
    val isDisabled: Boolean = false
) {
    val name: String get() = displayName
}

/**
 * Registry of Butterfly-MediaServer providers.
 * Synchronizes dynamically with Butterfly-MediaServer's GET /providers endpoint,
 * with a lightweight built-in default catalog matching Butterfly-MediaServer's providers directory.
 */
object VegaProviderRegistry {

    private val providerMap = ConcurrentHashMap<String, VegaRegisteredProvider>()

    private val DEFAULT_SERVER_PROVIDERS = listOf(
        VegaRegisteredProvider("autoEmbed", "AutoEmbed", "1.0", "Multi-Server"),
        VegaRegisteredProvider("vega", "Vega Movies", "1.0", "Movies/TV"),
        VegaRegisteredProvider("hdhub4u", "HDHub4u", "1.0", "Movies/TV"),
        VegaRegisteredProvider("4khdhub", "4KHDHub", "1.0", "4K UHD"),
        VegaRegisteredProvider("multi", "MultiStream", "1.0", "Movies/TV"),
        VegaRegisteredProvider("1cinevood", "Cinevood", "1.0", "Movies/TV"),
        VegaRegisteredProvider("cinemaLuxe", "CinemaLuxe", "1.0", "Movies/TV"),
        VegaRegisteredProvider("drive", "MoviesDrive", "1.0", "Movies/TV"),
        VegaRegisteredProvider("mod", "MoviesMod", "1.0", "Movies/TV"),
        VegaRegisteredProvider("uhd", "UHDMovies", "1.0", "4K UHD"),
        VegaRegisteredProvider("topmovies", "TopMovies", "1.0", "Movies/TV"),
        VegaRegisteredProvider("katmovies", "KatMovieHD", "1.0", "Movies/TV"),
        VegaRegisteredProvider("katMovieFix", "KatMovieFix", "1.0", "Movies/TV"),
        VegaRegisteredProvider("filmyfly", "FilmyFly", "1.0", "Movies/TV"),
        VegaRegisteredProvider("movies4u", "Movies4u", "1.0", "Movies/TV"),
        VegaRegisteredProvider("kmMovies", "KMMovies", "1.0", "Movies/TV"),
        VegaRegisteredProvider("zeefliz", "Zeefliz", "1.0", "Movies/TV"),
        VegaRegisteredProvider("luxMovies", "LuxMovies", "1.0", "Movies/TV"),
        VegaRegisteredProvider("guardahd", "GuardaHD", "1.0", "Movies/TV"),
        VegaRegisteredProvider("showbox", "ShowBox", "1.0", "Movies/TV"),
        VegaRegisteredProvider("ridoMovies", "RidoMovies", "1.0", "Movies/TV"),
        VegaRegisteredProvider("flixhq", "FlixHQ", "1.0", "Movies/TV"),
        VegaRegisteredProvider("primewire", "PrimeWire", "1.0", "Movies/TV"),
        VegaRegisteredProvider("kisskh", "KissKH", "1.0", "Asian Drama"),
        VegaRegisteredProvider("hiAnime", "HiAnime", "1.0", "Anime"),
        VegaRegisteredProvider("animetsu", "Animetsu", "1.0", "Anime"),
        VegaRegisteredProvider("anikoto", "Anikoto", "1.0", "Anime"),
        VegaRegisteredProvider("tokyoInsider", "TokyoInsider", "1.0", "Anime"),
        VegaRegisteredProvider("toonstream", "ToonStream", "1.0", "Anime"),
        VegaRegisteredProvider("w4u", "World4uFree", "1.0", "Movies/TV"),
        VegaRegisteredProvider("skyMovieHD", "SkyMovieHD", "1.0", "Movies/TV"),
        VegaRegisteredProvider("moviesApi", "MoviesAPI", "1.0", "Movies/TV")
    )

    init {
        DEFAULT_SERVER_PROVIDERS.forEach { providerMap[it.id] = it }
    }

    @Suppress("UNUSED_PARAMETER")
    fun initFromAssets(context: Context? = null) {
        if (providerMap.isEmpty()) {
            DEFAULT_SERVER_PROVIDERS.forEach { providerMap[it.id] = it }
        }
    }

    fun updateProvidersFromServer(providersArray: JSONArray) {
        for (i in 0 until providersArray.length()) {
            val item = providersArray.opt(i)
            when (item) {
                is JSONObject -> {
                    val id = item.optString("id").trim()
                    if (id.isNotBlank()) {
                        val existing = providerMap[id]
                        val displayName = existing?.displayName ?: id.replaceFirstChar { it.uppercase() }
                        providerMap[id] = VegaRegisteredProvider(
                            id = id,
                            displayName = displayName
                        )
                    }
                }
                is String -> {
                    val id = item.trim()
                    if (id.isNotBlank()) {
                        val existing = providerMap[id]
                        val displayName = existing?.displayName ?: id.replaceFirstChar { it.uppercase() }
                        providerMap[id] = VegaRegisteredProvider(
                            id = id,
                            displayName = displayName
                        )
                    }
                }
            }
        }
    }

    suspend fun refreshUrlsFromNetwork(): Boolean {
        return try {
            val ids = VegaProviderClient.getAvailableProviders()
            ids.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun getAllProviders(): List<VegaRegisteredProvider> {
        if (providerMap.isEmpty()) {
            DEFAULT_SERVER_PROVIDERS.forEach { providerMap[it.id] = it }
        }
        return providerMap.values.filter { !it.isDisabled }.sortedBy { it.displayName }
    }

    fun getAllProviderIds(): List<String> {
        return getAllProviders().map { it.id }
    }

    fun getProviderInfo(id: String): VegaRegisteredProvider? {
        return providerMap[id] ?: providerMap.values.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun getBaseUrl(id: String): String {
        return "${VegaProviderClient.getDefaultServerUrl()}/search/$id?q=2024"
    }
}
