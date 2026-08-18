package com.example.util

import android.content.Context
import android.content.SharedPreferences

object DebridSettingsManager {

    private const val PREFS_NAME = "app_player_settings"
    private const val KEY_DOUBLE_TAP_SEEK_SECS = "double_tap_seek_secs"
    private const val KEY_DAILYMOTION_LANG = "dailymotion_language"
    private const val KEY_ARCHIVE_FORMAT_PREF = "archive_format_pref"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    fun getDefaultDownloadQuality(context: Context): String {
        return getPrefs(context).getString("default_download_quality", "720p") ?: "720p"
    }

    fun setDefaultDownloadQuality(context: Context, quality: String) {
        getPrefs(context).edit().putString("default_download_quality", quality).apply()
    }

    fun getRememberDownloadQuality(context: Context): Boolean {
        return getPrefs(context).getBoolean("remember_download_quality", false)
    }

    fun setRememberDownloadQuality(context: Context, remember: Boolean) {
        getPrefs(context).edit().putBoolean("remember_download_quality", remember).apply()
    }
}
