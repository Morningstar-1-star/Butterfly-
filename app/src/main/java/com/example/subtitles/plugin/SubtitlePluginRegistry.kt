package com.example.subtitles.plugin

import android.content.Context
import android.content.SharedPreferences
import com.example.subtitles.plugin.plugins.GestdownPlugin
import com.example.subtitles.plugin.plugins.OpenSubtitlesPlugin
import com.example.subtitles.plugin.plugins.PodnapisiPlugin
import com.example.subtitles.plugin.plugins.SubDlPlugin
import com.example.subtitles.plugin.plugins.SubtitleCatPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight Provider Plugin Registry.
 * Manages plugin lifecycle, user settings, optional API keys, and lazy-loading
 * without blocking application startup.
 */
object SubtitlePluginRegistry {

    private const val PREFS_NAME = "butterfly_subtitle_plugins"
    private const val PREF_PREFIX_ENABLED = "enabled_"
    private const val PREF_PREFIX_INSTALLED = "installed_"
    private const val PREF_PREFIX_API_KEY = "apikey_"

    // Known default plugin factories - instantiates lazily on demand
    private val pluginFactories = mapOf<String, (context: Context) -> SubtitlePlugin>(
        "subdl" to { ctx ->
            SubDlPlugin(customApiKeyProvider = { getPluginApiKey(ctx, "subdl") })
        },
        "opensubtitles" to { ctx ->
            OpenSubtitlesPlugin(customApiKeyProvider = { getPluginApiKey(ctx, "opensubtitles") })
        },
        "subtitlecat" to { _ ->
            SubtitleCatPlugin()
        },
        "gestdown" to { _ ->
            GestdownPlugin()
        },
        "podnapisi" to { _ ->
            PodnapisiPlugin()
        }
    )

    // Base descriptors with default enable/install states
    private val defaultDescriptors = listOf(
        SubtitlePluginInfo(
            id = "subdl",
            name = "SubDL",
            description = "Official SubDL REST API v1. Rapid subtitle indexer with automated ZIP unpacking.",
            author = "SubDL Team",
            version = "1.4.5",
            requiresApiKey = false,
            apiKeyHelpUrl = "https://subdl.com",
            isInstalled = true,
            isEnabled = true,
            supportsFps = true,
            supportsHi = true,
            websiteUrl = "https://subdl.com"
        ),
        SubtitlePluginInfo(
            id = "opensubtitles",
            name = "OpenSubtitles.com",
            description = "World's largest multi-language subtitle database. Full support for FPS, HI, and moviehash.",
            author = "OpenSubtitles Org",
            version = "2.1.0",
            requiresApiKey = false,
            apiKeyHelpUrl = "https://www.opensubtitles.com",
            isInstalled = true,
            isEnabled = true,
            supportsFps = true,
            supportsHi = true,
            websiteUrl = "https://www.opensubtitles.com"
        ),
        SubtitlePluginInfo(
            id = "subtitlecat",
            name = "SubtitleCat",
            description = "Public multi-language subtitle library. Zero-config, supports 100+ languages without API keys.",
            author = "SubtitleCat Community",
            version = "1.2.0",
            requiresApiKey = false,
            isInstalled = true,
            isEnabled = true,
            supportsFps = false,
            supportsHi = false,
            websiteUrl = "https://www.subtitlecat.com"
        ),
        SubtitlePluginInfo(
            id = "gestdown",
            name = "Gestdown (Bazarr Addic7ed)",
            description = "Addic7ed TV series subtitle engine from Bazarr catalog. Precise episode & season sync.",
            author = "Bazarr / Addic7ed Community",
            version = "1.3.0",
            requiresApiKey = false,
            isInstalled = true,
            isEnabled = true,
            supportsFps = false,
            supportsHi = true,
            websiteUrl = "https://api.gestdown.info"
        ),
        SubtitlePluginInfo(
            id = "podnapisi",
            name = "Podnapisi",
            description = "Community subtitle catalog from Bazarr. Broad multi-language coverage & HI flags.",
            author = "Bazarr / Podnapisi Community",
            version = "1.1.0",
            requiresApiKey = false,
            isInstalled = true,
            isEnabled = false, // disabled by default as community scraper backup
            supportsFps = true,
            supportsHi = true,
            websiteUrl = "https://www.podnapisi.net"
        )
    )

    // Cached active plugin instances to avoid frequent allocations
    private val pluginInstances = ConcurrentHashMap<String, SubtitlePlugin>()

    private val _pluginUpdateSignal = MutableStateFlow(0L)
    val pluginUpdateSignal: StateFlow<Long> = _pluginUpdateSignal.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns current list of all plugin descriptors with user preferences applied.
     */
    fun getAllPluginDescriptors(context: Context): List<SubtitlePluginInfo> {
        val prefs = getPrefs(context)
        return defaultDescriptors.map { def ->
            val installed = prefs.getBoolean(PREF_PREFIX_INSTALLED + def.id, def.isInstalled)
            val enabled = if (installed) {
                prefs.getBoolean(PREF_PREFIX_ENABLED + def.id, def.isEnabled)
            } else false

            def.copy(
                isInstalled = installed,
                isEnabled = enabled
            )
        }
    }

    fun isPluginEnabled(context: Context, pluginId: String): Boolean {
        val prefs = getPrefs(context)
        val installed = isPluginInstalled(context, pluginId)
        if (!installed) return false

        val def = defaultDescriptors.find { it.id == pluginId }
        val defaultVal = def?.isEnabled ?: true
        return prefs.getBoolean(PREF_PREFIX_ENABLED + pluginId, defaultVal)
    }

    fun setPluginEnabled(context: Context, pluginId: String, enabled: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean(PREF_PREFIX_ENABLED + pluginId, enabled).apply()
        _pluginUpdateSignal.value = System.currentTimeMillis()
    }

    fun isPluginInstalled(context: Context, pluginId: String): Boolean {
        val prefs = getPrefs(context)
        val def = defaultDescriptors.find { it.id == pluginId }
        val defaultVal = def?.isInstalled ?: true
        return prefs.getBoolean(PREF_PREFIX_INSTALLED + pluginId, defaultVal)
    }

    fun setPluginInstalled(context: Context, pluginId: String, installed: Boolean) {
        val prefs = getPrefs(context)
        val editor = prefs.edit().putBoolean(PREF_PREFIX_INSTALLED + pluginId, installed)
        if (!installed) {
            // Also disable if uninstalled
            editor.putBoolean(PREF_PREFIX_ENABLED + pluginId, false)
            pluginInstances.remove(pluginId)
        }
        editor.apply()
        _pluginUpdateSignal.value = System.currentTimeMillis()
    }

    fun getPluginApiKey(context: Context, pluginId: String): String? {
        val prefs = getPrefs(context)
        val key = prefs.getString(PREF_PREFIX_API_KEY + pluginId, null)?.trim()
        return if (!key.isNullOrBlank()) key else null
    }

    fun setPluginApiKey(context: Context, pluginId: String, apiKey: String?) {
        val prefs = getPrefs(context)
        if (apiKey.isNullOrBlank()) {
            prefs.edit().remove(PREF_PREFIX_API_KEY + pluginId).apply()
        } else {
            prefs.edit().putString(PREF_PREFIX_API_KEY + pluginId, apiKey.trim()).apply()
        }
        // Invalidate instance so it picks up the new key on next call
        pluginInstances.remove(pluginId)
        _pluginUpdateSignal.value = System.currentTimeMillis()
    }

    /**
     * Lazy-loads or retrieves a cached plugin instance.
     */
    fun getPlugin(pluginId: String, context: Context): SubtitlePlugin? {
        if (!isPluginInstalled(context, pluginId)) return null

        return pluginInstances.getOrPut(pluginId) {
            val factory = pluginFactories[pluginId] ?: return null
            factory(context.applicationContext)
        }
    }

    /**
     * Returns only the currently enabled and installed plugins for searching.
     */
    fun getEnabledPlugins(context: Context): List<SubtitlePlugin> {
        val list = mutableListOf<SubtitlePlugin>()
        for (def in defaultDescriptors) {
            if (isPluginEnabled(context, def.id)) {
                val plugin = getPlugin(def.id, context)
                if (plugin != null) {
                    list.add(plugin)
                }
            }
        }
        return list
    }

    /**
     * Executes connection test for a given plugin.
     */
    suspend fun testPlugin(context: Context, pluginId: String): Result<String> {
        val factory = pluginFactories[pluginId]
            ?: return Result.failure(IllegalArgumentException("Unknown plugin: $pluginId"))
        val plugin = factory(context.applicationContext)
        return plugin.testConnection()
    }

    /**
     * Restores all providers to default installation, enablement and keys.
     */
    fun resetToDefaults(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().clear().apply()
        pluginInstances.clear()
        _pluginUpdateSignal.value = System.currentTimeMillis()
    }
}
