package com.example.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.model.CaptionOption
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import com.example.model.PlayableStreamOption
import com.example.model.StreamData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UniversalVideoPlayer(
    streamOption: PlayableStreamOption?,
    hlsUrl: String?,
    captionOption: CaptionOption?,
    streamData: StreamData? = null,
    previewItem: com.example.model.VideoItem? = null,
    chapters: List<com.example.extractor.chapters.VideoChapter> = emptyList(),
    providerId: String? = null,
    isPlaying: Boolean = true,
    videoId: String? = null,
    initialPositionMs: Long = 0L,
    availableStreamOptions: List<PlayableStreamOption> = emptyList(),
    onSelectStreamOption: (PlayableStreamOption) -> Unit = {},
    onSelectCaptionOption: (CaptionOption?) -> Unit = {},
    failedSourceLogs: List<com.example.model.FailedSourceLog> = emptyList(),
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onBackClick: (() -> Unit)? = null,
    onNextClick: (() -> Unit)? = null,
    onPreviousClick: (() -> Unit)? = null,
    nextEpisodeData: NextEpisodeData? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
    onSwipeDownDrag: ((dragDeltaY: Float) -> Unit)? = null,
    onSwipeDownEnd: ((accumulatedDy: Float) -> Unit)? = null,
    onOpenRelatedVideos: (() -> Unit)? = null,
    isPortraitExpanded: Boolean = false,
    onTogglePortraitExpanded: (() -> Unit)? = null,
    onPortraitCollapseDrag: ((dragDeltaY: Float) -> Unit)? = null,
    onPortraitCollapseEnd: ((accumulatedDy: Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rawVideoUrl = streamOption?.videoUrl ?: streamOption?.videoStream?.url ?: hlsUrl

    val playbackPrefs = remember(context) { com.example.util.PlaybackPreferences.getInstance(context) }
    val activeStreamData by GlobalPlayerManager.activeStreamData.collectAsState()

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeModeState by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var customAspectRatio by remember { mutableStateOf<Float?>(null) }
    var customAspectRatioLabel by remember { mutableStateOf("Default") }
    var customRatiosList by remember { mutableStateOf(listOf("16:8", "18:9", "21:9")) }
    var showAspectRatioSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showVideoEffectsSheet by remember { mutableStateOf(false) }
    var showAudioEnhancementSheet by remember { mutableStateOf(false) }
    var showSponsorBlockSheet by remember { mutableStateOf(false) }
    var showSpeedSubMenu by remember { mutableStateOf(false) }
    var showQualitySubMenu by remember { mutableStateOf(false) }
    var showAudioTrackSubMenu by remember { mutableStateOf(false) }
    var showAdditionalSettingsSubMenu by remember { mutableStateOf(false) }
    var showLensOverlay by remember { mutableStateOf(false) }

    val isAmbientModeEnabled by playbackPrefs.ambientModeEnabled.collectAsState()
    val isLoopVideoEnabled by GlobalPlayerManager.isLoopEnabled.collectAsState()

    val smartSkipSegments by com.example.smartskip.SmartSkipPlayerEngine.activeSegments.collectAsState()
    val currentPromptSegment by com.example.smartskip.SmartSkipPlayerEngine.currentPromptSegment.collectAsState()
    val skipNotificationText by com.example.smartskip.SmartSkipPlayerEngine.skipNotificationText.collectAsState()
    val skipNotificationCategory by com.example.smartskip.SmartSkipPlayerEngine.skipNotificationCategory.collectAsState()
    val isSkipAnimating by com.example.smartskip.SmartSkipPlayerEngine.isSkipAnimating.collectAsState()

    val audioTracks by GlobalPlayerManager.audioTracks.collectAsState()
    val speedOptions = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f) }
    var isMusicTrackDetected by remember { mutableStateOf(false) }

    val effectiveChapters = remember(chapters, streamData, activeStreamData) {
        if (chapters.isNotEmpty()) {
            chapters
        } else {
            val fromData = streamData?.chapters?.takeIf { it.isNotEmpty() }
                ?: activeStreamData?.chapters?.takeIf { it.isNotEmpty() }
            if (fromData != null) {
                fromData
            } else {
                val desc = streamData?.description ?: activeStreamData?.description ?: ""
                val dur = GlobalPlayerManager.durationMs.value
                com.example.extractor.chapters.YTCustomChapters.extractChapters(desc, dur)
            }
        }
    }

    // Resolve Default Playback Speed
    LaunchedEffect(videoId, rawVideoUrl, activeStreamData) {
        val currentTitle = activeStreamData?.title
        val currentUploader = activeStreamData?.channelName
        val currentDesc = activeStreamData?.description
        val currentTags = activeStreamData?.relatedVideos?.flatMap { it.tags }
        val currentProvider = providerId ?: activeStreamData?.providerId

        val isMusic = com.example.util.PlaybackPreferences.isMusicMedia(
            title = currentTitle,
            uploaderName = currentUploader,
            description = currentDesc,
            tags = currentTags,
            providerId = currentProvider
        )
        isMusicTrackDetected = isMusic

        val targetSpeed = playbackPrefs.getEffectiveSpeed(isMusic)
        playbackSpeed = targetSpeed
        GlobalPlayerManager.setPlaybackSpeed(targetSpeed)

        // Restore video effects for current video
        com.example.effects.VideoEffectsManager.onVideoChanged(videoId ?: activeStreamData?.videoId)
    }

    val torrentEngine = remember(context) { com.example.torrent.engine.TorrentEngine.getInstance(context) }
    val torrentStats by torrentEngine.stats.collectAsState()

    // Gesture Controls State
    val coroutineScope = rememberCoroutineScope()
    var isAutoBrightness by remember { mutableStateOf(true) }
    var brightnessLevel by remember { mutableFloatStateOf(0.7f) }
    var volumeLevel by remember { mutableFloatStateOf(0.7f) }
    var gestureNoticeText by remember { mutableStateOf<String?>(null) }
    var gestureNoticeIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var seekNoticeText by remember { mutableStateOf<String?>(null) }

    // Clean Double-Tap Seek & Play/Pause Feedback States
    var doubleTapSeekDirection by remember { mutableStateOf<String?>(null) }
    var doubleTapAccumulatedSeconds by remember { mutableIntStateOf(0) }
    var doubleTapSeekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var centerPlayPauseFeedback by remember { mutableStateOf<Boolean?>(null) }
    var centerPlayPauseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var activeVerticalGestureType by remember { mutableStateOf<String?>(null) }
    var verticalGestureValue by remember { mutableFloatStateOf(0.7f) }
    var verticalGestureJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var accumulatedDx by remember { mutableFloatStateOf(0f) }
    var accumulatedDy by remember { mutableFloatStateOf(0f) }
    var dragStartPosMs by remember { mutableLongStateOf(0L) }
    var initialBrightness by remember { mutableFloatStateOf(0.7f) }
    var initialVolume by remember { mutableFloatStateOf(0.7f) }
    var isDraggingHorizontally by remember { mutableStateOf(false) }
    var isDraggingVertically by remember { mutableStateOf(false) }
    var showFineScrubbing by remember { mutableStateOf(false) }

    val curPos by GlobalPlayerManager.currentPositionMs.collectAsState()
    val durMs by GlobalPlayerManager.durationMs.collectAsState()

    LaunchedEffect(seekNoticeText) {
        if (seekNoticeText != null) {
            delay(1200)
            seekNoticeText = null
        }
    }

    LaunchedEffect(gestureNoticeText) {
        if (gestureNoticeText != null) {
            delay(1200)
            gestureNoticeText = null
            gestureNoticeIcon = null
        }
    }

    val exoPlayer = remember(context) { GlobalPlayerManager.getExoPlayer(context) }
    val seekSecs = 10

    LaunchedEffect(playbackSpeed) {
        GlobalPlayerManager.setPlaybackSpeed(playbackSpeed)
    }

    // Continuous progress tracking loop
    LaunchedEffect(curPos, durMs) {
        if (durMs > 0 && curPos >= 0) {
            onProgressUpdate(curPos, durMs)
        }
    }

    val globalIsPlaying by GlobalPlayerManager.isPlaying.collectAsState()
    val globalIsBuffering by GlobalPlayerManager.isBuffering.collectAsState()
    val shouldKeepScreenOn = isPlaying || globalIsPlaying || globalIsBuffering
    val localView = androidx.compose.ui.platform.LocalView.current

    DisposableEffect(shouldKeepScreenOn) {
        val window = (context as? android.app.Activity)?.window
            ?: (localView.context as? android.app.Activity)?.window
        if (shouldKeepScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            localView.keepScreenOn = true
        } else if (!globalIsPlaying && !globalIsBuffering) {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            localView.keepScreenOn = false
        }
        onDispose {
            if (!GlobalPlayerManager.isPlaying.value && !GlobalPlayerManager.isBuffering.value) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                localView.keepScreenOn = false
            }
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) GlobalPlayerManager.play() else GlobalPlayerManager.pause()
    }

    LaunchedEffect(streamOption?.videoUrl, streamOption?.videoStream?.url, hlsUrl, captionOption?.languageCode, videoId) {
        val playUrl = streamOption?.videoUrl ?: streamOption?.videoStream?.url ?: hlsUrl
        if (playUrl.isNullOrBlank()) {
            return@LaunchedEffect
        }
        val curPos = GlobalPlayerManager.currentPositionMs.value.coerceAtLeast(0L)
        val resumePos = if (curPos > 0L) {
            curPos
        } else {
            initialPositionMs.takeIf { it > 0L }
                ?: videoId?.let { com.example.util.PlaybackResumeManager.getSavedPosition(context, it) }
                ?: 0L
        }
        GlobalPlayerManager.prepareAndPlay(
            context = context,
            streamData = streamData ?: activeStreamData,
            streamOption = streamOption,
            hlsUrl = hlsUrl,
            captionOption = captionOption,
            initialPos = resumePos
        )
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffsetX by remember { mutableFloatStateOf(0f) }
    var zoomOffsetY by remember { mutableFloatStateOf(0f) }

    val currentPlayerActivity = remember(context) { context.findActivity() }
    DisposableEffect(isLandscape, currentPlayerActivity) {
        val window = currentPlayerActivity?.window
        if (window != null && isLandscape) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.hide(WindowInsetsCompat.Type.captionBar())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        } else {
            onDispose {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentPlayerActivity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = act.window
                val lp = window.attributes
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    val areControlsVisible by GlobalPlayerManager.areControlsVisible.collectAsState()

    val playerContainerModifier = if (isLandscape || isPortraitExpanded) {
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    } else {
        modifier
            .fillMaxWidth()
            .background(Color.Black)
    }

    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = playerContainerModifier
            .pointerInput(isLandscape, isPortraitExpanded, seekSecs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    val startTime = System.currentTimeMillis()
                    val touchSlop = viewConfiguration.touchSlop
                    var hasPassedSlop = false
                    var isSwipingDownToMinimize = false
                    var isSwipingUpForLandscapeRelated = false
                    var isSwipingUpToCollapsePortrait = false

                    accumulatedDx = 0f
                    accumulatedDy = 0f
                    dragStartPosMs = GlobalPlayerManager.currentPositionMs.value
                    val currentSysBri = getSystemBrightness(context)
                    initialBrightness = if (isAutoBrightness) currentSysBri else brightnessLevel
                    initialVolume = volumeLevel
                    isDraggingHorizontally = false
                    isDraggingVertically = false

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val currentPos = change.position
                        val dx = currentPos.x - startPos.x
                        val dy = currentPos.y - startPos.y
                        val distSq = dx * dx + dy * dy

                        if (!hasPassedSlop && distSq > touchSlop * touchSlop) {
                            hasPassedSlop = true
                        }

                        if (hasPassedSlop) {
                            change.consume()
                            val dragAmountX = currentPos.x - (startPos.x + accumulatedDx)
                            val dragAmountY = currentPos.y - (startPos.y + accumulatedDy)
                            accumulatedDx += dragAmountX
                            accumulatedDy += dragAmountY
                            val absDx = kotlin.math.abs(accumulatedDx)
                            val absDy = kotlin.math.abs(accumulatedDy)

                            // Swipe down to minimize in portrait mode (only when NOT expanded)
                            if (!isLandscape && !isPortraitExpanded && !isDraggingHorizontally && (onSwipeDownDrag != null || onBackClick != null)) {
                                if (isSwipingDownToMinimize || (accumulatedDy > 8f && accumulatedDy > absDx * 1.1f)) {
                                    isSwipingDownToMinimize = true
                                    onSwipeDownDrag?.invoke(dragAmountY)
                                    continue
                                }
                            }

                            // Swipe up in portrait expanded mode to collapse back to standard 16:9 view
                            if (isPortraitExpanded && !isDraggingHorizontally) {
                                val isSwipeUpIntent = accumulatedDy < -8f && absDy > absDx * 1.1f
                                if (isSwipingUpToCollapsePortrait || isSwipeUpIntent) {
                                    isSwipingUpToCollapsePortrait = true
                                    onPortraitCollapseDrag?.invoke(dragAmountY)
                                    continue
                                }
                            }

                            // Swipe up in landscape mode for related videos
                            if (isLandscape && onOpenRelatedVideos != null && !isDraggingHorizontally) {
                                val totalWidth = size.width.toFloat().coerceAtLeast(100f)
                                val isCenterZone = startPos.x >= totalWidth * 0.35f && startPos.x <= totalWidth * 0.65f
                                val isSwipeUpIntent = accumulatedDy < -16f && absDy > absDx * 1.1f
                                if (isSwipingUpForLandscapeRelated || (isSwipeUpIntent && (areControlsVisible || isCenterZone))) {
                                    isSwipingUpForLandscapeRelated = true
                                    continue
                                }
                            }

                            if (!isDraggingHorizontally && !isDraggingVertically && !isSwipingUpForLandscapeRelated) {
                                if (absDx > 12f && absDx > absDy) {
                                    isDraggingHorizontally = true
                                } else if (absDy > 12f && absDy > absDx) {
                                    isDraggingVertically = true
                                }
                            }

                            if (isDraggingHorizontally) {
                                val totalWidth = size.width.toFloat().coerceAtLeast(100f)
                                val durationMs = GlobalPlayerManager.durationMs.value.coerceAtLeast(1L)
                                val maxSweepSecs = (durationMs / 1000L * 0.20f).coerceIn(30f, 180f)
                                val deltaSecs = ((accumulatedDx / totalWidth) * maxSweepSecs).toLong()
                                val targetPos = (dragStartPosMs + (deltaSecs * 1000L)).coerceIn(0L, durationMs)

                                GlobalPlayerManager.seekTo(targetPos)
                                val sign = if (deltaSecs >= 0) "+" else ""
                                gestureNoticeText = "$sign${deltaSecs}s (${formatVideoTimestamp(targetPos)} / ${formatVideoTimestamp(durationMs)})"
                                gestureNoticeIcon = if (deltaSecs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind
                            } else if (isDraggingVertically && !isSwipingUpForLandscapeRelated) {
                                // Volume and Brightness gestures only active when player controls are faded away
                                if (!areControlsVisible) {
                                    val totalHeight = size.height.toFloat().coerceAtLeast(100f)
                                    val isLeftZone = startPos.x < size.width * 0.35f
                                    val isRightZone = startPos.x > size.width * 0.65f

                                    if (isLeftZone) {
                                        activeVerticalGestureType = "BRIGHTNESS"
                                        val delta = -accumulatedDy / totalHeight
                                        val rawBrightness = initialBrightness + delta
                                        val activity = context as? Activity
                                            ?: (context as? ContextWrapper)?.baseContext as? Activity

                                        if (isAutoBrightness) {
                                            // When in auto brightness, swiping up past threshold switches to manual brightness
                                            if (delta > 0.05f) {
                                                isAutoBrightness = false
                                                brightnessLevel = (initialBrightness + delta).coerceIn(0.01f, 1.0f)
                                                verticalGestureValue = brightnessLevel
                                                activity?.let { act ->
                                                    val lp = act.window.attributes
                                                    lp.screenBrightness = brightnessLevel
                                                    act.window.attributes = lp
                                                }
                                            } else {
                                                // Kept swiping down or holding -> stays in auto brightness
                                                isAutoBrightness = true
                                                verticalGestureValue = 0f
                                                activity?.let { act ->
                                                    val lp = act.window.attributes
                                                    lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                                    act.window.attributes = lp
                                                }
                                            }
                                        } else {
                                            // When in manual brightness, dragging down all the way snaps to Auto brightness
                                            if (rawBrightness <= 0.02f) {
                                                isAutoBrightness = true
                                                verticalGestureValue = 0f
                                                activity?.let { act ->
                                                    val lp = act.window.attributes
                                                    lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                                    act.window.attributes = lp
                                                }
                                            } else {
                                                isAutoBrightness = false
                                                brightnessLevel = rawBrightness.coerceIn(0.01f, 1.0f)
                                                verticalGestureValue = brightnessLevel
                                                activity?.let { act ->
                                                    val lp = act.window.attributes
                                                    lp.screenBrightness = brightnessLevel
                                                    act.window.attributes = lp
                                                }
                                            }
                                        }
                                    } else if (isRightZone) {
                                        activeVerticalGestureType = "VOLUME"
                                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                                        if (audioManager != null) {
                                            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                            val delta = -accumulatedDy / totalHeight
                                            volumeLevel = (initialVolume + delta).coerceIn(0f, 1f)
                                            verticalGestureValue = volumeLevel
                                            val targetVol = (volumeLevel * maxVol).toInt()
                                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                                        }
                                    }

                                    verticalGestureJob?.cancel()
                                    verticalGestureJob = coroutineScope.launch {
                                        delay(900)
                                        activeVerticalGestureType = null
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (hasPassedSlop) {
                        if (isSwipingUpToCollapsePortrait) {
                            if (onPortraitCollapseEnd != null) {
                                onPortraitCollapseEnd.invoke(accumulatedDy)
                            } else if (accumulatedDy < -30f) {
                                onTogglePortraitExpanded?.invoke() ?: onBackClick?.invoke()
                            }
                            isSwipingUpToCollapsePortrait = false
                        }
                        if (isSwipingUpForLandscapeRelated) {
                            if (accumulatedDy < -30f) {
                                onOpenRelatedVideos?.invoke()
                            }
                            isSwipingUpForLandscapeRelated = false
                        }
                        if (isSwipingDownToMinimize) {
                            if (onSwipeDownEnd != null) {
                                onSwipeDownEnd.invoke(accumulatedDy)
                            } else {
                                onBackClick?.invoke()
                            }
                            isSwipingDownToMinimize = false
                        }
                        isDraggingHorizontally = false
                        isDraggingVertically = false
                        verticalGestureJob?.cancel()
                        verticalGestureJob = coroutineScope.launch {
                            delay(400)
                            activeVerticalGestureType = null
                        }
                    } else {
                        val upTime = System.currentTimeMillis()
                        val duration = upTime - startTime
                        if (duration < 400) {
                            val timeSinceLastTap = upTime - lastTapTimestamp
                            val tapDistSq = (startPos.x - lastTapPosition.x) * (startPos.x - lastTapPosition.x) + (startPos.y - lastTapPosition.y) * (startPos.y - lastTapPosition.y)
                            if (timeSinceLastTap < 350 && tapDistSq < touchSlop * touchSlop * 4) {
                                // DOUBLE TAP
                                lastTapTimestamp = 0L
                                val totalWidth = size.width
                                val leftBoundary = totalWidth * 0.35f
                                val rightBoundary = totalWidth * 0.65f
                                val stepSecs = seekSecs

                                if (startPos.x < leftBoundary) {
                                    val newSeconds = if (doubleTapSeekDirection == "LEFT") {
                                        doubleTapAccumulatedSeconds + stepSecs
                                    } else {
                                        stepSecs
                                    }
                                    doubleTapSeekDirection = "LEFT"
                                    doubleTapAccumulatedSeconds = newSeconds

                                    val seekMs = stepSecs * 1000L
                                    val targetPos = (exoPlayer.currentPosition - seekMs).coerceAtLeast(0L)
                                    GlobalPlayerManager.seekTo(targetPos)

                                    doubleTapSeekJob?.cancel()
                                    doubleTapSeekJob = coroutineScope.launch {
                                        delay(650)
                                        doubleTapSeekDirection = null
                                        doubleTapAccumulatedSeconds = 0
                                    }
                                } else if (startPos.x > rightBoundary) {
                                    val newSeconds = if (doubleTapSeekDirection == "RIGHT") {
                                        doubleTapAccumulatedSeconds + stepSecs
                                    } else {
                                        stepSecs
                                    }
                                    doubleTapSeekDirection = "RIGHT"
                                    doubleTapAccumulatedSeconds = newSeconds

                                    val seekMs = stepSecs * 1000L
                                    val targetPos = exoPlayer.currentPosition + seekMs
                                    GlobalPlayerManager.seekTo(targetPos)

                                    doubleTapSeekJob?.cancel()
                                    doubleTapSeekJob = coroutineScope.launch {
                                        delay(650)
                                        doubleTapSeekDirection = null
                                        doubleTapAccumulatedSeconds = 0
                                    }
                                } else {
                                    val isCurrentlyPlaying = GlobalPlayerManager.isPlaying.value
                                    if (isCurrentlyPlaying) {
                                        GlobalPlayerManager.pause()
                                        centerPlayPauseFeedback = false
                                    } else {
                                        GlobalPlayerManager.play()
                                        centerPlayPauseFeedback = true
                                    }

                                    centerPlayPauseJob?.cancel()
                                    centerPlayPauseJob = coroutineScope.launch {
                                        delay(600)
                                        centerPlayPauseFeedback = null
                                    }
                                }
                            } else {
                                // SINGLE TAP -> Immediately toggle controls visibility
                                lastTapTimestamp = upTime
                                lastTapPosition = startPos
                                GlobalPlayerManager.toggleControlsVisibility()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val currentPlayerContext = LocalContext.current
        val isBuffering by GlobalPlayerManager.isBuffering.collectAsState()
        val firstFrameRendered by GlobalPlayerManager.firstFrameRendered.collectAsState()
        val playerError by GlobalPlayerManager.playerError.collectAsState()

        val showLoadingIndicator = isBuffering || (!firstFrameRendered && playerError == null)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = zoomOffsetX,
                    translationY = zoomOffsetY
                )
        ) {
            val hostModifier = if (customAspectRatio != null) {
                Modifier
                    .fillMaxSize()
                    .aspectRatio(customAspectRatio!!)
                    .align(Alignment.Center)
            } else {
                Modifier.fillMaxSize()
            }
            PersistentPlayerHost(
                useController = false,
                resizeMode = if (customAspectRatio != null) AspectRatioFrameLayout.RESIZE_MODE_FILL else resizeModeState,
                onFullscreenClick = {
                    toggleFullscreen(currentPlayerContext)
                },
                modifier = hostModifier
            )

            // YouTube-style seamless thumbnail cover:
            // Displayed while buffering / before first frame renders so the player is NEVER a black box!
            val currentStream = streamData ?: activeStreamData
            val effectiveThumbnailUrl = currentStream?.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: previewItem?.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: (if (videoId != null && videoId.length == 11 && !videoId.startsWith("http")) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null)

            val isPlayingCenter by GlobalPlayerManager.isPlaying.collectAsState()
            val shouldShowThumbnailCover = !firstFrameRendered && playerError == null
            val thumbnailAlpha by animateFloatAsState(
                targetValue = if (shouldShowThumbnailCover && !effectiveThumbnailUrl.isNullOrBlank()) 1f else 0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "youtube_thumbnail_crossfade"
            )

            if (thumbnailAlpha > 0f && !effectiveThumbnailUrl.isNullOrBlank()) {
                Box(
                    modifier = hostModifier
                        .graphicsLayer { alpha = thumbnailAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(currentPlayerContext)
                            .data(effectiveThumbnailUrl)
                            .crossfade(false)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Subtle scrim for perfect white spinner contrast against light thumbnails
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.20f))
                    )
                }
            }

            // Real-time GPU Video Effects Overlay
            val videoEffectsConfig by com.example.effects.VideoEffectsManager.currentConfig.collectAsState()
            VideoEffectsOverlay(
                config = videoEffectsConfig,
                modifier = Modifier.fillMaxSize()
            )

            // YouTube Lightweight White Buffering Spinner Overlay
            if (showLoadingIndicator && playerError == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    YouTubeBufferingIndicator(
                        size = 38.dp,
                        strokeWidth = 2.8.dp
                    )
                }
            }

            // Error Overlay if playerError != null
            if (playerError != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Playback Failed",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playerError ?: "Unable to play stream",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                GlobalPlayerManager.prepareAndPlay(
                                    context = context,
                                    streamData = streamData ?: activeStreamData,
                                    streamOption = streamOption,
                                    hlsUrl = hlsUrl,
                                    captionOption = captionOption,
                                    initialPos = GlobalPlayerManager.currentPositionMs.value
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("Retry Playback", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        val availableOptions = (streamData ?: activeStreamData)?.availableStreamOptions ?: emptyList()
                        val webPlayerOpt = availableOptions.firstOrNull { it.format.equals("embed", true) || it.sourceName.contains("Embed", true) || it.videoUrl?.contains("/embed/") == true }
                        if (webPlayerOpt != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onSelectStreamOption(webPlayerOpt)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAD1457))
                            ) {
                                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play in Web Player", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Real-Time Subtitles & AI Live Captions Overlay
            SubtitleOverlay()

            // Top Header (Title + Actions Toolbar)
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)
            ) {
                val rawTitle = activeStreamData?.title?.takeIf { it.isNotBlank() }
                val currentDisplayTitle = if (!rawTitle.isNullOrBlank()) {
                    rawTitle
                } else {
                    val qLabel = streamOption?.qualityLabel
                    if (!qLabel.isNullOrBlank() && !qLabel.contains("http") && qLabel != "Direct Video Stream") {
                        qLabel
                    } else {
                        "Playing Video"
                    }
                }
                val currentUploader = activeStreamData?.channelName?.takeIf { it.isNotBlank() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left-side minimize button (Clean YouTube style: no overlapping title text)
                    IconButton(
                        onClick = {
                            if (onBackClick != null) {
                                onBackClick.invoke()
                            } else {
                                (currentPlayerContext as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                                    ?: (currentPlayerContext as? Activity)?.finish()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize Player",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Right-side actions (ONLY Speed, Caption, Settings - clean YouTube style, no background circles)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playback Speed Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    GlobalPlayerManager.showControls()
                                    showSpeedSubMenu = true
                                    showSettingsSheet = true
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (playbackSpeed == 1.0f) "1.0x" else "${playbackSpeed}x",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Subtitles & AI Captions CC Button
                        val subMode by GlobalPlayerManager.subtitleMode.collectAsState()
                        IconButton(
                            onClick = {
                                GlobalPlayerManager.showControls()
                                showSubtitleSheet = true
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Subtitles & AI Live Captions",
                                tint = if (subMode != GlobalPlayerManager.SubtitleMode.OFF) Color(0xFF00E5FF) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // YouTube Video Chapters Button
                        if (effectiveChapters.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    GlobalPlayerManager.showControls()
                                    showChaptersSheet = true
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewList,
                                    contentDescription = "Video Chapters",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Google Lens / Visual Search & Text Copy Button
                        IconButton(
                            onClick = {
                                GlobalPlayerManager.pause()
                                showLensOverlay = true
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Google Lens & Circle to Search",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Settings Gear Icon
                        IconButton(
                            onClick = {
                                GlobalPlayerManager.showControls()
                                showSpeedSubMenu = false
                                showQualitySubMenu = false
                                showAudioTrackSubMenu = false
                                showAdditionalSettingsSubMenu = false
                                showSettingsSheet = true
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Player Settings",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // YouTube-Style Center Controls (Previous, Play/Pause, Next)
            AnimatedVisibility(
                visible = areControlsVisible && !showLoadingIndicator,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val isCurrentlyPlayingCenter by GlobalPlayerManager.isPlaying.collectAsState()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(44.dp)
                    ) {
                        // Previous Video / Rewind Button
                        IconButton(
                            onClick = {
                                GlobalPlayerManager.showControls()
                                if (onPreviousClick != null) {
                                    onPreviousClick.invoke()
                                } else {
                                    val curMs = GlobalPlayerManager.currentPositionMs.value
                                    GlobalPlayerManager.seekTo((curMs - 10000L).coerceAtLeast(0L))
                                }
                            },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Video",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Center Big Play/Pause Button
                        IconButton(
                            onClick = {
                                GlobalPlayerManager.showControls()
                                if (isCurrentlyPlayingCenter) {
                                    GlobalPlayerManager.pause()
                                } else {
                                    GlobalPlayerManager.play()
                                }
                            },
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                imageVector = if (isCurrentlyPlayingCenter) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isCurrentlyPlayingCenter) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(54.dp)
                            )
                        }

                        // Next Video / Forward Button
                        IconButton(
                            onClick = {
                                GlobalPlayerManager.showControls()
                                if (onNextClick != null) {
                                    onNextClick.invoke()
                                } else {
                                    val curMs = GlobalPlayerManager.currentPositionMs.value
                                    GlobalPlayerManager.seekTo(curMs + 10000L)
                                }
                            },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Video",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }


                }
            }

            // Bottom YouTube-Style Section (Duration & Quality ABOVE Progress Bar, Progress Bar perfectly anchored at bottom edge)
            val currentPosMs by GlobalPlayerManager.currentPositionMs.collectAsState()
            val totalDurMs by GlobalPlayerManager.durationMs.collectAsState()
            val bufferedPosMs by GlobalPlayerManager.bufferedPositionMs.collectAsState()

            val candidateFrames = remember(currentStream, previewItem) {
                val direct = currentStream?.previewThumbnails?.takeIf { it.isNotEmpty() }
                    ?: previewItem?.previewThumbnails?.takeIf { it.isNotEmpty() }
                if (!direct.isNullOrEmpty()) {
                    direct
                } else if (previewItem != null) {
                    com.example.util.PreviewFrameResolver.resolvePreviewFrames(previewItem)
                } else if (currentStream != null) {
                    val dummy = com.example.model.VideoItem(
                        id = currentStream.videoId,
                        title = currentStream.title,
                        uploaderName = currentStream.channelName,
                        thumbnailUrl = currentStream.thumbnailUrl,
                        providerId = currentStream.providerId,
                        previewThumbnails = currentStream.previewThumbnails
                    )
                    com.example.util.PreviewFrameResolver.resolvePreviewFrames(dummy)
                } else emptyList()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                // Background Gradient Scrim (fades in when controls are visible)
                AnimatedVisibility(
                    visible = areControlsVisible,
                    enter = fadeIn(androidx.compose.animation.core.tween(180)),
                    exit = fadeOut(androidx.compose.animation.core.tween(180)),
                    modifier = Modifier.matchParentSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.50f),
                                        Color.Black.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. YouTube Duration & Quality ROW (Placed directly above the progress bar, smoothly fades)
                    AnimatedVisibility(
                        visible = areControlsVisible,
                        enter = fadeIn(androidx.compose.animation.core.tween(180)),
                        exit = fadeOut(androidx.compose.animation.core.tween(180)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 10.dp, bottom = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Current time / Total duration + Quality Chip + Chapter
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isLive = totalDurMs <= 0L && (activeStreamData?.hlsUrl != null || activeStreamData?.videoUrl?.contains("m3u8") == true)
                                if (isLive) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "${formatVideoTimestamp(currentPosMs)} / ${formatVideoTimestamp(totalDurMs)}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }

                                // Quick Quality Chip on top of progress bar
                                val qLabel = streamOption?.qualityLabel?.takeIf { it.isNotBlank() && it != "Direct Video Stream" } ?: "Auto"
                                Surface(
                                    onClick = {
                                        GlobalPlayerManager.showControls()
                                        showQualitySubMenu = true
                                        showSettingsSheet = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.16f),
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = qLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Current Chapter Name (if present)
                                val currentChapter = remember(currentPosMs, effectiveChapters) {
                                    effectiveChapters.lastOrNull { currentPosMs >= it.startTimeMs }
                                }
                                if (currentChapter != null) {
                                    Surface(
                                        onClick = {
                                            GlobalPlayerManager.showControls()
                                            showChaptersSheet = true
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.16f),
                                        contentColor = Color.White
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "• ${currentChapter.title}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 130.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Right: Fullscreen Toggle Button (Clean YouTube style)
                            IconButton(
                                onClick = {
                                    GlobalPlayerManager.showControls()
                                    if (isPortraitExpanded) {
                                        onTogglePortraitExpanded?.invoke() ?: onBackClick?.invoke()
                                    } else {
                                        toggleFullscreen(currentPlayerContext)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLandscape || isPortraitExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // 2. YouTube Precise Progress Bar (Stationary, anchored to bottom with 0 vertical movement)
                    YouTubePreciseSeekBar(
                        currentPositionMs = currentPosMs,
                        durationMs = totalDurMs,
                        bufferedPositionMs = bufferedPosMs,
                        isControlsVisible = areControlsVisible,
                        segments = smartSkipSegments,
                        chapters = effectiveChapters,
                        heatmap = currentStream?.heatmap,
                        isLandscape = isLandscape,
                        previewFrames = candidateFrames,
                        fallbackThumbnailUrl = currentStream?.thumbnailUrl ?: previewItem?.thumbnailUrl,
                        activeColor = Color(0xFFFFD600),
                        thumbColor = Color(0xFFFFD600),
                        onSlideUpForFineScrubbing = {
                            showFineScrubbing = true
                        },
                        onSeekStarted = { GlobalPlayerManager.showControls() },
                        onSeekScrubbing = { scrubMs ->
                            GlobalPlayerManager.seekTo(scrubMs)
                        },
                        onSeekFinished = { targetMs ->
                            GlobalPlayerManager.seekTo(targetMs)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

        // YouTube Style Settings Modal Bottom Sheet
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showSettingsSheet = false
                    showSpeedSubMenu = false
                    showQualitySubMenu = false
                    showAudioTrackSubMenu = false
                    showAdditionalSettingsSubMenu = false
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 32.dp, top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                showQualitySubMenu -> "Video Quality"
                                showSpeedSubMenu -> "Playback Speed"
                                showAudioTrackSubMenu -> "Audio Track & Language"
                                showAdditionalSettingsSubMenu -> "Additional settings"
                                else -> "Settings"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (showSpeedSubMenu || showQualitySubMenu || showAudioTrackSubMenu || showAdditionalSettingsSubMenu) {
                            TextButton(onClick = {
                                showSpeedSubMenu = false
                                showQualitySubMenu = false
                                showAudioTrackSubMenu = false
                                showAdditionalSettingsSubMenu = false
                            }) {
                                Text("Back", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    if (showQualitySubMenu) {
                        if (availableStreamOptions.isEmpty()) {
                            Text(
                                text = "Auto (Default quality stream)",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)
                            )
                        } else {
                            availableStreamOptions.forEach { option ->
                                val isSelected = (streamOption?.qualityLabel == option.qualityLabel || streamOption?.videoUrl == option.videoUrl)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSelectStreamOption(option)
                                            Toast.makeText(context, "Quality set to ${option.qualityLabel}", Toast.LENGTH_SHORT).show()
                                            showQualitySubMenu = false
                                            showSettingsSheet = false
                                        }
                                        .padding(vertical = 14.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = option.qualityLabel,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (option.format.isNotBlank()) {
                                            Text(
                                                text = option.format,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (showSpeedSubMenu) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isMusicTrackDetected && playbackPrefs.disableSpeedForMusic.collectAsState().value) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🎵 Music video detected — Automatically playing at 1.0× speed",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            // YouTube Interactive Fine-Tuning Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val newSpeed = (playbackSpeed - 0.05f).coerceAtLeast(0.25f)
                                                val rounded = Math.round(newSpeed * 100f) / 100f
                                                playbackSpeed = rounded
                                                GlobalPlayerManager.setPlaybackSpeed(rounded)
                                                playbackPrefs.setDefaultSpeed(rounded)
                                                Toast.makeText(context, "Changed default playback speed to ${String.format("%.2f", rounded)}x", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Decrease speed",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Text(
                                            text = "${String.format("%.2f", playbackSpeed)}x",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        IconButton(
                                            onClick = {
                                                val newSpeed = (playbackSpeed + 0.05f).coerceAtMost(5.0f)
                                                val rounded = Math.round(newSpeed * 100f) / 100f
                                                playbackSpeed = rounded
                                                GlobalPlayerManager.setPlaybackSpeed(rounded)
                                                playbackPrefs.setDefaultSpeed(rounded)
                                                Toast.makeText(context, "Changed default playback speed to ${String.format("%.2f", rounded)}x", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Increase speed",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // Horizontal Speed Pills
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 4.0f).forEach { speed ->
                                            val isSelected = (Math.abs(playbackSpeed - speed) < 0.03f)
                                            val label = if (speed == 1.0f) "1.0 Normal" else if (speed % 1.0f == 0f) "${speed.toInt()}" else "$speed"
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    playbackSpeed = speed
                                                    GlobalPlayerManager.setPlaybackSpeed(speed)
                                                    playbackPrefs.setDefaultSpeed(speed)
                                                    Toast.makeText(context, "Changed default playback speed to ${if (speed == 1.0f) "1.0" else "$speed"}x", Toast.LENGTH_SHORT).show()
                                                },
                                                label = { Text(label) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Preset Choices List
                            speedOptions.take(8).forEach { speed ->
                                val isSelected = (Math.abs(playbackSpeed - speed) < 0.03f)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            playbackSpeed = speed
                                            GlobalPlayerManager.setPlaybackSpeed(speed)
                                            playbackPrefs.setDefaultSpeed(speed)
                                            Toast.makeText(context, "Changed default playback speed to ${if (speed == 1.0f) "1.0" else "$speed"}x", Toast.LENGTH_SHORT).show()
                                            showSpeedSubMenu = false
                                            showSettingsSheet = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (speed == 1.0f) "Normal (1.0x)" else "${speed}x",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (showAudioTrackSubMenu) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Detected Audio Streams & Dubs",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )

                            if (audioTracks.isNotEmpty()) {
                                audioTracks.forEach { trackOpt ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                GlobalPlayerManager.selectAudioTrack(trackOpt)
                                                Toast.makeText(context, "Audio set to: ${trackOpt.label}", Toast.LENGTH_SHORT).show()
                                                showAudioTrackSubMenu = false
                                                showSettingsSheet = false
                                            }
                                            .padding(vertical = 12.dp, horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = trackOpt.label,
                                                fontWeight = if (trackOpt.isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (trackOpt.isSelected) MaterialTheme.colorScheme.primary else Color.White
                                            )
                                            if (trackOpt.channelInfo.isNotBlank()) {
                                                Text(
                                                    text = trackOpt.channelInfo,
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                        if (trackOpt.isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Standard Dual-Audio / Primary Audio Track",
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "Force Preferred Audio Language",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )

                            val langOptions = listOf(
                                "auto" to "Auto (Media Default)",
                                "jpn" to "Japanese (日本語 - Orig / Dub)",
                                "eng" to "English (English Dub)",
                                "hin" to "Hindi (हिंदी)",
                                "spa" to "Spanish (Español)",
                                "fre" to "French (Français)",
                                "ger" to "German (Deutsch)",
                                "chi" to "Chinese (中文)",
                                "kor" to "Korean (한국어)"
                            )

                            langOptions.forEach { (code, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            GlobalPlayerManager.setPreferredAudioLanguage(code)
                                            Toast.makeText(context, "Preferred audio language: $label", Toast.LENGTH_SHORT).show()
                                            showAudioTrackSubMenu = false
                                            showSettingsSheet = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else if (showAdditionalSettingsSubMenu) {
                        // Additional Settings Sub-menu (Ambient Mode, Loop Video, etc.)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            // 1. Ambient Mode Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        playbackPrefs.setAmbientModeEnabled(!isAmbientModeEnabled)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isAmbientModeEnabled) Color(0xFFFF9800).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WbIncandescent,
                                            contentDescription = "Ambient mode",
                                            tint = if (isAmbientModeEnabled) Color(0xFFFFB74D) else Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Ambient mode",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Subtly casts lighting effect matching video colors around the player",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = isAmbientModeEnabled,
                                    onCheckedChange = {
                                        playbackPrefs.setAmbientModeEnabled(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFFFF9800),
                                        uncheckedThumbColor = Color.LightGray,
                                        uncheckedTrackColor = Color.DarkGray
                                    )
                                )
                            }

                            // 2. Loop Video Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        GlobalPlayerManager.setLoopVideo(!isLoopVideoEnabled, context)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isLoopVideoEnabled) Color(0xFF2196F3).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Repeat,
                                            contentDescription = "Loop video",
                                            tint = if (isLoopVideoEnabled) Color(0xFF64B5F6) else Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Loop video",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Continuously repeat playback of the current video",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = isLoopVideoEnabled,
                                    onCheckedChange = {
                                        GlobalPlayerManager.setLoopVideo(it, context)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF2196F3),
                                        uncheckedThumbColor = Color.LightGray,
                                        uncheckedTrackColor = Color.DarkGray
                                    )
                                )
                            }

                            // 3. Battery Saver Quick Toggle
                            val batterySaverManager = remember(context) { com.example.util.BatterySaverManager.getInstance(context) }
                            val isBatterySaverActive by batterySaverManager.isPowerSaveActive.collectAsState()
                            val batterySaverManual by batterySaverManager.manualEnabled.collectAsState()
                            val batteryLevel by batterySaverManager.batteryLevel.collectAsState()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        batterySaverManager.setManualEnabled(!batterySaverManual)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isBatterySaverActive) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = "Battery Saver",
                                            tint = if (isBatterySaverActive) Color(0xFF81C784) else Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Battery Saver Mode",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (isBatterySaverActive) "Active ($batteryLevel%) • Limiting GPU & network drain" else "Optimizes decoding, caps resolution & disables glow",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isBatterySaverActive) Color(0xFF81C784) else Color.LightGray.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = batterySaverManual,
                                    onCheckedChange = {
                                        batterySaverManager.setManualEnabled(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF4CAF50),
                                        uncheckedThumbColor = Color.LightGray,
                                        uncheckedTrackColor = Color.DarkGray
                                    )
                                )
                            }
                        }
                    } else {
                        // Main Settings Items
                        // 0. Quality Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showQualitySubMenu = true }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "Quality",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Quality",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = streamOption?.qualityLabel ?: "Auto",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 1. Playback Speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showSpeedSubMenu = true }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Playback Speed",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (playbackSpeed == 1.0f) "Normal" else "${playbackSpeed}x",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 2. Audio Track & Language Selection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAudioTrackSubMenu = true }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Audio Track & Language",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Audio Track & Language",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            val activeTrackLabel = audioTracks.find { it.isSelected }?.displayLanguage ?: "Auto"
                            Text(
                                text = activeTrackLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 3. Aspect Ratio Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showSettingsSheet = false
                                    showAspectRatioSheet = true
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Aspect Ratio",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (customAspectRatio != null) customAspectRatioLabel else when (resizeModeState) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
                                    else -> "Fit"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 4. Subtitles & AI Live Captions
                        val currentSubMode by GlobalPlayerManager.subtitleMode.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showSettingsSheet = false
                                    showSubtitleSheet = true
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaption,
                                    contentDescription = "Subtitles",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Subtitles & AI Live Captions",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = when (currentSubMode) {
                                    GlobalPlayerManager.SubtitleMode.OFF -> "Off"
                                    GlobalPlayerManager.SubtitleMode.EXTERNAL_PROVIDER -> "External Subtitle"
                                    GlobalPlayerManager.SubtitleMode.BILIBILI_TRANSLATED -> "Bilibili Translated"
                                    GlobalPlayerManager.SubtitleMode.BILIBILI_ORIGINAL -> "Bilibili Original"
                                    GlobalPlayerManager.SubtitleMode.AI_LIVE_CAPTIONS -> "Whisper AI Live"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 5. Video Effects & Filters
                        val videoEffectsConfig by com.example.effects.VideoEffectsManager.currentConfig.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showSettingsSheet = false
                                    showVideoEffectsSheet = true
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Video Effects & Filters",
                                    tint = if (videoEffectsConfig.isEnabled) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Video Effects & Filters",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (!videoEffectsConfig.isEnabled) "Off"
                                else if (videoEffectsConfig.selectedPreset != com.example.effects.PresetFilter.NONE) videoEffectsConfig.selectedPreset.displayName
                                else "Active",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (videoEffectsConfig.isEnabled) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (videoEffectsConfig.isEnabled) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        // 6. Audio Enhancement
                        val audioEnhancementConfig by com.example.ui.player.audio.AudioEnhancementEngine.config.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showSettingsSheet = false
                                    showAudioEnhancementSheet = true
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Audio Enhancement",
                                    tint = if (audioEnhancementConfig.isEnabled) Color(0xFFB388FF) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Audio Enhancement",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (!audioEnhancementConfig.isEnabled) "Off"
                                else audioEnhancementConfig.selectedPreset.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (audioEnhancementConfig.isEnabled) Color(0xFFB388FF) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (audioEnhancementConfig.isEnabled) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        // 6. Smart Skip / SponsorBlock
                        val smartSkipPrefs = remember(context) { com.example.smartskip.SmartSkipPreferences.getInstance(context) }
                        val isSmartSkipOn by smartSkipPrefs.isSmartSkipEnabled.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showSettingsSheet = false
                                    showSponsorBlockSheet = true
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "Smart Skip / SponsorBlock",
                                    tint = if (isSmartSkipOn) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Smart Skip / SponsorBlock",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (isSmartSkipOn) "Enabled (${smartSkipSegments.size} segments)" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSmartSkipOn) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSmartSkipOn) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        // 6.5 YouTube Video Chapters
                        if (effectiveChapters.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        showSettingsSheet = false
                                        showChaptersSheet = true
                                    }
                                    .padding(vertical = 14.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ViewList,
                                        contentDescription = "Chapters",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Chapters",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "${effectiveChapters.size} chapters",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 7. Additional Settings (Ambient Mode, Loop, etc.)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showAdditionalSettingsSubMenu = true
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Additional settings",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Additional settings",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Ambient (${if (isAmbientModeEnabled) "On" else "Off"}) • Loop (${if (isLoopVideoEnabled) "On" else "Off"})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Normal
                            )
                        }

                        // 8. Google Lens & Circle-to-Search
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showSettingsSheet = false
                                    GlobalPlayerManager.pause()
                                    showLensOverlay = true
                                }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Circle to Search & Google Lens",
                                    tint = Color(0xFF4285F4)
                                )
                                Text(
                                    text = "Circle to Search & Google Lens",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Extract Text & Search",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (showAspectRatioSheet) {
            AspectRatioSettingsSheet(
                currentCustomRatio = customAspectRatio,
                currentRatioLabel = customAspectRatioLabel,
                customRatiosList = customRatiosList,
                onSelectRatio = { ratio, label ->
                    customAspectRatio = ratio
                    customAspectRatioLabel = label
                    if (ratio == null) {
                        resizeModeState = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        resizeModeState = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                    Toast.makeText(context, "Aspect Ratio set to $label", Toast.LENGTH_SHORT).show()
                },
                onAddCustomRatio = { newRatio ->
                    if (!customRatiosList.contains(newRatio)) {
                        customRatiosList = customRatiosList + newRatio
                    }
                },
                onRemoveCustomRatio = { ratioToRemove ->
                    customRatiosList = customRatiosList - ratioToRemove
                    if (customAspectRatioLabel == ratioToRemove) {
                        customAspectRatio = null
                        customAspectRatioLabel = "Default"
                        resizeModeState = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onDismiss = { showAspectRatioSheet = false }
            )
        }

        // Dedicated YouTube Chapters Sheet
        if (showChaptersSheet && effectiveChapters.isNotEmpty()) {
            val currentPosMsForSheet by GlobalPlayerManager.currentPositionMs.collectAsState()
            val totalDurMsForSheet by GlobalPlayerManager.durationMs.collectAsState()

            com.example.ui.components.YouTubeChaptersSheet(
                chapters = effectiveChapters,
                currentPositionMs = currentPosMsForSheet,
                videoDurationMs = totalDurMsForSheet,
                videoThumbnailUrl = streamData?.thumbnailUrl ?: activeStreamData?.thumbnailUrl,
                videoTitle = streamData?.title ?: activeStreamData?.title ?: "",
                onSeekTo = { targetMs ->
                    GlobalPlayerManager.seekTo(targetMs)
                    showChaptersSheet = false
                },
                onDismiss = { showChaptersSheet = false }
            )
        }

        if (showSubtitleSheet) {
            SubtitleSettingsSheet(
                availableCaptions = streamData?.captionOptions ?: emptyList(),
                selectedCaption = captionOption,
                onSelectCaption = { caption -> onSelectCaptionOption(caption) },
                onDismiss = { showSubtitleSheet = false }
            )
        }

        if (showVideoEffectsSheet) {
            VideoEffectsSettingsSheet(
                onDismiss = { showVideoEffectsSheet = false }
            )
        }

        if (showAudioEnhancementSheet) {
            com.example.ui.player.audio.AudioEnhancementSheet(
                onDismissRequest = { showAudioEnhancementSheet = false }
            )
        }

        if (showSponsorBlockSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSponsorBlockSheet = false },
                containerColor = MaterialTheme.colorScheme.background,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                com.example.smartskip.SponsorBlockSettingsScreen(
                    onBackClick = { showSponsorBlockSheet = false },
                    modifier = Modifier.fillMaxHeight(0.9f)
                )
            }
        }

        // YouTube Fine Scrubbing Filmstrip Overlay
        FineScrubbingOverlay(
            isVisible = showFineScrubbing,
            currentPositionMs = GlobalPlayerManager.currentPositionMs.collectAsState().value,
            durationMs = GlobalPlayerManager.durationMs.collectAsState().value,
            streamData = activeStreamData,
            previewItem = previewItem,
            onScrubPositionChange = { /* realtime scrubbing feedback */ },
            onConfirmSeek = { targetMs ->
                showFineScrubbing = false
                GlobalPlayerManager.seekTo(targetMs)
            },
            onCancel = {
                showFineScrubbing = false
            }
        )

        // Smart Skip Floating Prompt (Manual 'Show button' mode)
        AnimatedVisibility(
            visible = currentPromptSegment != null,
            enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 72.dp)
        ) {
            currentPromptSegment?.let { segment ->
                Surface(
                    onClick = {
                        com.example.smartskip.SmartSkipPlayerEngine.performManualSkip(context)
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.88f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, segment.category.color),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(segment.category.color)
                        )
                        Text(
                            text = "Skip ${segment.category.shortName}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Skip",
                            tint = segment.category.color,
                            modifier = Modifier.size(18.dp)
                        )
                        IconButton(
                            onClick = { com.example.smartskip.SmartSkipPlayerEngine.dismissPrompt() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Netflix-style Next Episode Autoplay Capsule with Fluid Progress Bar (Only for series/episodes)
        if (nextEpisodeData != null) {
            val curPosMsForNextEp by GlobalPlayerManager.currentPositionMs.collectAsState()
            val totalDurMsForNextEp by GlobalPlayerManager.durationMs.collectAsState()
            val isPlayingForNextEp by GlobalPlayerManager.isPlaying.collectAsState()

            NextEpisodeOverlay(
                nextEpisode = nextEpisodeData,
                currentPositionMs = curPosMsForNextEp,
                durationMs = totalDurMsForNextEp,
                isPlaying = isPlayingForNextEp,
                isLandscape = isLandscape,
                onPlayNext = {
                    onPlayNextEpisode?.invoke() ?: onNextClick?.invoke()
                },
                onDismiss = { /* user dismissed countdown */ },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = if (isLandscape) 24.dp else 12.dp,
                        bottom = if (areControlsVisible) 76.dp else if (isLandscape) 24.dp else 16.dp
                    )
            )
        }

        // Smart Skip Notification Toast (e.g. 'Skipped: Sponsor / Intro / Recap')
        AnimatedVisibility(
            visible = skipNotificationText != null,
            enter = fadeIn() + androidx.compose.animation.slideInVertically { -it / 2 },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
        ) {
            val catColor = skipNotificationCategory?.color ?: MaterialTheme.colorScheme.primary
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, catColor.copy(alpha = 0.7f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(catColor)
                    )
                    Text(
                        text = skipNotificationText ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Skip Animation Visual Pulse Effect
        AnimatedVisibility(
            visible = isSkipAnimating,
            enter = fadeIn(androidx.compose.animation.core.tween(100)),
            exit = fadeOut(androidx.compose.animation.core.tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, (skipNotificationCategory?.color ?: Color(0xFF00E676)).copy(alpha = 0.6f))
            )
        }

        // Clean Double-Tap Left (Rewind) Indicator
        AnimatedVisibility(
            visible = doubleTapSeekDirection == "LEFT",
            enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.8f, animationSpec = tween(120)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 1.06f, animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = if (isLandscape) 48.dp else 24.dp)
        ) {
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((-3).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer(scaleX = -1f)
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(13.dp)
                                .graphicsLayer(scaleX = -1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${doubleTapAccumulatedSeconds}s",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }

        // Clean Double-Tap Right (Forward) Indicator
        AnimatedVisibility(
            visible = doubleTapSeekDirection == "RIGHT",
            enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.8f, animationSpec = tween(120)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 1.06f, animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (isLandscape) 48.dp else 24.dp)
        ) {
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((-3).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(13.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${doubleTapAccumulatedSeconds}s",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }

        // Clean Center Double-Tap Play / Pause Indicator
        AnimatedVisibility(
            visible = centerPlayPauseFeedback != null,
            enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.75f, animationSpec = tween(120)),
            exit = fadeOut(tween(220)) + scaleOut(targetScale = 1.20f, animationSpec = tween(220)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (centerPlayPauseFeedback == true) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (centerPlayPauseFeedback == true) "Play" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // YouTube-Style Vertical Gesture HUD (Brightness on left, Volume on right)
        AnimatedVisibility(
            visible = activeVerticalGestureType != null,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(if (activeVerticalGestureType == "BRIGHTNESS") Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = if (isLandscape) 48.dp else 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.Black.copy(alpha = 0.70f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(26.dp))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Icon(
                        imageVector = if (activeVerticalGestureType == "BRIGHTNESS") {
                            if (isAutoBrightness) Icons.Default.BrightnessAuto
                            else if (verticalGestureValue < 0.33f) Icons.Default.BrightnessLow
                            else if (verticalGestureValue < 0.66f) Icons.Default.BrightnessMedium
                            else Icons.Default.BrightnessHigh
                        } else if (verticalGestureValue == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = if (activeVerticalGestureType == "BRIGHTNESS" && isAutoBrightness) Color(0xFF64B5F6) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )

                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(
                                    if (activeVerticalGestureType == "BRIGHTNESS" && isAutoBrightness) {
                                        getSystemBrightness(context).coerceIn(0.15f, 1f)
                                    } else {
                                        verticalGestureValue.coerceIn(0f, 1f)
                                    }
                                )
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (activeVerticalGestureType == "BRIGHTNESS" && isAutoBrightness) Color(0xFF64B5F6) else Color.White
                                )
                        )
                    }

                    Text(
                        text = if (activeVerticalGestureType == "BRIGHTNESS" && isAutoBrightness) "Auto" else "${(verticalGestureValue * 100).toInt()}%",
                        color = if (activeVerticalGestureType == "BRIGHTNESS" && isAutoBrightness) Color(0xFF64B5F6) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (activeVerticalGestureType == "BRIGHTNESS" && isAutoBrightness) 10.sp else 11.sp
                    )
                }
            }
        }

        // Gesture & Seek Overlay Notice (for scrub)
        val activeNoticeText = seekNoticeText
        AnimatedVisibility(
            visible = activeNoticeText != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = activeNoticeText ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Google Lens, Circle-to-Search & On-Device OCR Text Recognition Layer
        if (showLensOverlay) {
            com.example.ui.player.lens.PlayerLensOverlay(
                onDismiss = { showLensOverlay = false },
                videoTitle = activeStreamData?.title ?: streamData?.title,
                videoArtist = activeStreamData?.channelName ?: streamData?.channelName,
                onPlayToggle = { play ->
                    if (play) {
                        exoPlayer?.play()
                    } else {
                        exoPlayer?.pause()
                    }
                }
            )
        }
    }
}
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun toggleFullscreen(context: Context) {
    val activity = context.findActivity() ?: return
    val currentOrientation = activity.requestedOrientation
    val configOrientation = activity.resources.configuration.orientation
    val isLandscape = configOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    if (isLandscape) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        controller.show(WindowInsetsCompat.Type.systemBars())
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
    } else {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

@Composable
fun YouTubeBufferingIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.8.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = Color.White.copy(alpha = 0.95f),
            trackColor = Color.White.copy(alpha = 0.20f),
            strokeWidth = strokeWidth,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun GlowingBufferingIndicator(
    statusText: String = "Loading stream...",
    modifier: Modifier = Modifier
) {
    YouTubeBufferingIndicator(modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioSettingsSheet(
    currentCustomRatio: Float?,
    currentRatioLabel: String,
    customRatiosList: List<String>,
    onSelectRatio: (Float?, String) -> Unit,
    onAddCustomRatio: (String) -> Unit,
    onRemoveCustomRatio: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var widthInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val presetRatios = remember {
        listOf(
            "Default" to null,
            "4:3" to (4f / 3f),
            "16:9" to (16f / 9f),
            "16:10" to (16f / 10f),
            "21:9" to (21f / 9f),
            "32:9" to (32f / 9f),
            "1:1" to (1f / 1f),
            "2.35:1" to 2.35f,
            "2.39:1" to 2.39f
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp, top = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aspect Ratio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets
            Text(
                text = "Presets",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetRatios.forEach { (label, ratio) ->
                    val isSelected = (currentRatioLabel == label)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onSelectRatio(ratio, label)
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Custom Ratios List
            if (customRatiosList.isNotEmpty()) {
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    customRatiosList.forEach { ratioStr ->
                        val isSelected = (currentRatioLabel == ratioStr)
                        val parts = ratioStr.split(":")
                        val ratioValue = if (parts.size == 2) {
                            val w = parts[0].toFloatOrNull() ?: 16f
                            val h = parts[1].toFloatOrNull() ?: 9f
                            if (h > 0) w / h else 16f / 9f
                        } else null

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (ratioValue != null) {
                                    onSelectRatio(ratioValue, ratioStr)
                                }
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(ratioStr)
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onRemoveCustomRatio(ratioStr) }
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Add Custom Ratio
            Text(
                text = "Add Custom Ratio (e.g. 16:9)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = widthInput,
                    onValueChange = { widthInput = it.filter { char -> char.isDigit() } },
                    placeholder = { Text("Width") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = ":",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = heightInput,
                    onValueChange = { heightInput = it.filter { char -> char.isDigit() } },
                    placeholder = { Text("Height") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                IconButton(
                    onClick = {
                        val w = widthInput.toIntOrNull()
                        val h = heightInput.toIntOrNull()
                        if (w != null && h != null && w > 0 && h > 0) {
                            val newRatioStr = "$w:$h"
                            val calcRatio = w.toFloat() / h.toFloat()
                            onAddCustomRatio(newRatioStr)
                            onSelectRatio(calcRatio, newRatioStr)
                            widthInput = ""
                            heightInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Ratio",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

private fun getSystemBrightness(context: Context): Float {
    return try {
        val cr = context.contentResolver
        val brightnessInt = android.provider.Settings.System.getInt(
            cr,
            android.provider.Settings.System.SCREEN_BRIGHTNESS
        )
        (brightnessInt / 255f).coerceIn(0.01f, 1.0f)
    } catch (e: Exception) {
        0.5f
    }
}
