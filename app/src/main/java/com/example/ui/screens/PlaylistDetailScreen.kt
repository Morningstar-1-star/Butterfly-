package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VideoItem
import com.example.util.VideoCategory
import com.example.util.VideoCategoryClassifier

enum class PlaylistSortOption(val displayName: String) {
    DEFAULT("Default order"),
    NEWEST_ADDED("Date added (newest)"),
    OLDEST_ADDED("Date added (oldest)"),
    TITLE_A_Z("Title (A to Z)"),
    DURATION_LONGEST("Duration (longest first)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    title: String,
    authorName: String,
    isWatchLater: Boolean = false,
    videos: List<VideoItem>,
    onPlayVideo: (VideoItem) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRemoveVideo: (VideoItem) -> Unit,
    onRemoveWatched: (() -> Unit)? = null,
    onRemoveUnavailable: (() -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    onSort: ((List<VideoItem>) -> Unit)? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val topThumbnail = videos.firstOrNull()?.thumbnailUrl

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(VideoCategoryClassifier.CATEGORY_ALL) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var currentSortOption by remember { mutableStateOf(PlaylistSortOption.DEFAULT) }

    val categoryCounts = remember(videos) {
        VideoCategoryClassifier.getAllCategoriesInList(videos)
    }

    // Apply Sorting
    val sortedVideos = remember(videos, currentSortOption) {
        when (currentSortOption) {
            PlaylistSortOption.DEFAULT -> videos
            PlaylistSortOption.NEWEST_ADDED -> videos
            PlaylistSortOption.OLDEST_ADDED -> videos.reversed()
            PlaylistSortOption.TITLE_A_Z -> videos.sortedBy { it.title.lowercase() }
            PlaylistSortOption.DURATION_LONGEST -> videos.sortedByDescending { it.durationSeconds ?: 0L }
        }
    }

    // Apply Filter & Search
    val filteredVideos = remember(sortedVideos, searchQuery, selectedCategory) {
        sortedVideos.filter { video ->
            val matchesCategory = if (selectedCategory.id == VideoCategoryClassifier.CATEGORY_ALL.id) {
                true
            } else {
                val videoCats = VideoCategoryClassifier.classify(video)
                videoCats.any { it.id == selectedCategory.id }
            }

            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                video.title.lowercase().contains(q) ||
                (video.uploaderName ?: "").lowercase().contains(q) ||
                video.tags.any { it.lowercase().contains(q) }
            }

            matchesCategory && matchesQuery
        }
    }

    // Total Duration Computation
    val totalDurationText = remember(videos) {
        val totalSeconds = videos.sumOf { it.durationSeconds ?: 0L }
        if (totalSeconds > 0) {
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes}m"
            }
        } else {
            ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort playlist",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            if (onRemoveWatched != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove watched videos") },
                                    onClick = {
                                        showTopMenu = false
                                        onRemoveWatched()
                                        Toast.makeText(context, "Watched videos removed", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                    }
                                )
                            }
                            if (onRemoveUnavailable != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove unavailable videos") },
                                    onClick = {
                                        showTopMenu = false
                                        onRemoveUnavailable()
                                        Toast.makeText(context, "Unavailable videos cleaned", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Sort playlist") },
                                onClick = {
                                    showTopMenu = false
                                    showSortDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Sort, contentDescription = null)
                                }
                            )
                            if (onClearAll != null && videos.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isWatchLater) "Clear Watch Later" else "Clear playlist",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showTopMenu = false
                                        showClearConfirmDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteForever,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
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
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // HERO BANNER CARD (Sleek YouTube-like Aesthetic)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    // Ambient Backdrop Glow
                    if (!topThumbnail.isNullOrEmpty()) {
                        AsyncImage(
                            model = topThumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.4f),
                                            Color.Black.copy(alpha = 0.85f),
                                            Color.Black.copy(alpha = 0.98f)
                                        )
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                        )
                    }

                    // Content details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .align(Alignment.BottomStart)
                    ) {
                        // Title
                        Text(
                            text = title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Meta details
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = authorName,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "•",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "${videos.size} videos",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium
                            )
                            if (totalDurationText.isNotEmpty()) {
                                Text(
                                    text = "•",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = totalDurationText,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons: Play All & Shuffle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onPlayAll,
                                enabled = videos.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Play all",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = onShuffle,
                                enabled = videos.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.22f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Shuffle",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // SEARCH BAR & SMART CATEGORY TAG CHIPS
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search this list...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    )

                    // Category Chips (Only shown if multiple categories exist)
                    if (categoryCounts.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categoryCounts) { (category, count) ->
                                val isSelected = selectedCategory.id == category.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = category },
                                    label = {
                                        Text(
                                            text = "${category.emoji} ${category.name} ($count)",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }

                    // Swipe hint and item counter banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing ${filteredVideos.size} of ${videos.size} videos",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (selectedCategory.id != VideoCategoryClassifier.CATEGORY_ALL.id || searchQuery.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    selectedCategory = VideoCategoryClassifier.CATEGORY_ALL
                                    searchQuery = ""
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Reset filters", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (filteredVideos.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SwipeLeft,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Swipe left to remove",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // LIST ITEMS
            if (filteredVideos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isWatchLater) Icons.Outlined.WatchLater else Icons.Outlined.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (videos.isEmpty()) "No videos saved yet" else "No matching videos found",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (videos.isEmpty()) "Use the Save / Bookmark button while playing to add videos here" else "Try clearing your search or category filter",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredVideos,
                    key = { _, item -> item.id }
                ) { index, video ->
                    SwipeablePlaylistItemRow(
                        video = video,
                        index = index + 1,
                        onClick = { onPlayVideo(video) },
                        onRemove = {
                            onRemoveVideo(video)
                            Toast.makeText(context, "Removed from list", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // SORT DIALOG
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sort playlist", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlaylistSortOption.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    currentSortOption = option
                                    showSortDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentSortOption == option,
                                onClick = {
                                    currentSortOption = option
                                    showSortDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = option.displayName,
                                fontSize = 14.sp,
                                fontWeight = if (currentSortOption == option) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // CLEAR ALL CONFIRMATION DIALOG
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(if (isWatchLater) "Clear Watch Later?" else "Clear Playlist?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to remove all ${videos.size} videos? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAll?.invoke()
                        Toast.makeText(context, "Playlist cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear all", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeablePlaylistItemRow(
    video: VideoItem,
    index: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                },
                label = "dismiss_background_color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Remove",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = Color.White
                    )
                }
            }
        }
    ) {
        PlaylistItemContent(
            video = video,
            onClick = onClick,
            onRemove = onRemove
        )
    }
}

@Composable
private fun PlaylistItemContent(
    video: VideoItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Box
        Box(
            modifier = Modifier
                .width(128.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!video.thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Duration Badge
            if (video.formattedDuration.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            val detectedCategories = remember(video) {
                VideoCategoryClassifier.classify(video)
            }

            Text(
                text = video.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = video.uploaderName ?: "Unknown Channel",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                val primaryCategory = detectedCategories.firstOrNull()
                if (primaryCategory != null && primaryCategory.id != VideoCategoryClassifier.CATEGORY_OTHER.id) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${primaryCategory.emoji} ${primaryCategory.name}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (video.formattedViews.isNotEmpty() || !video.uploadDate.isNullOrEmpty()) {
                Text(
                    text = buildString {
                        if (video.formattedViews.isNotEmpty()) append(video.formattedViews)
                        if (!video.uploadDate.isNullOrEmpty()) {
                            if (isNotEmpty()) append(" • ")
                            append(video.uploadDate)
                        }
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 3-Dots Menu
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play video") },
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Remove from list") },
                    onClick = {
                        showMenu = false
                        onRemove()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}
