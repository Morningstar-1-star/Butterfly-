package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ExtractorErrorDetails

enum class TorrentState {
    IDLE,
    RESOLVING,
    TORRENT_FOUND,
    BUFFERING,
    PLAYING
}

@Composable
fun TorrentArtworkOverlay(
    isTorrent: Boolean,
    title: String,
    posterUrl: String?,
    isExtracting: Boolean,
    statusMessage: String?,
    firstFrameRendered: Boolean,
    extractionError: ExtractorErrorDetails? = null,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var torrentPlaybackReady by remember { mutableStateOf(false) }

    // Reset readiness when title or poster changes
    LaunchedEffect(title, posterUrl, isTorrent) {
        torrentPlaybackReady = false
    }

    // Trigger transition when ExoPlayer/Media3 renders first frame
    LaunchedEffect(firstFrameRendered, isTorrent) {
        if (isTorrent && firstFrameRendered) {
            torrentPlaybackReady = true
        }
    }

    val torrentState = remember(isTorrent, isExtracting, torrentPlaybackReady, firstFrameRendered, extractionError) {
        when {
            !isTorrent -> TorrentState.IDLE
            extractionError != null -> TorrentState.IDLE
            torrentPlaybackReady -> TorrentState.PLAYING
            isExtracting -> TorrentState.RESOLVING
            else -> TorrentState.BUFFERING
        }
    }

    // Target animation values
    val targetSaturation = if (isTorrent) {
        if (torrentPlaybackReady) 1.0f else 0.0f
    } else 1.0f

    val targetDarkAlpha = if (isTorrent) {
        if (torrentPlaybackReady) 0.0f else 0.45f
    } else 0.0f

    val targetOverlayAlpha = if (isTorrent) {
        if (torrentPlaybackReady) 0.0f else 1.0f
    } else {
        if (isExtracting || extractionError != null) 1.0f else 0.0f
    }

    // 500-700ms smooth animation for grayscale, dark overlay, and fade-out
    val animatedSaturation by animateFloatAsState(
        targetValue = targetSaturation,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "torrentGrayscale"
    )

    val animatedDarkAlpha by animateFloatAsState(
        targetValue = targetDarkAlpha,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "torrentDarkAlpha"
    )

    val animatedOverlayAlpha by animateFloatAsState(
        targetValue = targetOverlayAlpha,
        animationSpec = tween(durationMillis = 650, easing = LinearEasing),
        label = "torrentOverlayAlpha"
    )

    if (animatedOverlayAlpha <= 0.01f && torrentState == TorrentState.PLAYING) {
        // Overlay fully faded out, video playback revealed
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(animatedOverlayAlpha)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isTorrent) {
            // 1. TMDB POSTER ARTWORK WITH DYNAMIC GRAYSCALE MATRIX (100% -> 0%)
            val colorMatrix = remember(animatedSaturation) {
                ColorMatrix().apply { setToSaturation(animatedSaturation) }
            }

            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = "TMDB Artwork for $title",
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(colorMatrix),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF18181C))
                )
            }

            // 2. DARKEN OVERLAY (VISIBLE -> TRANSPARENT)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = animatedDarkAlpha))
            )

            // 3. FULL COLOR TMDB TITLE / LOGO ARTWORK & RESOLVING STATE INDICATORS
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                if (extractionError != null) {
                    ErrorDiagnosticCard(
                        errorDetails = extractionError,
                        onRetry = onRetry,
                        onOpenPoTokenConfig = {}
                    )
                } else if (isExtracting || torrentState == TorrentState.RESOLVING || torrentState == TorrentState.BUFFERING) {
                    // Minimal, sleek, premium circular progress indicator without distracting overlays
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.85f),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        } else {
            // NON-TORRENT SOURCE (NO GRAYSCALE, EXISTING UI)
            if (extractionError != null) {
                ErrorDiagnosticCard(
                    errorDetails = extractionError,
                    onRetry = onRetry,
                    onOpenPoTokenConfig = {}
                )
            } else if (isExtracting) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.85f),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
