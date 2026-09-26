package com.example.vega

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class VegaProviderInfo(
    val id: String,
    val name: String,
    val version: String,
    val icon: String,
    val baseUrl: String,
    val isEnabledByDefault: Boolean = false
)

object VegaProviderRegistry {
    private const val TAG = "VegaProviderRegistry"
    private const val URLS_REMOTE_ENDPOINT =
        "https://raw.githubusercontent.com/Zenda-Cross/vega-providers/refs/heads/main/urls.json"

    private val providerMap = ConcurrentHashMap<String, VegaProviderInfo>()
    private val dynamicUrls = ConcurrentHashMap<String, String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Canonical hardcoded mirrors in case of asset read issues or network delay
    private val DEFAULT_MIRRORS = mapOf(
        "vega" to "https://vegamovies.gallery",
        "hdhub4u" to "https://new6.hdhub4u.cl",
        "4khdhub" to "https://4khdhub.one",
        "drive" to "https://new4.moviesdrive.christmas",
        "world4u" to "https://world4ufree.beer",
        "mod" to "https://moviesmod.ai.in",
        "topmovies" to "https://moviesleech.club",
        "uhd" to "https://uhdmovies.my",
        "katmovies" to "https://new.katmoviehd.top",
        "katmoviefix" to "https://katmoviefix.study",
        "zeefliz" to "https://zeefliz.beer",
        "movies4u" to "https://movies4u.kg",
        "1cinevood" to "https://new1.cinevood.cv",
        "eonmovies" to "https://new4.eonmovies.click",
        "cinemaluxe" to "https://cinemalux.cyou",
        "cinefreak" to "https://cinefreak.net",
        "filmyfly" to "https://filmyfiy.co.in",
        "kmmovies" to "https://kmmovies.online",
        "multi" to "https://multimovies.fyi",
        "movieboxweb" to "https://officialmoviebox.com",
        "gokuhd" to "https://gokuhd.com",
        "mkvdrama" to "https://mkvdrama.net",
        "showbox" to "https://www.showbox.media",
        "ridomovies" to "https://ridomovies.tv",
        "primewire" to "https://primewire.pw",
        "kisskh" to "https://kisskh.is",
        "luxmovies" to "https://new2.rogmovies.click",
        "joya9tv" to "https://joya9tv1.com",
        "skymoviehd" to "https://skymovieshd.lgbt",
        "anikoto" to "https://anikototv.to",
        "kickassanime" to "https://kaa.lt",
        "protonmovies" to "https://m.protonmovies.space",
        "moviezwap" to "https://www.moviezwap.codes",
        "moviesapi" to "https://vidspark.to",
        "netflixmirror" to "https://net77.cc",
        "tokyoinsider" to "https://www.tokyoinsider.com",
        "hianime" to "https://hianime.to",
        "flixhq" to "https://flixhq.to"
    )

    private val ALL_52_PROVIDERS = listOf(
        VegaProviderInfo("vega", "VegaMovies", "2.45", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/vega.png", "https://vegamovies.gallery"),
        VegaProviderInfo("autoEmbed", "MultiStream", "3.1", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/autoEmbed.png", ""),
        VegaProviderInfo("drive", "MoviesDrive", "2.29", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/drive.png", "https://new4.moviesdrive.christmas"),
        VegaProviderInfo("multi", "MultiMovies", "1.4", "", "https://multimovies.fyi"),
        VegaProviderInfo("4khdhub", "4K HDHub", "2.20", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/4khdhub.png", "https://4khdhub.one"),
        VegaProviderInfo("1cinevood", "Cinewood", "1.38", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/1cinevood.png", "https://new1.cinevood.cv"),
        VegaProviderInfo("world4u", "World4UFree", "1.8", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/world4u.png", "https://world4ufree.beer"),
        VegaProviderInfo("katmovies", "KatMoviesHD", "1.36", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/katmovies.png", "https://new.katmoviehd.top"),
        VegaProviderInfo("mod", "MoviesMod", "1.7", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/mod.png", "https://moviesmod.ai.in"),
        VegaProviderInfo("uhd", "UHDMovies", "1.8", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/uhd.webp", "https://uhdmovies.my"),
        VegaProviderInfo("protonMovies", "ProtonMovies", "1.6", "", "https://m.protonmovies.space"),
        VegaProviderInfo("cinemaLuxe", "CinemaLuxe", "1.20", "", "https://cinemalux.cyou"),
        VegaProviderInfo("filmyfly", "FilmyFly", "1.9", "", "https://filmyfiy.co.in"),
        VegaProviderInfo("movieBox", "MovieBox App", "2.5", "", ""),
        VegaProviderInfo("movieBoxWeb", "MovieBox Web", "1.4", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/movieBoxWeb.png", "https://officialmoviebox.com"),
        VegaProviderInfo("gokuHD", "GokuHD", "1.7", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/gokuHD.png", "https://gokuhd.com"),
        VegaProviderInfo("mkvDrama", "MKVDrama", "1.74", "", "https://mkvdrama.net"),
        VegaProviderInfo("eonMovies", "EonMovies", "1.15", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/eonMovies.png", "https://new4.eonmovies.click"),
        VegaProviderInfo("movies4u", "Movies4U", "1.30", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/movies4u.png", "https://movies4u.kg"),
        VegaProviderInfo("kmMovies", "KmMovies", "2.34", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/kmMovies.png", "https://kmmovies.online"),
        VegaProviderInfo("zeefliz", "ZeeFliz", "1.33", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/zeefliz.jpg", "https://zeefliz.beer"),
        VegaProviderInfo("katMovieFix", "KatMovieFix", "1.18", "", "https://katmoviefix.study"),
        VegaProviderInfo("ringz", "Ringz", "1.2", "", ""),
        VegaProviderInfo("netflixMirror", "NetflixMirror", "2.9", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/netflixMirror.webp", "https://net77.cc"),
        VegaProviderInfo("primeMirror", "PrimeMirror", "2.8", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/primeMirror.webp", ""),
        VegaProviderInfo("disneyMirror", "DisneyMirror", "2.0", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/disneyMirror.webp", ""),
        VegaProviderInfo("hdhub4u", "HDHub4U", "2.27", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/hdhub4u.png", "https://new6.hdhub4u.cl"),
        VegaProviderInfo("ogomovies", "OgoMovies", "1.1", "", ""),
        VegaProviderInfo("a111477", "A111477", "1.7", "", ""),
        VegaProviderInfo("vadapav", "VadaPav", "1.1", "", ""),
        VegaProviderInfo("moviesApi", "MoviesApi", "1.1", "", "https://vidspark.to"),
        VegaProviderInfo("moviezwap", "MoviezWap", "1.3", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/moviezwap.png", "https://www.moviezwap.codes"),
        VegaProviderInfo("showbox", "ShowBox", "1.8", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/showbox.png", "https://www.showbox.media"),
        VegaProviderInfo("ridoMovies", "RidoMovies", "1.4", "", "https://ridomovies.tv"),
        VegaProviderInfo("flixhq", "FlixHQ", "1.1", "", "https://flixhq.to"),
        VegaProviderInfo("primewire", "PrimeWire", "1.4", "", "https://primewire.pw"),
        VegaProviderInfo("hiAnime", "HiAnime", "1.2", "", "https://hianime.to"),
        VegaProviderInfo("animetsu", "Animetsu", "1.10", "", ""),
        VegaProviderInfo("tokyoInsider", "TokyoInsider", "1.1", "", "https://www.tokyoinsider.com"),
        VegaProviderInfo("kissKh", "KissKH", "1.5", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/kissKh.png", "https://kisskh.is"),
        VegaProviderInfo("dooflix", "DooFlix", "1.5", "", ""),
        VegaProviderInfo("luxMovies", "RogMovies", "2.36", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/luxMovies.png", "https://new2.rogmovies.click"),
        VegaProviderInfo("topmovies", "TopMovies", "1.16", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/topmovies.png", "https://moviesleech.club"),
        VegaProviderInfo("guardahd", "GuardaHD", "2.0", "", ""),
        VegaProviderInfo("skyMovieHD", "SkyMoviesHD", "1.18", "", "https://skymovieshd.lgbt"),
        VegaProviderInfo("Joya9tv", "Joya9tv", "1.20", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/Joya9tv.png", "https://joya9tv1.com"),
        VegaProviderInfo("uniquestream", "AnimeStream", "1.2", "", ""),
        VegaProviderInfo("torrentio", "Torrentio", "1.20", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/torrentio.png", ""),
        VegaProviderInfo("cinefreak", "CineFreak", "1.99", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/cinefreak.png", "https://cinefreak.net"),
        VegaProviderInfo("anikoto", "AniKoto", "2.0", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/anikoto.png", "https://anikototv.to"),
        VegaProviderInfo("kickAssAnime", "KickAssAnime", "1.4", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/kickAssAnime.png", "https://kaa.lt"),
        VegaProviderInfo("everything", "Everything", "1.4", "https://cdn.jsdelivr.net/gh/Zenda-Cross/vega-providers@main/assets/everything.png", "")
    )

    init {
        // Register default providers
        ALL_52_PROVIDERS.forEach { info ->
            providerMap[info.id.lowercase()] = info
        }
        DEFAULT_MIRRORS.forEach { (k, v) ->
            dynamicUrls[k.lowercase()] = v
        }
    }

    private val PROVIDER_ALIASES = mapOf(
        "world4u" to listOf("w4u", "world4ufree"),
        "katmovies" to listOf("kat", "katmovieshd"),
        "mod" to listOf("moviesmod"),
        "uhd" to listOf("uhdmovies"),
        "hdhub4u" to listOf("hdhub"),
        "luxmovies" to listOf("lux", "rogmovies"),
        "skymoviehd" to listOf("skymovieshd"),
        "netflixmirror" to listOf("nfmirror"),
        "primemirror" to listOf("nfmirror"),
        "disneymirror" to listOf("nfmirror"),
        "1cinevood" to listOf("cinewood", "cinevood"),
        "kmmovies" to listOf("cinevood"),
        "filmyfly" to listOf("flimyfly"),
        "drive" to listOf("moviesdrive"),
        "vega" to listOf("vegamovies")
    )

    fun initFromAssets(context: Context) {
        try {
            // Read urls.json FIRST so manifest entries have resolved URLs
            val urlsJsonStr = context.assets.open("vega/urls.json").bufferedReader().use { it.readText() }
            val json = JSONObject(urlsJsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val entry = json.optJSONObject(k)
                val url = entry?.optString("url")?.trimEnd('/') ?: ""
                if (url.isNotBlank()) {
                    val kLower = k.lowercase()
                    dynamicUrls[kLower] = url
                    entry?.optString("name")?.let { nameVal ->
                        val nameLower = nameVal.lowercase()
                        dynamicUrls[nameLower] = url
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading urls.json from assets: ${e.message}")
        }

        try {
            // Read manifest.json SECOND
            val manifestJsonStr = context.assets.open("vega/manifest.json").bufferedReader().use { it.readText() }
            val array = JSONArray(manifestJsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("value").trim()
                if (id.isBlank()) continue
                val name = obj.optString("display_name").ifBlank { id }
                val ver = obj.optString("version").ifBlank { "1.0" }
                val icon = obj.optString("icon")
                val key = id.lowercase()
                val currentBase = getBaseUrl(key)
                providerMap[key] = VegaProviderInfo(
                    id = id,
                    name = name,
                    version = ver,
                    icon = icon,
                    baseUrl = currentBase
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading manifest from assets: ${e.message}")
        }
    }

    suspend fun refreshUrlsFromNetwork() = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(URLS_REMOTE_ENDPOINT)
                .header("User-Agent", "Mozilla/5.0 Butterfly/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val entry = json.optJSONObject(k)
                        val url = entry?.optString("url")?.trimEnd('/') ?: ""
                        if (url.isNotBlank()) {
                            dynamicUrls[k.lowercase()] = url
                            entry?.optString("name")?.let { nameVal ->
                                dynamicUrls[nameVal.lowercase()] = url
                            }
                        }
                    }
                    Log.i(TAG, "Successfully refreshed Vega provider mirror URLs from GitHub")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Skipped live url refresh: ${e.message}")
        }
    }

    fun getAllProviderIds(): List<String> {
        return ALL_52_PROVIDERS.map { it.id }
    }

    fun getAllProviders(): List<VegaProviderInfo> {
        return ALL_52_PROVIDERS.map { defaultInfo ->
            val key = defaultInfo.id.lowercase()
            val dynamicUrl = dynamicUrls[key] ?: defaultInfo.baseUrl
            val cached = providerMap[key]
            if (cached != null) {
                cached.copy(baseUrl = dynamicUrl)
            } else {
                defaultInfo.copy(baseUrl = dynamicUrl)
            }
        }
    }

    fun getBaseUrl(providerId: String): String {
        val clean = providerId.trim().lowercase()
        // 1. Direct match
        dynamicUrls[clean]?.let { if (it.isNotBlank()) return it }
        DEFAULT_MIRRORS[clean]?.let { if (it.isNotBlank()) return it }

        // 2. Alias resolution
        val aliases = PROVIDER_ALIASES[clean] ?: emptyList()
        for (alias in aliases) {
            val aClean = alias.lowercase()
            dynamicUrls[aClean]?.let { if (it.isNotBlank()) return it }
            DEFAULT_MIRRORS[aClean]?.let { if (it.isNotBlank()) return it }
        }

        return providerMap[clean]?.baseUrl ?: ""
    }

    fun getProviderInfo(providerId: String): VegaProviderInfo? {
        val clean = providerId.trim().lowercase()
        return providerMap[clean] ?: ALL_52_PROVIDERS.firstOrNull { it.id.equals(clean, ignoreCase = true) }
    }
}
