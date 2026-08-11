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
    private const val KEY_JAVINFO_API_KEY = "javinfo_api_key"
    private const val KEY_DOUBLE_TAP_SEEK_SECS = "double_tap_seek_secs"
    private const val KEY_DAILYMOTION_LANG = "dailymotion_language"
    private const val KEY_ARCHIVE_FORMAT_PREF = "archive_format_pref"

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

    fun getJavInfoApiKey(context: Context): String {
        val stored = getPrefs(context).getString(KEY_JAVINFO_API_KEY, "")?.trim() ?: ""
        return stored.ifBlank { "jvi_guxSYVMOELEfBGEDFlLPZeizhBbupsUsgggTgosYErOuEnLSXyVTWrUJwDFVmTaV" }
    }

    fun setJavInfoApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_JAVINFO_API_KEY, key.trim()).apply()
    }

    fun getDoubleTapSeekSecs(context: Context): Int {
        return getPrefs(context).getInt(KEY_DOUBLE_TAP_SEEK_SECS, 10)
    }

    fun setDoubleTapSeekSecs(context: Context, secs: Int) {
        getPrefs(context).edit().putInt(KEY_DOUBLE_TAP_SEEK_SECS, secs).apply()
    }

    fun getDailymotionLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_DAILYMOTION_LANG, "en")?.trim() ?: "en"
    }

    fun setDailymotionLanguage(context: Context, lang: String) {
        getPrefs(context).edit().putString(KEY_DAILYMOTION_LANG, lang.trim()).apply()
    }

    fun getArchiveFormatPreference(context: Context): String {
        return getPrefs(context).getString(KEY_ARCHIVE_FORMAT_PREF, "FAST_H264") ?: "FAST_H264"
    }

    fun setArchiveFormatPreference(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_ARCHIVE_FORMAT_PREF, mode).apply()
    }
}
