package com.example.ui.player.dynamicisland

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.DisplayCutout
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.example.ui.player.GlobalPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Manages the floating WindowManager overlay for the Butterfly Dynamic Island.
 * Dynamically detects device display cutouts (camera hole/notch) and positions
 * the island naturally at the top edge.
 */
class DynamicIslandOverlayManager(
    private val context: Context,
    private val onOpenApp: () -> Unit,
    private val onCloseIsland: () -> Unit
) {
    private val TAG = "DynamicIslandOverlay"

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayComposeLifecycleOwner? = null
    private var isAttached = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private var stateObserverJob: Job? = null

    private var notchTopOffset = 8
    private var notchCenterXOffset = 0

    fun show() {
        if (isAttached || windowManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AudioModeManager.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot draw overlays: Permission not granted")
            return
        }

        try {
            val owner = OverlayComposeLifecycleOwner()
            lifecycleOwner = owner

            val view = ComposeView(context).apply {
                owner.attachTo(this)

                // Detect notch / display cutouts
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setOnApplyWindowInsetsListener { _, insets ->
                        val cutout = insets.displayCutout
                        if (cutout != null) {
                            val rects = cutout.boundingRects
                            if (rects.isNotEmpty()) {
                                val firstCutout = rects.first()
                                notchTopOffset = (firstCutout.top / 2).coerceAtLeast(4)
                            }
                        }
                        insets
                    }
                }

                setContent {
                    val isExpanded by AudioModeManager.isIslandExpanded.collectAsState()

                    DynamicIslandContent(
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            AudioModeManager.toggleIslandExpanded()
                            updateOverlayParams(AudioModeManager.isIslandExpanded.value)
                        },
                        onOpenFullApp = {
                            onOpenApp()
                            AudioModeManager.setIslandExpanded(false)
                        },
                        onCloseIsland = {
                            onCloseIsland()
                        }
                    )
                }
            }

            val params = createLayoutParams(isExpanded = AudioModeManager.isIslandExpanded.value)
            windowManager.addView(view, params)
            composeView = view
            isAttached = true

            // Automatically hide/dismiss if playback stopped
            stateObserverJob = scope.launch {
                GlobalPlayerManager.activeStreamData.collect { stream ->
                    if (stream == null && isAttached) {
                        dismiss()
                    }
                }
            }

            Log.i(TAG, "Dynamic Island overlay successfully attached to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show Dynamic Island overlay: ${e.message}", e)
            dismiss()
        }
    }

    private fun createLayoutParams(isExpanded: Boolean): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = notchCenterXOffset
            y = if (isExpanded) 12 else notchTopOffset
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        return params
    }

    private fun updateOverlayParams(isExpanded: Boolean) {
        val view = composeView ?: return
        if (!isAttached || windowManager == null) return
        try {
            val params = createLayoutParams(isExpanded)
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update overlay params: ${e.message}")
        }
    }

    fun dismiss() {
        stateObserverJob?.cancel()
        stateObserverJob = null

        val view = composeView
        if (view != null && isAttached && windowManager != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove Dynamic Island view: ${e.message}")
            }
        }
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        composeView = null
        isAttached = false
        Log.i(TAG, "Dynamic Island overlay dismissed")
    }
}
