package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.model.SearchSuggestionItem
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.VideoCard

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
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TOP SEARCH BAR (Exact YouTube Layout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    .height(50.dp)
                    .focusRequester(focusRequester)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Mic Icon Button
            IconButton(
                onClick = { /* Voice Search action */ },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

        // CONTENT AREA
        if (isSearching) {
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
            // SEARCH RESULTS VIEW
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Results for \"$searchQuery\"",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                val filteredResults = searchResults.filter { adultContentEnabled || !viewModel.isAdultVideoItem(it) }
                items(filteredResults, key = { (it.providerId ?: "") + "_" + it.id }) { video ->
                    VideoCard(
                        video = video,
                        onClick = { onSelectVideo(video) }
                    )
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
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = thumbnailUrl,
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
