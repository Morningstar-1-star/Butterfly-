package com.example.vega

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class VegaProviderRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _installedProviders = MutableStateFlow<List<InstalledVegaProvider>>(emptyList())
    val installedProviders: StateFlow<List<InstalledVegaProvider>> = _installedProviders.asStateFlow()

    private val _serverUrl = MutableStateFlow(
        prefs.getString(KEY_SERVER_URL, VegaProviderClient.DEFAULT_SERVER_URL)
            ?.ifBlank { VegaProviderClient.DEFAULT_SERVER_URL }
            ?: VegaProviderClient.DEFAULT_SERVER_URL
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    init {
        loadInstalledProviders()
    }

    fun getServerUrl(): String {
        return _serverUrl.value
    }

    fun setServerUrl(url: String) {
        val cleanUrl = url.trim().trimEnd('/')
        if (cleanUrl.isNotBlank()) {
            _serverUrl.value = cleanUrl
            prefs.edit().putString(KEY_SERVER_URL, cleanUrl).apply()
        }
    }

    private fun loadInstalledProviders() {
        val jsonStr = prefs.getString(KEY_INSTALLED_PROVIDERS, null)
        val list = mutableListOf<InstalledVegaProvider>()

        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    if (id.isNotBlank()) {
                        val name = obj.optString("name").ifBlank { VegaProviderClient.formatProviderDisplayName(id) }
                        val isEnabled = obj.optBoolean("isEnabled", true)
                        val installedAt = obj.optLong("installedAtMs", System.currentTimeMillis())
                        list.add(
                            InstalledVegaProvider(
                                id = id,
                                name = name,
                                isEnabled = isEnabled,
                                installedAtMs = installedAt
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        } else {
            // Seed all default working & available providers
            val defaultSeeds = listOf(
                "hdhub4u", "4khdhub", "vega", "topmovies", "world4u", "uhd", 
                "showbox", "ridoMovies", "eonMovies", "movieBoxWeb", "mod", 
                "ringz", "kissKh", "torrentio", "autoEmbed", "drive", 
                "guardahd", "zeefliz", "anikoto", "movies4u"
            )
            defaultSeeds.forEach { id ->
                list.add(InstalledVegaProvider(id = id, name = VegaProviderClient.formatProviderDisplayName(id), isEnabled = true))
            }
            saveInstalledProviders(list)
        }

        _installedProviders.value = list
    }

    private fun saveInstalledProviders(list: List<InstalledVegaProvider>) {
        _installedProviders.value = list
        try {
            val array = JSONArray()
            list.forEach { provider ->
                val obj = JSONObject().apply {
                    put("id", provider.id)
                    put("name", provider.name)
                    put("isEnabled", provider.isEnabled)
                    put("installedAtMs", provider.installedAtMs)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_INSTALLED_PROVIDERS, array.toString()).apply()
        } catch (e: Exception) {
            // Ignore serialization error
        }
    }

    fun getInstalledProviders(): List<InstalledVegaProvider> {
        return _installedProviders.value
    }

    fun installProvider(id: String, name: String = VegaProviderClient.formatProviderDisplayName(id)) {
        val current = _installedProviders.value.toMutableList()
        val index = current.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        if (index >= 0) {
            current[index] = current[index].copy(name = name, isEnabled = true)
        } else {
            current.add(InstalledVegaProvider(id = id.trim().lowercase(), name = name, isEnabled = true))
        }
        saveInstalledProviders(current)
    }

    fun installAllProviders(providerIds: List<String>) {
        val currentMap = _installedProviders.value.associateBy { it.id.lowercase() }.toMutableMap()
        providerIds.forEach { rawId ->
            val cleanId = rawId.trim().lowercase()
            if (cleanId.isNotBlank()) {
                val displayName = VegaProviderClient.formatProviderDisplayName(cleanId)
                val existing = currentMap[cleanId]
                if (existing != null) {
                    currentMap[cleanId] = existing.copy(name = displayName, isEnabled = true)
                } else {
                    currentMap[cleanId] = InstalledVegaProvider(id = cleanId, name = displayName, isEnabled = true)
                }
            }
        }
        saveInstalledProviders(currentMap.values.toList())
    }

    fun uninstallProvider(id: String) {
        val current = _installedProviders.value.filterNot { it.id.equals(id, ignoreCase = true) }
        saveInstalledProviders(current)
    }

    fun setProviderEnabled(id: String, isEnabled: Boolean) {
        val current = _installedProviders.value.map {
            if (it.id.equals(id, ignoreCase = true)) {
                it.copy(isEnabled = isEnabled)
            } else {
                it
            }
        }
        saveInstalledProviders(current)
    }

    fun isProviderInstalled(id: String): Boolean {
        return _installedProviders.value.any { it.id.equals(id, ignoreCase = true) }
    }

    fun isProviderEnabled(id: String): Boolean {
        return _installedProviders.value.firstOrNull { it.id.equals(id, ignoreCase = true) }?.isEnabled ?: false
    }

    companion object {
        private const val PREFS_NAME = "butterfly_vega_providers_prefs"
        private const val KEY_INSTALLED_PROVIDERS = "installed_vega_providers_json"
        private const val KEY_SERVER_URL = "vega_server_host_url"
    }
}
