package com.example.effects

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton state manager for real-time video effects, presets, and per-video session memory.
 */
object VideoEffectsManager {

    private val _currentConfig = MutableStateFlow(VideoEffectsConfig())
    val currentConfig: StateFlow<VideoEffectsConfig> = _currentConfig.asStateFlow()

    // Per-video memory cache
    private val perVideoEffectsCache = ConcurrentHashMap<String, VideoEffectsConfig>()
    private var currentActiveVideoId: String? = null

    /**
     * Called when a new video is loaded to restore video-specific effect settings.
     */
    fun onVideoChanged(videoId: String?) {
        currentActiveVideoId = videoId
        if (videoId != null && perVideoEffectsCache.containsKey(videoId)) {
            _currentConfig.value = perVideoEffectsCache[videoId] ?: VideoEffectsConfig()
        } else {
            // Keep enabled status if user had it enabled or default to disabled
            val current = _currentConfig.value
            if (!current.hasActiveEffects()) {
                _currentConfig.value = VideoEffectsConfig()
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val updated = _currentConfig.value.copy(isEnabled = enabled)
        updateAndCache(updated)
    }

    fun toggleEnabled() {
        setEnabled(!_currentConfig.value.isEnabled)
    }

    fun applyPreset(preset: PresetFilter) {
        val newConfig = VideoEffectsEngine.getPresetConfig(preset)
        updateAndCache(newConfig)
    }

    fun updateBasic(modifier: (BasicEffectsState) -> BasicEffectsState) {
        val current = _currentConfig.value
        val updatedBasic = modifier(current.basic)
        val updated = current.copy(
            isEnabled = true,
            selectedPreset = if (current.selectedPreset != PresetFilter.NONE && updatedBasic != current.basic) PresetFilter.NONE else current.selectedPreset,
            basic = updatedBasic
        )
        updateAndCache(updated)
    }

    fun updateColor(modifier: (ColorAdvancedEffectsState) -> ColorAdvancedEffectsState) {
        val current = _currentConfig.value
        val updatedColor = modifier(current.color)
        val updated = current.copy(
            isEnabled = true,
            selectedPreset = if (current.selectedPreset != PresetFilter.NONE && updatedColor != current.color) PresetFilter.NONE else current.selectedPreset,
            color = updatedColor
        )
        updateAndCache(updated)
    }

    fun updateEnhancement(modifier: (EnhancementEffectsState) -> EnhancementEffectsState) {
        val current = _currentConfig.value
        val updatedEnhancement = modifier(current.enhancement)
        val updated = current.copy(
            isEnabled = true,
            enhancement = updatedEnhancement
        )
        updateAndCache(updated)
    }

    fun resetBasic() {
        val current = _currentConfig.value
        val updated = current.copy(
            basic = BasicEffectsState(),
            selectedPreset = PresetFilter.NONE
        )
        updateAndCache(updated)
    }

    fun resetColor() {
        val current = _currentConfig.value
        val updated = current.copy(
            color = ColorAdvancedEffectsState(),
            selectedPreset = PresetFilter.NONE
        )
        updateAndCache(updated)
    }

    fun resetEnhancement() {
        val current = _currentConfig.value
        val updated = current.copy(
            enhancement = EnhancementEffectsState()
        )
        updateAndCache(updated)
    }

    fun resetAll() {
        val resetConfig = VideoEffectsConfig(
            isEnabled = false,
            selectedPreset = PresetFilter.NONE,
            basic = BasicEffectsState(),
            color = ColorAdvancedEffectsState(),
            enhancement = EnhancementEffectsState()
        )
        updateAndCache(resetConfig)
    }

    private fun updateAndCache(config: VideoEffectsConfig) {
        _currentConfig.value = config
        currentActiveVideoId?.let { id ->
            perVideoEffectsCache[id] = config
        }
    }
}
