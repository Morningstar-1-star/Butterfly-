package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.ErrorDiagnosticCard
import com.example.ui.components.TorrentArtworkOverlay
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoDetailsSection
import com.example.ui.components.DownloadQualityBottomSheet
import com.example.ui.components.LandscapeRelatedDrawer
import com.example.ui.player.GlobalPlayerManager
import com.example.ui.player.YouTubePlayerView
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
    val serverScanState by viewModel.serverScanState.collectAsState()

    val currentStreamData = (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData
    val providerId = currentStreamData?.providerId
    val providerName = availableProviders.firstOrNull { it.id == providerId }?.name ?: providerId ?: "Video Player"

    val studioOrChannelName = remember(currentStreamData, providerId, availableProviders) {
        val rawCh = currentStreamData?.channelName?.takeIf { it.isNotBlank() }
        if (rawCh != null && !rawCh.contains("Torrent", ignoreCase = true) && rawCh != "Butterfly Stream") {
            rawCh
        } else {
            val pName = availableProviders.firstOrNull { it.id == providerId }?.name ?: providerId ?: ""
            if (pName.isNotBlank() && !pName.contains("Torrent", ignoreCase = true)) pName else "Official Channel"
        }
    }

    val initialPositionMs = remember(activeVideoId) { activeVideoId?.let { watchPositionMsMap[it] } ?: 0L }

    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }
    var showSaveToPlaylistSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var showDownloadQualitySheet by remember { mutableStateOf(false) }

    val offlineDownloads by viewModel.offlineDownloads.collectAsState()
    val liveProgressMap by viewModel.downloadLiveProgress.collectAsState()

    val isDownloaded = remember(offlineDownloads, activeVideoId) {
        offlineDownloads.any { it.videoId == activeVideoId && it.status == "COMPLETED" }
    }
    val currentDownloadInfo = activeVideoId?.let { liveProgressMap[it] }
    val isDownloading = currentDownloadInfo?.status == "DOWNLOADING" || 
        offlineDownloads.any { it.videoId == activeVideoId && it.status == "DOWNLOADING" }
    val downloadProgressFraction = currentDownloadInfo?.progress ?: 0f

    var seasonsAndEpisodes by remember(currentStreamData?.videoId) {
        mutableStateOf<List<com.example.model.SeriesSeason>>(
            currentStreamData?.let { com.example.util.SeriesDataHelper.generateSeasonsAndEpisodes(it) } ?: emptyList()
        )
    }

    val playbackEnded by com.example.ui.player.GlobalPlayerManager.playbackEnded.collectAsState()

    val allEpisodes = remember(seasonsAndEpisodes) {
        seasonsAndEpisodes.flatMap { it.episodes }
    }

    val currentEpisodeIndex = remember(allEpisodes, activeVideoId, selectedOption) {
        allEpisodes.indexOfFirst { ep ->
            ep.id == activeVideoId ||
            selectedOption?.videoUrl == ep.id ||
            (selectedOption?.qualityLabel?.contains("s${ep.seasonNumber}e${ep.episodeNumber}", ignoreCase = true) == true)
        }
    }

    val globalPlayerPosMs by com.example.ui.player.GlobalPlayerManager.currentPositionMs.collectAsState()
    val globalPlayerDurationMs by com.example.ui.player.GlobalPlayerManager.durationMs.collectAsState()

    val nextEpisode = remember(allEpisodes, currentEpisodeIndex) {
        if (currentEpisodeIndex >= 0 && currentEpisodeIndex + 1 < allEpisodes.size) {
            allEpisodes[currentEpisodeIndex + 1]
        } else null
    }

    val previousEpisode = remember(allEpisodes, currentEpisodeIndex) {
        if (currentEpisodeIndex > 0 && currentEpisodeIndex < allEpisodes.size) {
            allEpisodes[currentEpisodeIndex - 1]
        } else null
    }

    var autoPlayCountdown by remember { mutableIntStateOf(5) }
    var isUpNextActive by remember { mutableStateOf(false) }

    val playTargetEpisode: (EpisodeItem) -> Unit = { episode ->
        val sNum = episode.seasonNumber
        val eNum = episode.episodeNumber
        val match = currentStreamData?.availableStreamOptions?.firstOrNull { opt ->
            val qLabel = opt.qualityLabel.lowercase()
            opt.videoUrl == episode.id ||
            qLabel == episode.title.lowercase() ||
            qLabel.contains("s${sNum}e${eNum}") ||
            qLabel.contains("s0${sNum}e0${eNum}") ||
            qLabel.contains("s${sNum} e${eNum}") ||
            qLabel.contains("ep ${eNum}") ||
            qLabel.contains("episode ${eNum}")
        }
        if (match != null) {
            viewModel.selectStreamOption(match)
        } else {
            viewModel.playVideo(episode.id, episode.providerId)
        }
    }

    LaunchedEffect(playbackEnded, nextEpisode) {
        if (playbackEnded && nextEpisode != null) {
            isUpNextActive = true
            autoPlayCountdown = 5
            while (autoPlayCountdown > 0 && isUpNextActive) {
                delay(1000)
                autoPlayCountdown--
            }
            if (isUpNextActive && autoPlayCountdown == 0) {
                isUpNextActive = false
                com.example.ui.player.GlobalPlayerManager.clearPlaybackEnded()
                playTargetEpisode(nextEpisode)
            }
        } else {
            isUpNextActive = false
        }
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

    val activeProviderItem = availableProviders.firstOrNull { it.id == providerId }
    val isJavOrAdult = remember(currentStreamData, providerId) {
        com.example.util.TMDBHelper.isJavOrAdultProvider(currentStreamData?.providerId ?: providerId, currentStreamData?.title)
    }

    val isTorrentStream = remember(activeProviderItem, currentStreamData, selectedOption, isJavOrAdult) {
        if (isJavOrAdult) false
        else (activeProviderItem?.isTorrent == true) ||
                (currentStreamData?.isTorrent == true) ||
                (selectedOption?.isTorrent == true) ||
                (currentStreamData?.providerId?.lowercase()?.contains("eztv") == true) ||
                (currentStreamData?.providerId?.lowercase()?.contains("torrent") == true) ||
                (currentStreamData?.providerId?.lowercase()?.contains("yts") == true)
    }

    val isTvSeries = remember(currentStreamData, seasonsAndEpisodes, isJavOrAdult) {
        if (currentStreamData == null || isJavOrAdult) false
        else {
            val totalEpisodes = seasonsAndEpisodes.sumOf { it.episodes.size }
            totalEpisodes > 1
        }
    }

    var selectedPlayerTab by remember(isTvSeries, isJavOrAdult) {
        mutableStateOf(if (isTvSeries && !isJavOrAdult) com.example.ui.components.PlayerTab.SEASONS_EPISODES else com.example.ui.components.PlayerTab.RELATED)
    }

    val trendingVideos by viewModel.trendingVideos.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val activeVideoItem by viewModel.activeVideoItem.collectAsState()
    val failedSourceLogs by viewModel.failedSourceLogs.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

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

    LaunchedEffect(currentStreamData?.videoId, isTorrentStream, isJavOrAdult) {
        if (isJavOrAdult || currentStreamData == null) {
            seasonsAndEpisodes = emptyList()
        } else if (isTorrentStream) {
            try {
                val tmdbSeasons = com.example.util.TMDBHelper.fetchTvSeasonsAndEpisodes(currentStreamData)
                if (tmdbSeasons.isNotEmpty()) {
                    seasonsAndEpisodes = tmdbSeasons
                }
            } catch (e: Exception) {
                // Keep initial generated seasons
            }
        }
    }

    val hiddenVideoIds by viewModel.hiddenVideoIds.collectAsState()
    val notInterestedVideoIds by viewModel.notInterestedVideoIds.collectAsState()
    val notInterestedChannels by viewModel.notInterestedChannels.collectAsState()

    val relatedContent = remember(currentStreamData, trendingVideos, activeVideoId, hiddenVideoIds, notInterestedVideoIds, notInterestedChannels) {
        val base = if (currentStreamData != null) {
            com.example.util.SeriesDataHelper.getRelatedContent(currentStreamData, trendingVideos)
        } else {
            trendingVideos.filter { it.id != activeVideoId }
        }
        base.filterNot { viewModel.isBlockedVideo(it) }.take(10)
    }

    val recommendedContent = remember(currentStreamData, trendingVideos, activeVideoId, hiddenVideoIds, notInterestedVideoIds, notInterestedChannels) {
        val base = if (currentStreamData != null) {
            com.example.util.SeriesDataHelper.getRecommendedContent(currentStreamData, trendingVideos)
        } else {
            trendingVideos.filter { it.id != activeVideoId }
        }
        base.filterNot { viewModel.isBlockedVideo(it) }.take(10)
    }

    var fetchedComments by remember(currentStreamData?.videoId, activeVideoId) {
        mutableStateOf<List<com.example.model.VideoComment>>(emptyList())
    }
    var isCommentsLoading by remember(currentStreamData?.videoId, activeVideoId) {
        mutableStateOf(false)
    }
    var torrentReviewsResult by remember(currentStreamData?.videoId, activeVideoId) {
        mutableStateOf<com.example.util.TorrentReviewsResult?>(null)
    }

    LaunchedEffect(currentStreamData?.videoId, activeVideoId, currentVideoItem?.title, providerId) {
        val vid = activeVideoId ?: currentStreamData?.videoId ?: ""
        val titleToFetch = currentStreamData?.title ?: currentVideoItem?.title ?: vid
        if (vid.isNotBlank() || titleToFetch.isNotBlank()) {
            isCommentsLoading = true
            val isYt = providerId == "youtube" || (vid.length == 11 && !vid.startsWith("movie_") && !vid.startsWith("tv_"))
            var comments = emptyList<com.example.model.VideoComment>()
            if (isYt && vid.isNotBlank()) {
                comments = com.example.util.YouTubeApiHelper.fetchCommentsForVideo(vid)
            }
            if (comments.isNotEmpty()) {
                fetchedComments = comments
                torrentReviewsResult = com.example.util.TorrentReviewsResult(
                    reviews = comments,
                    totalCount = comments.size,
                    averageRating = 0f,
                    mediaTitle = titleToFetch
                )
            } else {
                val res = com.example.util.TorrentReviewFetcher.fetchReviewsForTorrent(
                    title = titleToFetch,
                    videoId = vid,
                    providerId = providerId
                )
                torrentReviewsResult = res
                fetchedComments = res.reviews
            }
            isCommentsLoading = false
        } else {
            fetchedComments = emptyList()
            isCommentsLoading = false
        }
    }

    val reactionGroups by viewModel.reactionGroups.collectAsState()
    val isLoadingReactions by viewModel.isLoadingReactions.collectAsState()

    val showReactionsTab = remember(currentVideoItem, currentStreamData) {
        com.example.util.ReactionHelper.isReactionEligible(currentVideoItem, currentStreamData)
    }

    LaunchedEffect(currentVideoItem?.title, currentStreamData?.title, showReactionsTab) {
        if (showReactionsTab) {
            val title = currentStreamData?.title ?: currentVideoItem?.title ?: ""
            val uploader = currentStreamData?.channelName ?: currentVideoItem?.uploaderName ?: ""
            val isTorrent = currentStreamData?.isTorrent == true || isTorrentStream
            if (title.isNotBlank()) {
                viewModel.loadReactionsForCurrentVideo(title, uploader, isTorrent)
            }
        }
    }

    val playerComments = fetchedComments

    val firstFrameRendered by GlobalPlayerManager.firstFrameRendered.collectAsState()

    val displayPosterUrl = currentStreamData?.effectiveThumbnailUrl ?: currentVideoItem?.thumbnailUrl
    val displayTitle = currentStreamData?.title ?: currentVideoItem?.title ?: ""
    val extractionError = (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Error)?.errorDetails

    val isSavedInWatchLater = currentVideoItem != null && watchLaterList.any { it.id == currentVideoItem.id }

    val listState = rememberLazyListState()
    var showLandscapeRelatedDrawer by remember { mutableStateOf(false) }

    val shouldLoadMorePlayerList = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMorePlayerList.value) {
        if (shouldLoadMorePlayerList.value && !isLoadingMore) {
            viewModel.loadMoreContent()
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val landscapeVideos = remember(relatedContent, recommendedContent, trendingVideos, activeVideoId) {
        (relatedContent + recommendedContent + trendingVideos)
            .distinctBy { it.id }
            .filter { it.id != activeVideoId }
    }

    if (isLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount < -25f && !showLandscapeRelatedDrawer) {
                            change.consume()
                            showLandscapeRelatedDrawer = true
                        } else if (dragAmount > 25f && showLandscapeRelatedDrawer) {
                            change.consume()
                            showLandscapeRelatedDrawer = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            YouTubePlayerView(
                streamOption = selectedOption,
                hlsUrl = currentStreamData?.hlsUrl ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.hlsUrl,
                captionOption = selectedCaption,
                embedUrl = currentStreamData?.embedUrl ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.embedUrl,
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
                    if (nextEpisode != null) {
                        playTargetEpisode(nextEpisode)
                    } else if (landscapeVideos.isNotEmpty()) {
                        val nextVid = landscapeVideos.first()
                        viewModel.playVideo(nextVid.id, nextVid.providerId)
                    } else {
                        val curMs = com.example.ui.player.GlobalPlayerManager.currentPositionMs.value
                        com.example.ui.player.GlobalPlayerManager.seekTo(curMs + 10000L)
                    }
                },
                onPreviousClick = {
                    if (previousEpisode != null) {
                        playTargetEpisode(previousEpisode)
                    } else {
                        val curMs = com.example.ui.player.GlobalPlayerManager.currentPositionMs.value
                        if (curMs > 5000L) {
                            com.example.ui.player.GlobalPlayerManager.seekTo(0L)
                        } else {
                            com.example.ui.player.GlobalPlayerManager.seekTo((curMs - 10000L).coerceAtLeast(0L))
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            TorrentArtworkOverlay(
                isTorrent = isTorrentStream,
                title = displayTitle,
                posterUrl = displayPosterUrl,
                isExtracting = isExtracting,
                statusMessage = "Loading video stream from $providerName...",
                firstFrameRendered = firstFrameRendered,
                extractionError = extractionError,
                onRetry = {
                    activeVideoId?.let { id -> viewModel.playVideo(id, providerId) }
                }
            )

            if (isUpNextActive && nextEpisode != null) {
                UpNextOverlay(
                    nextEpisode = nextEpisode,
                    countdownSecs = autoPlayCountdown,
                    onPlayNow = {
                        isUpNextActive = false
                        com.example.ui.player.GlobalPlayerManager.clearPlaybackEnded()
                        playTargetEpisode(nextEpisode)
                    },
                    onCancel = {
                        isUpNextActive = false
                        com.example.ui.player.GlobalPlayerManager.clearPlaybackEnded()
                    }
                )
            }

            com.example.sponsorblock.ui.SponsorBlockPlayerOverlay(
                videoId = activeVideoId ?: currentStreamData?.videoId,
                currentPositionMs = globalPlayerPosMs,
                durationMs = globalPlayerDurationMs,
                onSeekTo = { targetMs -> com.example.ui.player.GlobalPlayerManager.seekTo(targetMs) },
                streamTitle = currentStreamData?.title
            )

            // Landscape Related Videos Drawer
            LandscapeRelatedDrawer(
                isVisible = showLandscapeRelatedDrawer,
                videos = landscapeVideos,
                currentVideoId = activeVideoId,
                onVideoClick = { video ->
                    viewModel.playVideo(video.id, video.providerId)
                },
                onDismiss = {
                    showLandscapeRelatedDrawer = false
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = modifier.fillMaxSize().statusBarsPadding(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
        ) {
            // STICKY PLAYER AREA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            if (dragAmount > 25f) {
                                change.consume()
                                onBackClick()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                YouTubePlayerView(
                    streamOption = selectedOption,
                    hlsUrl = currentStreamData?.hlsUrl ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.hlsUrl,
                    captionOption = selectedCaption,
                    embedUrl = currentStreamData?.embedUrl ?: (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData?.embedUrl,
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
                        if (nextEpisode != null) {
                            playTargetEpisode(nextEpisode)
                        } else if (landscapeVideos.isNotEmpty()) {
                            val nextVid = landscapeVideos.first()
                            viewModel.playVideo(nextVid.id, nextVid.providerId)
                        } else {
                            val curMs = com.example.ui.player.GlobalPlayerManager.currentPositionMs.value
                            com.example.ui.player.GlobalPlayerManager.seekTo(curMs + 10000L)
                        }
                    },
                    onPreviousClick = {
                        if (previousEpisode != null) {
                            playTargetEpisode(previousEpisode)
                        } else {
                            val curMs = com.example.ui.player.GlobalPlayerManager.currentPositionMs.value
                            if (curMs > 5000L) {
                                com.example.ui.player.GlobalPlayerManager.seekTo(0L)
                            } else {
                                com.example.ui.player.GlobalPlayerManager.seekTo((curMs - 10000L).coerceAtLeast(0L))
                            }
                        }
                    }
                )

                TorrentArtworkOverlay(
                    isTorrent = isTorrentStream,
                    title = displayTitle,
                    posterUrl = displayPosterUrl,
                    isExtracting = isExtracting,
                    statusMessage = "Loading video stream from $providerName...",
                    firstFrameRendered = firstFrameRendered,
                    extractionError = extractionError,
                    onRetry = {
                        activeVideoId?.let { id -> viewModel.playVideo(id, providerId) }
                    }
                )

                if (isUpNextActive && nextEpisode != null) {
                    UpNextOverlay(
                        nextEpisode = nextEpisode,
                        countdownSecs = autoPlayCountdown,
                        onPlayNow = {
                            isUpNextActive = false
                            com.example.ui.player.GlobalPlayerManager.clearPlaybackEnded()
                            playTargetEpisode(nextEpisode)
                        },
                        onCancel = {
                            isUpNextActive = false
                            com.example.ui.player.GlobalPlayerManager.clearPlaybackEnded()
                        }
                    )
                }

                com.example.sponsorblock.ui.SponsorBlockPlayerOverlay(
                    videoId = activeVideoId ?: currentStreamData?.videoId,
                    currentPositionMs = globalPlayerPosMs,
                    durationMs = globalPlayerDurationMs,
                    onSeekTo = { targetMs -> com.example.ui.player.GlobalPlayerManager.seekTo(targetMs) },
                    streamTitle = currentStreamData?.title
                )
            }

            // CONTAINER FOR SCROLLABLE CONTENT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Video Details Section (Always visible with instant preview & live stream info)
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
                            onCommentsClick = { showCommentsSheet = true },
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
                            downloadProgress = downloadProgressFraction,
                            onDownloadClick = { showDownloadQualitySheet = true }
                        )
                    }

                    // Interactive Player Tab Bar (Seasons & Episodes, Related, Recommended, Comments, Reactions)
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            com.example.ui.components.PlayerTabBar(
                                selectedTab = selectedPlayerTab,
                                onTabSelected = { selectedPlayerTab = it },
                                showSeasonsTab = isTvSeries,
                                showReactionsTab = showReactionsTab,
                                commentsCount = torrentReviewsResult?.totalCount ?: playerComments.size
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // TAB CONTENT RENDERER
                    when (selectedPlayerTab) {
                        com.example.ui.components.PlayerTab.SEASONS_EPISODES -> {
                            item {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    com.example.ui.components.SeasonsAndEpisodesView(
                                        seasons = seasonsAndEpisodes,
                                        activeVideoId = activeVideoId,
                                        onEpisodeClick = { episode ->
                                            val sNum = episode.seasonNumber
                                            val eNum = episode.episodeNumber
                                            val match = currentStreamData?.availableStreamOptions?.firstOrNull { opt ->
                                                val qLabel = opt.qualityLabel.lowercase()
                                                opt.videoUrl == episode.id ||
                                                qLabel == episode.title.lowercase() ||
                                                qLabel.contains("s${sNum}e${eNum}") ||
                                                qLabel.contains("s0${sNum}e0${eNum}") ||
                                                qLabel.contains("s${sNum} e${eNum}") ||
                                                qLabel.contains("ep ${eNum}") ||
                                                qLabel.contains("episode ${eNum}")
                                            }
                                            if (match != null) {
                                                viewModel.selectStreamOption(match)
                                            } else {
                                                viewModel.playVideo(episode.id, episode.providerId)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        com.example.ui.components.PlayerTab.RELATED -> {
                            if (relatedContent.isNotEmpty()) {
                                items(relatedContent) { video ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                    ) {
                                        VideoCard(
                                            video = video,
                                            onClick = {
                                                viewModel.playVideo(video.id, video.providerId)
                                            },
                                            onChannelClick = { channelName -> viewModel.openChannel(channelName) },
                                            onNotInterested = { v -> viewModel.markNotInterested(v) },
                                            onPlayNextInQueue = { v -> viewModel.addToQueue(v) },
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
                                            text = "No related videos found.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        com.example.ui.components.PlayerTab.RECOMMENDED -> {
                            if (recommendedContent.isNotEmpty()) {
                                items(recommendedContent) { video ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                    ) {
                                        VideoCard(
                                            video = video,
                                            onClick = {
                                                viewModel.playVideo(video.id, video.providerId)
                                            },
                                            onChannelClick = { channelName -> viewModel.openChannel(channelName) },
                                            onNotInterested = { v -> viewModel.markNotInterested(v) },
                                            onPlayNextInQueue = { v -> viewModel.addToQueue(v) },
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
                                            text = "No recommendations available.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        com.example.ui.components.PlayerTab.REACTIONS -> {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp)) {
                                    com.example.ui.components.ReactionsView(
                                        reactionGroups = reactionGroups,
                                        isLoading = isLoadingReactions,
                                        onPlayVideo = { videoItem ->
                                            viewModel.playVideo(videoItem.id, videoItem.providerId)
                                        }
                                    )
                                }
                            }
                        }
                        com.example.ui.components.PlayerTab.COMMENTS -> {
                            item {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    com.example.ui.components.CommentsSectionView(
                                        comments = playerComments,
                                        onAddComment = { text ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Comment posted!")
                                            }
                                        },
                                        onSeekToTime = { seconds ->
                                            com.example.ui.player.GlobalPlayerManager.seekTo(seconds * 1000L)
                                        },
                                        isTorrent = isTorrentStream,
                                        isLoading = isCommentsLoading,
                                        totalReviewsCountText = torrentReviewsResult?.let { "${it.totalCount}" }
                                    )
                                }
                            }
                        }
                    }

                    if (isLoadingMore) {
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

    // MODAL BOTTOM SHEET: SAVE TO PLAYLIST
    if (showSaveToPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSaveToPlaylistSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Save video to...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { showCreatePlaylistDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New playlist", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Watch Later Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentVideoItem?.let { video ->
                                if (isSavedInWatchLater) {
                                    viewModel.removeFromWatchLater(video)
                                } else {
                                    viewModel.addToWatchLater(video)
                                }
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

    // COMMENTS BOTTOM SHEET
    if (showCommentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCommentsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                com.example.ui.components.CommentsSectionView(
                    comments = playerComments,
                    onAddComment = { text ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Comment posted!")
                        }
                    },
                    onSeekToTime = { seconds ->
                        com.example.ui.player.GlobalPlayerManager.seekTo(seconds * 1000L)
                        showCommentsSheet = false
                    },
                    isTorrent = isTorrentStream,
                    isLoading = isCommentsLoading,
                    totalReviewsCountText = torrentReviewsResult?.let { "${it.totalCount}" }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showCommentsSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}
}

@Composable
fun UpNextOverlay(
    nextEpisode: EpisodeItem,
    countdownSecs: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.85f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "UP NEXT IN ${countdownSecs}S",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = nextEpisode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (nextEpisode.durationText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Season ${nextEpisode.seasonNumber} Episode ${nextEpisode.episodeNumber} • ${nextEpisode.durationText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onPlayNow,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Now",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play Now")
                    }
                }
            }
        }
    }
}
