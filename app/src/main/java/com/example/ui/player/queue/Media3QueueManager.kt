package com.example.ui.player.queue

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import com.example.extractor.YouTubeExtractorHelper
import com.example.extractor.YtDlpResolver
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import com.example.model.VideoItem
import com.example.ui.player.core.MediaSourceFactoryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Represents an individual video item within the Media3 playback queue.
 */
data class MediaQueueItem(
    val uniqueQueueId: String = UUID.randomUUID().toString(),
    val videoItem: VideoItem,
    val streamData: StreamData? = null,
    val selectedOption: PlayableStreamOption? = null,
    val captionOption: CaptionOption? = null,
    val mediaSource: MediaSource? = null,
    val isResolving: Boolean = false,
    val errorMessage: String? = null
)

/**
 * High-performance Media3 ConcatenatingMediaSource queue manager.
 * Orchestrates seamless sequential playback across multiple videos in a playlist queue,
 * dynamic item insertion/reordering/removal, and background stream pre-resolution.
 */
class Media3QueueManager(
    private val appContext: Context,
    private val mainScope: CoroutineScope,
    private val onActiveVideoChanged: (StreamData, VideoItem) -> Unit
) {
    private val TAG = "Media3QueueManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var exoPlayer: ExoPlayer? = null
    var concatenatingMediaSource: ConcatenatingMediaSource = ConcatenatingMediaSource()
        private set

    private val _queueItems = MutableStateFlow<List<MediaQueueItem>>(emptyList())
    val queueItems: StateFlow<List<MediaQueueItem>> = _queueItems.asStateFlow()

    private val _videoQueue = MutableStateFlow<List<VideoItem>>(emptyList())
    val videoQueue: StateFlow<List<VideoItem>> = _videoQueue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _hasNextItem = MutableStateFlow(false)
    val hasNextItem: StateFlow<Boolean> = _hasNextItem.asStateFlow()

    private val _hasPreviousItem = MutableStateFlow(false)
    val hasPreviousItem: StateFlow<Boolean> = _hasPreviousItem.asStateFlow()

    private val resolutionJobs = mutableMapOf<String, Job>()

    fun attachPlayer(player: ExoPlayer) {
        this.exoPlayer = player
    }

    /**
     * Initializes playback with a primary video stream and creates a fresh ConcatenatingMediaSource.
     */
    fun setInitialStream(
        streamData: StreamData,
        streamOption: PlayableStreamOption?,
        captionOption: CaptionOption? = null,
        initialMediaSource: MediaSource,
        videoItem: VideoItem? = null
    ) {
        cancelAllResolutions()

        val primaryVideoItem = videoItem ?: VideoItem(
            id = streamData.videoId,
            title = streamData.title,
            uploaderName = streamData.channelName,
            thumbnailUrl = streamData.thumbnailUrl ?: "https://i.ytimg.com/vi/${streamData.videoId}/hqdefault.jpg",
            providerId = streamData.providerId,
            viewCount = streamData.viewCount
        )

        val primaryQueueItem = MediaQueueItem(
            videoItem = primaryVideoItem,
            streamData = streamData,
            selectedOption = streamOption,
            captionOption = captionOption,
            mediaSource = initialMediaSource,
            isResolving = false
        )

        // Create new ConcatenatingMediaSource with primary media source
        concatenatingMediaSource = ConcatenatingMediaSource(initialMediaSource)
        _queueItems.value = listOf(primaryQueueItem)
        _videoQueue.value = listOf(primaryVideoItem)
        _currentQueueIndex.value = 0
        updateNavigationState()

        val player = exoPlayer
        if (player != null) {
            player.setMediaSource(concatenatingMediaSource)
        }
    }

    /**
     * Adds a video item to the end of the queue, initiating background stream resolution.
     */
    fun addToQueue(video: VideoItem) {
        addVideosToQueue(listOf(video), playNext = false)
    }

    /**
     * Inserts a video to play next immediately after the currently playing video.
     */
    fun playNextInQueue(video: VideoItem) {
        addVideosToQueue(listOf(video), playNext = true)
    }

    /**
     * Adds a list of videos to the queue.
     */
    fun addVideosToQueue(videos: List<VideoItem>, playNext: Boolean = false) {
        if (videos.isEmpty()) return

        val currentList = _queueItems.value.toMutableList()
        val curIdx = _currentQueueIndex.value.coerceIn(0, (currentList.size - 1).coerceAtLeast(0))

        val newItems = videos.map { video ->
            MediaQueueItem(
                videoItem = video,
                streamData = null,
                selectedOption = null,
                mediaSource = null,
                isResolving = true
            )
        }

        val insertIndex = if (playNext && currentList.isNotEmpty()) {
            (curIdx + 1).coerceAtMost(currentList.size)
        } else {
            currentList.size
        }

        currentList.addAll(insertIndex, newItems)
        _queueItems.value = currentList
        _videoQueue.value = currentList.map { it.videoItem }
        updateNavigationState()

        // Resolve streams and attach to ConcatenatingMediaSource
        newItems.forEach { item ->
            resolveAndAttachMediaSource(item)
        }
    }

    private fun resolveAndAttachMediaSource(item: MediaQueueItem) {
        val vid = item.videoItem.id
        resolutionJobs[item.uniqueQueueId]?.cancel()

        val job = mainScope.launch {
            try {
                val streamResult = withContext(Dispatchers.IO) {
                    if (YtDlpResolver.isYtDlpSupportedUrl(vid)) {
                        YtDlpResolver.extractStreamInfo(appContext, vid)
                    } else {
                        YouTubeExtractorHelper.resolveStream(vid, appContext, item.videoItem.providerId)
                    }
                }

                if (streamResult is YouTubeExtractorHelper.ExtractionResult.Success) {
                    val streamData = streamResult.streamData
                    val selectedOption = streamData.selectedStreamOption
                        ?: streamData.availableStreamOptions.firstOrNull { !it.videoUrl.isNullOrBlank() }

                    val mediaSource = MediaSourceFactoryHelper.buildMediaSource(
                        context = appContext,
                        streamData = streamData,
                        streamOption = selectedOption,
                        captionOption = streamData.captionOptions.firstOrNull(),
                        hlsUrl = streamData.hlsUrl,
                        tag = item.uniqueQueueId
                    )

                    if (mediaSource != null) {
                        attachResolvedSourceToConcatenating(item.uniqueQueueId, streamData, selectedOption, mediaSource)
                    } else {
                        markResolutionFailed(item.uniqueQueueId, "Could not build media source")
                    }
                } else if (streamResult is YouTubeExtractorHelper.ExtractionResult.Error) {
                    markResolutionFailed(item.uniqueQueueId, streamResult.errorDetails.message)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed resolving stream for queue item $vid: ${e.message}")
                markResolutionFailed(item.uniqueQueueId, e.message ?: "Resolution error")
            } finally {
                resolutionJobs.remove(item.uniqueQueueId)
            }
        }
        resolutionJobs[item.uniqueQueueId] = job
    }

    private fun attachResolvedSourceToConcatenating(
        queueId: String,
        streamData: StreamData,
        selectedOption: PlayableStreamOption?,
        mediaSource: MediaSource
    ) {
        val currentList = _queueItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.uniqueQueueId == queueId }
        if (index == -1) return

        val updatedItem = currentList[index].copy(
            streamData = streamData,
            selectedOption = selectedOption,
            mediaSource = mediaSource,
            isResolving = false,
            errorMessage = null
        )
        currentList[index] = updatedItem
        _queueItems.value = currentList
        _videoQueue.value = currentList.map { it.videoItem }

        // Find insertion position in ConcatenatingMediaSource:
        // ConcatenatingMediaSource index corresponds to the count of resolved items before this index
        var concatenatingTargetIndex = 0
        for (i in 0 until index) {
            if (currentList[i].mediaSource != null) {
                concatenatingTargetIndex++
            }
        }

        try {
            if (concatenatingTargetIndex >= concatenatingMediaSource.size) {
                concatenatingMediaSource.addMediaSource(mediaSource)
            } else {
                concatenatingMediaSource.addMediaSource(concatenatingTargetIndex, mediaSource)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error inserting MediaSource into ConcatenatingMediaSource: ${e.message}", e)
        }

        updateNavigationState()
    }

    private fun markResolutionFailed(queueId: String, errorMsg: String) {
        val currentList = _queueItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.uniqueQueueId == queueId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(isResolving = false, errorMessage = errorMsg)
            _queueItems.value = currentList
        }
    }

    /**
     * Removes an item from the queue by its index.
     */
    fun removeFromQueue(index: Int) {
        val currentList = _queueItems.value.toMutableList()
        if (index !in currentList.indices) return

        val item = currentList.removeAt(index)
        resolutionJobs[item.uniqueQueueId]?.cancel()
        resolutionJobs.remove(item.uniqueQueueId)

        // If this item was attached to ConcatenatingMediaSource, remove it
        var concatIdx = 0
        var foundInConcat = false
        for (i in 0 until index) {
            if (_queueItems.value[i].mediaSource != null) {
                concatIdx++
            }
        }
        if (item.mediaSource != null && concatIdx < concatenatingMediaSource.size) {
            try {
                concatenatingMediaSource.removeMediaSource(concatIdx)
            } catch (e: Throwable) {
                Log.w(TAG, "Error removing from ConcatenatingMediaSource: ${e.message}")
            }
        }

        _queueItems.value = currentList
        _videoQueue.value = currentList.map { it.videoItem }

        val curIdx = _currentQueueIndex.value
        if (curIdx >= currentList.size) {
            _currentQueueIndex.value = (currentList.size - 1).coerceAtLeast(0)
        } else if (index < curIdx) {
            _currentQueueIndex.value = (curIdx - 1).coerceAtLeast(0)
        }

        updateNavigationState()
    }

    /**
     * Removes an item from the queue by its VideoItem id.
     */
    fun removeFromQueue(video: VideoItem) {
        val idx = _queueItems.value.indexOfFirst { it.videoItem.id == video.id }
        if (idx != -1) {
            removeFromQueue(idx)
        }
    }

    /**
     * Moves a queue item from fromIndex to toIndex.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentList = _queueItems.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) return

        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        _queueItems.value = currentList
        _videoQueue.value = currentList.map { it.videoItem }

        // Update ConcatenatingMediaSource if both items have mediaSources
        try {
            if (fromIndex < concatenatingMediaSource.size && toIndex < concatenatingMediaSource.size) {
                concatenatingMediaSource.moveMediaSource(fromIndex, toIndex)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error moving source in ConcatenatingMediaSource: ${e.message}")
        }

        // Adjust active index
        val curIdx = _currentQueueIndex.value
        if (curIdx == fromIndex) {
            _currentQueueIndex.value = toIndex
        } else if (fromIndex < curIdx && toIndex >= curIdx) {
            _currentQueueIndex.value = curIdx - 1
        } else if (fromIndex > curIdx && toIndex <= curIdx) {
            _currentQueueIndex.value = curIdx + 1
        }

        updateNavigationState()
    }

    /**
     * Clears all future items in the queue, keeping only the currently playing video.
     */
    fun clearQueue() {
        val currentList = _queueItems.value
        val curIdx = _currentQueueIndex.value.coerceIn(0, (currentList.size - 1).coerceAtLeast(0))
        if (currentList.isEmpty()) return

        val currentItem = currentList.getOrNull(curIdx) ?: currentList.first()

        // Cancel pending resolutions
        currentList.forEachIndexed { i, it ->
            if (i != curIdx) {
                resolutionJobs[it.uniqueQueueId]?.cancel()
                resolutionJobs.remove(it.uniqueQueueId)
            }
        }

        // Remove media sources after current from concatenating source
        try {
            while (concatenatingMediaSource.size > 1) {
                val removeIndex = if (curIdx == 0) 1 else 0
                concatenatingMediaSource.removeMediaSource(removeIndex)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error clearing ConcatenatingMediaSource: ${e.message}")
        }

        _queueItems.value = listOf(currentItem)
        _videoQueue.value = listOf(currentItem.videoItem)
        _currentQueueIndex.value = 0
        updateNavigationState()
    }

    /**
     * Skips directly to the queue item at targetIndex.
     */
    fun skipToQueueIndex(targetIndex: Int) {
        val currentList = _queueItems.value
        if (targetIndex !in currentList.indices) return

        val targetItem = currentList[targetIndex]
        val player = exoPlayer ?: return

        // Map targetIndex to concatenatingMediaSource index
        var concatIdx = 0
        for (i in 0 until targetIndex) {
            if (currentList[i].mediaSource != null) {
                concatIdx++
            }
        }

        if (targetItem.mediaSource != null && concatIdx < concatenatingMediaSource.size) {
            _currentQueueIndex.value = targetIndex
            updateNavigationState()
            player.seekToDefaultPosition(concatIdx)
            player.play()
            targetItem.streamData?.let { onActiveVideoChanged(it, targetItem.videoItem) }
        } else {
            // Need immediate resolution if not ready yet
            mainScope.launch {
                targetItem.videoItem.let { video ->
                    _currentQueueIndex.value = targetIndex
                    updateNavigationState()
                    val res = withContext(Dispatchers.IO) {
                        YouTubeExtractorHelper.resolveStream(video.id, appContext, video.providerId)
                    }
                    if (res is YouTubeExtractorHelper.ExtractionResult.Success) {
                        val streamData = res.streamData
                        val opt = streamData.selectedStreamOption
                            ?: streamData.availableStreamOptions.firstOrNull()
                        val ms = MediaSourceFactoryHelper.buildMediaSource(
                            context = appContext,
                            streamData = streamData,
                            streamOption = opt,
                            captionOption = streamData.captionOptions.firstOrNull(),
                            hlsUrl = streamData.hlsUrl,
                            tag = targetItem.uniqueQueueId
                        )
                        if (ms != null) {
                            attachResolvedSourceToConcatenating(targetItem.uniqueQueueId, streamData, opt, ms)
                            skipToQueueIndex(targetIndex)
                        }
                    }
                }
            }
        }
    }

    /**
     * Skips to the next video item in the playlist queue.
     */
    fun skipToNext(): Boolean {
        val player = exoPlayer ?: return false
        val nextIdx = _currentQueueIndex.value + 1
        if (nextIdx < _queueItems.value.size) {
            skipToQueueIndex(nextIdx)
            return true
        } else if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            return true
        }
        return false
    }

    /**
     * Skips to the previous video item in the playlist queue.
     */
    fun skipToPrevious(): Boolean {
        val player = exoPlayer ?: return false
        val prevIdx = _currentQueueIndex.value - 1
        if (prevIdx >= 0) {
            skipToQueueIndex(prevIdx)
            return true
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            return true
        }
        return false
    }

    /**
     * Handles Media3 onMediaItemTransition event.
     */
    fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val player = exoPlayer ?: return
        val concatIdx = player.currentMediaItemIndex
        val currentList = _queueItems.value

        // Map concatenating index back to queue item index
        var mappedQueueIdx = 0
        var foundCount = 0
        for (i in currentList.indices) {
            if (currentList[i].mediaSource != null) {
                if (foundCount == concatIdx) {
                    mappedQueueIdx = i
                    break
                }
                foundCount++
            }
        }

        if (mappedQueueIdx in currentList.indices) {
            _currentQueueIndex.value = mappedQueueIdx
            val item = currentList[mappedQueueIdx]
            updateNavigationState()

            item.streamData?.let {
                onActiveVideoChanged(it, item.videoItem)
            }
        }
    }

    private fun updateNavigationState() {
        val total = _queueItems.value.size
        val cur = _currentQueueIndex.value
        _hasNextItem.value = cur < total - 1
        _hasPreviousItem.value = cur > 0
    }

    private fun cancelAllResolutions() {
        resolutionJobs.values.forEach { it.cancel() }
        resolutionJobs.clear()
    }

    fun release() {
        cancelAllResolutions()
        exoPlayer = null
        _queueItems.value = emptyList()
        _videoQueue.value = emptyList()
    }
}
