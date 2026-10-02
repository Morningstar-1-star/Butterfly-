package com.example.extractor.nuvio

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.extractor.tmdbembed.TMDBMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NuvioProviderRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isMasterEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MASTER_ENABLED, true)
    )
    val isMasterEnabled: StateFlow<Boolean> = _isMasterEnabled.asStateFlow()

    private val _installedProviders = MutableStateFlow<List<InstalledNuvioProvider>>(emptyList())
    val installedProviders: StateFlow<List<InstalledNuvioProvider>> = _installedProviders.asStateFlow()

    private val _healthMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val healthMap: StateFlow<Map<String, String>> = _healthMap.asStateFlow()

    companion object {
        private const val TAG = "NuvioProviderRepo"
        private const val PREFS_NAME = "nuvio_provider_prefs"
        private const val KEY_MASTER_ENABLED = "nuvio_master_enabled"
        private const val KEY_INSTALLED = "nuvio_installed_providers_v2"
        private const val KEY_INITIALIZED = "nuvio_initialized_v2"
        private const val MANIFEST_URL = "https://raw.githubusercontent.com/yoruix/nuvio-providers/refs/heads/main/manifest.json"

        val ALL_OFFICIAL_PROVIDERS: List<NuvioScraperManifestItem> = listOf(
            // Top Priority Scrapers
            NuvioScraperManifestItem("uhdmovies", "UHDMovies", "UHDMovies 4K/1080p high-speed cloud streams with HubCloud & FastDL", "1.1.0", "Nuvio Team", listOf("movie", "tv"), "providers/uhdmovies.js", true, listOf("mkv", "mp4"), "https://uhdmovies.pink/favicon.ico", listOf("en", "hin"), priority = 100),
            NuvioScraperManifestItem("moviesmod", "MoviesMod", "MoviesMod direct stream links with dual audio & multi-resolution", "1.1.0", "Nuvio Team", listOf("movie", "tv"), "providers/moviesmod.js", true, listOf("mkv", "mp4"), "https://moviesmod.cc/favicon.ico", listOf("en", "hin"), priority = 98),
            NuvioScraperManifestItem("olamovies", "OlaMovies", "OlaMovies (Status: UNAVAILABLE upstream - Domain defunct / removed in official Nuvio manifest)", "0.9.0", "Legacy", listOf("movie", "tv"), "", false, listOf("mkv"), "", listOf("en", "hin"), priority = 10),
            NuvioScraperManifestItem("moviesdrive", "MoviesDrive", "MoviesDrive streaming with HubCloud, GDFlix & FastDL direct mirrors", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/moviesdrive.js", true, listOf("mkv", "mp4"), "https://www.google.com/s2/favicons?domain=moviesdrives.my&sz=128", listOf("en", "hin"), priority = 96),
            NuvioScraperManifestItem("hdhub4u", "HDHub4u", "Direct links from HDHub4u with high-speed download & direct streaming", "1.1.0", "Nuvio Team", listOf("movie", "tv"), "providers/hdhub4u.js", true, listOf("mkv", "mp4"), "https://www.google.com/s2/favicons?domain=new6.hdhub4u.fo&sz=128", listOf("en", "hin"), priority = 95),
            NuvioScraperManifestItem("4khdhub", "4KHDHub", "4KHDHub direct links and Ultra HD streams", "1.0.2", "Nuvio Team", listOf("movie", "tv"), "providers/4khdhub.js", true, listOf("mkv"), "https://www.google.com/s2/favicons?domain=4khdhub.click&sz=128", listOf("en"), priority = 94),
            NuvioScraperManifestItem("vidlink", "Vidlink Pro", "Vidlink streaming with encrypted TMDB ID support for movies and TV shows", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/vidlink.js", true, listOf("m3u8", "mp4"), "https://vidlink.pro/favicon.ico", listOf("en"), priority = 92),
            NuvioScraperManifestItem("showbox", "ShowBox / FebBox", "ShowBox streaming with multiple quality options and cloud speed", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/showbox.js", true, listOf("mp4", "mkv"), "https://raw.githubusercontent.com/tapframe/nuvio-providers/main/Assets/Logo-2.png", listOf("en"), priority = 90),
            NuvioScraperManifestItem("castle", "Castle Multi-Lang", "Multi-Language movie and TV series stream provider", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/castle.js", true, listOf("mp4", "m3u8"), "https://static.hbayy.com/simple-blog-13/prod/_nuxt/big-logo.DESZ4mBj.png", listOf("en", "hi", "ta", "te", "ml", "kn"), priority = 88),
            NuvioScraperManifestItem("vidnest", "Vidnest", "Vidnest streaming with encrypted AES-GCM sources and fast CDN", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/vidnest.js", true, listOf("mp4", "m3u8"), "https://vidnest.fun/favicon.ico", listOf("en"), priority = 86),
            NuvioScraperManifestItem("vidnest-anime", "VidnestAnime", "Vidnest anime streaming with TMDB→AniList mapping and multi-server proxy", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/vidnest-anime.js", true, listOf("m3u8"), "https://vidnest.fun/favicon.ico", listOf("en", "hi", "ja"), priority = 85),
            NuvioScraperManifestItem("dooflix", "DooFlix", "Fast streaming provider with direct TMDB integration", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/dooflix.js", true, listOf("m3u8"), "https://www.google.com/s2/favicons?domain=dooflix.org&sz=128", listOf("en", "hi"), priority = 84),
            NuvioScraperManifestItem("allmovieland", "AllMovieLand", "AllMovieLand streaming provider with multi-language support", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/allmovieland.js", true, listOf("m3u8"), "https://www.google.com/s2/favicons?domain=allmovieland.one&sz=128", listOf("en", "hi", "ta", "te"), priority = 82),
            NuvioScraperManifestItem("hianime", "HiAnime", "HiAnime multi-server anime streaming with real-time mapping and subtitles", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/hianime.js", true, listOf("m3u8"), "https://www.google.com/s2/favicons?domain=hianime.to&sz=128", listOf("en"), priority = 80),
            NuvioScraperManifestItem("kurage", "Kurage", "Kurage.live anime streaming with tRPC API and multi-server proxy", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/kurage.js", true, listOf("mp4"), "https://kurage.live/favicon.ico", listOf("en"), priority = 78),
            NuvioScraperManifestItem("anizone", "AniZone", "AniZone high-quality anime streaming with multi-audio and soft subtitles", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/anizone.js", true, listOf("m3u8"), "https://anizone.to/favicon.ico", listOf("en"), priority = 76),
            NuvioScraperManifestItem("animepahe", "AnimePahe", "AnimePahe Sub & Dub anime stream provider", "1.0.1", "Nuvio Team", listOf("movie", "tv"), "providers/animepahe.js", true, listOf("m3u8"), "https://www.google.com/s2/favicons?domain=animepahe.ru&sz=128", listOf("en"), priority = 75),
            NuvioScraperManifestItem("dahmermovies", "DahmerMovies", "Direct catalog film and TV series mirror", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/dahmermovies.js", true, listOf("mkv"), "https://dahmermovies.top/favicon.ico", listOf("en"), priority = 74),
            NuvioScraperManifestItem("netmirror", "NetMirror", "Modern cinema and TV series player mirror", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/netmirror.js", true, listOf("m3u8", "mp4"), "https://netmirror.app/favicon.ico", listOf("en"), priority = 72),
            NuvioScraperManifestItem("streamflix", "StreamFlix HD", "Clean high-definition stream embed", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/streamflix.js", true, listOf("mkv"), "https://streamflix.one/favicon.ico", listOf("en"), priority = 70),
            NuvioScraperManifestItem("moviebox", "MovieBox", "MovieBox direct multi-format streams", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/moviebox.js", true, listOf("mp4", "mpd"), "https://moviebox.ph/favicon.ico", listOf("en"), priority = 68),
            NuvioScraperManifestItem("vixsrc", "VixSrc", "VixSrc direct stream provider", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/vixsrc.js", true, listOf("m3u8", "mp4"), "https://vixsrc.to/favicon.ico", listOf("en"), priority = 66),
            NuvioScraperManifestItem("dvdplay", "DVDPlay", "DVDPlay retro & modern cinema streams", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/dvdplay.js", true, listOf("mkv"), "https://dvdplay.live/favicon.ico", listOf("en"), priority = 64),
            NuvioScraperManifestItem("yflix", "YFlix", "YFlix movies & series fast player", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/yflix.js", true, listOf("m3u8", "mp4"), "https://yflix.to/favicon.ico", listOf("en"), priority = 62),
            NuvioScraperManifestItem("videasy", "VIDEASY", "Videasy smooth adaptive streaming", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/videasy.js", true, listOf("m3u8", "mp4"), "https://player.videasy.net/favicon.ico", listOf("en"), priority = 60),
            NuvioScraperManifestItem("mallumv", "MalluMV", "South Indian cinema & Malayalam stream direct links", "1.0.0", "Nuvio Team", listOf("movie"), "providers/mallumv.js", true, listOf("mkv", "mp4"), "https://mallumv.lat/favicon.ico", listOf("ml", "ta", "hi", "en"), priority = 58),
            NuvioScraperManifestItem("cinevibe", "Cinevibe", "Cinevibe movies streaming engine", "1.0.0", "Nuvio Team", listOf("movie"), "providers/cinevibe.js", true, listOf("mp4", "m3u8"), "https://www.google.com/s2/favicons?domain=cinevibe.online&sz=128", listOf("en"), priority = 56),
            NuvioScraperManifestItem("cinemacity", "CinemaCity", "CinemaCity multi-language streaming", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/cinemacity.js", true, listOf("mp4"), "https://www.google.com/s2/favicons?domain=cinema-city.pl&sz=128", listOf("en", "hi", "ta", "te", "id", "pl", "ar"), priority = 54),
            NuvioScraperManifestItem("movieblast", "MovieBlast", "Direct links via MovieBlast API with multi-language support", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/movieblast.js", true, listOf("mp4", "mkv"), "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/movieblast.png", listOf("te", "hi", "en"), priority = 52),
            NuvioScraperManifestItem("mycima", "MyCima", "Arabic and international streaming provider", "1.0.0", "Nuvio Team", listOf("movie", "tv"), "providers/mycima.js", true, listOf("mp4", "m3u8"), "https://www.google.com/s2/favicons?domain=wecima.show&sz=128", listOf("ar"), priority = 50)
        )

        @Volatile
        private var instance: NuvioProviderRepository? = null

        fun getInstance(context: Context): NuvioProviderRepository {
            return instance ?: synchronized(this) {
                instance ?: NuvioProviderRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        loadInstalledProviders()
    }

    fun isMasterEnabled(): Boolean = _isMasterEnabled.value

    fun setMasterEnabled(enabled: Boolean) {
        _isMasterEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun getAllAvailableProviders(): List<NuvioScraperManifestItem> = ALL_OFFICIAL_PROVIDERS

    private fun loadInstalledProviders() {
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
        if (!initialized) {
            val defaultList = ALL_OFFICIAL_PROVIDERS.map {
                InstalledNuvioProvider(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    version = it.version,
                    author = it.author,
                    isEnabled = it.id != "olamovies",
                    isInstalled = true,
                    supportedTypes = it.supportedTypes,
                    formats = it.formats,
                    contentLanguage = it.contentLanguage,
                    logo = it.logo,
                    priority = it.priority,
                    status = if (it.id == "olamovies") "Unavailable" else "Active"
                )
            }
            saveProviders(defaultList)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            return
        }

        val jsonString = prefs.getString(KEY_INSTALLED, null)
        if (jsonString.isNullOrBlank()) {
            _installedProviders.value = emptyList()
            return
        }

        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<InstalledNuvioProvider>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val defItem = ALL_OFFICIAL_PROVIDERS.find { it.id == id }
                list.add(
                    InstalledNuvioProvider(
                        id = id,
                        name = obj.optString("name", defItem?.name ?: id),
                        description = obj.optString("description", defItem?.description ?: ""),
                        version = obj.optString("version", defItem?.version ?: "1.0.0"),
                        author = obj.optString("author", defItem?.author ?: "Nuvio Team"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isInstalled = obj.optBoolean("isInstalled", true),
                        supportedTypes = defItem?.supportedTypes ?: listOf("movie", "tv"),
                        formats = defItem?.formats ?: listOf("mp4", "m3u8"),
                        contentLanguage = defItem?.contentLanguage ?: listOf("en"),
                        logo = obj.optString("logo", defItem?.logo ?: ""),
                        priority = obj.optInt("priority", defItem?.priority ?: 50),
                        status = obj.optString("status", if (id == "olamovies") "Unavailable" else "Active"),
                        lastError = obj.optString("lastError", null),
                        installedAtMs = obj.optLong("installedAtMs", System.currentTimeMillis())
                    )
                )
            }
            _installedProviders.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Nuvio providers", e)
            _installedProviders.value = emptyList()
        }
    }

    private fun saveProviders(list: List<InstalledNuvioProvider>) {
        _installedProviders.value = list
        try {
            val array = JSONArray()
            for (p in list) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("description", p.description)
                    put("version", p.version)
                    put("author", p.author)
                    put("isEnabled", p.isEnabled)
                    put("isInstalled", p.isInstalled)
                    put("logo", p.logo)
                    put("priority", p.priority)
                    put("status", p.status)
                    put("lastError", p.lastError ?: "")
                    put("installedAtMs", p.installedAtMs)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_INSTALLED, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving Nuvio providers", e)
        }
    }

    fun toggleProvider(id: String, enabled: Boolean) {
        val current = _installedProviders.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(isEnabled = enabled)
            saveProviders(current)
        }
    }

    fun removeProvider(id: String) {
        val current = _installedProviders.value.toMutableList()
        current.removeAll { it.id == id }
        saveProviders(current)
    }

    fun installProvider(item: NuvioScraperManifestItem) {
        val current = _installedProviders.value.toMutableList()
        if (current.none { it.id == item.id }) {
            current.add(
                InstalledNuvioProvider(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    version = item.version,
                    author = item.author,
                    isEnabled = item.id != "olamovies",
                    isInstalled = true,
                    supportedTypes = item.supportedTypes,
                    formats = item.formats,
                    contentLanguage = item.contentLanguage,
                    logo = item.logo,
                    priority = item.priority,
                    status = if (item.id == "olamovies") "Unavailable" else "Active"
                )
            )
            saveProviders(current)
        }
    }

    fun markStatus(providerId: String, status: String, error: String? = null) {
        val health = _healthMap.value.toMutableMap()
        health[providerId] = if (error.isNullOrBlank()) status else "$status: $error"
        _healthMap.value = health

        val current = _installedProviders.value.toMutableList()
        val idx = current.indexOfFirst { it.id == providerId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(status = status, lastError = error)
            saveProviders(current)
        }
    }

    suspend fun syncManifestOnline(): Int = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
            val req = Request.Builder().url(MANIFEST_URL).header("User-Agent", "Butterfly-Nuvio").build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: return@withContext 0
                val json = JSONObject(body)
                val scrapers = json.optJSONArray("scrapers") ?: return@withContext 0
                var count = 0
                val current = _installedProviders.value.toMutableList()

                for (i in 0 until scrapers.length()) {
                    val s = scrapers.getJSONObject(i)
                    val sId = s.optString("id", "")
                    if (sId.isNotBlank()) {
                        val existingIdx = current.indexOfFirst { it.id == sId }
                        if (existingIdx >= 0) {
                            current[existingIdx] = current[existingIdx].copy(
                                version = s.optString("version", current[existingIdx].version),
                                description = s.optString("description", current[existingIdx].description)
                            )
                        } else {
                            current.add(
                                InstalledNuvioProvider(
                                    id = sId,
                                    name = s.optString("name", sId),
                                    description = s.optString("description", ""),
                                    version = s.optString("version", "1.0.0"),
                                    author = s.optString("author", "Nuvio Team"),
                                    isEnabled = true,
                                    isInstalled = true
                                )
                            )
                        }
                        count++
                    }
                }
                saveProviders(current)
                return@withContext count
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing Nuvio manifest: ${e.message}")
        }
        return@withContext 0
    }

    suspend fun testProviderLive(providerId: String): String = withContext(Dispatchers.IO) {
        if (providerId == "olamovies") {
            return@withContext "UNAVAILABLE (Upstream Domain Defunct)"
        }
        try {
            val testRequest = TMDBMediaRequest(
                title = "Inception",
                year = "2010",
                tmdbId = "27205",
                mediaType = "movie"
            )
            val streams = NuvioProviderEngine.resolveNuvioStreams(context, testRequest, providerId)
            if (streams.isNotEmpty()) {
                val s = streams.first()
                markStatus(providerId, "Online")
                return@withContext "Active • Found ${streams.size} link(s) (${s.qualityLabel})"
            } else {
                markStatus(providerId, "No Links")
                return@withContext "Reachable • No streams found for test movie"
            }
        } catch (e: Exception) {
            markStatus(providerId, "Error", e.message)
            return@withContext "Failed: ${e.message ?: "Network error"}"
        }
    }
}
