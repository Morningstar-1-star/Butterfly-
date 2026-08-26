package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.*
import com.example.ui.MainViewModel
import com.example.util.ExploreMediaHelper
import kotlinx.coroutines.launch

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

    val savedBookmarks by viewModel.watchLaterList.collectAsState()

    var sections by remember { mutableStateOf<List<ExploreSection>>(emptyList()) }
    var isLoadingFeed by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    var selectedFilterCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ExploreMediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var selectedMediaForDetails by remember { mutableStateOf<ExploreMediaItem?>(null) }
    var resolvedMediaDetails by remember { mutableStateOf<ExploreMediaItem?>(null) }
    var isResolvingDetails by remember { mutableStateOf(false) }

    var activeTorrentMedia by remember { mutableStateOf<ExploreMediaItem?>(null) }
    var activeTorrentIdentity by remember { mutableStateOf<com.example.torrent.provider.MediaIdentity?>(null) }
    val torrentReleases by viewModel.torrentReleases.collectAsState()
    val isSearchingTorrents by viewModel.isSearchingTorrents.collectAsState()

    // Load initial explore feed
    fun loadFeed(forceRefresh: Boolean = false) {
        coroutineScope.launch {
            if (forceRefresh) isRefreshing = true else isLoadingFeed = true
            try {
                sections = ExploreMediaHelper.fetchExploreFeed()
            } catch (e: Exception) {
                // Keep existing or fallback
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
            com.example.util.ThumbnailOptimizer.preloadUrls(context, allItems.map { it.posterUrl ?: it.backdropUrl }, maxCount = 35)
        }
    }

    // Perform live search when search query changes
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

    // Filter items based on active category
    val filteredCategoryItems by produceState<List<ExploreMediaItem>>(initialValue = emptyList(), key1 = selectedFilterCategory) {
        if (selectedFilterCategory == "All") {
            value = emptyList()
        } else {
            val mediaType = when (selectedFilterCategory) {
                "Movies" -> ExploreMediaType.MOVIE
                "TV Series" -> ExploreMediaType.TV
                "Anime" -> ExploreMediaType.ANIME
                else -> ExploreMediaType.ALL
            }
            value = ExploreMediaHelper.fetchCategoryItems(mediaType)
        }
    }

    val pullRefreshState = rememberPullToRefreshState()

    // Featured hero item from trending movies or anime
    val heroItem = remember(sections) {
        sections.firstOrNull()?.items?.firstOrNull()
            ?: sections.getOrNull(1)?.items?.firstOrNull()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { loadFeed(forceRefresh = true) },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topPadding + 4.dp,
                    bottom = bottomPadding + 24.dp
                )
            ) {
                // 1. EXPLORE SEARCH & HEADER BAR
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        // Search text field
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text(
                                            text = "Search movies, series, anime (TMDB & AniList)...",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                    modifier = Modifier.weight(1f)
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category Chips Row
                        val categories = listOf("All", "Movies", "TV Series", "Anime")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { cat ->
                                val isSelected = (selectedFilterCategory == cat && searchQuery.isBlank())
                                val chipBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                val chipFg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                                Surface(
                                    onClick = {
                                        selectedFilterCategory = cat
                                        if (searchQuery.isNotBlank()) searchQuery = ""
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = chipBg,
                                    contentColor = chipFg,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val icon = when (cat) {
                                            "Movies" -> Icons.Outlined.Movie
                                            "TV Series" -> Icons.Outlined.Tv
                                            "Anime" -> Icons.Outlined.AutoAwesome
                                            else -> Icons.Outlined.Explore
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = cat,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = cat,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. SEARCH RESULTS VIEW (IF SEARCHING)
                if (searchQuery.isNotBlank()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Results for \"$searchQuery\"",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = "${searchResults.size} found",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (searchResults.isEmpty() && !isSearching) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.SearchOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No results found for \"$searchQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        // Display search items in responsive grid
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(searchResults, key = { it.id }) { item ->
                                    val isSaved = viewModel.isExploreMediaSaved(item.id)
                                    ExplorePosterCard(
                                        item = item,
                                        isSaved = isSaved,
                                        onCardClick = {
                                            selectedMediaForDetails = item
                                        },
                                        onSaveClick = {
                                            viewModel.toggleSaveExploreMedia(item)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                // 3. CATEGORY FILTER VIEW (IF CATEGORY != ALL)
                else if (selectedFilterCategory != "All") {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Explore $selectedFilterCategory",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    if (filteredCategoryItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    } else {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(filteredCategoryItems, key = { it.id }) { item ->
                                    val isSaved = viewModel.isExploreMediaSaved(item.id)
                                    ExplorePosterCard(
                                        item = item,
                                        isSaved = isSaved,
                                        onCardClick = { selectedMediaForDetails = item },
                                        onSaveClick = { viewModel.toggleSaveExploreMedia(item) }
                                    )
                                }
                            }
                        }
                    }
                }
                // 4. MAIN CURATED FEED (HERO + HORIZONTAL SECTIONS)
                else {
                    // HERO SPOTLIGHT BANNER
                    if (heroItem != null) {
                        item {
                            val isHeroSaved = viewModel.isExploreMediaSaved(heroItem.id)
                            HeroSpotlightBanner(
                                item = heroItem,
                                isSaved = isHeroSaved,
                                onCardClick = { selectedMediaForDetails = heroItem },
                                onSaveClick = { viewModel.toggleSaveExploreMedia(heroItem) },
                                onWatchClick = {
                                    val searchQ = "${heroItem.title} trailer"
                                    viewModel.updateSearchQuery(searchQ)
                                    viewModel.performSearch(searchQ)
                                    viewModel.navigateToScreen(AppScreen.HOME)
                                }
                            )
                        }
                    }

                    // SKELETON LOADING
                    if (isLoadingFeed && sections.isEmpty()) {
                        items(3) {
                            ExploreSectionSkeleton()
                        }
                    } else {
                        // HORIZONTAL SECTIONS
                        items(sections, key = { it.title }) { section ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                // Section Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
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
                                }

                                // Carousel Row
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(section.items, key = { it.id }) { mediaItem ->
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

        // 5. MEDIA DETAILS BOTTOM SHEET
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

            MediaDetailsBottomSheet(
                item = displayItem,
                isSaved = isSaved,
                onDismiss = {
                    selectedMediaForDetails = null
                    resolvedMediaDetails = null
                },
                onToggleSave = { viewModel.toggleSaveExploreMedia(displayItem) },
                onOpenTorrentStreams = {
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
                onPlayTrailerOrSearch = { query ->
                    selectedMediaForDetails = null
                    resolvedMediaDetails = null
                    viewModel.updateSearchQuery(query)
                    viewModel.performSearch(query)
                    viewModel.navigateToScreen(AppScreen.HOME)
                },
                onSelectRelatedMedia = { related ->
                    selectedMediaForDetails = related
                    resolvedMediaDetails = null
                }
            )
        }

        // BitTorrent P2P Releases Selector Sheet
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

// ==================== HERO SPOTLIGHT BANNER ====================

@Composable
private fun HeroSpotlightBanner(
    item: ExploreMediaItem,
    isSaved: Boolean,
    onCardClick: () -> Unit,
    onSaveClick: () -> Unit,
    onWatchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageModel = remember(item.backdropUrl, item.posterUrl) {
        ImageRequest.Builder(context)
            .data(item.backdropUrl ?: item.posterUrl)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(230.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCardClick() }
    ) {
        // Backdrop Image
        AsyncImage(
            model = imageModel,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay for crisp typography
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Top Badges (Source & Type)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = "FEATURED SPOTLIGHT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    letterSpacing = 0.5.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.65f),
                contentColor = Color.White
            ) {
                Text(
                    text = item.typeBadge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Quick Save Button in Top-Right
        IconButton(
            onClick = onSaveClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                contentDescription = if (isSaved) "Saved" else "Save to Movies & TV",
                tint = if (isSaved) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Bottom Info & Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            // Rating + Year + Genres
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (item.rating > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFB300),
                        contentColor = Color.Black
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = item.displayRating, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (item.releaseYear.isNotBlank()) {
                    Text(
                        text = item.releaseYear,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }

                if (item.genres.isNotEmpty()) {
                    Text(
                        text = "• " + item.genres.take(2).joinToString(", "),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Buttons: Watch Trailer / Play & Quick Save
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onWatchClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Watch Trailer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSaveClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.6f)))
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSaved) "Saved" else "Save",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSaved) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }
        }
    }
}

// ==================== EXPLORE POSTER CARD ====================

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
        ImageRequest.Builder(context)
            .data(item.posterUrl ?: item.backdropUrl)
            .crossfade(true)
            .build()
    }

    Column(
        modifier = modifier
            .width(135.dp)
            .clickable { onCardClick() }
    ) {
        // Poster Box with Aspect Ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top rating badge
            if (item.rating > 0) {
                Surface(
                    shape = RoundedCornerShape(topStart = 14.dp, bottomEnd = 10.dp),
                    color = Color.Black.copy(alpha = 0.75f),
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
                    .padding(6.dp)
                    .size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                        contentDescription = if (isSaved) "Saved" else "Save",
                        modifier = Modifier.size(16.dp)
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
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
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

        // Release year / source
        Text(
            text = if (item.releaseYear.isNotBlank()) item.releaseYear else item.source.label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

// ==================== MEDIA DETAILS BOTTOM SHEET ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsBottomSheet(
    item: ExploreMediaItem,
    isSaved: Boolean,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenTorrentStreams: () -> Unit = {},
    onPlayTrailerOrSearch: (String) -> Unit,
    onSelectRelatedMedia: (ExploreMediaItem) -> Unit = {}
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedScreenshotUrl by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // 1. BACKDROP & HERO HEADER
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    AsyncImage(
                        model = item.backdropUrl ?: item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                )
                            )
                    )

                    // Large Trailer Play button over backdrop
                    IconButton(
                        onClick = {
                            val q = if (!item.trailerYoutubeId.isNullOrBlank()) item.trailerYoutubeId else "${item.title} trailer"
                            onPlayTrailerOrSearch(q)
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play Trailer",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            // 2. TITLE & PRIMARY METADATA
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!item.tagline.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "\"${item.tagline}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!item.originalTitle.isNullOrBlank() && item.originalTitle != item.title) {
                        Text(
                            text = item.originalTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Meta Chips Row (Type, Year, Rating, Runtime, Director, IMDb ID)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        // Rating Chip
                        if (item.rating > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFB300),
                                contentColor = Color.Black
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${item.displayRating} (${item.ratingSource})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Type Chip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = item.typeBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Release Year
                        if (item.releaseYear.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    text = item.releaseYear,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Runtime
                        if (!item.runtimeText.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = item.runtimeText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Director / Studio
                        val creatorName = item.director ?: item.studio
                        if (!creatorName.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    text = creatorName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // IMDb ID Badge
                        if (!item.imdbId.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF5C518),
                                contentColor = Color.Black
                            ) {
                                Text(
                                    text = "IMDb: ${item.imdbId}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Action: Stream via BitTorrent P2P Engine
                    Button(
                        onClick = onOpenTorrentStreams,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C4DFF),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (item.mediaType == ExploreMediaType.MOVIE) "Stream Full Movie (P2P Releases)" else "Stream Episodes (P2P Releases)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Action Buttons Row (Save to Movies & TV + Watch Trailer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggleSave,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Check else Icons.Outlined.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaved) "Saved to You" else "Save to You",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val q = if (!item.trailerYoutubeId.isNullOrBlank()) item.trailerYoutubeId else "${item.title} trailer"
                                onPlayTrailerOrSearch(q)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Play Trailer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Genres Row
                    if (item.genres.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            item.genres.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Overview / Synopsis
                    if (item.overview.isNotBlank()) {
                        Text(
                            text = "Story Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            // 3. CLIPS & TRAILERS GALLERY
            if (item.clipsAndTrailers.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Clips & Trailers (${item.clipsAndTrailers.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(item.clipsAndTrailers) { clip ->
                                Column(
                                    modifier = Modifier
                                        .width(200.dp)
                                        .clickable { onPlayTrailerOrSearch(clip.key.ifBlank { "${item.title} ${clip.name}" }) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(112.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    ) {
                                        if (!clip.thumbnailUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = clip.thumbnailUrl,
                                                contentDescription = clip.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        // Play Icon Overlay
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayCircleFilled,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }

                                        // Badge
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.Black.copy(alpha = 0.75f),
                                            contentColor = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = clip.type,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = clip.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 4. SCREENSHOTS & STILLS GALLERY
            if (item.screenshots.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Screenshots & Stills",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(item.screenshots) { screenshotUrl ->
                                Box(
                                    modifier = Modifier
                                        .width(220.dp)
                                        .height(125.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable { selectedScreenshotUrl = screenshotUrl }
                                ) {
                                    AsyncImage(
                                        model = screenshotUrl,
                                        contentDescription = "Screenshot",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 5. CAST & CHARACTERS SECTION
            if (item.cast.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Cast & Characters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(item.cast) { member ->
                                CastMemberItem(member = member)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 6. AUDIENCE REVIEWS & COMMENTS (IMDb / TMDB / AniList)
            if (item.reviews.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Audience Reviews & Comments (${item.reviews.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item.reviews.forEach { review ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!review.authorAvatarUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = review.authorAvatarUrl,
                                                            contentDescription = review.author,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Text(
                                                            text = review.author.take(1).uppercase(),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                }

                                                Column {
                                                    Text(
                                                        text = review.author,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = review.source + (if (!review.createdAt.isNullOrBlank()) " • ${review.createdAt}" else ""),
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (review.rating != null && review.rating > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFFFB300),
                                                    contentColor = Color.Black
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(10.dp))
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = String.format("%.1f", review.rating),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = review.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                            lineHeight = 18.sp,
                                            maxLines = 6,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 7. RELATED & RECOMMENDED CONTENT
            if (item.relatedContent.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Related & More Like This",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(item.relatedContent) { related ->
                                ExplorePosterCard(
                                    item = related,
                                    isSaved = false,
                                    onCardClick = { onSelectRelatedMedia(related) },
                                    onSaveClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Screenshot Fullscreen Zoom Dialog
    if (selectedScreenshotUrl != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { selectedScreenshotUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = selectedScreenshotUrl,
                    contentDescription = "Full Screenshot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { selectedScreenshotUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
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
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = member.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (!member.role.isNullOrBlank()) {
            Text(
                text = member.role,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
