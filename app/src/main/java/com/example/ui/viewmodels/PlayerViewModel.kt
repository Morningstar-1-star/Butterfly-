package com.example.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.extractor.YouTubeExtractorHelper
import com.example.extractor.YtDlpResolver
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated ViewModel managing the Video Player screen state, active streams, quality selections, and TV series navigation.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "PlayerViewModel"

    private val _activeVideoId = MutableStateFlow<String?>(null)
    val activeVideoId: StateFlow<String?> = _activeVideoId.asStateFlow()

    private val _currentStreamData = MutableStateFlow<StreamData?>(null)
    val currentStreamData: StateFlow<StreamData?> = _currentStreamData.asStateFlow()

    private val _isStreamLoading = MutableStateFlow(false)
    val isStreamLoading: StateFlow<Boolean> = _isStreamLoading.asStateFlow()

    private val _playbackQualityOptions = MutableStateFlow<List<PlayableStreamOption>>(emptyList())
    val playbackQualityOptions: StateFlow<List<PlayableStreamOption>> = _playbackQualityOptions.asStateFlow()

    private val _playerRecommendations = MutableStateFlow<List<VideoItem>>(emptyList())
    val playerRecommendations: StateFlow<List<VideoItem>> = _playerRecommendations.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    val playbackQueue: StateFlow<List<VideoItem>> = com.example.ui.player.GlobalPlayerManager.playbackQueue
    val currentQueueIndex: StateFlow<Int> = com.example.ui.player.GlobalPlayerManager.currentQueueIndex
    val hasNextItem: StateFlow<Boolean> = com.example.ui.player.GlobalPlayerManager.hasNextItem
    val hasPreviousItem: StateFlow<Boolean> = com.example.ui.player.GlobalPlayerManager.hasPreviousItem

    fun addToQueue(video: VideoItem) {
        com.example.ui.player.GlobalPlayerManager.addToQueue(video)
    }

    fun playNextInQueue(video: VideoItem) {
        com.example.ui.player.GlobalPlayerManager.playNextInQueue(video)
    }

    fun removeFromQueue(index: Int) {
        com.example.ui.player.GlobalPlayerManager.removeFromQueue(index)
    }

    fun removeFromQueue(video: VideoItem) {
        com.example.ui.player.GlobalPlayerManager.removeFromQueue(video)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        com.example.ui.player.GlobalPlayerManager.moveQueueItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        com.example.ui.player.GlobalPlayerManager.clearQueue()
    }

    fun skipToQueueIndex(index: Int) {
        com.example.ui.player.GlobalPlayerManager.skipToQueueIndex(index)
    }

    fun skipToNext(): Boolean {
        return com.example.ui.player.GlobalPlayerManager.skipToNext()
    }

    fun skipToPrevious(): Boolean {
        return com.example.ui.player.GlobalPlayerManager.skipToPrevious()
    }

    fun setPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun playVideo(videoIdOrUrl: String, providerId: String? = null) {
        _activeVideoId.value = videoIdOrUrl
        _isStreamLoading.value = true

        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val streamResult = withContext(Dispatchers.IO) {
                    if (YtDlpResolver.isYtDlpSupportedUrl(videoIdOrUrl)) {
                        YtDlpResolver.extractStreamInfo(ctx, videoIdOrUrl)
                    } else {
                        YouTubeExtractorHelper.resolveStream(videoIdOrUrl, ctx, providerId)
                    }
                }

                if (streamResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    val rawStream = streamResult.streamData
                    val titleRes = com.example.util.UniversalTranslator.translateTitle(rawStream.title)
                    val descRes = if (!rawStream.description.isNullOrBlank()) {
                        com.example.util.UniversalTranslator.translateDescription(rawStream.description)
                    } else Pair(null, null)

                    val translatedRelated = com.example.util.UniversalTranslator.translateVideoItemList(rawStream.relatedVideos)

                    val translatedStream = rawStream.copy(
                        title = titleRes.translatedEN.ifBlank { rawStream.title },
                        originalTitle = rawStream.title,
                        translatedTitleEN = titleRes.translatedEN.ifBlank { rawStream.title },
                        detectedLanguage = titleRes.detectedLanguage,
                        translatedDescriptionEN = descRes.first ?: rawStream.description,
                        relatedVideos = translatedRelated
                    )

                    _currentStreamData.value = translatedStream
                    _playbackQualityOptions.value = translatedStream.availableStreamOptions
                } else if (streamResult is YouTubeExtractorHelper.ExtractionResult.Error) {
                    Log.e(TAG, "Stream resolution error: ${streamResult.errorDetails.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "playVideo failed: ${e.message}", e)
            } finally {
                _isStreamLoading.value = false
            }
        }
    }

    fun closePlayer() {
        _activeVideoId.value = null
        _currentStreamData.value = null
        _playbackQualityOptions.value = emptyList()
        _isStreamLoading.value = false
    }
}
