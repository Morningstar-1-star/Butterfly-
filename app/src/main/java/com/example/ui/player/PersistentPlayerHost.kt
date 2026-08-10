package com.example.ui.player

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout

@Composable
fun PersistentPlayerHost(
    modifier: Modifier = Modifier,
    useController: Boolean = true,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onFullscreenClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isEmbedWeb by GlobalPlayerManager.isEmbedOrWebPage.collectAsState()

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { frameLayout ->
            val targetView: View = if (isEmbedWeb) {
                GlobalPlayerManager.getOrCreateWebView(context)
            } else {
                val playerView = GlobalPlayerManager.getOrCreatePlayerView(context)
                playerView.useController = useController
                playerView.resizeMode = resizeMode
                if (onFullscreenClick != null) {
                    playerView.setFullscreenButtonClickListener {
                        onFullscreenClick()
                    }
                } else {
                    playerView.setFullscreenButtonClickListener(null)
                }
                playerView
            }

            if (targetView.parent != frameLayout) {
                (targetView.parent as? ViewGroup)?.removeView(targetView)
                frameLayout.removeAllViews()
                frameLayout.addView(
                    targetView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        },
        onRelease = { frameLayout ->
            frameLayout.removeAllViews()
        },
        modifier = modifier
    )
}
