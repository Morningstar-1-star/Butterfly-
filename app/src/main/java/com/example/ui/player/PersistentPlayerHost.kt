package com.example.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

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
            PlayerView(ctx).apply {
                this.player = exoPlayer
                this.useController = useController
                this.resizeMode = resizeMode
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    GlobalPlayerManager.setControlsVisibility(visibility == android.view.View.VISIBLE)
                })
                if (onFullscreenClick != null) {
                    setFullscreenButtonClickListener { onFullscreenClick() }
                } else {
                    setFullscreenButtonClickListener(null)
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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
            if (onFullscreenClick != null) {
                playerView.setFullscreenButtonClickListener { onFullscreenClick() }
            } else {
                playerView.setFullscreenButtonClickListener(null)
            }
        },
        onRelease = { playerView ->
            // Do not clear playerView.player to null on the singleton ExoPlayer during transitions.
            // Setting player to null here disconnects the video decoder surface, leading to black screens or ANRs.
        },
        modifier = modifier
    )
}
