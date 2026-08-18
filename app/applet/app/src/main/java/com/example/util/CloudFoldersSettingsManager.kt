package com.example.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object CloudFoldersSettingsManager {
    private const val PREFS_NAME = "cloud_folders_settings"
    private const val KEY_MEGA_FOLDERS_JSON = "mega_folder_urls_json_v2"
    private const val KEY_TELEGRAM_CHANNELS_JSON = "telegram_channel_urls_json_v2"

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
    fun formatMegaUrl(rawInput: String): String {
        var trimmed = rawInput.trim()
        if (trimmed.isBlank()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        return trimmed
    }

    fun getMegaFolderUrls(context: Context): List<String> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_MEGA_FOLDERS_JSON, null)
        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val url = formatMegaUrl(array.optString(i, ""))
                    if (url.isNotBlank()) {
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
        val formatted = formatMegaUrl(url)
        if (formatted.isBlank()) return
        val current = getMegaFolderUrls(context).toMutableList()
        if (!current.contains(formatted)) {
            current.add(formatted)
            saveMegaFolders(context, current)
        }
    }

    fun addMultipleMegaFolderUrls(context: Context, rawInput: String): Int {
        val urls = parseMultiUrls(rawInput)
        if (urls.isEmpty()) return 0
        val current = getMegaFolderUrls(context).toMutableList()
        var addedCount = 0
        urls.forEach { raw ->
            val formatted = formatMegaUrl(raw)
            if (formatted.isNotBlank() && !current.contains(formatted)) {
                current.add(formatted)
                addedCount++
            }
        }
        if (addedCount > 0) {
            saveMegaFolders(context, current)
        }
        return addedCount
    }

    fun removeMegaFolderUrl(context: Context, url: String) {
        val formatted = formatMegaUrl(url)
        val current = getMegaFolderUrls(context).toMutableList()
        current.removeAll { 
            it.trim().equals(url.trim(), ignoreCase = true) ||
            it.trim().equals(formatted.trim(), ignoreCase = true)
        }
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
                    if (url.isNotBlank()) {
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
        if (formatted.isBlank()) return
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
            if (formatted.isNotBlank() && !current.contains(formatted)) {
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
