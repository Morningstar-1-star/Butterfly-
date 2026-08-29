package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.ErrorDiagnosticCard
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoDetailsSection
import com.example.ui.components.DownloadQualityBottomSheet
import com.example.ui.components.LandscapeRelatedDrawer
import com.example.ui.player.GlobalPlayerManager
import com.example.resolver.SourceStreamType
import com.example.ui.player.EmbedWebViewPlayer
import com.example.ui.player.UniversalVideoPlayer
import com.example.ui.ambient.AmbientPlayerGlow
import com.example.ui.ambient.rememberAmbientPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.model.EpisodeItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeVideoId by viewModel.activeVideoId.collectAsState()
    val extractionResult by viewModel.extractionResult.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val selectedOption by viewModel.selectedStreamOption.collectAsState()
    val selectedCaption by viewModel.selectedCaptionOption.collectAsState()
    val availableProviders by viewModel.availableProviders.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val watchLaterList by viewModel.watchLaterList.collectAsState()
    val watchPositionMsMap by viewModel.watchPositionMsMap.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()

    val currentStreamData = (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData
    val providerId = currentStreamData?.providerId
    val context = androidx.compose.ui.platform.LocalContext.current

    val initialPositionMs = remember(activeVideoId) {
        activeVideoId?.let { id ->
            watchPositionMsMap[id]?.takeIf { it > 0L }
                ?: com.example.util.PlaybackResumeManager.getSavedPosition(context, id)
        } ?: 0L
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }
    var showSaveToPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var showDownloadQualitySheet by remember { mutableStateOf(false) }

    val offlineDownloads by viewModel.offlineDownloads.collectAsState()
    val isDownloaded = remember(activeVideoId, offlineDownloads) {
        activeVideoId != null && offlineDownloads.any { it.videoId == activeVideoId && it.status == "COMPLETED" }
    }
    val isDownloading = remember(activeVideoId, offlineDownloads) {
        activeVideoId != null && offlineDownloads.any { it.videoId == activeVideoId && it.status == "DOWNLOADING" }
    }

    val playbackEnded by GlobalPlayerManager.playbackEnded.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()
    var isUpNextActive by remember { mutableStateOf(false) }
    var autoPlayCountdown by remember { mutableStateOf(5) }

    val trendingVideos by viewModel.trendingVideos.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val activeVideoItem by viewModel.activeVideoItem.collectAsState()
    val failedSourceLogs by viewModel.failedSourceLogs.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val showThumbnailTags by viewModel.showThumbnailTags.collectAsState()
    val videoComments by viewModel.videoComments.collectAsState()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsState()
    val tvSeasons by viewModel.tvSeasons.collectAsState()
    val isSeasonsLoading by viewModel.isSeasonsLoading.collectAsState()
    var selectedSeasonNumber by remember { mutableStateOf(1) }
    var selectedPillTab by remember { mutableStateOf("RELATED") } // "EPISODES", "RELATED", "COMMENTS"

    LaunchedEffect(tvSeasons) {
        if (tvSeasons.isNotEmpty()) {
            if (tvSeasons.none { it.seasonNumber == selectedSeasonNumber }) {
                selectedSeasonNumber = tvSeasons.first().seasonNumber
            }
            if (selectedPillTab == "RELATED") {
                selectedPillTab = "EPISODES"
            }
        } else {
            selectedPillTab = "RELATED"
        }
    }

    val currentVideoItem = remember(currentStreamData, activeVideoId, activeVideoItem, trendingVideos, searchResults) {
        if (currentStreamData != null) {
            VideoItem(
                id = activeVideoId ?: "playing_video",
                title = currentStreamData.title,
                uploaderName = currentStreamData.channelName,
                thumbnailUrl = currentStreamData.thumbnailUrl,
                providerId = providerId ?: currentStreamData.providerId ?: "youtube"
            )
        } else if (activeVideoItem != null) {
            activeVideoItem
        } else {
            activeVideoId?.let { vid ->
                trendingVideos.firstOrNull { it.id == vid }
                    ?: searchResults.firstOrNull { it.id == vid }
                    ?: VideoItem(
                        id = vid,
                        title = if (vid.length == 11) "YouTube Video" else vid,
                        uploaderName = providerId?.replaceFirstChar { it.uppercase() } ?: "YouTube",
                        thumbnailUrl = if (vid.length == 11) "https://i.ytimg.com/vi/$vid/hqdefault.jpg" else null,
                        providerId = providerId ?: "youtube"
                    )
            }
        }
    }

    val hiddenVideoIds by viewModel.hiddenVideoIds.collectAsState()
    val notInterestedVideoIds by viewModel.notInterestedVideoIds.collectAsState()
    val notInterestedChannels by viewModel.notInterestedChannels.collectAsState()
    val playerRecommendations by viewModel.playerRecommendations.collectAsState()
    val isLoadingPlayerRecs by viewModel.isLoadingPlayerRecs.collectAsState()

    val playbackPrefs = remember(context) { com.example.util.PlaybackPreferences.getInstance(context) }
    val isAmbientEnabled by playbackPrefs.ambientModeEnabled.collectAsState()
    val isPowerSaveActive by viewModel.isPowerSaveActive.collectAsState()
    val batterySaverDisableAmbient by viewModel.batterySaverDisableAmbient.collectAsState()
    val effectiveAmbient = isAmbientEnabled && (!isPowerSaveActive || !batterySaverDisableAmbient)
    val ambientPalette = rememberAmbientPalette(
        thumbnailUrl = currentStreamData?.thumbnailUrl ?: currentVideoItem?.thumbnailUrl
    )

    val relatedContent = remember(trendingVideos, playerRecommendations, activeVideoId, hiddenVideoIds, notInterestedVideoIds, notInterestedChannels) {
        val base = trendingVideos.filter { it.id != activeVideoId }
        (base + playerRecommendations)
            .distinctBy { it.id }
            .filterNot { viewModel.isBlockedVideo(it) }
    }

    val displayTitle = currentStreamData?.title ?: currentVideoItem?.title ?: ""
    val extractionError = (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Error)?.errorDetails
    val isSavedInWatchLater = currentVideoItem != null && watchLaterList.any { it.id == currentVideoItem.id }

    val listState = rememberLazyListState()
    var showLandscapeRelatedDrawer by remember { mutableStateOf(false) }
    var showServerSelectorSheet by remember { mutableStateOf(false) }

    val unifiedCandidates by viewModel.unifiedCandidates.collectAsState()
    val activeSourceCandidate by viewModel.activeSourceCandidate.collectAsState()
    val isResolvingUnifiedSources by viewModel.isResolvingUnifiedSources.collectAsState()
    val unifiedStatusMessage by viewModel.unifiedStatusMessage.collectAsState()

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val dragOffsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val maxDockDistancePx = with(density) { 360.dp.toPx() }
    val minimizeThresholdPx = with(density) { 80.dp.toPx() }

    val currentDragY = dragOffsetY.value.coerceAtLeast(0f)
    val dragFraction = (currentDragY / maxDockDistancePx).coerceIn(0f, 1f)

    // Details sheet fades out smoothly during vertical drag
    val detailsAlpha = (1.0f - dragFraction * 2.5f).coerceIn(0f, 1f)
    val detailsTranslationY = currentDragY * 0.8f

    // 16:9 video player scales and translates down cleanly towards mini-player position
    val playerScale = 1.0f - (dragFraction * 0.30f)
    val playerTranslationX = dragFraction * (with(density) { 48.dp.toPx() })
    val playerTranslationY = currentDragY * 0.85f
    val playerCornerDp = (dragFraction * 16).dp
    val bgOverlayAlpha = (1.0f - dragFraction * 1.2f).coerceIn(0f, 1f)

    val minimizePlayerAction: () -> Unit = {
        coroutineScope.launch {
            if (dragOffsetY.value < maxDockDistancePx) {
                dragOffsetY.animateTo(maxDockDistancePx, tween(140, easing = FastOutSlowInEasing))
            }
            onBackClick()
        }
    }

    LaunchedEffect(activeVideoId) {
        dragOffsetY.snapTo(0f)
    }

    val landscapeVideos = remember(relatedContent, activeVideoId) {
        relatedContent.filter { it.id != activeVideoId }
    }

    LaunchedEffect(initialPositionMs, activeVideoId) {
        if (initialPositionMs > 5000L) {
            val totalSecs = initialPositionMs / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            val formatted = String.format("%02d:%02d", mins, secs)
            snackbarHostState.showSnackbar("Resumed at $formatted")
        }
    }

    LaunchedEffect(currentStreamData?.videoId, activeVideoId) {
        viewModel.loadMorePlayerRecommendations(currentStreamData)
    }

    LaunchedEffect(displayTitle, activeVideoId) {
        if (displayTitle.isNotBlank() && displayTitle != "Loading video...") {
            val cleanTitle = displayTitle.replace(Regex("""\s*\(\d{4}\).*"""), "").trim()
            viewModel.resolveUnifiedSourcesForMedia(
                com.example.model.MediaIdentity(
                    title = cleanTitle,
                    mediaType = if (cleanTitle.contains("season", true) || cleanTitle.contains("episode", true)) com.example.model.MediaType.TV else com.example.model.MediaType.MOVIE
                )
            )
        }
    }

    LaunchedEffect(playbackEnded) {
        if (playbackEnded) {
            val currentQueue = viewModel.playbackQueue.value
            if (currentQueue.isNotEmpty()) {
                viewModel.playNextInQueue()
            }
        }
    }

    if (isLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = { totalDrag = 0f },
                        onDragCancel = { totalDrag = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            totalDrag += dragAmount
                            if (totalDrag < -60f && !showLandscapeRelatedDrawer) {
                                change.consume()
                                totalDrag = 0f
                                showLandscapeRelatedDrawer = true
                            } else if (totalDrag > 60f && showLandscapeRelatedDrawer) {
                                change.consume()
                                totalDrag = 0f
                                showLandscapeRelatedDrawer = false
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (activeSourceCandidate?.type == SourceStreamType.EMBED_WEBVIEW) {
                EmbedWebViewPlayer(
                    candidate = activeSourceCandidate!!,
                    onClose = onBackClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                UniversalVideoPlayer(
                streamOption = selectedOption,
                hlsUrl = currentStreamData?.hlsUrl ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.hlsUrl,
                captionOption = selectedCaption,
                streamData = currentStreamData ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData,
                providerId = providerId,
                isPlaying = isPlaying,
                videoId = activeVideoId,
                initialPositionMs = initialPositionMs,
                availableStreamOptions = currentStreamData?.availableStreamOptions ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.availableStreamOptions ?: emptyList(),
                onSelectStreamOption = { option -> viewModel.selectStreamOption(option) },
                failedSourceLogs = failedSourceLogs,
                onProgressUpdate = { pos, dur ->
                    activeVideoId?.let { id -> viewModel.recordWatchProgress(id, pos, dur) }
                },
                onBackClick = onBackClick,
                onNextClick = {
                    val currentQueue = viewModel.playbackQueue.value
                    if (currentQueue.isNotEmpty()) {
                        viewModel.playNextInQueue()
                    } else if (landscapeVideos.isNotEmpty()) {
                        val nextVid = landscapeVideos.first()
                        viewModel.playVideo(nextVid.id, nextVid.providerId)
                    } else {
                        val curMs = GlobalPlayerManager.currentPositionMs.value
                        GlobalPlayerManager.seekTo(curMs + 10000L)
                    }
                },
                onPreviousClick = {
                    val curMs = GlobalPlayerManager.currentPositionMs.value
                    if (curMs > 5000L) {
                        GlobalPlayerManager.seekTo(0L)
                    } else {
                        GlobalPlayerManager.seekTo((curMs - 10000L).coerceAtLeast(0L))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            }

            // Landscape Related Videos Drawer
            LandscapeRelatedDrawer(
                isVisible = showLandscapeRelatedDrawer,
                videos = landscapeVideos,
                currentVideoId = activeVideoId,
                onVideoClick = { video ->
                    viewModel.playVideo(video.id, video.providerId)
                },
                onDismiss = { showLandscapeRelatedDrawer = false }
            )
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { paddingValues ->
            // Backdrop Overlay (Fades out cleanly to reveal underlying screen)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = bgOverlayAlpha))
            )

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // YouTube-style Dynamic Ambient Mode Lighting Effect
                AmbientPlayerGlow(
                    palette = ambientPalette,
                    isEnabled = effectiveAmbient && detailsAlpha > 0.1f
                )

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // PORTRAIT VIDEO PLAYER VIEW (Isolated 16:9 player container scaling smoothly)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .graphicsLayer {
                                translationY = playerTranslationY
                                translationX = playerTranslationX
                                scaleX = playerScale
                                scaleY = playerScale
                                transformOrigin = TransformOrigin(0.5f, 0.0f)
                                shape = RoundedCornerShape(playerCornerDp)
                                clip = true
                                alpha = if (dragFraction > 0.95f) 1.0f - ((dragFraction - 0.95f) * 20f).coerceIn(0f, 1f) else 1.0f
                            }
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeSourceCandidate?.type == SourceStreamType.EMBED_WEBVIEW) {
                            EmbedWebViewPlayer(
                                candidate = activeSourceCandidate!!,
                                onClose = minimizePlayerAction,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            UniversalVideoPlayer(
                            streamOption = selectedOption,
                            hlsUrl = currentStreamData?.hlsUrl ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.hlsUrl,
                            captionOption = selectedCaption,
                            streamData = currentStreamData ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData,
                            providerId = providerId,
                            isPlaying = isPlaying,
                            videoId = activeVideoId,
                            initialPositionMs = initialPositionMs,
                            availableStreamOptions = currentStreamData?.availableStreamOptions ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.availableStreamOptions ?: emptyList(),
                            onSelectStreamOption = { option -> viewModel.selectStreamOption(option) },
                            failedSourceLogs = failedSourceLogs,
                            onProgressUpdate = { pos, dur ->
                                activeVideoId?.let { id -> viewModel.recordWatchProgress(id, pos, dur) }
                            },
                            onSwipeDownDrag = { deltaY ->
                                coroutineScope.launch {
                                    dragOffsetY.snapTo((dragOffsetY.value + deltaY).coerceAtLeast(0f))
                                }
                            },
                            onSwipeDownEnd = { accumulatedDy ->
                                coroutineScope.launch {
                                    if (dragOffsetY.value > minimizeThresholdPx || accumulatedDy > 50f) {
                                        minimizePlayerAction()
                                    } else {
                                        dragOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                            },
                            onBackClick = minimizePlayerAction,
                            onNextClick = {
                                val currentQueue = viewModel.playbackQueue.value
                                if (currentQueue.isNotEmpty()) {
                                    viewModel.playNextInQueue()
                                } else if (landscapeVideos.isNotEmpty()) {
                                    val nextVid = landscapeVideos.first()
                                    viewModel.playVideo(nextVid.id, nextVid.providerId)
                                } else {
                                    val curMs = GlobalPlayerManager.currentPositionMs.value
                                    GlobalPlayerManager.seekTo(curMs + 10000L)
                                }
                            },
                            onPreviousClick = {
                                val curMs = GlobalPlayerManager.currentPositionMs.value
                                if (curMs > 5000L) {
                                    GlobalPlayerManager.seekTo(0L)
                                } else {
                                    GlobalPlayerManager.seekTo((curMs - 10000L).coerceAtLeast(0L))
                                }
                            }
                        )
                        }
                    }

                    // SCROLLABLE CONTENT (DETAILS + RELATED VIDEOS) - SLIDES DOWN & VANISHES INSTANTLY UPON SWIPING DOWN!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .graphicsLayer {
                                alpha = detailsAlpha
                                translationY = detailsTranslationY
                            }
                    ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // Video Details Section
                        item {
                            VideoDetailsSection(
                                streamData = currentStreamData,
                                previewItem = currentVideoItem,
                                selectedOption = selectedOption,
                                selectedCaption = selectedCaption,
                                onSelectOption = { viewModel.selectStreamOption(it) },
                                onSelectCaption = { viewModel.selectCaptionOption(it) },
                                onTagClick = { tag ->
                                    viewModel.updateSearchQuery(tag)
                                    viewModel.performSearch(tag)
                                    onBackClick()
                                },
                                isLiked = isLiked,
                                isDisliked = isDisliked,
                                isSaved = isSavedInWatchLater,
                                onLikeClick = {
                                    isLiked = !isLiked
                                    if (isLiked) isDisliked = false
                                    activeVideoId?.let { id -> viewModel.toggleLikeVideo(id) }
                                },
                                onDislikeClick = {
                                    isDisliked = !isDisliked
                                    if (isDisliked) isLiked = false
                                    activeVideoId?.let { id -> viewModel.toggleDislikeVideo(id) }
                                },
                                onSaveClick = {
                                    currentVideoItem?.let { video ->
                                        if (isSavedInWatchLater) {
                                            viewModel.removeFromWatchLater(video)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Removed from Watch Later")
                                            }
                                        } else {
                                            viewModel.addToWatchLater(video)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Saved to Watch Later")
                                            }
                                        }
                                    }
                                },
                                onSaveLongClick = { showSaveToPlaylistSheet = true },
                                onShareClick = {
                                    val shareUrl = currentStreamData?.videoUrl?.takeIf { it.isNotBlank() }
                                        ?: (if (!activeVideoId.isNullOrBlank() && activeVideoId!!.length == 11) "https://youtu.be/$activeVideoId" else "")
                                    try {
                                        val sendIntent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            putExtra(android.content.Intent.EXTRA_TEXT, if (displayTitle.isNotBlank()) "$displayTitle\n$shareUrl" else shareUrl)
                                            type = "text/plain"
                                        }
                                        val shareIntent = android.content.Intent.createChooser(sendIntent, "Share video via")
                                        context.startActivity(shareIntent)
                                    } catch (e: Exception) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Video link copied to clipboard")
                                        }
                                    }
                                },
                                onCommentsClick = { selectedPillTab = "COMMENTS" },
                                onChannelClick = { channelName ->
                                    viewModel.openChannel(channelName)
                                },
                                isSubscribed = viewModel.isSubscribed(currentStreamData?.channelName ?: currentVideoItem?.uploaderName ?: ""),
                                onSubscribeClick = {
                                    val chName = currentStreamData?.channelName ?: currentVideoItem?.uploaderName ?: ""
                                    if (chName.isNotBlank()) {
                                        val isNowSub = !viewModel.isSubscribed(chName)
                                        viewModel.toggleSubscription(chName, currentStreamData?.channelAvatarUrl ?: currentVideoItem?.thumbnailUrl)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(if (isNowSub) "Subscribed to $chName" else "Unsubscribed from $chName")
                                        }
                                    }
                                },
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                downloadProgress = 0f,
                                onDownloadClick = { showDownloadQualitySheet = true },
                                onServersClick = {
                                    showServerSelectorSheet = true
                                    if (displayTitle.isNotBlank()) {
                                        val cleanTitle = displayTitle.replace(Regex("""\s*\(\d{4}\).*"""), "").trim()
                                        viewModel.resolveUnifiedSourcesForMedia(
                                            com.example.model.MediaIdentity(
                                                title = cleanTitle,
                                                mediaType = if (cleanTitle.contains("season", true) || cleanTitle.contains("episode", true)) com.example.model.MediaType.TV else com.example.model.MediaType.MOVIE
                                            ),
                                            force = true
                                        )
                                    }
                                }
                            )
                        }

                        // YouTube Queue Section (Temporary Session Playlist)
                        if (playbackQueue.isNotEmpty()) {
                            item {
                                QueueSection(
                                    queue = playbackQueue,
                                    onPlayItem = { video -> viewModel.playFromQueue(video) },
                                    onRemoveItem = { video -> viewModel.removeFromQueue(video) },
                                    onClearQueue = { viewModel.clearQueue() }
                                )
                            }
                        }

                        // Modern Pill Tab Navigation Bar (Episodes vs Related Videos vs Comments)
                        item {
                            LazyRow(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (tvSeasons.isNotEmpty()) {
                                    item {
                                        val totalEpCount = tvSeasons.sumOf { it.episodes.size }
                                        FilterChip(
                                            selected = selectedPillTab == "EPISODES",
                                            onClick = { selectedPillTab = "EPISODES" },
                                            label = {
                                                Text(
                                                    text = if (totalEpCount > 0) "Episodes ($totalEpCount)" else "Episodes",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Tv,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                    }
                                }

                                item {
                                    FilterChip(
                                        selected = selectedPillTab == "RELATED",
                                        onClick = { selectedPillTab = "RELATED" },
                                        label = {
                                            Text(
                                                text = "Related",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.VideoLibrary,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                }

                                item {
                                    FilterChip(
                                        selected = selectedPillTab == "COMMENTS",
                                        onClick = { selectedPillTab = "COMMENTS" },
                                        label = {
                                            Text(
                                                text = "Comments (${videoComments.size})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Comment,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                }
                            }
                        }

                        // Tab Content Section
                        if (selectedPillTab == "EPISODES" && (tvSeasons.isNotEmpty() || isSeasonsLoading)) {
                            if (isSeasonsLoading && tvSeasons.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else {
                                // Season Selector Row
                                item {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(tvSeasons, key = { "season_${it.seasonNumber}" }) { season ->
                                            val isSelected = season.seasonNumber == selectedSeasonNumber
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.clickable { selectedSeasonNumber = season.seasonNumber }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Text(
                                                        text = season.name,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 13.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "(${season.episodes.size})",
                                                        fontSize = 11.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Episodes of selected season
                                val currentSeason = tvSeasons.firstOrNull { it.seasonNumber == selectedSeasonNumber } ?: tvSeasons.firstOrNull()
                                val episodesList = currentSeason?.episodes ?: emptyList()

                                items(episodesList, key = { "ep_${it.id}_s${it.seasonNumber}_e${it.episodeNumber}" }) { episode ->
                                    val isCurrentPlaying = (currentStreamData?.selectedStreamOption?.videoUrl == episode.id) ||
                                            (displayTitle.contains("E${episode.episodeNumber}", ignoreCase = true) && displayTitle.contains("S${episode.seasonNumber}", ignoreCase = true))

                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrentPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        border = if (isCurrentPlaying) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .clickable {
                                                viewModel.playEpisode(episode, currentStreamData)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Episode Still / Thumbnail
                                            Box(
                                                modifier = Modifier
                                                    .width(112.dp)
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                if (!episode.thumbnailUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = episode.thumbnailUrl,
                                                        contentDescription = episode.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayCircleOutline,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                }

                                                // Episode number badge overlay
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color.Black.copy(alpha = 0.75f),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(4.dp)
                                                ) {
                                                    Text(
                                                        text = "EP ${episode.episodeNumber}",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }

                                                // Play icon overlay if active
                                                if (isCurrentPlaying) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.4f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Playing",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Episode Metadata
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "Episode ${episode.episodeNumber}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                        fontWeight = FontWeight.SemiBold
                                                    )

                                                    if (episode.voteAverage != null && episode.voteAverage > 0f) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.Star,
                                                                contentDescription = null,
                                                                tint = Color(0xFFFFB800),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text(
                                                                text = String.format("%.1f", episode.voteAverage),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = episode.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (!episode.overview.isNullOrBlank()) {
                                                    Text(
                                                        text = episode.overview,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        lineHeight = 15.sp,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }

                                                if (isCurrentPlaying) {
                                                    Text(
                                                        text = "▶ Now Playing",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(top = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (selectedPillTab == "COMMENTS") {
                            item {
                                com.example.ui.components.VideoCommentsSection(
                                    comments = videoComments,
                                    isLoading = isCommentsLoading,
                                    onAddComment = { text ->
                                        activeVideoId?.let { vid -> viewModel.addComment(text, vid) }
                                    },
                                    onLikeComment = { commentId ->
                                        viewModel.toggleCommentLike(commentId)
                                    },
                                    onSeekToTimestamp = { ms ->
                                        GlobalPlayerManager.seekTo(ms)
                                    }
                                )
                            }
                        } else {
                            // Related Videos List
                            if (relatedContent.isNotEmpty()) {
                                items(
                                    items = relatedContent,
                                    key = { "rel_${it.providerId ?: ""}_${it.id}" },
                                    contentType = { "video_card" }
                                ) { video ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 0.dp)
                                    ) {
                                        VideoCard(
                                            video = video,
                                            showProviderBadge = showThumbnailTags,
                                            onClick = {
                                                viewModel.playVideo(video.id, video.providerId)
                                            },
                                            onChannelClick = { channelName -> viewModel.openChannel(channelName) },
                                            onNotInterested = { v -> viewModel.markNotInterested(v) },
                                            onPlayNextInQueue = { v -> viewModel.playNextInQueue(v) },
                                            onAddToQueue = { v -> viewModel.addToQueue(v) },
                                            onSaveToWatchLater = { v -> viewModel.addToWatchLater(v) },
                                            onDownload = { v -> viewModel.showDownloadSheet(v) }
                                        )
                                    }
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No related videos available.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (isLoadingMore || isLoadingPlayerRecs) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

    // MODAL BOTTOM SHEET: DOWNLOAD QUALITY PICKER
    if (showDownloadQualitySheet) {
        DownloadQualityBottomSheet(
            videoTitle = currentStreamData?.title ?: currentVideoItem?.title ?: "Video",
            channelName = currentStreamData?.channelName ?: currentVideoItem?.uploaderName ?: "Channel",
            thumbnailUrl = currentStreamData?.channelAvatarUrl ?: currentVideoItem?.thumbnailUrl,
            durationText = currentVideoItem?.formattedDuration,
            availableOptions = currentStreamData?.availableStreamOptions ?: emptyList(),
            onConfirmDownload = { qualityLabel, chosenStreamOption ->
                showDownloadQualitySheet = false
                val targetVideoId = activeVideoId ?: currentStreamData?.videoId ?: currentVideoItem?.id
                if (!targetVideoId.isNullOrBlank()) {
                    viewModel.startDownload(
                        videoId = targetVideoId,
                        title = currentStreamData?.title ?: currentVideoItem?.title ?: "Video",
                        channelName = currentStreamData?.channelName ?: currentVideoItem?.uploaderName ?: "Channel",
                        thumbnailUrl = currentStreamData?.channelAvatarUrl ?: currentVideoItem?.thumbnailUrl,
                        qualityLabel = qualityLabel,
                        streamOption = chosenStreamOption ?: selectedOption
                    )
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Download started in $qualityLabel")
                    }
                }
            },
            onDismissRequest = { showDownloadQualitySheet = false }
        )
    }

    // UNIFIED SERVERS & SOURCES SELECTOR SHEET (Vega Direct Streams + BitTorrent Swarms)
    if (showServerSelectorSheet) {
        com.example.ui.player.UnifiedServerSelectorSheet(
            candidates = unifiedCandidates,
            activeCandidate = activeSourceCandidate,
            isResolving = isResolvingUnifiedSources,
            statusMessage = unifiedStatusMessage,
            onSelectCandidate = { candidate ->
                showServerSelectorSheet = false
                viewModel.switchUnifiedSource(candidate)
            },
            onDismiss = { showServerSelectorSheet = false }
        )
    }

    // SAVE TO PLAYLIST SHEET
    if (showSaveToPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSaveToPlaylistSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Save video to...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            showSaveToPlaylistSheet = false
                            showCreatePlaylistDialog = true
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Playlist")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Watch Later item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentVideoItem?.let { video ->
                                if (isSavedInWatchLater) viewModel.removeFromWatchLater(video)
                                else viewModel.addToWatchLater(video)
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSavedInWatchLater,
                        onCheckedChange = { checked ->
                            currentVideoItem?.let { video ->
                                if (checked) viewModel.addToWatchLater(video)
                                else viewModel.removeFromWatchLater(video)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Watch later",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Custom User Playlists
                userPlaylists.forEach { playlist ->
                    val isVideoInPlaylist = currentVideoItem != null && playlist.videos.any { it.id == currentVideoItem.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentVideoItem?.let { video ->
                                    if (isVideoInPlaylist) {
                                        viewModel.removeFromPlaylist(playlist.id, video)
                                    } else {
                                        viewModel.addToPlaylist(playlist.id, video)
                                    }
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isVideoInPlaylist,
                            onCheckedChange = { checked ->
                                currentVideoItem?.let { video ->
                                    if (checked) viewModel.addToPlaylist(playlist.id, video)
                                    else viewModel.removeFromPlaylist(playlist.id, video)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = playlist.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showSaveToPlaylistSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }

    // CREATE PLAYLIST DIALOG
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text("Playlist Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistTitle.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistTitle)
                            val created = viewModel.userPlaylists.value.lastOrNull()
                            if (created != null && currentVideoItem != null) {
                                viewModel.addToPlaylist(created.id, currentVideoItem)
                            }
                            newPlaylistTitle = ""
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QueueSection(
    queue: List<VideoItem>,
    onPlayItem: (VideoItem) -> Unit,
    onRemoveItem: (VideoItem) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Queue • ${queue.size} video${if (queue.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Temporary",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                TextButton(
                    onClick = onClearQueue,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Queue",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Queued items in a horizontal scrollable row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(queue, key = { index, item -> "queue_${item.id}_$index" }) { index, item ->
                    Card(
                        onClick = { onPlayItem(item) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.width(180.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = item.thumbnailUrl ?: "https://i.ytimg.com/vi/${item.id}/hqdefault.jpg",
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (index == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = "Playing next",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onRemoveItem(item) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                if (item.formattedDuration.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.Black.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = item.formattedDuration,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = item.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = item.uploaderName,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
