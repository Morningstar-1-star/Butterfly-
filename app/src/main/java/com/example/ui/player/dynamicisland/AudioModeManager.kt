package com.example.ui.player.dynamicisland

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.MainActivity
import com.example.ui.player.GlobalPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AudioModeManager: Orchestrates the transition between Video and Audio Mode,
 * and controls the Butterfly Dynamic Island background overlay lifecycle.
 */
object AudioModeManager {

    private const val TAG = "AudioModeManager"

    private val _isAudioModeActive = MutableStateFlow(false)
    val isAudioModeActive: StateFlow<Boolean> = _isAudioModeActive.asStateFlow()

    private val _isIslandExpanded = MutableStateFlow(false)
    val isIslandExpanded: StateFlow<Boolean> = _isIslandExpanded.asStateFlow()

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch overlay permission settings: ${e.message}")
            }
        }
    }

    /**
     * Enters Audio Mode:
     * - Keeps playback alive on the exact same Media3 / ExoPlayer instance without restarting.
     * - Starts the Butterfly Dynamic Island foreground service.
     * - Video rendering is hidden/detached, audio continues uninterrupted.
     */
    fun enterAudioMode(context: Context) {
        Log.i(TAG, "Entering Audio Mode & launching Dynamic Island")
        _isAudioModeActive.value = true

        try {
            val intent = Intent(context, ButterflyDynamicIslandService::class.java).apply {
                action = ButterflyDynamicIslandService.ACTION_START_ISLAND
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Dynamic Island service: ${e.message}", e)
        }
    }

    /**
     * Exits Audio Mode and restores full video player in Butterfly:
     * - Re-opens MainActivity to bring full video player back to foreground.
     * - The player surface re-attaches at the exact same playback position.
     */
    fun exitAudioMode(context: Context) {
        Log.i(TAG, "Exiting Audio Mode & returning to full video player")
        _isAudioModeActive.value = false
        _isIslandExpanded.value = false

        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("FROM_DYNAMIC_ISLAND", true)
            }
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch MainActivity from Dynamic Island: ${e.message}")
        }
    }

    fun toggleIslandExpanded() {
        _isIslandExpanded.value = !_isIslandExpanded.value
    }

    fun setIslandExpanded(expanded: Boolean) {
        _isIslandExpanded.value = expanded
    }

    fun stopAudioModeAndService(context: Context) {
        _isAudioModeActive.value = false
        _isIslandExpanded.value = false
        try {
            val intent = Intent(context, ButterflyDynamicIslandService::class.java).apply {
                action = ButterflyDynamicIslandService.ACTION_STOP_ISLAND
            }
            context.stopService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop Dynamic Island service: ${e.message}")
        }
    }
}
