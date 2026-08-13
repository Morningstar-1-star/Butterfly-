package com.example.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun PersistentPlayerHost(
    modifier: Modifier = Modifier,
    useController: Boolean = true,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onFullscreenClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isEmbedWeb by GlobalPlayerManager.isEmbedOrWebPage.collectAsState()

    if (isEmbedWeb) {
        val webView = remember(context) { GlobalPlayerManager.getOrCreateWebView(context) }
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    try {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        addView(
                            webView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    } catch (t: Throwable) {
                        // Safe catch
                    }
                }
            },
            update = { frameLayout ->
                if (webView.parent != frameLayout) {
                    try {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        frameLayout.removeAllViews()
                        frameLayout.addView(
                            webView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    } catch (t: Throwable) {
                        // Safe catch
                    }
                }
            },
            onRelease = { frameLayout ->
                try {
                    frameLayout.removeAllViews()
                } catch (t: Throwable) {}
            },
            modifier = modifier
        )
    } else {
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
                playerView.player = exoPlayer
                playerView.useController = useController
                playerView.resizeMode = resizeMode
                if (onFullscreenClick != null) {
                    playerView.setFullscreenButtonClickListener { onFullscreenClick() }
                } else {
                    playerView.setFullscreenButtonClickListener(null)
                }
            },
            onRelease = { playerView ->
                try {
                    playerView.player = null
                } catch (t: Throwable) {}
            },
            modifier = modifier
        )
    }
}

