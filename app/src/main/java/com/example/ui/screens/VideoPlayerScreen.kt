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

    val initialPositionMs = remember(activeVideoId) { activeVideoId?.let { watchPositionMsMap[it] } ?: 0L }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }
    var showSaveToPlaylistSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

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

    val nextEpisode = remember(allEpisodes, currentEpisodeIndex) {
        if (currentEpisodeIndex >= 0 && currentEpisodeIndex + 1 < allEpisodes.size) {
            allEpisodes[currentEpisodeIndex + 1]
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

    val isTvSeries = remember(currentStreamData, seasonsAndEpisodes) {
        if (currentStreamData == null) false
        else {
            val totalEpisodes = seasonsAndEpisodes.sumOf { it.episodes.size }
            totalEpisodes > 1
        }
    }

    var selectedPlayerTab by remember(isTvSeries) {
        mutableStateOf(if (isTvSeries) com.example.ui.components.PlayerTab.SEASONS_EPISODES else com.example.ui.components.PlayerTab.RELATED)
    }

    val trendingVideos by viewModel.trendingVideos.collectAsState()
    val failedSourceLogs by viewModel.failedSourceLogs.collectAsState()

    LaunchedEffect(currentStreamData?.videoId) {
        if (currentStreamData != null) {
            try {
                val tmdbSeasons = com.example.util.TMDBHelper.fetchTvSeasonsAndEpisodes(currentStreamData)
                if (tmdbSeasons.isNotEmpty()) {
                    seasonsAndEpisodes = tmdbSeasons
                }
            } catch (e: Exception) {
                // Keep initial generated seasons
            }
        } else {
            seasonsAndEpisodes = emptyList()
        }
    }

    val relatedContent = remember(currentStreamData, trendingVideos) {
        currentStreamData?.let { com.example.util.SeriesDataHelper.getRelatedContent(it, trendingVideos) } ?: emptyList()
    }

    val recommendedContent = remember(currentStreamData, trendingVideos) {
        currentStreamData?.let { com.example.util.SeriesDataHelper.getRecommendedContent(it, trendingVideos) } ?: emptyList()
    }

    val activeProviderItem = availableProviders.firstOrNull { it.id == providerId }
    val isTorrentStream = remember(activeProviderItem, currentStreamData, selectedOption) {
        (activeProviderItem?.isTorrent == true) ||
                (currentStreamData?.isTorrent == true) ||
                (selectedOption?.isTorrent == true)
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

    LaunchedEffect(currentStreamData?.videoId, activeVideoId) {
        if (currentStreamData != null) {
            isCommentsLoading = true
            val titleToFetch = currentStreamData.title
            val vid = activeVideoId ?: currentStreamData.videoId
            val res = com.example.util.TorrentReviewFetcher.fetchReviewsForTorrent(
                title = titleToFetch,
                videoId = vid,
                providerId = providerId
            )
            torrentReviewsResult = res
            fetchedComments = res.reviews
            isCommentsLoading = false
        } else {
            fetchedComments = emptyList()
            isCommentsLoading = false
        }
    }

    val playerComments = fetchedComments

    val currentVideoItem = remember(currentStreamData, activeVideoId) {
        currentStreamData?.let { data ->
            VideoItem(
                id = activeVideoId ?: "playing_video",
                title = data.title,
                uploaderName = data.channelName,
                thumbnailUrl = data.thumbnailUrl,
                providerId = providerId ?: "youtube"
            )
        }
    }

    val firstFrameRendered by GlobalPlayerManager.firstFrameRendered.collectAsState()

    val displayPosterUrl = currentStreamData?.effectiveThumbnailUrl ?: currentVideoItem?.thumbnailUrl
    val displayTitle = currentStreamData?.title ?: currentVideoItem?.title ?: ""
    val extractionError = (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Error)?.errorDetails

    val isSavedInWatchLater = currentVideoItem != null && watchLaterList.any { it.id == currentVideoItem.id }

    val listState = rememberLazyListState()
    val isScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 420
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
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
                failedSourceLogs = failedSourceLogs,
                onProgressUpdate = { pos, dur ->
                    activeVideoId?.let { id -> viewModel.recordWatchProgress(id, pos, dur) }
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
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentStreamData?.title ?: "Now Playing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (providerName.isNotEmpty()) {
                            Text(
                                text = "Source: $providerName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize to Mini Player",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { viewModel.closeVideo() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Video",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
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
                    failedSourceLogs = failedSourceLogs,
                    onProgressUpdate = { pos, dur ->
                        activeVideoId?.let { id -> viewModel.recordWatchProgress(id, pos, dur) }
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
            }

            // CONTAINER FOR SCROLLABLE CONTENT & STICKY LIQUID GLASS FLOATING ACTION TOOLBAR
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Video Details Section
                    if (currentStreamData != null) {
                        item {
                            VideoDetailsSection(
                                streamData = currentStreamData,
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
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Video link copied to clipboard")
                                    }
                                },
                                onCommentsClick = { showCommentsSheet = true }
                            )
                        }
                    }

                    // Interactive Player Tab Bar (Seasons & Episodes, Related, Recommended, Comments)
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            com.example.ui.components.PlayerTabBar(
                                selectedTab = selectedPlayerTab,
                                onTabSelected = { selectedPlayerTab = it },
                                showSeasonsTab = isTvSeries,
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
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        VideoCard(
                                            video = video,
                                            onClick = {
                                                viewModel.playVideo(video.id, video.providerId)
                                            }
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
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        VideoCard(
                                            video = video,
                                            onClick = {
                                                viewModel.playVideo(video.id, video.providerId)
                                            }
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
                                        isTorrent = isTorrentStream,
                                        isLoading = isCommentsLoading,
                                        totalReviewsCountText = torrentReviewsResult?.let { "${it.totalCount}" }
                                    )
                                }
                            }
                        }
                    }
                }

                // STICKY LIQUID GLASS FLOATING ACTION CAPSULES AT BOTTOM (ONLY APPEARS ON SCROLLING DOWN)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isScrolledPastHeader,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // CAPSULE 1 (LEFT): LIKE, DISLIKE, COMMENTS
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = 12.dp,
                            modifier = Modifier
                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                                .border(width = 1.dp, color = Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(28.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // LIKE
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            isLiked = !isLiked
                                            if (isLiked) isDisliked = false
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                        contentDescription = "Like",
                                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isLiked) "38K" else "37K",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // DISLIKE
                                IconButton(
                                    onClick = {
                                        isDisliked = !isDisliked
                                        if (isDisliked) isLiked = false
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                        contentDescription = "Dislike",
                                        tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                // COMMENTS
                                IconButton(
                                    onClick = { showCommentsSheet = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = "Comments",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        // CAPSULE 2 (RIGHT): SAVE / WATCH LATER, MORE / SHARE
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = 12.dp,
                            modifier = Modifier
                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                                .border(width = 1.dp, color = Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(28.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // SAVE (TAP FOR WATCH LATER, LONG PRESS FOR PLAYLISTS)
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            onClick = {
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
                                            onLongClick = {
                                                showSaveToPlaylistSheet = true
                                            }
                                        )
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSavedInWatchLater) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Save video",
                                        tint = if (isSavedInWatchLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // MORE OPTIONS / SHARE
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Video link copied to clipboard")
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreHoriz,
                                        contentDescription = "More",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
