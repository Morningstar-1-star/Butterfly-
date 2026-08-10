package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
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

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -15f) {
                    if (isBarsVisible) isBarsVisible = false
                } else if (available.y > 15f) {
                    if (!isBarsVisible) isBarsVisible = true
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AnimatedVisibility(
                    visible = isBarsVisible || isSearchExpanded,
                    enter = slideInVertically(initialOffsetY = { it }) + expandVertically(expandFrom = Alignment.Bottom),
                    exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    LiquidGlassNavBar(
                        currentScreen = currentScreen,
                        onSelectScreen = { screen ->
                            isSearchExpanded = false
                            viewModel.navigateToScreen(screen)
                        }
                    )
                }
            },
        topBar = {
            AnimatedVisibility(
                visible = isBarsVisible || isSearchExpanded,
                enter = slideInVertically(initialOffsetY = { -it }) + expandVertically(expandFrom = Alignment.Top),
                exit = slideOutVertically(targetOffsetY = { -it }) + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                if (currentScreen != AppScreen.ACCOUNT) {
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

                        // Smart Contextual Tags & Category Filter Chips
                        val activeContextTitle = globalActiveStreamData?.title 
                            ?: currentStreamData?.title 
                            ?: trendingVideos.firstOrNull()?.title 
                            ?: searchQuery

                        val smartTagsList = remember(activeContextTitle, searchQuery) {
                            buildSmartTags(activeContextTitle, searchQuery)
                        }

                        LazyRow(
                            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                        Text(tag, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                                    text = "No videos found. Try selecting another category or tag.",
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

    // PERSISTENT FLOATING MINI PLAYER OVERLAY (Fixed on screen, NEVER disappears when scrolling top/bottom)
    val activeStreamData by com.example.ui.player.GlobalPlayerManager.activeStreamData.collectAsState()
    val playingStreamData = activeStreamData ?: currentStreamData

    if (playingStreamData != null && currentScreen != AppScreen.PLAYER) {
        val navBarHeightDp = if (isBarsVisible || isSearchExpanded) 80.dp else 0.dp
        val animatedBottomPadding by animateDpAsState(
            targetValue = navBarHeightDp + 12.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = animatedBottomPadding, end = 8.dp, start = 8.dp),
            contentAlignment = Alignment.BottomCenter
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
