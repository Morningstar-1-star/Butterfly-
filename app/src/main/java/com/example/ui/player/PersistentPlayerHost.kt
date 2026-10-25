package com.example.ui.player

import android.view.LayoutInflater
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

@Composable
fun PersistentPlayerHost(
    modifier: Modifier = Modifier,
    useController: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onFullscreenClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val exoPlayer = remember(context) { GlobalPlayerManager.getExoPlayer(context) }
    val isPlaying by GlobalPlayerManager.isPlaying.collectAsState()
    val isBuffering by GlobalPlayerManager.isBuffering.collectAsState()
    val shouldKeepScreenOn = isPlaying || isBuffering

    AndroidView(
        factory = { ctx ->
            val view = LayoutInflater.from(ctx).inflate(R.layout.persistent_media3_player_view, null, false) as PlayerView
            view.apply {
                this.player = exoPlayer
                this.useController = useController
                this.resizeMode = resizeMode
                this.isClickable = false
                this.isFocusable = false
                this.keepScreenOn = shouldKeepScreenOn
                this.setOnTouchListener { _, _ -> false }
                setKeepContentOnPlayerReset(false)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
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
            playerView.keepScreenOn = shouldKeepScreenOn
            if (playerView.player != exoPlayer) {
                playerView.player = exoPlayer
            }
            if (playerView.useController != useController) {
                playerView.useController = useController
            }
            if (playerView.resizeMode != resizeMode) {
                playerView.resizeMode = resizeMode
            }
            playerView.setKeepContentOnPlayerReset(false)
            playerView.setShutterBackgroundColor(android.graphics.Color.BLACK)
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            if (onFullscreenClick != null) {
                playerView.setFullscreenButtonClickListener { onFullscreenClick() }
            } else {
                playerView.setFullscreenButtonClickListener(null)
            }
        },
        onRelease = { playerView ->
            playerView.setControllerVisibilityListener(null as? PlayerView.ControllerVisibilityListener)
            playerView.setFullscreenButtonClickListener(null)
            playerView.player = null
        },
        modifier = modifier
    )
}
