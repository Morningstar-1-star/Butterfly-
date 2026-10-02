package com.example.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages all video seekbar configurations, gestures, custom progress and accent colors,
 * DVR livestream rules, and thumbnail seekbars.
 */
class SeekbarPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "butterfly_seekbar_prefs"

        const val KEY_DISABLE_PRECISE_SEEKING = "disable_precise_seeking_gesture"
        const val KEY_ENABLE_CUSTOM_COLOR = "enable_custom_seekbar_color"
        const val KEY_SEEKBAR_COLOR = "seekbar_color"
        const val KEY_SEEKBAR_ACCENT_COLOR = "seekbar_accent_color"
        const val KEY_ENABLE_SEEKBAR_GRADIENT = "enable_seekbar_gradient"
        const val KEY_FULLSCREEN_LARGE_SEEKBAR = "enable_fullscreen_large_seekbar"
        const val KEY_ENABLE_LIVESTREAM_DVR = "enable_livestream_dvr"
        const val KEY_EXPAND_DVR_DURATION = "expand_livestream_dvr_duration"
        const val KEY_ENABLE_SLIDE_TO_SEEK = "enable_slide_to_seek"
        const val KEY_ENABLE_TAP_TO_SEEK = "enable_tap_to_seek"
        const val KEY_HIDE_PLAYER_SEEKBAR = "hide_video_player_seekbar"
        const val KEY_HIDE_THUMBNAILS_SEEKBAR = "hide_video_thumbnails_seekbar"
        const val KEY_ENABLE_TOP_SPEED_GESTURE = "enable_top_speed_gesture"
        const val KEY_SHOW_HEATMAP_GRAPH = "show_heatmap_graph"
        const val KEY_SHOW_CHAPTER_MARKERS = "show_chapter_markers"
        const val KEY_SEEK_HAPTICS_ENABLED = "seek_haptics_enabled"
        const val KEY_DOUBLE_TAP_SEEK_INTERVAL = "double_tap_seek_interval_secs"

        const val DEFAULT_SEEKBAR_COLOR = "#FFD400"
        const val DEFAULT_SEEKBAR_ACCENT_COLOR = "#FF4081"

        @Volatile
        private var INSTANCE: SeekbarPreferences? = null

        fun getInstance(context: Context): SeekbarPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SeekbarPreferences(context).also { INSTANCE = it }
            }
        }

        fun parseHexColor(hex: String, defaultColor: Color): Color {
            return try {
                val clean = hex.trim().removePrefix("#")
                val colorInt = when (clean.length) {
                    6 -> android.graphics.Color.parseColor("#FF$clean")
                    8 -> android.graphics.Color.parseColor("#$clean")
                    3 -> {
                        val r = clean[0]; val g = clean[1]; val b = clean[2]
                        android.graphics.Color.parseColor("#FF$r$r$g$g$b$b")
                    }
                    else -> defaultColor.toArgb()
                }
                Color(colorInt)
            } catch (_: Exception) {
                defaultColor
            }
        }
    }

    private val _disablePreciseSeekingGesture = MutableStateFlow(prefs.getBoolean(KEY_DISABLE_PRECISE_SEEKING, false))
    val disablePreciseSeekingGesture: StateFlow<Boolean> = _disablePreciseSeekingGesture.asStateFlow()

    private val _enableCustomSeekbarColor = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_CUSTOM_COLOR, false))
    val enableCustomSeekbarColor: StateFlow<Boolean> = _enableCustomSeekbarColor.asStateFlow()

    private val _seekbarColorHex = MutableStateFlow(prefs.getString(KEY_SEEKBAR_COLOR, DEFAULT_SEEKBAR_COLOR) ?: DEFAULT_SEEKBAR_COLOR)
    val seekbarColorHex: StateFlow<String> = _seekbarColorHex.asStateFlow()

    private val _seekbarAccentColorHex = MutableStateFlow(prefs.getString(KEY_SEEKBAR_ACCENT_COLOR, DEFAULT_SEEKBAR_ACCENT_COLOR) ?: DEFAULT_SEEKBAR_ACCENT_COLOR)
    val seekbarAccentColorHex: StateFlow<String> = _seekbarAccentColorHex.asStateFlow()

    private val _enableFullscreenLargeSeekbar = MutableStateFlow(prefs.getBoolean(KEY_FULLSCREEN_LARGE_SEEKBAR, false))
    val enableFullscreenLargeSeekbar: StateFlow<Boolean> = _enableFullscreenLargeSeekbar.asStateFlow()

    private val _enableLivestreamDvr = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_LIVESTREAM_DVR, false))
    val enableLivestreamDvr: StateFlow<Boolean> = _enableLivestreamDvr.asStateFlow()

    private val _expandLivestreamDvrDuration = MutableStateFlow(prefs.getBoolean(KEY_EXPAND_DVR_DURATION, false))
    val expandLivestreamDvrDuration: StateFlow<Boolean> = _expandLivestreamDvrDuration.asStateFlow()

    private val _enableSlideToSeek = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_SLIDE_TO_SEEK, true))
    val enableSlideToSeek: StateFlow<Boolean> = _enableSlideToSeek.asStateFlow()

    private val _enableTapToSeek = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_TAP_TO_SEEK, true))
    val enableTapToSeek: StateFlow<Boolean> = _enableTapToSeek.asStateFlow()

    private val _hideVideoPlayerSeekbar = MutableStateFlow(prefs.getBoolean(KEY_HIDE_PLAYER_SEEKBAR, false))
    val hideVideoPlayerSeekbar: StateFlow<Boolean> = _hideVideoPlayerSeekbar.asStateFlow()

    private val _hideVideoThumbnailsSeekbar = MutableStateFlow(prefs.getBoolean(KEY_HIDE_THUMBNAILS_SEEKBAR, false))
    val hideVideoThumbnailsSeekbar: StateFlow<Boolean> = _hideVideoThumbnailsSeekbar.asStateFlow()

    private val _enableTopSpeedGesture = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_TOP_SPEED_GESTURE, true))
    val enableTopSpeedGesture: StateFlow<Boolean> = _enableTopSpeedGesture.asStateFlow()

    private val _enableGradientProgress = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_SEEKBAR_GRADIENT, true))
    val enableGradientProgress: StateFlow<Boolean> = _enableGradientProgress.asStateFlow()

    private val _showHeatmapGraph = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HEATMAP_GRAPH, true))
    val showHeatmapGraph: StateFlow<Boolean> = _showHeatmapGraph.asStateFlow()

    private val _showChapterMarkers = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CHAPTER_MARKERS, true))
    val showChapterMarkers: StateFlow<Boolean> = _showChapterMarkers.asStateFlow()

    private val _seekHapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_SEEK_HAPTICS_ENABLED, true))
    val seekHapticsEnabled: StateFlow<Boolean> = _seekHapticsEnabled.asStateFlow()

    private val _doubleTapSeekIntervalSecs = MutableStateFlow(prefs.getInt(KEY_DOUBLE_TAP_SEEK_INTERVAL, 10))
    val doubleTapSeekIntervalSecs: StateFlow<Int> = _doubleTapSeekIntervalSecs.asStateFlow()

    fun setDisablePreciseSeekingGesture(disabled: Boolean) {
        _disablePreciseSeekingGesture.value = disabled
        prefs.edit().putBoolean(KEY_DISABLE_PRECISE_SEEKING, disabled).apply()
    }

    fun setEnableCustomSeekbarColor(enabled: Boolean) {
        _enableCustomSeekbarColor.value = enabled
        prefs.edit().putBoolean(KEY_ENABLE_CUSTOM_COLOR, enabled).apply()
    }

    fun setSeekbarColorHex(hex: String) {
        val clean = if (hex.startsWith("#")) hex.uppercase() else "#${hex.uppercase()}"
        _seekbarColorHex.value = clean
        prefs.edit().putString(KEY_SEEKBAR_COLOR, clean).apply()
    }

    fun setSeekbarAccentColorHex(hex: String) {
        val clean = if (hex.startsWith("#")) hex.uppercase() else "#${hex.uppercase()}"
        _seekbarAccentColorHex.value = clean
        prefs.edit().putString(KEY_SEEKBAR_ACCENT_COLOR, clean).apply()
    }

    fun setEnableGradientProgress(enabled: Boolean) {
        _enableGradientProgress.value = enabled
        prefs.edit().putBoolean(KEY_ENABLE_SEEKBAR_GRADIENT, enabled).apply()
    }

    fun setShowHeatmapGraph(show: Boolean) {
        _showHeatmapGraph.value = show
        prefs.edit().putBoolean(KEY_SHOW_HEATMAP_GRAPH, show).apply()
    }

    fun setShowChapterMarkers(show: Boolean) {
        _showChapterMarkers.value = show
        prefs.edit().putBoolean(KEY_SHOW_CHAPTER_MARKERS, show).apply()
    }

    fun setSeekHapticsEnabled(enabled: Boolean) {
        _seekHapticsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SEEK_HAPTICS_ENABLED, enabled).apply()
    }

    fun setDoubleTapSeekIntervalSecs(seconds: Int) {
        val valid = seconds.coerceIn(5, 60)
        _doubleTapSeekIntervalSecs.value = valid
        prefs.edit().putInt(KEY_DOUBLE_TAP_SEEK_INTERVAL, valid).apply()
    }

    fun setEnableFullscreenLargeSeekbar(enabled: Boolean) {
        _enableFullscreenLargeSeekbar.value = enabled
        prefs.edit().putBoolean(KEY_FULLSCREEN_LARGE_SEEKBAR, enabled).apply()
    }

    fun setEnableLivestreamDvr(enabled: Boolean) {
        _enableLivestreamDvr.value = enabled
        prefs.edit().putBoolean(KEY_ENABLE_LIVESTREAM_DVR, enabled).apply()
    }

    fun setExpandLivestreamDvrDuration(enabled: Boolean) {
        _expandLivestreamDvrDuration.value = enabled
        prefs.edit().putBoolean(KEY_EXPAND_DVR_DURATION, enabled).apply()
    }

    fun setEnableSlideToSeek(enabled: Boolean) {
        _enableSlideToSeek.value = enabled
        prefs.edit().putBoolean(KEY_ENABLE_SLIDE_TO_SEEK, enabled).apply()
    }

    fun setEnableTapToSeek(enabled: Boolean) {
        _enableTapToSeek.value = enabled
        prefs.edit().putBoolean(KEY_ENABLE_TAP_TO_SEEK, enabled).apply()
    }

    fun setHideVideoPlayerSeekbar(hidden: Boolean) {
        _hideVideoPlayerSeekbar.value = hidden
        prefs.edit().putBoolean(KEY_HIDE_PLAYER_SEEKBAR, hidden).apply()
    }

    fun setHideVideoThumbnailsSeekbar(hidden: Boolean) {
        _hideVideoThumbnailsSeekbar.value = hidden
        prefs.edit().putBoolean(KEY_HIDE_THUMBNAILS_SEEKBAR, hidden).apply()
    }

    fun setEnableTopSpeedGesture(enabled: Boolean) {
        _enableTopSpeedGesture.value = enabled
        prefs.edit().putBoolean(KEY_ENABLE_TOP_SPEED_GESTURE, enabled).apply()
    }

    data class ThumbnailSeekbarConfig(
        val hideThumbnailsSeekbar: Boolean = false,
        val enableCustomColor: Boolean = false,
        val progressColor: Color = Color.Red,
        val accentColor: Color = Color(0xFFFF4081),
        val enableGradientProgress: Boolean = false
    )

    fun getThumbnailSeekbarConfig(): ThumbnailSeekbarConfig {
        val hide = _hideVideoThumbnailsSeekbar.value
        val custom = _enableCustomSeekbarColor.value
        val progColor = if (custom) parseHexColor(_seekbarColorHex.value, Color.Red) else Color.Red
        val accColor = if (custom) parseHexColor(_seekbarAccentColorHex.value, Color(0xFFFF4081)) else Color(0xFFFF4081)
        val grad = _enableGradientProgress.value
        return ThumbnailSeekbarConfig(
            hideThumbnailsSeekbar = hide,
            enableCustomColor = custom,
            progressColor = progColor,
            accentColor = accColor,
            enableGradientProgress = grad
        )
    }

    fun getEffectiveActiveColor(defaultActive: Color = Color(0xFFFFD600)): Color {
        return if (_enableCustomSeekbarColor.value) {
            parseHexColor(_seekbarColorHex.value, defaultActive)
        } else {
            defaultActive
        }
    }

    fun getEffectiveAccentColor(defaultAccent: Color = Color(0xFFFF4081)): Color {
        return if (_enableCustomSeekbarColor.value) {
            parseHexColor(_seekbarAccentColorHex.value, defaultAccent)
        } else {
            defaultAccent
        }
    }
}

