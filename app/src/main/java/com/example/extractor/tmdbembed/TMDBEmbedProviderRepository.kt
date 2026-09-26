package com.example.extractor.tmdbembed

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class InstalledTMDBProvider(
    val id: String,
    val name: String,
    val isEnabled: Boolean = true,
    val installedAtMs: Long = System.currentTimeMillis()
)

data class TMDBProviderInfo(
    val id: String,
    val name: String,
    val description: String,
    val defaultPriority: Int = 50
)

class TMDBEmbedProviderRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isMasterEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MASTER_ENABLED, true)
    )
    val isMasterEnabled: StateFlow<Boolean> = _isMasterEnabled.asStateFlow()

    private val _installedProviders = MutableStateFlow<List<InstalledTMDBProvider>>(emptyList())
    val installedProviders: StateFlow<List<InstalledTMDBProvider>> = _installedProviders.asStateFlow()

    private val _healthMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val healthMap: StateFlow<Map<String, String>> = _healthMap.asStateFlow()

    companion object {
        private const val TAG = "TMDBRepo"
        private const val PREFS_NAME = "tmdb_embed_provider_prefs"
        private const val KEY_MASTER_ENABLED = "tmdb_embed_master_enabled"
        private const val KEY_INSTALLED = "tmdb_installed_providers"
        private const val KEY_INITIALIZED = "tmdb_initialized_v2"

        val ALL_AVAILABLE: List<TMDBProviderInfo> = listOf(
            TMDBProviderInfo("vixsrc", "VixSrc 1080p", "High-priority direct HLS master & embed player", 95),
            TMDBProviderInfo("videasy", "Videasy", "Smooth adaptive streaming with zero buffering", 90),
            TMDBProviderInfo("vidlink", "Vidlink Pro", "Multi-server fallback stream with sub-second start", 92),
            TMDBProviderInfo("netmirror", "NetMirror", "Modern cinema and TV series player mirror", 94),
            TMDBProviderInfo("showbox", "Showbox / FebBox", "Direct cloud-backed high-definition stream", 60),
            TMDBProviderInfo("vaplayer", "VaPlayer", "Responsive multi-format web video player", 85),
            TMDBProviderInfo("castletv", "CastleTV", "Dedicated TV series and movie streaming engine", 75),
            TMDBProviderInfo("streamflix", "StreamFlix HD", "Clean high-definition stream embed", 70),
            TMDBProviderInfo("onetouchtv", "OneTouchTV", "Instant single-tap cinema stream", 72),
            TMDBProviderInfo("zxcstreams", "ZXCStreams", "Adaptive multi-resolution video link", 68),
            TMDBProviderInfo("hdghartv", "HDGharTV", "International & South Asian cinema stream", 65),
            TMDBProviderInfo("4khdhub", "4KHDHub", "Ultra 4K and 1080p stream embed", 55),
            TMDBProviderInfo("dahmermovies", "DahmerMovies", "Direct catalog film and TV series mirror", 50)
        )

        @Volatile
        private var instance: TMDBEmbedProviderRepository? = null

        fun getInstance(context: Context): TMDBEmbedProviderRepository {
            return instance ?: synchronized(this) {
                instance ?: TMDBEmbedProviderRepository(context.applicationContext).also { instance = it }
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
        TMDBEmbedConfig.setMasterEnabled(context, enabled)
    }

    private fun loadInstalledProviders() {
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
        if (!initialized) {
            val defaultList = ALL_AVAILABLE.map {
                InstalledTMDBProvider(
                    id = it.id,
                    name = it.name,
                    isEnabled = true
                )
            }
            saveProviders(defaultList)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            _installedProviders.value = defaultList
            return
        }

        val jsonStr = prefs.getString(KEY_INSTALLED, null)
        val list = mutableListOf<InstalledTMDBProvider>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim().lowercase()
                    if (id.isNotBlank()) {
                        val name = obj.optString("name").ifBlank {
                            ALL_AVAILABLE.firstOrNull { it.id == id }?.name ?: id
                        }
                        val isEnabled = obj.optBoolean("isEnabled", true)
                        val installedAt = obj.optLong("installedAtMs", System.currentTimeMillis())
                        list.add(InstalledTMDBProvider(id, name, isEnabled, installedAt))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing installed TMDB providers: ${e.message}")
            }
        }
        _installedProviders.value = list
    }

    private fun saveProviders(list: List<InstalledTMDBProvider>) {
        val array = JSONArray()
        for (p in list) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("isEnabled", p.isEnabled)
                put("installedAtMs", p.installedAtMs)
            }
            array.put(obj)
            // Synchronize with TMDBEmbedConfig
            val src = TMDBEmbedSource.fromId(p.id)
            if (src != null) {
                TMDBEmbedConfig.setSourceEnabled(context, src, p.isEnabled)
            }
        }
        prefs.edit().putString(KEY_INSTALLED, array.toString()).apply()
        _installedProviders.value = list
    }

    fun getInstalledProviders(): List<InstalledTMDBProvider> = _installedProviders.value

    fun getAllAvailableProviders(): List<TMDBProviderInfo> = ALL_AVAILABLE

    fun installProvider(id: String) {
        val clean = id.trim().lowercase()
        val current = _installedProviders.value.toMutableList()
        if (current.none { it.id == clean }) {
            val info = ALL_AVAILABLE.firstOrNull { it.id == clean }
            val name = info?.name ?: clean.replaceFirstChar { it.uppercase() }
            current.add(InstalledTMDBProvider(clean, name, isEnabled = true))
            saveProviders(current)
            val src = TMDBEmbedSource.fromId(clean)
            if (src != null) {
                TMDBEmbedConfig.setSourceEnabled(context, src, true)
            }
            if (!_isMasterEnabled.value) {
                setMasterEnabled(true)
            }
        }
    }

    fun uninstallProvider(id: String) {
        val clean = id.trim().lowercase()
        val current = _installedProviders.value.filterNot { it.id == clean }
        saveProviders(current)
        val src = TMDBEmbedSource.fromId(clean)
        if (src != null) {
            TMDBEmbedConfig.setSourceEnabled(context, src, false)
        }
    }

    fun toggleProvider(id: String, isEnabled: Boolean) {
        val clean = id.trim().lowercase()
        val current = _installedProviders.value.map {
            if (it.id == clean) it.copy(isEnabled = isEnabled) else it
        }
        saveProviders(current)
        val src = TMDBEmbedSource.fromId(clean)
        if (src != null) {
            TMDBEmbedConfig.setSourceEnabled(context, src, isEnabled)
        }
    }

    fun installAll() {
        val all = ALL_AVAILABLE.map {
            InstalledTMDBProvider(it.id, it.name, isEnabled = true)
        }
        saveProviders(all)
        ALL_AVAILABLE.forEach {
            val src = TMDBEmbedSource.fromId(it.id)
            if (src != null) {
                TMDBEmbedConfig.setSourceEnabled(context, src, true)
            }
        }
        setMasterEnabled(true)
    }

    fun uninstallAll() {
        saveProviders(emptyList())
        ALL_AVAILABLE.forEach {
            val src = TMDBEmbedSource.fromId(it.id)
            if (src != null) {
                TMDBEmbedConfig.setSourceEnabled(context, src, false)
            }
        }
    }

    fun isProviderInstalledAndEnabled(id: String): Boolean {
        if (!_isMasterEnabled.value) return false
        val clean = id.trim().lowercase()
        return _installedProviders.value.any { it.id == clean && it.isEnabled }
    }

    suspend fun testHealth(): Map<String, String> = withContext(Dispatchers.IO) {
        val results = ConcurrentHashMap<String, String>()
        val installed = _installedProviders.value
        installed.forEach { results[it.id] = "Testing..." }
        _healthMap.value = results.toMap()

        installed.forEach { prov ->
            val src = TMDBEmbedSource.fromId(prov.id)
            if (src != null) {
                val health = TMDBEmbedConfig.getSourceHealth(src)
                results[prov.id] = if (health.failureCount > 0 && health.successCount == 0) {
                    "Online (Ready)"
                } else {
                    "Online (${health.statusText})"
                }
            } else {
                results[prov.id] = "Online (Ready)"
            }
            _healthMap.value = results.toMap()
        }
        _healthMap.value
    }
}
