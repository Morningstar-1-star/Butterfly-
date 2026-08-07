package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.extractor.YouTubeExtractorHelper
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.ErrorDiagnosticCard
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoDetailsSection
import com.example.ui.player.YouTubePlayerView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeVideoId by viewModel.activeVideoId.collectAsState()
    val extractionResult by viewModel.extractionResult.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val selectedOption by viewModel.selectedStreamOption.collectAsState()
    val selectedCaption by viewModel.selectedCaptionOption.collectAsState()
    val availableProviders by viewModel.availableProviders.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val watchLaterList by viewModel.watchLaterList.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()

    val currentStreamData = (extractionResult as? YouTubeExtractorHelper.ExtractionResult.Success)?.streamData
    val providerId = currentStreamData?.providerId
    val providerName = availableProviders.firstOrNull { it.id == providerId }?.name ?: providerId ?: "Video Player"

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }
    var showSaveToPlaylistSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

    val currentVideoItem = remember(currentStreamData, activeVideoId) {
        currentStreamData?.let { data ->
            VideoItem(
                id = activeVideoId ?: "playing_video",
                title = data.title,
                uploaderName = data.channelName,
                thumbnailUrl = data.thumbnailUrl,
                providerId = providerId ?: "youtube"
            )
        }
    }

    val isSavedInWatchLater = currentVideoItem != null && watchLaterList.any { it.id == currentVideoItem.id }

    val listState = rememberLazyListState()
    val isScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 420
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentStreamData?.title ?: "Now Playing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (providerName.isNotEmpty()) {
                            Text(
                                text = "Source: $providerName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize to Mini Player",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { viewModel.closeVideo() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Video",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // STICKY PLAYER AREA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isExtracting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading video stream from $providerName...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                } else if (extractionResult != null) {
                    when (val res = extractionResult) {
                        is YouTubeExtractorHelper.ExtractionResult.Success -> {
                            YouTubePlayerView(
                                streamOption = selectedOption,
                                hlsUrl = res.streamData.hlsUrl,
                                captionOption = selectedCaption,
                                embedUrl = res.streamData.embedUrl,
                                providerId = providerId,
                                isPlaying = isPlaying
                            )
                        }
                        is YouTubeExtractorHelper.ExtractionResult.Error -> {
                            ErrorDiagnosticCard(
                                errorDetails = res.errorDetails,
                                onRetry = {
                                    activeVideoId?.let { id -> viewModel.playVideo(id, providerId) }
                                },
                                onOpenPoTokenConfig = {}
                            )
                        }
                        null -> {}
                    }
                }
            }

            // CONTAINER FOR SCROLLABLE CONTENT & STICKY LIQUID GLASS FLOATING ACTION TOOLBAR
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Video Details Section
                    if (currentStreamData != null) {
                        item {
                            VideoDetailsSection(
                                streamData = currentStreamData,
                                selectedOption = selectedOption,
                                selectedCaption = selectedCaption,
                                onSelectOption = { viewModel.selectStreamOption(it) },
                                onSelectCaption = { viewModel.selectCaptionOption(it) },
                                onTagClick = { tag ->
                                    viewModel.updateSearchQuery(tag)
                                    viewModel.performSearch(tag)
                                    onBackClick()
                                },
                                isLiked = isLiked,
                                isDisliked = isDisliked,
                                isSaved = isSavedInWatchLater,
                                onLikeClick = {
                                    isLiked = !isLiked
                                    if (isLiked) isDisliked = false
                                },
                                onDislikeClick = {
                                    isDisliked = !isDisliked
                                    if (isDisliked) isLiked = false
                                },
                                onSaveClick = {
                                    currentVideoItem?.let { video ->
                                        if (isSavedInWatchLater) {
                                            viewModel.removeFromWatchLater(video)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Removed from Watch Later")
                                            }
                                        } else {
                                            viewModel.addToWatchLater(video)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Saved to Watch Later")
                                            }
                                        }
                                    }
                                },
                                onSaveLongClick = { showSaveToPlaylistSheet = true },
                                onShareClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Video link copied to clipboard")
                                    }
                                },
                                onCommentsClick = { showCommentsSheet = true }
                            )
                        }
                    }

                    // Divider
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    // Up Next Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Up Next & Recommendations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            AssistChip(
                                onClick = { },
                                label = { Text("Autoplay On", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }

                    // Recommended Videos List
                    val recommendations = currentStreamData?.relatedVideos ?: emptyList()
                    if (recommendations.isNotEmpty()) {
                        items(recommendations) { video ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                VideoCard(
                                    video = video,
                                    onClick = {
                                        viewModel.playVideo(video.id, video.providerId)
                                    }
                                )
                            }
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Loading recommendations...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // STICKY LIQUID GLASS FLOATING ACTION CAPSULES AT BOTTOM (ONLY APPEARS ON SCROLLING DOWN)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isScrolledPastHeader,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // CAPSULE 1 (LEFT): LIKE, DISLIKE, COMMENTS
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = 12.dp,
                            modifier = Modifier
                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                                .border(width = 1.dp, color = Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(28.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // LIKE
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            isLiked = !isLiked
                                            if (isLiked) isDisliked = false
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                        contentDescription = "Like",
                                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isLiked) "38K" else "37K",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // DISLIKE
                                IconButton(
                                    onClick = {
                                        isDisliked = !isDisliked
                                        if (isDisliked) isLiked = false
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                        contentDescription = "Dislike",
                                        tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                // COMMENTS
                                IconButton(
                                    onClick = { showCommentsSheet = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = "Comments",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        // CAPSULE 2 (RIGHT): SAVE / WATCH LATER, MORE / SHARE
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = 12.dp,
                            modifier = Modifier
                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                                .border(width = 1.dp, color = Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(28.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // SAVE (TAP FOR WATCH LATER, LONG PRESS FOR PLAYLISTS)
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            onClick = {
                                                currentVideoItem?.let { video ->
                                                    if (isSavedInWatchLater) {
                                                        viewModel.removeFromWatchLater(video)
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Removed from Watch Later")
                                                        }
                                                    } else {
                                                        viewModel.addToWatchLater(video)
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Saved to Watch Later")
                                                        }
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                showSaveToPlaylistSheet = true
                                            }
                                        )
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSavedInWatchLater) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Save video",
                                        tint = if (isSavedInWatchLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // MORE OPTIONS / SHARE
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Video link copied to clipboard")
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreHoriz,
                                        contentDescription = "More",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MODAL BOTTOM SHEET: SAVE TO PLAYLIST
    if (showSaveToPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSaveToPlaylistSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Save video to...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { showCreatePlaylistDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New playlist", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Watch Later Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentVideoItem?.let { video ->
                                if (isSavedInWatchLater) {
                                    viewModel.removeFromWatchLater(video)
                                } else {
                                    viewModel.addToWatchLater(video)
                                }
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSavedInWatchLater,
                        onCheckedChange = { checked ->
                            currentVideoItem?.let { video ->
                                if (checked) viewModel.addToWatchLater(video)
                                else viewModel.removeFromWatchLater(video)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Watch later",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Custom User Playlists
                userPlaylists.forEach { playlist ->
                    val isVideoInPlaylist = currentVideoItem != null && playlist.videos.any { it.id == currentVideoItem.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentVideoItem?.let { video ->
                                    if (isVideoInPlaylist) {
                                        viewModel.removeFromPlaylist(playlist.id, video)
                                    } else {
                                        viewModel.addToPlaylist(playlist.id, video)
                                    }
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isVideoInPlaylist,
                            onCheckedChange = { checked ->
                                currentVideoItem?.let { video ->
                                    if (checked) viewModel.addToPlaylist(playlist.id, video)
                                    else viewModel.removeFromPlaylist(playlist.id, video)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = playlist.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showSaveToPlaylistSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }

    // CREATE PLAYLIST DIALOG
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text("Playlist Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistTitle.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistTitle)
                            val created = viewModel.userPlaylists.value.lastOrNull()
                            if (created != null && currentVideoItem != null) {
                                viewModel.addToPlaylist(created.id, currentVideoItem)
                            }
                            newPlaylistTitle = ""
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // COMMENTS BOTTOM SHEET
    if (showCommentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCommentsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Comments (342)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                listOf(
                    "Alex Smith" to "Awesome video! The liquid glass design looks incredible 🔥",
                    "Sarah Tech" to "So smooth! Love the floating toolbar at the bottom.",
                    "Dev Community" to "Super clean UX and fast streaming options."
                ).forEach { (user, comment) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.take(1),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = user,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = comment,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showCommentsSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Comments")
                }
            }
        }
    }
}
