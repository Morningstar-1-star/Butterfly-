package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppScreen
import com.example.model.UserPlaylist
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: MainViewModel,
    onSelectVideo: (VideoItem) -> Unit,
    onToggleSearch: () -> Unit,
    showShortsFeed: Boolean,
    onToggleShortsFeed: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()
    val watchLaterList by viewModel.watchLaterList.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    var isViewingWatchLaterDetail by remember { mutableStateOf(false) }
    var isViewingHistoryDetail by remember { mutableStateOf(false) }
    var isViewingDownloadsDetail by remember { mutableStateOf(false) }

    // Detail View: Full Watch History Page
    if (isViewingHistoryDetail) {
        HistoryDetailScreen(
            videos = watchHistory,
            watchProgressMap = watchProgressMap,
            onPlayVideo = { onSelectVideo(it) },
            onRemoveFromHistory = { viewModel.removeFromWatchHistory(it) },
            onSaveToWatchLater = { viewModel.addToWatchLater(it) },
            onClearAll = { viewModel.clearWatchHistory() },
            onBackClick = { isViewingHistoryDetail = false }
        )
        return
    }

    // Detail View: Watch Later Page
    if (isViewingWatchLaterDetail) {
        PlaylistDetailScreen(
            title = "Watch later",
            authorName = userProfile.name,
            isWatchLater = true,
            videos = watchLaterList,
            onPlayVideo = { onSelectVideo(it) },
            onPlayAll = {
                if (watchLaterList.isNotEmpty()) {
                    onSelectVideo(watchLaterList.first())
                    watchLaterList.drop(1).forEach { viewModel.addToQueue(it) }
                }
            },
            onShuffle = {
                if (watchLaterList.isNotEmpty()) {
                    val shuffled = watchLaterList.shuffled()
                    onSelectVideo(shuffled.first())
                    shuffled.drop(1).forEach { viewModel.addToQueue(it) }
                }
            },
            onRemoveVideo = { viewModel.removeFromWatchLater(it) },
            onBackClick = { isViewingWatchLaterDetail = false }
        )
        return
    }

    // Detail View: Custom User Playlist Page
    if (selectedPlaylist != null) {
        val activePl = userPlaylists.firstOrNull { it.id == selectedPlaylist?.id } ?: selectedPlaylist!!
        PlaylistDetailScreen(
            title = activePl.title,
            authorName = userProfile.name,
            isWatchLater = false,
            videos = activePl.videos,
            onPlayVideo = { onSelectVideo(it) },
            onPlayAll = {
                if (activePl.videos.isNotEmpty()) {
                    onSelectVideo(activePl.videos.first())
                    activePl.videos.drop(1).forEach { viewModel.addToQueue(it) }
                }
            },
            onShuffle = {
                if (activePl.videos.isNotEmpty()) {
                    val shuffled = activePl.videos.shuffled()
                    onSelectVideo(shuffled.first())
                    shuffled.drop(1).forEach { viewModel.addToQueue(it) }
                }
            },
            onRemoveVideo = { video -> viewModel.removeFromPlaylist(activePl.id, video) },
            onBackClick = { selectedPlaylist = null }
        )
        return
    }

    // Detail View: Downloads Page
    if (isViewingDownloadsDetail) {
        DownloadsDetailScreen(
            onBackClick = { isViewingDownloadsDetail = false },
            onPlayVideo = { onSelectVideo(it) }
        )
        return
    }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editHandle by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    var editAvatarUrl by remember { mutableStateOf("") }
    var editAvatarPreset by remember { mutableStateOf("purple") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(onClick = { onToggleSearch() }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.navigateToScreen(AppScreen.SETTINGS) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. PROFILE HEADER CARD (YouTube Style)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(getAvatarBrush(userProfile.avatarPreset)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!userProfile.avatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = userProfile.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = userProfile.name.take(1).uppercase(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = userProfile.handle,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(text = "•", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "View channel",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            editName = userProfile.name
                            editHandle = userProfile.handle
                            editBio = userProfile.bio
                            editAvatarUrl = userProfile.avatarUrl ?: ""
                            editAvatarPreset = userProfile.avatarPreset
                            showEditProfileDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. HISTORY SECTION (Horizontal Recently Played Video Items)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isViewingHistoryDetail = true }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "History",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "View History",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        TextButton(onClick = { isViewingHistoryDetail = true }) {
                            Text("View all", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (watchHistory.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Outlined.History,
                            title = "No watched videos yet",
                            description = "Videos you watch will appear here so you can easily resume or re-watch them."
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(watchHistory) { video ->
                                HistoryVideoCard(
                                    video = video,
                                    progressFraction = watchProgressMap[video.id] ?: 0.2f,
                                    onPlay = { onSelectVideo(video) },
                                    onRemove = { viewModel.removeFromWatchHistory(video) },
                                    onSaveWatchLater = { viewModel.addToWatchLater(video) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. PLAYLISTS SECTION (Custom Playlists + Watch Later)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Playlists",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        IconButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Playlist",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Watch Later Card (Always First)
                        item {
                            WatchLaterPlaylistTile(
                                itemCount = watchLaterList.size,
                                onClick = { isViewingWatchLaterDetail = true }
                            )
                        }

                        // User Custom Playlists
                        items(userPlaylists) { playlist ->
                            UserPlaylistTile(
                                playlist = playlist,
                                onClick = { selectedPlaylist = playlist }
                            )
                        }
                    }
                }
            }

            // 4. DOWNLOADS SECTION
            item {
                AccountNavigationTile(
                    icon = Icons.Outlined.FileDownload,
                    title = "Downloads",
                    subtitle = "0 videos downloaded • Available offline",
                    onClick = { isViewingDownloadsDetail = true }
                )
            }

            // 5. MOVIES & TV SECTION (Saved Library)
            item {
                AccountNavigationTile(
                    icon = Icons.Outlined.Movie,
                    title = "Your movies & TV",
                    subtitle = "Saved movies, series, vault & favorites",
                    onClick = { viewModel.navigateToScreen(AppScreen.LIBRARY) }
                )
            }

            // 6. YOUR VIDEOS / QUEUE SECTION
            item {
                AccountNavigationTile(
                    icon = Icons.Outlined.VideoLibrary,
                    title = "Your videos & queue (${playbackQueue.size})",
                    subtitle = "Playback queue and queued playlist items",
                    onClick = {
                        if (playbackQueue.isNotEmpty()) {
                            onSelectVideo(playbackQueue.first())
                        }
                    }
                )
            }

            // 7. PREFERENCES & EXTENSIONS
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        AccountMenuItem(
                            icon = Icons.Outlined.Extension,
                            title = "Content Providers & Extensions",
                            subtitle = "Manage active extensions & sources",
                            onClick = { viewModel.navigateToScreen(AppScreen.PROVIDERS) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleShortsFeed(!showShortsFeed) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Slideshow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Show Shorts Carousel",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Display Shorts section on Home feed",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = showShortsFeed,
                                onCheckedChange = { onToggleShortsFeed(it) }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        AccountMenuItem(
                            icon = Icons.Outlined.Settings,
                            title = "App Settings",
                            subtitle = "Extractor settings, adult filters & playback",
                            onClick = { viewModel.navigateToScreen(AppScreen.SETTINGS) }
                        )
                    }
                }
            }
        }
    }

    // CREATE PLAYLIST DIALOG
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text("Playlist Title") },
                    placeholder = { Text("e.g. Favorite Music") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistTitle.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistTitle.trim())
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

    // EDIT PROFILE & ACCOUNT OPTIONS DIALOG
    if (showEditProfileDialog) {
        val likedVideoIds by viewModel.likedVideoIds.collectAsState()
        val currentTimeSlot = remember { com.example.engine.RecommendationPipelineEngine.getCurrentTimeSlot() }

        var emailInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var authErrorMessage by remember { mutableStateOf<String?>(null) }
        var isRegisteringMode by remember { mutableStateOf(false) }
        var isSubmittingAuth by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ManageAccounts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Account & Profile Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SECTION 1: LOCAL PROFILE DETAILS
                    Text(
                        text = "Local Profile Details",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g. BeaT BoX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editHandle,
                        onValueChange = { editHandle = it },
                        label = { Text("Profile Tag / Handle") },
                        placeholder = { Text("e.g. @beatbox5789") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Bio") },
                        placeholder = { Text("Write something about yourself...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Avatar Theme",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val presets = listOf("purple", "pink", "blue", "emerald", "gold")
                        presets.forEach { presetKey ->
                            val isSelected = editAvatarPreset == presetKey
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(getAvatarBrush(presetKey))
                                    .clickable { editAvatarPreset = presetKey },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // SECTION 3: SMART RECOMMENDATION ENGINE
                    Text(
                        text = "Smart Recommendation Engine",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("Slot: $currentTimeSlot", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("Likes Active: ${likedVideoIds.size}", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUserProfile(
                            name = editName,
                            handle = editHandle,
                            bio = editBio,
                            avatarUrl = editAvatarUrl,
                            avatarPreset = editAvatarPreset
                        )
                        showEditProfileDialog = false
                    }
                ) {
                    Text("Save & Close")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ---------------------- COMPOSABLE HELPERS ----------------------

@Composable
fun HistoryVideoCard(
    video: VideoItem,
    progressFraction: Float,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onSaveWatchLater: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onPlay() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Duration Badge
            if (video.formattedDuration.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Red Progress Bar at Bottom of Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction.coerceIn(0.05f, 1f))
                    .height(3.dp)
                    .background(Color.Red)
                    .align(Alignment.BottomStart)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!video.uploaderName.isNullOrEmpty()) {
                    Text(
                        text = video.uploaderName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        onClick = {
                            showMenu = false
                            onPlay()
                        },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to Watch Later") },
                        onClick = {
                            showMenu = false
                            onSaveWatchLater()
                        },
                        leadingIcon = { Icon(Icons.Outlined.WatchLater, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from history") },
                        onClick = {
                            showMenu = false
                            onRemove()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@Composable
fun WatchLaterPlaylistTile(
    itemCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WatchLater,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "$itemCount",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Watch later",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "Private • Playlist",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun UserPlaylistTile(
    playlist: UserPlaylist,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.videos.isNotEmpty() && !playlist.videos.first().thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = playlist.videos.first().thumbnailUrl,
                        contentDescription = playlist.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "${playlist.videos.size}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = playlist.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Private • Playlist",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AccountNavigationTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun AccountMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    videos: List<VideoItem>,
    watchProgressMap: Map<String, Float>,
    onPlayVideo: (VideoItem) -> Unit,
    onRemoveFromHistory: (VideoItem) -> Unit,
    onSaveToWatchLater: (VideoItem) -> Unit,
    onClearAll: () -> Unit,
    onBackClick: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch History (${videos.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (videos.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No watch history items found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(videos) { video ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            VideoCard(
                                video = video,
                                onClick = { onPlayVideo(video) },
                                watchProgressFraction = watchProgressMap[video.id] ?: 0.2f,
                                onSaveToWatchLater = { onSaveToWatchLater(video) }
                            )
                        }
                        IconButton(onClick = { onRemoveFromHistory(video) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove from history",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Watch History?") },
            text = { Text("This will remove all videos from your watch history.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onClearAll()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsDetailScreen(
    onBackClick: () -> Unit,
    onPlayVideo: (VideoItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "No downloaded videos yet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Downloaded videos will appear here and can be played offline.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getAvatarBrush(preset: String): androidx.compose.ui.graphics.Brush {
    return when (preset.lowercase()) {
        "purple" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)))
        "pink" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)))
        "blue" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2193b0), Color(0xFF6dd5ed)))
        "emerald" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF11998e), Color(0xFF38ef7d)))
        "gold" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFF2994A), Color(0xFFF2C94C)))
        else -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)))
    }
}
