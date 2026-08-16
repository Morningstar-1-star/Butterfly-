package com.example.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object CloudFoldersSettingsManager {

    private const val PREFS_NAME = "cloud_folders_settings"
    private const val KEY_MEGA_FOLDERS_JSON = "mega_folder_urls_json_v2"
    private const val KEY_TELEGRAM_CHANNELS_JSON = "telegram_channel_urls_json_v2"
    private const val KEY_INITIALIZED = "cloud_folders_initialized_v2"

    // Legacy dummy links to discard permanently
    private val LEGACY_DISCARD_URLS = setOf(
        "https://mega.nz/folder/Io4myLQC#0MV-ZU9NXIQZtRfcKSiqog/folder/1hogxZ7B",
        "https://mega.nz/folder/r9BlSRgA#kM1H8SJBL6oFlzKC8k1vyg/folder/esAkWZ4Y",
        "https://t.me/s/movies",
        "https://t.me/s/telegram",
        "https://t.me/movies",
        "https://t.me/telegram"
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Helper to parse user text that may contain multiple links/handles
     * separated by newlines, commas, semicolons, or spaces.
     */
    fun parseMultiUrls(rawInput: String): List<String> {
        if (rawInput.isBlank()) return emptyList()
        val tokens = rawInput.split(Regex("[\\n\\r,;\\s]+"))
        return tokens.map { it.trim() }.filter { it.isNotBlank() }
    }

    // --- MEGA FOLDERS ---

    fun getMegaFolderUrls(context: Context): List<String> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_MEGA_FOLDERS_JSON, null)
        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val url = array.optString(i, "").trim()
                    if (url.isNotBlank() && !LEGACY_DISCARD_URLS.contains(url)) {
                        list.add(url)
                    }
                }
                return list
            } catch (e: Exception) {
                // fall through
            }
        }
        return emptyList()
    }

    fun addMegaFolderUrl(context: Context, url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank() || LEGACY_DISCARD_URLS.contains(trimmed)) return
        val current = getMegaFolderUrls(context).toMutableList()
        if (!current.contains(trimmed)) {
            current.add(trimmed)
            saveMegaFolders(context, current)
        }
    }

    fun addMultipleMegaFolderUrls(context: Context, rawInput: String): Int {
        val urls = parseMultiUrls(rawInput)
        if (urls.isEmpty()) return 0
        val current = getMegaFolderUrls(context).toMutableList()
        var addedCount = 0
        urls.forEach { url ->
            val trimmed = url.trim()
            if (trimmed.isNotBlank() && !LEGACY_DISCARD_URLS.contains(trimmed) && !current.contains(trimmed)) {
                current.add(trimmed)
                addedCount++
            }
        }
        if (addedCount > 0) {
            saveMegaFolders(context, current)
        }
        return addedCount
    }

    fun removeMegaFolderUrl(context: Context, url: String) {
        val current = getMegaFolderUrls(context).toMutableList()
        current.removeAll { it.trim().equals(url.trim(), ignoreCase = true) }
        saveMegaFolders(context, current)
    }

    fun clearAllMegaFolders(context: Context) {
        saveMegaFolders(context, emptyList())
    }

    private fun saveMegaFolders(context: Context, list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        getPrefs(context).edit()
            .putString(KEY_MEGA_FOLDERS_JSON, array.toString())
            .remove("mega_folder_urls") // clear legacy Set
            .apply()
    }

    // --- TELEGRAM CHANNELS ---

    fun getTelegramChannelUrls(context: Context): List<String> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_TELEGRAM_CHANNELS_JSON, null)
        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val url = array.optString(i, "").trim()
                    if (url.isNotBlank() && !LEGACY_DISCARD_URLS.contains(url)) {
                        list.add(url)
                    }
                }
                return list
            } catch (e: Exception) {
                // fall through
            }
        }
        return emptyList()
    }

    fun formatTelegramUrl(rawInput: String): String {
        var trimmed = rawInput.trim()
        if (trimmed.isBlank()) return ""
        // Remove trailing slashes
        trimmed = trimmed.trimEnd('/')
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            if (trimmed.contains("t.me/") && !trimmed.contains("t.me/s/")) {
                val channelPart = trimmed.substringAfter("t.me/").substringBefore("/")
                return "https://t.me/s/$channelPart"
            }
            return trimmed
        }
        val cleanName = trimmed.removePrefix("@").removePrefix("t.me/s/").removePrefix("t.me/").substringBefore("/")
        return "https://t.me/s/$cleanName"
    }

    fun addTelegramChannelUrl(context: Context, rawInput: String) {
        val formatted = formatTelegramUrl(rawInput)
        if (formatted.isBlank() || LEGACY_DISCARD_URLS.contains(formatted)) return
        val current = getTelegramChannelUrls(context).toMutableList()
        if (!current.contains(formatted)) {
            current.add(formatted)
            saveTelegramChannels(context, current)
        }
    }

    fun addMultipleTelegramChannelUrls(context: Context, rawInput: String): Int {
        val tokens = parseMultiUrls(rawInput)
        if (tokens.isEmpty()) return 0
        val current = getTelegramChannelUrls(context).toMutableList()
        var addedCount = 0
        tokens.forEach { token ->
            val formatted = formatTelegramUrl(token)
            if (formatted.isNotBlank() && !LEGACY_DISCARD_URLS.contains(formatted) && !current.contains(formatted)) {
                current.add(formatted)
                addedCount++
            }
        }
        if (addedCount > 0) {
            saveTelegramChannels(context, current)
        }
        return addedCount
    }

    fun removeTelegramChannelUrl(context: Context, url: String) {
        val formatted = formatTelegramUrl(url)
        val current = getTelegramChannelUrls(context).toMutableList()
        current.removeAll { 
            it.trim().equals(url.trim(), ignoreCase = true) || 
            it.trim().equals(formatted.trim(), ignoreCase = true) 
        }
        saveTelegramChannels(context, current)
    }

    fun clearAllTelegramChannels(context: Context) {
        saveTelegramChannels(context, emptyList())
    }

    private fun saveTelegramChannels(context: Context, list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        getPrefs(context).edit()
            .putString(KEY_TELEGRAM_CHANNELS_JSON, array.toString())
            .remove("telegram_channel_urls") // clear legacy Set
            .apply()
    }
}
