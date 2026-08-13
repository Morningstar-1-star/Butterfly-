package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()

    val userProfile by viewModel.userProfile.collectAsState()
    val globalActiveStreamData by com.example.ui.player.GlobalPlayerManager.activeStreamData.collectAsState()
    val hasShownGreeting by viewModel.hasShownGreeting.collectAsState()
    var showGreetingText by remember { mutableStateOf(!hasShownGreeting) }

    LaunchedEffect(hasShownGreeting) {
        if (!hasShownGreeting) {
            showGreetingText = true
            kotlinx.coroutines.delay(3800)
            showGreetingText = false
            viewModel.markGreetingShown()
        }
    }

    var showPoTokenDialog by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var activeCategory by remember { mutableStateOf("All") }
    var isBarsVisible by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(currentScreen, isSearchExpanded) {
        isBarsVisible = true
    }

    var scrollAccumulator by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if ((delta > 0 && scrollAccumulator < 0) || (delta < 0 && scrollAccumulator > 0)) {
                    scrollAccumulator = 0f
                }
                scrollAccumulator += delta

                if (scrollAccumulator < -28f) {
                    if (isBarsVisible && !isSearchExpanded) {
                        isBarsVisible = false
                    }
                    scrollAccumulator = 0f
                } else if (scrollAccumulator > 28f) {
                    if (!isBarsVisible) {
                        isBarsVisible = true
                    }
                    scrollAccumulator = 0f
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0f && !isBarsVisible) {
                    isBarsVisible = true
                    scrollAccumulator = 0f
                }
                return Offset.Zero
            }
        }
    }

    var topBarHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var bottomBarHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    val shouldShowTopBar = (isBarsVisible || isSearchExpanded) && (currentScreen != AppScreen.ACCOUNT)
    val shouldShowBottomBar = isBarsVisible || isSearchExpanded

    val animatedTopBarOffsetPx by animateFloatAsState(
        targetValue = if (shouldShowTopBar) 0f else -topBarHeightPx.coerceAtLeast(1f),
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "top_bar_translation"
    )

    val animatedBottomBarOffsetPx by animateFloatAsState(
        targetValue = if (shouldShowBottomBar) 0f else bottomBarHeightPx.coerceAtLeast(1f),
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "bottom_bar_translation"
    )

    val density = LocalDensity.current
    val topBarPaddingDp = remember(topBarHeightPx, density) {
        if (topBarHeightPx > 0f) with(density) { topBarHeightPx.toDp() } else 108.dp
    }
    val bottomBarPaddingDp = remember(bottomBarHeightPx, density) {
        if (bottomBarHeightPx > 0f) with(density) { bottomBarHeightPx.toDp() } else 80.dp
    }

    val categories = listOf("All", "APIJAV", "Eporner", "Dailymotion", "YouTube", "Gaming", "Podcasts", "Music")
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

    if (showPoTokenDialog) {
        PoTokenDialog(
            onDismiss = { showPoTokenDialog = false },
            onApplyToken = {
                activeVideoId?.let { id -> viewModel.playVideo(id) }
            }
        )
    }

    if (currentScreen == AppScreen.PLAYER) {
        VideoPlayerScreen(
            viewModel = viewModel,
            onBackClick = { viewModel.navigateToScreen(AppScreen.HOME) },
            modifier = modifier
        )
        return
    }

    if (currentScreen == AppScreen.SETTINGS) {
        SettingsScreen(
            viewModel = viewModel,
            onBackClick = { viewModel.navigateToScreen(AppScreen.HOME) },
            modifier = modifier
        )
        return
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
                        AppScreen.PROVIDERS -> {
                            ProvidersScreen(viewModel = viewModel)
                        }

                        AppScreen.EXPLORE -> {
                            ExploreScreen(
                                viewModel = viewModel,
                                onMovieSelected = { video ->
                                    viewModel.playVideo(video.id, video.providerId)
                                },
                                onGenreSelected = { term ->
                                    viewModel.updateSearchQuery(term)
                                    viewModel.performSearch(term)
                                    isSearchExpanded = true
                                },
                                topPadding = if (currentScreen != AppScreen.ACCOUNT && !isSearchExpanded) topBarPaddingDp else 0.dp,
                                bottomPadding = bottomBarPaddingDp + 90.dp
                            )
                        }

                        AppScreen.ACCOUNT -> {
                            AccountScreen(
                                viewModel = viewModel,
                                onSelectVideo = { video ->
                                    viewModel.playVideo(video.id, video.providerId)
                                },
                                onToggleSearch = { isSearchExpanded = !isSearchExpanded },
                                showShortsFeed = showShortsFeed,
                                onToggleShortsFeed = { viewModel.setShowShortsFeed(it) }
                            )
                        }

                        AppScreen.SHORTS -> {
                            val rawFeed = if (searchResults.isNotEmpty()) searchResults else trendingVideos
                            val feedList = rawFeed.filter { adultContentEnabled || !viewModel.isAdultVideoItem(it) }
                            ShortsSection(
                                shorts = feedList,
                                onSelectShort = { video ->
                                    viewModel.playVideo(video.id, video.providerId)
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        AppScreen.SUBSCRIPTIONS, AppScreen.LIBRARY -> {
                            LibraryScreen(
                                viewModel = viewModel,
                                onSelectVideo = { video ->
                                    viewModel.playVideo(video.id, video.providerId)
                                },
                                topPadding = if (currentScreen != AppScreen.ACCOUNT && !isSearchExpanded) topBarPaddingDp else 0.dp,
                                bottomPadding = bottomBarPaddingDp + 90.dp
                            )
                        }

                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBackClick = { viewModel.navigateToScreen(AppScreen.HOME) }
                            )
                        }

                        AppScreen.HOME -> {
                            val rawFeed = if (searchResults.isNotEmpty()) searchResults else trendingVideos
                            val feedList = remember(rawFeed, hiddenVideoIds, adultContentEnabled) {
                                rawFeed
                                    .filterNot { hiddenVideoIds.contains(it.id) }
                                    .filter { adultContentEnabled || !viewModel.isAdultVideoItem(it) }
                            }
                            val shortsFeedList = remember(rawFeed, adultContentEnabled) {
                                rawFeed.filter { adultContentEnabled || !viewModel.isAdultVideoItem(it) }
                            }

                            PullToRefreshBox(
                                isRefreshing = isLoadingTrending || isSearching,
                                onRefresh = { viewModel.refreshFeed() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        top = if (currentScreen != AppScreen.ACCOUNT && !isSearchExpanded) topBarPaddingDp else 0.dp,
                                        bottom = bottomBarPaddingDp + 90.dp
                                    )
                                ) {
                                    // SHORTS CAROUSEL SECTION (ONLY IF ENABLED)
                                    if (showShortsFeed) {
                                        item {
                                            if (shortsFeedList.isNotEmpty()) {
                                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayCircle,
                                                            contentDescription = "Shorts",
                                                            tint = Color.Red,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Shorts",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    ShortsSection(
                                                        shorts = shortsFeedList.take(6),
                                                        onSelectShort = { video ->
                                                            viewModel.playVideo(video.id, video.providerId)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

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

                                    // FEED ERROR / CARDS
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
                                    } else {
                                        if (feedList.isEmpty()) {
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
                                            items(feedList, key = { it.id }) { video ->
                                                VideoCard(
                                                    video = video,
                                                    watchProgressFraction = watchProgressMap[video.id] ?: 0f,
                                                    onClick = {
                                                        viewModel.playVideo(video.id, video.providerId)
                                                    },
                                                    onPlayNextInQueue = { v -> viewModel.addToQueue(v) },
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
                                                    onNotInterested = { v ->
                                                        viewModel.markNotInterested(v)
                                                    },
                                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        AppScreen.PLAYER -> {}
                        else -> {}
                    }
                }
            }
        }

        // LAYER 2: TOP BAR OVERLAY
        if (currentScreen != AppScreen.ACCOUNT && !isSearchExpanded) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeightPx = it.height.toFloat() }
                    .graphicsLayer {
                        translationY = animatedTopBarOffsetPx
                    }
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(Color(0xFF8E24AA), Color(0xFFE91E63))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val wingColor = Color.White
                                    drawCircle(
                                        color = wingColor,
                                        radius = w * 0.28f,
                                        center = androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.35f)
                                    )
                                    drawCircle(
                                        color = wingColor,
                                        radius = w * 0.28f,
                                        center = androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.35f)
                                    )
                                    drawCircle(
                                        color = wingColor.copy(alpha = 0.85f),
                                        radius = w * 0.20f,
                                        center = androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.68f)
                                    )
                                    drawCircle(
                                        color = wingColor.copy(alpha = 0.85f),
                                        radius = w * 0.20f,
                                        center = androidx.compose.ui.geometry.Offset(w * 0.64f, h * 0.68f)
                                    )
                                    drawLine(
                                        color = Color(0xFF4A148C),
                                        start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.20f),
                                        end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.82f),
                                        strokeWidth = w * 0.08f
                                    )
                                    drawLine(
                                        color = Color(0xFF4A148C),
                                        start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.22f),
                                        end = androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.10f),
                                        strokeWidth = w * 0.05f
                                    )
                                    drawLine(
                                        color = Color(0xFF4A148C),
                                        start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.22f),
                                        end = androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.10f),
                                        strokeWidth = w * 0.05f
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            AnimatedContent(
                                targetState = showGreetingText,
                                transitionSpec = {
                                    if (targetState) {
                                        (slideInHorizontally(
                                            animationSpec = tween(600, easing = FastOutSlowInEasing),
                                            initialOffsetX = { -it }
                                        ) + fadeIn(animationSpec = tween(600))) togetherWith (
                                            slideOutHorizontally(
                                                animationSpec = tween(400),
                                                targetOffsetX = { it }
                                            ) + fadeOut(animationSpec = tween(400))
                                        )
                                    } else {
                                        (slideInHorizontally(
                                            animationSpec = tween(600, easing = FastOutSlowInEasing),
                                            initialOffsetX = { -it }
                                        ) + fadeIn(animationSpec = tween(600))) togetherWith (
                                            slideOutHorizontally(
                                                animationSpec = tween(400),
                                                targetOffsetX = { it }
                                            ) + fadeOut(animationSpec = tween(400))
                                        )
                                    }
                                },
                                label = "TopBarTitleAnimation"
                            ) { isGreeting ->
                                if (isGreeting) {
                                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                                    val timeGreeting = when (hour) {
                                        in 4..11 -> "Good morning"
                                        in 12..16 -> "Good afternoon"
                                        in 17..21 -> "Good evening"
                                        else -> "Good night"
                                    }
                                    Text(
                                        text = "Hi, $timeGreeting, ${userProfile.name}!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = "Butterfly",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    },
                    actions = {
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

                // Smart Contextual Tags & Category Filter Chips
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

                LazyRow(
                    contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        val isExploreSelected = currentScreen == AppScreen.EXPLORE
                        Surface(
                            onClick = { viewModel.navigateToScreen(AppScreen.EXPLORE) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isExploreSelected) selectedChipBg else unselectedChipBg,
                            contentColor = if (isExploreSelected) selectedChipFg else unselectedChipFg,
                            modifier = Modifier.size(width = 44.dp, height = 36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Explore,
                                    contentDescription = "Explore",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

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

        // LAYER 3: PERSISTENT FLOATING MINI PLAYER OVERLAY
        val activeStreamData by com.example.ui.player.GlobalPlayerManager.activeStreamData.collectAsState()
        val playingStreamData = activeStreamData ?: currentStreamData

        if (playingStreamData != null && currentScreen != AppScreen.PLAYER && !isSearchExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomBarPaddingDp + 12.dp, end = 8.dp, start = 8.dp)
                    .graphicsLayer {
                        translationY = animatedBottomBarOffsetPx
                    }
            ) {
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
                    onSelectScreen = { screen ->
                        isSearchExpanded = false
                        viewModel.navigateToScreen(screen)
                    }
                )
            }
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
    onSelectVideo: (VideoItem) -> Unit,
    onNotInterested: ((VideoItem) -> Unit)? = null
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
                onClick = { onSelectVideo(video) },
                onNotInterested = onNotInterested,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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
