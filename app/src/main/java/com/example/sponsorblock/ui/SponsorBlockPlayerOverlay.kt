package com.example.sponsorblock.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sponsorblock.SponsorBlockPreferences
import com.example.sponsorblock.SponsorBlockRepository
import com.example.sponsorblock.model.SponsorBlockAction
import com.example.sponsorblock.model.SponsorSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SponsorBlockPlayerOverlay(
    videoId: String?,
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    streamTitle: String? = null
) {
    val context = LocalContext.current
    val prefs = remember { SponsorBlockPreferences.getInstance(context) }
    val isEnabled by prefs.isEnabled.collectAsState()
    val showUndoSkipNotification by prefs.showUndoSkipNotification.collectAsState()
    val skipNotificationDurationSeconds by prefs.skipNotificationDurationSeconds.collectAsState()
    val useCompactSkipButton by prefs.useCompactSkipButton.collectAsState()
    val useSquareLayout by prefs.useSquareLayout.collectAsState()

    var segments by remember { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    val ignoredSegmentUuids = remember { mutableStateListOf<String>() }

    var activeUndoSegment by remember { mutableStateOf<SponsorSegment?>(null) }
    var undoBannerTimerSeconds by remember { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()

    // Load multi-platform segments (YouTube SponsorBlock, Anime AniSkip, TV IntroDB, Movie Songs)
    LaunchedEffect(videoId, streamTitle, isEnabled) {
        if (!isEnabled || (videoId.isNullOrBlank() && streamTitle.isNullOrBlank())) {
            segments = emptyList()
            return@LaunchedEffect
        }
        segments = SponsorBlockRepository.fetchAllMediaSegments(context, videoId, streamTitle)
    }

    // Auto-skip and Manual Skip logic during playback
    LaunchedEffect(currentPositionMs, segments, isEnabled) {
        if (!isEnabled || segments.isEmpty() || currentPositionMs <= 0L) return@LaunchedEffect

        val currentSec = currentPositionMs / 1000.0

        // Find matching active segment
        val activeSeg = segments.firstOrNull { seg ->
            currentSec >= seg.startTime && currentSec < (seg.endTime - 0.3) && !ignoredSegmentUuids.contains(seg.uuid)
        } ?: return@LaunchedEffect

        val action = prefs.getCategoryAction(activeSeg.category)

        if (action == SponsorBlockAction.AUTO_SKIP) {
            // Seek to segment end
            val targetMs = (activeSeg.endTime * 1000L).toLong()
            onSeekTo(targetMs)

            // Record stats
            prefs.recordSkip(activeSeg.duration)
            coroutineScope.launch {
                SponsorBlockRepository.sendSkipViewedTime(context, activeSeg.uuid)
            }

            // Show Undo banner if enabled
            if (showUndoSkipNotification) {
                activeUndoSegment = activeSeg
                undoBannerTimerSeconds = skipNotificationDurationSeconds
            }
        }
    }

    // Countdown timer for Undo Notification
    LaunchedEffect(activeUndoSegment) {
        if (activeUndoSegment != null) {
            while (undoBannerTimerSeconds > 0) {
                delay(1000)
                undoBannerTimerSeconds--
            }
            activeUndoSegment = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Manual Skip Button
        val currentSec = currentPositionMs / 1000.0
        val manualSeg = remember(currentSec, segments) {
            segments.firstOrNull { seg ->
                currentSec >= seg.startTime && currentSec < seg.endTime &&
                !ignoredSegmentUuids.contains(seg.uuid) &&
                prefs.getCategoryAction(seg.category) == SponsorBlockAction.MANUAL_SKIP
            }
        }

        AnimatedVisibility(
            visible = manualSeg != null,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp, end = 24.dp)
        ) {
            manualSeg?.let { seg ->
                val shape = if (useSquareLayout) RoundedCornerShape(8.dp) else CircleShape
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable {
                            val targetMs = (seg.endTime * 1000L).toLong()
                            onSeekTo(targetMs)
                            prefs.recordSkip(seg.duration)
                            coroutineScope.launch {
                                SponsorBlockRepository.sendSkipViewedTime(context, seg.uuid)
                            }
                        }
                        .padding(
                            horizontal = if (useCompactSkipButton) 12.dp else 18.dp,
                            vertical = if (useCompactSkipButton) 8.dp else 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(seg.category.color)
                    )
                    Text(
                        text = if (useCompactSkipButton) "Skip" else "Skip ${seg.category.title}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Skip",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Undo Auto-Skip Notification Banner
        AnimatedVisibility(
            visible = activeUndoSegment != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            activeUndoSegment?.let { seg ->
                val shape = if (useSquareLayout) RoundedCornerShape(8.dp) else RoundedCornerShape(24.dp)
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.88f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(seg.category.color)
                    )
                    Text(
                        text = "Skipped ${seg.category.title}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = {
                            val startMs = (seg.startTime * 1000L).toLong()
                            ignoredSegmentUuids.add(seg.uuid)
                            onSeekTo(startMs)
                            activeUndoSegment = null
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = "Undo",
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Undo", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Composable that draws colored SponsorBlock segment indicators on top of a seekbar
 */
@Composable
fun SponsorBlockSeekbarOverlay(
    videoId: String?,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { SponsorBlockPreferences.getInstance(context) }
    val isEnabled by prefs.isEnabled.collectAsState()

    var segments by remember { mutableStateOf<List<SponsorSegment>>(emptyList()) }

    LaunchedEffect(videoId, isEnabled) {
        if (!isEnabled || videoId.isNullOrBlank()) {
            segments = emptyList()
            return@LaunchedEffect
        }
        val cleanId = SponsorBlockRepository.extractYouTubeVideoId(videoId)
        if (cleanId != null) {
            segments = SponsorBlockRepository.fetchSegments(context, cleanId)
        } else {
            segments = emptyList()
        }
    }

    if (segments.isEmpty() || durationMs <= 0L) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(4.dp)) {
        val totalWidthDp = maxWidth

        segments.forEach { seg ->
            val startRatio = (seg.startTime * 1000.0 / durationMs).coerceIn(0.0, 1.0).toFloat()
            val endRatio = (seg.endTime * 1000.0 / durationMs).coerceIn(0.0, 1.0).toFloat()
            val widthRatio = (endRatio - startRatio).coerceAtLeast(0.005f)

            val startPx = totalWidthDp * startRatio
            val widthPx = totalWidthDp * widthRatio

            Box(
                modifier = Modifier
                    .offset(x = startPx)
                    .width(widthPx)
                    .fillMaxHeight()
                    .background(seg.category.color)
            )
        }
    }
}
