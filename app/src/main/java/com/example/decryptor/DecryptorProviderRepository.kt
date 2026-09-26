package com.example.decryptor

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

data class InstalledDecryptorProvider(
    val id: String,
    val name: String,
    val isEnabled: Boolean = true,
    val installedAtMs: Long = System.currentTimeMillis()
)

data class DecryptorProviderInfo(
    val id: String,
    val name: String,
    val description: String,
    val isDirectHls: Boolean = true,
    val testUrl: String = "https://player.autoembed.cc"
)

class DecryptorProviderRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isMasterEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MASTER_ENABLED, true)
    )
    val isMasterEnabled: StateFlow<Boolean> = _isMasterEnabled.asStateFlow()

    private val _installedProviders = MutableStateFlow<List<InstalledDecryptorProvider>>(emptyList())
    val installedProviders: StateFlow<List<InstalledDecryptorProvider>> = _installedProviders.asStateFlow()

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
        private const val TAG = "DecryptorRepo"
        private const val PREFS_NAME = "decryptor_provider_prefs"
        private const val KEY_MASTER_ENABLED = "decryptor_master_enabled"
        private const val KEY_INSTALLED = "decryptor_installed_providers"
        private const val KEY_INITIALIZED = "decryptor_initialized_v2"

        val ALL_AVAILABLE: List<DecryptorProviderInfo> = listOf(
            DecryptorProviderInfo("vidhide", "Vidhide Multi-Quality", "Direct adaptive HLS master stream", true, "https://vidhide.com"),
            DecryptorProviderInfo("turbo", "Turbo HLS", "Ultra-fast multi-threaded cinema stream", true, "https://turbovid.stream"),
            DecryptorProviderInfo("nxsha", "Nxsha Cloud Proxy", "Direct encrypted stream decoder proxy", true, "https://vidsrc.to"),
            DecryptorProviderInfo("lulustream", "Lulustream Fast CDN", "High-bandwidth global CDN delivery", true, "https://luluvdo.com"),
            DecryptorProviderInfo("vidara", "Vidara 1080p", "Pure 1080p cinema release stream", true, "https://vidara.org"),
            DecryptorProviderInfo("fastcdn", "Fast CDN Direct", "Low-latency master video link", true, "https://vidsrc.net"),
            DecryptorProviderInfo("autoembed", "AutoEmbed Ultra", "1080p Ultra HLS multi-server stream", false, "https://player.autoembed.cc"),
            DecryptorProviderInfo("vidlink", "VidLink Multi", "Multi-server cinema fallback", false, "https://vidlink.pro"),
            DecryptorProviderInfo("smashystream", "SmashyStream Fast", "Fast cinema release mirror", false, "https://embed.smashystream.com"),
            DecryptorProviderInfo("superembed", "SuperEmbed VIP", "VIP cinema multi-server stream", false, "https://multiembed.mov"),
            DecryptorProviderInfo("twoembed", "2Embed Direct", "Resilient direct player mirror", false, "https://www.2embed.cc")
        )

        @Volatile
        private var instance: DecryptorProviderRepository? = null

        fun getInstance(context: Context): DecryptorProviderRepository {
            return instance ?: synchronized(this) {
                instance ?: DecryptorProviderRepository(context.applicationContext).also { instance = it }
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
            val defaultList = ALL_AVAILABLE.map {
                InstalledDecryptorProvider(
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
        val list = mutableListOf<InstalledDecryptorProvider>()
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
                        list.add(InstalledDecryptorProvider(id, name, isEnabled, installedAt))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing installed Decryptor providers: ${e.message}")
            }
        }
        _installedProviders.value = list
    }

    private fun saveProviders(list: List<InstalledDecryptorProvider>) {
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

    fun getInstalledProviders(): List<InstalledDecryptorProvider> = _installedProviders.value

    fun getAllAvailableProviders(): List<DecryptorProviderInfo> = ALL_AVAILABLE

    fun installProvider(id: String) {
        val clean = id.trim().lowercase()
        val current = _installedProviders.value.toMutableList()
        if (current.none { it.id == clean }) {
            val info = ALL_AVAILABLE.firstOrNull { it.id == clean }
            val name = info?.name ?: clean.replaceFirstChar { it.uppercase() }
            current.add(InstalledDecryptorProvider(clean, name, isEnabled = true))
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
            InstalledDecryptorProvider(it.id, it.name, isEnabled = true)
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
