package com.example.smartskip

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SmartSkipPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isSmartSkipEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MASTER_ENABLED, true)
    )
    val isSmartSkipEnabled: StateFlow<Boolean> = _isSmartSkipEnabled.asStateFlow()

    private val _skipNotification = MutableStateFlow(
        prefs.getBoolean(KEY_SKIP_NOTIFICATION, true)
    )
    val skipNotification: StateFlow<Boolean> = _skipNotification.asStateFlow()

    private val _skipConfirmation = MutableStateFlow(
        prefs.getBoolean(KEY_SKIP_CONFIRMATION, false)
    )
    val skipConfirmation: StateFlow<Boolean> = _skipConfirmation.asStateFlow()

    private val _skipAnimation = MutableStateFlow(
        prefs.getBoolean(KEY_SKIP_ANIMATION, true)
    )
    val skipAnimation: StateFlow<Boolean> = _skipAnimation.asStateFlow()

    private val _skipButtonDurationSec = MutableStateFlow(
        prefs.getInt(KEY_BUTTON_DURATION_SEC, 8)
    )
    val skipButtonDurationSec: StateFlow<Int> = _skipButtonDurationSec.asStateFlow()

    // Category behaviors
    private val _categoryBehaviors = MutableStateFlow(loadCategoryBehaviors())
    val categoryBehaviors: StateFlow<Map<SkipCategory, SkipBehavior>> = _categoryBehaviors.asStateFlow()

    // Per-source toggles
    private val _sourceToggles = MutableStateFlow(loadSourceToggles())
    val sourceToggles: StateFlow<Map<SkipSource, Boolean>> = _sourceToggles.asStateFlow()

    private fun loadCategoryBehaviors(): Map<SkipCategory, SkipBehavior> {
        val map = mutableMapOf<SkipCategory, SkipBehavior>()
        for (cat in SkipCategory.values()) {
            val savedName = prefs.getString(KEY_PREFIX_CATEGORY + cat.id, null)
            val behavior = if (savedName != null) {
                try {
                    SkipBehavior.valueOf(savedName)
                } catch (_: Exception) {
                    cat.defaultBehavior
                }
            } else {
                cat.defaultBehavior
            }
            map[cat] = behavior
        }
        return map
    }

    private fun loadSourceToggles(): Map<SkipSource, Boolean> {
        val map = mutableMapOf<SkipSource, Boolean>()
        for (src in SkipSource.values()) {
            map[src] = prefs.getBoolean(KEY_PREFIX_SOURCE + src.id, true)
        }
        return map
    }

    fun setMasterEnabled(enabled: Boolean) {
        _isSmartSkipEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun setSkipNotification(enabled: Boolean) {
        _skipNotification.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_NOTIFICATION, enabled).apply()
    }

    fun setSkipConfirmation(enabled: Boolean) {
        _skipConfirmation.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_CONFIRMATION, enabled).apply()
    }

    fun setSkipAnimation(enabled: Boolean) {
        _skipAnimation.value = enabled
        prefs.edit().putBoolean(KEY_SKIP_ANIMATION, enabled).apply()
    }

    fun setSkipButtonDurationSec(seconds: Int) {
        val clamped = seconds.coerceIn(3, 30)
        _skipButtonDurationSec.value = clamped
        prefs.edit().putInt(KEY_BUTTON_DURATION_SEC, clamped).apply()
    }

    fun setCategoryBehavior(category: SkipCategory, behavior: SkipBehavior) {
        val current = _categoryBehaviors.value.toMutableMap()
        current[category] = behavior
        _categoryBehaviors.value = current
        prefs.edit().putString(KEY_PREFIX_CATEGORY + category.id, behavior.name).apply()
    }

    fun setSourceEnabled(source: SkipSource, enabled: Boolean) {
        val current = _sourceToggles.value.toMutableMap()
        current[source] = enabled
        _sourceToggles.value = current
        prefs.edit().putBoolean(KEY_PREFIX_SOURCE + source.id, enabled).apply()
    }

    fun isSourceEnabled(source: SkipSource): Boolean {
        return _sourceToggles.value[source] ?: true
    }

    fun getBehaviorFor(category: SkipCategory): SkipBehavior {
        if (!_isSmartSkipEnabled.value) return SkipBehavior.DONT_SKIP
        return _categoryBehaviors.value[category] ?: category.defaultBehavior
    }

    fun resetToDefaults() {
        val editor = prefs.edit()
        _isSmartSkipEnabled.value = true
        _skipNotification.value = true
        _skipConfirmation.value = false
        _skipAnimation.value = true
        _skipButtonDurationSec.value = 8
        editor.putBoolean(KEY_MASTER_ENABLED, true)
        editor.putBoolean(KEY_SKIP_NOTIFICATION, true)
        editor.putBoolean(KEY_SKIP_CONFIRMATION, false)
        editor.putBoolean(KEY_SKIP_ANIMATION, true)
        editor.putInt(KEY_BUTTON_DURATION_SEC, 8)

        val defaultCats = mutableMapOf<SkipCategory, SkipBehavior>()
        for (cat in SkipCategory.values()) {
            defaultCats[cat] = cat.defaultBehavior
            editor.putString(KEY_PREFIX_CATEGORY + cat.id, cat.defaultBehavior.name)
        }
        _categoryBehaviors.value = defaultCats

        val defaultSources = mutableMapOf<SkipSource, Boolean>()
        for (src in SkipSource.values()) {
            defaultSources[src] = true
            editor.putBoolean(KEY_PREFIX_SOURCE + src.id, true)
        }
        _sourceToggles.value = defaultSources

        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "butterfly_sponsorblock_prefs"
        private const val KEY_MASTER_ENABLED = "sb_master_enabled"
        private const val KEY_SKIP_NOTIFICATION = "sb_skip_notification"
        private const val KEY_SKIP_CONFIRMATION = "sb_skip_confirmation"
        private const val KEY_SKIP_ANIMATION = "sb_skip_animation"
        private const val KEY_BUTTON_DURATION_SEC = "sb_button_duration_sec"
        private const val KEY_PREFIX_CATEGORY = "sb_cat_"
        private const val KEY_PREFIX_SOURCE = "sb_src_"

        @Volatile
        private var INSTANCE: SmartSkipPreferences? = null

        fun getInstance(context: Context): SmartSkipPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartSkipPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
