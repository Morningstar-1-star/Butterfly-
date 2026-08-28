package com.example.ui.player

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R

@Composable
fun PersistentPlayerHost(
    modifier: Modifier = Modifier,
    useController: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onFullscreenClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val exoPlayer = remember(context) { GlobalPlayerManager.getExoPlayer(context) }

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


