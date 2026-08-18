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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.model.CaptionOption
import com.example.model.PlayableStreamOption
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalVideoPlayer(
    streamOption: PlayableStreamOption?,
    hlsUrl: String?,
    captionOption: CaptionOption?,
    providerId: String? = null,
    isPlaying: Boolean = true,
    videoId: String? = null,
    initialPositionMs: Long = 0L,
    availableStreamOptions: List<PlayableStreamOption> = emptyList(),
    onSelectStreamOption: (PlayableStreamOption) -> Unit = {},
    failedSourceLogs: List<com.example.model.FailedSourceLog> = emptyList(),
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onBackClick: (() -> Unit)? = null,
    onNextClick: (() -> Unit)? = null,
    onPreviousClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rawVideoUrl = streamOption?.videoUrl ?: streamOption?.videoStream?.url ?: hlsUrl

    val playbackPrefs = remember(context) { com.example.util.PlaybackPreferences.getInstance(context) }
    val activeStreamData by GlobalPlayerManager.activeStreamData.collectAsState()

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeModeState by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSpeedSubMenu by remember { mutableStateOf(false) }
    var showQualitySubMenu by remember { mutableStateOf(false) }
    var showAudioTrackSubMenu by remember { mutableStateOf(false) }

    val audioTracks by GlobalPlayerManager.audioTracks.collectAsState()
    val speedOptions = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f) }
    var isMusicTrackDetected by remember { mutableStateOf(false) }

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
    }

    // Gesture Controls State
    var brightnessLevel by remember { mutableFloatStateOf(0.7f) }
    var volumeLevel by remember { mutableFloatStateOf(0.7f) }
    var gestureNoticeText by remember { mutableStateOf<String?>(null) }
    var gestureNoticeIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var seekNoticeText by remember { mutableStateOf<String?>(null) }

    var accumulatedDx by remember { mutableFloatStateOf(0f) }
    var accumulatedDy by remember { mutableFloatStateOf(0f) }
    var dragStartPosMs by remember { mutableLongStateOf(0L) }
    var initialBrightness by remember { mutableFloatStateOf(0.7f) }
    var initialVolume by remember { mutableFloatStateOf(0.7f) }
    var isDraggingHorizontally by remember { mutableStateOf(false) }
    var isDraggingVertically by remember { mutableStateOf(false) }

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

    LaunchedEffect(isPlaying) {
        if (isPlaying) GlobalPlayerManager.play() else GlobalPlayerManager.pause()
    }

    LaunchedEffect(streamOption, hlsUrl, captionOption, videoId) {
        GlobalPlayerManager.prepareAndPlay(
            context = context,
            streamData = null,
            streamOption = streamOption,
            hlsUrl = hlsUrl,
            captionOption = captionOption,
            initialPos = initialPositionMs
        )
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

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

    val playerContainerModifier = if (isLandscape) {
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    }

    Box(
        modifier = playerContainerModifier
            .pointerInput(seekSecs) {
                detectTapGestures(
                    onTap = {
                        GlobalPlayerManager.toggleControlsVisibility()
                    },
                    onDoubleTap = { offset ->
                        val totalWidth = size.width
                        val left30Boundary = totalWidth * 0.30f
                        val right30Boundary = totalWidth * 0.70f
                        val seekMs = seekSecs * 1000L

                        if (offset.x < left30Boundary) {
                            gestureNoticeText = "◄◄ ${seekSecs}s Rewind"
                            gestureNoticeIcon = Icons.Default.FastRewind
                            GlobalPlayerManager.seekTo(exoPlayer.currentPosition - seekMs)
                            GlobalPlayerManager.showControls()
                        } else if (offset.x > right30Boundary) {
                            gestureNoticeText = "${seekSecs}s Forward ►►"
                            gestureNoticeIcon = Icons.Default.FastForward
                            GlobalPlayerManager.seekTo(exoPlayer.currentPosition + seekMs)
                            GlobalPlayerManager.showControls()
                        } else {
                            GlobalPlayerManager.toggleControlsVisibility()
                        }
                    }
                )
            }
            .pointerInput(isLandscape) {
                detectDragGestures(
                    onDragStart = {
                        accumulatedDx = 0f
                        accumulatedDy = 0f
                        dragStartPosMs = GlobalPlayerManager.currentPositionMs.value
                        initialBrightness = brightnessLevel
                        initialVolume = volumeLevel
                        isDraggingHorizontally = false
                        isDraggingVertically = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDx += dragAmount.x
                        accumulatedDy += dragAmount.y
                        val absDx = kotlin.math.abs(accumulatedDx)
                        val absDy = kotlin.math.abs(accumulatedDy)

                        // Swipe down to dismiss in portrait
                        if (dragAmount.y > 35f && dragAmount.y > kotlin.math.abs(dragAmount.x) * 1.5f && !isLandscape && onBackClick != null && !isDraggingHorizontally) {
                            onBackClick.invoke()
                            return@detectDragGestures
                        }

                        if (!isDraggingHorizontally && !isDraggingVertically) {
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
                        } else if (isDraggingVertically) {
                            val totalHeight = size.height.toFloat().coerceAtLeast(100f)
                            val isLeftHalf = change.position.x < size.width * 0.5f

                            if (isLeftHalf) {
                                // Left side = Brightness
                                val delta = -accumulatedDy / totalHeight
                                brightnessLevel = (initialBrightness + delta).coerceIn(0.05f, 1.0f)
                                val activity = context as? Activity
                                    ?: (context as? ContextWrapper)?.baseContext as? Activity
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    lp.screenBrightness = brightnessLevel
                                    act.window.attributes = lp
                                }
                                gestureNoticeText = "Brightness ${(brightnessLevel * 100).toInt()}%"
                                gestureNoticeIcon = Icons.Default.BrightnessMedium
                            } else {
                                // Right side = Volume
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                                if (audioManager != null) {
                                    val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                    val delta = -accumulatedDy / totalHeight
                                    volumeLevel = (initialVolume + delta).coerceIn(0f, 1f)
                                    val targetVol = (volumeLevel * maxVol).toInt()
                                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                                    gestureNoticeText = "Volume ${(volumeLevel * 100).toInt()}%"
                                    gestureNoticeIcon = if (targetVol == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        isDraggingHorizontally = false
                        isDraggingVertically = false
                    },
                    onDragCancel = {
                        isDraggingHorizontally = false
                        isDraggingVertically = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val currentPlayerContext = LocalContext.current
        Box(modifier = Modifier.fillMaxSize()) {
            PersistentPlayerHost(
                useController = false,
                resizeMode = resizeModeState,
                onFullscreenClick = {
                    toggleFullscreen(currentPlayerContext)
                },
                modifier = Modifier.fillMaxSize()
            )

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
                    // Left-side minimize button + Title & Uploader info
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (onBackClick != null) {
                                    onBackClick.invoke()
                                } else {
                                    (currentPlayerContext as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                                        ?: (currentPlayerContext as? Activity)?.finish()
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Minimize Player",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = currentDisplayTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (!currentUploader.isNullOrBlank()) {
                                Text(
                                    text = currentUploader,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Right-side actions (Speed, Settings, Fullscreen)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Playback Speed Button / Badge
                        Surface(
                            onClick = {
                                GlobalPlayerManager.showControls()
                                showSpeedSubMenu = true
                                showSettingsSheet = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            contentColor = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (playbackSpeed == 1.0f) "1.0x" else "${playbackSpeed}x",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Settings Gear Icon
                        IconButton(
                            onClick = {
                                GlobalPlayerManager.showControls()
                                showSpeedSubMenu = false
                                showSettingsSheet = true
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Player Settings",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Fullscreen Toggle Icon
                        IconButton(
                            onClick = {
                                GlobalPlayerManager.showControls()
                                toggleFullscreen(currentPlayerContext)
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // YouTube-Style Center Controls (Previous, Play/Pause, Next)
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp)
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
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Video",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Center Big Play/Pause Button
                    val isCurrentlyPlayingCenter by GlobalPlayerManager.isPlaying.collectAsState()
                    IconButton(
                        onClick = {
                            GlobalPlayerManager.showControls()
                            if (isCurrentlyPlayingCenter) {
                                GlobalPlayerManager.pause()
                            } else {
                                GlobalPlayerManager.play()
                            }
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isCurrentlyPlayingCenter) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isCurrentlyPlayingCenter) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
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
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Video",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Bottom YouTube-Style Bar (Seekbar + Live/Elapsed Duration Below + Quick Controls)
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.60f),
                                Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentPosMs by GlobalPlayerManager.currentPositionMs.collectAsState()
                    val totalDurMs by GlobalPlayerManager.durationMs.collectAsState()
                    val bufferedPosMs by GlobalPlayerManager.bufferedPositionMs.collectAsState()
                    val isCurrentlyPlaying by GlobalPlayerManager.isPlaying.collectAsState()

                    // 1. YouTube Precise Seekbar
                    YouTubePreciseSeekBar(
                        currentPositionMs = currentPosMs,
                        durationMs = totalDurMs,
                        bufferedPositionMs = bufferedPosMs,
                        onSeekStarted = { GlobalPlayerManager.showControls() },
                        onSeekScrubbing = { /* Scrubbing */ },
                        onSeekFinished = { targetMs ->
                            GlobalPlayerManager.seekTo(targetMs)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 2. Duration text below Seekbar + Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Current time & Total duration (just like YouTube)
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

                            // Quick Quality Chip
                            val qLabel = streamOption?.qualityLabel?.takeIf { it.isNotBlank() && it != "Direct Video Stream" } ?: "Auto"
                            Surface(
                                onClick = {
                                    GlobalPlayerManager.showControls()
                                    showQualitySubMenu = true
                                    showSettingsSheet = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = qLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Right: Play/Pause, Fullscreen
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    GlobalPlayerManager.showControls()
                                    if (isCurrentlyPlaying) {
                                        GlobalPlayerManager.pause()
                                    } else {
                                        GlobalPlayerManager.play()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrentlyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isCurrentlyPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    GlobalPlayerManager.showControls()
                                    toggleFullscreen(currentPlayerContext)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // YouTube Style Bottom Mini Progress Bar (Visible when controls are hidden/collapsed)
        val currentPosMsForMini by GlobalPlayerManager.currentPositionMs.collectAsState()
        val totalDurMsForMini by GlobalPlayerManager.durationMs.collectAsState()
        val bufferedPosMsForMini by GlobalPlayerManager.bufferedPositionMs.collectAsState()

        AnimatedVisibility(
            visible = !areControlsVisible && totalDurMsForMini > 0L,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
        ) {
            val safeDuration = totalDurMsForMini.coerceAtLeast(1L).toFloat()
            val progressFraction = (currentPosMsForMini.toFloat() / safeDuration).coerceIn(0f, 1f)
            val bufferFraction = (bufferedPosMsForMini.toFloat() / safeDuration).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.20f))
            ) {
                if (bufferFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = bufferFraction)
                            .background(Color.White.copy(alpha = 0.60f))
                    )
                }
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progressFraction)
                            .background(Color(0xFFFF0033))
                    )
                }
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
                },
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White
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
                                else -> "Settings"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (showSpeedSubMenu || showQualitySubMenu || showAudioTrackSubMenu) {
                            TextButton(onClick = {
                                showSpeedSubMenu = false
                                showQualitySubMenu = false
                                showAudioTrackSubMenu = false
                            }) {
                                Text("Back", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.12f)
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
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                        if (option.format.isNotBlank()) {
                                            Text(
                                                text = option.format,
                                                fontSize = 11.sp,
                                                color = Color.Gray
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
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isMusicTrackDetected && playbackPrefs.disableSpeedForMusic.collectAsState().value) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
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

                            // Presets
                            speedOptions.forEach { speed ->
                                val isSelected = (playbackSpeed == speed)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            playbackSpeed = speed
                                            GlobalPlayerManager.setPlaybackSpeed(speed)
                                            Toast.makeText(
                                                context,
                                                "Playback speed set to ${if (speed == 1.0f) "Normal" else "${speed}x"}",
                                                Toast.LENGTH_SHORT
                                            ).show()
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
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
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
                                    tint = Color.White
                                )
                                Text(
                                    text = "Quality",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
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
                                    tint = Color.White
                                )
                                Text(
                                    text = "Playback Speed",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
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
                                    tint = Color.White
                                )
                                Text(
                                    text = "Audio Track & Language",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
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
                                    resizeModeState = when (resizeModeState) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    Toast.makeText(
                                        context,
                                        "Aspect Ratio: ${when (resizeModeState) {
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop / Zoom"
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch / Fill"
                                            else -> "Fit Screen"
                                        }}",
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                                    tint = Color.White
                                )
                                Text(
                                    text = "Aspect Ratio",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = when (resizeModeState) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
                                    else -> "Fit"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
        }

        // Gesture & Seek Overlay Notice
        val activeNoticeText = gestureNoticeText ?: seekNoticeText
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
                    gestureNoticeIcon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = activeNoticeText ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
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
