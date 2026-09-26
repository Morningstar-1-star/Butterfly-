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

    private val _isVegaMasterEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_VEGA_MASTER_ENABLED, false)
    )
    val isVegaMasterEnabled: StateFlow<Boolean> = _isVegaMasterEnabled.asStateFlow()

    private val _installedProviders = MutableStateFlow<List<InstalledVegaProvider>>(emptyList())
    val installedProviders: StateFlow<List<InstalledVegaProvider>> = _installedProviders.asStateFlow()

    private val _serverUrl = MutableStateFlow(
        prefs.getString(KEY_SERVER_URL, VegaProviderClient.DEFAULT_SERVER_URL)
            ?.ifBlank { VegaProviderClient.DEFAULT_SERVER_URL }
            ?: VegaProviderClient.DEFAULT_SERVER_URL
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    init {
        // Initialize in-app Vega engine and registry with local assets
        try {
            VegaInAppEngine.init(context)
        } catch (_: Exception) {}

        // Sync global flag on startup
        VegaProviderClient.isVegaGloballyEnabled = _isVegaMasterEnabled.value
        loadInstalledProviders()
    }

    fun isVegaMasterEnabled(): Boolean = _isVegaMasterEnabled.value

    fun setVegaMasterEnabled(enabled: Boolean) {
        _isVegaMasterEnabled.value = enabled
        VegaProviderClient.isVegaGloballyEnabled = enabled
        prefs.edit().putBoolean(KEY_VEGA_MASTER_ENABLED, enabled).apply()
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
        val migrationDone = prefs.getBoolean(KEY_MIGRATION_V3, false)
        if (!migrationDone) {
            // User requested all Vega sources uninstalled and disabled by default
            prefs.edit()
                .putString(KEY_INSTALLED_PROVIDERS, "[]")
                .putBoolean(KEY_VEGA_MASTER_ENABLED, false)
                .putBoolean(KEY_MIGRATION_V3, true)
                .apply()
            _isVegaMasterEnabled.value = false
            VegaProviderClient.isVegaGloballyEnabled = false
            _installedProviders.value = emptyList()
            return
        }

        val jsonStr = prefs.getString(KEY_INSTALLED_PROVIDERS, null)
        val list = mutableListOf<InstalledVegaProvider>()

        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim().lowercase()
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
        }

        _installedProviders.value = list
    }

    private fun saveInstalledProviders(list: List<InstalledVegaProvider>) {
        _installedProviders.value = list
        try {
            val array = JSONArray()
            list.forEach { provider ->
                val obj = JSONObject().apply {
                    put("id", provider.id.trim().lowercase())
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
        val cleanId = id.trim().lowercase()
        val current = _installedProviders.value.toMutableList()
        val index = current.indexOfFirst { it.id.equals(cleanId, ignoreCase = true) }
        if (index >= 0) {
            current[index] = current[index].copy(name = name, isEnabled = true)
        } else {
            current.add(InstalledVegaProvider(id = cleanId, name = name, isEnabled = true))
        }
        saveInstalledProviders(current)
    }

    fun installAllProviders(providerIds: List<String>) {
        val currentMap = _installedProviders.value.associateBy { it.id.trim().lowercase() }.toMutableMap()
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
        val cleanId = id.trim().lowercase()
        val current = _installedProviders.value.filterNot { it.id.equals(cleanId, ignoreCase = true) }
        saveInstalledProviders(current)
    }

    fun uninstallAllProviders() {
        saveInstalledProviders(emptyList())
    }

    fun setProviderEnabled(id: String, isEnabled: Boolean) {
        val cleanId = id.trim().lowercase()
        val current = _installedProviders.value.map {
            if (it.id.equals(cleanId, ignoreCase = true)) {
                it.copy(isEnabled = isEnabled)
            } else {
                it
            }
        }
        saveInstalledProviders(current)
    }

    fun isProviderInstalled(id: String): Boolean {
        val cleanId = id.trim().lowercase()
        return _installedProviders.value.any { it.id.equals(cleanId, ignoreCase = true) }
    }

    fun isProviderEnabled(id: String): Boolean {
        val cleanId = id.trim().lowercase()
        return _installedProviders.value.firstOrNull { it.id.equals(cleanId, ignoreCase = true) }?.isEnabled ?: false
    }

    companion object {
        private const val PREFS_NAME = "butterfly_vega_providers_prefs"
        private const val KEY_INSTALLED_PROVIDERS = "installed_vega_providers_json"
        private const val KEY_SERVER_URL = "vega_server_host_url"
        private const val KEY_VEGA_MASTER_ENABLED = "vega_master_enabled"
        private const val KEY_MIGRATION_V3 = "vega_seeds_uninstalled_v3"
    }
}
