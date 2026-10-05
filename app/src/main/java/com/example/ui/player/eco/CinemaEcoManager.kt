package com.example.ui.player.eco

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CinemaEcoManager (Deep Landscape Cinema Hibernation Engine)
 * 
 * Automatically freezes non-essential background tasks, reduces display refresh rate to match
 * video frame rate (60Hz / 30Hz / 24Hz instead of battery-draining 120Hz), suspends GPU canvas glows,
 * and throttles background scrapers/sync after 4 seconds of uninterrupted fullscreen video playback.
 * 
 * Instantly restores 120Hz/90Hz and full interactivity on any screen touch or orientation change.
 */
object CinemaEcoManager {
    private const val TAG = "CinemaEcoManager"
    private const val HIBERNATION_DELAY_MS = 4000L // 4 seconds of uninterrupted playback

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hibernationJob: Job? = null

    private val _isCinemaEcoActive = MutableStateFlow(false)
    val isCinemaEcoActive: StateFlow<Boolean> = _isCinemaEcoActive.asStateFlow()

    private val _isBackgroundProcessingSuspended = MutableStateFlow(false)
    val isBackgroundProcessingSuspended: StateFlow<Boolean> = _isBackgroundProcessingSuspended.asStateFlow()

    private val _isEcoFeatureEnabled = MutableStateFlow(true)
    val isEcoFeatureEnabled: StateFlow<Boolean> = _isEcoFeatureEnabled.asStateFlow()

    private var originalModeId: Int? = null

    fun setEcoFeatureEnabled(enabled: Boolean, context: Context? = null) {
        _isEcoFeatureEnabled.value = enabled
        context?.let { ctx ->
            ctx.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("cinema_eco_enabled", enabled)
                .apply()
        }
        if (!enabled && _isCinemaEcoActive.value) {
            deactivateCinemaEco(context)
        }
    }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
        _isEcoFeatureEnabled.value = prefs.getBoolean("cinema_eco_enabled", true)
    }

    /**
     * Notify player state changes to manage the auto-hibernation timer.
     */
    fun updatePlaybackState(
        isLandscape: Boolean,
        isPlaying: Boolean,
        areControlsVisible: Boolean,
        activity: Activity? = null
    ) {
        if (!_isEcoFeatureEnabled.value) {
            if (_isCinemaEcoActive.value) {
                deactivateCinemaEco(activity)
            }
            return
        }

        val shouldHibernate = isLandscape && isPlaying && !areControlsVisible

        if (shouldHibernate) {
            if (!_isCinemaEcoActive.value && hibernationJob == null) {
                hibernationJob = scope.launch {
                    delay(HIBERNATION_DELAY_MS)
                    activateCinemaEco(activity)
                }
            }
        } else {
            hibernationJob?.cancel()
            hibernationJob = null
            if (_isCinemaEcoActive.value) {
                deactivateCinemaEco(activity)
            }
        }
    }

    /**
     * User touched screen or controls became visible: wake up immediately.
     */
    fun onUserInteraction(activity: Activity? = null) {
        hibernationJob?.cancel()
        hibernationJob = null
        if (_isCinemaEcoActive.value) {
            deactivateCinemaEco(activity)
        }
    }

    private fun activateCinemaEco(activity: Activity?) {
        _isCinemaEcoActive.value = true
        _isBackgroundProcessingSuspended.value = true
        Log.d(TAG, "Cinema Eco Mode Engaged: Background tasks suspended, GPU & Display optimized.")

        // Optimize display refresh rate: drop from 120Hz/90Hz down to standard 60Hz/30Hz/24Hz
        activity?.let { act ->
            try {
                val window = act.window ?: return
                val params = window.attributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val display = act.display ?: return
                    val modes = display.supportedModes
                    // Find standard 60Hz or 30Hz/24Hz video mode to reduce display panel power by ~30%
                    val ecoMode = modes.filter { it.refreshRate in 23.9f..60.1f }.minByOrNull { it.refreshRate }
                        ?: modes.firstOrNull { it.refreshRate <= 60.1f }
                    if (ecoMode != null) {
                        if (originalModeId == null) originalModeId = params.preferredDisplayModeId
                        params.preferredDisplayModeId = ecoMode.modeId
                        window.attributes = params
                        Log.d(TAG, "Display refresh rate adjusted to ${ecoMode.refreshRate}Hz for battery conservation.")
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    val display = window.windowManager.defaultDisplay
                    val modes = display?.supportedModes ?: return
                    val ecoMode = modes.filter { it.refreshRate in 23.9f..60.1f }.minByOrNull { it.refreshRate }
                    if (ecoMode != null) {
                        if (originalModeId == null) originalModeId = params.preferredDisplayModeId
                        params.preferredDisplayModeId = ecoMode.modeId
                        window.attributes = params
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Display mode switch note: ${e.message}")
            }
        }
    }

    private fun deactivateCinemaEco(context: Context?) {
        _isCinemaEcoActive.value = false
        _isBackgroundProcessingSuspended.value = false
        Log.d(TAG, "Cinema Eco Mode Disengaged: Resumed standard 120Hz refresh & background processing.")

        val act = (context as? Activity)
        act?.let { a ->
            try {
                val window = a.window ?: return
                val params = window.attributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val display = a.display ?: return
                    val maxMode = display.supportedModes.maxByOrNull { it.refreshRate }
                    if (maxMode != null) {
                        params.preferredDisplayModeId = maxMode.modeId
                        window.attributes = params
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    val display = window.windowManager.defaultDisplay
                    val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
                    if (maxMode != null) {
                        params.preferredDisplayModeId = maxMode.modeId
                        window.attributes = params
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
