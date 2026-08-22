package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val feedError by viewModel.feedError.collectAsState()
    val activeVideoId by viewModel.activeVideoId.collectAsState()
    val extractionResult by viewModel.extractionResult.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val showShortsFeed by viewModel.showShortsFeed.collectAsState()

    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val recommendedVideos by viewModel.recommendedVideos.collectAsState()
    val hiddenVideoIds by viewModel.hiddenVideoIds.collectAsState()
    val notInterestedVideoIds by viewModel.notInterestedVideoIds.collectAsState()
    val notInterestedChannels by viewModel.notInterestedChannels.collectAsState()
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val showThumbnailTags by viewModel.showThumbnailTags.collectAsState()

    val userProfile by viewModel.userProfile.collectAsState()
    val globalActiveStreamData by com.example.ui.player.GlobalPlayerManager.activeStreamData.collectAsState()
    val isSearchExpandedState by viewModel.isSearchExpanded.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    var showPoTokenDialog by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isSearchExpandedState) {
        isSearchExpanded = isSearchExpandedState
    }
    var activeCategory by remember { mutableStateOf("All") }
    var isBarsVisible by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    val feedListState = rememberLazyListState()

    LaunchedEffect(currentScreen, isSearchExpanded) {
        isBarsVisible = true
    }

    LaunchedEffect(feedListState.firstVisibleItemIndex) {
        if (feedListState.firstVisibleItemIndex == 0) {
            isBarsVisible = true
        }
    }

    LaunchedEffect(feedListState) {
        androidx.compose.runtime.snapshotFlow {
            val layoutInfo = feedListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }.collect { isNearBottom ->
            if (isNearBottom && !isLoadingTrending && !isSearching && !isLoadingMore) {
                viewModel.loadMoreContent()
            }
        }
    }

    var topAppBarHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var fullHeaderHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var bottomBarHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var scrollOffsetPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    val maxScrollOffsetPx = fullHeaderHeightPx.coerceAtLeast(1f)

    val nestedScrollConnection = remember(maxScrollOffsetPx, isSearchExpanded) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isSearchExpanded) return Offset.Zero
                val delta = available.y
                val previousOffset = scrollOffsetPx
                val newOffset = (previousOffset + delta).coerceIn(-maxScrollOffsetPx, 0f)
                scrollOffsetPx = newOffset

                if (delta < -10f && isBarsVisible && feedListState.firstVisibleItemIndex > 0) {
                    isBarsVisible = false
                } else if (delta > 10f && !isBarsVisible) {
                    isBarsVisible = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(feedListState.firstVisibleItemIndex, feedListState.firstVisibleItemScrollOffset) {
        if (feedListState.firstVisibleItemIndex == 0 && feedListState.firstVisibleItemScrollOffset == 0) {
            scrollOffsetPx = 0f
            isBarsVisible = true
        }
    }

    LaunchedEffect(currentScreen, isSearchExpanded) {
        scrollOffsetPx = 0f
        isBarsVisible = true
    }

    val animatedScrollOffsetPx by animateFloatAsState(
        targetValue = scrollOffsetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scroll_offset_spring"
    )

    val animatedBottomBarOffsetPx by animateFloatAsState(
        targetValue = if (isBarsVisible || isSearchExpanded) 0f else bottomBarHeightPx.coerceAtLeast(1f),
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "bottom_bar_translation"
    )

    val density = LocalDensity.current
    val topBarPaddingDp = remember(fullHeaderHeightPx, density) {
        if (fullHeaderHeightPx > 0f) {
            with(density) { fullHeaderHeightPx.toDp() + 12.dp }
        } else {
            160.dp
        }
    }
    val bottomBarPaddingDp = remember(bottomBarHeightPx, density) {
        if (bottomBarHeightPx > 0f) with(density) { bottomBarHeightPx.toDp() } else 80.dp
    }

    val categories = listOf("All", "YouTube", "Dailymotion", "Gaming", "Podcasts", "Music", "Trending", "News")
    val activeProviderName = availableProviders.firstOrNull { it.id == activeProviderId }?.name ?: activeProviderId

    // StreamData extracted for player / mini player
    val currentStreamData = remember(extractionResult, activeVideoId, searchResults, trendingVideos) {
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
    val globalProgress by com.example.ui.player.GlobalPlayerManager.progressFraction.collectAsState()
    val globalIsPlaying by com.example.ui.player.GlobalPlayerManager.isPlaying.collectAsState()
    val isPipMode by viewModel.isPipMode.collectAsState()
    val activeVideoProgress = if (globalProgress > 0f) globalProgress else (activeVideoId?.let { watchProgressMap[it] } ?: 0f)

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

    BackHandler(enabled = (currentScreen != AppScreen.HOME || isSearchExpanded)) {
        if (isSearchExpanded) {
            viewModel.clearSearch()
            isSearchExpanded = false
        } else {
            viewModel.navigateToScreen(AppScreen.HOME)
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
                        viewModel.playVideo(video.id, video.providerId)
                    },
                    onCloseSearch = {
                        viewModel.clearSearch()
                        isSearchExpanded = false
                    }
                )
            } else {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                         scaleIn(initialScale = 0.98f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 1.02f, animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
                            )
                    },
                    label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        AppScreen.EXPLORE -> {
                            ExploreScreen(
                                viewModel = viewModel,
                                onSelectVideo = { video ->
                                    viewModel.playVideo(video.id, video.providerId)
                                },
                                topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                                bottomPadding = bottomBarPaddingDp + (if (currentStreamData != null) 72.dp else 16.dp)
                            )
                        }

                        AppScreen.LIBRARY -> {
                            LibraryScreen(
                                viewModel = viewModel,
                                onSelectVideo = { video ->
                                    viewModel.playVideo(video.id, video.providerId)
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
                                    viewModel.playVideo(video.id, video.providerId)
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
                            val rawFeed = if (searchResults.isNotEmpty()) searchResults else trendingVideos
                            val feedList = remember(rawFeed, hiddenVideoIds, notInterestedVideoIds, notInterestedChannels, adultContentEnabled) {
                                rawFeed
                                    .filterNot { viewModel.isBlockedVideo(it) }
                                    .filter { adultContentEnabled || !viewModel.isAdultVideoItem(it) }
                                    .filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }
                                    .distinctBy { "${it.providerId}_${it.id}" }
                            }
                            val shortsFeedList = remember(rawFeed, hiddenVideoIds, notInterestedVideoIds, notInterestedChannels, adultContentEnabled) {
                                rawFeed
                                    .filterNot { viewModel.isBlockedVideo(it) }
                                    .filter { adultContentEnabled || !viewModel.isAdultVideoItem(it) }
                                    .filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }
                                    .distinctBy { "${it.providerId}_${it.id}" }
                            }

                            LaunchedEffect(feedList) {
                                if (feedList.isNotEmpty()) {
                                    com.example.util.ThumbnailOptimizer.preloadThumbnails(context, feedList, maxCount = 12)
                                }
                            }

                            val pullRefreshState = rememberPullToRefreshState()
                            val isRefreshingFeed = isLoadingTrending || isSearching

                            PullToRefreshBox(
                                isRefreshing = isRefreshingFeed,
                                onRefresh = { viewModel.refreshFeed() },
                                state = pullRefreshState,
                                indicator = {
                                    PullToRefreshDefaults.Indicator(
                                        state = pullRefreshState,
                                        isRefreshing = isRefreshingFeed,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = if (!isSearchExpanded) topBarPaddingDp + 8.dp else 16.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
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
                                    // MAIN FEED HEADER (ONLY WHEN SEARCH RESULTS EXIST)
                                    if (searchResults.isNotEmpty()) {
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Search Results",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                        }
                                    }

                                    // FEED ERROR / SKELETON / CARDS
                                    if (feedError != null) {
                                        item {
                                            FeedErrorDiagnosticCard(
                                                errorDetails = feedError!!,
                                                onRetry = {
                                                    if (activeCategory == "All" && searchQuery.isBlank()) {
                                                        viewModel.loadTrending()
                                                    } else {
                                                        viewModel.performSearch()
                                                    }
                                                }
                                            )
                                        }
                                    } else if ((isLoadingTrending || isSearching) && feedList.isEmpty()) {
                                        item {
                                            FeedSkeletonLoading(
                                                itemCount = 5,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    } else if (feedList.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "No videos found. Try selecting another category or tag.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        items(feedList, key = { "${it.providerId}_${it.id}" }) { video ->
                                            VideoCard(
                                                video = video,
                                                watchProgressFraction = watchProgressMap[video.id] ?: 0f,
                                                showProviderBadge = showThumbnailTags,
                                                onClick = {
                                                    viewModel.playVideo(video.id, video.providerId)
                                                },
                                                onPlayNextInQueue = { v -> viewModel.playNextInQueue(v) },
                                                onAddToQueue = { v -> viewModel.addToQueue(v) },
                                                onSaveToWatchLater = { v -> viewModel.addToWatchLater(v) },
                                                onSaveToPlaylist = { v ->
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
                                                },
                                                onDownload = { v ->
                                                    viewModel.showDownloadSheet(v)
                                                },
                                                onNotInterested = { v ->
                                                    viewModel.markNotInterested(v)
                                                },
                                                onChannelClick = { ch ->
                                                    viewModel.openChannel(ch)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
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

            val smartTagsList = remember(activeContextTitle, searchQuery) {
                buildSmartTags(activeContextTitle, searchQuery)
            }

            val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background.run { (red * 0.299 + green * 0.587 + blue * 0.114) < 0.5 }
            val selectedChipBg = if (isDarkTheme) Color(0xFFF1F1F1) else Color(0xFF0F0F0F)
            val selectedChipFg = if (isDarkTheme) Color(0xFF0F0F0F) else Color.White
            val unselectedChipBg = if (isDarkTheme) Color(0xFF272727) else Color(0xFFF2F2F2)
            val unselectedChipFg = if (isDarkTheme) Color(0xFFF1F1F1) else Color(0xFF0F0F0F)

            val headerTranslationY = animatedScrollOffsetPx.coerceIn(-fullHeaderHeightPx, 0f)

            // Combined Collapsible Header + Tags Column
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(9f)
                    .graphicsLayer {
                        translationY = headerTranslationY
                    }
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .onSizeChanged { fullHeaderHeightPx = it.height.toFloat() }
            ) {
                // Header Bar (TopAppBar with Logo & Actions)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { topAppBarHeightPx = it.height.toFloat() }
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
                    var isSourceMenuExpanded by remember { mutableStateOf(false) }
                    val activeProviderName = if (activeProviderId == "all") "All Sources" else (availableProviders.firstOrNull { it.id == activeProviderId }?.name ?: activeProviderId)

                    LazyRow(
                        contentPadding = PaddingValues(start = 12.dp, top = 2.dp, end = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // DIRECT SOURCE SELECTOR DROPDOWN BUTTON (In front of all tags)
                        item {
                            Box {
                                Surface(
                                    onClick = { isSourceMenuExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (activeProviderId != "all") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (activeProviderId != "all") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Source Selector",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = activeProviderName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Source",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = isSourceMenuExpanded,
                                    onDismissRequest = { isSourceMenuExpanded = false }
                                ) {
                                    availableProviders.forEach { provider ->
                                        val isSelected = (activeProviderId == provider.id)
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = provider.name,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            },
                                            trailingIcon = {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.setActiveProvider(provider.id)
                                                isSourceMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // CATEGORY TAG CHIPS
                        items(smartTagsList) { tag ->
                            val isSelected = if (tag == "All") searchQuery.isBlank() else searchQuery.equals(tag, ignoreCase = true)
                            Surface(
                                onClick = {
                                    if (tag == "All") {
                                        viewModel.clearSearch()
                                    } else {
                                        viewModel.updateSearchQuery(tag)
                                        viewModel.performSearch(tag)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) selectedChipBg else unselectedChipBg,
                                contentColor = if (isSelected) selectedChipFg else unselectedChipFg,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // LAYER 3: PERSISTENT FLOATING MINI PLAYER OVERLAY
        val activeStreamData by com.example.ui.player.GlobalPlayerManager.activeStreamData.collectAsState()
        val playingStreamData = activeStreamData ?: currentStreamData

        AnimatedVisibility(
            visible = (playingStreamData != null && currentScreen != AppScreen.PLAYER && !isSearchExpanded),
            enter = fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        initialOffsetY = { (it * 0.4f).toInt() },
                        animationSpec = tween(160, easing = FastOutSlowInEasing)
                    ),
            exit = fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing)) +
                   slideOutVertically(
                       targetOffsetY = { (it * 0.4f).toInt() },
                       animationSpec = tween(120, easing = FastOutSlowInEasing)
                   ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarPaddingDp + 12.dp, end = 8.dp, start = 8.dp)
                .graphicsLayer {
                    translationY = animatedBottomBarOffsetPx
                }
        ) {
            if (playingStreamData != null) {
                LiquidGlassMiniPlayer(
                    streamData = playingStreamData,
                    progressFraction = activeVideoProgress,
                    isPlaying = globalIsPlaying,
                    onTogglePlay = {
                        com.example.ui.player.GlobalPlayerManager.togglePlayPause()
                        viewModel.togglePlayback()
                    },
                    onExpand = { viewModel.navigateToScreen(AppScreen.PLAYER) },
                    onClose = {
                        com.example.ui.player.GlobalPlayerManager.stopAndClear()
                        viewModel.closeVideo()
                    },
                    onNext = { viewModel.playNextInQueue() }
                )
            }
        }

        // LAYER 4: BOTTOM NAVIGATION BAR OVERLAY
        if (!isSearchExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarHeightPx = it.height.toFloat() }
                    .graphicsLayer {
                        translationY = animatedBottomBarOffsetPx
                    }
            ) {
                LiquidGlassNavBar(
                    currentScreen = currentScreen,
                    userProfile = userProfile,
                    onSelectScreen = { screen ->
                        isSearchExpanded = false
                        viewModel.navigateToScreen(screen)
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

        // FULLSCREEN OVERLAY: SETTINGS SCREEN WITH CLEAN SMOOTH TRANSITION
        AnimatedVisibility(
            visible = (currentScreen == AppScreen.SETTINGS),
            enter = slideInVertically(
                initialOffsetY = { (it * 0.1f).toInt() },
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { (it * 0.1f).toInt() },
                animationSpec = tween(150, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier.fillMaxSize().zIndex(90f)
        ) {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { viewModel.navigateToScreen(AppScreen.HOME) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // FULLSCREEN OVERLAY: VIDEO PLAYER WITH SMOOTH, MINIMAL TRANSITION
        AnimatedVisibility(
            visible = (currentScreen == AppScreen.PLAYER),
            enter = fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                    slideInVertically(
                        initialOffsetY = { (it * 0.06f).toInt() },
                        animationSpec = tween(160, easing = FastOutSlowInEasing)
                    ),
            exit = fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)) +
                   slideOutVertically(
                       targetOffsetY = { (it * 0.06f).toInt() },
                       animationSpec = tween(140, easing = FastOutSlowInEasing)
                   ),
            modifier = Modifier.fillMaxSize().zIndex(100f)
        ) {
            VideoPlayerScreen(
                viewModel = viewModel,
                onBackClick = { viewModel.navigateToScreen(AppScreen.HOME) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ExploreContent(
    onSelectCategory: (String) -> Unit
) {
    val categories = listOf(
        "APIJAV" to Icons.Default.Whatshot,
        "Eporner" to Icons.Default.PlayArrow,
        "Dailymotion" to Icons.Default.OndemandVideo,
        "YouTube" to Icons.Default.VideoLibrary,
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
        items(videos) { video ->
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

private fun buildSmartTags(activeTitle: String?, currentQuery: String?): List<String> {
    val tags = mutableListOf<String>()
    tags.add("All")

    val combined = "${activeTitle ?: ""} ${currentQuery ?: ""}".lowercase()

    // Smart contextual rules based on active video / movie / show / query
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
                    "sub", "dub", "watch", "online", "free", "part"
                ).contains(word.lowercase())
            }
        words.take(3).forEach { w ->
            val cap = w.replaceFirstChar { it.uppercase() }
            if (!tags.contains(cap)) tags.add(cap)
        }
    }

    // Core categories & popular genres requested
    val coreCategories = listOf(
        "Movies", "Series", "Funny", "Action", "Fantasy", "Horror", 
        "Crime", "Sci-Fi", "Drama", "Anime", "Romance", "Thriller"
    )
    coreCategories.forEach { cat ->
        if (!tags.contains(cat)) tags.add(cat)
    }

    return tags.distinct()
}
