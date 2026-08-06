package com.example.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption

@Composable
fun YouTubePlayerView(
    streamOption: PlayableStreamOption?,
    hlsUrl: String?,
    captionOption: CaptionOption?,
    embedUrl: String? = null,
    providerId: String? = null,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    UniversalVideoPlayer(
        streamOption = streamOption,
        hlsUrl = hlsUrl,
        captionOption = captionOption,
        embedUrl = embedUrl,
        providerId = providerId,
        isPlaying = isPlaying,
        modifier = modifier
    )
}
