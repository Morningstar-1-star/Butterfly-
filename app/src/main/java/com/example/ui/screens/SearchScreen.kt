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
import com.example.ui.components.SourceBrandLogo
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.semantics.Role
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
    val activeSanitized by viewModel.activeSearchSanitizedResult.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()
    val availableProviders by viewModel.availableProviders.collectAsState()
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val showThumbnailTags by viewModel.showThumbnailTags.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val trendingTopics by viewModel.trendingTopics.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val directUrlMatchItem by viewModel.directUrlMatchItem.collectAsState()
    val detectedCategoryTags by viewModel.detectedCategoryTags.collectAsState()
    val clipboardUrlSuggestion by viewModel.clipboardUrlSuggestion.collectAsState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var showFilterDialog by remember { mutableStateOf(false) }

    val watchedVideoIds = remember(watchHistory) { watchHistory.map { it.id }.toSet() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
            viewModel.checkClipboardForVideoUrl()
        } catch (_: Exception) {}
    }

    if (showFilterDialog) {
        val filteredAvailableProviders = remember(availableProviders, adultContentEnabled) {
            availableProviders.filter {
                if (adultContentEnabled) {
                    viewModel.isAdultProviderId(it.id) && !viewModel.isNormalProvider(it.id)
                } else {
                    !viewModel.isAdultProviderId(it.id)
                }
            }
        }
        SearchFilterDialog(
            currentFilter = searchFilter,
            availableProviders = filteredAvailableProviders,
            onDismiss = { showFilterDialog = false },
            onApply = { newFilter ->
                viewModel.updateSearchFilter(newFilter)
            },
            onReset = {
                viewModel.resetSearchFilter()
            }
        )
    }

    // Default trending topic fallbacks when offline or loading
    val trendingFallbacks = remember(adultContentEnabled) {
        if (adultContentEnabled) {
            listOf(
                "Yua Mikami",
                "Eimi Fukada",
                "Karen Kaede",
                "SSIS",
                "IPX",
                "MIDE",
                "SNIS",
                "Cosplay",
                "Japanese",
                "Uncensored",
                "Amateur",
                "4K Ultra HD"
            )
        } else {
            listOf(
                "Toy Story 5",
                "Mutiny",
                "Spider-Man: Brand New Day",
                "Lanterns",
                "Reacher",
                "Silo",
                "Deadpool & Wolverine",
                "Dune: Part Two",
                "Stranger Things",
                "Arcane",
                "Solo Leveling",
                "House of the Dragon"
            )
        }
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
        // 2. Add all available providers from ViewModel
        availableProviders
            .filter { it.id.lowercase() != "all" }
            .filter {
                if (adultContentEnabled) {
                    viewModel.isAdultProviderId(it.id) && !viewModel.isNormalProvider(it.id)
                } else {
                    !viewModel.isAdultProviderId(it.id)
                }
            }
            .forEach { provider ->
                val pId = provider.id.lowercase()
                if (!processedIds.contains(pId)) {
                    processedIds.add(pId)
                    val (label, icon, color) = getProviderChipInfo(provider.id, provider.name)
                    list.add(ProviderSourceItemData(id = provider.id, label = label, icon = icon, accentColor = color))
                }
            }

        // 3. Ensure popular default providers exist in normal mode
        if (!adultContentEnabled) {
            val defaults = listOf(
                ProviderSourceItemData("tencent", "Tencent Video", Icons.Default.Tv, Color(0xFF0052D9)),
                ProviderSourceItemData("youtube", "YouTube", Icons.Default.PlayArrow, Color(0xFFFF0000)),
                ProviderSourceItemData("bilibili", "Bilibili", Icons.Default.Tv, Color(0xFF00A1D6)),
                ProviderSourceItemData("sonyliv", "SonyLIV", Icons.Default.Tv, Color(0xFF003087)),
                ProviderSourceItemData("hotstar", "Hotstar", Icons.Default.Star, Color(0xFF001435)),
                ProviderSourceItemData("amazonminitv", "miniTV", Icons.Default.Tv, Color(0xFFFF9900)),
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
        } else {
            val adultDefaults = listOf(
                ProviderSourceItemData("eporner", "Eporner", Icons.Default.Explicit, Color(0xFFFF5722)),
                ProviderSourceItemData("spankbang", "SpankBang", Icons.Default.Explicit, Color(0xFFFF4081)),
                ProviderSourceItemData("xnxx", "XNXX", Icons.Default.Explicit, Color(0xFF00B0FF)),
                ProviderSourceItemData("hellporno", "HellPorno", Icons.Default.Explicit, Color(0xFFFF1744)),
                ProviderSourceItemData("pornhub", "Pornhub", Icons.Default.Explicit, Color(0xFFFF9900)),
                ProviderSourceItemData("xvideos", "XVideos", Icons.Default.Explicit, Color(0xFFD32F2F)),
                ProviderSourceItemData("stripchat", "Stripchat", Icons.Default.VideoLibrary, Color(0xFFFF3D00)),
                ProviderSourceItemData("chaturbate", "Chaturbate", Icons.Default.VideoLibrary, Color(0xFFFF6D00)),
                ProviderSourceItemData("sextb", "SEXTB", Icons.Default.Explicit, Color(0xFFE91E63)),
                ProviderSourceItemData("supjav", "SupJav", Icons.Default.Explicit, Color(0xFFFF4081)),
                ProviderSourceItemData("123av", "123AV", Icons.Default.Explicit, Color(0xFF9C27B0))
            )
            adultDefaults.forEach { item ->
                if (!processedIds.contains(item.id.lowercase())) {
                    processedIds.add(item.id.lowercase())
                    list.add(item)
                }
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
                        text = "Search title, tags (#fantasy), or paste video link...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                ),
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
                    .defaultMinSize(minHeight = 44.dp)
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
                        viewModel.setSearchSourceProvider(newSource)
                    }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f), thickness = 1.dp)

        // CLIPBOARD URL QUICK PASTE BANNER
        androidx.compose.animation.AnimatedVisibility(
            visible = clipboardUrlSuggestion != null && searchQuery.isBlank()
        ) {
            clipboardUrlSuggestion?.let { clipUrl ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable {
                            viewModel.updateSearchQuery(clipUrl)
                            viewModel.performSearch(clipUrl)
                            viewModel.clearClipboardSuggestion()
                        },
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Paste Link",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Paste & Search Link: ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = clipUrl,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { viewModel.clearClipboardSuggestion() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // DETECTED CATEGORY & GENRE TAG BADGES
        if (detectedCategoryTags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        text = "Tags:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(detectedCategoryTags) { tag ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "#$tag",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

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
                .filter {
                    if (adultContentEnabled) {
                        (viewModel.isAdultVideoItem(it) || viewModel.isAdultProviderId(it.providerId)) && !viewModel.isNormalProvider(it.providerId)
                    } else {
                        !viewModel.isAdultVideoItem(it) && !viewModel.isAdultProviderId(it.providerId)
                    }
                }
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
                    // DIRECT URL MATCH HERO CARD
                    directUrlMatchItem?.let { heroVideo ->
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSelectVideo(heroVideo) },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Header badge
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "DIRECT LINK MATCH",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    letterSpacing = 0.8.sp
                                                )
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                                            ) {
                                                val heroBadge = com.example.util.SourceTagHelper.getSourceBadge(heroVideo)
                                                Text(
                                                    text = heroBadge.name,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // Video Thumbnail Card
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .background(Color.Black)
                                        ) {
                                            AsyncImage(
                                                model = heroVideo.thumbnailUrl,
                                                contentDescription = heroVideo.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            // Play Button Overlay
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                                    .align(Alignment.Center),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Play Direct Match",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                            // Duration pill
                                            if (heroVideo.durationSeconds > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color.Black.copy(alpha = 0.8f),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(8.dp)
                                                ) {
                                                    Text(
                                                        text = com.example.util.DateUtils.formatDurationSeconds(heroVideo.durationSeconds),
                                                        fontSize = 11.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Video Details Footer
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = heroVideo.title,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val heroViews = remember(heroVideo.viewCount, heroVideo.formattedViews) {
                                                    com.example.util.DateUtils.formatViews(heroVideo.viewCount, heroVideo.formattedViews)
                                                }
                                                val heroTimeAgo = remember(heroVideo.uploadDate) {
                                                    com.example.util.DateUtils.formatRelativeTime(heroVideo.uploadDate)
                                                }
                                                val heroMetadataLine = remember(heroVideo.uploaderName, heroViews, heroTimeAgo) {
                                                    com.example.util.DateUtils.buildYouTubeMetadataLine(
                                                        channelName = heroVideo.uploaderName,
                                                        formattedViews = heroViews,
                                                        timeAgo = heroTimeAgo
                                                    )
                                                }
                                                Text(
                                                    text = heroMetadataLine.ifBlank { heroVideo.uploaderName },
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = { onSelectVideo(heroVideo) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Play Original Video Now", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Related Videos Across All Sources",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (activeSanitized != null && activeSanitized?.wasCleaned == true) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (activeSanitized?.didYouMean != null) "Did you mean: " else "Smart AI Search: ",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = activeSanitized?.cleanQuery ?: "",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = activeSanitized?.noiseDescription ?: "Cleaned technical noise & tags",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (activeSanitized?.originalQuery != activeSanitized?.cleanQuery) {
                                            TextButton(
                                                onClick = {
                                                    val raw = activeSanitized?.originalQuery ?: ""
                                                    if (raw.isNotBlank()) viewModel.performSearch(raw)
                                                }
                                            ) {
                                                Text("Search Raw", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Results for \"${activeSanitized?.cleanQuery ?: searchQuery}\"",
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
                    }
                    items(
                        items = filteredResults,
                        key = { "${it.providerId ?: ""}_${it.id}" },
                        contentType = { "video_card" }
                    ) { video ->
                        VideoCard(
                            video = video,
                            showProviderBadge = showThumbnailTags,
                            onClick = { onSelectVideo(video) },
                            onNotInterested = { v -> viewModel.markNotInterested(v) },
                            onPlayNextInQueue = { v -> viewModel.playNextInQueue(v) },
                            onAddToQueue = { v -> viewModel.addToQueue(v) },
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
                items(
                    items = effectiveSuggestions,
                    key = { "${it.query}_${it.providerBadge}" }
                ) { suggestion ->
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
            // EMPTY SEARCH QUERY -> CLEAN MODERN RECENT & TRENDING SEARCHES CARDS (Matching user reference)
            val topics = if (trendingTopics.isNotEmpty()) trendingTopics else trendingFallbacks
            val chunkedTopics = remember(topics) { topics.chunked(2) }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. RECENT SEARCHES CARD (Shown only if user has recent searches)
                if (recentSearches.isNotEmpty()) {
                    item(key = "recent_searches_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                // Header: Clock Icon + Title + "CLEAR ALL"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Recent Searches",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    TextButton(
                                        onClick = { viewModel.clearAllRecentSearches() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "CLEAR ALL",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Recent Search Pills
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    recentSearches.forEach { queryText ->
                                        RecentSearchPill(
                                            text = queryText,
                                            onClick = {
                                                focusManager.clearFocus()
                                                viewModel.updateSearchQuery(queryText)
                                                viewModel.performSearch(queryText)
                                            },
                                            onDelete = {
                                                viewModel.removeRecentSearch(queryText)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. TRENDING SEARCHES CARD
                item(key = "trending_searches_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 18.dp)
                        ) {
                            Text(
                                text = "Trending Searches",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 2-Column Grid Layout for Trending items
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                chunkedTopics.forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Left Item
                                        TrendingSearchItem(
                                            title = pair[0],
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                focusManager.clearFocus()
                                                viewModel.updateSearchQuery(pair[0])
                                                viewModel.performSearch(pair[0])
                                            }
                                        )

                                        // Right Item
                                        if (pair.size > 1) {
                                            TrendingSearchItem(
                                                title = pair[1],
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    viewModel.updateSearchQuery(pair[1])
                                                    viewModel.performSearch(pair[1])
                                                }
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
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
        "tencent" -> Triple("Tencent Video", Icons.Default.Tv, Color(0xFF0052D9))
        "youtube" -> Triple("YouTube", Icons.Default.PlayArrow, Color(0xFFFF0000))
        "dailymotion" -> Triple("Dailymotion", Icons.Default.Movie, Color(0xFF0066DC))
        "bilibili" -> Triple("Bilibili", Icons.Default.Tv, Color(0xFF00A1D6))
        "sonyliv" -> Triple("SonyLIV", Icons.Default.Tv, Color(0xFF003087))
        "hotstar" -> Triple("Hotstar", Icons.Default.Star, Color(0xFF001435))
        "amazonminitv", "minitv" -> Triple("miniTV", Icons.Default.Tv, Color(0xFFFF9900))
        "discoveryplus", "discovery" -> Triple("Discovery+", Icons.Default.VideoLibrary, Color(0xFF00838F))
        "disney", "disneyplus" -> Triple("Disney+", Icons.Default.Star, Color(0xFF113CCF))
        "hbo", "hbomax", "max" -> Triple("HBO Max", Icons.Default.Movie, Color(0xFF5822B4))
        "curiositystream", "curiosity" -> Triple("CuriosityStream", Icons.Default.VideoLibrary, Color(0xFFE50914))
        "googledrive", "gdrive", "google_drive" -> Triple("Drive", Icons.Default.Cloud, Color(0xFF0F9D58))
        "imdb" -> Triple("IMDb", Icons.Default.Movie, Color(0xFFE4BB24))
        "mxplayer" -> Triple("MX Player", Icons.Default.PlayArrow, Color(0xFF1565C0))
        "popcorntv", "popcorn" -> Triple("PopcornTV", Icons.Default.Movie, Color(0xFFD32F2F))
        "decryptor" -> Triple("Decryptor", Icons.Default.Cloud, Color(0xFF00E5FF))
        "vidsrc" -> Triple("VidSrc", Icons.Default.PlayArrow, Color(0xFFFF9100))
        "jikan_anime", "anime" -> Triple("Anime", Icons.Default.Star, Color(0xFF7B1FA2))
        "archive_org", "internet_archive" -> Triple("Archive.org", Icons.Default.Folder, Color(0xFF5D4037))
        "mega" -> Triple("Mega", Icons.Default.Cloud, Color(0xFFD32F2F))
        "telegram" -> Triple("Telegram", Icons.Default.Send, Color(0xFF0288D1))
        "direct_mp4", "direct_hls" -> Triple("Direct Video", Icons.Default.VideoLibrary, Color(0xFF00796B))
        "rss_video", "json" -> Triple("Feeds", Icons.Default.RssFeed, Color(0xFFF57C00))
        "xnxx" -> Triple("XNXX", Icons.Default.Explicit, Color(0xFF00B0FF))
        "hellporno" -> Triple("HellPorno", Icons.Default.Explicit, Color(0xFFFF1744))
        "stripchat" -> Triple("Stripchat", Icons.Default.VideoLibrary, Color(0xFFFF3D00))
        "chaturbate" -> Triple("Chaturbate", Icons.Default.VideoLibrary, Color(0xFFFF6D00))
        "pornhub" -> Triple("Pornhub", Icons.Default.Explicit, Color(0xFFFF9900))
        "xvideos" -> Triple("XVideos", Icons.Default.Explicit, Color(0xFFD32F2F))
        "txxx" -> Triple("TXXX", Icons.Default.Explicit, Color(0xFFFF8F00))
        "eporner" -> Triple("Eporner", Icons.Default.Explicit, Color(0xFFC2185B))
        "sextb" -> Triple("SEXТB", Icons.Default.Explicit, Color(0xFFE91E63))
        "supjav" -> Triple("SupJav", Icons.Default.Explicit, Color(0xFFFF4081))
        "123av" -> Triple("123AV", Icons.Default.Explicit, Color(0xFF9C27B0))
        "javtiful" -> Triple("Javtiful", Icons.Default.Explicit, Color(0xFF673AB7))
        "jav_all" -> Triple("All JAV", Icons.Default.Explicit, Color(0xFFE91E63))
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceBrandLogo(
                providerId = data.id,
                size = 18.dp
            )
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

@Composable
private fun RecentSearchPill(
    text: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClick = onDelete
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun TrendingSearchItem(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


