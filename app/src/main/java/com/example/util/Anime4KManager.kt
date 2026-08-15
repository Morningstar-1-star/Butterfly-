package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Anime4KPowerMode(
    val id: String,
    val title: String,
    val description: String,
    val badgeLabel: String,
    val isEnabled: Boolean
) {
    OFF("off", "Off (Native Video)", "Standard native video playback without upscaling", "", false),
    FAST_LOW("fast_low", "Anime4K Fast (Low Power)", "Fast edge refinement & line sharpening. Battery friendly.", "Anime4K Fast", true),
    BALANCED_MEDIUM("balanced_medium", "Anime4K Mode A (Balanced)", "Reconstructs line art and denoises 720p/1080p anime.", "Anime4K A", true),
    HIGH_PRECISION("high_precision", "Anime4K Mode B (High Precision)", "Precise detail restoration and anti-aliasing.", "Anime4K B", true),
    ULTRA_4K_MAX("ultra_4k_max", "Anime4K Mode C (Ultra HQ 4K)", "Maximum processing power for crisp 4K super-resolution.", "Anime4K 4K", true)
}

class Anime4KManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentMode = MutableStateFlow(
        getModeFromId(prefs.getString(KEY_MODE, Anime4KPowerMode.OFF.id) ?: Anime4KPowerMode.OFF.id)
    )
    val currentMode: StateFlow<Anime4KPowerMode> = _currentMode.asStateFlow()

    private val _preferredAudioLang = MutableStateFlow(
        prefs.getString(KEY_PREFERRED_AUDIO_LANG, "auto") ?: "auto"
    )
    val preferredAudioLang: StateFlow<String> = _preferredAudioLang.asStateFlow()

    fun setMode(mode: Anime4KPowerMode) {
        _currentMode.value = mode
        prefs.edit().putString(KEY_MODE, mode.id).apply()
    }

    fun setPreferredAudioLang(langCode: String) {
        _preferredAudioLang.value = langCode
        prefs.edit().putString(KEY_PREFERRED_AUDIO_LANG, langCode).apply()
    }

    companion object {
        private const val PREFS_NAME = "anime4k_preferences"
        private const val KEY_MODE = "anime4k_power_mode"
        private const val KEY_PREFERRED_AUDIO_LANG = "preferred_audio_language"

        @Volatile
        private var INSTANCE: Anime4KManager? = null

        fun getInstance(context: Context): Anime4KManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Anime4KManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getModeFromId(id: String): Anime4KPowerMode {
            return Anime4KPowerMode.values().find { it.id == id } ?: Anime4KPowerMode.OFF
        }
    }
}
