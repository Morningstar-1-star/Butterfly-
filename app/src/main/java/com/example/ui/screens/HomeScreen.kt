package com.example.ui.screens

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.AppScreen
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val activeProviderId by viewModel.activeProviderId.collectAsState()
    val availableProviders by viewModel.availableProviders.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val trendingVideos by viewModel.trendingVideos.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val isFeedRefreshing by viewModel.isFeedRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val feedError by viewModel.feedError.collectAsState()
    val activeVideoId by viewModel.activeVideoId.collectAsState()
    val extractionResult by viewModel.extractionResult.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val showShortsFeed by viewModel.showShortsFeed.collectAsState()

    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val searchDrivenRecommendations by viewModel.searchDrivenRecommendations.collectAsState()
    val latestSearchIntent by viewModel.latestSearchIntent.collectAsState()
    val recommendedVideos by viewModel.recommendedVideos.collectAsState()
    val hiddenVideoIds by viewModel.hiddenVideoIds.collectAsState()
    val notInterestedVideoIds by viewModel.notInterestedVideoIds.collectAsState()
    val notInterestedChannels by viewModel.notInterestedChannels.collectAsState()
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val showThumbnailTags by viewModel.showThumbnailTags.collectAsState()
    val enabledProviderIds by viewModel.enabledProviderIds.collectAsState()

    val userProfile by viewModel.userProfile.collectAsState()
    val globalActiveStreamData by com.example.ui.player.GlobalPlayerManager.activeStreamData.collectAsState()
    val isSearchExpandedState by viewModel.isSearchExpanded.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showPoTokenDialog by remember { mutableStateOf(false) }
    var showSourceSelectorSheet by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isSearchExpandedState) {
        isSearchExpanded = isSearchExpandedState
    }
    var activeCategory by remember { mutableStateOf("All") }
    val focusManager = LocalFocusManager.current

    var currentTabScreen by remember { mutableStateOf(AppScreen.HOME) }
    LaunchedEffect(currentScreen) {
        if (currentScreen != AppScreen.PLAYER) {
            currentTabScreen = currentScreen
        }
    }

    val feedListState = rememberLazyListState()

    val currentFeedList = trendingVideos

    var isSourceSwitching by remember(activeProviderId, activeCategory) { mutableStateOf(currentFeedList.isEmpty()) }

    LaunchedEffect(activeProviderId, activeCategory, currentFeedList, isLoadingTrending, isSearching, isFeedRefreshing) {
        if (currentFeedList.isNotEmpty()) {
            isSourceSwitching = false
        } else if (!isLoadingTrending && !isSearching && !isFeedRefreshing) {
            kotlinx.coroutines.delay(600L)
            if (currentFeedList.isEmpty() && !isLoadingTrending && !isSearching && !isFeedRefreshing) {
                isSourceSwitching = false
            }
        }
    }

    // When pull-to-refresh or category switch completes, anchor list cleanly at top so user scrolls DOWN
    LaunchedEffect(isFeedRefreshing) {
        if (!isFeedRefreshing && currentFeedList.isNotEmpty()) {
            try {
                feedListState.scrollToItem(0, 0)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(activeProviderId, activeCategory) {
        if (feedListState.firstVisibleItemIndex > 0 || feedListState.firstVisibleItemScrollOffset > 0) {
            try {
                feedListState.scrollToItem(0, 0)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(feedListState) {
        androidx.compose.runtime.snapshotFlow {
            val layoutInfo = feedListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= (total - 3).coerceAtLeast(0)
        }
        .distinctUntilChanged()
        .collect { isNearBottom ->
            if (isNearBottom && !isLoadingTrending && !isSearching && !isLoadingMore) {
                viewModel.loadMoreContent()
            }
        }
    }

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarPaddingDp = remember(statusBarTopPadding) {
        statusBarTopPadding + 56.dp + 44.dp + 6.dp
    }
    val bottomBarPaddingDp = 80.dp

    // YouTube-style collapsible top & bottom bars state
    var areBarsVisible by remember { mutableStateOf(true) }
    val isFeedAtTop by remember {
        derivedStateOf {
            feedListState.firstVisibleItemIndex == 0 && feedListState.firstVisibleItemScrollOffset <= 8
        }
    }

    LaunchedEffect(isFeedAtTop) {
        if (isFeedAtTop) {
            areBarsVisible = true
        }
    }

    LaunchedEffect(currentTabScreen, isSearchExpanded, activeProviderId, activeCategory) {
        areBarsVisible = true
    }

    val barsAnimatedFraction by animateFloatAsState(
        targetValue = if (areBarsVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 260,
            easing = FastOutSlowInEasing
        ),
        label = "bars_visibility"
    )

    val density = androidx.compose.ui.platform.LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }

    val nestedScrollConnection = remember(scrollThresholdPx) {
        val accumulatedScroll = floatArrayOf(0f)
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val deltaY = available.y
                if (source == NestedScrollSource.UserInput) {
                    val isAtTop = feedListState.firstVisibleItemIndex == 0 && feedListState.firstVisibleItemScrollOffset <= 8
                    if (deltaY < 0f) {
                        // Scrolling DOWN -> accumulate downward delta
                        if (accumulatedScroll[0] > 0f) accumulatedScroll[0] = 0f
                        accumulatedScroll[0] += deltaY
                        if (accumulatedScroll[0] < -scrollThresholdPx && !isAtTop) {
                            if (areBarsVisible) areBarsVisible = false
                            accumulatedScroll[0] = 0f
                        }
                    } else if (deltaY > 0f) {
                        // Scrolling UP -> accumulate upward delta
                        if (accumulatedScroll[0] < 0f) accumulatedScroll[0] = 0f
                        accumulatedScroll[0] += deltaY
                        if (accumulatedScroll[0] > scrollThresholdPx) {
                            if (!areBarsVisible) areBarsVisible = true
                            accumulatedScroll[0] = 0f
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }

    val categories = listOf("All", "Series", "Movies", "Anime", "Animation", "Music", "Songs", "Gaming", "Gameplay", "Tech", "Trailers", "News", "Podcasts", "Comedy", "Trending")
    val activeProviderName = availableProviders.firstOrNull { it.id == activeProviderId }?.name ?: activeProviderId

    // StreamData extracted for player / mini player
    val currentStreamData = remember(extractionResult, activeVideoId, searchResults, trendingVideos) {
        if (activeVideoId == null) {
            null
        } else {
            (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData
                ?: activeVideoId?.let { id ->
                    val match = (searchResults + trendingVideos).firstOrNull { it.id == id }
                    if (match != null) {
                        com.example.model.StreamData(
                            videoId = match.id,
                            videoUrl = "",
                            title = match.title,
                            channelName = match.uploaderName,
                            thumbnailUrl = match.thumbnailUrl,
                            providerId = match.providerId
                        )
                    } else {
                        com.example.model.StreamData(
                            videoId = id,
                            videoUrl = "",
                            title = id,
                            channelName = "Media Stream",
                            thumbnailUrl = null,
                            providerId = null
                        )
                    }
                }
        }
    }
    val isPipMode by viewModel.isPipMode.collectAsState()
    val playbackCardOriginY by viewModel.playbackCardOriginY.collectAsState()

    if (isPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            com.example.ui.player.PersistentPlayerHost(
                useController = false,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val hasActivePlayer = (activeVideoId != null)
    val isNotDefaultHome = (currentScreen != AppScreen.HOME || isSearchExpanded || hasActivePlayer || activeCategory != "All")

    BackHandler(enabled = isNotDefaultHome) {
        when {
            currentScreen == AppScreen.PLAYER -> {
                viewModel.navigateToScreen(currentTabScreen)
            }
            isSearchExpanded -> {
                viewModel.clearSearch()
                isSearchExpanded = false
            }
            currentScreen != AppScreen.HOME -> {
                viewModel.navigateToScreen(AppScreen.HOME)
            }
            hasActivePlayer -> {
                com.example.ui.player.GlobalPlayerManager.stopAndClear()
                viewModel.closeVideo()
            }
            activeCategory != "All" -> {
                activeCategory = "All"
            }
        }
    }

    if (showPoTokenDialog) {
        PoTokenDialog(
            onDismiss = { showPoTokenDialog = false },
            onApplyToken = {
                activeVideoId?.let { id -> viewModel.playVideo(id) }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        // LAYER 1: SCREEN CONTENT
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isSearchExpanded) {
                SearchScreen(
                    viewModel = viewModel,
                    onSelectVideo = { video ->
                        viewModel.playVideo(video)
                    },
                    onCloseSearch = {
                        viewModel.clearSearch()
                        isSearchExpanded = false
                    }
                )
            } else {
                AnimatedContent(
                    targetState = currentTabScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing))
                            .togetherWith(
                                fadeOut(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing))
                            )
                    },
                    label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        AppScreen.EXPLORE -> {
                            ExploreScreen(
                                viewModel = viewModel,
                                onSelectVideo = { video ->
                                    viewModel.playVideo(video)
                                },
                                topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                                bottomPadding = bottomBarPaddingDp + (if (currentStreamData != null) 72.dp else 16.dp)
                            )
                        }

                        AppScreen.SUBSCRIPTIONS -> {
                            SubscriptionsScreen(
                                viewModel = viewModel,
                                onSelectVideo = { video ->
                                    viewModel.playVideo(video)
                                },
                                onChannelClick = { chName, avatarUrl ->
                                    viewModel.openChannel(chName, avatarUrl)
                                },
                                onOpenSearch = { isSearchExpanded = true },
                                onOpenSettings = { viewModel.navigateToScreen(AppScreen.SETTINGS) },
                                topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                                bottomPadding = bottomBarPaddingDp + (if (currentStreamData != null) 72.dp else 16.dp)
                            )
                        }

                        AppScreen.CHANNEL -> {
                            ChannelScreen(
                                viewModel = viewModel,
                                onSelectVideo = { video ->
                                    viewModel.playVideo(video)
                                },
                                onBackClick = { viewModel.navigateToScreen(AppScreen.HOME) },
                                topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                                bottomPadding = bottomBarPaddingDp + (if (currentStreamData != null) 72.dp else 16.dp)
                            )
                        }

                        AppScreen.LIBRARY -> {
                            LibraryScreen(
                                viewModel = viewModel,
                                onSelectVideo = { video ->
                                    viewModel.playVideo(video)
                                },
                                onBackClick = { viewModel.navigateToScreen(AppScreen.HOME) },
                                topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                                bottomPadding = bottomBarPaddingDp + (if (currentStreamData != null) 72.dp else 16.dp)
                            )
                        }

                        AppScreen.ACCOUNT -> {
                            AccountScreen(
                                viewModel = viewModel,
                                onSelectVideo = { video ->
                                    viewModel.playVideo(video)
                                },
                                onOpenSettings = { viewModel.navigateToScreen(AppScreen.SETTINGS) },
                                onOpenMoviesAndTv = { viewModel.navigateToScreen(AppScreen.EXPLORE) },
                                topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                                bottomPadding = bottomBarPaddingDp + (if (currentStreamData != null) 72.dp else 16.dp)
                            )
                        }

                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBackClick = { viewModel.navigateToScreen(AppScreen.HOME) }
                            )
                        }

                        else -> {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val rawFeed = trendingVideos
                            val feedList = remember(rawFeed, enabledProviderIds) {
                                rawFeed.filter { viewModel.isItemProviderEnabled(it.providerId, enabledProviderIds) }
                            }
                            val shortsFeedList = remember(rawFeed, enabledProviderIds) {
                                rawFeed.filter { viewModel.isItemProviderEnabled(it.providerId, enabledProviderIds) }
                            }

                            LaunchedEffect(feedList) {
                                if (feedList.isNotEmpty()) {
                                    com.example.util.ThumbnailOptimizer.preloadThumbnails(context, feedList, maxCount = 12)
                                }
                            }

                            val onPlayNextInQueueAction: (VideoItem) -> Unit = remember(viewModel) { { v -> viewModel.playNextInQueue(v) } }
                            val onAddToQueueAction: (VideoItem) -> Unit = remember(viewModel) { { v -> viewModel.addToQueue(v) } }
                            val onSaveToWatchLaterAction: (VideoItem) -> Unit = remember(viewModel) { { v -> viewModel.addToWatchLater(v) } }
                            val onDownloadAction: (VideoItem) -> Unit = remember(viewModel) { { v -> viewModel.showDownloadSheet(v) } }
                            val onNotInterestedAction: (VideoItem) -> Unit = remember(viewModel) { { v -> viewModel.markNotInterested(v) } }
                            val onChannelClickAction: (String) -> Unit = remember(viewModel) { { ch -> viewModel.openChannel(ch) } }
                            val onSaveToPlaylistAction: (VideoItem) -> Unit = remember(viewModel) {
                                { v ->
                                    val userPls = viewModel.userPlaylists.value
                                    if (userPls.isNotEmpty()) {
                                        viewModel.addToPlaylist(userPls.first().id, v)
                                    } else {
                                        viewModel.createPlaylist("Favorites")
                                        val updated = viewModel.userPlaylists.value
                                        if (updated.isNotEmpty()) {
                                            viewModel.addToPlaylist(updated.first().id, v)
                                        }
                                    }
                                }
                            }

                            val pullRefreshState = rememberPullToRefreshState()
                            val isRefreshingFeed = isFeedRefreshing

                            LaunchedEffect(isRefreshingFeed) {
                                if (isRefreshingFeed) {
                                    feedListState.scrollToItem(0, 0)
                                }
                            }

                            PullToRefreshBox(
                                isRefreshing = isRefreshingFeed,
                                onRefresh = {
                                    coroutineScope.launch {
                                        feedListState.scrollToItem(0, 0)
                                    }
                                    viewModel.refreshFeed()
                                },
                                state = pullRefreshState,
                                indicator = {
                                    YouTubePullToRefreshIndicator(
                                        state = pullRefreshState,
                                        isRefreshing = isRefreshingFeed,
                                        modifier = Modifier.align(Alignment.TopCenter),
                                        topPadding = if (!isSearchExpanded) topBarPaddingDp else 16.dp,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    state = feedListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        top = if (!isSearchExpanded) topBarPaddingDp else 0.dp,
                                        bottom = bottomBarPaddingDp + (if (currentStreamData != null) 72.dp else 16.dp)
                                    )
                                ) {
                                    // FEED ERROR / SKELETON / CARDS
                                    if (feedError != null) {
                                        item {
                                            FeedErrorDiagnosticCard(
                                                errorDetails = feedError!!,
                                                onRetry = {
                                                    if (activeCategory == "All") {
                                                        viewModel.loadTrending(forceRefresh = true)
                                                    } else {
                                                        viewModel.loadHomeCategoryTopic(activeCategory)
                                                    }
                                                }
                                            )
                                        }
                                    } else if (feedList.isEmpty() && (isLoadingTrending || isFeedRefreshing || isSourceSwitching)) {
                                        item {
                                            FeedSkeletonLoading(
                                                itemCount = 5,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    } else if (feedList.isEmpty()) {
                                        item {
                                            FeedSkeletonLoading(
                                                itemCount = 5,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    } else {
                                        val isCurrentAdultContext = adultContentEnabled || viewModel.isAdultProviderId(activeProviderId)
                                        val isSingleProviderActive = activeProviderId != "all" && activeProviderId.isNotBlank()
                                        val showSearchRecsShelf = !isSingleProviderActive && activeCategory == "All" && searchDrivenRecommendations.isNotEmpty() && !latestSearchIntent.isNullOrBlank() && (
                                            if (isCurrentAdultContext) searchDrivenRecommendations.all { (viewModel.isAdultVideoItem(it) || viewModel.isAdultProviderId(it.providerId)) && !viewModel.isNormalProvider(it.providerId) }
                                            else searchDrivenRecommendations.all { !viewModel.isAdultVideoItem(it) && !viewModel.isAdultProviderId(it.providerId) }
                                        )

                                        if (!showSearchRecsShelf) {
                                            // Unified continuous items list for optimal 120fps scrolling
                                            items(
                                                 items = feedList,
                                                 key = { "${it.providerId}_${it.id}" },
                                                 contentType = { "video_card" }
                                             ) { video ->
                                                 VideoCard(
                                                     video = video,
                                                     watchProgressFraction = watchProgressMap[video.id] ?: 0f,
                                                     showProviderBadge = showThumbnailTags,
                                                     onClick = { viewModel.playVideo(video) },
                                                     onPlayNextInQueue = onPlayNextInQueueAction,
                                                     onAddToQueue = onAddToQueueAction,
                                                     onSaveToWatchLater = onSaveToWatchLaterAction,
                                                     onSaveToPlaylist = onSaveToPlaylistAction,
                                                     onDownload = onDownloadAction,
                                                     onNotInterested = onNotInterestedAction,
                                                     onChannelClick = onChannelClickAction,
                                                     modifier = Modifier.fillMaxWidth()
                                                 )
                                             }
                                        } else {
                                            val shelfInsertIndex = 2.coerceAtMost(feedList.size)
                                            items(
                                                 items = feedList.take(shelfInsertIndex),
                                                 key = { "${it.providerId}_${it.id}" },
                                                 contentType = { "video_card" }
                                             ) { video ->
                                                 VideoCard(
                                                     video = video,
                                                     watchProgressFraction = watchProgressMap[video.id] ?: 0f,
                                                     showProviderBadge = showThumbnailTags,
                                                     onClick = { viewModel.playVideo(video) },
                                                     onPlayNextInQueue = onPlayNextInQueueAction,
                                                     onAddToQueue = onAddToQueueAction,
                                                     onSaveToWatchLater = onSaveToWatchLaterAction,
                                                     onSaveToPlaylist = onSaveToPlaylistAction,
                                                     onDownload = onDownloadAction,
                                                     onNotInterested = onNotInterestedAction,
                                                     onChannelClick = onChannelClickAction,
                                                     modifier = Modifier.fillMaxWidth()
                                                 )
                                             }

                                             if (feedList.size > shelfInsertIndex) {
                                                 items(
                                                     items = feedList.drop(shelfInsertIndex),
                                                     key = { "${it.providerId}_${it.id}" },
                                                     contentType = { "video_card" }
                                                 ) { video ->
                                                     VideoCard(
                                                         video = video,
                                                         watchProgressFraction = watchProgressMap[video.id] ?: 0f,
                                                         showProviderBadge = showThumbnailTags,
                                                         onClick = { viewModel.playVideo(video) },
                                                         onPlayNextInQueue = onPlayNextInQueueAction,
                                                         onAddToQueue = onAddToQueueAction,
                                                         onSaveToWatchLater = onSaveToWatchLaterAction,
                                                         onSaveToPlaylist = onSaveToPlaylistAction,
                                                         onDownload = onDownloadAction,
                                                         onNotInterested = onNotInterestedAction,
                                                         onChannelClick = onChannelClickAction,
                                                         modifier = Modifier.fillMaxWidth()
                                                     )
                                                 }
                                             }
                                        }

                                        if (isLoadingMore) {
                                            item {
                                                VideoCardSkeleton(
                                                    shimmerBrush = rememberShimmerBrush(),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 16.dp)
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
        }

        // LAYER 2: YOUTUBE-STYLE COLLAPSIBLE TOP BAR OVERLAY (ONLY ON HOME SCREEN)
        if (!isSearchExpanded && currentScreen == AppScreen.HOME) {
            // Status bar solid background shield
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background)
                    .zIndex(10f)
            )

            val activeContextTitle = globalActiveStreamData?.title 
                ?: currentStreamData?.title 
                ?: trendingVideos.firstOrNull()?.title 
                ?: searchQuery

            val smartTagsList = remember(activeContextTitle, recentSearches, activeProviderId, adultContentEnabled) {
                if (activeProviderId == "bilibili") {
                    listOf("All", "Live", "Anime", "Bangumi", "Music", "Gaming", "Technology", "Dance", "Entertainment", "Life", "Food", "Film & TV")
                } else if (activeProviderId == "hanime1") {
                    listOf("All", "New Releases", "OVA", "Uncensored", "Isekai", "Fantasy", "School", "Comedy", "Cosplay", "3D", "Subbed")
                } else if (activeProviderId == "tencent") {
                    listOf("All", "Dramas", "Anime", "Donghua", "Costume Drama", "Romance", "Movies", "Action", "Wuxia & Fantasy", "Variety Shows", "Documentary")
                } else if (activeProviderId == "xnxx") {
                    listOf("All", "Trending", "Top Rated", "HD Video", "New Releases", "Verified", "Amateur", "Lesbian", "Blowjob", "MILF", "Asian", "Popular")
                } else if (activeProviderId == "hellporno") {
                    listOf("All", "Top Rated", "Popular", "Latest HD", "Hardcore", "Anal", "Creampie", "Fetish", "BDSM", "Teens 18+")
                } else if (activeProviderId == "stripchat") {
                    listOf("All", "Live Female", "Couples", "Male Models", "Trans Cams", "VR Cams", "Private Shows", "Top Broadcasters", "New Models")
                } else if (activeProviderId == "chaturbate") {
                    listOf("All", "Female Cams", "Male Cams", "Couple Shows", "Trans Cams", "Featured Live", "Teen (18+)", "Spy Cams", "VR Live")
                } else if (activeProviderId == "txxx") {
                    listOf("All", "Top Rated", "Latest HD", "Full HD", "Most Popular", "Hardcore", "Amateur", "Verified", "Trending")
                } else {
                    buildSmartTags(activeContextTitle, searchQuery, recentSearches, adultContentEnabled)
                }
            }

            val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background.run { (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5 }
            val selectedChipBg = if (isDarkTheme) Color(0xFFF1F1F1) else Color(0xFF0F0F0F)
            val selectedChipFg = if (isDarkTheme) Color(0xFF0F0F0F) else Color.White
            val unselectedChipBg = if (isDarkTheme) Color(0xFF272727) else Color(0xFFF2F2F2)
            val unselectedChipFg = if (isDarkTheme) Color(0xFFF1F1F1) else Color(0xFF0F0F0F)

            var topBarHeightPx by remember { mutableStateOf(0f) }

            // Top Header + Tags Column (Stably anchored, zero jank, YouTube-style smooth hide/show)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(9f)
                    .onSizeChanged { topBarHeightPx = it.height.toFloat() }
                    .graphicsLayer {
                        val fraction = 1f - barsAnimatedFraction
                        translationY = if (topBarHeightPx > 0f) -topBarHeightPx * fraction else 0f
                        alpha = barsAnimatedFraction.coerceIn(0f, 1f)
                    }
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                // Header Bar (TopAppBar with Logo & Actions)
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.navigateToScreen(AppScreen.SETTINGS)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                com.example.ui.components.ThemedButterflyLogo(
                                    size = 32.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Butterfly",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    letterSpacing = (-0.2).sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        actions = {
                            if (currentScreen == AppScreen.ACCOUNT) {
                                IconButton(onClick = {
                                    viewModel.navigateToScreen(AppScreen.SETTINGS)
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            IconButton(onClick = {
                                if (isSearchExpanded) {
                                    viewModel.clearSearch()
                                }
                                isSearchExpanded = !isSearchExpanded
                            }) {
                                Icon(
                                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = if (isSearchExpanded) "Close Search" else "Search",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }

                // Tags Bar (Smart contextual category chips & Direct Source Dropdown) - ONLY ON HOME TAB
                if (currentScreen == AppScreen.HOME) {
                    val activeProviderName = if (activeProviderId == "all") "All Sources" else (availableProviders.firstOrNull { it.id == activeProviderId }?.name ?: activeProviderId)

                    LazyRow(
                        contentPadding = PaddingValues(start = 12.dp, top = 2.dp, end = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // MINIMAL SMOOTH SOURCE SELECTOR DROPDOWN
                        item {
                            var isSourceMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                Surface(
                                    onClick = { isSourceMenuExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (activeProviderId != "all") {
                                        if (adultContentEnabled) Color(0xFFE91E63).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    } else unselectedChipBg,
                                    contentColor = if (activeProviderId != "all") Color.White else unselectedChipFg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (activeProviderId != "all") {
                                            if (adultContentEnabled) Color(0xFFE91E63).copy(alpha = 0.65f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                        } else Color.White.copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (activeProviderId != "all") {
                                            SourceBrandLogo(
                                                providerId = activeProviderId,
                                                size = 17.dp,
                                                isAdultMode = adultContentEnabled
                                            )
                                            Text(
                                                text = activeProviderName.substringBefore(" ("),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = "Source Selector",
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Select Source",
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = isSourceMenuExpanded,
                                    onDismissRequest = { isSourceMenuExpanded = false },
                                    containerColor = Color(0xFF0C0C12),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 12.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier
                                        .width(220.dp)
                                        .heightIn(max = 420.dp)
                                ) {
                                    // Minimal Mode Switcher Pill Row
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (adultContentEnabled) "Mode: 18+ Adult" else "Mode: Mainstream",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = Color.White.copy(alpha = 0.9f)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = if (adultContentEnabled) Color(0xFFE91E63).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
                                                ) {
                                                    Text(
                                                        text = "Switch",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (adultContentEnabled) Color(0xFFFF80AB) else Color.White.copy(alpha = 0.85f),
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                        fontSize = 9.5.sp
                                                    )
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (adultContentEnabled) Icons.Default.Explicit else Icons.Default.SwapHoriz,
                                                contentDescription = null,
                                                tint = if (adultContentEnabled) Color(0xFFE91E63) else Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        onClick = {
                                            isSourceMenuExpanded = false
                                            activeCategory = "All"
                                            viewModel.setAdultContentEnabled(!adultContentEnabled)
                                        }
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                                    val menuSources = if (adultContentEnabled) {
                                        listOf(
                                            Pair("all", "All 18+ Sources"),
                                            Pair("supjav", "SupJav"),
                                            Pair("sextb", "SEXTB"),
                                            Pair("123av", "123AV"),
                                            Pair("javtiful", "Javtiful"),
                                            Pair("xnxx", "XNXX"),
                                            Pair("pornhub", "Pornhub"),
                                            Pair("xvideos", "XVideos"),
                                            Pair("spankbang", "SpankBang"),
                                            Pair("eporner", "Eporner"),
                                            Pair("stripchat", "Stripchat Live"),
                                            Pair("chaturbate", "Chaturbate Live")
                                        )
                                    } else {
                                        buildList {
                                            add(Pair("all", "All Sources"))
                                            add(Pair("youtube", "YouTube"))
                                            add(Pair("bilibili", "Bilibili"))
                                            add(Pair("tencent", "Tencent Video"))
                                            add(Pair("dailymotion", "Dailymotion"))
                                            add(Pair("archive_org", "Archive.org"))
                                            add(Pair("torrent", "Cinema & Anime"))
                                            availableProviders
                                                .filter { !viewModel.isAdultProviderId(it.id) && it.id !in setOf("all", "youtube", "bilibili", "tencent", "dailymotion", "archive_org", "torrent") }
                                                .forEach { add(Pair(it.id, it.name)) }
                                        }
                                    }

                                    menuSources.forEach { (id, name) ->
                                        val isSelected = (activeProviderId == id)
                                        val accentColor = if (adultContentEnabled) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = name,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.82f),
                                                    maxLines = 1
                                                )
                                            },
                                            leadingIcon = {
                                                SourceBrandLogo(
                                                    providerId = id,
                                                    size = 20.dp,
                                                    isAdultMode = adultContentEnabled
                                                )
                                            },
                                            trailingIcon = {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = accentColor,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            onClick = {
                                                isSourceMenuExpanded = false
                                                activeCategory = "All"
                                                viewModel.setActiveProvider(id)
                                            }
                                        )
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "More Sources...",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.65f)
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.MoreHoriz,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.65f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        onClick = {
                                            isSourceMenuExpanded = false
                                            showSourceSelectorSheet = true
                                        }
                                    )
                                }
                            }
                        }

                        // CATEGORY TAG CHIPS (Cleanly filters Home feed without opening Search)
                        items(smartTagsList) { tag ->
                            val isSelected = activeCategory.equals(tag, ignoreCase = true)
                            Surface(
                                onClick = {
                                    activeCategory = tag
                                    viewModel.loadHomeCategoryTopic(tag)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) selectedChipBg else unselectedChipBg,
                                contentColor = if (isSelected) selectedChipFg else unselectedChipFg,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // LAYER 3: PERSISTENT FLOATING PIP MINI PLAYER OVERLAY (YouTube Style)
        val activeStreamData by com.example.ui.player.GlobalPlayerManager.activeStreamData.collectAsState()
        val playingStreamData = activeStreamData ?: currentStreamData
        val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        AnimatedVisibility(
            visible = (playingStreamData != null && currentScreen != AppScreen.PLAYER),
            enter = scaleIn(
                initialScale = 0.90f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(
                animationSpec = tween(180, easing = LinearOutSlowInEasing)
            ),
            exit = scaleOut(
                targetScale = 0.94f,
                animationSpec = tween(140, easing = FastOutSlowInEasing)
            ) + fadeOut(
                animationSpec = tween(120, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(92f)
        ) {
            if (playingStreamData != null) {
                val globalProgress by com.example.ui.player.GlobalPlayerManager.progressFraction.collectAsState()
                val globalIsPlaying by com.example.ui.player.GlobalPlayerManager.isPlaying.collectAsState()
                val activeVideoProgress = if (globalProgress > 0f) globalProgress else (activeVideoId?.let { watchProgressMap[it] } ?: 0f)
                LiquidGlassMiniPlayer(
                    streamData = playingStreamData,
                    progressFraction = activeVideoProgress,
                    isPlaying = globalIsPlaying,
                    onTogglePlay = {
                        com.example.ui.player.GlobalPlayerManager.togglePlayPause()
                        viewModel.togglePlayback()
                    },
                    onExpand = {
                        isSearchExpanded = false
                        viewModel.setSearchExpanded(false)
                        viewModel.navigateToScreen(AppScreen.PLAYER)
                    },
                    onClose = {
                        com.example.ui.player.GlobalPlayerManager.stopAndClear()
                        viewModel.closeVideo()
                    },
                    onNext = { viewModel.playNextInQueue() },
                    bottomBarPaddingDp = if (isSearchExpanded || currentTabScreen !in listOf(AppScreen.HOME, AppScreen.EXPLORE, AppScreen.SUBSCRIPTIONS, AppScreen.LIBRARY, AppScreen.ACCOUNT)) 16.dp else if (areBarsVisible) bottomBarPaddingDp else 16.dp,
                    statusBarPaddingDp = statusBarTopPadding
                )
            }
        }

        // LAYER 4: BOTTOM NAVIGATION BAR OVERLAY (Only show on main navigation tabs)
        val isBottomBarVisible = !isSearchExpanded && currentTabScreen in listOf(
            AppScreen.HOME,
            AppScreen.EXPLORE,
            AppScreen.SUBSCRIPTIONS,
            AppScreen.LIBRARY,
            AppScreen.ACCOUNT
        ) && currentScreen != AppScreen.PLAYER

        if (isBottomBarVisible) {
            var bottomBarHeightPx by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarHeightPx = it.height.toFloat() }
                    .graphicsLayer {
                        val fraction = 1f - barsAnimatedFraction
                        translationY = if (bottomBarHeightPx > 0f) bottomBarHeightPx * fraction else 0f
                        alpha = barsAnimatedFraction.coerceIn(0f, 1f)
                    }
            ) {
                LiquidGlassNavBar(
                    currentScreen = currentScreen,
                    userProfile = userProfile,
                    onSelectScreen = { screen ->
                        if (isSearchExpanded) {
                            viewModel.clearSearch()
                        }
                        isSearchExpanded = false
                        if (screen == AppScreen.HOME) {
                            if (currentScreen == AppScreen.HOME) {
                                coroutineScope.launch {
                                    feedListState.animateScrollToItem(0)
                                }
                            } else {
                                viewModel.navigateToScreen(AppScreen.HOME)
                                coroutineScope.launch {
                                    feedListState.scrollToItem(0)
                                }
                            }
                        } else {
                            viewModel.navigateToScreen(screen)
                        }
                    }
                )
            }
        }

        // DOWNLOAD QUALITY PICKER FROM HOME FEED
        if (viewModel.isDownloadSheetVisible && viewModel.downloadSheetVideoItem != null) {
            val sheetVideo = viewModel.downloadSheetVideoItem!!
            val sheetStream = viewModel.downloadSheetStreamData
            com.example.ui.components.DownloadQualityBottomSheet(
                videoTitle = sheetVideo.title,
                channelName = sheetVideo.uploaderName,
                thumbnailUrl = sheetVideo.thumbnailUrl,
                durationText = sheetVideo.formattedDuration,
                availableOptions = sheetStream?.availableStreamOptions ?: emptyList(),
                onConfirmDownload = { qualityLabel, chosenOption ->
                    viewModel.dismissDownloadSheet()
                    viewModel.startDownload(
                        videoId = sheetVideo.id,
                        title = sheetVideo.title,
                        channelName = sheetVideo.uploaderName,
                        thumbnailUrl = sheetVideo.thumbnailUrl,
                        qualityLabel = qualityLabel,
                        streamOption = chosenOption
                    )
                },
                onDismissRequest = { viewModel.dismissDownloadSheet() }
            )
        }

        // FULLSCREEN OVERLAY: VIDEO PLAYER WITH SMOOTH YOUTUBE-STYLE EXPAND/COLLAPSE
        AnimatedVisibility(
            visible = (currentScreen == AppScreen.PLAYER),
            enter = slideInVertically(
                initialOffsetY = { fullHeight ->
                    val origin = playbackCardOriginY
                    if (origin != null && origin > 0f) {
                        origin.roundToInt().coerceIn(0, (fullHeight * 0.85f).roundToInt())
                    } else {
                        (fullHeight * 0.35f).roundToInt()
                    }
                },
                animationSpec = tween(
                    durationMillis = 280,
                    easing = CubicBezierEasing(0.08f, 0.82f, 0.17f, 1.0f)
                )
            ) + scaleIn(
                initialScale = 0.95f,
                transformOrigin = TransformOrigin(0.5f, 0.0f),
                animationSpec = tween(
                    durationMillis = 280,
                    easing = CubicBezierEasing(0.08f, 0.82f, 0.17f, 1.0f)
                )
            ) + fadeIn(
                animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight ->
                    val origin = playbackCardOriginY
                    if (origin != null && origin > 0f) {
                        origin.roundToInt().coerceIn(0, fullHeight)
                    } else {
                        (fullHeight * 0.35f).roundToInt()
                    }
                },
                animationSpec = tween(
                    durationMillis = 240,
                    easing = FastOutSlowInEasing
                )
            ) + scaleOut(
                targetScale = 0.95f,
                transformOrigin = TransformOrigin(0.5f, 0.0f),
                animationSpec = tween(
                    durationMillis = 240,
                    easing = FastOutSlowInEasing
                )
            ) + fadeOut(
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.fillMaxSize().zIndex(100f)
        ) {
            VideoPlayerScreen(
                viewModel = viewModel,
                onBackClick = { viewModel.navigateToScreen(currentTabScreen) },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showSourceSelectorSheet) {
            com.example.ui.components.SourceSelectorSheet(
                viewModel = viewModel,
                onDismiss = { showSourceSelectorSheet = false }
            )
        }
    }
}

@Composable
fun ExploreContent(
    onSelectCategory: (String) -> Unit
) {
    val categories = listOf(
        "Tencent Video" to Icons.Default.LiveTv,
        "YouTube" to Icons.Default.VideoLibrary,
        "Bilibili" to Icons.Default.SmartDisplay,
        "MX Player" to Icons.Default.PlayCircle,
        "PopcornTV" to Icons.Default.LocalMovies,
        "IMDb" to Icons.Default.Movie,
        "Google Drive" to Icons.Default.CloudQueue,
        "Dailymotion" to Icons.Default.OndemandVideo,
        "Music" to Icons.Default.MusicNote,
        "Gaming" to Icons.Default.SportsEsports,
        "Podcasts" to Icons.Default.Podcasts
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Explore Sources & Categories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(categories) { (name, icon) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectCategory(name) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionsContent(
    videos: List<VideoItem>,
    watchProgressMap: Map<String, Float> = emptyMap(),
    showProviderBadge: Boolean = true,
    onSelectVideo: (VideoItem) -> Unit,
    onNotInterested: ((VideoItem) -> Unit)? = null,
    onChannelClick: ((String) -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Text(
                text = "Subscribed Channels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(5) { index ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "C${index + 1}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Channel ${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "Latest Multi-Source Videos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        items(videos, key = { "multi_${it.providerId ?: ""}_${it.id}" }) { video ->
            VideoCard(
                video = video,
                watchProgressFraction = watchProgressMap[video.id] ?: 0f,
                showProviderBadge = showProviderBadge,
                onClick = { onSelectVideo(video) },
                onNotInterested = onNotInterested,
                onChannelClick = onChannelClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun buildSmartTags(
    activeTitle: String?,
    currentQuery: String?,
    recentSearches: List<String> = emptyList(),
    adultContentEnabled: Boolean = false
): List<String> {
    val tags = mutableListOf<String>()
    tags.add("All")

    val adultKeywords = setOf("sextb", "streamtb", "18+", "adult", "porn", "xxx", "jav", "hentai", "123av", "javtiful")
    val eligibleSearches = if (adultContentEnabled) {
        recentSearches.filter { !it.lowercase().contains("sextb") && !it.lowercase().contains("streamtb") }
    } else {
        recentSearches.filter { search ->
            val s = search.lowercase()
            adultKeywords.none { s.contains(it) }
        }
    }

    // 1. Elevate top recent searches & topics directly into smart tag chips
    for (search in eligibleSearches.take(4)) {
        val clean = search.trim()
        if (clean.length in 3..25 && !setOf("all", "video", "movies", "show", "watch").contains(clean.lowercase())) {
            val formatted = clean.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            if (!tags.contains(formatted)) {
                tags.add(formatted)
            }
        }
    }

    val combined = "${activeTitle ?: ""} ${currentQuery ?: ""} ${eligibleSearches.take(3).joinToString(" ")}".lowercase()

    // Music & Song detection for tags
    if (combined.contains("song") || combined.contains("music") || combined.contains("audio") || 
        combined.contains("sing") || combined.contains("lyric") || combined.contains("track") || 
        combined.contains("album") || combined.contains("remix") || combined.contains("t-series") || 
        combined.contains("pop") || combined.contains("rap") || combined.contains("hip hop") || 
        combined.contains("lofi") || combined.contains("taylor") || combined.contains("arijit") || 
        combined.contains("drake") || combined.contains("bts") || combined.contains("gana") || 
        combined.contains("geet") || combined.contains("singer") || combined.contains("soundtrack")) {
        tags.addAll(listOf("Songs", "Music", "Soundtracks", "Hindi Songs", "Bollywood", "Pop", "Hip-Hop", "Lofi", "Acoustic", "Remixes"))
    }

    // Smart contextual rules based on active video / movie / show / query / recent searches
    if (combined.contains("hotstar") || combined.contains("jiohotstar") || combined.contains("disney")) {
        tags.addAll(listOf("Specials", "Serials", "Movies", "Anupamaa", "RadhaKrishn", "Drama", "Cricket", "Comedy"))
    }
    if (combined.contains("spider") || combined.contains("venom")) {
        tags.addAll(listOf("Spider-Man", "Marvel", "Sony", "Tom Holland", "Venom", "Peter Parker", "Superhero"))
    }
    if (combined.contains("inception") || combined.contains("nolan") || combined.contains("oppenheimer") || combined.contains("interstellar")) {
        tags.addAll(listOf("Inception", "Christopher Nolan", "Leonardo DiCaprio", "Cillian Murphy", "Sci-Fi", "Mind-Bending"))
    }
    if (combined.contains("frieren") || combined.contains("sousou")) {
        tags.addAll(listOf("Sousou no Frieren", "Madhouse", "Fantasy", "Magic", "Elf", "Anime"))
    }
    if (combined.contains("lioness") || combined.contains("special ops")) {
        tags.addAll(listOf("Special Ops: Lioness", "Zoe Saldana", "Action", "Thriller", "Military", "Series"))
    }
    if (combined.contains("batman") || combined.contains("dark knight") || combined.contains("joker")) {
        tags.addAll(listOf("Batman", "DC", "Christopher Nolan", "Christian Bale", "Joker", "Action"))
    }
    if (combined.contains("avengers") || combined.contains("iron man") || combined.contains("mcu")) {
        tags.addAll(listOf("Avengers", "Marvel", "MCU", "Robert Downey Jr", "Superhero"))
    }
    if (combined.contains("naruto") || combined.contains("one piece") || combined.contains("bleach") || combined.contains("demon slayer") || combined.contains("jujutsu")) {
        tags.addAll(listOf("Jujutsu Kaisen", "Demon Slayer", "One Piece", "MAPPA", "ufotable", "Anime"))
    }
    if (combined.contains("star wars") || combined.contains("mandalorian") || combined.contains("jedi")) {
        tags.addAll(listOf("Star Wars", "Lucasfilm", "Sci-Fi", "Jedi"))
    }

    // Dynamic extraction of proper noun terms from active video title
    if (!activeTitle.isNullOrEmpty()) {
        val words = activeTitle.replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { word ->
                word.length > 3 && !setOf(
                    "the", "and", "with", "from", "for", "full", "movie", "hd", "1080p", 
                    "720p", "4k", "official", "trailer", "video", "episode", "season", 
                    "sub", "dub", "watch", "online", "free", "part", "torrent", "toreent", "magnet"
                ).contains(word.lowercase())
            }
        words.take(3).forEach { w ->
            val cap = w.replaceFirstChar { it.uppercase() }
            if (!tags.contains(cap)) tags.add(cap)
        }
    }

    // Core categories & popular genres requested
    val coreCategories = listOf(
        "Songs", "Music", "Movies", "Series", "Anime", "Funny", 
        "Action", "Fantasy", "Horror", "Crime", "Sci-Fi", "Drama", "Romance", "Thriller"
    )
    coreCategories.forEach { cat ->
        if (!tags.contains(cat)) tags.add(cat)
    }

    val excludedSourceNames = setOf(
        "youtube", "tencent", "tencent video", "bilibili", "dailymotion", "twitch", "hotstar", "disney+ hotstar", "sonyliv",
        "disney", "disney+", "minitv", "amazon minitv", "mx player", "mxplayer", "popcorntv", "tubitv", "tubi", "imdb", "discovery+", "drive", "google drive",
        "netflix", "crunchyroll", "v.qq.com", "v_qq_com", "qq", "vqqcom", "bunkr", "telegram", "mega", "bun-tel-meg",
        "xnxx", "hellporno", "stripchat", "chaturbate", "txxx", "pornhub", "xvideos", "spankbang", "supjav",
        "123av", "javtiful", "hanime1", "rule34video", "pmvhaven", "piped", "invidious", "hianime", "aniwatch", "bigo", "kick", "rumble"
    )

    return tags.filter { tag ->
        val lower = tag.lowercase()
        val notTorrent = !lower.contains("torrent") && !lower.contains("toreent")
        val notSextb = !lower.contains("sextb") && !lower.contains("streamtb")
        val notSource = excludedSourceNames.none { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") } &&
                !lower.contains(".com") && !lower.contains(".org") && !lower.contains(".net") && !lower.contains("v.qq")
        if (!adultContentEnabled) {
            notTorrent && notSextb && notSource && adultKeywords.none { lower.contains(it) }
        } else {
            notTorrent && notSextb && notSource
        }
    }.distinct()
}

@Composable
fun SearchDrivenRecommendationsShelf(
    searchQuery: String,
    videos: List<VideoItem>,
    modifier: Modifier = Modifier,
    showProviderBadge: Boolean = true,
    onSelectVideo: (VideoItem) -> Unit,
    onOpenSearch: (String) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
        ) {
            // Header: Sparkle + "Because you searched for..." + View All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF5A623).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFF5A623),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Because you searched for",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "\"$searchQuery\"",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                TextButton(
                    onClick = { onOpenSearch(searchQuery) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "See all",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal Carousel of Related Videos
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(videos, key = { "shelf_${it.providerId}_${it.id}" }) { video ->
                    SearchRecommendationShelfCard(
                        video = video,
                        showProviderBadge = showProviderBadge,
                        onClick = { onSelectVideo(video) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchRecommendationShelfCard(
    video: VideoItem,
    showProviderBadge: Boolean,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(video.thumbnailUrl) {
        com.example.util.ThumbnailOptimizer.buildThumbnailRequest(
            context = context,
            url = video.thumbnailUrl,
            crossfadeMillis = 0,
            preferCompact = true
        )
    }

    Card(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF1E1E22))
            ) {
                coil.compose.AsyncImage(
                    model = imageRequest ?: video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Duration badge
                if (video.formattedDuration.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = video.formattedDuration,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Provider badge
                if (showProviderBadge) {
                    val badge = com.example.util.SourceTagHelper.getSourceBadge(video)
                    if (badge.name.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(badge.backgroundColor.copy(alpha = 0.95f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge.name,
                                color = badge.contentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = video.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.uploaderName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
