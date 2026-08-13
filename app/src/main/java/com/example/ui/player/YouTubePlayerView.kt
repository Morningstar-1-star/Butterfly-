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
    videoId: String? = null,
    initialPositionMs: Long = 0L,
    availableStreamOptions: List<PlayableStreamOption> = emptyList(),
    onSelectStreamOption: (PlayableStreamOption) -> Unit = {},
    failedSourceLogs: List<com.example.model.FailedSourceLog> = emptyList(),
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    UniversalVideoPlayer(
        streamOption = streamOption,
        hlsUrl = hlsUrl,
        captionOption = captionOption,
        embedUrl = embedUrl,
        providerId = providerId,
        isPlaying = isPlaying,
        videoId = videoId,
        initialPositionMs = initialPositionMs,
        availableStreamOptions = availableStreamOptions,
        onSelectStreamOption = onSelectStreamOption,
        failedSourceLogs = failedSourceLogs,
        onProgressUpdate = onProgressUpdate,
        modifier = modifier
    )
}
