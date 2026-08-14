package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages user preferences for displaying or hiding tags/badges on video thumbnails.
 * Supports a master toggle ("Hide All Video Tags") and granular per-tag toggles
 * (e.g. YouTube, Dailymotion, Anime, Series, Movies, 18+, Archive, Vimeo).
 */
class VideoTagPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hideAllTags = MutableStateFlow(
        prefs.getBoolean(KEY_HIDE_ALL_TAGS, false)
    )
    val hideAllTags: StateFlow<Boolean> = _hideAllTags.asStateFlow()

    private val _hiddenTags = MutableStateFlow(
        prefs.getStringSet(KEY_HIDDEN_TAGS, emptySet()) ?: emptySet()
    )
    val hiddenTags: StateFlow<Set<String>> = _hiddenTags.asStateFlow()

    fun setHideAllTags(hide: Boolean) {
        _hideAllTags.value = hide
        prefs.edit().putBoolean(KEY_HIDE_ALL_TAGS, hide).apply()
    }

    fun setTagHidden(tag: String, hidden: Boolean) {
        val current = _hiddenTags.value.toMutableSet()
        val normalizedTag = normalizeTag(tag)
        if (hidden) {
            current.add(normalizedTag)
            // also handle movie/movies alias
            if (normalizedTag.equals("Movie", ignoreCase = true)) {
                current.add("Movies")
            }
        } else {
            current.remove(normalizedTag)
            if (normalizedTag.equals("Movie", ignoreCase = true) || normalizedTag.equals("Movies", ignoreCase = true)) {
                current.remove("Movie")
                current.remove("Movies")
            }
        }
        _hiddenTags.value = current
        prefs.edit().putStringSet(KEY_HIDDEN_TAGS, current).apply()
    }

    fun isTagHidden(tag: String): Boolean {
        if (_hideAllTags.value) return true
        val normalized = normalizeTag(tag)
        return _hiddenTags.value.contains(normalized) || 
               (normalized.equals("Movie", ignoreCase = true) && _hiddenTags.value.contains("Movies")) ||
               (normalized.equals("Movies", ignoreCase = true) && _hiddenTags.value.contains("Movie"))
    }

    fun isTagVisible(tag: String): Boolean {
        return !isTagHidden(tag)
    }

    fun unhideAllSpecificTags() {
        _hiddenTags.value = emptySet()
        prefs.edit().putStringSet(KEY_HIDDEN_TAGS, emptySet()).apply()
    }

    private fun normalizeTag(tag: String): String {
        return when (tag.lowercase().trim()) {
            "youtube" -> "YouTube"
            "dailymotion" -> "Dailymotion"
            "anime" -> "Anime"
            "series" -> "Series"
            "movie", "movies" -> "Movie"
            "18+", "18", "adult" -> "18+"
            "archive", "archive_org" -> "Archive"
            "vimeo" -> "Vimeo"
            else -> tag.trim().replaceFirstChar { it.uppercase() }
        }
    }

    companion object {
        private const val PREFS_NAME = "butterfly_video_tag_prefs"
        private const val KEY_HIDE_ALL_TAGS = "hide_all_video_tags"
        private const val KEY_HIDDEN_TAGS = "hidden_video_tags_set"

        val AVAILABLE_TAGS = listOf(
            "YouTube",
            "Dailymotion",
            "Anime",
            "Series",
            "Movie",
            "18+",
            "Archive",
            "Vimeo"
        )

        @Volatile
        private var instance: VideoTagPreferences? = null

        fun getInstance(context: Context): VideoTagPreferences {
            return instance ?: synchronized(this) {
                instance ?: VideoTagPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
