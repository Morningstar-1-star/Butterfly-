package com.example.util

import android.content.Context
import android.content.SharedPreferences

object NotInterestedManager {
    private const val PREFS_NAME = "not_interested_prefs"
    private const val KEY_HIDDEN_IDS = "hidden_video_ids"
    private const val KEY_BLOCKED_CHANNELS = "blocked_channels"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getHiddenVideoIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_HIDDEN_IDS, emptySet()) ?: emptySet()
    }

    fun getBlockedChannels(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_BLOCKED_CHANNELS, emptySet()) ?: emptySet()
    }

    fun markNotInterested(context: Context, videoId: String, channelName: String? = null) {
        val cleanVid = videoId.trim()
        if (cleanVid.isEmpty()) return
        val prefs = getPrefs(context)
        val currentIds = getHiddenVideoIds(context).toMutableSet()
        currentIds.add(cleanVid)
        val editor = prefs.edit().putStringSet(KEY_HIDDEN_IDS, currentIds)

        val cleanChannel = channelName?.trim()?.lowercase() ?: ""
        if (cleanChannel.isNotBlank()) {
            val currentChannels = getBlockedChannels(context).toMutableSet()
            currentChannels.add(cleanChannel)
            editor.putStringSet(KEY_BLOCKED_CHANNELS, currentChannels)
        }
        editor.apply()
    }

    fun removeNotInterested(context: Context, videoId: String) {
        val cleanVid = videoId.trim()
        val current = getHiddenVideoIds(context).toMutableSet()
        current.remove(cleanVid)
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_IDS, current).apply()
    }

    fun isVideoBlocked(context: Context, videoId: String, channelName: String? = null): Boolean {
        val cleanVid = videoId.trim()
        if (cleanVid.isEmpty()) return false
        val hidden = getHiddenVideoIds(context)
        if (hidden.contains(cleanVid)) return true
        val cleanChannel = channelName?.trim()?.lowercase() ?: ""
        if (cleanChannel.isNotEmpty() && getBlockedChannels(context).contains(cleanChannel)) return true
        return false
    }

    fun setHiddenVideoIds(context: Context, ids: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_IDS, ids).apply()
    }

    fun setBlockedChannels(context: Context, channels: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_CHANNELS, channels).apply()
    }

    fun clearAll(context: Context) {
        getPrefs(context).edit().remove(KEY_HIDDEN_IDS).remove(KEY_BLOCKED_CHANNELS).apply()
    }
}


