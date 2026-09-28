package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackPreferences private constructor(context: Context) {

    val prefsContext: Context = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Speed & Audio
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

    // Quality, Codec & Decoder Settings (From User Prompt & Screenshot 2)
    private val _prioritizeVideoQuality = MutableStateFlow(
        prefs.getBoolean(KEY_PRIORITIZE_VIDEO_QUALITY, true)
    )
    val prioritizeVideoQuality: StateFlow<Boolean> = _prioritizeVideoQuality.asStateFlow()

    private val _disableDrcAudio = MutableStateFlow(
        prefs.getBoolean(KEY_DISABLE_DRC_AUDIO, false)
    )
    val disableDrcAudio: StateFlow<Boolean> = _disableDrcAudio.asStateFlow()

    private val _disableHdrVideo = MutableStateFlow(
        prefs.getBoolean(KEY_DISABLE_HDR_VIDEO, false)
    )
    val disableHdrVideo: StateFlow<Boolean> = _disableHdrVideo.asStateFlow()

    private val _forceAvcCodec = MutableStateFlow(
        prefs.getBoolean(KEY_FORCE_AVC_CODEC, false)
    )
    val forceAvcCodec: StateFlow<Boolean> = _forceAvcCodec.asStateFlow()

    private val _videoCodecPreference = MutableStateFlow(
        prefs.getString(KEY_VIDEO_CODEC_PREFERENCE, "AUTO") ?: "AUTO"
    )
    val videoCodecPreference: StateFlow<String> = _videoCodecPreference.asStateFlow()

    private val _decoderMode = MutableStateFlow(
        prefs.getString(KEY_DECODER_MODE, "HARDWARE") ?: "HARDWARE"
    )
    val decoderMode: StateFlow<String> = _decoderMode.asStateFlow()

    private val _forceOriginalAudioLanguage = MutableStateFlow(
        prefs.getBoolean(KEY_FORCE_ORIGINAL_AUDIO_LANGUAGE, false)
    )
    val forceOriginalAudioLanguage: StateFlow<Boolean> = _forceOriginalAudioLanguage.asStateFlow()

    private val _customPlaybackSpeedMenu = MutableStateFlow(
        prefs.getBoolean(KEY_CUSTOM_PLAYBACK_SPEED_MENU, true)
    )
    val customPlaybackSpeedMenu: StateFlow<Boolean> = _customPlaybackSpeedMenu.asStateFlow()

    private val _restoreOldPlaybackSpeedMenu = MutableStateFlow(
        prefs.getBoolean(KEY_RESTORE_OLD_PLAYBACK_SPEED_MENU, false)
    )
    val restoreOldPlaybackSpeedMenu: StateFlow<Boolean> = _restoreOldPlaybackSpeedMenu.asStateFlow()

    private val _customPlaybackSpeeds = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_PLAYBACK_SPEEDS, "0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0") ?: "0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0"
    )
    val customPlaybackSpeeds: StateFlow<String> = _customPlaybackSpeeds.asStateFlow()

    private val _tapAndHoldSpeed = MutableStateFlow(
        prefs.getFloat(KEY_TAP_AND_HOLD_SPEED, 2.0f)
    )
    val tapAndHoldSpeed: StateFlow<Float> = _tapAndHoldSpeed.asStateFlow()

    private val _rememberPlaybackSpeed = MutableStateFlow(
        prefs.getBoolean(KEY_REMEMBER_PLAYBACK_SPEED, true)
    )
    val rememberPlaybackSpeed: StateFlow<Boolean> = _rememberPlaybackSpeed.asStateFlow()

    private val _speedChangeNotifications = MutableStateFlow(
        prefs.getBoolean(KEY_SPEED_CHANGE_NOTIFICATIONS, true)
    )
    val speedChangeNotifications: StateFlow<Boolean> = _speedChangeNotifications.asStateFlow()

    private val _defaultResolution = MutableStateFlow(
        prefs.getString(KEY_DEFAULT_RESOLUTION, "1080p") ?: "1080p"
    )
    val defaultResolution: StateFlow<String> = _defaultResolution.asStateFlow()

    private val _doubleTapSeekSeconds = MutableStateFlow(
        prefs.getInt(KEY_DOUBLE_TAP_SEEK_SECONDS, 10)
    )
    val doubleTapSeekSeconds: StateFlow<Int> = _doubleTapSeekSeconds.asStateFlow()

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

    fun setPrioritizeVideoQuality(enabled: Boolean) {
        _prioritizeVideoQuality.value = enabled
        prefs.edit().putBoolean(KEY_PRIORITIZE_VIDEO_QUALITY, enabled).apply()
    }

    fun setDisableDrcAudio(disabled: Boolean) {
        _disableDrcAudio.value = disabled
        prefs.edit().putBoolean(KEY_DISABLE_DRC_AUDIO, disabled).apply()
    }

    fun setDisableHdrVideo(disabled: Boolean) {
        _disableHdrVideo.value = disabled
        prefs.edit().putBoolean(KEY_DISABLE_HDR_VIDEO, disabled).apply()
    }

    fun setForceAvcCodec(force: Boolean) {
        _forceAvcCodec.value = force
        prefs.edit().putBoolean(KEY_FORCE_AVC_CODEC, force).apply()
    }

    fun setVideoCodecPreference(codec: String) {
        _videoCodecPreference.value = codec
        prefs.edit().putString(KEY_VIDEO_CODEC_PREFERENCE, codec).apply()
        if (codec == "AVC") {
            _forceAvcCodec.value = true
            prefs.edit().putBoolean(KEY_FORCE_AVC_CODEC, true).apply()
        } else if (codec != "AUTO") {
            _forceAvcCodec.value = false
            prefs.edit().putBoolean(KEY_FORCE_AVC_CODEC, false).apply()
        }
    }

    fun setDecoderMode(mode: String) {
        _decoderMode.value = mode
        prefs.edit().putString(KEY_DECODER_MODE, mode).apply()
    }

    fun setForceOriginalAudioLanguage(force: Boolean) {
        _forceOriginalAudioLanguage.value = force
        prefs.edit().putBoolean(KEY_FORCE_ORIGINAL_AUDIO_LANGUAGE, force).apply()
    }

    fun setCustomPlaybackSpeedMenu(enabled: Boolean) {
        _customPlaybackSpeedMenu.value = enabled
        prefs.edit().putBoolean(KEY_CUSTOM_PLAYBACK_SPEED_MENU, enabled).apply()
    }

    fun setRestoreOldPlaybackSpeedMenu(enabled: Boolean) {
        _restoreOldPlaybackSpeedMenu.value = enabled
        prefs.edit().putBoolean(KEY_RESTORE_OLD_PLAYBACK_SPEED_MENU, enabled).apply()
    }

    fun setCustomPlaybackSpeeds(speeds: String) {
        _customPlaybackSpeeds.value = speeds
        prefs.edit().putString(KEY_CUSTOM_PLAYBACK_SPEEDS, speeds).apply()
    }

    fun setTapAndHoldSpeed(speed: Float) {
        _tapAndHoldSpeed.value = speed
        prefs.edit().putFloat(KEY_TAP_AND_HOLD_SPEED, speed).apply()
    }

    fun setRememberPlaybackSpeed(remember: Boolean) {
        _rememberPlaybackSpeed.value = remember
        prefs.edit().putBoolean(KEY_REMEMBER_PLAYBACK_SPEED, remember).apply()
    }

    fun setSpeedChangeNotifications(enabled: Boolean) {
        _speedChangeNotifications.value = enabled
        prefs.edit().putBoolean(KEY_SPEED_CHANGE_NOTIFICATIONS, enabled).apply()
    }

    fun setDefaultResolution(resolution: String) {
        _defaultResolution.value = resolution
        prefs.edit().putString(KEY_DEFAULT_RESOLUTION, resolution).apply()
    }

    fun setDoubleTapSeekSeconds(seconds: Int) {
        _doubleTapSeekSeconds.value = seconds
        prefs.edit().putInt(KEY_DOUBLE_TAP_SEEK_SECONDS, seconds).apply()
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

        private const val KEY_PRIORITIZE_VIDEO_QUALITY = "prioritize_video_quality"
        private const val KEY_DISABLE_DRC_AUDIO = "disable_drc_audio"
        private const val KEY_DISABLE_HDR_VIDEO = "disable_hdr_video"
        private const val KEY_FORCE_AVC_CODEC = "force_avc_codec"
        private const val KEY_VIDEO_CODEC_PREFERENCE = "video_codec_preference"
        private const val KEY_DECODER_MODE = "decoder_mode"
        private const val KEY_FORCE_ORIGINAL_AUDIO_LANGUAGE = "force_original_audio_language"
        private const val KEY_CUSTOM_PLAYBACK_SPEED_MENU = "custom_playback_speed_menu"
        private const val KEY_RESTORE_OLD_PLAYBACK_SPEED_MENU = "restore_old_playback_speed_menu"
        private const val KEY_CUSTOM_PLAYBACK_SPEEDS = "custom_playback_speeds"
        private const val KEY_TAP_AND_HOLD_SPEED = "tap_and_hold_speed"
        private const val KEY_REMEMBER_PLAYBACK_SPEED = "remember_playback_speed"
        private const val KEY_SPEED_CHANGE_NOTIFICATIONS = "speed_change_notifications"
        private const val KEY_DEFAULT_RESOLUTION = "default_resolution"
        private const val KEY_DOUBLE_TAP_SEEK_SECONDS = "double_tap_seek_seconds"

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
