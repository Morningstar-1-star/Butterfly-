package com.example.engine

import android.content.Context
import android.util.Log
import com.example.model.CaptionOption
import com.example.model.FailedSourceLog
import com.example.model.MediaIdentity
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.plugin.manager.PipelineValidationResult
import com.example.plugin.manager.SourcePipelineEngine
import com.example.plugin.sdk.api.ContentProviderApi
import com.example.ui.player.GlobalPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class PlaybackEngine(
    private val context: Context,
    private val sourcePipelineEngine: SourcePipelineEngine = SourcePipelineEngine(context = context)
) {
    companion object {
        private const val TAG = "PlaybackEngine"
    }

    private val _isResolvingStreams = MutableStateFlow(false)
    val isResolvingStreams: StateFlow<Boolean> = _isResolvingStreams.asStateFlow()

    private val _playableStreams = MutableStateFlow<List<PlayableStreamOption>>(emptyList())
    val playableStreams: StateFlow<List<PlayableStreamOption>> = _playableStreams.asStateFlow()

    private val _failedSourceLogs = MutableStateFlow<List<FailedSourceLog>>(emptyList())
    val failedSourceLogs: StateFlow<List<FailedSourceLog>> = _failedSourceLogs.asStateFlow()

    private val _canonicalMediaIdentity = MutableStateFlow<MediaIdentity?>(null)
    val canonicalMediaIdentity: StateFlow<MediaIdentity?> = _canonicalMediaIdentity.asStateFlow()

    suspend fun discoverAndPrepareStreams(
        idOrUrl: String,
        providers: List<ContentProviderApi>,
        torBoxApiKey: String? = null,
        targetProviderId: String? = null
    ): PipelineValidationResult = withContext(Dispatchers.IO) {
        _isResolvingStreams.value = true
        Log.d(TAG, "Discovering streams for '$idOrUrl' across ${providers.size} providers...")

        val result = sourcePipelineEngine.discoverAndRankStreams(
            idOrUrl = idOrUrl,
            providers = providers,
            torBoxApiKey = torBoxApiKey,
            targetProviderId = targetProviderId
        )

        _playableStreams.value = result.playableStreams
        _failedSourceLogs.value = result.failedLogs
        _canonicalMediaIdentity.value = result.mediaIdentity
        _isResolvingStreams.value = false

        result
    }

    fun playStream(
        streamData: StreamData?,
        streamOption: PlayableStreamOption?,
        hlsUrl: String? = null,
        captionOption: CaptionOption? = null,
        embedUrl: String? = null,
        initialPos: Long = 0L
    ) {
        GlobalPlayerManager.prepareAndPlay(
            context = context,
            streamData = streamData,
            streamOption = streamOption,
            hlsUrl = hlsUrl,
            captionOption = captionOption,
            embedUrl = embedUrl,
            initialPos = initialPos
        )
    }

    fun stopPlayback() {
        GlobalPlayerManager.stopAndClear()
    }
}
