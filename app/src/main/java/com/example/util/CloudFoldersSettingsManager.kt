package com.example.util

import android.content.Context
import android.content.SharedPreferences

object CloudFoldersSettingsManager {

    private const val PREFS_NAME = "cloud_folders_settings"
    private const val KEY_MEGA_FOLDERS = "mega_folder_urls"
    private const val KEY_TELEGRAM_CHANNELS = "telegram_channel_urls"

    private val DEFAULT_MEGA_FOLDERS = listOf(
        "https://mega.nz/folder/Io4myLQC#0MV-ZU9NXIQZtRfcKSiqog/folder/1hogxZ7B",
        "https://mega.nz/folder/r9BlSRgA#kM1H8SJBL6oFlzKC8k1vyg/folder/esAkWZ4Y"
    )

    private val DEFAULT_TELEGRAM_CHANNELS = listOf(
        "https://t.me/s/movies",
        "https://t.me/s/telegram"
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getMegaFolderUrls(context: Context): List<String> {
        val set = getPrefs(context).getStringSet(KEY_MEGA_FOLDERS, null)
        if (set == null || set.isEmpty()) {
            return DEFAULT_MEGA_FOLDERS
        }
        return set.toList().filter { it.isNotBlank() }
    }

    fun addMegaFolderUrl(context: Context, url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val current = getMegaFolderUrls(context).toMutableSet()
        current.add(trimmed)
        getPrefs(context).edit().putStringSet(KEY_MEGA_FOLDERS, current).apply()
    }

    fun removeMegaFolderUrl(context: Context, url: String) {
        val current = getMegaFolderUrls(context).toMutableSet()
        current.remove(url.trim())
        getPrefs(context).edit().putStringSet(KEY_MEGA_FOLDERS, current).apply()
    }

    fun getTelegramChannelUrls(context: Context): List<String> {
        val set = getPrefs(context).getStringSet(KEY_TELEGRAM_CHANNELS, null)
        if (set == null || set.isEmpty()) {
            return DEFAULT_TELEGRAM_CHANNELS
        }
        return set.toList().filter { it.isNotBlank() }
    }

    fun addTelegramChannelUrl(context: Context, url: String) {
        var trimmed = url.trim()
        if (trimmed.isBlank()) return
        if (!trimmed.startsWith("http")) {
            val cleanName = trimmed.removePrefix("@").removePrefix("t.me/s/").removePrefix("t.me/")
            trimmed = "https://t.me/s/$cleanName"
        } else if (trimmed.contains("t.me/") && !trimmed.contains("t.me/s/")) {
            trimmed = trimmed.replace("t.me/", "t.me/s/")
        }
        val current = getTelegramChannelUrls(context).toMutableSet()
        current.add(trimmed)
        getPrefs(context).edit().putStringSet(KEY_TELEGRAM_CHANNELS, current).apply()
    }

    fun removeTelegramChannelUrl(context: Context, url: String) {
        val current = getTelegramChannelUrls(context).toMutableSet()
        current.remove(url.trim())
        getPrefs(context).edit().putStringSet(KEY_TELEGRAM_CHANNELS, current).apply()
    }
}
