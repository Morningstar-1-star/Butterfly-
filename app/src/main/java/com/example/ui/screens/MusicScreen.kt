package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.MainViewModel
import com.example.util.PlaybackPreferences
import com.example.util.ThumbnailOptimizer

@Composable
fun MusicScreen(
    viewModel: MainViewModel,
    onSelectTrack: (VideoItem) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf("All Music") }

    val trendingList by viewModel.trendingVideos.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val adultContentEnabled by viewModel.adultContentEnabled.collectAsState()

    // Aggregate music items from YouTube Music, Topic channels, Archive.org Audio & MySpace Music
    val allMusicFeed = remember(trendingList, searchResults, adultContentEnabled) {
        val rawFeed = if (searchResults.isNotEmpty()) searchResults else trendingList
        rawFeed
            .filterNot { viewModel.isBlockedVideo(it) }
            .filter { adultContentEnabled || !viewModel.isAdultVideoItem(it) }
            .filter { item ->
                PlaybackPreferences.isMusicMedia(
                    title = item.title,
                    uploaderName = item.uploaderName,
                    description = item.description,
                    tags = item.tags,
                    providerId = item.providerId
                ) ||
                        item.title.contains("official music video", ignoreCase = true) ||
                        item.title.contains("official audio", ignoreCase = true) ||
                        item.title.contains("full song", ignoreCase = true) ||
                        item.title.contains("lyrics", ignoreCase = true) ||
                        item.title.contains("audio", ignoreCase = true) ||
                        item.title.contains("lofi", ignoreCase = true) ||
                        item.title.contains("remix", ignoreCase = true) ||
                        (item.uploaderName?.endsWith("- Topic", ignoreCase = true) == true) ||
                        (item.uploaderName?.contains("vevo", ignoreCase = true) == true) ||
                        item.providerId == "archive_org" || item.providerId == "myspace"
            }
            .distinctBy { "${it.providerId}_${it.id}" }
    }

    val filteredMusic = remember(allMusicFeed, activeCategory, searchQuery) {
        var list = allMusicFeed
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        (it.uploaderName?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
        when (activeCategory) {
            "YouTube Music" -> list.filter { it.providerId == "youtube" || it.providerId.isNullOrEmpty() }
            "Pop & Hits" -> list.filter { it.title.contains("pop", ignoreCase = true) || it.title.contains("hit", ignoreCase = true) }
            "Hip-Hop" -> list.filter { it.title.contains("hip hop", ignoreCase = true) || it.title.contains("rap", ignoreCase = true) }
            "Lofi & Chill" -> list.filter { it.title.contains("lofi", ignoreCase = true) || it.title.contains("chill", ignoreCase = true) }
            "Other Sources" -> list.filter { it.providerId == "archive_org" || it.providerId == "myspace" || it.providerId == "dailymotion" }
            else -> list
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "YouTube Music & Audio",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search songs, artists, albums, or audio...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Category Filter Chips
        val categories = listOf("All Music", "YouTube Music", "Pop & Hits", "Hip-Hop", "Lofi & Chill", "Other Sources")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = (activeCategory == cat),
                    onClick = { activeCategory = cat },
                    label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding.coerceAtLeast(120.dp)),
            modifier = Modifier.fillMaxSize()
        ) {
            // Hero / Trending Music Row
            item {
                if (filteredMusic.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "Featured Music Hits",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredMusic.take(8), key = { "featured_${it.providerId}_${it.id}" }) { item ->
                                FeaturedMusicCard(item = item, onClick = { onSelectTrack(item) })
                            }
                        }
                    }
                }
            }

            // Music Genres Mood Pills
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Moods & Genres",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    val moods = listOf(
                        "🎧 Focus & Lofi" to Color(0xFF3F51B5),
                        "🔥 Top Charts" to Color(0xFFE91E63),
                        "⚡ Workout & EDM" to Color(0xFFFF9800),
                        "🌙 Relax & Chill" to Color(0xFF009688),
                        "🎸 Rock & Acoustic" to Color(0xFF795548),
                        "☕ Jazz & Blues" to Color(0xFF673AB7)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(moods) { (label, bgColor) ->
                            Surface(
                                color = bgColor.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable {
                                    activeCategory = "All Music"
                                    searchQuery = label.substringAfter(" ").trim()
                                }
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Top Song List Section
            item {
                Text(
                    text = "Songs & Audio Tracks",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp)
                )
            }

            if (filteredMusic.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No music tracks found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(filteredMusic, key = { index, item -> "track_${item.providerId}_${item.id}_$index" }) { index, item ->
                    MusicTrackRow(
                        rank = index + 1,
                        item = item,
                        onClick = { onSelectTrack(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedMusicCard(
    item: VideoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.thumbnailUrl) {
        ThumbnailOptimizer.buildThumbnailRequest(context, item.thumbnailUrl, crossfadeMillis = 100)
    }

    Card(
        modifier = Modifier
            .width(170.dp)
            .height(220.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Provider Tag
            val providerName = when (item.providerId) {
                "archive_org" -> "Archive.org"
                "myspace" -> "MySpace"
                else -> "YouTube Music"
            }
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = providerName,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // Play Icon Overlay
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Track",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(20.dp)
                )
            }

            // Track Details
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 40.dp, bottom = 10.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.uploaderName ?: "Music Artist",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MusicTrackRow(
    rank: Int,
    item: VideoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.thumbnailUrl) {
        ThumbnailOptimizer.buildThumbnailRequest(context, item.thumbnailUrl, crossfadeMillis = 100)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank number
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(28.dp)
        )

        // Thumbnail
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val providerLabel = when (item.providerId) {
                    "archive_org" -> "Archive.org"
                    "myspace" -> "MySpace"
                    else -> "YouTube Music"
                }
                Text(
                    text = "${item.uploaderName ?: "Artist"} • $providerLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = "Play Track",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
