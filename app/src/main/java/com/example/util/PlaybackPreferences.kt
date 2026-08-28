package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackPreferences private constructor(context: Context) {

    val prefsContext: Context = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _forceCustomSpeed = MutableStateFlow(
        prefs.getBoolean(KEY_FORCE_CUSTOM_SPEED, true)
    )
    val forceCustomSpeed: StateFlow<Boolean> = _forceCustomSpeed.asStateFlow()

    private val _defaultSpeed = MutableStateFlow(
        prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f)
    )
    val defaultSpeed: StateFlow<Float> = _defaultSpeed.asStateFlow()

    private val _disableSpeedForMusic = MutableStateFlow(
        prefs.getBoolean(KEY_DISABLE_SPEED_FOR_MUSIC, true)
    )
    val disableSpeedForMusic: StateFlow<Boolean> = _disableSpeedForMusic.asStateFlow()

    private val _ambientModeEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AMBIENT_MODE_ENABLED, true)
    )
    val ambientModeEnabled: StateFlow<Boolean> = _ambientModeEnabled.asStateFlow()

    private val _loopVideoEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LOOP_VIDEO_ENABLED, false)
    )
    val loopVideoEnabled: StateFlow<Boolean> = _loopVideoEnabled.asStateFlow()

    fun setForceCustomSpeed(enabled: Boolean) {
        _forceCustomSpeed.value = enabled
        prefs.edit().putBoolean(KEY_FORCE_CUSTOM_SPEED, enabled).apply()
    }

    fun setDefaultSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.1f, 16.0f)
        _defaultSpeed.value = clamped
        prefs.edit().putFloat(KEY_DEFAULT_SPEED, clamped).apply()
    }

    fun setDisableSpeedForMusic(disabled: Boolean) {
        _disableSpeedForMusic.value = disabled
        prefs.edit().putBoolean(KEY_DISABLE_SPEED_FOR_MUSIC, disabled).apply()
    }

    fun setAmbientModeEnabled(enabled: Boolean) {
        _ambientModeEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AMBIENT_MODE_ENABLED, enabled).apply()
    }

    fun setLoopVideoEnabled(enabled: Boolean) {
        _loopVideoEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LOOP_VIDEO_ENABLED, enabled).apply()
    }

    fun getEffectiveSpeed(isMusic: Boolean): Float {
        if (isMusic && _disableSpeedForMusic.value) {
            return 1.0f
        }
        if (_forceCustomSpeed.value) {
            return _defaultSpeed.value
        }
        return 1.0f
    }

    companion object {
        private const val PREFS_NAME = "butterfly_playback_prefs"
        private const val KEY_FORCE_CUSTOM_SPEED = "force_custom_speed"
        private const val KEY_DEFAULT_SPEED = "default_speed"
        private const val KEY_DISABLE_SPEED_FOR_MUSIC = "disable_speed_for_music"
        private const val KEY_AMBIENT_MODE_ENABLED = "ambient_mode_enabled"
        private const val KEY_LOOP_VIDEO_ENABLED = "loop_video_enabled"

        @Volatile
        private var INSTANCE: PlaybackPreferences? = null

        var torrentioBaseUrl: String
            get() = AppConfig.getTorrentioBaseUrl()
            set(value) {
                INSTANCE?.let { AppConfig.setTorrentioBaseUrl(it.prefsContext, value) }
            }

        var torznabBaseUrl: String
            get() = AppConfig.getTorznabBaseUrl()
            set(value) {
                INSTANCE?.let { AppConfig.setTorznabBaseUrl(it.prefsContext, value) }
            }

        var torznabApiKey: String
            get() = AppConfig.getTorznabApiKey()
            set(value) {
                INSTANCE?.let { AppConfig.setTorznabApiKey(it.prefsContext, value) }
            }

        fun getInstance(context: Context): PlaybackPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaybackPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun isMusicMedia(
            title: String?,
            uploaderName: String?,
            description: String? = null,
            tags: List<String>? = null,
            providerId: String? = null
        ): Boolean {
            val uploader = uploaderName?.lowercase() ?: ""
            val tName = title?.lowercase() ?: ""
            val desc = description?.lowercase() ?: ""
            val provider = providerId?.lowercase() ?: ""

            if (provider.contains("music") || provider.contains("ytmusic")) {
                return true
            }

            if (uploader.endsWith(" - topic") || uploader.endsWith("vevo") || uploader.contains("official music") || uploader.contains("records")) {
                return true
            }

            val musicKeywords = listOf(
                "official music video", "official audio", "lyric video", "lyrics video",
                "full song", "audio song", "music video", "official lyric", "official video",
                "soundtrack", "ost", "album", "vevo", "singles", "ep", "topic",
                "remix", "prod.", "feat.", "ft.", "cover song", "audio track"
            )

            val combined = "$tName $desc ${tags?.joinToString(" ")?.lowercase().orEmpty()}"

            for (kw in musicKeywords) {
                if (combined.contains(kw)) return true
            }

            tags?.forEach { tag ->
                val lowerTag = tag.lowercase()
                if (lowerTag == "music" || lowerTag == "song" || lowerTag == "songs" || lowerTag == "audio" ||
                    lowerTag == "soundtrack" || lowerTag == "ost" || lowerTag == "hip hop" || lowerTag == "pop" ||
                    lowerTag == "rock" || lowerTag == "rap" || lowerTag == "edm" || lowerTag == "kpop") {
                    return true
                }
            }

            return false
        }
    }
}
