package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import com.example.db.OfflineDownloadEntity
import com.example.model.AppScreen
import com.example.model.UserPlaylist
import com.example.model.VideoItem
import com.example.ui.MainViewModel
import com.example.ui.components.AvatarCustomizerSheet
import com.example.ui.components.BuiltinAvatarPresets
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: MainViewModel,
    onSelectVideo: (VideoItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMoviesAndTv: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 90.dp,
    bottomPadding: Dp = 100.dp
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val rawWatchHistory by viewModel.watchHistory.collectAsState()
    val rawWatchLaterList by viewModel.watchLaterList.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val rawLikedVideos by viewModel.likedVideos.collectAsState()
    val downloads by viewModel.offlineDownloads.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()
    val showThumbnailTags by viewModel.showThumbnailTags.collectAsState()

    val watchHistory = remember(rawWatchHistory, adultContentEnabled) {
        if (adultContentEnabled) rawWatchHistory else rawWatchHistory.filter { !viewModel.isAdultVideoItem(it) }
    }
    val watchLaterList = remember(rawWatchLaterList, adultContentEnabled) {
        if (adultContentEnabled) rawWatchLaterList else rawWatchLaterList.filter { !viewModel.isAdultVideoItem(it) }
    }
    val likedVideos = remember(rawLikedVideos, adultContentEnabled) {
        if (adultContentEnabled) rawLikedVideos else rawLikedVideos.filter { !viewModel.isAdultVideoItem(it) }
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedPlaylistForDetail by remember { mutableStateOf<UserPlaylist?>(null) }
    var showWatchLaterSheet by remember { mutableStateOf(false) }
    var showLikedVideosSheet by remember { mutableStateOf(false) }
    var showDownloadsSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showMoviesAndTvSheet by remember { mutableStateOf(false) }
    var showBadgesSheet by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    var showAccountsDialog by remember { mutableStateOf(false) }

    val savedMoviesAndTv = remember(watchLaterList) {
        watchLaterList.filter { 
            it.id.startsWith("movie_") || it.id.startsWith("tv_") || it.id.startsWith("anime_") ||
            it.providerId in listOf("tmdb", "anilist", "jikan", "imdb") ||
            it.uploaderName.contains("Movie", ignoreCase = true) ||
            it.uploaderName.contains("Anime", ignoreCase = true) ||
            it.uploaderName.contains("TV Series", ignoreCase = true)
        }
    }

    var videoForPlaylistPicker by remember { mutableStateOf<VideoItem?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topPadding + 16.dp,
                bottom = bottomPadding + 24.dp
            )
        ) {
            // 1. USER PROFILE HEADER WITH INLINE SETTINGS BUTTON
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Info & Avatar (Clickable to edit profile or change 3D avatar)
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Avatar with Neon Ring & 3D Cartoon Model / Custom Logo
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clickable { showAvatarPickerSheet = true }
                        ) {
                            val activeModel = BuiltinAvatarPresets.models.find { it.id == userProfile.avatarPreset }
                            val ringGradient = if (activeModel != null) {
                                Brush.linearGradient(activeModel.gradientColors)
                            } else {
                                Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF007F), Color(0xFFFFD600)))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(ringGradient)
                                    .padding(2.5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF12121A)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!userProfile.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userProfile.avatarUrl,
                                        contentDescription = "User Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (activeModel != null) {
                                    AsyncImage(
                                        model = activeModel.imageUrl,
                                        contentDescription = activeModel.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    // Default Lucifer 3D demon character placeholder
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = "Profile Avatar",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }

                            // Camera / 3D Customizer Badge Overlay
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF00E5FF),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF12121A)),
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PhotoCamera,
                                        contentDescription = "Change 3D Avatar or Upload Logo",
                                        tint = Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showEditProfileDialog = true }
                        ) {
                            Text(
                                text = userProfile.name.ifBlank { "Lucifer" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${userProfile.handle.ifBlank { "@lucifer4982" }} • View channel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = "View Channel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp).padding(start = 2.dp)
                                )
                            }
                        }
                    }

                    // Settings Button aligned right in the same top row
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // 3. HISTORY SECTION
            item {
                SectionHeaderRow(
                    title = "History",
                    onArrowClick = { showHistorySheet = true }
                )
            }

            item {
                if (watchHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Videos you watch will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(watchHistory.take(15), key = { "hist_${it.id}" }) { video ->
                            HistoryVideoCard(
                                video = video,
                                progressFraction = watchProgressMap[video.id] ?: 0.3f,
                                onClick = { onSelectVideo(video) },
                                onPlayNext = { viewModel.playNextInQueue(video) },
                                onAddToQueue = { viewModel.addToQueue(video) },
                                onSaveToWatchLater = { viewModel.addToWatchLater(video) },
                                onSaveToPlaylist = { videoForPlaylistPicker = video },
                                onRemoveFromHistory = { viewModel.removeFromWatchHistory(video) }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // 4. PLAYLISTS SECTION (With "+" Button on Right)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { /* expand playlists */ }
                    ) {
                        Text(
                            text = "Playlists",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Playlists",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(16.dp).padding(start = 4.dp)
                        )
                    }

                    // "+" Button to create new playlist
                    IconButton(onClick = { showCreatePlaylistDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create New Playlist",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // PLAYLISTS CAROUSEL
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Item 1: Watch Later (Queue)
                    item {
                        PlaylistCoverCard(
                            title = "Watch Later",
                            subtitle = "Private • Playlist",
                            videoCount = watchLaterList.size,
                            thumbnailUrl = watchLaterList.firstOrNull()?.thumbnailUrl,
                            icon = Icons.Outlined.WatchLater,
                            badgeColor = Color(0xFF0F4C81),
                            onClick = { viewModel.navigateToScreen(AppScreen.LIBRARY) }
                        )
                    }

                    // Item 2: Liked Videos
                    item {
                        PlaylistCoverCard(
                            title = "Liked videos",
                            subtitle = "Private • Playlist",
                            videoCount = likedVideos.size,
                            thumbnailUrl = likedVideos.firstOrNull()?.thumbnailUrl,
                            icon = Icons.Outlined.ThumbUp,
                            badgeColor = Color(0xFF1E88E5),
                            onClick = { showLikedVideosSheet = true }
                        )
                    }

                    // Item 3: Offline Downloads
                    item {
                        PlaylistCoverCard(
                            title = "Downloads",
                            subtitle = "Offline • Ready",
                            videoCount = downloads.size,
                            thumbnailUrl = downloads.firstOrNull()?.thumbnailUrl,
                            icon = Icons.Outlined.Download,
                            badgeColor = Color(0xFF2E7D32),
                            onClick = { showDownloadsSheet = true }
                        )
                    }

                    // Item 4: Movies & TV (Saved from Explore)
                    item {
                        PlaylistCoverCard(
                            title = "Movies & TV",
                            subtitle = "Saved • Explore",
                            videoCount = savedMoviesAndTv.size,
                            thumbnailUrl = savedMoviesAndTv.firstOrNull()?.thumbnailUrl,
                            icon = Icons.Outlined.Movie,
                            badgeColor = Color(0xFFE50914),
                            onClick = { showMoviesAndTvSheet = true }
                        )
                    }

                    // Item 5+: User Created Playlists
                    items(userPlaylists, key = { it.id }) { playlist ->
                        PlaylistCoverCard(
                            title = playlist.title,
                            subtitle = "Private • Playlist",
                            videoCount = playlist.videos.size,
                            thumbnailUrl = playlist.videos.firstOrNull()?.thumbnailUrl,
                            icon = Icons.Outlined.PlaylistPlay,
                            badgeColor = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { selectedPlaylistForDetail = playlist },
                            onDelete = { viewModel.deletePlaylist(playlist.id) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // 5. ACCOUNT MENU LIST ITEMS (YouTube Standard)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    AccountMenuListItem(
                        icon = Icons.Outlined.SmartDisplay,
                        title = "Your videos",
                        onClick = { viewModel.navigateToScreen(AppScreen.LIBRARY) }
                    )

                    AccountMenuListItem(
                        icon = Icons.Outlined.Download,
                        title = "Downloads",
                        subtitle = if (downloads.isNotEmpty()) "${downloads.size} videos available offline" else "No downloads yet",
                        onClick = { showDownloadsSheet = true }
                    )

                    AccountMenuListItem(
                        icon = Icons.Outlined.Movie,
                        title = "Movies & TV",
                        subtitle = if (savedMoviesAndTv.isNotEmpty()) "${savedMoviesAndTv.size} saved titles" else "Discover movies, series & anime",
                        onClick = { showMoviesAndTvSheet = true }
                    )

                    AccountMenuListItem(
                        icon = Icons.Outlined.ContentCut,
                        title = "Clips",
                        onClick = { /* Clips list */ }
                    )

                    AccountMenuListItem(
                        icon = Icons.Outlined.MilitaryTech,
                        title = "Badges & Achievements",
                        subtitle = "Personality, Hall of Fame, Hall of Shame & Trophies",
                        onClick = { showBadgesSheet = true }
                    )
                }
            }
        }

        // CREATE NEW PLAYLIST DIALOG
        if (showCreatePlaylistDialog) {
            var playlistNameInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                title = {
                    Text(
                        text = "New playlist",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter a title for your playlist:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = playlistNameInput,
                            onValueChange = { playlistNameInput = it },
                            placeholder = { Text("Title (e.g. Favorites, Anime, Music)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (playlistNameInput.isNotBlank()) {
                                viewModel.createPlaylist(playlistNameInput.trim())
                                showCreatePlaylistDialog = false
                            }
                        },
                        enabled = playlistNameInput.isNotBlank()
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

        // EDIT PROFILE DIALOG
        if (showEditProfileDialog) {
            var nameInput by remember { mutableStateOf(userProfile.name) }
            var handleInput by remember { mutableStateOf(userProfile.handle.removePrefix("@")) }
            var bioInput by remember { mutableStateOf(userProfile.bio) }

            AlertDialog(
                onDismissRequest = { showEditProfileDialog = false },
                title = { Text("Channel Details", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Avatar preview & change button
                        Surface(
                            onClick = {
                                showEditProfileDialog = false
                                showAvatarPickerSheet = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1E2C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!userProfile.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = userProfile.avatarUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.AccountCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "3D Cartoon Model / Logo",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tap to pick 3D model or upload from gallery",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = "Edit",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = handleInput,
                            onValueChange = { handleInput = it },
                            label = { Text("Handle") },
                            prefix = { Text("@") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bioInput,
                            onValueChange = { bioInput = it },
                            label = { Text("Bio") },
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateUserProfile(
                            name = nameInput,
                            handle = handleInput,
                            bio = bioInput,
                            avatarUrl = userProfile.avatarUrl,
                            avatarPreset = userProfile.avatarPreset
                        )
                        showEditProfileDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditProfileDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // AVATAR CUSTOMIZER & 3D MODEL / GALLERY PICKER SHEET
        if (showAvatarPickerSheet) {
            AvatarCustomizerSheet(
                userProfile = userProfile,
                onDismiss = { showAvatarPickerSheet = false },
                onAvatarSelected = { avatarUrl, presetId ->
                    viewModel.updateUserProfile(
                        name = userProfile.name,
                        handle = userProfile.handle,
                        bio = userProfile.bio,
                        avatarUrl = avatarUrl,
                        avatarPreset = presetId
                    )
                }
            )
        }

        // ACCOUNTS DIALOG
        if (showAccountsDialog) {
            AlertDialog(
                onDismissRequest = { showAccountsDialog = false },
                title = { Text("Accounts", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile.name.ifBlank { "Lucifer" },
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = userProfile.handle.ifBlank { "@lucifer4982" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAccountsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // ADD TO PLAYLIST PICKER SHEET
        if (videoForPlaylistPicker != null) {
            val video = videoForPlaylistPicker!!
            ModalBottomSheet(
                onDismissRequest = { videoForPlaylistPicker = null },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Save video to...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Watch Later quick option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.addToWatchLater(video)
                                videoForPlaylistPicker = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WatchLater,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Watch Later",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    if (userPlaylists.isEmpty()) {
                        Text(
                            text = "No custom playlists yet. Create one below!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        userPlaylists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addToPlaylist(pl.id, video)
                                        videoForPlaylistPicker = null
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = pl.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${pl.videos.size} videos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            videoForPlaylistPicker = null
                            showCreatePlaylistDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Playlist")
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // FULL WATCH LATER SHEET
        if (showWatchLaterSheet) {
            VideoListBottomSheet(
                title = "Watch Later",
                videos = watchLaterList,
                onDismiss = { showWatchLaterSheet = false },
                onSelectVideo = {
                    showWatchLaterSheet = false
                    onSelectVideo(it)
                },
                onPlayAll = {
                    if (watchLaterList.isNotEmpty()) {
                        showWatchLaterSheet = false
                        viewModel.clearQueue()
                        watchLaterList.drop(1).forEach { viewModel.addToQueue(it) }
                        onSelectVideo(watchLaterList.first())
                    }
                },
                onRemoveVideo = { viewModel.removeFromWatchLater(it) },
                onClearAll = { viewModel.clearWatchLater() }
            )
        }

        // FULL LIKED VIDEOS SHEET
        if (showLikedVideosSheet) {
            VideoListBottomSheet(
                title = "Liked videos",
                videos = likedVideos,
                onDismiss = { showLikedVideosSheet = false },
                onSelectVideo = {
                    showLikedVideosSheet = false
                    onSelectVideo(it)
                },
                onPlayAll = {
                    if (likedVideos.isNotEmpty()) {
                        showLikedVideosSheet = false
                        viewModel.clearQueue()
                        likedVideos.drop(1).forEach { viewModel.addToQueue(it) }
                        onSelectVideo(likedVideos.first())
                    }
                },
                onRemoveVideo = { viewModel.toggleLikeVideo(it.id) },
                onClearAll = null
            )
        }

        // FULL DOWNLOADS SHEET
        if (showDownloadsSheet) {
            DownloadsBottomSheet(
                downloads = downloads,
                onDismiss = { showDownloadsSheet = false },
                onPlayDownload = { dl ->
                    showDownloadsSheet = false
                    viewModel.playOfflineDownload(dl)
                },
                onDeleteDownload = { dl ->
                    viewModel.deleteDownload(dl.videoId, dl.localFilePath)
                }
            )
        }

        // FULL HISTORY SHEET
        if (showHistorySheet) {
            VideoListBottomSheet(
                title = "Watch History",
                videos = watchHistory,
                onDismiss = { showHistorySheet = false },
                onSelectVideo = {
                    showHistorySheet = false
                    onSelectVideo(it)
                },
                onPlayAll = {
                    if (watchHistory.isNotEmpty()) {
                        showHistorySheet = false
                        viewModel.clearQueue()
                        watchHistory.drop(1).forEach { viewModel.addToQueue(it) }
                        onSelectVideo(watchHistory.first())
                    }
                },
                onRemoveVideo = { viewModel.removeFromWatchHistory(it) },
                onClearAll = { viewModel.clearWatchHistory() }
            )
        }

        // CUSTOM PLAYLIST DETAIL SHEET
        if (selectedPlaylistForDetail != null) {
            val pl = selectedPlaylistForDetail!!
            val freshPl = userPlaylists.find { it.id == pl.id } ?: pl
            var showRenameDialog by remember { mutableStateOf(false) }

            VideoListBottomSheet(
                title = freshPl.title,
                videos = freshPl.videos,
                onDismiss = { selectedPlaylistForDetail = null },
                onSelectVideo = {
                    selectedPlaylistForDetail = null
                    onSelectVideo(it)
                },
                onPlayAll = {
                    if (freshPl.videos.isNotEmpty()) {
                        selectedPlaylistForDetail = null
                        viewModel.clearQueue()
                        freshPl.videos.drop(1).forEach { viewModel.addToQueue(it) }
                        onSelectVideo(freshPl.videos.first())
                    }
                },
                onRemoveVideo = { v -> viewModel.removeFromPlaylist(freshPl.id, v) },
                onClearAll = { viewModel.clearPlaylist(freshPl.id) },
                onRename = { showRenameDialog = true },
                onDeletePlaylist = {
                    viewModel.deletePlaylist(freshPl.id)
                    selectedPlaylistForDetail = null
                }
            )

            if (showRenameDialog) {
                var renameInput by remember { mutableStateOf(freshPl.title) }
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("Rename Playlist") },
                    text = {
                        OutlinedTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (renameInput.isNotBlank()) {
                                viewModel.renamePlaylist(freshPl.id, renameInput.trim())
                                showRenameDialog = false
                            }
                        }) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        // FULL MOVIES, TV & ANIME SHEET
        if (showMoviesAndTvSheet) {
            MoviesAndTvBottomSheet(
                savedMedia = savedMoviesAndTv,
                onDismiss = { showMoviesAndTvSheet = false },
                onSelectVideo = { video ->
                    showMoviesAndTvSheet = false
                    onSelectVideo(video)
                },
                onExploreMore = {
                    showMoviesAndTvSheet = false
                    viewModel.navigateToScreen(AppScreen.EXPLORE)
                },
                onRemoveMedia = { video ->
                    viewModel.removeFromWatchLater(video)
                }
            )
        }

        // BADGES & PERSONALITY METER SHEET
        if (showBadgesSheet) {
            com.example.ui.components.PersonalityBadgesSheet(
                viewModel = viewModel,
                onDismiss = { showBadgesSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoviesAndTvBottomSheet(
    savedMedia: List<VideoItem>,
    onDismiss: () -> Unit,
    onSelectVideo: (VideoItem) -> Unit,
    onExploreMore: () -> Unit,
    onRemoveMedia: (VideoItem) -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredList = remember(savedMedia, selectedFilter) {
        when (selectedFilter) {
            "Movies" -> savedMedia.filter { it.id.startsWith("movie_") || it.uploaderName.contains("Movie", ignoreCase = true) }
            "TV Series" -> savedMedia.filter { it.id.startsWith("tv_") || it.uploaderName.contains("TV Series", ignoreCase = true) }
            "Anime" -> savedMedia.filter { it.id.startsWith("anime_") || it.uploaderName.contains("Anime", ignoreCase = true) || it.providerId in listOf("anilist", "jikan") }
            else -> savedMedia
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Saved Movies, TV & Anime",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${savedMedia.size} titles saved from Explore",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onExploreMore,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Explore More", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Filter Chips Row
            val filterOptions = listOf("All", "Movies", "TV Series", "Anime")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { opt ->
                    val isSelected = (selectedFilter == opt)
                    Surface(
                        onClick = { selectedFilter = opt },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(text = opt, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No saved $selectedFilter yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Explore trending movies, popular series and anime in the Explore tab and tap Save to store them here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onExploreMore,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Explore, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Explore Tab")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        Surface(
                            onClick = { onSelectVideo(item) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Poster Thumbnail
                                Box(
                                    modifier = Modifier
                                        .width(70.dp)
                                        .height(98.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = item.uploaderName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    if (!item.description.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onRemoveMedia(item) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.BookmarkRemove,
                                        contentDescription = "Remove from Saved",
                                        tint = MaterialTheme.colorScheme.error
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

@Composable
private fun SectionHeaderRow(
    title: String,
    onArrowClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onArrowClick() }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(16.dp).padding(start = 4.dp)
            )
        }
    }
}

/**
 * YouTube-style horizontal history video card
 */
@Composable
private fun HistoryVideoCard(
    video: VideoItem,
    progressFraction: Float,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onSaveToWatchLater: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasScrubbing = remember(video) {
        com.example.util.PreviewFrameResolver.supportsScrubbing(video)
    }
    val previewFrames = remember(video, hasScrubbing) {
        if (hasScrubbing) com.example.util.PreviewFrameResolver.resolvePreviewFrames(video) else emptyList()
    }
    val isScrubbable = hasScrubbing && previewFrames.size > 1
    var isAutoPlaying by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var cardWidthPx by remember { mutableFloatStateOf(1f) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isAutoPlaying, previewFrames) {
        if (isAutoPlaying && isScrubbable) {
            while (isAutoPlaying) {
                kotlinx.coroutines.delay(200L)
                frameIndex = (frameIndex + 1) % previewFrames.size
                scrubFraction = (frameIndex + 1).toFloat() / previewFrames.size
            }
        }
    }

    val isPreviewActive = (isScrubbing || isAutoPlaying) && isScrubbable
    val activeThumb = if (isPreviewActive && frameIndex in previewFrames.indices) {
        previewFrames[frameIndex]
    } else {
        video.thumbnailUrl ?: "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg"
    }

    val scrubModifier = if (isScrubbable) {
        Modifier.pointerInput(previewFrames, isAutoPlaying) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragAccumulator = 0f
                    if (!isAutoPlaying) {
                        isScrubbing = true
                        val frac = (offset.x / cardWidthPx).coerceIn(0f, 1f)
                        scrubFraction = frac
                        frameIndex = (frac * (previewFrames.size - 1)).roundToInt().coerceIn(0, previewFrames.size - 1)
                    }
                },
                onDragEnd = {
                    isScrubbing = false
                    if (kotlin.math.abs(dragAccumulator) > 15f) {
                        isAutoPlaying = true
                    }
                },
                onDragCancel = { isScrubbing = false },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragAccumulator += dragAmount
                    if (!isAutoPlaying) {
                        val frac = (change.position.x / cardWidthPx).coerceIn(0f, 1f)
                        scrubFraction = frac
                        frameIndex = (frac * (previewFrames.size - 1)).roundToInt().coerceIn(0, previewFrames.size - 1)
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .width(160.dp)
            .clickable { if (!isScrubbing) onClick() }
    ) {
        // Thumbnail with duration overlay & progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onGloballyPositioned { layoutCoords ->
                    cardWidthPx = layoutCoords.size.width.toFloat().coerceAtLeast(1f)
                }
                .then(scrubModifier)
        ) {
            AsyncImage(
                model = activeThumb,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration badge
            if (video.formattedDuration.isNotBlank() && !isPreviewActive) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.82f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Teaser Scrubber Bar
            if (isPreviewActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = scrubFraction.coerceIn(0.01f, 1f))
                            .background(Color(0xFFFF1744))
                    )
                }
            } else if (progressFraction > 0f) {
                // Red watch progress line at bottom of thumbnail
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Gray.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progressFraction.coerceIn(0f, 1f))
                            .background(Color(0xFFFF0000))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title and 3-dots row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
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
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play next in queue") },
                        leadingIcon = { Icon(Icons.Outlined.QueuePlayNext, null) },
                        onClick = {
                            onPlayNext()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Play last in queue") },
                        leadingIcon = { Icon(Icons.Outlined.PlaylistAdd, null) },
                        onClick = {
                            onAddToQueue?.invoke()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to Watch Later") },
                        leadingIcon = { Icon(Icons.Outlined.WatchLater, null) },
                        onClick = {
                            onSaveToWatchLater()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to playlist") },
                        leadingIcon = { Icon(Icons.Outlined.PlaylistAdd, null) },
                        onClick = {
                            onSaveToPlaylist()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from watch history") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = {
                            onRemoveFromHistory()
                            menuExpanded = false
                        }
                    )
                }
            }
        }

        // Channel Name
        Text(
            text = video.uploaderName.ifBlank { "Channel" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * YouTube-style Playlist Cover Card
 */
@Composable
private fun PlaylistCoverCard(
    title: String,
    subtitle: String,
    videoCount: Int,
    thumbnailUrl: String?,
    icon: ImageVector,
    badgeColor: Color,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(160.dp)
            .clickable { onClick() }
    ) {
        // Thumbnail collage with playlist badge overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (!thumbnailUrl.isNullOrBlank()) MaterialTheme.colorScheme.surfaceVariant
                    else badgeColor
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Playlist bottom-right semi-transparent badge with count & icon
            Surface(
                shape = RoundedCornerShape(topStart = 6.dp),
                color = Color.Black.copy(alpha = 0.78f),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "$videoCount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title and 3-dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (onDelete != null) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = {
                                onDelete()
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Menu list item for YouTube Account section
 */
@Composable
private fun AccountMenuListItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Bottom sheet viewer for Watch Later / Liked Videos / Playlist detail
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoListBottomSheet(
    title: String,
    videos: List<VideoItem>,
    onDismiss: () -> Unit,
    onSelectVideo: (VideoItem) -> Unit,
    onPlayAll: () -> Unit,
    onRemoveVideo: ((VideoItem) -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDeletePlaylist: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${videos.size} videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onRename != null) {
                        IconButton(onClick = onRename) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Rename")
                        }
                    }
                    if (onDeletePlaylist != null) {
                        IconButton(onClick = onDeletePlaylist) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                    if (onClearAll != null && videos.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Clear all", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Play All Action Button
            if (videos.isNotEmpty()) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play all", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Video List
            if (videos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No videos in this playlist yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(videos, key = { it.id }) { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectVideo(video) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(width = 100.dp, height = 56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = video.thumbnailUrl ?: "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg",
                                    contentDescription = video.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (video.formattedDuration.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(2.dp),
                                        color = Color.Black.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                    ) {
                                        Text(
                                            text = video.formattedDuration,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = video.uploaderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }

                            if (onRemoveVideo != null) {
                                IconButton(onClick = { onRemoveVideo(video) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Bottom sheet viewer for Downloads
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsBottomSheet(
    downloads: List<OfflineDownloadEntity>,
    onDismiss: () -> Unit,
    onPlayDownload: (OfflineDownloadEntity) -> Unit,
    onDeleteDownload: (OfflineDownloadEntity) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${downloads.size} videos available offline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No downloaded videos yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(downloads, key = { it.videoId }) { dl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPlayDownload(dl) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 100.dp, height = 56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (!dl.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = dl.thumbnailUrl,
                                        contentDescription = dl.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dl.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${dl.channelName} • ${dl.qualityLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { onDeleteDownload(dl) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
