package com.example.util

import android.content.Context
import android.content.SharedPreferences

object NotInterestedManager {
    private const val PREFS_NAME = "not_interested_prefs"
    private const val KEY_HIDDEN_IDS = "hidden_video_ids"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getHiddenVideoIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_HIDDEN_IDS, emptySet()) ?: emptySet()
    }

    fun markNotInterested(context: Context, videoId: String) {
        val current = getHiddenVideoIds(context).toMutableSet()
        current.add(videoId)
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_IDS, current).apply()
    }

    fun removeNotInterested(context: Context, videoId: String) {
        val current = getHiddenVideoIds(context).toMutableSet()
        current.remove(videoId)
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_IDS, current).apply()
    }

    fun clearAll(context: Context) {
        getPrefs(context).edit().remove(KEY_HIDDEN_IDS).apply()
    }
}
