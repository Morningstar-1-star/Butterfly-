package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ExtractorErrorDetails

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
    var playbackReady by remember { mutableStateOf(false) }

    // Reset readiness when title or poster changes
    LaunchedEffect(title, posterUrl) {
        playbackReady = false
    }

    // Trigger transition when ExoPlayer/Media3 renders first frame
    LaunchedEffect(firstFrameRendered) {
        if (firstFrameRendered) {
            playbackReady = true
        }
    }

    val isLoading = isExtracting || !firstFrameRendered

    val targetOverlayAlpha = when {
        extractionError != null -> 1.0f
        playbackReady -> 0.0f
        isLoading -> 1.0f
        else -> 0.0f
    }

    val animatedOverlayAlpha by animateFloatAsState(
        targetValue = targetOverlayAlpha,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "playerOverlayAlpha"
    )

    if (animatedOverlayAlpha <= 0.01f && playbackReady) {
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
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = "Artwork for $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle dark scrim so loading spinner is clearly visible
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }

        // Clean, uniform loading indicator & error diagnostic
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
            } else if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.9f),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(42.dp)
                )
                if (!statusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = statusMessage,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

