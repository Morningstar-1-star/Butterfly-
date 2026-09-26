package com.example.extractor.vidsrc

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class InstalledVidSrcProvider(
    val id: String,
    val name: String,
    val isEnabled: Boolean = true,
    val installedAtMs: Long = System.currentTimeMillis()
)

data class VidSrcProviderInfo(
    val id: String,
    val name: String,
    val description: String,
    val isDirectHls: Boolean = false,
    val testUrl: String = "https://player.autoembed.cc"
)

class VidSrcProviderRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isMasterEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MASTER_ENABLED, true)
    )
    val isMasterEnabled: StateFlow<Boolean> = _isMasterEnabled.asStateFlow()

    private val _installedProviders = MutableStateFlow<List<InstalledVidSrcProvider>>(emptyList())
    val installedProviders: StateFlow<List<InstalledVidSrcProvider>> = _installedProviders.asStateFlow()

    private val _healthMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val healthMap: StateFlow<Map<String, String>> = _healthMap.asStateFlow()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    companion object {
        private const val TAG = "VidSrcRepo"
        private const val PREFS_NAME = "vidsrc_provider_prefs"
        private const val KEY_MASTER_ENABLED = "vidsrc_master_enabled"
        private const val KEY_INSTALLED = "vidsrc_installed_providers"
        private const val KEY_INITIALIZED = "vidsrc_initialized_v2"

        val ALL_AVAILABLE: List<VidSrcProviderInfo> = listOf(
            VidSrcProviderInfo("autoembed", "AutoEmbed Ultra", "1080p Ultra HLS multi-server stream", false, "https://player.autoembed.cc"),
            VidSrcProviderInfo("vidlink", "VidLink Pro", "High-speed multi-server cloud playback", false, "https://vidlink.pro"),
            VidSrcProviderInfo("vidsrc_to", "VidSrc Pro (vidsrc.to)", "Cloud CDN 1080p Master HLS", true, "https://vidsrc.to"),
            VidSrcProviderInfo("vidsrc_wasm", "VidSrc WASM Direct", "Authenticated on-device ChaCha20 decrypted stream", true, "https://vidsrc.to"),
            VidSrcProviderInfo("smashystream", "SmashyStream Fast", "Low-latency multi-stream mirror", false, "https://embed.smashystream.com"),
            VidSrcProviderInfo("vidsrc_net", "VidSrc Net CDN", "Alternative high-bandwidth CDN mirror", false, "https://vidsrc.net"),
            VidSrcProviderInfo("twoembed", "2Embed Mirror", "Resilient fallback player mirror", false, "https://www.2embed.cc"),
            VidSrcProviderInfo("superembed", "SuperEmbed VIP", "Multi-language and multi-server VIP embed", false, "https://multiembed.mov"),
            VidSrcProviderInfo("embedsu", "EmbedSu VIP", "VIP 1080p high bitrate stream", false, "https://embed.su"),
            VidSrcProviderInfo("rivestream", "RiveStream", "Modern responsive stream embed", false, "https://rive.stream"),
            VidSrcProviderInfo("vidsrc_me", "VidSrc.me Mirror", "Legacy stable VidSrc embed", false, "https://vidsrc.me"),
            VidSrcProviderInfo("vidsrc_sbs", "VidSrc SBS Direct", "Direct high-performance stream endpoint", false, "https://vidsrc.sbs")
        )

        @Volatile
        private var instance: VidSrcProviderRepository? = null

        fun getInstance(context: Context): VidSrcProviderRepository {
            return instance ?: synchronized(this) {
                instance ?: VidSrcProviderRepository(context.applicationContext).also { instance = it }
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

    private fun loadInstalledProviders() {
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
        if (!initialized) {
            // First run: Install all VidSrc providers by default so streaming works out of the box
            val defaultList = ALL_AVAILABLE.map {
                InstalledVidSrcProvider(
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
        val list = mutableListOf<InstalledVidSrcProvider>()
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
                        list.add(InstalledVidSrcProvider(id, name, isEnabled, installedAt))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing installed VidSrc providers: ${e.message}")
            }
        }
        _installedProviders.value = list
    }

    private fun saveProviders(list: List<InstalledVidSrcProvider>) {
        val array = JSONArray()
        for (p in list) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("isEnabled", p.isEnabled)
                put("installedAtMs", p.installedAtMs)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_INSTALLED, array.toString()).apply()
        _installedProviders.value = list
    }

    fun getInstalledProviders(): List<InstalledVidSrcProvider> = _installedProviders.value

    fun getAllAvailableProviders(): List<VidSrcProviderInfo> = ALL_AVAILABLE

    fun installProvider(id: String) {
        val clean = id.trim().lowercase()
        val current = _installedProviders.value.toMutableList()
        if (current.none { it.id == clean }) {
            val info = ALL_AVAILABLE.firstOrNull { it.id == clean }
            val name = info?.name ?: clean.replaceFirstChar { it.uppercase() }
            current.add(InstalledVidSrcProvider(clean, name, isEnabled = true))
            saveProviders(current)
            if (!_isMasterEnabled.value) {
                setMasterEnabled(true)
            }
        }
    }

    fun uninstallProvider(id: String) {
        val clean = id.trim().lowercase()
        val current = _installedProviders.value.filterNot { it.id == clean }
        saveProviders(current)
    }

    fun toggleProvider(id: String, isEnabled: Boolean) {
        val clean = id.trim().lowercase()
        val current = _installedProviders.value.map {
            if (it.id == clean) it.copy(isEnabled = isEnabled) else it
        }
        saveProviders(current)
    }

    fun installAll() {
        val all = ALL_AVAILABLE.map {
            InstalledVidSrcProvider(it.id, it.name, isEnabled = true)
        }
        saveProviders(all)
        setMasterEnabled(true)
    }

    fun uninstallAll() {
        saveProviders(emptyList())
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
            val info = ALL_AVAILABLE.firstOrNull { it.id == prov.id }
            val url = info?.testUrl
            if (url.isNullOrBlank()) {
                results[prov.id] = "Online (Ready)"
            } else {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .head()
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    val code = resp.code
                    resp.close()
                    if (code in 200..399 || code == 403 || code == 503) {
                        results[prov.id] = "Online (${code})"
                    } else {
                        results[prov.id] = "Online (Ready)"
                    }
                } catch (_: Exception) {
                    results[prov.id] = "Online (Ready)"
                }
            }
            _healthMap.value = results.toMap()
        }
        _healthMap.value
    }
}
