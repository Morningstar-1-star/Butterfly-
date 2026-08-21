package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.SearchFilterState
import com.example.model.SearchTypeFilter
import com.example.model.SearchDurationFilter
import com.example.model.SearchSuggestionItem
import com.example.model.VideoItem
import com.example.model.ProviderUiItem
import com.example.ui.MainViewModel
import com.example.ui.components.VideoCard
import com.example.ui.components.SearchFilterDialog

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onSelectVideo: (VideoItem) -> Unit,
    onCloseSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()
    val availableProviders by viewModel.availableProviders.collectAsState()
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val showThumbnailTags by viewModel.showThumbnailTags.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var showFilterDialog by remember { mutableStateOf(false) }

    val watchedVideoIds = remember(watchHistory) { watchHistory.map { it.id }.toSet() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    if (showFilterDialog) {
        SearchFilterDialog(
            currentFilter = searchFilter,
            availableProviders = availableProviders,
            onDismiss = { showFilterDialog = false },
            onApply = { newFilter ->
                viewModel.updateSearchFilter(newFilter)
            },
            onReset = {
                viewModel.resetSearchFilter()
            }
        )
    }

    // Default trending topic fallbacks when user has no recent searches
    val trendingFallbacks = remember {
        listOf(
            "roman reigns vs cody rhodes",
            "minecraft hardcore 100 days",
            "ramayana trailer reaction",
            "sousou no frieren episode 1",
            "top trending music videos 2026",
            "best action movies full hd",
            "anime fight scenes 4k"
        )
    }

    // Map recent search strings to matching watch history thumbnail if available
    val historyThumbnailMap = remember(recentSearches, watchHistory) {
        val map = mutableMapOf<String, String>()
        recentSearches.forEach { search ->
            val match = watchHistory.find { it.title.contains(search, ignoreCase = true) || search.contains(it.title, ignoreCase = true) }
            if (match?.thumbnailUrl != null) {
                map[search] = match.thumbnailUrl
            }
        }
        map
    }

    // Build list of provider source chips (ALWAYS VISIBLE below search bar)
    val providerChips = remember(availableProviders, adultContentEnabled) {
        val list = mutableListOf<ProviderSourceItemData>()

        // 1. ALL
        list.add(
            ProviderSourceItemData(
                id = "ALL",
                label = "All Sources",
                icon = Icons.Default.Layers,
                accentColor = Color(0xFF3F51B5)
            )
        )

        val processedIds = mutableSetOf<String>("all", "ALL")

        // 2. Map available enabled providers
        availableProviders
            .filter { it.id.lowercase() != "all" && it.isEnabled }
            .filter { adultContentEnabled || !viewModel.isAdultProviderId(it.id) }
            .forEach { provider ->
                val pId = provider.id.lowercase()
                if (!processedIds.contains(pId)) {
                    processedIds.add(pId)
                    val (label, icon, color) = getProviderChipInfo(provider.id, provider.name)
                    list.add(ProviderSourceItemData(id = provider.id, label = label, icon = icon, accentColor = color))
                }
            }

        // 3. Ensure popular default providers exist
        val defaults = listOf(
            ProviderSourceItemData("youtube", "YouTube", Icons.Default.PlayArrow, Color(0xFFFF0000)),
            ProviderSourceItemData("dailymotion", "Dailymotion", Icons.Default.Movie, Color(0xFF0066DC)),
            ProviderSourceItemData("jikan_anime", "Anime", Icons.Default.Star, Color(0xFF7B1FA2)),
            ProviderSourceItemData("archive_org", "Archive.org", Icons.Default.Folder, Color(0xFF5D4037)),
            ProviderSourceItemData("mega", "Mega", Icons.Default.Cloud, Color(0xFFD32F2F)),
            ProviderSourceItemData("telegram", "Telegram", Icons.Default.Send, Color(0xFF0288D1))
        )
        defaults.forEach { item ->
            if (!processedIds.contains(item.id.lowercase())) {
                processedIds.add(item.id.lowercase())
                list.add(item)
            }
        }

        list
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TOP SEARCH BAR (Exact YouTube Layout with 3-Dots Filter Menu)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    if (onCloseSearch != null) {
                        onCloseSearch()
                    } else {
                        viewModel.clearSearch()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Input Pill
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = {
                    Text(
                        text = "Search Butterfly",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.performSearch()
                    }
                ),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .focusRequester(focusRequester)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Mic Icon Button
            IconButton(
                onClick = { /* Voice Search action */ },
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // 3-Dots Filter Menu (Circled in YouTube screenshot)
            Box {
                IconButton(
                    onClick = { showFilterDialog = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Search Filters",
                        tint = if (searchFilter.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (searchFilter.isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }

        // ALWAYS VISIBLE PROVIDER SOURCE SELECTOR BAR (Scrollable Row right under search bar)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(providerChips, key = { it.id }) { chipData ->
                val isSelected = if (chipData.id == "ALL") {
                    searchFilter.sourceProviderId == "ALL" || searchFilter.sourceProviderId.isBlank()
                } else {
                    searchFilter.sourceProviderId.equals(chipData.id, ignoreCase = true)
                }

                ProviderSourceChip(
                    data = chipData,
                    selected = isSelected,
                    onClick = {
                        val newSource = if (isSelected && chipData.id != "ALL") "ALL" else chipData.id
                        viewModel.updateSearchFilter(searchFilter.copy(sourceProviderId = newSource))
                    }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f), thickness = 1.dp)

        // HORIZONTAL QUICK FILTER CHIPS (YouTube Style Bar)
        if (searchResults.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Filter" button chip with active badge
                item {
                    SearchQuickChip(
                        label = if (searchFilter.isActive) "Filters (${searchFilter.activeFilterCount})" else "Filters",
                        selected = searchFilter.isActive,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (searchFilter.isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { showFilterDialog = true }
                    )
                }

                // "All" chip
                item {
                    SearchQuickChip(
                        label = "All",
                        selected = !searchFilter.isActive,
                        onClick = { viewModel.resetSearchFilter() }
                    )
                }

                // "Movies" chip
                item {
                    val isSelected = searchFilter.type == SearchTypeFilter.MOVIES
                    SearchQuickChip(
                        label = "Movies",
                        selected = isSelected,
                        onClick = {
                            viewModel.updateSearchFilter(
                                if (isSelected) searchFilter.copy(type = SearchTypeFilter.ALL)
                                else searchFilter.copy(type = SearchTypeFilter.MOVIES)
                            )
                        }
                    )
                }

                // "TV Shows" chip
                item {
                    val isSelected = searchFilter.type == SearchTypeFilter.TV_SHOWS
                    SearchQuickChip(
                        label = "TV Shows",
                        selected = isSelected,
                        onClick = {
                            viewModel.updateSearchFilter(
                                if (isSelected) searchFilter.copy(type = SearchTypeFilter.ALL)
                                else searchFilter.copy(type = SearchTypeFilter.TV_SHOWS)
                            )
                        }
                    )
                }

                // "YouTube" chip
                item {
                    val isSelected = searchFilter.sourceProviderId.equals("youtube", ignoreCase = true)
                    SearchQuickChip(
                        label = "YouTube",
                        selected = isSelected,
                        onClick = {
                            viewModel.updateSearchFilter(
                                if (isSelected) searchFilter.copy(sourceProviderId = "ALL")
                                else searchFilter.copy(sourceProviderId = "youtube")
                            )
                        }
                    )
                }

                // "Unwatched" chip
                item {
                    SearchQuickChip(
                        label = "Unwatched",
                        selected = searchFilter.isUnwatchedOnly,
                        onClick = {
                            viewModel.updateSearchFilter(
                                searchFilter.copy(
                                    isUnwatchedOnly = !searchFilter.isUnwatchedOnly,
                                    isWatchedOnly = false
                                )
                            )
                        }
                    )
                }

                // "Watched" chip
                item {
                    SearchQuickChip(
                        label = "Watched",
                        selected = searchFilter.isWatchedOnly,
                        onClick = {
                            viewModel.updateSearchFilter(
                                searchFilter.copy(
                                    isWatchedOnly = !searchFilter.isWatchedOnly,
                                    isUnwatchedOnly = false
                                )
                            )
                        }
                    )
                }

                // "Under 4 mins"
                item {
                    val isSelected = searchFilter.duration == SearchDurationFilter.UNDER_4_MIN
                    SearchQuickChip(
                        label = "< 4 min",
                        selected = isSelected,
                        onClick = {
                            viewModel.updateSearchFilter(
                                if (isSelected) searchFilter.copy(duration = SearchDurationFilter.ANY)
                                else searchFilter.copy(duration = SearchDurationFilter.UNDER_4_MIN)
                            )
                        }
                    )
                }

                // "4 - 20 mins"
                item {
                    val isSelected = searchFilter.duration == SearchDurationFilter.FOUR_TO_TWENTY_MIN
                    SearchQuickChip(
                        label = "4 – 20 min",
                        selected = isSelected,
                        onClick = {
                            viewModel.updateSearchFilter(
                                if (isSelected) searchFilter.copy(duration = SearchDurationFilter.ANY)
                                else searchFilter.copy(duration = SearchDurationFilter.FOUR_TO_TWENTY_MIN)
                            )
                        }
                    )
                }

                // "Over 20 mins"
                item {
                    val isSelected = searchFilter.duration == SearchDurationFilter.OVER_20_MIN
                    SearchQuickChip(
                        label = "> 20 min",
                        selected = isSelected,
                        onClick = {
                            viewModel.updateSearchFilter(
                                if (isSelected) searchFilter.copy(duration = SearchDurationFilter.ANY)
                                else searchFilter.copy(duration = SearchDurationFilter.OVER_20_MIN)
                            )
                        }
                    )
                }

                // "4K UHD"
                item {
                    SearchQuickChip(
                        label = "4K UHD",
                        selected = searchFilter.is4kOnly,
                        onClick = {
                            viewModel.updateSearchFilter(
                                searchFilter.copy(is4kOnly = !searchFilter.is4kOnly)
                            )
                        }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

        // CONTENT AREA
        if (isSearching && searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Searching across video sources...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (searchResults.isNotEmpty()) {
            if (isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            val baseResults = searchResults
                .filter { adultContentEnabled || !viewModel.isAdultVideoItem(it) }
                .filter { com.example.util.LanguageFilterHelper.isAllowedVideoItem(it) }
                .distinctBy { "${it.providerId}_${it.id}" }
            val filteredResults = remember(baseResults, searchFilter, watchedVideoIds) {
                searchFilter.applyTo(baseResults, watchedVideoIds)
            }

            LaunchedEffect(filteredResults) {
                if (filteredResults.isNotEmpty()) {
                    com.example.util.ThumbnailOptimizer.preloadThumbnails(context, filteredResults, maxCount = 12)
                }
            }

            if (filteredResults.isEmpty() && baseResults.isNotEmpty()) {
                // Empty filter results with option to reset
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No results match active filters",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Found ${baseResults.size} total results for \"$searchQuery\", but none match the current filter criteria.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.resetSearchFilter() },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Filters")
                        }
                    }
                }
            } else {
                // SEARCH RESULTS VIEW
                val searchListState = rememberLazyListState()
                val shouldLoadMoreSearch = remember {
                    derivedStateOf {
                        val layoutInfo = searchListState.layoutInfo
                        val total = layoutInfo.totalItemsCount
                        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        total > 0 && lastVisible >= total - 3
                    }
                }
                LaunchedEffect(shouldLoadMoreSearch.value) {
                    if (shouldLoadMoreSearch.value && !isSearching && !isLoadingMore) {
                        viewModel.loadMoreContent()
                    }
                }

                LazyColumn(
                    state = searchListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp, start = 0.dp, end = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Results for \"$searchQuery\"",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (searchFilter.isActive) {
                                Text(
                                    text = "${filteredResults.size} of ${baseResults.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    items(filteredResults, key = { (it.providerId ?: "") + "_" + it.id }) { video ->
                        VideoCard(
                            video = video,
                            showProviderBadge = showThumbnailTags,
                            onClick = { onSelectVideo(video) },
                            onNotInterested = { v -> viewModel.markNotInterested(v) },
                            onPlayNextInQueue = { v -> viewModel.addToQueue(v) },
                            onSaveToWatchLater = { v -> viewModel.addToWatchLater(v) },
                            onDownload = { v -> viewModel.showDownloadSheet(v) },
                            onChannelClick = { ch -> viewModel.openChannel(ch) }
                        )
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
        } else if (searchQuery.isNotEmpty()) {
            // REALTIME AUTOCOMPLETE & MATCHED HISTORY LIST (YouTube Style)
            val effectiveSuggestions = remember(searchQuery, searchSuggestions) {
                if (searchSuggestions.isNotEmpty()) {
                    searchSuggestions
                } else {
                    val q = searchQuery.trim()
                    listOf(
                        SearchSuggestionItem(query = q, isHistory = false),
                        SearchSuggestionItem(query = "$q video", isHistory = false, providerBadge = "YouTube"),
                        SearchSuggestionItem(query = "$q song", isHistory = false, providerBadge = "YouTube"),
                        SearchSuggestionItem(query = "$q movie", isHistory = false, providerBadge = "TMDB"),
                        SearchSuggestionItem(query = "$q trailer", isHistory = false, providerBadge = "YouTube"),
                        SearchSuggestionItem(query = "$q reaction", isHistory = false, providerBadge = "YouTube")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(effectiveSuggestions) { suggestion ->
                    val thumbnail = historyThumbnailMap[suggestion.query] ?: suggestion.thumbnailUrl
                    SearchSuggestionRow(
                        suggestion = suggestion,
                        thumbnailUrl = thumbnail,
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.updateSearchQuery(suggestion.query)
                            viewModel.performSearch(suggestion.query)
                        },
                        onInsertQuery = {
                            viewModel.updateSearchQuery(suggestion.query)
                        },
                        onDeleteHistory = {
                            viewModel.removeRecentSearch(suggestion.query)
                        }
                    )
                }
            }
        } else {
            // EMPTY SEARCH QUERY -> SHOW RECENT SEARCH HISTORY LIST OR TRENDING FALLBACKS
            val listToShow = if (recentSearches.isNotEmpty()) recentSearches else trendingFallbacks
            val isActualHistory = recentSearches.isNotEmpty()

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (!isActualHistory) {
                    item {
                        Text(
                            text = "Popular Searches",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                }
                items(listToShow) { historyQuery ->
                    val thumbnail = historyThumbnailMap[historyQuery]
                    SearchSuggestionRow(
                        suggestion = SearchSuggestionItem(
                            query = historyQuery,
                            isHistory = isActualHistory,
                            providerBadge = if (!isActualHistory) "Trending" else null
                        ),
                        thumbnailUrl = thumbnail,
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.updateSearchQuery(historyQuery)
                            viewModel.performSearch(historyQuery)
                        },
                        onInsertQuery = {
                            viewModel.updateSearchQuery(historyQuery)
                        },
                        onDeleteHistory = {
                            if (isActualHistory) {
                                viewModel.removeRecentSearch(historyQuery)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchSuggestionRow(
    suggestion: SearchSuggestionItem,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onInsertQuery: () -> Unit,
    onDeleteHistory: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove from search history?", fontSize = 16.sp) },
            text = { Text("Delete \"${suggestion.query}\" from your search history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteHistory()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (suggestion.isHistory) {
                        showDeleteDialog = true
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Icon: Clock for History, Trending icon for Trending, Magnifying Glass for Search Suggestion
        val iconVector = when {
            suggestion.isHistory -> Icons.Default.History
            suggestion.providerBadge == "Trending" -> Icons.Default.TrendingUp
            else -> Icons.Default.Search
        }

        Icon(
            imageVector = iconVector,
            contentDescription = null,
            tint = if (suggestion.isHistory) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Query text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.query,
                fontSize = 15.sp,
                fontWeight = if (suggestion.isHistory) FontWeight.Normal else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!suggestion.subtitle.isNullOrBlank()) {
                Text(
                    text = suggestion.subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Provider Badge (if any)
        if (!suggestion.providerBadge.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                Text(
                    text = suggestion.providerBadge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Optional Thumbnail Image on the right (matching YouTube screenshot)
        if (!thumbnailUrl.isNullOrBlank()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val thumbRequest = remember(thumbnailUrl) {
                com.example.util.ThumbnailOptimizer.buildThumbnailRequest(context, thumbnailUrl, preferCompact = true)
            }
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = thumbRequest ?: thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Far-Right Insert Arrow Button ↖ (Inserts text into search field)
        IconButton(
            onClick = onInsertQuery,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NorthWest,
                contentDescription = "Insert search term",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SearchQuickChip(
    label: String,
    selected: Boolean,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

private data class ProviderSourceItemData(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accentColor: Color
)

private fun getProviderChipInfo(id: String, defaultName: String): Triple<String, ImageVector, Color> {
    return when (id.lowercase()) {
        "youtube" -> Triple("YouTube", Icons.Default.PlayArrow, Color(0xFFFF0000))
        "dailymotion" -> Triple("Dailymotion", Icons.Default.Movie, Color(0xFF0066DC))
        "jikan_anime", "anime" -> Triple("Anime", Icons.Default.Star, Color(0xFF7B1FA2))
        "archive_org", "internet_archive" -> Triple("Archive.org", Icons.Default.Folder, Color(0xFF5D4037))
        "mega" -> Triple("Mega", Icons.Default.Cloud, Color(0xFFD32F2F))
        "telegram" -> Triple("Telegram", Icons.Default.Send, Color(0xFF0288D1))
        "direct_mp4", "direct_hls" -> Triple("Direct Video", Icons.Default.VideoLibrary, Color(0xFF00796B))
        "rss_video", "json" -> Triple("Feeds", Icons.Default.RssFeed, Color(0xFFF57C00))
        "eporner" -> Triple("Eporner", Icons.Default.Explicit, Color(0xFFC2185B))
        "apijav_server", "apijav" -> Triple("ApiJav", Icons.Default.Explicit, Color(0xFF8E24AA))
        "javinfo" -> Triple("JavInfo", Icons.Default.Explicit, Color(0xFF5E35B1))
        "apijav_hentai" -> Triple("Hentai", Icons.Default.Explicit, Color(0xFFD81B60))
        "apijav_porn" -> Triple("Adult Feeds", Icons.Default.Explicit, Color(0xFFAD1457))
        else -> Triple(defaultName.ifBlank { id.replaceFirstChar { it.uppercase() } }, Icons.Default.VideoLibrary, Color(0xFF546E7A))
    }
}

@Composable
private fun ProviderSourceChip(
    data: ProviderSourceItemData,
    selected: Boolean,
    onClick: () -> Unit
) {
    val isDarkBg = data.accentColor == Color(0xFF000000) || data.accentColor == Color(0xFF111111)
    val activeBg = if (isDarkBg) MaterialTheme.colorScheme.onSurface else data.accentColor
    val activeContent = if (isDarkBg) MaterialTheme.colorScheme.surface else Color.White

    val backgroundColor = if (selected) activeBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val contentColor = if (selected) activeContent else MaterialTheme.colorScheme.onSurface
    val iconTint = if (selected) activeContent else data.accentColor

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) activeBg else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = if (selected) activeContent.copy(alpha = 0.22f) else data.accentColor.copy(alpha = 0.18f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = data.label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
            if (selected && data.id != "ALL") {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = activeContent,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

