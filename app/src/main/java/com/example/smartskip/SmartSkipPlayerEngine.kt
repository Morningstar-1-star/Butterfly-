package com.example.smartskip

import android.content.Context
import android.util.Log
import com.example.ui.player.GlobalPlayerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SmartSkipPlayerEngine {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _activeSegments = MutableStateFlow<List<SkipSegment>>(emptyList())
    val activeSegments: StateFlow<List<SkipSegment>> = _activeSegments.asStateFlow()

    private val _currentPromptSegment = MutableStateFlow<SkipSegment?>(null)
    val currentPromptSegment: StateFlow<SkipSegment?> = _currentPromptSegment.asStateFlow()

    private val _skipNotificationText = MutableStateFlow<String?>(null)
    val skipNotificationText: StateFlow<String?> = _skipNotificationText.asStateFlow()

    private val _skipNotificationCategory = MutableStateFlow<SkipCategory?>(null)
    val skipNotificationCategory: StateFlow<SkipCategory?> = _skipNotificationCategory.asStateFlow()

    private val _isSkipAnimating = MutableStateFlow(false)
    val isSkipAnimating: StateFlow<Boolean> = _isSkipAnimating.asStateFlow()

    // Track UUIDs or keys of segments already auto-skipped in current playback pass
    private val skippedSegmentKeys = mutableSetOf<String>()
    private var currentVideoKey: String = ""

    private var promptDismissJob: Job? = null
    private var notificationDismissJob: Job? = null
    private var loadJob: Job? = null

    fun onVideoChanged(
        context: Context,
        videoId: String?,
        durationMs: Long = 0L,
        title: String? = null,
        channelName: String? = null,
        providerId: String? = null,
        extraMeta: Map<String, String> = emptyMap()
    ) {
        val cleanVideoId = videoId ?: ""
        if (cleanVideoKey == cleanVideoId && cleanVideoId.isNotEmpty()) return

        cleanVideoKey = cleanVideoId
        skippedSegmentKeys.clear()
        _activeSegments.value = emptyList()
        _currentPromptSegment.value = null
        _skipNotificationText.value = null
        _skipNotificationCategory.value = null

        if (cleanVideoId.isBlank()) return

        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            try {
                val segments = SmartSkipCoordinator.resolveSegments(
                    context = context,
                    videoId = cleanVideoId,
                    durationMs = durationMs,
                    title = title,
                    channelName = channelName,
                    providerId = providerId,
                    extraMeta = extraMeta
                )
                withContext(Dispatchers.Main) {
                    _activeSegments.value = segments
                    Log.d("SmartSkipPlayerEngine", "Loaded ${segments.size} segments for $cleanVideoId")
                }
            } catch (e: Exception) {
                Log.w("SmartSkipPlayerEngine", "Error loading segments: ${e.message}")
            }
        }
    }

    private var cleanVideoKey: String = ""

    fun onPlaybackPositionUpdate(context: Context, currentPositionMs: Long) {
        val segments = _activeSegments.value
        if (segments.isEmpty()) {
            if (_currentPromptSegment.value != null) {
                _currentPromptSegment.value = null
            }
            return
        }

        val prefs = SmartSkipPreferences.getInstance(context)
        if (!prefs.isSmartSkipEnabled.value) {
            _currentPromptSegment.value = null
            return
        }

        // Find active segment at current position
        val matchingSegment = segments.firstOrNull { seg ->
            currentPositionMs >= seg.startMs && currentPositionMs < (seg.endMs - 150)
        }

        if (matchingSegment == null) {
            if (_currentPromptSegment.value != null) {
                _currentPromptSegment.value = null
            }
            return
        }

        val segmentKey = "${matchingSegment.category.id}_${matchingSegment.startMs}_${matchingSegment.endMs}"
        val behavior = prefs.getBehaviorFor(matchingSegment.category)

        when (behavior) {
            SkipBehavior.AUTO_SKIP -> {
                // Auto skip past segment if not already skipped
                if (!skippedSegmentKeys.contains(segmentKey)) {
                    skippedSegmentKeys.add(segmentKey)
                    _currentPromptSegment.value = null

                    // Perform seek
                    GlobalPlayerManager.seekTo(matchingSegment.endMs)

                    // Trigger notification
                    if (prefs.skipNotification.value) {
                        showNotification(
                            text = "Skipped: ${matchingSegment.label.ifBlank { matchingSegment.category.displayName }}",
                            category = matchingSegment.category
                        )
                    }

                    // Trigger skip animation
                    if (prefs.skipAnimation.value) {
                        triggerAnimation()
                    }

                    Log.i("SmartSkipPlayerEngine", "Auto-skipped ${matchingSegment.category.displayName} from ${matchingSegment.startMs}ms to ${matchingSegment.endMs}ms")
                }
            }

            SkipBehavior.SHOW_BUTTON -> {
                if (_currentPromptSegment.value != matchingSegment && !skippedSegmentKeys.contains(segmentKey)) {
                    _currentPromptSegment.value = matchingSegment
                    schedulePromptDismiss(prefs.skipButtonDurationSec.value)
                }
            }

            SkipBehavior.DONT_SKIP -> {
                _currentPromptSegment.value = null
            }
        }
    }

    fun performManualSkip(context: Context) {
        val seg = _currentPromptSegment.value ?: return
        val segmentKey = "${seg.category.id}_${seg.startMs}_${seg.endMs}"
        skippedSegmentKeys.add(segmentKey)
        _currentPromptSegment.value = null

        val prefs = SmartSkipPreferences.getInstance(context)
        GlobalPlayerManager.seekTo(seg.endMs)

        if (prefs.skipNotification.value) {
            showNotification(
                text = "Skipped: ${seg.label.ifBlank { seg.category.displayName }}",
                category = seg.category
            )
        }
        if (prefs.skipAnimation.value) {
            triggerAnimation()
        }
    }

    fun dismissPrompt() {
        promptDismissJob?.cancel()
        _currentPromptSegment.value = null
    }

    private fun schedulePromptDismiss(durationSec: Int) {
        promptDismissJob?.cancel()
        promptDismissJob = scope.launch {
            delay(durationSec * 1000L)
            _currentPromptSegment.value = null
        }
    }

    private fun showNotification(text: String, category: SkipCategory) {
        notificationDismissJob?.cancel()
        _skipNotificationText.value = text
        _skipNotificationCategory.value = category
        notificationDismissJob = scope.launch {
            delay(2800L)
            _skipNotificationText.value = null
            _skipNotificationCategory.value = null
        }
    }

    private fun triggerAnimation() {
        scope.launch {
            _isSkipAnimating.value = true
            delay(350L)
            _isSkipAnimating.value = false
        }
    }

    fun reset() {
        loadJob?.cancel()
        promptDismissJob?.cancel()
        notificationDismissJob?.cancel()
        cleanVideoKey = ""
        skippedSegmentKeys.clear()
        _activeSegments.value = emptyList()
        _currentPromptSegment.value = null
        _skipNotificationText.value = null
        _skipNotificationCategory.value = null
        _isSkipAnimating.value = false
    }
}
