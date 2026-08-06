package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.components.ErrorDiagnosticCard
import com.example.ui.components.FeedErrorDiagnosticCard
import com.example.ui.components.PoTokenDialog
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoDetailsSection
import com.example.ui.player.YouTubePlayerView

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
    val selectedOption by viewModel.selectedStreamOption.collectAsState()
    val selectedCaption by viewModel.selectedCaptionOption.collectAsState()

    var showPoTokenDialog by remember { mutableStateOf(false) }
    var activeCategory by remember { mutableStateOf("Popular") }
    val focusManager = LocalFocusManager.current

    val categories = listOf("Popular", "Music", "Gaming", "News", "Technology", "Nature")
    val activeProviderName = availableProviders.firstOrNull { it.id == activeProviderId }?.name ?: activeProviderId

    if (showPoTokenDialog) {
        PoTokenDialog(
            onDismiss = { showPoTokenDialog = false },
            onApplyToken = {
                activeVideoId?.let { id -> viewModel.playVideo(id) }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = (currentScreen == AppScreen.HOME),
                    onClick = { viewModel.navigateToScreen(AppScreen.HOME) },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Videos") },
                    label = { Text("Videos") }
                )
                NavigationBarItem(
                    selected = (currentScreen == AppScreen.PROVIDERS),
                    onClick = { viewModel.navigateToScreen(AppScreen.PROVIDERS) },
                    icon = { Icon(Icons.Default.Extension, contentDescription = "Providers") },
                    label = { Text("Providers") }
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Butterfly",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Butterfly",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = { viewModel.navigateToScreen(AppScreen.PROVIDERS) },
                            label = { Text(activeProviderName, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.navigateToScreen(AppScreen.PROVIDERS) }) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "Providers Screen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showPoTokenDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "PoToken Architecture",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.PROVIDERS -> {
                    ProvidersScreen(viewModel = viewModel)
                }
                AppScreen.HOME -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search Input Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text("Search $activeProviderName or paste video URL...", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            viewModel.updateSearchQuery("")
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear"
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
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Quick Category Chips
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { category ->
                                FilterChip(
                                    selected = activeCategory == category,
                                    onClick = {
                                        activeCategory = category
                                        if (category == "Popular") {
                                            viewModel.loadTrending()
                                        } else {
                                            viewModel.updateSearchQuery(category)
                                            viewModel.performSearch(category)
                                        }
                                    },
                                    label = { Text(category, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }

                        // Main Content Area
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            // ACTIVE PLAYER CONTAINER
                            if (isExtracting) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Fetching streams from $activeProviderName...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.LightGray
                                            )
                                        }
                                    }
                                }
                            } else if (extractionResult != null) {
                                when (val res = extractionResult) {
                                    is YouTubeExtractorHelper.ExtractionResult.Success -> {
                                        item {
                                            YouTubePlayerView(
                                                streamOption = selectedOption,
                                                hlsUrl = res.streamData.hlsUrl,
                                                captionOption = selectedCaption
                                            )
                                        }
                                        item {
                                            VideoDetailsSection(
                                                streamData = res.streamData,
                                                selectedOption = selectedOption,
                                                selectedCaption = selectedCaption,
                                                onSelectOption = { viewModel.selectStreamOption(it) },
                                                onSelectCaption = { viewModel.selectCaptionOption(it) }
                                            )
                                        }
                                    }
                                    is YouTubeExtractorHelper.ExtractionResult.Error -> {
                                        item {
                                            ErrorDiagnosticCard(
                                                errorDetails = res.errorDetails,
                                                onRetry = {
                                                    activeVideoId?.let { id -> viewModel.playVideo(id) }
                                                },
                                                onOpenPoTokenConfig = {
                                                    showPoTokenDialog = true
                                                }
                                            )
                                        }
                                    }
                                    null -> {}
                                }
                            }

                            // FEED HEADER
                            item {
                                Text(
                                    text = if (searchResults.isNotEmpty()) "Search Results ($activeProviderName)" else "$activeProviderName Content",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }

                            // FEED ERROR DIAGNOSTIC DISPLAY
                            if (feedError != null) {
                                item {
                                    FeedErrorDiagnosticCard(
                                        errorDetails = feedError!!,
                                        onRetry = {
                                            if (activeCategory == "Popular" && searchQuery.isBlank()) {
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
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else {
                                val feedList = if (searchResults.isNotEmpty()) searchResults else trendingVideos
                                if (feedList.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No videos found for $activeProviderName. Try searching or selecting another provider.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    items(feedList, key = { it.id + it.title }) { video ->
                                        VideoCard(
                                            video = video,
                                            onClick = {
                                                viewModel.playVideo(video.id)
                                            },
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
