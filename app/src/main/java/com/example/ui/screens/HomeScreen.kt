package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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

    val userProfile by viewModel.userProfile.collectAsState()
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
    var isBottomBarVisible by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    var accumulatedScroll by remember { mutableFloatStateOf(0f) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y

                // Reset direction accumulator when scroll direction flips so threshold is immediate and responsive
                if (delta > 0f && accumulatedScroll < 0f) {
                    accumulatedScroll = 0f
                } else if (delta < 0f && accumulatedScroll > 0f) {
                    accumulatedScroll = 0f
                }

                accumulatedScroll += delta

                // Finger swiping UP (scrolling down feed -> delta < 0): HIDE bars
                if (delta < 0f && accumulatedScroll < -15f && isBottomBarVisible) {
                    isBottomBarVisible = false
                    accumulatedScroll = 0f
                }
                // Finger swiping DOWN (scrolling up feed -> delta > 0): SHOW bars
                else if (delta > 0f && accumulatedScroll > 10f && !isBottomBarVisible) {
                    isBottomBarVisible = true
                    accumulatedScroll = 0f
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // If user pulls down at top edge, ensure top/bottom bars are immediately restored
                if (available.y > 0f && !isBottomBarVisible) {
                    isBottomBarVisible = true
                    accumulatedScroll = 0f
                }
                return Offset.Zero
            }
        }
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
    val activeVideoProgress = if (globalProgress > 0f) globalProgress else (activeVideoId?.let { watchProgressMap[it] } ?: 0f)

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

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(280, easing = FastOutSlowInEasing)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(280, easing = FastOutSlowInEasing)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(200))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (currentStreamData != null && currentScreen != AppScreen.PLAYER) {
                        LiquidGlassMiniPlayer(
                            streamData = currentStreamData,
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
                            onNext = { viewModel.playNextInQueue() },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    LiquidGlassNavBar(
                        currentScreen = currentScreen,
                        onSelectScreen = { screen ->
                            isSearchExpanded = false
                            viewModel.navigateToScreen(screen)
                        }
                    )
                }
            }
        },
        topBar = {
            if (currentScreen != AppScreen.ACCOUNT) {
                AnimatedVisibility(
                    visible = isBottomBarVisible,
                    enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(280, easing = FastOutSlowInEasing)) + slideInVertically(initialOffsetY = { -it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(200)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(280, easing = FastOutSlowInEasing)) + slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(200))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        // Top Action Header
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
                                    // Top Wings
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
                                    // Bottom Wings
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
                                    // Body
                                    drawLine(
                                        color = Color(0xFF4A148C),
                                        start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.20f),
                                        end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.82f),
                                        strokeWidth = w * 0.08f
                                    )
                                    // Antennae
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
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                // Source Provider Filter Chips
                val availableProviders by viewModel.availableProviders.collectAsState()
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val isSelected = (activeProviderId == "all")
                        Surface(
                            onClick = { viewModel.setActiveProvider("all") },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text("All Sources", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(availableProviders.filter { it.id != "all" }) { provider ->
                        val isSelected = (activeProviderId == provider.id)
                        Surface(
                            onClick = { viewModel.setActiveProvider(provider.id) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(provider.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                         scaleIn(initialScale = 0.95f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)))
                            .togetherWith(
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleOut(targetScale = 1.05f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
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
                                }
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
                        val feedList = if (searchResults.isNotEmpty()) searchResults else trendingVideos
                        ShortsSection(
                            shorts = feedList,
                            onSelectShort = { video ->
                                viewModel.playVideo(video.id, video.providerId)
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    AppScreen.SUBSCRIPTIONS -> {
                        SubscriptionsContent(
                            videos = trendingVideos,
                            watchProgressMap = watchProgressMap,
                            onSelectVideo = { video ->
                                viewModel.playVideo(video.id, video.providerId)
                            }
                        )
                    }

                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBackClick = { viewModel.navigateToScreen(AppScreen.HOME) }
                        )
                    }

                    AppScreen.HOME -> {
                        PullToRefreshBox(
                            isRefreshing = isLoadingTrending || isSearching,
                            onRefresh = { viewModel.refreshFeed() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 160.dp)
                            ) {
                                // SHORTS CAROUSEL SECTION (ONLY IF ENABLED)
                                if (showShortsFeed) {
                                    item {
                                        val feedList = if (searchResults.isNotEmpty()) searchResults else trendingVideos
                                        if (feedList.isNotEmpty()) {
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
                                                    shorts = feedList.take(6),
                                                    onSelectShort = { video ->
                                                        viewModel.playVideo(video.id, video.providerId)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // MAIN FEED HEADER
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (searchResults.isNotEmpty()) "Search Results ($activeProviderName)" else "$activeProviderName Content",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }

                                // FEED ERROR / LOADING / CARDS
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
                                } else if (isSearching || isLoadingTrending) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "Aggregating videos from $activeProviderName...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val rawFeed = if (searchResults.isNotEmpty()) searchResults else trendingVideos
                                    val feedList = rawFeed.filterNot { hiddenVideoIds.contains(it.id) }
                                    if (feedList.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "No videos found for $activeProviderName. Try selecting another category or provider.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        items(feedList, key = { (it.providerId ?: "") + "_" + it.id }) { video ->
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
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
    onSelectVideo: (VideoItem) -> Unit
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}
