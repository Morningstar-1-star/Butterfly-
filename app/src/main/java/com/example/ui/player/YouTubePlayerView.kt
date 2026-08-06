package com.example.ui.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import okhttp3.OkHttpClient

@Composable
fun YouTubePlayerView(
    streamOption: PlayableStreamOption?,
    hlsUrl: String?,
    captionOption: CaptionOption?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val okHttpClient = remember {
        OkHttpClient.Builder().build()
    }

    val dataSourceFactory = remember {
        OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(streamOption, hlsUrl, captionOption) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        if (streamOption != null) {
            val videoUrl = streamOption.videoUrl ?: streamOption.videoStream?.url
            val audioUrl = streamOption.audioUrl ?: streamOption.audioStream?.url

            if (streamOption.isMuxed && videoUrl != null) {
                val builder = MediaItem.Builder().setUri(Uri.parse(videoUrl))
                if (captionOption != null) {
                    val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(captionOption.url))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(captionOption.languageCode)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    builder.setSubtitleConfigurations(listOf(subtitleConfig))
                }
                exoPlayer.setMediaItem(builder.build())
                exoPlayer.prepare()
            } else if (!streamOption.isMuxed && videoUrl != null && audioUrl != null) {
                val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)))
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(audioUrl)))

                val mergedSource = MergingMediaSource(videoSource, audioSource)
                exoPlayer.setMediaSource(mergedSource)
                exoPlayer.prepare()
            }
        } else if (!hlsUrl.isNullOrEmpty()) {
            val mediaItem = MediaItem.fromUri(Uri.parse(hlsUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
