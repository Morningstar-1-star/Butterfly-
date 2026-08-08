package com.example.util

import android.content.Context
import android.content.SharedPreferences

object DebridSettingsManager {

    private const val PREFS_NAME = "debrid_settings"
    private const val KEY_TORBOX_API_KEY = "torbox_api_key"
    private const val KEY_ORION_API_KEY = "orion_api_key"
    private const val KEY_COMET_ENDPOINT = "comet_endpoint"
    private const val KEY_MEDIAFUSION_ENDPOINT = "mediafusion_endpoint"
    private const val KEY_ZILEAN_ENDPOINT = "zilean_endpoint"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTorBoxApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_TORBOX_API_KEY, "")?.trim() ?: ""
    }

    fun setTorBoxApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_TORBOX_API_KEY, key.trim()).apply()
    }

    fun getOrionApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_ORION_API_KEY, "")?.trim() ?: ""
    }

    fun setOrionApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_ORION_API_KEY, key.trim()).apply()
    }

    fun getCometEndpoint(context: Context): String {
        val custom = getPrefs(context).getString(KEY_COMET_ENDPOINT, "")?.trim() ?: ""
        return if (custom.isNotBlank()) custom else "https://comet.elfhosted.com"
    }

    fun setCometEndpoint(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_COMET_ENDPOINT, url.trim()).apply()
    }

    fun getMediaFusionEndpoint(context: Context): String {
        val custom = getPrefs(context).getString(KEY_MEDIAFUSION_ENDPOINT, "")?.trim() ?: ""
        return if (custom.isNotBlank()) custom else "https://mediafusion.elfhosted.com"
    }

    fun setMediaFusionEndpoint(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_MEDIAFUSION_ENDPOINT, url.trim()).apply()
    }

    fun getZileanEndpoint(context: Context): String {
        val custom = getPrefs(context).getString(KEY_ZILEAN_ENDPOINT, "")?.trim() ?: ""
        return if (custom.isNotBlank()) custom else "https://zilean.elfhosted.com"
    }

    fun setZileanEndpoint(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_ZILEAN_ENDPOINT, url.trim()).apply()
    }
}
