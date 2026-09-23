package com.example.ui.components

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.isActive
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import com.example.model.VideoItem
import com.example.util.PreviewFrameResolver
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.example.ui.animation.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    watchProgressFraction: Float = 0f,
    showProviderBadge: Boolean = true,
    onPlayNextInQueue: ((VideoItem) -> Unit)? = null,
    onAddToQueue: ((VideoItem) -> Unit)? = null,
    onSaveToWatchLater: ((VideoItem) -> Unit)? = null,
    onSaveToPlaylist: ((VideoItem) -> Unit)? = null,
    onDownload: ((VideoItem) -> Unit)? = null,
    onShare: ((VideoItem) -> Unit)? = null,
    onNotInterested: ((VideoItem) -> Unit)? = null,
    onReport: ((VideoItem) -> Unit)? = null,
    onChannelClick: ((String) -> Unit)? = null
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var localShowOriginal by remember(video.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val effectiveWatchProgress = watchProgressFraction

    val detectedLang = remember(video.title, video.detectedLanguage) {
        video.detectedLanguage ?: "en"
    }

    val effectiveOriginalTitle = video.originalTitle ?: video.title
    val effectiveTranslatedTitle = video.translatedTitleEN

    val activeTitle = remember(video.id, video.title, localShowOriginal, effectiveOriginalTitle, effectiveTranslatedTitle, detectedLang) {
        if (localShowOriginal || detectedLang == "hi") {
            effectiveOriginalTitle
        } else {
            effectiveTranslatedTitle?.takeIf { it.isNotBlank() } ?: video.getDisplayTitle(false, "en")
        }
    }

    val hasTranslation = remember(effectiveOriginalTitle, effectiveTranslatedTitle, detectedLang) {
        detectedLang != "en" &&
        detectedLang != "hi" &&
        !effectiveTranslatedTitle.isNullOrBlank() &&
        !effectiveOriginalTitle.equals(effectiveTranslatedTitle, ignoreCase = true)
    }

    val sourceBadge = remember(video.providerId, video.id, video.uploaderName, video.thumbnailUrl) {
        com.example.util.SourceTagHelper.getSourceBadge(video)
    }

    val seriesPillText = remember(video.tags, video.title, video.providerId) {
        val tagSeries = video.tags.firstOrNull { it.startsWith("S") && it.contains("ep") }
        if (!tagSeries.isNullOrBlank()) {
            tagSeries
        } else {
            val titleLower = video.title.lowercase()
            val seasonMatch = Regex("""s(\d+)\s*e(\d+)""", RegexOption.IGNORE_CASE).find(titleLower)
            if (seasonMatch != null) {
                val s = seasonMatch.groupValues[1].toIntOrNull() ?: 1
                val e = seasonMatch.groupValues[2].toIntOrNull() ?: 1
                "S$s · Ep $e"
            } else if (titleLower.contains("season") || titleLower.contains("s0") || (video.providerId == "torrent" && video.id.contains("tv_"))) {
                "Series"
            } else {
                null
            }
        }
    }

    val isMovieOrMedia = remember(video.providerId, video.id, video.tags, video.title) {
        val pid = (video.providerId ?: "").lowercase()
        pid in listOf("torrent", "tmdb", "anilist", "jikan", "jikan_anime", "vega", "vegacloud", "vidsrc", "vidrock") ||
        video.id.startsWith("torrent_") || video.id.startsWith("movie_") || video.id.startsWith("tv_") || video.id.startsWith("anilist_") ||
        video.tags.any { it.startsWith("★") } ||
        video.title.contains("1080p", ignoreCase = true) || video.title.contains("bluray", ignoreCase = true)
    }

    val displayDurationText = remember(video.durationSeconds, video.displayDuration, isMovieOrMedia) {
        if (video.displayDuration.isNotEmpty() && video.displayDuration != "0:00" && video.displayDuration != "00:00") {
            video.displayDuration
        } else if (video.durationSeconds > 0) {
            val h = video.durationSeconds / 3600
            val m = (video.durationSeconds % 3600) / 60
            val s = video.durationSeconds % 60
            if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        } else if (isMovieOrMedia) {
            "1:45:00"
        } else {
            ""
        }
    }

    val ratingTag = remember(video.tags) {
        video.tags.firstOrNull { it.startsWith("★") || it.contains("★") }
    }

    val formattedTimeAgo = remember(video.uploadDate) {
        com.example.util.DateUtils.formatRelativeTime(video.uploadDate)
    }

    val formattedViewsText = remember(video.formattedViews, video.viewCount) {
        com.example.util.DateUtils.formatViews(video.viewCount, video.formattedViews)
    }

    val effectiveThumbnailUrl = remember(video.thumbnailUrl, video.id, video.providerId) {
        val raw = video.thumbnailUrl?.trim()
        when {
            !raw.isNullOrBlank() && !raw.contains("placeholder") && !raw.contains("blank.gif") && !raw.contains("loading.gif") && (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("//")) -> {
                if (raw.startsWith("//")) "https:$raw" else raw
            }
            video.id.length == 11 && !video.id.contains("/") -> "https://i.ytimg.com/vi/${video.id}/mqdefault.jpg"
            (video.providerId == "youtube" || video.providerId == "all") && video.id.contains("v=") -> {
                val vId = video.id.substringAfter("v=").substringBefore("&")
                "https://i.ytimg.com/vi/$vId/mqdefault.jpg"
            }
            video.providerId == "beeg" && video.id.isNotBlank() -> {
                val fileId = Regex("""\d+""").find(video.id)?.value ?: ""
                if (fileId.isNotBlank()) "https://thumbs.externulls.com/240x180/$fileId.jpg" else null
            }
            video.id.startsWith("bunkr_") -> {
                val fId = video.id.removePrefix("bunkr_")
                "https://i.bunkr.site/thumbs/$fId.jpg"
            }
            video.id.startsWith("mega_") || video.providerId == "mega" -> {
                "https://mega.nz/favicon.ico"
            }
            else -> null
        }
    }

    var isAutoPlaying by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var isPreloadingTeaser by remember { mutableStateOf(false) }
    var loadedPreviewFrames by remember(video.id, video.thumbnailUrl) {
        mutableStateOf<List<String>?>(PreviewFrameResolver.getCachedFrames(video))
    }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val view = LocalView.current

    // Teaser capability check (lightweight check without regex parsing on idle scroll)
    val hasScrubbingTeaser = remember(video.providerId, video.thumbnailUrl) {
        PreviewFrameResolver.supportsScrubbing(video)
    }

    val isPreviewRequested = (isScrubbing || isAutoPlaying) && hasScrubbingTeaser

    // Trigger frame preloading & validation as soon as teaser preview is requested
    LaunchedEffect(isPreviewRequested, loadedPreviewFrames) {
        if (isPreviewRequested && loadedPreviewFrames == null) {
            isPreloadingTeaser = true
            val valid = PreviewFrameResolver.preloadAndValidateFrames(context, video)
            loadedPreviewFrames = valid
            isPreloadingTeaser = false
            currentFrameIndex = 0
            scrubFraction = 0f
        }
    }

    val previewFrames = loadedPreviewFrames ?: emptyList()
    val isPreviewActive = isPreviewRequested && !isPreloadingTeaser && previewFrames.size > 1

    // Fast Automatic Teaser Loop: cycles through fully pre-loaded frames at ~8.3 FPS (120ms)
    LaunchedEffect(isAutoPlaying, isPreviewActive, previewFrames) {
        if (isAutoPlaying && isPreviewActive && previewFrames.size > 1) {
            while (isAutoPlaying) {
                kotlinx.coroutines.delay(120L) // 8.3 FPS smooth, fast video clip teaser preview
                currentFrameIndex = (currentFrameIndex + 1) % previewFrames.size
                scrubFraction = (currentFrameIndex + 1).toFloat() / previewFrames.size
            }
        }
    }

    // Trigger subtle haptic tick feedback as user scrubs or when auto-play engages
    LaunchedEffect(currentFrameIndex, isScrubbing, isAutoPlaying) {
        if ((isScrubbing || isAutoPlaying) && previewFrames.size > 1) {
            try {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            } catch (_: Exception) {}
        }
    }

    val activeImageUrl = remember(isPreviewActive, currentFrameIndex, previewFrames, effectiveThumbnailUrl) {
        if (isPreviewActive && currentFrameIndex in previewFrames.indices) {
            previewFrames[currentFrameIndex]
        } else {
            effectiveThumbnailUrl
        }
    }

    val thumbnailImageRequest = remember(activeImageUrl, isPreviewActive) {
        com.example.util.ThumbnailOptimizer.buildThumbnailRequest(
            context,
            activeImageUrl,
            crossfadeMillis = 0,
            preferCompact = true
        )
    }

    // Horizontal Scrubbing Modifier: only attached when teaser preview is active to keep scroll physics 100% native and fluid
    val scrubModifier = if (hasScrubbingTeaser && (isPreviewActive || isAutoPlaying)) {
        Modifier.pointerInput(video.id, loadedPreviewFrames) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragAccumulator = 0f
                    if (isAutoPlaying) {
                        isAutoPlaying = false
                    }
                    isScrubbing = true
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val frac = (offset.x / width).coerceIn(0f, 1f)
                    scrubFraction = frac
                    val fSize = loadedPreviewFrames?.size ?: 1
                    if (fSize > 1) {
                        currentFrameIndex = (frac * (fSize - 1)).roundToInt().coerceIn(0, fSize - 1)
                    }
                },
                onDragEnd = {
                    isScrubbing = false
                    val fSize = loadedPreviewFrames?.size ?: 1
                    if (dragAccumulator < -40f && fSize > 1) {
                        isAutoPlaying = true
                    }
                },
                onDragCancel = {
                    isScrubbing = false
                    isAutoPlaying = false
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragAccumulator += dragAmount
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val frac = (change.position.x / width).coerceIn(0f, 1f)
                    scrubFraction = frac
                    val fSize = loadedPreviewFrames?.size ?: 1
                    if (fSize > 1) {
                        currentFrameIndex = (frac * (fSize - 1)).roundToInt().coerceIn(0, fSize - 1)
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable {
                if (!isScrubbing) onClick()
            }
    ) {
        // Thumbnail container with Duration Badge and Horizontal Drag Scrubbing / Auto Teaser
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(scrubModifier)
        ) {
                if (thumbnailImageRequest != null) {
                    val isStandardVideoTube = remember(video.providerId, video.id) {
                        val pid = (video.providerId ?: "").lowercase()
                        val idLower = video.id.lowercase()
                        pid in listOf("pornhub", "thumbzilla", "xvideos", "redtube", "spankbang", "eporner", "youporn", "xhamster", "thisvid", "tnaflix", "noodlemagazine", "rule34video", "dailymotion", "vimeo", "bilibili", "twitch", "beeg", "4tube", "hqporner") ||
                        idLower.contains("pornhub") || idLower.contains("ph") || idLower.contains("xvideos") || idLower.contains("spankbang") || idLower.contains("eporner")
                    }

                    val isKnownPosterSource = remember(video.providerId, video.id, video.tags, video.thumbnailUrl) {
                        val pid = (video.providerId ?: "").lowercase()
                        val idLower = video.id.lowercase()
                        val thumbLower = (video.thumbnailUrl ?: "").lowercase()
                        !isStandardVideoTube && (
                            pid in listOf("tmdb", "anilist", "jikan", "torrent", "vidsrc", "vega") ||
                            idLower.contains("movie_") || idLower.contains("tv_") || idLower.contains("torrent_") ||
                            thumbLower.contains("poster") || thumbLower.contains("image.tmdb.org") ||
                            video.tags.any { it.contains("Poster", ignoreCase = true) }
                        )
                    }

                    val isNon169Ratio = isKnownPosterSource && !isStandardVideoTube

                    if (isNon169Ratio) {
                        // High-contrast clean dark background for poster / non-16:9 media cards
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF101010))
                        )
                        AsyncImage(
                            model = thumbnailImageRequest,
                            contentDescription = video.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Standard Full-Frame 16:9 Landscape Artwork Layer (Pornhub, Tubes, etc.)
                        AsyncImage(
                            model = thumbnailImageRequest,
                            contentDescription = video.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Subtle bottom gradient for badge legibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.45f)
                                    ),
                                    startY = 120f
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Normal duration badge (hidden when actively playing teaser or scrubbing)
                if (displayDurationText.isNotEmpty() && !isPreviewActive && !isPreloadingTeaser) {
                    Text(
                        text = displayDurationText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Series Season/Episode Pill badge on bottom-left of thumbnail
                if (!seriesPillText.isNullOrEmpty() && !isPreviewActive && !isPreloadingTeaser) {
                    Text(
                        text = seriesPillText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                if (showProviderBadge && sourceBadge.name.isNotBlank() && !isPreviewActive && !isPreloadingTeaser) {
                    val isAdult = com.example.util.SourceTagHelper.isAdultSource(video.providerId)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.82f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SourceBrandLogo(
                                providerId = video.providerId ?: sourceBadge.name,
                                size = 13.dp,
                                isAdultMode = isAdult
                            )
                            Text(
                                text = sourceBadge.name,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Subtle Teaser Badge indicator when idle (Tap to auto-play teaser frames)
                if (hasScrubbingTeaser && !isPreviewActive && !isPreloadingTeaser) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                isAutoPlaying = true
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SlowMotionVideo,
                            contentDescription = "Swipe or tap to play teaser",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Teaser",
                            color = Color(0xFFFFD54F),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // TEASER PRELOADING INDICATOR OVERLAY: Sleek progress badge while fetching full frames
                if (isPreloadingTeaser) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = Color(0xFFFF4081),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Loading Teaser Frames...",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ACTIVE TEASER / SCRUBBING OVERLAY: Teaser Frame info & Playing Indicator
                if (isPreviewActive) {
                    val estimatedSeconds = if (video.durationSeconds > 0) (scrubFraction * video.durationSeconds).toLong() else -1L
                    val timeText = if (estimatedSeconds >= 0) {
                        val m = (estimatedSeconds % 3600) / 60
                        val s = estimatedSeconds % 60
                        val h = estimatedSeconds / 3600
                        if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
                    } else null

                    Surface(
                        color = Color.Black.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isAutoPlaying) Icons.Default.SlowMotionVideo else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFF4081),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isAutoPlaying) "Playing Teaser • ${currentFrameIndex + 1}/${previewFrames.size}" else "Teaser Clip • Frame ${currentFrameIndex + 1}/${previewFrames.size}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (timeText != null) {
                                Text(
                                    text = "($timeText)",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            if (isAutoPlaying) {
                                Text(
                                    text = "✕",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            isAutoPlaying = false
                                        }
                                        .padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }

                // ACTIVE TEASER / SCRUBBING: Animated Progress bar across bottom of thumbnail
                if (isPreviewActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = scrubFraction.coerceIn(0.01f, 1f))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFF1744), Color(0xFFFF80AB), Color(0xFFFFD54F))
                                    )
                                )
                        )
                    }
                } else if (effectiveWatchProgress > 0f) {
                    // Standard Red YouTube-style Watch Progress Bar on bottom of thumbnail
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(Color.DarkGray.copy(alpha = 0.6f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(effectiveWatchProgress.coerceIn(0.01f, 1f))
                                .background(Color.Red)
                        )
                    }
                }
            }

            // Info Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Channel Logo or Avatar or Fallback
                val brandInfo = remember(video.uploaderName, video.uploaderAvatarUrl, video.title) {
                    com.example.util.ChannelLogoHelper.getBrandInfo(video.uploaderName, video.uploaderAvatarUrl, video.title)
                }

                val targetChannelName = remember(video.uploaderName, brandInfo.brandName) {
                    if (video.uploaderName.isBlank() || video.uploaderName.lowercase().contains("tv network") || video.uploaderName == "T") brandInfo.brandName else video.uploaderName
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(brandInfo.backgroundColor)
                        .then(
                            if (onChannelClick != null && targetChannelName.isNotBlank()) {
                                Modifier.clickable { onChannelClick(targetChannelName) }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (brandInfo.logoUrls.isNotEmpty()) {
                        val primaryUrl = brandInfo.logoUrls.first()
                        val logoImageRequest = remember(primaryUrl) {
                            ImageRequest.Builder(context)
                                .data(primaryUrl)
                                .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .size(coil.size.Size(width = 96, height = 96))
                                .precision(coil.size.Precision.INEXACT)
                                .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                                .allowHardware(true)
                                .allowRgb565(true)
                                .crossfade(false)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                .build()
                        }

                        AsyncImage(
                            model = logoImageRequest,
                            contentDescription = video.uploaderName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = brandInfo.brandShortText,
                            color = brandInfo.textColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (brandInfo.brandShortText.length > 3) 8.sp else 10.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = activeTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (hasTranslation && !localShowOriginal && !effectiveOriginalTitle.equals(activeTitle, ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = effectiveOriginalTitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val youtubeMetadataLine = remember(
                        targetChannelName,
                        formattedViewsText,
                        formattedTimeAgo,
                        ratingTag,
                        seriesPillText
                    ) {
                        com.example.util.DateUtils.buildYouTubeMetadataLine(
                            channelName = targetChannelName,
                            formattedViews = formattedViewsText,
                            timeAgo = formattedTimeAgo,
                            extraTag = ratingTag ?: if (targetChannelName.isBlank()) seriesPillText else null
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = youtubeMetadataLine,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .then(
                                    if (onChannelClick != null && targetChannelName.isNotBlank()) {
                                        Modifier.clickable { onChannelClick(targetChannelName) }
                                    } else Modifier
                                )
                        )

                        if (hasTranslation) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.clickable { localShowOriginal = !localShowOriginal }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Translate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = if (localShowOriginal) "Show Translation" else "Original",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (!video.recommendationReason.isNullOrBlank()) {
                        val isFullContent = video.recommendationReason.contains("Full Movie") ||
                                video.recommendationReason.contains("Full Match") ||
                                video.recommendationReason.contains("Full Episode") ||
                                video.recommendationReason.contains("Full Video")

                        Spacer(modifier = Modifier.height(3.dp))
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isFullContent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            border = if (isFullContent) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null
                        ) {
                            Text(
                                text = video.recommendationReason,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isFullContent) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (isFullContent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // THREE-DOTS CONTEXT MENU BUTTON
                IconButton(
                    onClick = { showBottomSheet = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Video options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                val hasTranslationSheet = detectedLang != "en" &&
                        detectedLang != "hi" &&
                        !effectiveTranslatedTitle.isNullOrBlank() &&
                        effectiveOriginalTitle != effectiveTranslatedTitle
                if (hasTranslationSheet) {
                    VideoOptionMenuItem(
                        icon = Icons.Outlined.Translate,
                        label = if (localShowOriginal) "Show Translated Title (English)" else "Show Original Untouched Title",
                        onClick = {
                            localShowOriginal = !localShowOriginal
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showBottomSheet = false
                            }
                        }
                    )
                }

                VideoOptionMenuItem(
                    icon = Icons.Outlined.PlaylistPlay,
                    label = "Play next in queue",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onPlayNextInQueue?.invoke(video)
                            Toast.makeText(context, "Playing next in queue", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.PlaylistAdd,
                    label = "Play last in queue",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onAddToQueue?.invoke(video)
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.WatchLater,
                    label = "Save to Watch Later",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onSaveToWatchLater?.invoke(video)
                            Toast.makeText(context, "Saved to Watch Later", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.BookmarkBorder,
                    label = "Save to playlist",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onSaveToPlaylist?.invoke(video)
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.FileDownload,
                    label = "Download video",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onDownload?.invoke(video)
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.Share,
                    label = "Share",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            if (onShare != null) {
                                onShare.invoke(video)
                            } else {
                                com.example.util.VideoShareHelper.shareVideo(context, video)
                            }
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.Block,
                    label = "Not interested",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            onNotInterested?.invoke(video)
                            Toast.makeText(context, "We won't recommend this video again", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                VideoOptionMenuItem(
                    icon = Icons.Outlined.Flag,
                    label = "Report",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                            if (onReport != null) {
                                onReport.invoke(video)
                            } else {
                                Toast.makeText(context, "Report submitted. Thank you for your feedback.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun VideoOptionMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

