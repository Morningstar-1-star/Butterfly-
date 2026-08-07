package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    var isViewingWatchLaterDetail by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PROFILE CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
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
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile.name,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = userProfile.handle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = userProfile.bio,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                }
            }

            // PLAYBACK QUEUE SECTION
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Next in Queue (${playbackQueue.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (playbackQueue.isNotEmpty()) {
                            Row {
                                TextButton(onClick = { viewModel.playNextInQueue() }) {
                                    Text("Play Queue", fontSize = 12.sp)
                                }
                                TextButton(onClick = { viewModel.clearQueue() }) {
                                    Text("Clear", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (playbackQueue.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Outlined.QueueMusic,
                            title = "Queue is empty",
                            description = "Use 'Play next in queue' from the 3-dots menu on any video card to add items here."
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(playbackQueue) { video ->
                                QueueVideoCard(
                                    video = video,
                                    onPlay = { onSelectVideo(video) },
                                    onRemove = { viewModel.removeFromQueue(video) }
                                )
                            }
                        }
                    }
                }
            }

            // PLAYLISTS SECTION
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Playlists (${userPlaylists.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
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

                    if (userPlaylists.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Outlined.BookmarkBorder,
                            title = "No playlists created yet",
                            description = "Tap '+' above to create a playlist or save videos from the 3-dots option menu."
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(userPlaylists) { playlist ->
                                PlaylistCardItem(
                                    playlist = playlist,
                                    onClick = { selectedPlaylist = playlist }
                                )
                            }
                        }
                    }
                }
            }

            // WATCH LATER SECTION
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isViewingWatchLaterDetail = true }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.WatchLater,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Watch Later (${watchLaterList.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        TextButton(onClick = { isViewingWatchLaterDetail = true }) {
                            Text("View all", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (watchLaterList.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Outlined.WatchLater,
                            title = "No videos in Watch Later",
                            description = "Save videos to Watch Later using the 3-dots menu on any video card."
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(watchLaterList) { video ->
                                WatchLaterVideoCard(
                                    video = video,
                                    onPlay = { onSelectVideo(video) },
                                    onRemove = { viewModel.removeFromWatchLater(video) }
                                )
                            }
                        }
                    }
                }
            }

            // QUICK PREFERENCES & SHORTCUTS
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
                            subtitle = "Manage installed sources and plugins",
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
                            subtitle = "Extractor settings, playback preferences & PO token",
                            onClick = { viewModel.navigateToScreen(AppScreen.SETTINGS) }
                        )
                    }
                }
            }
        }
    }

    // EDIT PROFILE DIALOG
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Local Profile", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g. Lucifer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editHandle,
                        onValueChange = { editHandle = it },
                        label = { Text("Profile Tag / Handle") },
                        placeholder = { Text("e.g. @lucifer") },
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
                        text = "Avatar Poster Theme",
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

                    OutlinedTextField(
                        value = editAvatarUrl,
                        onValueChange = { editAvatarUrl = it },
                        label = { Text("Custom Avatar Image URL (Optional)") },
                        placeholder = { Text("https://example.com/avatar.jpg") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    Text("Save Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // CREATE PLAYLIST DIALOG
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("e.g. My Favorites") },
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

    // PLAYLIST DETAILS MODAL / DIALOG
    if (selectedPlaylist != null) {
        AlertDialog(
            onDismissRequest = { selectedPlaylist = null },
            title = { Text(selectedPlaylist!!.title) },
            text = {
                val videos = selectedPlaylist!!.videos
                if (videos.isEmpty()) {
                    Text(
                        text = "This playlist is currently empty. Add videos using the 3-dots menu on any video card.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        videos.forEach { video ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectVideo(video)
                                        selectedPlaylist = null
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = video.title,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPlaylist = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun QueueVideoCard(
    video: VideoItem,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { onPlay() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                if (!video.thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = video.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.uploaderName,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlaylistCardItem(
    playlist: UserPlaylist,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${playlist.videos.size} videos",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = playlist.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun WatchLaterVideoCard(
    video: VideoItem,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { onPlay() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                if (!video.thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = video.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.uploaderName,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AccountMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun getAvatarBrush(preset: String): androidx.compose.ui.graphics.Brush {
    return when (preset) {
        "pink" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFF4081), Color(0xFFE91E63)))
        "blue" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0288D1)))
        "emerald" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF009688)))
        "gold" -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFD600), Color(0xFFFF9100)))
        else -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF8E24AA), Color(0xFFAB47BC)))
    }
}
