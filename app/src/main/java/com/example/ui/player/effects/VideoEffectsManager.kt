package com.example.ui.player.effects

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VideoEffectsConfig(
    val brightness: Float = 0.0f,     // -0.4f (darker) to +0.4f (brighter)
    val contrast: Float = 1.0f,       // 0.6f to 1.6f
    val saturation: Float = 1.0f,     // 0.0f (grayscale) to 2.0f (ultra vivid)
    val amoledBlackLevel: Float = 0.0f, // 0.0f to 0.5f (dimming blacks)
    val nightShieldWarmth: Float = 0.0f, // 0.0f to 0.6f (warm orange blue-light filter)
    val activePreset: VideoPreset = VideoPreset.STANDARD
)

enum class VideoPreset(val displayName: String) {
    STANDARD("Standard (Original)"),
    CINEMA_HDR("Cinema HDR Vivid"),
    AMOLED_BLACK("AMOLED Deep Black"),
    NIGHT_SHIELD("Night Warm Shield"),
    ANIME_BOOST("Anime & Color Boost")
}

class VideoEffectsManager private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("butterfly_video_effects", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<VideoEffectsConfig> = _config.asStateFlow()

    private fun loadConfig(): VideoEffectsConfig {
        val brightness = prefs.getFloat(KEY_BRIGHTNESS, 0.0f).takeIf { !it.isNaN() }?.coerceIn(-0.4f, 0.4f) ?: 0.0f
        val contrast = prefs.getFloat(KEY_CONTRAST, 1.0f).takeIf { !it.isNaN() }?.coerceIn(0.6f, 1.6f) ?: 1.0f
        val saturation = prefs.getFloat(KEY_SATURATION, 1.0f).takeIf { !it.isNaN() }?.coerceIn(0.0f, 2.0f) ?: 1.0f
        val amoled = prefs.getFloat(KEY_AMOLED, 0.0f).takeIf { !it.isNaN() }?.coerceIn(0.0f, 0.5f) ?: 0.0f
        val nightShield = prefs.getFloat(KEY_NIGHT_SHIELD, 0.0f).takeIf { !it.isNaN() }?.coerceIn(0.0f, 0.6f) ?: 0.0f
        val presetName = prefs.getString(KEY_PRESET, VideoPreset.STANDARD.name) ?: VideoPreset.STANDARD.name
        val preset = runCatching { VideoPreset.valueOf(presetName) }.getOrDefault(VideoPreset.STANDARD)

        return VideoEffectsConfig(
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            amoledBlackLevel = amoled,
            nightShieldWarmth = nightShield,
            activePreset = preset
        )
    }

    fun setBrightness(value: Float) {
        val clamped = value.coerceIn(-0.4f, 0.4f)
        _config.value = _config.value.copy(brightness = clamped, activePreset = VideoPreset.STANDARD)
        prefs.edit().putFloat(KEY_BRIGHTNESS, clamped).putString(KEY_PRESET, VideoPreset.STANDARD.name).apply()
    }

    fun setContrast(value: Float) {
        val clamped = value.coerceIn(0.6f, 1.6f)
        _config.value = _config.value.copy(contrast = clamped, activePreset = VideoPreset.STANDARD)
        prefs.edit().putFloat(KEY_CONTRAST, clamped).putString(KEY_PRESET, VideoPreset.STANDARD.name).apply()
    }

    fun setSaturation(value: Float) {
        val clamped = value.coerceIn(0.0f, 2.0f)
        _config.value = _config.value.copy(saturation = clamped, activePreset = VideoPreset.STANDARD)
        prefs.edit().putFloat(KEY_SATURATION, clamped).putString(KEY_PRESET, VideoPreset.STANDARD.name).apply()
    }

    fun setAmoledBlackLevel(value: Float) {
        val clamped = value.coerceIn(0.0f, 0.5f)
        _config.value = _config.value.copy(amoledBlackLevel = clamped, activePreset = VideoPreset.STANDARD)
        prefs.edit().putFloat(KEY_AMOLED, clamped).putString(KEY_PRESET, VideoPreset.STANDARD.name).apply()
    }

    fun setNightShieldWarmth(value: Float) {
        val clamped = value.coerceIn(0.0f, 0.6f)
        _config.value = _config.value.copy(nightShieldWarmth = clamped, activePreset = VideoPreset.STANDARD)
        prefs.edit().putFloat(KEY_NIGHT_SHIELD, clamped).putString(KEY_PRESET, VideoPreset.STANDARD.name).apply()
    }

    fun applyPreset(preset: VideoPreset) {
        val newConfig = when (preset) {
            VideoPreset.STANDARD -> VideoEffectsConfig(
                brightness = 0.0f,
                contrast = 1.0f,
                saturation = 1.0f,
                amoledBlackLevel = 0.0f,
                nightShieldWarmth = 0.0f,
                activePreset = VideoPreset.STANDARD
            )
            VideoPreset.CINEMA_HDR -> VideoEffectsConfig(
                brightness = 0.05f,
                contrast = 1.25f,
                saturation = 1.25f,
                amoledBlackLevel = 0.08f,
                nightShieldWarmth = 0.0f,
                activePreset = VideoPreset.CINEMA_HDR
            )
            VideoPreset.AMOLED_BLACK -> VideoEffectsConfig(
                brightness = -0.05f,
                contrast = 1.30f,
                saturation = 1.10f,
                amoledBlackLevel = 0.25f,
                nightShieldWarmth = 0.0f,
                activePreset = VideoPreset.AMOLED_BLACK
            )
            VideoPreset.NIGHT_SHIELD -> VideoEffectsConfig(
                brightness = -0.08f,
                contrast = 0.95f,
                saturation = 0.90f,
                amoledBlackLevel = 0.10f,
                nightShieldWarmth = 0.35f,
                activePreset = VideoPreset.NIGHT_SHIELD
            )
            VideoPreset.ANIME_BOOST -> VideoEffectsConfig(
                brightness = 0.04f,
                contrast = 1.20f,
                saturation = 1.45f,
                amoledBlackLevel = 0.05f,
                nightShieldWarmth = 0.0f,
                activePreset = VideoPreset.ANIME_BOOST
            )
        }
        _config.value = newConfig
        prefs.edit()
            .putFloat(KEY_BRIGHTNESS, newConfig.brightness)
            .putFloat(KEY_CONTRAST, newConfig.contrast)
            .putFloat(KEY_SATURATION, newConfig.saturation)
            .putFloat(KEY_AMOLED, newConfig.amoledBlackLevel)
            .putFloat(KEY_NIGHT_SHIELD, newConfig.nightShieldWarmth)
            .putString(KEY_PRESET, preset.name)
            .apply()
    }

    fun resetToDefaults() {
        applyPreset(VideoPreset.STANDARD)
    }

    companion object {
        private const val KEY_BRIGHTNESS = "key_brightness"
        private const val KEY_CONTRAST = "key_contrast"
        private const val KEY_SATURATION = "key_saturation"
        private const val KEY_AMOLED = "key_amoled"
        private const val KEY_NIGHT_SHIELD = "key_night_shield"
        private const val KEY_PRESET = "key_preset"

        @Volatile
        private var INSTANCE: VideoEffectsManager? = null

        fun getInstance(context: Context): VideoEffectsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VideoEffectsManager(context).also { INSTANCE = it }
            }
        }
    }
}
