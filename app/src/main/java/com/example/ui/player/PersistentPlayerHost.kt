package com.example.ui.player

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.effects.VideoEffectsConfig
import com.example.effects.VideoEffectsEngine
import com.example.effects.VideoEffectsManager

@Composable
fun PersistentPlayerHost(
    modifier: Modifier = Modifier,
    useController: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onFullscreenClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val exoPlayer = remember(context) { GlobalPlayerManager.getExoPlayer(context) }
    val videoEffectsConfig by VideoEffectsManager.currentConfig.collectAsState()

    AndroidView(
        factory = { ctx ->
            val view = LayoutInflater.from(ctx).inflate(R.layout.persistent_media3_player_view, null, false) as PlayerView
            view.apply {
                this.player = exoPlayer
                this.useController = useController
                this.resizeMode = resizeMode
                this.isClickable = false
                this.isFocusable = false
                this.setOnTouchListener { _, _ -> false }
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    GlobalPlayerManager.setControlsVisibility(visibility == android.view.View.VISIBLE)
                })
                if (onFullscreenClick != null) {
                    setFullscreenButtonClickListener { onFullscreenClick() }
                } else {
                    setFullscreenButtonClickListener(null)
                }
                applyEffectsToPlayerView(this, videoEffectsConfig)
            }
        },
        update = { playerView ->
            if (playerView.player != exoPlayer) {
                playerView.player = exoPlayer
            }
            if (playerView.useController != useController) {
                playerView.useController = useController
            }
            if (playerView.resizeMode != resizeMode) {
                playerView.resizeMode = resizeMode
            }
            playerView.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            if (onFullscreenClick != null) {
                playerView.setFullscreenButtonClickListener { onFullscreenClick() }
            } else {
                playerView.setFullscreenButtonClickListener(null)
            }
            applyEffectsToPlayerView(playerView, videoEffectsConfig)
        },
        onRelease = { playerView ->
            // Detach this playerView from the player without resetting ExoPlayer's active rendering surface
            // if ExoPlayer is still playing or managed globally.
            playerView.setControllerVisibilityListener(null as? PlayerView.ControllerVisibilityListener)
            playerView.setFullscreenButtonClickListener(null)
        },
        modifier = modifier
    )
}

private fun applyEffectsToPlayerView(playerView: PlayerView, config: VideoEffectsConfig) {
    val targetViews = mutableListOf<View>(playerView)
    playerView.videoSurfaceView?.let { targetViews.add(it) }

    if (!config.isEnabled) {
        for (v in targetViews) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                v.setRenderEffect(null)
            }
            v.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        return
    }

    val matrixArray = VideoEffectsEngine.computeCombinedColorMatrix(config)
    val colorFilter = android.graphics.ColorMatrixColorFilter(matrixArray)
    val blurRadius = (config.enhancement.blur / 100f) * 25f

    for (v in targetViews) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val colorEffect = android.graphics.RenderEffect.createColorFilterEffect(colorFilter)
            val finalEffect = if (blurRadius > 0.5f) {
                val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    android.graphics.Shader.TileMode.CLAMP
                )
                android.graphics.RenderEffect.createChainEffect(blurEffect, colorEffect)
            } else {
                colorEffect
            }
            v.setRenderEffect(finalEffect)
        } else {
            val paint = android.graphics.Paint().apply {
                this.colorFilter = colorFilter
            }
            v.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
    }
}


