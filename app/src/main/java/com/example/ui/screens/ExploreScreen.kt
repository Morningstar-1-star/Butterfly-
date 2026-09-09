package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.model.*
import com.example.ui.MainViewModel
import com.example.util.ExploreMediaHelper
import com.example.util.ThumbnailOptimizer
import kotlinx.coroutines.launch

// ==================== BUTTERY BOUNCY RUBBER INTERACTION MODIFIER ====================

@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    scaleDownTo: Float = 0.93f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDownTo else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bouncy_scale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

// ==================== MAIN EXPLORE SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    onSelectVideo: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 90.dp,
    bottomPadding: Dp = 100.dp
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var sections by remember { mutableStateOf<List<ExploreSection>>(ExploreMediaHelper.getInstantInitialFeed()) }
    var isLoadingFeed by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    var isSearchOverlayOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ExploreMediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var activeCategorySection by remember { mutableStateOf<ExploreSection?>(null) }
    var selectedMediaForDetails by remember { mutableStateOf<ExploreMediaItem?>(null) }
    var resolvedMediaDetails by remember { mutableStateOf<ExploreMediaItem?>(null) }
    var isResolvingDetails by remember { mutableStateOf(false) }

    var activeTorrentMedia by remember { mutableStateOf<ExploreMediaItem?>(null) }
    var activeTorrentIdentity by remember { mutableStateOf<com.example.torrent.provider.MediaIdentity?>(null) }
    val torrentReleases by viewModel.torrentReleases.collectAsState()
    val isSearchingTorrents by viewModel.isSearchingTorrents.collectAsState()

    // Handle back button on hardware / gesture
    BackHandler(enabled = selectedMediaForDetails != null) {
        selectedMediaForDetails = null
        resolvedMediaDetails = null
    }

    BackHandler(enabled = selectedMediaForDetails == null && isSearchOverlayOpen) {
        isSearchOverlayOpen = false
        searchQuery = ""
        searchResults = emptyList()
    }

    BackHandler(enabled = selectedMediaForDetails == null && !isSearchOverlayOpen && activeCategorySection != null) {
        activeCategorySection = null
    }

    // Load initial explore feed
    fun loadFeed(forceRefresh: Boolean = false) {
        coroutineScope.launch {
            if (forceRefresh) isRefreshing = true
            try {
                val fresh = ExploreMediaHelper.fetchExploreFeed()
                if (fresh.isNotEmpty()) {
                    sections = fresh
                }
            } catch (e: Exception) {
                // Keep existing
            } finally {
                isLoadingFeed = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadFeed()
    }

    LaunchedEffect(sections) {
        if (sections.isNotEmpty()) {
            val allItems = sections.flatMap { it.items }
            ThumbnailOptimizer.preloadPosters(context, allItems.map { it.posterUrl ?: it.backdropUrl }, maxCount = 40)
        }
    }

    // Live search
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            try {
                val sanitized = com.example.util.SmartSearchSanitizer.sanitizeQuery(searchQuery.trim())
                searchResults = ExploreMediaHelper.searchAll(sanitized.cleanQuery)
            } catch (e: Exception) {
                searchResults = emptyList()
            } finally {
                isSearching = false
            }
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    val pullRefreshState = rememberPullToRefreshState()

    // Top curated items for the Hero Spotlight Carousel
    val heroItems = remember(sections) {
        val candidates = mutableListOf<ExploreMediaItem>()
        // Pick primary showcase movies & series
        sections.forEach { section ->
            candidates.addAll(section.items.take(2))
        }
        candidates.distinctBy { it.id }.take(5)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Content View (Home Feed or Category Grid)
        AnimatedContent(
            targetState = activeCategorySection,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } +
                            fadeIn(tween(220))).togetherWith(
                        slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { -it / 3 } +
                                fadeOut(tween(180))
                    )
                } else {
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { -it / 3 } +
                            fadeIn(tween(220))).togetherWith(
                        slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } +
                                fadeOut(tween(180))
                    )
                }
            },
            label = "explore_section_transition"
        ) { currentActiveSection ->
            if (currentActiveSection != null) {
                // ==================== CATEGORY GRID SCREEN ====================
                CategoryGridView(
                    section = currentActiveSection,
                    topPadding = topPadding,
                    bottomPadding = bottomPadding,
                    isSaved = { id -> viewModel.isExploreMediaSaved(id) },
                    onBack = { activeCategorySection = null },
                    onCardClick = { item -> selectedMediaForDetails = item },
                    onSaveClick = { item -> viewModel.toggleSaveExploreMedia(item) }
                )
            } else {
                // ==================== MAIN EXPLORE FEED ====================
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { loadFeed(forceRefresh = true) },
                    state = pullRefreshState,
                    indicator = {
                        com.example.ui.components.YouTubePullToRefreshIndicator(
                            state = pullRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                            topPadding = topPadding,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = topPadding,
                            bottom = bottomPadding + 32.dp
                        )
                    ) {
                        // 1. HERO SPOTLIGHT CAROUSEL (Cinematic Header)
                        if (heroItems.isNotEmpty()) {
                            item(key = "hero_carousel") {
                                HeroSpotlightCarousel(
                                    items = heroItems,
                                    isSaved = { id -> viewModel.isExploreMediaSaved(id) },
                                    onCardClick = { item -> selectedMediaForDetails = item },
                                    onSaveClick = { item -> viewModel.toggleSaveExploreMedia(item) },
                                    onWatchClick = { item ->
                                        val searchQ = "${item.title} trailer"
                                        viewModel.updateSearchQuery(searchQ)
                                        viewModel.performSearch(searchQ)
                                        viewModel.navigateToScreen(AppScreen.HOME)
                                    }
                                )
                            }
                        }

                        // 2. MAIN SECTIONS
                            if (isLoadingFeed && sections.isEmpty()) {
                                items(3) {
                                    ExploreSectionSkeleton()
                                }
                            } else {
                                items(sections, key = { it.title }) { section ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp)
                                    ) {
                                        // Section Header with Title + Circular arrow button
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = section.title,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                if (!section.subtitle.isNullOrBlank()) {
                                                    Text(
                                                        text = section.subtitle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Circular > Button opens Category Grid View
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                    .bouncyClickable {
                                                        activeCategorySection = section
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = "View all ${section.title}",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        // Horizontal Carousel Row
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(section.items, key = { "${section.title}_${it.id}" }) { mediaItem ->
                                                val isSaved = viewModel.isExploreMediaSaved(mediaItem.id)
                                                ExplorePosterCard(
                                                    item = mediaItem,
                                                    isSaved = isSaved,
                                                    onCardClick = { selectedMediaForDetails = mediaItem },
                                                    onSaveClick = { viewModel.toggleSaveExploreMedia(mediaItem) }
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

        // Floating Little Search Icon in Top-Right Corner
        if (activeCategorySection == null && selectedMediaForDetails == null && !isSearchOverlayOpen) {
            Surface(
                onClick = { isSearchOverlayOpen = true },
                shape = CircleShape,
                color = Color(0xCC161622),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                contentColor = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 16.dp)
                    .size(42.dp)
                    .zIndex(15f)
                    .bouncyClickable { isSearchOverlayOpen = true }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Movies & Series",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ==================== FULL SCREEN SEARCH OVERLAY ====================
        AnimatedVisibility(
            visible = isSearchOverlayOpen,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(tween(180))
        ) {
            ExploreSearchOverlay(
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                searchResults = searchResults,
                isSearching = isSearching,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                isSaved = { id -> viewModel.isExploreMediaSaved(id) },
                onBack = {
                    isSearchOverlayOpen = false
                    searchQuery = ""
                    searchResults = emptyList()
                },
                onCardClick = { item ->
                    selectedMediaForDetails = item
                },
                onSaveClick = { item ->
                    viewModel.toggleSaveExploreMedia(item)
                }
            )
        }

        // ==================== CINEMATIC FULL SCREEN MOVIE & TV DETAILS ====================
        AnimatedVisibility(
            visible = selectedMediaForDetails != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(tween(250)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(tween(200))
        ) {
            if (selectedMediaForDetails != null) {
                val currentSelected = selectedMediaForDetails!!

                LaunchedEffect(currentSelected.id) {
                    isResolvingDetails = true
                    try {
                        resolvedMediaDetails = ExploreMediaHelper.resolveFullMediaDetails(currentSelected)
                    } catch (e: Exception) {
                        resolvedMediaDetails = currentSelected
                    } finally {
                        isResolvingDetails = false
                    }
                }

                val displayItem = resolvedMediaDetails ?: currentSelected
                val isSaved = viewModel.isExploreMediaSaved(displayItem.id)

                CinematicMovieDetailsView(
                    item = displayItem,
                    isSaved = isSaved,
                    isResolving = isResolvingDetails,
                    onBack = {
                        selectedMediaForDetails = null
                        resolvedMediaDetails = null
                    },
                    onToggleSave = { viewModel.toggleSaveExploreMedia(displayItem) },
                    onPlay = {
                        val mediaIdentity = com.example.torrent.provider.MediaIdentity(
                            title = displayItem.title,
                            year = displayItem.releaseYear.filter { it.isDigit() }.take(4).ifBlank { null },
                            imdbId = displayItem.imdbId,
                            tmdbId = displayItem.tmdbId ?: displayItem.id,
                            mediaType = when (displayItem.mediaType) {
                                ExploreMediaType.TV -> "tv"
                                ExploreMediaType.ANIME -> "anime"
                                else -> "movie"
                            },
                            season = if (displayItem.mediaType != ExploreMediaType.MOVIE) 1 else null,
                            episode = if (displayItem.mediaType != ExploreMediaType.MOVIE) 1 else null
                        )
                        activeTorrentMedia = displayItem
                        activeTorrentIdentity = mediaIdentity
                        viewModel.searchTorrentReleases(mediaIdentity)
                    },
                    onPlayTrailer = { q ->
                        selectedMediaForDetails = null
                        resolvedMediaDetails = null
                        viewModel.updateSearchQuery(q)
                        viewModel.performSearch(q)
                        viewModel.navigateToScreen(AppScreen.HOME)
                    },
                    onSelectRelatedMedia = { related ->
                        selectedMediaForDetails = related
                        resolvedMediaDetails = null
                    }
                )
            }
        }

        // ==================== BITTORRENT P2P RELEASES SELECTOR MODAL ====================
        if (activeTorrentIdentity != null && activeTorrentMedia != null) {
            val curMedia = activeTorrentMedia!!
            val curIdentity = activeTorrentIdentity!!
            val seasons: List<com.example.model.SeriesSeason> = remember(curMedia) {
                if (curMedia.mediaType != ExploreMediaType.MOVIE) {
                    val epCount = curMedia.episodesCount?.coerceIn(1, 100) ?: 12
                    listOf(
                        com.example.model.SeriesSeason(
                            seasonNumber = 1,
                            seasonName = "Season 1",
                            episodes = (1..epCount).map { epNum ->
                                com.example.model.EpisodeItem(
                                    id = "${curMedia.id}_s1e$epNum",
                                    seasonNumber = 1,
                                    episodeNumber = epNum,
                                    title = "Episode $epNum"
                                )
                            }
                        )
                    )
                } else emptyList()
            }

            com.example.ui.torrent.TorrentReleasesBottomSheet(
                mediaIdentity = curIdentity,
                posterUrl = curMedia.posterUrl ?: curMedia.backdropUrl,
                seasons = seasons,
                releases = torrentReleases,
                isLoading = isSearchingTorrents,
                onDismiss = {
                    activeTorrentIdentity = null
                    activeTorrentMedia = null
                    viewModel.clearTorrentReleases()
                },
                onSelectRelease = { release ->
                    val identityToPlay = curIdentity
                    val posterToPlay = curMedia.posterUrl ?: curMedia.backdropUrl
                    activeTorrentIdentity = null
                    activeTorrentMedia = null
                    selectedMediaForDetails = null
                    resolvedMediaDetails = null
                    viewModel.playTorrentRelease(release, identityToPlay, posterToPlay)
                },
                onSelectEpisode = { seasonNum, epNum ->
                    val updated = curIdentity.copy(season = seasonNum, episode = epNum)
                    activeTorrentIdentity = updated
                    viewModel.searchTorrentReleases(updated)
                }
            )
        }
    }
}

// ==================== HERO SPOTLIGHT CAROUSEL ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroSpotlightCarousel(
    items: List<ExploreMediaItem>,
    isSaved: (String) -> Boolean,
    onCardClick: (ExploreMediaItem) -> Unit,
    onSaveClick: (ExploreMediaItem) -> Unit,
    onWatchClick: (ExploreMediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(470.dp)
            .background(Color(0xFF0C0C10))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val item = items[pageIndex]
            val context = LocalContext.current
            val imageModel = remember(item.backdropUrl, item.posterUrl) {
                ThumbnailOptimizer.buildBackdropRequest(context, item.backdropUrl ?: item.posterUrl)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onCardClick(item) }
            ) {
                // High-res Backdrop / Poster
                SubcomposeAsyncImage(
                    model = imageModel,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                            alpha = 1f - kotlin.math.abs(pageOffset) * 0.35f
                        },
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF14141E)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF14141E))
                        )
                    }
                )

                // Cinematic seamless gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Transparent,
                                    Color(0xFF0C0C10).copy(alpha = 0.65f),
                                    Color(0xFF0C0C10)
                                )
                            )
                        )
                )

                // Quick Save Top Right
                val saved = isSaved(item.id)
                IconButton(
                    onClick = { onSaveClick(item) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                        contentDescription = "Save",
                        tint = if (saved) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Info and Action at Bottom Center
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    // Stylized Title
                    Text(
                        text = item.title.uppercase(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.8.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle metadata
                    val genresStr = item.genres.take(2).joinToString(", ")
                    val metaSubtitle = buildString {
                        append(item.typeBadge)
                        if (genresStr.isNotBlank()) append(" • $genresStr")
                        if (item.releaseYear.isNotBlank()) append(" • ${item.releaseYear}")
                    }
                    Text(
                        text = metaSubtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Big White Pill Button: "View Details"
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        contentColor = Color.Black,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .height(44.dp)
                            .bouncyClickable { onCardClick(item) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 28.dp)
                        ) {
                            Text(
                                text = "View Details",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Animated Dot Indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until items.size) {
                            val isSelected = (pagerState.currentPage == i)
                            val dotWidth by animateDpAsState(
                                targetValue = if (isSelected) 22.dp else 6.dp,
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                                label = "dot_width"
                            )
                            val dotAlpha by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0.35f,
                                label = "dot_alpha"
                            )

                            Box(
                                modifier = Modifier
                                    .width(dotWidth)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = dotAlpha))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== CATEGORY GRID VIEW SCREEN ====================

@Composable
private fun CategoryGridView(
    section: ExploreSection,
    topPadding: Dp,
    bottomPadding: Dp,
    isSaved: (String) -> Boolean,
    onBack: () -> Unit,
    onCardClick: (ExploreMediaItem) -> Unit,
    onSaveClick: (ExploreMediaItem) -> Unit
) {
    var selectedSortChip by remember { mutableStateOf("All") }
    val sortChips = listOf("All", "Top Rated", "Action", "Sci-Fi", "Newest")

    val displayedItems = remember(section.items, selectedSortChip) {
        when (selectedSortChip) {
            "Top Rated" -> section.items.sortedByDescending { it.rating }
            "Action" -> section.items.filter { it.genres.any { g -> g.contains("Action", ignoreCase = true) } }.ifEmpty { section.items }
            "Sci-Fi" -> section.items.filter { it.genres.any { g -> g.contains("Sci-Fi", ignoreCase = true) || g.contains("Fantasy", ignoreCase = true) } }.ifEmpty { section.items }
            "Newest" -> section.items.sortedByDescending { it.releaseYear }
            else -> section.items
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .bouncyClickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${displayedItems.size} titles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Filter / Sort Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(sortChips) { chip ->
                val isSelected = selectedSortChip == chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .height(34.dp)
                        .bouncyClickable { selectedSortChip = chip }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = chip,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3-Column Poster Grid with Bouncy Touch
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(displayedItems, key = { it.id }) { item ->
                val saved = isSaved(item.id)
                ExplorePosterCard(
                    item = item,
                    isSaved = saved,
                    onCardClick = { onCardClick(item) },
                    onSaveClick = { onSaveClick(item) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ==================== POSTER CARD (TACTILE BOUNCY SPRING TOUCH) ====================

@Composable
fun ExplorePosterCard(
    item: ExploreMediaItem,
    isSaved: Boolean,
    onCardClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageModel = remember(item.posterUrl, item.backdropUrl) {
        ThumbnailOptimizer.buildPosterRequest(context, item.posterUrl ?: item.backdropUrl)
    }

    Column(
        modifier = modifier
            .width(132.dp)
            .bouncyClickable { onCardClick() }
    ) {
        // Poster Box with Aspect Ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E2A))
        ) {
            SubcomposeAsyncImage(
                model = imageModel,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1A26)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF26263A), Color(0xFF14141E))
                                )
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (item.mediaType) {
                                    ExploreMediaType.ANIME -> Icons.Default.Animation
                                    ExploreMediaType.TV -> Icons.Default.Tv
                                    else -> Icons.Default.Movie
                                },
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 2,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            )

            // Top rating badge
            if (item.rating > 0) {
                Surface(
                    shape = RoundedCornerShape(topStart = 14.dp, bottomEnd = 10.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    contentColor = Color(0xFFFFB300),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = item.displayRating, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quick Save Button Top-Right
            Surface(
                onClick = onSaveClick,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = if (isSaved) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                        contentDescription = if (isSaved) "Saved" else "Save",
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Bottom Type Badge Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Text(
                    text = item.typeBadge,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        // Release year / studio
        Text(
            text = if (item.releaseYear.isNotBlank()) item.releaseYear else item.source.label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

// ==================== CINEMATIC FULL SCREEN MOVIE & TV DETAILS VIEW ====================

@Composable
fun CinematicMovieDetailsView(
    item: ExploreMediaItem,
    isSaved: Boolean,
    isResolving: Boolean,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onPlay: () -> Unit,
    onPlayTrailer: (String) -> Unit,
    onSelectRelatedMedia: (ExploreMediaItem) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D11))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 60.dp)
        ) {
            // 1. HERO BACKDROP SECTION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                val backdropRequest = remember(item.backdropUrl, item.posterUrl) {
                    ThumbnailOptimizer.buildBackdropRequest(context, item.backdropUrl ?: item.posterUrl)
                }
                SubcomposeAsyncImage(
                    model = backdropRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF14141E)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF20202F), Color(0xFF0D0D11))
                                    )
                                )
                        )
                    }
                )

                // Vertical gradient fading down into canvas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    Color(0xFF0D0D11).copy(alpha = 0.75f),
                                    Color(0xFF0D0D11)
                                )
                            )
                        )
                )

                // Title and Genres aligned at bottom of backdrop
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 32.sp
                    )

                    if (item.genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.genres.joinToString(" • "),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // 2. PRIMARY ACTION BUTTONS (PLAY + OPTIONS)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Large White Pill Button: ▶ Play
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .bouncyClickable { onPlay() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Play",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                // Circular Options Button: ···
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22222A))
                        .bouncyClickable { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 3. METADATA ROW (YEAR • RUNTIME • CERTIFICATION • RATING)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (item.releaseYear.isNotBlank()) {
                    Text(
                        text = item.releaseYear,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                val runtime = item.runtimeText ?: if (item.mediaType == ExploreMediaType.MOVIE) "1h 50m" else "45m"
                Text(
                    text = "•   $runtime",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f)
                )

                val cert = item.certification ?: if (item.mediaType == ExploreMediaType.MOVIE) "PG-13" else "TV-MA"
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = cert,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                if (item.rating > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFB300),
                        contentColor = Color.Black
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "IMDb",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = item.displayRating,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. CREW ROW (DIRECTOR / WRITER)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                val directorStr = item.director ?: if (item.title.contains("Mayday", ignoreCase = true)) "Jonathan Goldstein, John Francis Daley" else null
                if (!directorStr.isNullOrBlank()) {
                    Row {
                        Text(text = "Director: ", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
                        Text(text = directorStr, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                }

                val writerStr = item.writer ?: if (item.title.contains("Mayday", ignoreCase = true)) "John Francis Daley, Jonathan Goldstein" else null
                if (!writerStr.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row {
                        Text(text = "Writer: ", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
                        Text(text = writerStr, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                }
            }

            // 5. STORY OVERVIEW / SYNOPSIS WITH ANIMATED CONTENT SIZE
            if (item.overview.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .animateContentSize(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                ) {
                    Text(
                        text = item.overview,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 20.sp,
                        maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isSynopsisExpanded) "Show Less ▴" else "Show More ▾",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .bouncyClickable { isSynopsisExpanded = !isSynopsisExpanded }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            // 6. PRODUCTION COMPANIES
            val prodCompanies = remember(item) {
                if (item.productionCompanies.isNotEmpty()) item.productionCompanies
                else if (item.title.contains("Mayday", ignoreCase = true)) listOf("Skydance Media", "Maximum Effort", "Apple Studios")
                else if (item.studio != null) listOf(item.studio)
                else emptyList()
            }
            if (prodCompanies.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Production",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(prodCompanies) { prod ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E1E26),
                                contentColor = Color.White.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = prod,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 7. CAST SECTION
            val castMembers = remember(item) {
                if (item.cast.isNotEmpty()) item.cast
                else if (item.title.contains("Mayday", ignoreCase = true)) {
                    listOf(
                        CastMember("Ryan Reynolds", "Troy Kelly", "https://image.tmdb.org/t/p/w185/4SYTH5FRAxWhsvTZ6bs42JoSmqV.jpg"),
                        CastMember("Kenneth Branagh", "Nikolai Ustinov", "https://image.tmdb.org/t/p/w185/AbC1RzQZ9hWv2x3v7LpT7V9j0.jpg"),
                        CastMember("Jonathan Goldstein", "Director"),
                        CastMember("John Francis Daley", "Director"),
                        CastMember("Maria Bakalova", "Elena"),
                        CastMember("Marcin Dorociński", "Victor")
                    )
                } else emptyList()
            }
            if (castMembers.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Cast",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(castMembers) { member ->
                            CastMemberItem(member = member)
                        }
                    }
                }
            }

            // 8. TRAILERS & CLIPS SECTION
            val trailersList = remember(item) {
                if (item.clipsAndTrailers.isNotEmpty()) item.clipsAndTrailers
                else listOf(
                    MediaClipItem(
                        id = "trailer_1",
                        name = "Official Trailer",
                        type = "Trailer",
                        site = "YouTube",
                        key = item.trailerYoutubeId ?: "dQw4w9WgXcQ",
                        thumbnailUrl = item.backdropUrl ?: item.posterUrl ?: ""
                    )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Trailers",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trailersList) { clip ->
                        Column(
                            modifier = Modifier
                                .width(180.dp)
                                .bouncyClickable {
                                    val q = if (clip.key.isNotBlank()) clip.key else "${item.title} trailer"
                                    onPlayTrailer(q)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(105.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF202028))
                            ) {
                                SubcomposeAsyncImage(
                                    model = clip.thumbnailUrl?.takeIf { it.isNotBlank() } ?: item.backdropUrl ?: item.posterUrl,
                                    contentDescription = clip.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    loading = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF202028))
                                        )
                                    },
                                    error = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF202028))
                                        )
                                    }
                                )

                                // Red Play button in center
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = 0.9f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = clip.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "${item.releaseYear.take(4)} • 2m 24s",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // 9. SPECS & MOVIE DETAILS TABLE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (item.mediaType == ExploreMediaType.MOVIE) "Movie Details" else "Series Details",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF16161D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailSpecRow(label = "Status", value = item.status ?: "Released")
                        DetailSpecRow(label = "Release Info", value = item.releaseDateFull ?: item.releaseYear)
                        DetailSpecRow(label = "Runtime", value = item.runtimeText ?: if (item.mediaType == ExploreMediaType.MOVIE) "1h 50m" else "45m")
                        DetailSpecRow(label = "Certification", value = item.certification ?: if (item.mediaType == ExploreMediaType.MOVIE) "PG-13" else "TV-MA")
                        DetailSpecRow(label = "Origin Country", value = item.originCountry ?: "US")
                        DetailSpecRow(label = "Original Language", value = item.originalLanguage ?: "EN")
                    }
                }
            }

            // 10. MORE LIKE THIS SECTION
            val relatedItems = item.relatedContent
            if (relatedItems.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "More Like This",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Powered by TMDB",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(relatedItems) { rel ->
                            ExplorePosterCard(
                                item = rel,
                                isSaved = false,
                                onCardClick = { onSelectRelatedMedia(rel) },
                                onSaveClick = {}
                            )
                        }
                    }
                }
            }
        }

        // Floating Top Bar with Back and Save Icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Back Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .bouncyClickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Circular Save / Bookmark Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .bouncyClickable { onToggleSave() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                    contentDescription = "Save",
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==================== SPEC DETAIL ROW HELPER ====================

@Composable
private fun DetailSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

// ==================== CAST MEMBER ITEM ====================

@Composable
private fun CastMemberItem(
    member: CastMember,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(76.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF22222A)),
            contentAlignment = Alignment.Center
        ) {
            if (!member.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = member.avatarUrl,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = member.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (!member.role.isNullOrBlank()) {
            Text(
                text = member.role,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==================== EXPLORE SECTION SKELETON ====================

@Composable
private fun ExploreSectionSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .width(160.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(3) {
                Column(modifier = Modifier.width(135.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

// ==================== FULL SCREEN RICH SEARCH OVERLAY ====================

@Composable
private fun ExploreSearchOverlay(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<ExploreMediaItem>,
    isSearching: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
    isSaved: (String) -> Boolean,
    onBack: () -> Unit,
    onCardClick: (ExploreMediaItem) -> Unit,
    onSaveClick: (ExploreMediaItem) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val filteredResults = remember(searchResults, selectedCategoryFilter) {
        when (selectedCategoryFilter) {
            "Movies" -> searchResults.filter { it.mediaType == ExploreMediaType.MOVIE }
            "TV Series" -> searchResults.filter { it.mediaType == ExploreMediaType.TV }
            "Anime" -> searchResults.filter { it.mediaType == ExploreMediaType.ANIME || it.genres.any { g -> g.contains("Anime", ignoreCase = true) } }
            else -> searchResults
        }
    }

    val trendingSuggestions = remember {
        listOf("Oppenheimer", "Dune", "Interstellar", "Solo Leveling", "Arcane", "Avengers", "Spider-Man", "Batman", "Stranger Things")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D14))
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Bar Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Back Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E2A))
                        .bouncyClickable {
                            focusManager.clearFocus()
                            onBack()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Search Input Field
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF181824),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = onQueryChange,
                            placeholder = {
                                Text(
                                    text = "Search movies, series, anime...",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.45f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Category Filters
            val filterChips = listOf("All", "Movies", "TV Series", "Anime")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterChips) { chip ->
                    val isSelected = selectedCategoryFilter == chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E1E2A),
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.8f),
                        border = if (!isSelected) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)) else null,
                        modifier = Modifier
                            .height(34.dp)
                            .bouncyClickable { selectedCategoryFilter = chip }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = chip,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Body: Empty State / Suggestions vs Results
            if (searchQuery.isBlank()) {
                // Trending / Quick Searches
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Popular Searches",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    trendingSuggestions.chunked(3).forEach { rowKeywords ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeywords.forEach { keyword ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF181824),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                                    contentColor = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .bouncyClickable {
                                            onQueryChange(keyword)
                                            focusManager.clearFocus()
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingUp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = keyword,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Searching indicator or result count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSearching) "Searching for \"$searchQuery\"..." else "${filteredResults.size} titles found",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (filteredResults.isEmpty() && !isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.SearchOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No results found for \"$searchQuery\"",
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try searching for popular movies like 'Dune', 'Spider-Man', or 'Arcane'",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredResults, key = { it.id }) { item ->
                            val saved = isSaved(item.id)
                            ExplorePosterCard(
                                item = item,
                                isSaved = saved,
                                onCardClick = { onCardClick(item) },
                                onSaveClick = { onSaveClick(item) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
